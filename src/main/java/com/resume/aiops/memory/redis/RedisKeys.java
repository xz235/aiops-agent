package com.resume.aiops.memory.redis;

final class RedisKeys {
    private static final String PREFIX = "aiops:";

    private RedisKeys() {
    }

    static String task(String taskId) { return PREFIX + "task:" + taskId + ":state"; }
    static String taskVersion(String taskId) { return PREFIX + "task:" + taskId + ":version"; }
    static String events(String taskId) { return PREFIX + "task:" + taskId + ":events"; }
    static String eventSequence(String taskId) { return PREFIX + "task:" + taskId + ":event-seq"; }
    static String toolCalls(String taskId) { return PREFIX + "task:" + taskId + ":tool-calls"; }
    static String memory(String taskId) { return PREFIX + "task:" + taskId + ":memory"; }
}

