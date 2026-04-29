package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import com.example.function_calling.tools.WeatherTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

@Component
public class WeatherAgent implements Agent {

    private final ChatModel chatModel;
    private final WeatherTools weatherTools;
    private final ConversationService conversationService;

    public WeatherAgent(ChatModel chatModel, WeatherTools weatherTools,
                       ConversationService conversationService) {
        this.chatModel = chatModel;
        this.weatherTools = weatherTools;
        this.conversationService = conversationService;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.WEATHER;
    }

    @Override
    public String handle(String userMessage) {
        return handleWithSession("default-session", userMessage);
    }

    /**
     * 带会话 ID 的处理方法
     */
    public String handleWithSession(String sessionId, String userMessage) {
        // 使用统一的 ConversationService 获取记忆
        MessageWindowChatMemory memory = conversationService.getOrCreateMemory(sessionId);

        WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
                .chatModel(chatModel)
                .tools(weatherTools)
                .chatMemory(memory)  // 使用共享记忆
                .build();

        return assistant.chat(userMessage);
    }

    @Override
    public String getName() {
        return "天气助手";
    }

    /**
     * 天气助手接口 - 用于 Function Calling
     */
    interface WeatherAssistant {
        String chat(String userMessage);
    }
}
