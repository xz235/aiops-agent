package com.resume.aiops.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiOpsPropertiesTest {

    @Test
    void defaultsAreUsableWithoutAnyConfiguration() {
        AiOpsProperties properties = new AiOpsProperties(null, null, null, null, null, null, 0);

        assertThat(properties.skillsDirectory()).isEqualTo(Path.of("skills"));
        assertThat(properties.taskTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.sseTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.agentDecisionTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.maxAgentSteps()).isEqualTo(8);
    }

    @Test
    void nonPositiveDurationsFallBackToDefaults() {
        AiOpsProperties properties = new AiOpsProperties(null, null, null,
                Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(-5), -1);

        assertThat(properties.taskTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.sseTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.agentDecisionTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.maxAgentSteps()).isEqualTo(8);
    }

    @Test
    void explicitValuesArePreserved() {
        AiOpsProperties properties = new AiOpsProperties(Path.of("s"), Path.of("r"), Path.of("out"),
                Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofSeconds(15), 3);

        assertThat(properties.taskTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.sseTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.agentDecisionTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.maxAgentSteps()).isEqualTo(3);
    }
}
