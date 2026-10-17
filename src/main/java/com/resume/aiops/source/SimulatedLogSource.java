package com.resume.aiops.source;

import com.resume.aiops.agent.domain.AlertInput;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic fixtures for the offline demo. Named for what it is: samples are
 * scripted, not read from a real log store.
 */
@Component
public class SimulatedLogSource implements LogSource {
    @Override
    public Map<String, Object> query(AlertInput alert, Map<String, Object> arguments) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        List<String> samples = switch (scenario) {
            case "connection_pool" -> List.of("HikariPool-1 - Connection is not available, request timed out",
                    "SQLTransientConnectionException: connection pool exhausted");
            case "bad_release" -> List.of("NoSuchMethodError after deployment version 2.4.1",
                    "HTTP 500 /orders/submit traceId=demo-101");
            default -> List.of("GC overhead limit approaching threshold", "worker-17 processed 12000 records");
        };
        return Map.of("service", alert.service(), "query", arguments.getOrDefault("query", "ERROR"),
                "matched", samples.size(), "samples", samples);
    }
}
