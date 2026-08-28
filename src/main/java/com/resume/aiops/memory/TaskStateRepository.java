package com.resume.aiops.memory;

import com.resume.aiops.agent.domain.DiagnosisTask;
import java.util.Optional;

public interface TaskStateRepository {
    DiagnosisTask save(DiagnosisTask task);
    Optional<DiagnosisTask> findById(String taskId);
}

