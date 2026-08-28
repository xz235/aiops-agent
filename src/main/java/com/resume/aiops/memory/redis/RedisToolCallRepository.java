package com.resume.aiops.memory.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.memory.ToolCallRepository;
import com.resume.aiops.memory.domain.ToolCallRecord;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("redis")
public class RedisToolCallRepository extends RedisJsonSupport implements ToolCallRepository {
    private final StringRedisTemplate redis;

    public RedisToolCallRepository(ObjectMapper objectMapper, StringRedisTemplate redis) {
        super(objectMapper);
        this.redis = redis;
    }

    @Override
    public void append(ToolCallRecord record) {
        redis.opsForList().rightPush(RedisKeys.toolCalls(record.taskId()), write(record));
    }

    @Override
    public List<ToolCallRecord> findByTaskId(String taskId) {
        List<String> values = redis.opsForList().range(RedisKeys.toolCalls(taskId), 0, -1);
        return values == null ? List.of() : values.stream()
                .map(value -> read(value, ToolCallRecord.class)).toList();
    }
}

