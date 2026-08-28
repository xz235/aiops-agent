package com.resume.aiops.web;

import com.resume.aiops.agent.DiagnosisTaskService;
import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.streaming.SseTaskStream;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {
    private final DiagnosisTaskService taskService;
    private final SseTaskStream taskStream;

    public DiagnosisController(DiagnosisTaskService taskService, SseTaskStream taskStream) {
        this.taskService = taskService;
        this.taskStream = taskStream;
    }

    @PostMapping
    ResponseEntity<DiagnosisTask> create(@Valid @RequestBody AlertInput input) {
        return ResponseEntity.accepted().body(taskService.create(input));
    }

    @GetMapping("/{taskId}")
    DiagnosisTask get(@PathVariable String taskId) {
        return taskService.get(taskId);
    }

    @GetMapping(path = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String taskId,
                      @RequestHeader(name = "Last-Event-ID", defaultValue = "0") long lastEventId) {
        taskService.get(taskId);
        return taskStream.subscribe(taskId, lastEventId);
    }
}

