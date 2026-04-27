package com.example.function_calling.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${aliyun.bailian.api-key}")
    private String apiKey;

    @Value("${aliyun.bailian.base-url}")
    private String baseUrl;

    @Value("${aliyun.bailian.model-name}")
    private String modelName;

    /**
     * 创建 ChatLanguageModel Bean
     * 支持 Function Calling
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .temperature(0.7)
                // 启用 Function Calling 支持
                .build();
    }

    /**
     * 创建 StreamingChatLanguageModel Bean
     * 用于流式输出
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .temperature(0.7)
                .build();
    }
}
