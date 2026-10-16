package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Simulated instance lifecycle. Rules infrastructure causes in or out. */
@Component("queryContainerEventsHandler")
public class QueryContainerEventsHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        boolean throttled = "cpu_spike".equals(scenario);
        List<Map<String, Object>> events = throttled
                ? List.of(Map.of("type", "Warning", "reason", "CPUThrottled", "count", 34,
                        "message", "Cgroup CPU quota exceeded on 6 of 12 replicas"))
                : List.of(Map.of("type", "Normal", "reason", "Running", "count", 12,
                        "message", "All replicas running with no restarts in the window"));
        return Map.of("service", alert.service(), "window", arguments.getOrDefault("timeRange", "15m"),
                "replicaCount", 12,
                "restartCount", 0,
                "oomKilled", false,
                "evicted", false,
                "cpuThrottled", throttled,
                "eventCount", events.size(),
                "events", events);
    }
}
