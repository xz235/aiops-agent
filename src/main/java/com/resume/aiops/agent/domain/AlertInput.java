package com.resume.aiops.agent.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record AlertInput(
        @NotBlank String title,
        @NotBlank String service,
        @NotBlank String severity,
        String description,
        Map<String, Object> labels) {

    public AlertInput {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }
}

