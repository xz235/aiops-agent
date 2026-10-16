package com.resume.aiops.agent;

import com.resume.aiops.agent.AgentPlan.PlannedSkillCall;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.agent.domain.TaskStatus;
import com.resume.aiops.config.AiOpsProperties;
import com.resume.aiops.memory.TaskStateRepository;
import com.resume.aiops.memory.ToolCallRepository;
import com.resume.aiops.memory.AgentMemoryRepository;
import com.resume.aiops.memory.domain.AgentMemoryEntry;
import com.resume.aiops.memory.domain.AgentEventType;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.rag.RunbookEvidence;
import com.resume.aiops.rag.RunbookRetriever;
import com.resume.aiops.report.PdfReportService;
import com.resume.aiops.skill.SkillExecutor;
import com.resume.aiops.streaming.AgentEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntime {
    private final TaskStateRepository taskRepository;
    private final ToolCallRepository toolCallRepository;
    private final AgentMemoryRepository memoryRepository;
    private final AgentPlanner planner;
    private final AgentLoop agentLoop;
    private final SkillExecutor skillExecutor;
    private final RunbookRetriever runbookRetriever;
    private final DiagnosisSynthesizer synthesizer;
    private final PdfReportService reportService;
    private final AgentEventPublisher eventPublisher;
    private final Clock clock;
    private final AiOpsProperties properties;
    private final Counter completedCounter;
    private final Counter reviewCounter;
    private final Counter failedCounter;

    public AgentRuntime(TaskStateRepository taskRepository, ToolCallRepository toolCallRepository,
                        AgentMemoryRepository memoryRepository,
                        AgentPlanner planner, SkillExecutor skillExecutor, RunbookRetriever runbookRetriever,
                        DiagnosisSynthesizer synthesizer, PdfReportService reportService,
                        AgentEventPublisher eventPublisher, Clock clock, MeterRegistry meterRegistry,
                        AiOpsProperties properties, AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
        this.taskRepository = taskRepository;
        this.toolCallRepository = toolCallRepository;
        this.memoryRepository = memoryRepository;
        this.planner = planner;
        this.skillExecutor = skillExecutor;
        this.runbookRetriever = runbookRetriever;
        this.synthesizer = synthesizer;
        this.reportService = reportService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.properties = properties;
        this.completedCounter = meterRegistry.counter("aiops.agent.tasks", "result", "completed");
        this.reviewCounter = meterRegistry.counter("aiops.agent.tasks", "result", "needs_human_review");
        this.failedCounter = meterRegistry.counter("aiops.agent.tasks", "result", "failed");
    }

    @Async
    public void execute(String taskId) {
        MDC.put("taskId", taskId);
        try {
            DiagnosisTask task = requireTask(taskId);
            task = transition(task, TaskStatus.PLANNING);
            boolean agentMode = "agent".equals(task.alert().labels().get("mode"));
            AgentPlan plan = agentMode ? new AgentPlan("Observe and decide one action at a time", List.of())
                    : planner.createPlan(task.alert());
            int allowedSteps = Math.min(plan.calls().size(), properties.maxAgentSteps());
            int omittedSteps = plan.calls().size() - allowedSteps;
            List<PlannedSkillCall> selectedCalls = plan.calls().subList(0, allowedSteps);
            task = task.withPlan(selectedCalls.stream().map(PlannedSkillCall::skillName).toList(), Instant.now(clock));
            taskRepository.save(task);
            eventPublisher.publish(taskId, AgentEventType.PLAN_CREATED,
                    Map.of("summary", plan.summary(), "skills", task.plannedSkills(),
                            "omittedSteps", omittedSteps, "version", task.version()));

            DiagnosisTask retrieving = transition(task, TaskStatus.RETRIEVING);
            List<RunbookEvidence> evidence = runbookRetriever.retrieve(retrieving.alert(), 3);
            memoryRepository.append(new AgentMemoryEntry(UUID.randomUUID().toString(), taskId,
                    "RAG_EVIDENCE", "Retrieved " + evidence.size() + " relevant runbook(s)",
                    Map.of("documents", evidence), Instant.now(clock)));
            eventPublisher.publish(taskId, AgentEventType.RAG_RETRIEVAL,
                    Map.of("references", evidence));

            DiagnosisTask executing = transition(retrieving, TaskStatus.EXECUTING_TOOLS);
            List<ToolCallRecord> calls = new ArrayList<>();
            DiagnosisDecision decision;
            if (agentMode) {
                AgentLoop.Result result = agentLoop.run(executing, evidence);
                executing = result.task();
                calls.addAll(result.calls());
                decision = result.decision();
            } else {
                for (PlannedSkillCall call : selectedCalls) {
                    calls.add(executeSkill(executing, call));
                }
                decision = synthesizer.synthesize(executing.alert(), calls, evidence, omittedSteps);
            }
            DiagnosisTask analyzing = transition(executing, TaskStatus.ANALYZING);
            String conclusion = decision.conclusion();
            memoryRepository.append(new AgentMemoryEntry(UUID.randomUUID().toString(), taskId,
                    "DIAGNOSIS", conclusion,
                    Map.of("toolCallCount", calls.size(), "reviewRequired", decision.reviewRequired(),
                            "reasons", decision.reasons()), Instant.now(clock)));
            if (agentMode) {
                eventPublisher.publish(taskId, AgentEventType.DIAGNOSIS_READY, Map.of("conclusion", conclusion));
            } else {
                streamConclusion(taskId, conclusion);
            }

            DiagnosisTask reporting = transition(analyzing, TaskStatus.GENERATING_REPORT);
            String reportId = reportService.generate(reporting, calls, evidence, conclusion,
                    decision.reviewRequired());
            eventPublisher.publish(taskId, AgentEventType.REPORT_READY,
                    Map.of("reportId", reportId, "downloadUrl", "/api/reports/" + reportId));

            if (decision.reviewRequired()) {
                DiagnosisTask review = reporting.requireHumanReview(conclusion, reportId, Instant.now(clock));
                taskRepository.save(review);
                eventPublisher.publish(taskId, AgentEventType.TASK_REVIEW_REQUIRED,
                        Map.of("status", review.status(), "conclusion", conclusion,
                                "reasons", decision.reasons(), "version", review.version()));
                reviewCounter.increment();
            } else {
                DiagnosisTask completed = reporting.complete(conclusion, reportId, Instant.now(clock));
                taskRepository.save(completed);
                eventPublisher.publish(taskId, AgentEventType.TASK_COMPLETED,
                        Map.of("status", completed.status(), "conclusion", conclusion, "version", completed.version()));
                completedCounter.increment();
            }
        } catch (Exception exception) {
            failTask(taskId, exception);
        } finally {
            MDC.remove("taskId");
        }
    }

    private ToolCallRecord executeSkill(DiagnosisTask task, PlannedSkillCall call) {
        String callId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now(clock);
        eventPublisher.publish(task.taskId(), AgentEventType.TOOL_STARTED,
                Map.of("callId", callId, "skill", call.skillName(), "arguments", call.arguments()));
        try {
            Map<String, Object> result = skillExecutor.execute(call.skillName(), task.alert(), call.arguments());
            Instant completedAt = Instant.now(clock);
            ToolCallRecord record = new ToolCallRecord(callId, task.taskId(), call.skillName(), call.arguments(),
                    result, true, Duration.between(startedAt, completedAt).toMillis(), startedAt, completedAt);
            toolCallRepository.append(record);
            eventPublisher.publish(task.taskId(), AgentEventType.TOOL_COMPLETED,
                    Map.of("callId", callId, "skill", call.skillName(), "result", result,
                            "latencyMs", record.latencyMs()));
            return record;
        } catch (RuntimeException exception) {
            Instant completedAt = Instant.now(clock);
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            ToolCallRecord record = new ToolCallRecord(callId, task.taskId(), call.skillName(), call.arguments(),
                    Map.of("error", message), false,
                    Duration.between(startedAt, completedAt).toMillis(), startedAt, completedAt);
            toolCallRepository.append(record);
            eventPublisher.publish(task.taskId(), AgentEventType.TOOL_FAILED,
                    Map.of("callId", callId, "skill", call.skillName(), "error", message,
                            "latencyMs", record.latencyMs()));
            return record;
        }
    }

    private void streamConclusion(String taskId, String conclusion) {
        for (String token : conclusion.split(" ")) {
            eventPublisher.publish(taskId, AgentEventType.MODEL_TOKEN, Map.of("token", token + " "));
        }
    }

    private DiagnosisTask transition(DiagnosisTask task, TaskStatus status) {
        DiagnosisTask updated = task.transition(status, Instant.now(clock));
        taskRepository.save(updated);
        eventPublisher.publish(task.taskId(), AgentEventType.STATE_CHANGED,
                Map.of("from", task.status(), "to", status, "version", updated.version()));
        return updated;
    }

    private DiagnosisTask requireTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task disappeared: " + taskId));
    }

    private void failTask(String taskId, Exception exception) {
        taskRepository.findById(taskId).ifPresent(task -> {
            DiagnosisTask failed = task.fail(exception.getMessage(), Instant.now(clock));
            taskRepository.save(failed);
            eventPublisher.publish(taskId, AgentEventType.TASK_FAILED,
                    Map.of("status", failed.status(), "message", exception.getMessage() == null ? "Unknown error" : exception.getMessage()));
        });
        failedCounter.increment();
    }
}
