package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Simulated dependency health. Distinguishes a local bottleneck from an inherited one. */
@Component("queryDependencyHealthHandler")
public class QueryDependencyHealthHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        boolean degraded = "connection_pool".equals(scenario);
        List<Map<String, Object>> dependencies = switch (scenario) {
            case "connection_pool" -> List.of(
                    dependency("orders-db", "DEGRADED", 0.021, 480, "CLOSED", true),
                    dependency("pricing-service", "HEALTHY", 0.001, 95, "CLOSED", true));
            case "bad_release" -> List.of(
                    dependency("orders-db", "HEALTHY", 0.002, 60, "CLOSED", true),
                    dependency("pricing-service", "HEALTHY", 0.003, 110, "CLOSED", true));
            default -> List.of(
                    dependency("orders-db", "HEALTHY", 0.001, 55, "CLOSED", true),
                    dependency("pricing-service", "HEALTHY", 0.002, 90, "CLOSED", true));
        };
        return Map.of("service", alert.service(), "window", arguments.getOrDefault("timeRange", "15m"),
                "degradedDownstream", degraded,
                "dependencyServersHealthy", true,
                "dependencyCount", dependencies.size(),
                "dependencies", dependencies);
    }

    private Map<String, Object> dependency(String name, String status, double errorRate,
                                           int p99Ms, String circuit, boolean serverHealthy) {
        return Map.of("name", name, "status", status, "errorRate", errorRate,
                "p99Ms", p99Ms, "circuitState", circuit, "serverHealthy", serverHealthy);
    }
}
