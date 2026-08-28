package com.resume.aiops.memory.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.memory.EventJournal;
import com.resume.aiops.memory.domain.AgentEvent;
import com.resume.aiops.memory.domain.AgentEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("redis")
public class RedisEventJournal extends RedisJsonSupport implements EventJournal {
    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisEventJournal(ObjectMapper objectMapper, StringRedisTemplate redis, Clock clock) {
        super(objectMapper);
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public AgentEvent append(String taskId, AgentEventType type, Map<String, Object> payload) {
        Long eventId = redis.opsForValue().increment(RedisKeys.eventSequence(taskId));
        AgentEvent event = new AgentEvent(eventId == null ? 1 : eventId, taskId, type, payload, Instant.now(clock));
        redis.opsForList().rightPush(RedisKeys.events(taskId), write(event));
        return event;
    }

    @Override
    public List<AgentEvent> findAfter(String taskId, long lastEventId) {
        List<String> values = redis.opsForList().range(RedisKeys.events(taskId), 0, -1);
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> read(value, AgentEvent.class))
                .filter(event -> event.eventId() > lastEventId).toList();
    }
}

