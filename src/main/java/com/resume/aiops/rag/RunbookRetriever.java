package com.resume.aiops.rag;

import com.resume.aiops.agent.domain.AlertInput;
import com.resume.aiops.config.AiOpsProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RunbookRetriever {
    private final AiOpsProperties properties;

    public RunbookRetriever(AiOpsProperties properties) {
        this.properties = properties;
    }

    public List<RunbookEvidence> retrieve(AlertInput alert, int topK) {
        try {
            Files.createDirectories(properties.runbooksDirectory());
            String query = alert.title() + " " + alert.description() + " " + alert.labels().getOrDefault("scenario", "");
            try (var paths = Files.list(properties.runbooksDirectory())) {
                return paths.filter(path -> path.getFileName().toString().endsWith(".md"))
                        .map(path -> score(path, query))
                        .filter(evidence -> evidence.score() > 0)
                        .sorted(Comparator.comparingDouble(RunbookEvidence::score).reversed())
                        .limit(topK)
                        .toList();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot retrieve runbooks", exception);
        }
    }

    private RunbookEvidence score(Path path, String query) {
        try {
            String content = Files.readString(path);
            Set<String> queryTerms = terms(query);
            Set<String> contentTerms = terms(content);
            long overlap = queryTerms.stream().filter(contentTerms::contains).count();
            double score = queryTerms.isEmpty() ? 0 : (double) overlap / queryTerms.size();
            String title = content.lines().filter(line -> line.startsWith("# ")).findFirst()
                    .map(line -> line.substring(2)).orElse(path.getFileName().toString());
            String excerpt = content.replaceAll("\\s+", " ");
            if (excerpt.length() > 320) {
                excerpt = excerpt.substring(0, 320);
            }
            return new RunbookEvidence(path.getFileName().toString(), title, excerpt, score);
        } catch (IOException exception) {
            return new RunbookEvidence(path.getFileName().toString(), path.getFileName().toString(), "", 0);
        }
    }

    private Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) {
            return result;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (token.length() > 2) {
                result.add(token);
            }
        }
        return result;
    }
}

