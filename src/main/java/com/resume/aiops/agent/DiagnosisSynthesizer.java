package com.resume.aiops.agent;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.rag.RunbookEvidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisSynthesizer {
    public DiagnosisDecision synthesize(AlertInput alert, List<ToolCallRecord> calls,
                                        List<RunbookEvidence> evidence, int omittedSteps) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", ""));
        List<String> reasons = new ArrayList<>();
        if (!Set.of("cpu_spike", "connection_pool", "bad_release").contains(scenario)) {
            reasons.add("no supported diagnostic scenario was identified");
        }
        if (omittedSteps > 0) {
            reasons.add(omittedSteps + " planned tool step(s) exceeded the configured limit");
        }
        String expectedRunbook = switch (scenario) {
            case "bad_release" -> "bad-release.md";
            case "connection_pool" -> "connection-pool.md";
            case "cpu_spike" -> "cpu-spike.md";
            default -> "";
        };
        if (!expectedRunbook.isEmpty() && evidence.stream()
                .noneMatch(item -> expectedRunbook.equals(item.documentId()))) {
            reasons.add("no matching runbook was found for " + scenario);
        }
        List<String> requiredSkills = new ArrayList<>(List.of("query_service_metrics", "query_application_logs"));
        if ("bad_release".equals(scenario)) {
            requiredSkills.add("query_recent_releases");
        }
        for (String skill : requiredSkills) {
            if (calls.stream().noneMatch(call -> skill.equals(call.skillName())
                    && call.success() && !call.result().isEmpty())) {
                reasons.add("missing successful evidence from " + skill);
            }
        }
        if (reasons.isEmpty() && !hasScenarioSignals(scenario, calls)) {
            reasons.add("tool results do not contain the expected diagnostic signals for " + scenario);
        }
        if (!reasons.isEmpty()) {
            return new DiagnosisDecision("Evidence is incomplete; the root cause requires human review. "
                    + String.join("; ", reasons) + ".", true, reasons);
        }
        String conclusion = switch (scenario) {
            case "connection_pool" -> "Database connection pool saturation caused request queuing and timeouts.";
            case "bad_release" -> "The error spike correlates with release 2.4.1 and is consistent with a binary compatibility failure.";
            default -> "CPU saturation and elevated GC activity caused the service latency alert.";
        } + " Diagnosis is backed by " + calls.size() + " tool results and " + evidence.size() + " runbook reference(s).";
        return new DiagnosisDecision(conclusion, false, List.of());
    }

    private boolean hasScenarioSignals(String scenario, List<ToolCallRecord> calls) {
        Map<String, Object> metrics = resultOf(calls, "query_service_metrics");
        Map<String, Object> logs = resultOf(calls, "query_application_logs");
        String samples = String.valueOf(logs.getOrDefault("samples", ""));
        return switch (scenario) {
            case "bad_release" -> number(metrics.get("errorRate")) >= 0.05
                    && samples.contains("NoSuchMethodError")
                    && Boolean.TRUE.equals(resultOf(calls, "query_recent_releases").get("recentChange"));
            case "connection_pool" -> number(metrics.get("dbPoolMax")) > 0
                    && number(metrics.get("dbPoolActive")) >= number(metrics.get("dbPoolMax"))
                    && samples.contains("connection pool exhausted");
            case "cpu_spike" -> number(metrics.get("cpuUsage")) >= 0.9
                    && samples.contains("GC overhead");
            default -> false;
        };
    }

    private Map<String, Object> resultOf(List<ToolCallRecord> calls, String skill) {
        return calls.stream().filter(call -> skill.equals(call.skillName()) && call.success())
                .map(ToolCallRecord::result).findFirst().orElse(Map.of());
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
