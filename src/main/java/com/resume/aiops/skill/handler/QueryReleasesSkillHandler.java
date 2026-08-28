package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component("queryReleasesHandler")
public class QueryReleasesSkillHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        boolean changed = "bad_release".equals(alert.labels().get("scenario"));
        return Map.of("service", alert.service(), "recentChange", changed,
                "version", changed ? "2.4.1" : "2.4.0",
                "deployedAt", Instant.now().minusSeconds(changed ? 420 : 86400).toString());
    }
}

