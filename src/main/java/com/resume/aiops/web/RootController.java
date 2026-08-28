package com.resume.aiops.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {
    @GetMapping("/api")
    Map<String, Object> api() {
        return Map.of("name", "AIOps Agent Platform", "status", "ready", "version", "0.1.0");
    }
}

