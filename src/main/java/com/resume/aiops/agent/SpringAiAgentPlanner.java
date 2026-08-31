package com.resume.aiops.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.agent.domain.AlertInput;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("llm")
public class SpringAiAgentPlanner implements AgentPlanner {
    private static final String SYSTEM_PROMPT = """
            You are an AIOps diagnosis planner. Return JSON only.
            Select the minimum useful sequence from these Skills:
            query_service_metrics, query_application_logs, query_recent_releases.
            The response schema is:
            {"summary":"string","calls":[{"skillName":"string","arguments":{}}]}.
            Metrics uses {"timeRange":"15m"}; logs uses {"query":"level:ERROR"};
            releases uses {"timeRange":"2h"}. Never invent another Skill.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiAgentPlanner(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.create(chatModel);
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentPlan createPlan(AlertInput alert) {
        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Alert: " + write(alert))
                .call()
                .content();
        try {
            return objectMapper.readValue(stripFence(response), AgentPlan.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Model returned an invalid AgentPlan", exception);
        }
    }

    private String write(AlertInput alert) {
        try {
            return objectMapper.writeValueAsString(alert);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize alert", exception);
        }
    }

    private String stripFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine > 0 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}

