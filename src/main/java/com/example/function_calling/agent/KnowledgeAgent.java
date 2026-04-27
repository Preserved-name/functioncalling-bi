package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeAgent implements Agent {

    private final ChatLanguageModel chatModel;

    public KnowledgeAgent(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.KNOWLEDGE;
    }

    @Override
    public String handle(String userMessage) {
        String prompt = String.format(
                "你是一个知识渊博的问答助手。请准确、详细地回答以下问题：\n\n" +
                "用户问题：%s\n\n" +
                "请提供清晰、准确的回答，必要时可以补充相关背景知识。",
                userMessage
        );
        
        return chatModel.generate(prompt);
    }

    @Override
    public String getName() {
        return "知识助手";
    }
}
