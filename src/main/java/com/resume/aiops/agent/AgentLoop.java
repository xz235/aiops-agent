package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.*;
import com.resume.aiops.config.AiOpsProperties;
import com.resume.aiops.memory.*;
import com.resume.aiops.memory.domain.*;
import com.resume.aiops.rag.*;
import com.resume.aiops.skill.*;
import com.resume.aiops.streaming.AgentEventPublisher;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Bounded observe/decide/act loop; shares storage and tools with the legacy workflow. */
@Service
public class AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    private final ObjectProvider<AgentDecisionClient> clients;
    private final DynamicSkillRegistry registry;
    private final SkillExecutor executor;
    private final ToolCallRepository tools;
    private final TaskStateRepository tasks;
    private final AgentMemoryRepository memory;
    private final AgentEventPublisher events;
    private final AiOpsProperties properties;
    private final Clock clock;
    private final ExecutorService workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "agent-bounded-call");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy
    public void close() { workers.shutdownNow(); }

    private <T> T bounded(Callable<T> operation, long timeoutMs) {
        Future<T> future;
        try {
            future = workers.submit(operation);
        } catch (RejectedExecutionException error) {
            throw new IllegalStateException("Agent worker pool is saturated; no capacity for this call", error);
        }
        try {
            return future.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent call interrupted", error);
        } catch (TimeoutException error) {
            throw new IllegalStateException("Agent call timed out", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            throw new IllegalStateException(cause == null ? "Agent call failed"
                    : Objects.toString(cause.getMessage(), "Agent call failed"), error);
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    public AgentLoop(ObjectProvider<AgentDecisionClient> clients, DynamicSkillRegistry registry,
                     SkillExecutor executor, ToolCallRepository tools, TaskStateRepository tasks,
                     AgentMemoryRepository memory, AgentEventPublisher events,
                     AiOpsProperties properties, Clock clock) {
        this.clients = clients; this.registry = registry; this.executor = executor;
        this.tools = tools; this.tasks = tasks; this.memory = memory; this.events = events;
        this.properties = properties; this.clock = clock;
    }

    public void validateMode(AlertInput alert) {
        String mode = String.valueOf(alert.labels().getOrDefault("mode", "workflow"));
        if (!Set.of("workflow", "agent").contains(mode)) {
            throw new IllegalArgumentException("mode must be workflow or agent");
        }
        if ("agent".equals(mode) && clients.getIfAvailable() == null) {
            throw new IllegalArgumentException("Agent mode requires the llm profile and model configuration");
        }
    }

    public Result run(DiagnosisTask initial, List<RunbookEvidence> runbooks) {
        AgentDecisionClient client = clients.getObject();
        DiagnosisTask task = initial;
        List<ToolCallRecord> observations = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        Set<Invocation> attempted = new HashSet<>();
        Instant deadline = Instant.now(clock).plus(properties.taskTimeout());
        var catalog = registry.definitions().stream()
                .filter(tool -> tool.riskLevel() == SkillRiskLevel.READ_ONLY).toList();
        for (int turn = 0; turn <= properties.maxAgentSteps(); turn++) {
            if (!Instant.now(clock).isBefore(deadline)) return review(task, observations, "Task time budget exceeded");
            task = task.transition(TaskStatus.PLANNING, Instant.now(clock));
            tasks.save(task);
            AgentAction action;
            try {
                AgentContext context = new AgentContext(task.alert(), catalog, runbooks, observations,
                        properties.maxAgentSteps() - observations.size());
                action = bounded(() -> client.decide(context),
                        Math.min(properties.agentDecisionTimeout().toMillis(),
                                Duration.between(Instant.now(clock), deadline).toMillis()));
                if (action == null) throw new IllegalArgumentException("Empty decision");
            } catch (RuntimeException error) {
                log.warn("Agent decision failed for task {} at turn {}; routing to human review",
                        task.taskId(), turn, error);
                return review(task, observations, "Model unavailable or invalid decision; human review required");
            }
            memory.append(new AgentMemoryEntry(UUID.randomUUID().toString(), task.taskId(),
                    "AGENT_ACTION", action.type(), Map.of("turn", turn, "action", action), Instant.now(clock)));
            events.publish(task.taskId(), AgentEventType.AGENT_DECISION,
                    Map.of("turn", turn, "action", action));
            if (!Instant.now(clock).isBefore(deadline)) return review(task, observations, "Task time budget exceeded");
            if ("FINAL".equals(action.type())) {
                Set<String> validIds = new HashSet<>();
                observations.stream().filter(ToolCallRecord::success).forEach(call -> validIds.add(call.callId()));
                runbooks.forEach(book -> validIds.add(book.documentId()));
                boolean hasTool = observations.stream().anyMatch(call -> call.success()
                        && action.evidenceIds().contains(call.callId()));
                boolean hasRunbook = runbooks.stream().anyMatch(book -> action.evidenceIds().contains(book.documentId()));
                if (!hasTool || !hasRunbook || !validIds.containsAll(action.evidenceIds())) {
                    return review(task, observations, "Final answer has missing or invalid evidence citations; human review required");
                }
                String conclusion = action.conclusion() + "\nEvidence: " + String.join(", ", action.evidenceIds());
                return new Result(task, List.copyOf(observations), new DiagnosisDecision(conclusion,
                        action.reviewRequired(), action.reviewRequired() ? List.of("Model requested human review") : List.of()));
            }
            if (turn == properties.maxAgentSteps()) return review(task, observations, "Tool call budget exhausted");
            Invocation fingerprint = new Invocation(action.skillName(), action.arguments());
            if (!attempted.add(fingerprint)) return review(task, observations, "Repeated identical tool call stopped");
            selected.add(action.skillName());
            task = task.withPlan(selected, Instant.now(clock)).transition(TaskStatus.EXECUTING_TOOLS, Instant.now(clock));
            tasks.save(task);
            Instant started = Instant.now(clock);
            String id = UUID.randomUUID().toString();
            events.publish(task.taskId(), AgentEventType.TOOL_STARTED,
                    Map.of("callId", id, "skill", action.skillName(), "arguments", action.arguments()));
            Map<String, Object> output;
            boolean success;
            try {
                if (catalog.stream().noneMatch(tool -> tool.name().equals(action.skillName()))) {
                    throw new IllegalArgumentException("Tool is not in the read-only catalog");
                }
                AlertInput alert = task.alert();
                long timeout = registry.require(action.skillName()).definition().timeoutMs();
                output = bounded(() -> executor.execute(action.skillName(), alert, action.arguments()),
                        Math.min(timeout, Duration.between(Instant.now(clock), deadline).toMillis()));
                success = true;
            } catch (RuntimeException error) {
                log.warn("Tool {} failed for task {}", action.skillName(), task.taskId(), error);
                output = Map.of("error", Objects.toString(error.getMessage(), "Tool execution failed"));
                success = false;
            }
            Instant ended = Instant.now(clock);
            ToolCallRecord record = new ToolCallRecord(id, task.taskId(), action.skillName(), action.arguments(),
                    output, success, Duration.between(started, ended).toMillis(), started, ended);
            tools.append(record);
            observations.add(record);
            events.publish(task.taskId(), success ? AgentEventType.TOOL_COMPLETED : AgentEventType.TOOL_FAILED,
                    Map.of("callId", id, "skill", action.skillName(), "result", output, "latencyMs", record.latencyMs()));
        }
        return review(task, observations, "Decision budget exhausted");
    }

    private Result review(DiagnosisTask task, List<ToolCallRecord> calls, String reason) {
        return new Result(task, List.copyOf(calls), new DiagnosisDecision(reason, true, List.of(reason)));
    }

    public record Result(DiagnosisTask task, List<ToolCallRecord> calls, DiagnosisDecision decision) {}
    private record Invocation(String skillName, Map<String, Object> arguments) {}
}
