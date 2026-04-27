package com.example.function_calling.service;

import com.example.function_calling.model.Intent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;

@Service
public class IntentRecognizer {

    private final ChatLanguageModel chatModel;

    public IntentRecognizer(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 识别用户问题的意图
     */
    public Intent recognize(String userMessage) {
        String prompt = String.format(
                "请分析以下用户问题属于哪个类别，只返回类别名称（WEATHER、MATH、KNOWLEDGE、CODE、BI_ANALYSIS、GENERAL）：\n\n" +
                "类别说明：\n" +
                "- WEATHER: 天气查询相关问题\n" +
                "- MATH: 数学计算、数值运算问题\n" +
                "- KNOWLEDGE: 知识问答、事实性问题\n" +
                "- CODE: 编程、代码相关问题\n" +
                "- BI_ANALYSIS: 数据库查询、数据分析、统计相关问题（如：查询某表数据、统计数量、平均值等）\n" +
                "- GENERAL: 其他通用对话\n\n" +
                "用户问题：%s\n\n" +
                "类别：",
                userMessage
        );

        String response = chatModel.generate(prompt);
        String intentStr = response.trim().toUpperCase();

        // 匹配意图
        for (Intent intent : Intent.values()) {
            if (intentStr.contains(intent.name())) {
                return intent;
            }
        }

        return Intent.GENERAL;
    }
}
