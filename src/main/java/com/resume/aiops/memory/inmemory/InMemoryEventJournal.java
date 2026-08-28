package com.resume.aiops.memory.inmemory;

import com.resume.aiops.memory.EventJournal;
import com.resume.aiops.memory.domain.AgentEvent;
import com.resume.aiops.memory.domain.AgentEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!redis")
public class InMemoryEventJournal implements EventJournal {
    private final ConcurrentHashMap<String, List<AgentEvent>> events = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryEventJournal(Clock clock) {
        this.clock = clock;
    }

    @Override
    public AgentEvent append(String taskId, AgentEventType type, Map<String, Object> payload) {
        long id = sequences.computeIfAbsent(taskId, ignored -> new AtomicLong()).incrementAndGet();
        AgentEvent event = new AgentEvent(id, taskId, type, payload, Instant.now(clock));
        events.computeIfAbsent(taskId, ignored -> new ArrayList<>());
        synchronized (events.get(taskId)) {
            events.get(taskId).add(event);
        }
        return event;
    }

    @Override
    public List<AgentEvent> findAfter(String taskId, long lastEventId) {
        List<AgentEvent> taskEvents = events.getOrDefault(taskId, List.of());
        synchronized (taskEvents) {
            return taskEvents.stream().filter(event -> event.eventId() > lastEventId).toList();
        }
    }
}

