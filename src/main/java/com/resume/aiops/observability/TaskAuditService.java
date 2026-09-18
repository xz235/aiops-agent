package com.resume.aiops.observability;

import com.resume.aiops.agent.DiagnosisTaskService;
import com.resume.aiops.memory.AgentMemoryRepository;
import com.resume.aiops.memory.EventJournal;
import com.resume.aiops.memory.ToolCallRepository;
import org.springframework.stereotype.Service;

@Service
public class TaskAuditService {
    private final DiagnosisTaskService taskService;
    private final AgentMemoryRepository memoryRepository;
    private final ToolCallRepository toolCallRepository;
    private final EventJournal eventJournal;

    public TaskAuditService(DiagnosisTaskService taskService,
                            AgentMemoryRepository memoryRepository,
                            ToolCallRepository toolCallRepository,
                            EventJournal eventJournal) {
        this.taskService = taskService;
        this.memoryRepository = memoryRepository;
        this.toolCallRepository = toolCallRepository;
        this.eventJournal = eventJournal;
    }

    public TaskAuditView get(String taskId) {
        return new TaskAuditView(
                taskService.get(taskId),
                memoryRepository.findByTaskId(taskId),
                toolCallRepository.findByTaskId(taskId),
                eventJournal.findAfter(taskId, 0));
    }
}

