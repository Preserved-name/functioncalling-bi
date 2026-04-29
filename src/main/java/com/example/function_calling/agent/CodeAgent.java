package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

@Component
public class CodeAgent implements Agent {

    private final ChatModel chatModel;
    private final ConversationService conversationService;

    public CodeAgent(ChatModel chatModel, ConversationService conversationService) {
        this.chatModel = chatModel;
        this.conversationService = conversationService;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.CODE;
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

        CodeAssistant assistant = AiServices.builder(CodeAssistant.class)
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        return assistant.chat(userMessage);
    }

    @Override
    public String getName() {
        return "代码助手";
    }

    /**
     * 代码助手接口 - 用于 Function Calling
     */
    interface CodeAssistant {
        String chat(String userMessage);
    }
}
