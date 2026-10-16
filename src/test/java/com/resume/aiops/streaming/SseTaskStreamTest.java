package com.resume.aiops.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resume.aiops.config.AiOpsProperties;
import com.resume.aiops.memory.EventJournal;
import com.resume.aiops.memory.domain.AgentEvent;
import com.resume.aiops.memory.domain.AgentEventType;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseTaskStreamTest {
    private final EventJournal journal = mock(EventJournal.class);
    private final SseTaskStream stream = new SseTaskStream(journal, properties());

    @Test
    void replayReadsTheJournalOnceSoTerminalDetectionMatchesWhatWasSent() {
        when(journal.findAfter("task-1", 0L))
                .thenReturn(List.of(event("task-1", 1, AgentEventType.STATE_CHANGED)));

        stream.subscribe("task-1", 0L);

        verify(journal, times(1)).findAfter("task-1", 0L);
    }

    @Test
    void terminalBroadcastReleasesTheTaskEntry() {
        when(journal.findAfter(anyString(), anyLong())).thenReturn(List.of());
        stream.subscribe("task-2", 0L);
        assertThat(emitters()).containsKey("task-2");

        stream.broadcast(event("task-2", 9, AgentEventType.TASK_COMPLETED));

        assertThat(emitters()).doesNotContainKey("task-2");
    }

    @Test
    void lastSubscriberLeavingReleasesTheTaskEntry() {
        when(journal.findAfter(anyString(), anyLong())).thenReturn(List.of());
        SseEmitter first = stream.subscribe("task-3", 0L);
        SseEmitter second = stream.subscribe("task-3", 0L);
        assertThat(emitters().get("task-3")).hasSize(2);

        stream.deregister("task-3", first);
        assertThat(emitters().get("task-3")).hasSize(1);

        stream.deregister("task-3", second);
        assertThat(emitters()).doesNotContainKey("task-3");
    }

    @Test
    void reviewRequiredIsTerminalSoClientsAreNotLeftHanging() {
        when(journal.findAfter(anyString(), anyLong())).thenReturn(List.of());
        stream.subscribe("task-4", 0L);

        stream.broadcast(event("task-4", 4, AgentEventType.TASK_REVIEW_REQUIRED));

        assertThat(emitters()).doesNotContainKey("task-4");
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<SseEmitter>> emitters() {
        return (Map<String, List<SseEmitter>>) ReflectionTestUtils.getField(stream, "emitters");
    }

    private AgentEvent event(String taskId, long id, AgentEventType type) {
        return new AgentEvent(id, taskId, type, Map.of(), Instant.now());
    }

    private AiOpsProperties properties() {
        return new AiOpsProperties(Path.of("skills"), Path.of("runbooks"), Path.of("reports"),
                Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofSeconds(60), 8);
    }
}
