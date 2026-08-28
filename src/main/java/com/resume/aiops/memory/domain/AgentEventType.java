package com.resume.aiops.memory.domain;

public enum AgentEventType {
    TASK_CREATED,
    STATE_CHANGED,
    PLAN_CREATED,
    RAG_RETRIEVAL,
    MODEL_TOKEN,
    TOOL_STARTED,
    TOOL_PROGRESS,
    TOOL_COMPLETED,
    REPORT_READY,
    TASK_COMPLETED,
    TASK_FAILED,
    HEARTBEAT
}

