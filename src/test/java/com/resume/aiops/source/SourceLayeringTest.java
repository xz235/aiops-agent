package com.resume.aiops.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.handler.QueryLogsSkillHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The point of the Source layer: the Skill is a contract, not a data source.
 * Changing where data comes from must not touch the Skill or its handler.
 */
class SourceLayeringTest {
    @TempDir
    Path directory;

    private final AlertInput alert = new AlertInput("Order API error spike", "order-service", "HIGH",
            "Error rate increased", Map.of("scenario", "bad_release"));

    @Test
    void skillReturnsExactlyWhatTheSourceProduces() {
        LogSource stub = (ignored, arguments) -> Map.of("matched", 2, "echo", arguments);
        Map<String, Object> result = new QueryLogsSkillHandler(stub)
                .execute(alert, Map.of("query", "level:ERROR"));

        assertThat(result).containsEntry("matched", 2).containsEntry("echo", Map.of("query", "level:ERROR"));
    }

    @Test
    void readingARealFileNeedsNoChangeToTheSkill() throws Exception {
        Path logFile = directory.resolve("app.log");
        Files.writeString(logFile, "2026-10-17T22:00:00 ERROR order-service NoSuchMethodError\n"
                + "2026-10-17T22:00:01 INFO order-service request ok\n");

        LogSource fromRealFile = (ignored, arguments) -> {
            try {
                List<String> lines = Files.readAllLines(logFile).stream()
                        .filter(line -> line.contains("ERROR")).toList();
                return Map.of("matched", lines.size(), "samples", lines);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot read log file", exception);
            }
        };

        Map<String, Object> result = new QueryLogsSkillHandler(fromRealFile)
                .execute(alert, Map.of("query", "level:ERROR"));

        assertThat(result).containsEntry("matched", 1);
        assertThat((List<?>) result.get("samples")).singleElement().asString().contains("NoSuchMethodError");
    }

    @Test
    void simulatedSourcesStillDriveTheScenarioSignals() {
        Map<String, Object> metrics = new SimulatedMetricsSource().query(alert, Map.of("timeRange", "15m"));
        Map<String, Object> logs = new SimulatedLogSource().query(alert, Map.of("query", "level:ERROR"));

        assertThat(metrics).containsEntry("errorRate", 0.183);
        assertThat((List<?>) logs.get("samples")).anySatisfy(
                sample -> assertThat(sample.toString()).contains("NoSuchMethodError"));
    }
}
