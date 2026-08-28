package com.resume.aiops.memory;

import com.resume.aiops.memory.domain.AgentEvent;
import com.resume.aiops.memory.domain.AgentEventType;
import java.util.List;
import java.util.Map;

public interface EventJournal {
    AgentEvent append(String taskId, AgentEventType type, Map<String, Object> payload);
    List<AgentEvent> findAfter(String taskId, long lastEventId);
}

