package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.AlertInput;

public interface AgentPlanner {
    AgentPlan createPlan(AlertInput alert);
}

