package com.resume.aiops.skill;

import com.resume.aiops.agent.domain.AlertInput;
import java.util.Map;

public interface SkillHandler {
    Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments);
}

