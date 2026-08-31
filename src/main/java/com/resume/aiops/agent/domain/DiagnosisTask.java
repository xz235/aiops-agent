package com.resume.aiops.agent.domain;

import java.time.Instant;
import java.util.List;

public record DiagnosisTask(
        String taskId,
        long version,
        TaskStatus status,
        AlertInput alert,
        List<String> plannedSkills,
        String conclusion,
        String reportId,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public DiagnosisTask {
        plannedSkills = plannedSkills == null ? List.of() : List.copyOf(plannedSkills);
    }

    public static DiagnosisTask create(String taskId, AlertInput alert, Instant now) {
        return new DiagnosisTask(taskId, 1, TaskStatus.CREATED, alert, List.of(), null, null, null, now, now);
    }

    public DiagnosisTask transition(TaskStatus next, Instant now) {
        return new DiagnosisTask(taskId, version + 1, next, alert, plannedSkills,
                conclusion, reportId, errorMessage, createdAt, now);
    }

    public DiagnosisTask withPlan(List<String> skills, Instant now) {
        return new DiagnosisTask(taskId, version + 1, TaskStatus.PLANNING, alert, skills,
                conclusion, reportId, errorMessage, createdAt, now);
    }

    public DiagnosisTask complete(String result, String generatedReportId, Instant now) {
        return new DiagnosisTask(taskId, version + 1, TaskStatus.COMPLETED, alert, plannedSkills,
                result, generatedReportId, null, createdAt, now);
    }

    public DiagnosisTask fail(String message, Instant now) {
        return new DiagnosisTask(taskId, version + 1, TaskStatus.FAILED, alert, plannedSkills,
                conclusion, reportId, message, createdAt, now);
    }
}
