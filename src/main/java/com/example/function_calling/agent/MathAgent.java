package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component
public class MathAgent implements Agent {

    private final ChatLanguageModel chatModel;

    public MathAgent(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.MATH;
    }

    @Override
    public String handle(String userMessage) {
        String prompt = String.format(
                "你是一个专业的数学计算助手。请仔细解答以下数学问题，展示计算过程：\n\n" +
                "用户问题：%s\n\n" +
                "请给出详细的解题步骤和最终答案。",
                userMessage
        );
        
        return chatModel.generate(prompt);
    }

    @Override
    public String getName() {
        return "数学助手";
    }
}
