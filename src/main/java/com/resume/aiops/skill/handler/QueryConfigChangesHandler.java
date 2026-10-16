package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Simulated configuration audit. A deployment and a config change need different remedies. */
@Component("queryConfigChangesHandler")
public class QueryConfigChangesHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        List<Map<String, Object>> changes = switch (scenario) {
            case "connection_pool" -> List.of(change("db.pool.maximum-pool-size", "80", "50",
                    Instant.now().minusSeconds(7200), "platform-team"));
            default -> List.of(change("jvm.gc.collector", "Parallel", "G1",
                    Instant.now().minusSeconds(259200), "platform-team"));
        };
        boolean recent = "connection_pool".equals(scenario);
        return Map.of("service", alert.service(), "window", arguments.getOrDefault("timeRange", "2h"),
                "recentConfigChange", recent,
                "changeCount", recent ? changes.size() : 0,
                "changes", recent ? changes : List.of());
    }

    private Map<String, Object> change(String key, String previous, String current,
                                       Instant changedAt, String changedBy) {
        return Map.of("key", key, "previousValue", previous, "currentValue", current,
                "changedAt", changedAt.toString(), "changedBy", changedBy);
    }
}
