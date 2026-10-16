package com.resume.aiops.skill.handler;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.SkillHandler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Simulated incident history. Gives a reviewer a precedent instead of a guess. */
@Component("querySimilarIncidentsHandler")
public class QuerySimilarIncidentsHandler implements SkillHandler {
    @Override
    public Map<String, Object> execute(AlertInput alert, Map<String, Object> arguments) {
        String scenario = String.valueOf(alert.labels().getOrDefault("scenario", "cpu_spike"));
        List<Map<String, Object>> matches = switch (scenario) {
            case "connection_pool" -> List.of(incident("INC-2187", scenario, 0.86,
                    "Pool ceiling lowered to 50; requests queued until timeout",
                    "Restored the pool ceiling and alerted on pool utilisation"));
            case "bad_release" -> List.of(incident("INC-2310", scenario, 0.79,
                    "Binary incompatibility introduced by release 2.4.0",
                    "Rolled back to 2.3.9 after approval; added a compatibility smoke test"));
            case "cpu_spike" -> List.of(incident("INC-2291", scenario, 0.74,
                    "Batch job saturated CPU and triggered GC pressure",
                    "Capped batch concurrency and moved the job off peak hours"));
            default -> List.of();
        };
        return Map.of("service", alert.service(), "lookback", arguments.getOrDefault("lookback", "30d"),
                "matchCount", matches.size(),
                "matches", matches);
    }

    private Map<String, Object> incident(String id, String cause, double similarity,
                                         String finding, String resolution) {
        return Map.of("incidentId", id, "occurredAt", Instant.now().minusSeconds(1_123_200).toString(),
                "cause", cause, "similarity", similarity,
                "finding", finding, "resolution", resolution);
    }
}
