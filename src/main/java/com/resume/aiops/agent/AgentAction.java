package com.resume.aiops.agent;

import java.util.List;
import java.util.Map;

/** One externally visible action, never a hidden reasoning trace. */
public record AgentAction(String type, String skillName, Map<String, Object> arguments,
                          String conclusion, List<String> evidenceIds, boolean reviewRequired) {
    public AgentAction {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        if (!"TOOL".equals(type) && !"FINAL".equals(type)) {
            throw new IllegalArgumentException("Action type must be TOOL or FINAL");
        }
        if ("TOOL".equals(type) && (skillName == null || skillName.isBlank())) {
            throw new IllegalArgumentException("TOOL action requires skillName");
        }
        if ("FINAL".equals(type) && (conclusion == null || conclusion.isBlank())) {
            throw new IllegalArgumentException("FINAL action requires conclusion");
        }
    }
}
