package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import com.example.function_calling.tools.WeatherTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

@Component
public class WeatherAgent implements Agent {

    private final ChatLanguageModel chatModel;
    private final WeatherTools weatherTools;
    private final ConversationService conversationService;

    public WeatherAgent(ChatLanguageModel chatModel, WeatherTools weatherTools, 
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
                .chatLanguageModel(chatModel)
                .tools(weatherTools)
                .chatMemory(memory)  // 使用共享记忆
                .build();
        
        String response = assistant.chat(userMessage);
        
        // 手动添加消息到记忆（AiServices 会自动管理，但这里确保一致性）
        // 注意：AiServices 已经自动添加了，所以不需要手动添加
        
        return response;
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
