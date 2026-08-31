package com.resume.aiops.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.agent.domain.AlertInput;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Adapts a Markdown Skill to Spring AI's native ToolCallback contract.
 * Runtime-owned alert context is supplied through ToolContext so it is not exposed
 * as a model-controlled argument.
 */
public class SpringAiSkillToolCallback implements ToolCallback {
    public static final String ALERT_CONTEXT_KEY = "aiops.alert";

    private final DynamicSkillRegistry.RegisteredSkill skill;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public SpringAiSkillToolCallback(DynamicSkillRegistry.RegisteredSkill skill, ObjectMapper objectMapper) {
        this.skill = skill;
        this.objectMapper = objectMapper;
        this.toolDefinition = ToolDefinition.builder()
                .name(skill.definition().name())
                .description(skill.definition().description())
                .inputSchema(buildInputSchema(skill.definition()))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        throw new IllegalStateException("AIOps Skill requires Runtime ToolContext");
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            Object alertValue = toolContext.getContext().get(ALERT_CONTEXT_KEY);
            AlertInput alert = objectMapper.convertValue(alertValue, AlertInput.class);
            Map<String, Object> arguments = objectMapper.readValue(toolInput, new TypeReference<>() {});
            Map<String, Object> result = skill.handler().execute(alert, arguments);
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Skill callback failed: " + skill.definition().name(), exception);
        }
    }

    private String buildInputSchema(SkillDefinition definition) {
        try {
            Map<String, Object> properties = new LinkedHashMap<>();
            definition.parameters().forEach((name, parameter) -> properties.put(name, Map.of(
                    "type", parameter.type(),
                    "description", parameter.description())));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", definition.parameters().entrySet().stream()
                    .filter(entry -> entry.getValue().required()).map(Map.Entry::getKey).toList());
            schema.put("additionalProperties", false);
            return objectMapper.writeValueAsString(schema);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build tool schema", exception);
        }
    }
}

