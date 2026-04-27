package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;
import com.example.function_calling.tools.BiTools;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BiAgent implements Agent {

    private final ChatLanguageModel chatModel;
    private final BiTools biTools;

    public BiAgent(ChatLanguageModel chatModel, BiTools biTools) {
        this.chatModel = chatModel;
        this.biTools = biTools;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.BI_ANALYSIS;
    }

    @Override
    public String handle(String userMessage) {
        // 定义 BI Agent 的系统提示词
        String systemPrompt = "你是一个精通 MySQL 数据库和 ECharts 可视化的资深数据分析师。\n" +
                "你的任务是根据用户的问题，利用提供的工具查询数据库并给出简洁、准确的回答。\n" +
                "请严格遵循以下原则：\n" +
                "1. **先查结构**：在生成 SQL 前，务必先调用 getDatabaseSchema 了解表结构。\n" +
                "2. **执行查询**：使用 executeQuery 获取原始数据。\n" +
                "3. **智能图表决策**：根据问题类型决定是否生成图表。\n" +
                "   - **需要生成图表的场景**：\n" +
                "     * 趋势分析：涉及时间序列的变化（如：'每月销售额'、'年度增长趋势'）\n" +
                "     * 分类对比：需要对比多个类别的数据（如：'各部门人数对比'、'各产品销量'）\n" +
                "     * 占比分析：展示构成比例（如：'状态分布'、'市场份额'）\n" +
                "   - **不需要生成图表的场景**：\n" +
                "     * 单一数值查询（如：'哪个产品卖得最多'、'总销售额是多少'）\n" +
                "     * 简单的事实性问题（如：'有多少个部门'、'最高薪资是多少'）\n" +
                "     * Top N 排名（如：'销量前三的产品'）只需文字列出即可\n" +
                "   - **图表类型选择规则**（仅在需要时）：\n" +
                "     * 折线图(line)：时间序列、趋势分析\n" +
                "     * 柱状图(bar)：分类对比、排名\n" +
                "     * 饼图(pie)：占比分析、构成比例\n" +
                "   - **JSON 格式**（仅在需要图表时）：{\"type\": \"chart_event\", \"chartType\": \"line/bar/pie\", \"title\": \"图表标题\", \"rawData\": [数据数组]}\n" +
                "   - rawData 应该是从 executeQuery 得到的原始数据（List<Map> 格式）。\n" +
                "   - 如果决定生成图表，JSON 必须放在回复的最后，单独一行。\n" +
                "4. **SQL 规范**：确保 SQL 符合 MySQL 8.0 规范，并自动添加 LIMIT 100。\n" +
                "5. **回答风格**：用中文简洁明了地回答问题，只在真正需要可视化时才附加图表 JSON。";

        // 创建带工具的 BI 助手
        BiAssistant assistant = AiServices.builder(BiAssistant.class)
                .chatLanguageModel(chatModel)
                .tools(biTools)
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        
        return assistant.chat(userMessage);
    }

    @Override
    public String getName() {
        return "数据分析助手";
    }

    /**
     * BI 助手接口 - 用于 Function Calling
     */
    interface BiAssistant {
        String chat(String userMessage);
    }
}
