package com.resume.aiops.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarkdownSkillParser {
    private static final String DELIMITER = "---";
    private final ObjectMapper yamlMapper;

    public MarkdownSkillParser() {
        this.yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
    }

    public SkillDefinition parse(Path path) {
        try {
            String content = Files.readString(path);
            if (!content.startsWith(DELIMITER)) {
                throw new IllegalArgumentException("Skill must begin with YAML front matter: " + path);
            }
            int metadataEnd = content.indexOf(DELIMITER, DELIMITER.length());
            if (metadataEnd < 0) {
                throw new IllegalArgumentException("Unclosed YAML front matter: " + path);
            }
            String yaml = content.substring(DELIMITER.length(), metadataEnd).trim();
            String instructions = content.substring(metadataEnd + DELIMITER.length()).trim();
            Map<String, Object> metadata = yamlMapper.readValue(yaml, new TypeReference<>() {});
            return toDefinition(metadata, instructions, path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot parse skill " + path, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private SkillDefinition toDefinition(Map<String, Object> metadata, String instructions, Path source) {
        Map<String, SkillParameter> parameters = new LinkedHashMap<>();
        Map<String, Object> rawParameters = (Map<String, Object>) metadata.getOrDefault("parameters", Map.of());
        rawParameters.forEach((name, value) -> {
            Map<String, Object> parameter = (Map<String, Object>) value;
            parameters.put(name, new SkillParameter(
                    String.valueOf(parameter.getOrDefault("type", "string")),
                    Boolean.parseBoolean(String.valueOf(parameter.getOrDefault("required", false))),
                    String.valueOf(parameter.getOrDefault("description", ""))));
        });
        return new SkillDefinition(
                required(metadata, "name"),
                required(metadata, "description"),
                String.valueOf(metadata.getOrDefault("version", "1.0.0")),
                SkillRiskLevel.valueOf(String.valueOf(metadata.getOrDefault("riskLevel", "READ_ONLY"))),
                required(metadata, "handler"),
                Long.parseLong(String.valueOf(metadata.getOrDefault("timeoutMs", 5000))),
                parameters,
                instructions,
                source);
    }

    private String required(Map<String, Object> metadata, String name) {
        Object value = metadata.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required skill field: " + name);
        }
        return value.toString();
    }
}

