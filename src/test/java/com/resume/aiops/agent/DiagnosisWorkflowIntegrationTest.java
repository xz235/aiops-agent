package com.resume.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.agent.domain.TaskStatus;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DiagnosisWorkflowIntegrationTest {
    @Autowired
    DiagnosisTaskService taskService;

    @Test
    void completesBadReleaseDiagnosisAndGeneratesReport() {
        DiagnosisTask created = taskService.create(new AlertInput(
                "Order API error spike",
                "order-service",
                "HIGH",
                "Error rate increased immediately after deployment",
                Map.of("scenario", "bad_release")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            DiagnosisTask completed = taskService.get(created.taskId());
            assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(completed.reportId()).isNotBlank();
            assertThat(completed.conclusion()).contains("release 2.4.1");
            assertThat(completed.plannedSkills()).contains("query_recent_releases");
        });
    }
}

