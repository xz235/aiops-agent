package com.resume.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.agent.domain.TaskStatus;
import java.time.Duration;
import java.util.Map;
import com.resume.aiops.observability.TaskAuditService;
import com.resume.aiops.observability.TaskAuditView;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class DiagnosisWorkflowIntegrationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc http;

    @Test
    void exposesModeSelectorAndReturnsUsefulApiErrorWithoutModel() throws Exception {
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/index.html"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"mode\"")));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/diagnoses")
                .contentType("application/json")
                .content("{\"title\":\"Failure\",\"service\":\"test\",\"severity\":\"HIGH\",\"labels\":{\"mode\":\"agent\"}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("model configuration")));
    }
    @Test
    void agentModeWithoutModelIsRejectedBeforeCreatingTask() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> taskService.create(new AlertInput(
                "Failure", "test", "HIGH", "Exit 1", Map.of("mode", "agent"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("model configuration");
    }
    @Autowired
    DiagnosisTaskService taskService;
    @Autowired
    TaskAuditService auditService;

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
            TaskAuditView audit = auditService.get(created.taskId());
            assertThat(audit.memory()).extracting("category")
                    .contains("RAG_EVIDENCE", "DIAGNOSIS");
            assertThat(audit.toolCalls()).hasSize(3).allMatch(call -> call.success());
            assertThat(audit.events()).isNotEmpty();
        });
    }
}
