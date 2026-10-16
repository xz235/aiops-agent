package com.resume.aiops.agent;

public interface AgentDecisionClient {
    AgentAction decide(AgentContext context);
}
