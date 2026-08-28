package com.resume.aiops.memory.domain;

import java.time.Instant;
import java.util.Map;

public record AgentMemoryEntry(
        String memoryId,
        String taskId,
        String category,
        String summary,
        Map<String, Object> metadata,
        Instant createdAt) {

    public AgentMemoryEntry {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

