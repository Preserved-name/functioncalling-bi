package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import com.example.function_calling.tools.MemoryTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

@Component
public class GeneralAgent implements Agent {

    private final ChatLanguageModel chatModel;
    private final ConversationService conversationService;
    private final MemoryTools memoryTools;

    public GeneralAgent(ChatLanguageModel chatModel, 
                       ConversationService conversationService,
                       MemoryTools memoryTools) {
        this.chatModel = chatModel;
        this.conversationService = conversationService;
        this.memoryTools = memoryTools;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.GENERAL;
    }

    @Override
    public String handle(String userMessage) {
        return handleWithMemory("default-session", userMessage);
    }

    /**
     * 带会话 ID 的处理方法
     */
    public String handleWithMemory(String sessionId, String userMessage) {
        // 创建带记忆的助手
        ChatAssistant assistant = AiServices.builder(ChatAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(memoryId -> conversationService.getOrCreateMemory(sessionId))
                .tools(memoryTools)
                .build();
        
        return assistant.chat(userMessage);
    }

    @Override
    public String getName() {
        return "通用助手";
    }

    /**
     * 聊天助手接口 - 用于 Function Calling 和记忆
     */
    interface ChatAssistant {
        String chat(String userMessage);
    }
}
