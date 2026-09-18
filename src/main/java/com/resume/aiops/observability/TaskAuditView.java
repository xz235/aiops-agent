package com.resume.aiops.observability;

import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.memory.domain.AgentEvent;
import com.resume.aiops.memory.domain.AgentMemoryEntry;
import com.resume.aiops.memory.domain.ToolCallRecord;
import java.util.List;

public record TaskAuditView(
        DiagnosisTask task,
        List<AgentMemoryEntry> memory,
        List<ToolCallRecord> toolCalls,
        List<AgentEvent> events) {
}

