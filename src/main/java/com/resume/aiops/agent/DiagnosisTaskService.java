package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.memory.TaskStateRepository;
import com.resume.aiops.memory.domain.AgentEventType;
import com.resume.aiops.streaming.AgentEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisTaskService {
    private final TaskStateRepository repository;
    private final AgentRuntime runtime;
    private final AgentEventPublisher eventPublisher;
    private final Clock clock;
    private final AgentLoop agentLoop;

    public DiagnosisTaskService(TaskStateRepository repository, AgentRuntime runtime,
                                AgentEventPublisher eventPublisher, Clock clock, AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
        this.repository = repository;
        this.runtime = runtime;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public DiagnosisTask create(AlertInput alert) {
        agentLoop.validateMode(alert);
        DiagnosisTask task = DiagnosisTask.create(UUID.randomUUID().toString(), alert, Instant.now(clock));
        repository.save(task);
        eventPublisher.publish(task.taskId(), AgentEventType.TASK_CREATED,
                Map.of("status", task.status(), "alert", task.alert(), "version", task.version()));
        runtime.execute(task.taskId());
        return task;
    }

    public DiagnosisTask get(String taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Diagnosis task not found: " + taskId));
    }
}

