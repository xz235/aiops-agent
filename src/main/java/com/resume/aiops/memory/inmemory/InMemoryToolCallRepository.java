package com.resume.aiops.memory.inmemory;

import com.resume.aiops.memory.ToolCallRepository;
import com.resume.aiops.memory.domain.ToolCallRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!redis")
public class InMemoryToolCallRepository implements ToolCallRepository {
    private final ConcurrentHashMap<String, List<ToolCallRecord>> calls = new ConcurrentHashMap<>();

    @Override
    public void append(ToolCallRecord record) {
        List<ToolCallRecord> taskCalls = calls.computeIfAbsent(record.taskId(), ignored -> new ArrayList<>());
        synchronized (taskCalls) {
            taskCalls.add(record);
        }
    }

    @Override
    public List<ToolCallRecord> findByTaskId(String taskId) {
        List<ToolCallRecord> taskCalls = calls.getOrDefault(taskId, List.of());
        synchronized (taskCalls) {
            return List.copyOf(taskCalls);
        }
    }
}

