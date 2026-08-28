package com.resume.aiops.memory.domain;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(
        long eventId,
        String taskId,
        AgentEventType type,
        Map<String, Object> payload,
        Instant occurredAt) {

    public AgentEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

