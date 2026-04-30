package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import com.example.function_calling.service.RagService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

/**
 * RAG Agent：知识库检索 + LLM 生成
 */
@Component
public class RagAgent implements Agent {

    private final ChatModel chatModel;
    private final RagService ragService;
    private final ConversationService conversationService;

    public RagAgent(ChatModel chatModel, RagService ragService,
                    ConversationService conversationService) {
        this.chatModel = chatModel;
        this.ragService = ragService;
        this.conversationService = conversationService;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.RAG;
    }

    @Override
    public String handle(String userMessage) {
        return handleWithSession("default-session", userMessage);
    }

    public String handleWithSession(String sessionId, String userMessage) {
        // 使用 RAG 检索 + 生成
        return ragService.query(userMessage);
    }

    @Override
    public String getName() {
        return "知识库助手";
    }
}
