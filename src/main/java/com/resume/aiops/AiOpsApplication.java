package com.resume.aiops;

import com.resume.aiops.config.AiOpsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(AiOpsProperties.class)
public class AiOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiOpsApplication.class, args);
    }
}

