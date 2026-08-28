package com.resume.aiops.agent;

import java.util.List;
import java.util.Map;

public record AgentPlan(String summary, List<PlannedSkillCall> calls) {
    public AgentPlan {
        calls = calls == null ? List.of() : List.copyOf(calls);
    }

    public record PlannedSkillCall(String skillName, Map<String, Object> arguments) {
        public PlannedSkillCall {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }
}

