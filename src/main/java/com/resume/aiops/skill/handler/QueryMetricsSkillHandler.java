package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import com.resume.aiops.source.MetricsSource;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Thin adapter only: the Skill owns the contract the model sees, the source owns
 * where the numbers come from. Swapping in a real backend means adding another
 * MetricsSource implementation, not touching anything here.
 */
@Component("queryMetricsHandler")
public class QueryMetricsSkillHandler implements SkillHandler {
    private final MetricsSource source;

    public QueryMetricsSkillHandler(MetricsSource source) {
        this.source = source;
    }

    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        return source.query(alert, arguments);
    }
}
