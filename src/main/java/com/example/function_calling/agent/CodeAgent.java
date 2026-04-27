package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component
public class CodeAgent implements Agent {

    private final ChatLanguageModel chatModel;

    public CodeAgent(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.CODE;
    }

    @Override
    public String handle(String userMessage) {
        String prompt = String.format(
                "你是一个专业的编程助手。请帮助解决以下编程相关问题：\n\n" +
                "用户问题：%s\n\n" +
                "请提供清晰的代码示例和详细的解释说明。如果是代码问题，请指出问题所在并给出修复方案。",
                userMessage
        );
        
        return chatModel.generate(prompt);
    }

    @Override
    public String getName() {
        return "代码助手";
    }
}
