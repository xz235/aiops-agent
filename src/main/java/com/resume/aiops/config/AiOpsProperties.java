package com.resume.aiops.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops")
public record AiOpsProperties(
        Path skillsDirectory,
        Path runbooksDirectory,
        Path reportsDirectory,
        Duration taskTimeout,
        Duration sseTimeout,
        int maxAgentSteps) {

    public AiOpsProperties {
        skillsDirectory = skillsDirectory == null ? Path.of("skills") : skillsDirectory;
        runbooksDirectory = runbooksDirectory == null ? Path.of("runbooks") : runbooksDirectory;
        reportsDirectory = reportsDirectory == null ? Path.of("reports") : reportsDirectory;
        taskTimeout = taskTimeout == null ? Duration.ofMinutes(5) : taskTimeout;
        sseTimeout = sseTimeout == null ? Duration.ofMinutes(30) : sseTimeout;
        maxAgentSteps = maxAgentSteps <= 0 ? 8 : maxAgentSteps;
    }
}

