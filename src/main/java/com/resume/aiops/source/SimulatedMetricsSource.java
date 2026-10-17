package com.resume.aiops.source;

import com.resume.aiops.agent.domain.AlertInput;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic fixtures for the offline demo. Named for what it is: this returns
 * scripted values and must never be described as a real monitoring backend.
 * Replace with an implementation that reads a real source to make the numbers real.
 */
@Component
public class SimulatedMetricsSource implements MetricsSource {
    @Override
    public Map<String, Object> query(AlertInput alert, Map<String, Object> arguments) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        if ("connection_pool".equals(scenario)) {
            return Map.of("service", alert.service(), "dbPoolActive", 50, "dbPoolMax", 50,
                    "requestP95Ms", 4210, "window", arguments.getOrDefault("timeRange", "15m"));
        }
        if ("bad_release".equals(scenario)) {
            return Map.of("service", alert.service(), "errorRate", 0.183, "requestP95Ms", 2870,
                    "window", arguments.getOrDefault("timeRange", "15m"));
        }
        return Map.of("service", alert.service(), "cpuUsage", 0.96, "loadAverage", 8.7,
                "requestP95Ms", 1950, "window", arguments.getOrDefault("timeRange", "15m"));
    }
}
