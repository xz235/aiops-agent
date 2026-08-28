package com.resume.aiops.streaming;

import com.resume.aiops.config.AiOpsProperties;
import com.resume.aiops.memory.EventJournal;
import com.resume.aiops.memory.domain.AgentEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseTaskStream {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final EventJournal journal;
    private final AiOpsProperties properties;

    public SseTaskStream(EventJournal journal, AiOpsProperties properties) {
        this.journal = journal;
        this.properties = properties;
    }

    public SseEmitter subscribe(String taskId, long lastEventId) {
        SseEmitter emitter = new SseEmitter(properties.sseTimeout().toMillis());
        CopyOnWriteArrayList<SseEmitter> taskEmitters = emitters.computeIfAbsent(taskId,
                ignored -> new CopyOnWriteArrayList<>());
        taskEmitters.add(emitter);
        Runnable cleanup = () -> taskEmitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        for (AgentEvent event : journal.findAfter(taskId, lastEventId)) {
            if (!send(emitter, event)) {
                break;
            }
        }
        return emitter;
    }

    public void broadcast(AgentEvent event) {
        List<SseEmitter> taskEmitters = emitters.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>());
        taskEmitters.removeIf(emitter -> !send(emitter, event));
    }

    private boolean send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(event.eventId()))
                    .name(event.type().name().toLowerCase())
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException exception) {
            emitter.completeWithError(exception);
            return false;
        }
    }
}

