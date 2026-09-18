package com.resume.aiops.web;

import com.resume.aiops.observability.TaskAuditService;
import com.resume.aiops.observability.TaskAuditView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnoses")
public class AuditController {
    private final TaskAuditService auditService;

    public AuditController(TaskAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{taskId}/audit")
    TaskAuditView get(@PathVariable String taskId) {
        return auditService.get(taskId);
    }
}

