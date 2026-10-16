package com.resume.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.agent.domain.TaskStatus;
import com.resume.aiops.memory.domain.AgentEventType;
import com.resume.aiops.observability.TaskAuditService;
import com.resume.aiops.report.PdfReportService;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "aiops.runbooks-directory=target/test-empty-review-runbooks")
class DiagnosisReviewIntegrationTest {
    @Autowired
    DiagnosisTaskService taskService;
    @Autowired
    TaskAuditService auditService;
    @Autowired
    PdfReportService reportService;

    @Test
    void retainsReportAndAuditWhenNoRunbookSupportsAutonomousDiagnosis() {
        DiagnosisTask created = taskService.create(new AlertInput(
                "Order API error spike", "order-service", "HIGH",
                "Error rate increased after deployment", Map.of("scenario", "bad_release")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            DiagnosisTask review = taskService.get(created.taskId());
            assertThat(review.status()).isEqualTo(TaskStatus.NEEDS_HUMAN_REVIEW);
            assertThat(review.reportId()).isNotBlank();
            assertThat(reportService.load(review.reportId()).exists()).isTrue();
            assertThat(review.conclusion()).contains("requires human review", "no matching runbook");
            var audit = auditService.get(created.taskId());
            assertThat(audit.toolCalls()).hasSize(3).allMatch(call -> call.success());
            assertThat(audit.events()).extracting("type").contains(AgentEventType.TASK_REVIEW_REQUIRED);
        });
    }
}
