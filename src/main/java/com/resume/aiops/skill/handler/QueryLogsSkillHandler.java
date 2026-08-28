package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("queryLogsHandler")
public class QueryLogsSkillHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
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

