package com.resume.aiops.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("llm")
public class LlmConfiguration {
    @Bean
    ChatModel openAiCompatibleChatModel(
            @Value("${aiops.llm.api-key}") String apiKey,
            @Value("${aiops.llm.base-url:https://api.openai.com}") String baseUrl,
            @Value("${aiops.llm.model:gpt-4.1-mini}") String model) {
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }
}

