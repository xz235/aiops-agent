package com.resume.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.rag.RunbookEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosisSynthesizerTest {
    private final DiagnosisSynthesizer synthesizer = new DiagnosisSynthesizer();

    @Test
    void failedRequiredToolPreventsAutonomousConclusion() {
        AlertInput alert = new AlertInput("Error spike", "order-service", "HIGH", "After release",
                Map.of("scenario", "bad_release"));
        List<ToolCallRecord> calls = List.of(
                call("query_service_metrics", true),
                call("query_application_logs", false),
                call("query_recent_releases", true));

        DiagnosisDecision decision = synthesizer.synthesize(alert, calls,
                List.of(new RunbookEvidence("bad-release.md", "Rollback", "Evidence", 0.5)), 0);

        assertThat(decision.reviewRequired()).isTrue();
        assertThat(decision.reasons()).contains("missing successful evidence from query_application_logs");
        assertThat(decision.conclusion()).doesNotContain("binary compatibility failure");
    }

    @Test
    void truncatedPlanRequiresReviewEvenWithAvailableEvidence() {
        AlertInput alert = new AlertInput("CPU spike", "checkout-service", "HIGH", "CPU alert",
                Map.of("scenario", "cpu_spike"));
        DiagnosisDecision decision = synthesizer.synthesize(alert,
                List.of(call("query_service_metrics", true), call("query_application_logs", true)),
                List.of(new RunbookEvidence("cpu-spike.md", "CPU", "Evidence", 0.5)), 1);

        assertThat(decision.reviewRequired()).isTrue();
        assertThat(decision.reasons()).anyMatch(reason -> reason.contains("exceeded the configured limit"));
    }

    @Test
    void successfulButIrrelevantToolResultsRequireReview() {
        AlertInput alert = new AlertInput("CPU spike", "checkout-service", "HIGH", "CPU alert",
                Map.of("scenario", "cpu_spike"));
        DiagnosisDecision decision = synthesizer.synthesize(alert,
                List.of(call("query_service_metrics", true), call("query_application_logs", true)),
                List.of(new RunbookEvidence("cpu-spike.md", "CPU", "Evidence", 0.5)), 0);

        assertThat(decision.reviewRequired()).isTrue();
        assertThat(decision.reasons()).contains("tool results do not contain the expected diagnostic signals for cpu_spike");
    }

    private ToolCallRecord call(String skill, boolean success) {
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        return new ToolCallRecord(skill, "task-1", skill, Map.of(),
                success ? Map.of("value", 1) : Map.of("error", "timeout"), success, 1, now, now);
    }
}
