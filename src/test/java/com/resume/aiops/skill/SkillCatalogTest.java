package com.resume.aiops.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.skill.handler.QueryConfigChangesHandler;
import com.resume.aiops.skill.handler.QueryContainerEventsHandler;
import com.resume.aiops.skill.handler.QueryDependencyHealthHandler;
import com.resume.aiops.skill.handler.QuerySimilarIncidentsHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Guards the Skill catalog: a Markdown file without a resolvable handler fails startup. */
@SpringBootTest
class SkillCatalogTest {
    private static final List<String> EXPECTED = List.of(
            "query_service_metrics", "query_application_logs", "query_recent_releases",
            "query_dependency_health", "query_config_changes",
            "query_similar_incidents", "query_container_events");

    @Autowired
    DynamicSkillRegistry registry;

    @Test
    void everySkillIsRegisteredReadOnlyAndBackedByAHandler() {
        assertThat(registry.definitions()).extracting(SkillDefinition::name)
                .containsExactlyInAnyOrderElementsOf(EXPECTED);
        assertThat(registry.definitions())
                .allSatisfy(definition -> assertThat(definition.riskLevel()).isEqualTo(SkillRiskLevel.READ_ONLY));
        EXPECTED.forEach(name -> assertThat(registry.require(name).handler()).isNotNull());
    }

    @Test
    void newSkillsReturnEvidenceForEveryScenario() {
        for (String scenario : List.of("cpu_spike", "connection_pool", "bad_release", "unclassified")) {
            AlertInput alert = alert(scenario);
            assertThat(new QueryDependencyHealthHandler().execute(alert, Map.of("timeRange", "15m")))
                    .containsKey("degradedDownstream").containsEntry("service", "order-service");
            assertThat(new QueryConfigChangesHandler().execute(alert, Map.of("timeRange", "2h")))
                    .containsKey("recentConfigChange");
            assertThat(new QuerySimilarIncidentsHandler().execute(alert, Map.of("lookback", "30d")))
                    .containsKey("matchCount");
            assertThat(new QueryContainerEventsHandler().execute(alert, Map.of("timeRange", "15m")))
                    .containsEntry("oomKilled", false).containsEntry("restartCount", 0);
        }
    }

    @Test
    void dependencyHealthSeparatesALocalBottleneckFromAnInheritedOne() {
        Map<String, Object> pooled = new QueryDependencyHealthHandler()
                .execute(alert("connection_pool"), Map.of("timeRange", "15m"));
        Map<String, Object> released = new QueryDependencyHealthHandler()
                .execute(alert("bad_release"), Map.of("timeRange", "15m"));

        assertThat(pooled).containsEntry("degradedDownstream", true)
                .containsEntry("dependencyServersHealthy", true);
        assertThat(released).containsEntry("degradedDownstream", false);
    }

    @Test
    void configChangesDistinguishADeploymentFromAConfigEdit() {
        assertThat(new QueryConfigChangesHandler().execute(alert("connection_pool"), Map.of("timeRange", "2h")))
                .containsEntry("recentConfigChange", true);
        assertThat(new QueryConfigChangesHandler().execute(alert("bad_release"), Map.of("timeRange", "2h")))
                .containsEntry("recentConfigChange", false)
                .containsEntry("changeCount", 0);
    }

    private AlertInput alert(String scenario) {
        return new AlertInput("Order API error spike", "order-service", "HIGH",
                "Error rate increased", Map.of("scenario", scenario));
    }
}
