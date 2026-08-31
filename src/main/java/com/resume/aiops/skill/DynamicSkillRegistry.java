package com.resume.aiops.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.config.AiOpsProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class DynamicSkillRegistry implements ToolCallbackProvider {
    private final AiOpsProperties properties;
    private final MarkdownSkillParser parser;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<String, RegisteredSkill>> registry = new AtomicReference<>(Map.of());

    public DynamicSkillRegistry(AiOpsProperties properties, MarkdownSkillParser parser,
                                ApplicationContext applicationContext, ObjectMapper objectMapper) {
        this.properties = properties;
        this.parser = parser;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() throws IOException {
        reload();
    }

    public synchronized List<SkillDefinition> reload() throws IOException {
        Path directory = properties.skillsDirectory();
        Files.createDirectories(directory);
        Map<String, RegisteredSkill> next = new LinkedHashMap<>();
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(parser::parse)
                    .forEach(definition -> register(next, definition));
        }
        registry.set(Map.copyOf(next));
        return definitions();
    }

    public List<SkillDefinition> definitions() {
        return registry.get().values().stream().map(RegisteredSkill::definition).toList();
    }

    public RegisteredSkill require(String name) {
        RegisteredSkill registeredSkill = registry.get().get(name);
        if (registeredSkill == null) {
            throw new NoSuchElementException("Unknown skill: " + name);
        }
        return registeredSkill;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return registry.get().values().stream()
                .map(skill -> new SpringAiSkillToolCallback(skill, objectMapper))
                .toArray(ToolCallback[]::new);
    }

    private void register(Map<String, RegisteredSkill> next, SkillDefinition definition) {
        if (next.containsKey(definition.name())) {
            throw new IllegalArgumentException("Duplicate skill name: " + definition.name());
        }
        SkillHandler handler;
        try {
            handler = applicationContext.getBean(definition.handler(), SkillHandler.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown SkillHandler bean: " + definition.handler(), exception);
        }
        next.put(definition.name(), new RegisteredSkill(definition, handler));
    }

    public record RegisteredSkill(SkillDefinition definition, SkillHandler handler) {
    }
}
