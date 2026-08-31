package com.resume.aiops.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownSkillParserTest {
    @TempDir
    Path directory;

    @Test
    void parsesFrontMatterAndInstructions() throws Exception {
        Path skillFile = directory.resolve("demo.md");
        Files.writeString(skillFile, """
                ---
                name: demo_skill
                description: Demo read-only tool
                version: 1.2.0
                riskLevel: READ_ONLY
                handler: demoHandler
                timeoutMs: 1200
                parameters:
                  service:
                    type: string
                    required: true
                    description: Service name
                ---
                # Rules
                Only query data.
                """);

        SkillDefinition definition = new MarkdownSkillParser().parse(skillFile);

        assertThat(definition.name()).isEqualTo("demo_skill");
        assertThat(definition.timeoutMs()).isEqualTo(1200);
        assertThat(definition.parameters().get("service").required()).isTrue();
        assertThat(definition.instructions()).contains("Only query data");
    }
}

