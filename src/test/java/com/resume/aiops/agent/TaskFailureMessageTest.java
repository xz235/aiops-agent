package com.resume.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.resume.aiops.agent.domain.AlertInput;
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
import org.springframework.boot.test.mock.mockito.MockBean;

/** A failure with no message used to be persisted as null, leaving the audit trail without a cause. */
@SpringBootTest
class TaskFailureMessageTest {
    @Autowired
    DiagnosisTaskService tasks;
    @Autowired
    TaskAuditService audit;
    @MockBean
    PdfReportService reports;

    @Test
    void exceptionWithoutMessageStillRecordsAReadableCause() {
        when(reports.generate(any(), any(), any(), anyString(), anyBoolean()))
                .thenThrow(new IllegalStateException());

        String id = tasks.create(new AlertInput("Order API error spike", "order-service", "HIGH",
                "Error rate increased", Map.of("scenario", "bad_release"))).taskId();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(tasks.get(id).status()).isEqualTo(TaskStatus.FAILED));
        assertThat(tasks.get(id).errorMessage()).isNotBlank();

        var failureEvent = audit.get(id).events().stream()
                .filter(event -> event.type() == AgentEventType.TASK_FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(failureEvent.payload()).containsEntry("message", tasks.get(id).errorMessage());
    }
}
