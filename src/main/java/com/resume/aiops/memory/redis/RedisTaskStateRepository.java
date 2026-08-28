package com.resume.aiops.memory.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.memory.TaskStateRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@Profile("redis")
public class RedisTaskStateRepository extends RedisJsonSupport implements TaskStateRepository {
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[2])
            if current and tonumber(ARGV[1]) < tonumber(current) then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[2])
            redis.call('SET', KEYS[2], ARGV[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;

    public RedisTaskStateRepository(ObjectMapper objectMapper, StringRedisTemplate redis) {
        super(objectMapper);
        this.redis = redis;
    }

    @Override
    public DiagnosisTask save(DiagnosisTask task) {
        Long saved = redis.execute(SAVE_SCRIPT,
                List.of(RedisKeys.task(task.taskId()), RedisKeys.taskVersion(task.taskId())),
                Long.toString(task.version()), write(task));
        if (saved == null || saved == 0) {
            throw new IllegalStateException("Stale task version for " + task.taskId());
        }
        return task;
    }

    @Override
    public Optional<DiagnosisTask> findById(String taskId) {
        return Optional.ofNullable(redis.opsForValue().get(RedisKeys.task(taskId)))
                .map(value -> read(value, DiagnosisTask.class));
    }
}

