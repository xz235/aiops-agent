package com.resume.aiops.memory.domain;

import java.time.Instant;
import java.util.Map;

public record ToolCallRecord(
        String callId,
        String taskId,
        String skillName,
        Map<String, Object> arguments,
        Map<String, Object> result,
        boolean success,
        long latencyMs,
        Instant startedAt,
        Instant completedAt) {
}

