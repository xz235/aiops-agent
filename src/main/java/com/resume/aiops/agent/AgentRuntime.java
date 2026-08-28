package com.resume.aiops.agent;

import com.resume.aiops.agent.AgentPlan.PlannedSkillCall;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.agent.domain.TaskStatus;
import com.resume.aiops.memory.TaskStateRepository;
import com.resume.aiops.memory.ToolCallRepository;
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
    private final AgentPlanner planner;
    private final SkillExecutor skillExecutor;
    private final RunbookRetriever runbookRetriever;
    private final DiagnosisSynthesizer synthesizer;
    private final PdfReportService reportService;
    private final AgentEventPublisher eventPublisher;
    private final Clock clock;
    private final Counter completedCounter;
    private final Counter failedCounter;

    public AgentRuntime(TaskStateRepository taskRepository, ToolCallRepository toolCallRepository,
                        AgentPlanner planner, SkillExecutor skillExecutor, RunbookRetriever runbookRetriever,
                        DiagnosisSynthesizer synthesizer, PdfReportService reportService,
                        AgentEventPublisher eventPublisher, Clock clock, MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.toolCallRepository = toolCallRepository;
        this.planner = planner;
        this.skillExecutor = skillExecutor;
        this.runbookRetriever = runbookRetriever;
        this.synthesizer = synthesizer;
        this.reportService = reportService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.completedCounter = meterRegistry.counter("aiops.agent.tasks", "result", "completed");
        this.failedCounter = meterRegistry.counter("aiops.agent.tasks", "result", "failed");
    }

    @Async
    public void execute(String taskId) {
        MDC.put("taskId", taskId);
        try {
            DiagnosisTask task = requireTask(taskId);
            task = transition(task, TaskStatus.PLANNING);
            AgentPlan plan = planner.createPlan(task.alert());
            task = task.withPlan(plan.calls().stream().map(PlannedSkillCall::skillName).toList(), Instant.now(clock));
            taskRepository.save(task);
            eventPublisher.publish(taskId, AgentEventType.PLAN_CREATED,
                    Map.of("summary", plan.summary(), "skills", task.plannedSkills(), "version", task.version()));

            DiagnosisTask retrieving = transition(task, TaskStatus.RETRIEVING);
            List<RunbookEvidence> evidence = runbookRetriever.retrieve(retrieving.alert(), 3);
            eventPublisher.publish(taskId, AgentEventType.RAG_RETRIEVAL,
                    Map.of("references", evidence));

            DiagnosisTask executing = transition(retrieving, TaskStatus.EXECUTING_TOOLS);
            List<ToolCallRecord> calls = new ArrayList<>();
            for (PlannedSkillCall call : plan.calls()) {
                calls.add(executeSkill(executing, call));
            }

            DiagnosisTask analyzing = transition(executing, TaskStatus.ANALYZING);
            String conclusion = synthesizer.synthesize(analyzing.alert(), calls, evidence);
            streamConclusion(taskId, conclusion);

            DiagnosisTask reporting = transition(analyzing, TaskStatus.GENERATING_REPORT);
            String reportId = reportService.generate(reporting, calls, evidence, conclusion);
            eventPublisher.publish(taskId, AgentEventType.REPORT_READY,
                    Map.of("reportId", reportId, "downloadUrl", "/api/reports/" + reportId));

            DiagnosisTask completed = reporting.complete(conclusion, reportId, Instant.now(clock));
            taskRepository.save(completed);
            eventPublisher.publish(taskId, AgentEventType.TASK_COMPLETED,
                    Map.of("status", completed.status(), "conclusion", conclusion, "version", completed.version()));
            completedCounter.increment();
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
            ToolCallRecord record = new ToolCallRecord(callId, task.taskId(), call.skillName(), call.arguments(),
                    Map.of("error", exception.getMessage()), false,
                    Duration.between(startedAt, completedAt).toMillis(), startedAt, completedAt);
            toolCallRepository.append(record);
            throw exception;
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

