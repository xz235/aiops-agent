package com.resume.aiops.skill;

import java.nio.file.Path;
import java.util.Map;

public record SkillDefinition(
        String name,
        String description,
        String version,
        SkillRiskLevel riskLevel,
        String handler,
        long timeoutMs,
        Map<String, SkillParameter> parameters,
        String instructions,
        Path source) {

    public SkillDefinition {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}

