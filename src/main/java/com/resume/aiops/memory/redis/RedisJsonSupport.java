package com.resume.aiops.memory.redis;

import com.fasterxml.jackson.databind.ObjectMapper;

abstract class RedisJsonSupport {
    protected final ObjectMapper objectMapper;

    RedisJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize Redis value", exception);
        }
    }

    protected <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot deserialize Redis value", exception);
        }
    }
}

