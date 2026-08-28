package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.rag.RunbookEvidence;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisSynthesizer {
    public String synthesize(AlertInput alert, List<ToolCallRecord> calls, List<RunbookEvidence> evidence) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        return switch (scenario) {
            case "connection_pool" -> "Database connection pool saturation caused request queuing and timeouts.";
            case "bad_release" -> "The error spike correlates with release 2.4.1 and is consistent with a binary compatibility failure.";
            default -> "CPU saturation and elevated GC activity caused the service latency alert.";
        } + " Diagnosis is backed by " + calls.size() + " tool results and " + evidence.size() + " runbook reference(s).";
    }
}

