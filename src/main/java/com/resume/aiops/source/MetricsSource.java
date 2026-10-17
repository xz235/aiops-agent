package com.resume.aiops.source;

import com.resume.aiops.agent.domain.AlertInput;
import java.util.Map;

/**
 * Where saturation numbers actually come from. This is the "just query it" layer:
 * a plain call with no Skill, schema or model involvement. Implementations may read
 * /proc, an MCP server, a metrics backend, or return fixtures for the offline demo.
 */
public interface MetricsSource {
    Map<String, Object> query(AlertInput alert, Map<String, Object> arguments);
}
