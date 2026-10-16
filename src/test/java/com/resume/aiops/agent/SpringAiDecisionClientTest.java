package com.resume.aiops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.skill.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SpringAiDecisionClientTest {
    @Test
    void sendsToolCatalogAndPriorObservationsAndParsesOneAction() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"type\":\"TOOL\",\"skillName\":\"query_application_logs\",\"arguments\":{\"query\":\"ERROR\"}}")))));
        var client = new SpringAiDecisionClient(model, new ObjectMapper().findAndRegisterModules());
        Instant now = Instant.now();
        AgentAction action = client.decide(new AgentContext(
                new AlertInput("Failure", "test", "HIGH", "Exit 1", Map.of()),
                List.of(new SkillDefinition("query_application_logs", "Read logs", "1", SkillRiskLevel.READ_ONLY,
                        "handler", 3000, Map.of("query", new SkillParameter("string", true, "Filter")),
                        "Read only", Path.of("skills/logs.md"))), List.of(),
                List.of(new ToolCallRecord("call-1", "task", "query_service_metrics", Map.of(),
                        Map.of("error", "timeout"), false, 10, now, now)), 2));
        assertThat(action.type()).isEqualTo("TOOL");
        assertThat(action.arguments()).containsEntry("query", "ERROR");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("call-1", "timeout", "query_application_logs", "remainingToolCalls");
    }

    @Test
    void malformedModelOutputIsRejected() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("not JSON")))));
        var client = new SpringAiDecisionClient(model, new ObjectMapper());
        assertThatThrownBy(() -> client.decide(new AgentContext(
                new AlertInput("Failure", "test", "HIGH", "Exit 1", Map.of()), List.of(), List.of(), List.of(), 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid JSON");
    }
}
