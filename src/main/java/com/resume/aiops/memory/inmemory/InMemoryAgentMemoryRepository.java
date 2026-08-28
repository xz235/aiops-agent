package com.resume.aiops.memory.inmemory;

import com.resume.aiops.memory.AgentMemoryRepository;
import com.resume.aiops.memory.domain.AgentMemoryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!redis")
public class InMemoryAgentMemoryRepository implements AgentMemoryRepository {
    private final ConcurrentHashMap<String, List<AgentMemoryEntry>> memory = new ConcurrentHashMap<>();

    @Override
    public void append(AgentMemoryEntry entry) {
        List<AgentMemoryEntry> entries = memory.computeIfAbsent(entry.taskId(), ignored -> new ArrayList<>());
        synchronized (entries) {
            entries.add(entry);
        }
    }

    @Override
    public List<AgentMemoryEntry> findByTaskId(String taskId) {
        List<AgentMemoryEntry> entries = memory.getOrDefault(taskId, List.of());
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }
}

