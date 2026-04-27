package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.tools.WeatherTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WeatherAgent implements Agent {

    private final ChatLanguageModel chatModel;
    private final WeatherTools weatherTools;

    public WeatherAgent(ChatLanguageModel chatModel, WeatherTools weatherTools) {
        this.chatModel = chatModel;
        this.weatherTools = weatherTools;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.WEATHER;
    }

    @Override
    public String handle(String userMessage) {
        // 使用 LangChain4j 的 AiServices 创建带工具调用的助手
        WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
                .chatLanguageModel(chatModel)
                .tools(weatherTools)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
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
