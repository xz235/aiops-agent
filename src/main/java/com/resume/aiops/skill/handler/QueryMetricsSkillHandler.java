package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("queryMetricsHandler")
public class QueryMetricsSkillHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
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

