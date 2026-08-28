package com.resume.aiops.web;

import com.resume.aiops.skill.DynamicSkillRegistry;
import com.resume.aiops.skill.SkillDefinition;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final DynamicSkillRegistry registry;

    public SkillController(DynamicSkillRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    List<SkillDefinition> list() {
        return registry.definitions();
    }

    @PostMapping("/reload")
    List<SkillDefinition> reload() throws IOException {
        return registry.reload();
    }
}

