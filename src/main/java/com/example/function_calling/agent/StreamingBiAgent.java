package com.example.function_calling.agent;

import com.example.function_calling.model.ChartRequest;
import com.example.function_calling.model.Intent;
import com.example.function_calling.service.ConversationService;
import com.example.function_calling.service.SchemaExtractor;
import com.example.function_calling.tools.BiTools;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 优化后的 BI Agent：低延迟、单次 LLM + 程序执行
 */
@Component
public class StreamingBiAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(StreamingBiAgent.class);
    
    private final StreamingChatLanguageModel streamingModel;
    private final ChatLanguageModel chatModel; // 用于非流式调用（生成 SQL）
    private final BiTools biTools;
    private final ConversationService conversationService;
    private final SchemaExtractor schemaExtractor;
    private final JdbcTemplate jdbcTemplate;

    public StreamingBiAgent(StreamingChatLanguageModel streamingModel, 
                           ChatLanguageModel chatModel,
                           BiTools biTools,
                           ConversationService conversationService,
                           SchemaExtractor schemaExtractor,
                           JdbcTemplate jdbcTemplate) {
        this.streamingModel = streamingModel;
        this.chatModel = chatModel;
        this.biTools = biTools;
        this.conversationService = conversationService;
        this.schemaExtractor = schemaExtractor;
        this.jdbcTemplate = jdbcTemplate;
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
     * 优化 2 & 4：重构 BI Agent - 单次 LLM + 程序执行 + 真正流式输出
     */
    public void handleStreamingWithSession(String sessionId, String userMessage, 
                                          Consumer<String> onToken,
                                          Consumer<ChartRequest> onChart,
                                          Consumer<String> onComplete) {
        // 优化：立即发送初始反馈，让用户感知系统正在响应
        onToken.accept("正在分析您的问题...\n\n");

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 程序预处理：获取 schema（从缓存）
                String schema = schemaExtractor.extractAllSchemas();
                
                // 2. 一次 LLM 调用：生成 SQL 并判断是否需要图表
                String sqlPrompt = buildSqlPrompt(userMessage, schema);
                String llmResponse = chatModel.generate(sqlPrompt);
                
                // 解析 LLM 返回的 SQL 和图表意图
                ParsedResult parsed = parseLlmResponse(llmResponse);
                
                // 优化：在 SQL 执行阶段发送提示
                onToken.accept("正在查询数据库...\n\n");
                
                // 3. 程序执行：执行 SQL
                List<Map<String, Object>> data = executeQuery(parsed.sql);
                
                // 4. 第二次 LLM 调用：分析数据并生成总结与图表指令
                String analysisPrompt = buildAnalysisPrompt(userMessage, parsed.sql, data);
                String analysisResponse = chatModel.generate(analysisPrompt);
                
                // 解析分析结果（提取图表意图和文本总结）
                ParsedResult finalResult = parseLlmResponse(analysisResponse);
                
                // 5. 程序执行：构建图表数据（如果需要）
                if (finalResult.needsChart) {
                    onToken.accept("正在生成图表...\n\n");
                    ChartRequest chartRequest = buildChartRequest(data, finalResult.chartType, finalResult.title);
                    if (chartRequest != null && onChart != null) {
                        onChart.accept(chartRequest);
                    }
                }
                
                // 6. 流式输出 LLM 生成的专业总结
                String summary = finalResult.sql; // 这里的 sql 字段现在存储的是 LLM 生成的文本总结
                for (char c : summary.toCharArray()) {
                    onToken.accept(String.valueOf(c));
                    try { Thread.sleep(5); } catch (InterruptedException e) { break; }
                }
                
                onComplete.accept(summary);
                
            } catch (Exception e) {
                log.error("BI Agent 处理异常", e);
                onToken.accept("处理出错: " + e.getMessage());
                onComplete.accept("处理出错: " + e.getMessage());
            }
        });
    }

    private String buildAnalysisPrompt(String userQuestion, String sql, List<Map<String, Object>> data) {
        // 限制传给 LLM 的数据量，防止 Token 爆炸
        List<Map<String, Object>> sampleData = data.size() > 20 ? data.subList(0, 20) : data;
        
        return String.format(
            "你是一个资深数据分析师。用户的问题是：'%s'。\n" +
            "我们已经执行了 SQL 查询并得到了以下 %d 条数据（部分展示）：\n%s\n\n" +
            "请根据这些数据完成以下任务：\n" +
            "1. 用简洁、专业的中文回答用户的问题，指出数据中的关键趋势或结论。\n" +
            "2. 判断是否需要图表来辅助说明。如果需要，请在回复末尾另起一行输出：\n" +
            "   CHART: [line/bar/pie] | [图表标题]\n" +
            "3. 如果数据为空或不需要图表，请在末尾输出：NO_CHART\n\n" +
            "注意：请直接给出分析结论，不要重复 SQL 语句。",
            userQuestion, data.size(), sampleData.toString()
        );
    }

    private String buildSqlPrompt(String userMessage, String schema) {
        return String.format(
            "你是一个 SQL 专家。根据以下数据库结构和用户问题，生成一条 MySQL SELECT 语句。\n" +
            "如果问题适合可视化，请在最后输出 'CHART: [line/bar/pie] | [标题]'。\n" +
            "否则输出 'NO_CHART'。\n\n" +
            "数据库结构:\n%s\n\n" +
            "用户问题: %s\n\n" +
            "SQL:",
            schema, userMessage
        );
    }

    private ParsedResult parseLlmResponse(String response) {
        String sql = response;
        boolean needsChart = false;
        String chartType = "bar";
        String title = "数据图表";

        // 1. 提取图表意图
        if (response.contains("CHART:")) {
            needsChart = true;
            String[] parts = response.split("CHART:");
            sql = parts[0]; // 先不 trim，留给后面统一处理
            String chartInfo = parts[1].trim();
            String[] chartParts = chartInfo.split("\\|");
            if (chartParts.length >= 2) {
                chartType = chartParts[0].trim();
                title = chartParts[1].trim();
            }
        } else if (response.contains("NO_CHART")) {
            sql = response.replace("NO_CHART", "");
        }
        
        // 2. 健壮地提取纯净 SQL（去除 Markdown 标记和无关前缀）
        sql = extractCleanSql(sql);
        
        return new ParsedResult(sql, needsChart, chartType, title);
    }

    /**
     * 从 LLM 响应中提取纯净的 SQL 语句
     */
    private String extractCleanSql(String rawSql) {
        if (rawSql == null) return "";
        
        String sql = rawSql.trim();
        
        // 1. 移除 Markdown 代码块标记 (```sql ... ```)
        if (sql.contains("```")) {
            // 提取第一个 ``` 和最后一个 ``` 之间的内容
            int start = sql.indexOf("```");
            int end = sql.lastIndexOf("```");
            if (start != -1 && end != -1 && end > start) {
                sql = sql.substring(start + 3, end).trim();
            }
        }
        
        // 2. 移除常见的语言标识符或前缀（如 sql, mysql, SELECT语句：等）
        // 使用正则匹配开头的非 SQL 关键字部分
        sql = sql.replaceAll("^(?i)(sql|mysql|select\\s*statement|select\\s*语句)[:：\\s]*", "").trim();
        
        // 3. 寻找第一个真正的 SELECT 关键字位置（确保后面跟着空格或换行，避免匹配到单词内部）
        Pattern pattern = Pattern.compile("(?i)\\bSELECT\\b");
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            sql = sql.substring(matcher.start());
        } else {
            // 如果找不到 SELECT，尝试查找其他可能的开头，或者直接返回清理后的字符串
            log.warn("未在响应中找到 SELECT 关键字，原始内容: {}", rawSql);
        }
        
        // 4. 移除末尾的分号、空白和可能的 LIMIT 重复添加
        sql = sql.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }

    private List<Map<String, Object>> executeQuery(String sql) {
        try {
            // 二次安全检查与清理
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalArgumentException("SQL 语句为空");
            }
            
            String cleanSql = sql.trim();
            // 再次确保移除末尾分号，防止 LLM 绕过解析逻辑
            while (cleanSql.endsWith(";")) {
                cleanSql = cleanSql.substring(0, cleanSql.length() - 1).trim();
            }

            if (!cleanSql.toLowerCase().startsWith("select")) {
                throw new IllegalArgumentException("仅支持 SELECT 查询");
            }

            log.info("执行 SQL: {}", cleanSql);
            return jdbcTemplate.queryForList(cleanSql);
        } catch (Exception e) {
            log.error("SQL 执行失败: {}", sql, e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    private ChartRequest buildChartRequest(List<Map<String, Object>> data, String type, String title) {
        if (data == null || data.isEmpty()) return null;
        
        Map<String, Object> firstRow = data.get(0);
        List<String> keys = new ArrayList<>(firstRow.keySet());
        
        if (keys.isEmpty()) return null;
        
        // 1. 智能识别 X 轴和 Y 轴字段
        String tempXKey = null;
        List<String> yKeys = new ArrayList<>();
        
        for (String key : keys) {
            if(key.contains("_id")) continue;
            Object val = firstRow.get(key);
            // 简单判断：如果是数字类型，归为 Y 轴（数值）；否则归为 X 轴（分类/时间）
            if (val instanceof Number) {
                yKeys.add(key);
            } else {
                // 优先选择非 ID 字段作为 X 轴
                if (tempXKey == null && !key.toLowerCase().contains("id")) {
                    tempXKey = key;
                } else if (tempXKey == null) {
                    tempXKey = key; // 兜底
                }
            }
        }
        
        // 如果没找到非数字字段做 X 轴，就用第一个字段
        if (tempXKey == null && !keys.isEmpty()) {
            tempXKey = keys.get(0);
        }
        
        // 关键修复：创建一个 final 副本供 Lambda 使用
        final String xKey = tempXKey;
        
        ChartRequest request = new ChartRequest();
        request.setChartType(type);
        request.setTitle(title);
        
        // 2. 构建 X 轴
        ChartRequest.AxisConfig xAxis = new ChartRequest.AxisConfig();
        xAxis.setLabel(xKey);
        xAxis.setData(data.stream().map(r -> String.valueOf(r.get(xKey))).collect(java.util.stream.Collectors.toList()));
        request.setXAxis(xAxis);
        
        // 2.1 构建 Y 轴（取第一个数值字段的名称作为 Y 轴标签）
        if (!yKeys.isEmpty()) {
            ChartRequest.AxisConfig yAxis = new ChartRequest.AxisConfig();
            yAxis.setLabel(yKeys.get(0)); // 使用第一个数值列的名称作为 Y 轴标题
            request.setYAxis(yAxis);
        }
        
        // 3. 构建 Series (只包含数值字段)
        List<ChartRequest.Series> seriesList = new java.util.ArrayList<>();
        for (String yKey : yKeys) {
            List<Object> yData = data.stream().map(r -> r.get(yKey)).collect(java.util.stream.Collectors.toList());
            
            ChartRequest.Series series = new ChartRequest.Series();
            series.setName(yKey);
            series.setData(yData);
            seriesList.add(series);
        }
        
        // 4. 兜底：如果没有数值字段，就把剩下的字段都塞进 Series
        if (seriesList.isEmpty()) {
             for (String key : keys) {
                 if (!key.equals(xKey)) {
                     List<Object> yData = data.stream().map(r -> String.valueOf(r.get(key))).collect(java.util.stream.Collectors.toList());
                     ChartRequest.Series series = new ChartRequest.Series();
                     series.setName(key);
                     series.setData(yData);
                     seriesList.add(series);
                 }
             }
        }
        
        request.setSeries(seriesList);
        
        return request;
    }

    private static class ParsedResult {
        String sql;
        boolean needsChart;
        String chartType;
        String title;
        ParsedResult(String sql, boolean needsChart, String chartType, String title) {
            this.sql = sql;
            this.needsChart = needsChart;
            this.chartType = chartType;
            this.title = title;
        }
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
