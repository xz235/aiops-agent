package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import com.resume.aiops.source.LogSource;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Thin adapter only; see QueryMetricsSkillHandler. */
@Component("queryLogsHandler")
public class QueryLogsSkillHandler implements SkillHandler {
    private final LogSource source;

    public QueryLogsSkillHandler(LogSource source) {
        this.source = source;
    }

    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        return source.query(alert, arguments);
    }
}
