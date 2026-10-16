package com.resume.aiops.agent.domain;

public enum TaskStatus {
    CREATED,
    PLANNING,
    RETRIEVING,
    EXECUTING_TOOLS,
    ANALYZING,
    GENERATING_REPORT,
    COMPLETED,
    NEEDS_HUMAN_REVIEW,
    FAILED,
    CANCELLED
}
