package com.resume.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.agent.domain.TaskStatus;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/** The model wait was previously hard-coded to 60s, so it could not be tuned or tested. */
@SpringBootTest(properties = "aiops.agent-decision-timeout=150ms")
class AgentDecisionTimeoutTest {
    @Autowired
    DiagnosisTaskService tasks;
    @MockBean
    AgentDecisionClient model;

    @Test
    void slowModelDecisionIsCutOffByTheConfiguredTimeout() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(10_000);
            return null;
        }).when(model).decide(any());

        String id = tasks.create(new AlertInput("Order API error spike", "order-service", "HIGH",
                "Error rate increased", Map.of("scenario", "bad_release", "mode", "agent"))).taskId();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(tasks.get(id).status()).isEqualTo(TaskStatus.NEEDS_HUMAN_REVIEW));
        assertThat(tasks.get(id).conclusion()).contains("Model unavailable");
    }
}
