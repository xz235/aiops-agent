package com.resume.aiops.memory;

import com.resume.aiops.memory.domain.AgentMemoryEntry;
import java.util.List;

public interface AgentMemoryRepository {
    void append(AgentMemoryEntry entry);
    List<AgentMemoryEntry> findByTaskId(String taskId);
}

