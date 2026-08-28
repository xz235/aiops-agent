package com.resume.aiops.agent;

import com.resume.aiops.agent.AgentPlan.PlannedSkillCall;
import com.resume.aiops.agent.domain.AlertInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!llm")
public class MockAgentPlanner implements AgentPlanner {
    @Override
    public AgentPlan createPlan(AlertInput alert) {
        List<PlannedSkillCall> calls = new ArrayList<>();
        calls.add(new PlannedSkillCall("query_service_metrics", Map.of("timeRange", "15m")));
        calls.add(new PlannedSkillCall("query_application_logs", Map.of("query", "level:ERROR")));
        if ("bad_release".equals(alert.labels().get("scenario"))) {
            calls.add(new PlannedSkillCall("query_recent_releases", Map.of("timeRange", "2h")));
        }
        return new AgentPlan("Collect metrics and logs, then correlate recent changes.", calls);
    }
}

