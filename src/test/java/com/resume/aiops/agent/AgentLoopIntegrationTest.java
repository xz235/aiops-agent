package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.*;
import com.resume.aiops.memory.domain.*;
import com.resume.aiops.observability.TaskAuditService;
import java.time.Duration;
import java.util.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Scripted model test double: validates orchestration, not real-model accuracy. */
@SpringBootTest(properties = "aiops.max-agent-steps=3")
class AgentLoopIntegrationTest {
    @Autowired DiagnosisTaskService tasks;
    @Autowired TaskAuditService audit;
    @MockBean AgentDecisionClient model;

    @Test
    void observationsChangeNextActionAndFinalTextComesFromModel() {
        when(model.decide(any())).thenAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            assertThat(context.tools()).extracting("name").contains("query_application_logs");
            if (context.observations().isEmpty()) return tool("query_application_logs", Map.of("query", "level:ERROR"));
            String logs = context.observations().get(0).result().toString();
            if (context.observations().size() == 1) return logs.contains("NoSuchMethodError")
                    ? tool("query_recent_releases", Map.of("timeRange", "2h"))
                    : tool("query_service_metrics", Map.of("timeRange", "15m"));
            return finish(context, "Model conclusion based on observed evidence");
        });
        String release = start("bad_release", "agent");
        await(release, TaskStatus.COMPLETED);
        String cpu = start("cpu_spike", "agent");
        await(cpu, TaskStatus.COMPLETED);
        assertThat(audit.get(release).toolCalls()).extracting("skillName")
                .containsExactly("query_application_logs", "query_recent_releases");
        assertThat(audit.get(cpu).toolCalls()).extracting("skillName")
                .containsExactly("query_application_logs", "query_service_metrics");
        assertThat(tasks.get(release).conclusion()).contains("Model conclusion", "Evidence:");
        assertThat(tasks.get(release).reportId()).isNotBlank();
        assertThat(audit.get(release).memory()).extracting("category").contains("AGENT_ACTION", "DIAGNOSIS");
        assertThat(audit.get(release).events()).extracting("type")
                .contains(AgentEventType.AGENT_DECISION, AgentEventType.DIAGNOSIS_READY)
                .doesNotContain(AgentEventType.MODEL_TOKEN);
    }

    @Test
    void toolFailureIsReturnedToModelForRecovery() {
        when(model.decide(any())).thenAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            if (context.observations().isEmpty()) return tool("unregistered_tool", Map.of());
            assertThat(context.observations().get(0).success()).isFalse();
            if (context.observations().size() == 1) return tool("query_application_logs", Map.of("query", "level:ERROR"));
            return finish(context, "Recovered after rejected tool");
        });
        String id = start("bad_release", "agent");
        await(id, TaskStatus.COMPLETED);
        assertThat(audit.get(id).toolCalls()).extracting("success").containsExactly(false, true);
    }

    @Test
    void fabricatedCitationsCannotCompleteDiagnosis() {
        when(model.decide(any())).thenReturn(new AgentAction("FINAL", null, Map.of(),
                "Unsupported claim", List.of("invented-evidence"), false));
        String id = start("bad_release", "agent");
        await(id, TaskStatus.NEEDS_HUMAN_REVIEW);
        assertThat(tasks.get(id).conclusion()).contains("invalid evidence").doesNotContain("Unsupported claim");
    }

    @Test
    void repeatedCallsStopWithoutExecutingTwice() {
        when(model.decide(any())).thenReturn(tool("query_application_logs", Map.of("query", "level:ERROR")));
        String id = start("bad_release", "agent");
        await(id, TaskStatus.NEEDS_HUMAN_REVIEW);
        assertThat(audit.get(id).toolCalls()).hasSize(1);
        assertThat(tasks.get(id).conclusion()).contains("Repeated");
    }

    @Test
    void budgetStopsUnboundedDistinctCalls() {
        when(model.decide(any())).thenAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            return tool("query_application_logs", Map.of("query", "attempt-" + context.observations().size()));
        });
        String id = start("bad_release", "agent");
        await(id, TaskStatus.NEEDS_HUMAN_REVIEW);
        assertThat(audit.get(id).toolCalls()).hasSize(3);
        assertThat(tasks.get(id).conclusion()).contains("budget exhausted");
    }

    @Test
    void modelFailureProducesReviewReportInsteadOfRuleFallback() {
        when(model.decide(any())).thenThrow(new IllegalStateException("invalid JSON"));
        String id = start("bad_release", "agent");
        await(id, TaskStatus.NEEDS_HUMAN_REVIEW);
        assertThat(tasks.get(id).reportId()).isNotBlank();
        assertThat(audit.get(id).toolCalls()).isEmpty();
    }

    @Test
    void workflowDoesNotInvokeModelEvenWhenAvailable() {
        String id = start("bad_release", "workflow");
        await(id, TaskStatus.COMPLETED);
        verifyNoInteractions(model);
        assertThat(audit.get(id).toolCalls()).hasSize(3);
    }

    private String start(String scenario, String mode) {
        return tasks.create(new AlertInput("Experiment failed", "test-service", "HIGH", "Exit code 1",
                Map.of("scenario", scenario, "mode", mode))).taskId();
    }

    private void await(String id, TaskStatus status) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(tasks.get(id).status()).isEqualTo(status));
    }

    private AgentAction tool(String name, Map<String, Object> args) {
        return new AgentAction("TOOL", name, args, null, List.of(), false);
    }

    private AgentAction finish(AgentContext context, String text) {
        List<String> refs = new ArrayList<>();
        context.observations().stream().filter(ToolCallRecord::success).forEach(call -> refs.add(call.callId()));
        refs.add(context.runbooks().get(0).documentId());
        return new AgentAction("FINAL", null, Map.of(), text, refs, false);
    }
}
