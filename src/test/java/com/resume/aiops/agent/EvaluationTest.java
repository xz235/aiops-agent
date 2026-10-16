package com.resume.aiops.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.resume.aiops.agent.domain.*;
import com.resume.aiops.config.AiOpsProperties;
import com.resume.aiops.rag.RunbookEvidence;
import com.resume.aiops.skill.SkillExecutor;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Fixture replay through the production AgentLoop. Opt-in; never calls a model by default. */
@SpringBootTest
@EnabledIfSystemProperty(named="eval.enabled", matches="true")
class EvaluationTest {
    @Autowired AgentLoop loop;
    @Autowired ObjectMapper mapper;
    @Autowired ObjectProvider<ChatModel> chatModels;
    @MockBean AgentDecisionClient model;
    @MockBean SkillExecutor executor;
    @SpyBean AiOpsProperties properties;

    @Test void evaluate() throws Exception {
        boolean live = Boolean.getBoolean("eval.live");
        String split = System.getProperty("eval.split", "dev");
        assertThat(Set.of("dev","test","all")).contains(split);
        AgentDecisionClient liveClient = live ? new SpringAiDecisionClient(chatModels.getObject(),mapper) : null;
        List<RunbookEvidence> books = new ArrayList<>();
        for (String name : List.of("bad-release.md","connection-pool.md","cpu-spike.md"))
            books.add(new RunbookEvidence(name,name,Files.readString(Path.of("runbooks",name)),1.0));
        Path output = Path.of("target","eval", (live ? "live-" : "smoke-")+split+"-"+System.currentTimeMillis()+".jsonl");
        Files.createDirectories(output.getParent());
        int count=0, matched=0;
        try (var writer = Files.newBufferedWriter(output)) {
            for (String line : Files.readAllLines(Path.of("eval","cases.jsonl"))) {
                JsonNode item=mapper.readTree(line);
                if (!split.equals("all") && !split.equals(item.path("split").asText())) continue;
                reset(model,executor);
                when(properties.maxAgentSteps()).thenReturn(item.path("maxToolCalls").asInt());
                when(properties.taskTimeout()).thenReturn(java.time.Duration.ofMinutes(10));
                when(executor.execute(anyString(),any(),anyMap())).thenAnswer(inv -> {
                    JsonNode fixture=item.path("fixtures").path((String)inv.getArgument(0));
                    if (fixture.has("error")) throw new IllegalStateException(fixture.path("error").asText());
                    if (!fixture.has("result")) throw new IllegalArgumentException("Unknown fixture tool");
                    return mapper.convertValue(fixture.path("result"),new TypeReference<Map<String,Object>>(){});
                });
                when(model.decide(any())).thenAnswer(inv -> {
                    AgentContext context=inv.getArgument(0);
                    if (live) return liveClient.decide(context);
                    // Deliberately label-blind smoke driver; not a diagnostic baseline.
                    List<String> names=List.of("query_service_metrics","query_application_logs","query_recent_releases");
                    int n=context.observations().size();
                    if (n<3 && context.remainingToolCalls()>0) {
                        String name=names.get(n);
                        return new AgentAction("TOOL",name,Map.of(n==1 ? "query":"timeRange",n==1 ? "level:ERROR":"15m"),null,List.of(),false);
                    }
                    List<String> refs=new ArrayList<>();
                    context.observations().stream().filter(c->c.success()).forEach(c->refs.add(c.callId()));
                    refs.add(books.get(0).documentId());
                    return new AgentAction("FINAL",null,Map.of(),"Smoke driver only: no diagnostic reasoning performed",refs,true);
                });
                AlertInput alert=mapper.treeToValue(item.path("alert"),AlertInput.class);
                long start=System.nanoTime();
                var result=loop.run(DiagnosisTask.create(UUID.randomUUID().toString(),alert,Instant.now()),books);
                var successful=result.calls().stream().filter(c->c.success()).map(c->c.skillName()).toList();
                List<String> required=mapper.convertValue(item.path("expected").path("requiredTools"),new TypeReference<List<String>>(){});
                boolean reviewMatch=result.decision().reviewRequired()==item.path("expected").path("reviewRequired").asBoolean();
                if(reviewMatch) matched++;
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",item.path("id").asText()); row.put("mode",live?"live":"smoke");
                row.put("category",item.path("category").asText()); row.put("expected",item.path("expected"));
                row.put("decision",result.decision()); row.put("calls",result.calls());
                row.put("reviewMatch",reviewMatch); row.put("requiredToolsCovered",successful.containsAll(required));
                row.put("latencyMs",(System.nanoTime()-start)/1_000_000);
                row.put("semanticReview","PENDING");
                row.put("model",live ? System.getenv().getOrDefault("OPENAI_MODEL","configuration default") : "scripted-smoke");
                writer.write(mapper.writeValueAsString(row)); writer.newLine(); writer.flush();
                assertThat(result.calls().size()).isLessThanOrEqualTo(item.path("maxToolCalls").asInt());
                count++;
            }
        }
        assertThat(count).isEqualTo(split.equals("all")?100:split.equals("dev")?20:80);
        System.out.println("Evaluation output: "+output+"; cases="+count+"; review matches="+matched+"; semantic scoring PENDING. Smoke scores are NOT model accuracy.");
    }
}
