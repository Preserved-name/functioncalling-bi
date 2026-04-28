package com.example.function_calling.agent;

import com.example.function_calling.model.ChartRequest;
import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import com.example.function_calling.tools.BiTools;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 支持真正流式输出的 BI Agent
 * 替换原有的 BiAgent，提供实时流式体验
 */
@Component
public class StreamingBiAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(StreamingBiAgent.class);
    
    private final StreamingChatLanguageModel streamingModel;
    private final BiTools biTools;
    private final ConversationService conversationService;

    public StreamingBiAgent(StreamingChatLanguageModel streamingModel, BiTools biTools,
                           ConversationService conversationService) {
        this.streamingModel = streamingModel;
        this.biTools = biTools;
        this.conversationService = conversationService;
    }

    @Override
    public Intent getSupportedIntent() {
        return Intent.BI_ANALYSIS;
    }

    /**
     * 流式处理用户消息（带工具调用支持）
     * @param userMessage 用户消息
     * @param onToken 每个 token 的回调
     * @param onChart 图表数据回调
     * @param onComplete 完成回调（包含完整响应）
     */
    public void handleStreaming(String userMessage, Consumer<String> onToken, 
                               Consumer<ChartRequest> onChart, Consumer<String> onComplete) {
        handleStreamingWithSession("default-session", userMessage, onToken, onChart, onComplete);
    }
    
    /**
     * 带会话 ID 的流式处理（带工具调用支持）
     * @param sessionId 会话 ID
     * @param userMessage 用户消息
     * @param onToken 每个 token 的回调
     * @param onChart 图表数据回调
     * @param onComplete 完成回调（包含完整响应）
     */
    public void handleStreamingWithSession(String sessionId, String userMessage, 
                                          Consumer<String> onToken,
                                          Consumer<ChartRequest> onChart,
                                          Consumer<String> onComplete) {
        // 使用统一的 ConversationService 获取记忆
        MessageWindowChatMemory memory = conversationService.getOrCreateMemory(sessionId);
        
        // 用于存储图表数据（在异步环境中使用 final 数组引用）
        final ChartRequest[] chartDataHolder = new ChartRequest[1];
        
        // 定义系统提示词
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
                "4. **SQL 规范**：确保 SQL 符合 MySQL 8.0 规范，并自动添加 LIMIT 100。\n" +
                "5. **回答风格**：用中文简洁明了地回答问题。\n" +
                "6. **【极其重要】禁止在文本中输出 JSON**：\n" +
                "   ⚠️ **绝对禁止**：不要在回复文本中包含任何 JSON 格式的内容！\n" +
                "   ⚠️ **绝对禁止**：不要输出 `{\"type\":\"chart\"}` 或类似的结构化数据！\n" +
                "   ⚠️ **绝对禁止**：不要在文本末尾附加任何 JSON 字符串！\n" +
                "   ✅ **正确做法**：当需要图表时，直接调用 renderChart 工具，传入参数即可。\n" +
                "   ✅ **正确做法**：文本回复只包含自然语言描述，不包含任何结构化数据。\n" +
                "7. **工具调用说明**：\n" +
                "   - 当决定要展示图表时，必须调用 renderChart 工具\n" +
                "   - renderChart 的参数会自动传递给前端进行渲染\n" +
                "   - 调用工具后，继续用自然语言回答用户问题\n" +
                "   - 不要在文本中解释你调用了什么工具";

        // 创建带工具的 BI 助手
        BiAssistant assistant = AiServices.builder(BiAssistant.class)
                .streamingChatLanguageModel(streamingModel)
                .tools(biTools)
                .systemMessageProvider(chatMemoryId -> systemPrompt)
                .chatMemory(memory)  // 使用共享记忆
                .build();

        // 异步执行流式请求
        CompletableFuture.runAsync(() -> {
            try {
                StringBuilder fullResponse = new StringBuilder();
                AtomicBoolean isFirstToken = new AtomicBoolean(true);
                
                // 流式调用
                assistant.chat(userMessage)
                    .onNext(token -> {
                        if (isFirstToken.get()) {
                            log.debug("收到第一个 token");
                            isFirstToken.set(false);
                        }
                        fullResponse.append(token);
                        // 实时推送 token（只发送文本）
                        onToken.accept(token);
                    })
                    .onComplete(response -> {
                        String completeText = fullResponse.toString();
                        log.debug("流式输出完成，总长度: {}", completeText.length());
                        
                        // 检查是否有图表数据（优先从 holder 获取，其次从 ThreadLocal 获取）
                        ChartRequest chartRequest = chartDataHolder[0];
                        if (chartRequest == null) {
                            chartRequest = biTools.getCurrentChartRequest();
                        }
                        
                        if (chartRequest != null && onChart != null) {
                            log.debug("检测到图表数据，触发 onChart 回调: chartType={}, title={}", 
                                chartRequest.getChartType(), chartRequest.getTitle());
                            onChart.accept(chartRequest);
                        } else {
                            log.debug("未检测到图表数据");
                        }
                        
                        onComplete.accept(completeText);
                    })
                    .onError(error -> {
                        log.error("流式输出错误", error);
                        onComplete.accept("错误: " + error.getMessage());
                    })
                    .start();
                    
            } catch (Exception e) {
                log.error("流式处理异常", e);
                onComplete.accept("错误: " + e.getMessage());
            }
        });
    }
    
    @Override
    public String handle(String userMessage) {
        // 同步方法：等待流式完成（用于兼容旧接口）
        return handleWithSession("default-session", userMessage);
    }
    
    /**
     * 带会话 ID 的同步处理方法
     */
    public String handleWithSession(String sessionId, String userMessage) {
        StringBuilder response = new StringBuilder();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        handleStreamingWithSession(sessionId, userMessage, 
            token -> {}, // 不处理单个 token
            chartRequest -> {}, // 不处理图表（同步方法不需要）
            complete -> {
                response.append(complete);
                latch.countDown();
            }
        );
        
        try {
            latch.await(); // 等待完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return response.toString();
    }

    @Override
    public String getName() {
        return "数据分析助手（流式）";
    }

    /**
     * BI 助手接口 - 用于 Function Calling
     */
    interface BiAssistant {
        TokenStream chat(String userMessage);
    }
}
