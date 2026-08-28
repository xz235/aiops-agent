package com.resume.aiops.memory.inmemory;

import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.memory.TaskStateRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!redis")
public class InMemoryTaskStateRepository implements TaskStateRepository {
    private final ConcurrentHashMap<String, DiagnosisTask> tasks = new ConcurrentHashMap<>();

    @Override
    public DiagnosisTask save(DiagnosisTask task) {
        tasks.compute(task.taskId(), (id, current) -> {
            if (current != null && task.version() < current.version()) {
                throw new IllegalStateException("Stale task version for " + id);
            }
            return task;
        });
        return task;
    }

    @Override
    public Optional<DiagnosisTask> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }
}

