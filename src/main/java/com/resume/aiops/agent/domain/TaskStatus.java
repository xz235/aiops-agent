package com.resume.aiops.agent.domain;

public enum TaskStatus {
    CREATED,
    PLANNING,
    RETRIEVING,
    EXECUTING_TOOLS,
    ANALYZING,
    GENERATING_REPORT,
    COMPLETED,
    FAILED,
    CANCELLED
}

