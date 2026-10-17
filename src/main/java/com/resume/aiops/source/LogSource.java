package com.resume.aiops.source;

import com.resume.aiops.agent.domain.AlertInput;
import java.util.Map;

/**
 * Where log evidence actually comes from. See {@link MetricsSource} for why this is
 * separate from the Skill that describes it to the model.
 */
public interface LogSource {
    Map<String, Object> query(AlertInput alert, Map<String, Object> arguments);
}
