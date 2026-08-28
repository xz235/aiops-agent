package com.resume.aiops.skill;

import com.resume.aiops.agent.domain.AlertInput;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SkillExecutor {
    private final DynamicSkillRegistry registry;

    public SkillExecutor(DynamicSkillRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> execute(String name, AlertInput alert, Map<String, Object> arguments) {
        DynamicSkillRegistry.RegisteredSkill skill = registry.require(name);
        validate(skill.definition(), arguments);
        return skill.handler().execute(alert, Map.copyOf(arguments));
    }

    private void validate(SkillDefinition definition, Map<String, Object> arguments) {
        Map<String, String> errors = new LinkedHashMap<>();
        definition.parameters().forEach((name, parameter) -> {
            if (parameter.required() && !arguments.containsKey(name)) {
                errors.put(name, "required");
            }
        });
        arguments.keySet().stream()
                .filter(name -> !definition.parameters().containsKey(name))
                .forEach(name -> errors.put(name, "unknown"));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments for " + definition.name() + ": " + errors);
        }
    }
}

