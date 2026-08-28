package com.resume.aiops.streaming;

import com.resume.aiops.memory.EventJournal;
import com.resume.aiops.memory.domain.AgentEvent;
import com.resume.aiops.memory.domain.AgentEventType;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentEventPublisher {
    private final EventJournal eventJournal;
    private final SseTaskStream taskStream;

    public AgentEventPublisher(EventJournal eventJournal, SseTaskStream taskStream) {
        this.eventJournal = eventJournal;
        this.taskStream = taskStream;
    }

    public AgentEvent publish(String taskId, AgentEventType type, Map<String, Object> payload) {
        AgentEvent event = eventJournal.append(taskId, type, payload);
        taskStream.broadcast(event);
        return event;
    }
}

