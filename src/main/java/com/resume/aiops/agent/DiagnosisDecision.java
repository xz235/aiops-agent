package com.resume.aiops.agent;

import java.util.List;

public record DiagnosisDecision(String conclusion, boolean reviewRequired, List<String> reasons) {
    public DiagnosisDecision {
        reasons = List.copyOf(reasons);
    }
}
