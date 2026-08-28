package com.resume.aiops.memory.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.memory.AgentMemoryRepository;
import com.resume.aiops.memory.domain.AgentMemoryEntry;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("redis")
public class RedisAgentMemoryRepository extends RedisJsonSupport implements AgentMemoryRepository {
    private final StringRedisTemplate redis;

    public RedisAgentMemoryRepository(ObjectMapper objectMapper, StringRedisTemplate redis) {
        super(objectMapper);
        this.redis = redis;
    }

    @Override
    public void append(AgentMemoryEntry entry) {
        redis.opsForList().rightPush(RedisKeys.memory(entry.taskId()), write(entry));
    }

    @Override
    public List<AgentMemoryEntry> findByTaskId(String taskId) {
        List<String> values = redis.opsForList().range(RedisKeys.memory(taskId), 0, -1);
        return values == null ? List.of() : values.stream()
                .map(value -> read(value, AgentMemoryEntry.class)).toList();
    }
}

