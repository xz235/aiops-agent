package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.rag.RunbookEvidence;
import com.resume.aiops.skill.SkillDefinition;
import java.util.List;

public record AgentContext(AlertInput alert, List<SkillDefinition> tools,
                           List<RunbookEvidence> runbooks, List<ToolCallRecord> observations,
                           int remainingToolCalls) {
    public AgentContext {
        tools = List.copyOf(tools);
        runbooks = List.copyOf(runbooks);
        observations = List.copyOf(observations);
    }
}
