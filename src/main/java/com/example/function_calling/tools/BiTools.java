package com.example.function_calling.tools;

import com.example.function_calling.model.ChartRequest;
import com.example.function_calling.service.SchemaExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BI 工具类 - 提供数据库查询功能
 */
@Component
public class BiTools {

    private static final Logger log = LoggerFactory.getLogger(BiTools.class);
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SchemaExtractor schemaExtractor;
    
    // 用于存储最新的图表请求（通过 ThreadLocal 实现线程安全）
    private static final ThreadLocal<ChartRequest> currentChartRequest = new ThreadLocal<>();
    
    // 用于在异步环境中传递图表数据（由 StreamingBiAgent 设置）
    private ChartRequest asyncChartRequest;

    /**
     * 获取数据库表结构信息
     */
    @Tool("获取数据库中所有表的结构信息，包括表名、字段名、字段类型和注释")
    public String getDatabaseSchema() {
        return schemaExtractor.extractAllSchemas();
    }

    /**
     * 执行 SQL 查询（只读）
     */
    @Tool("执行 SQL 查询语句并返回结果。注意：只能执行 SELECT 查询，会自动限制返回行数为 100")
    public String executeQuery(String sql) {
        log.debug("执行 SQL 查询：{}", sql);
        // 安全检查：确保只执行 SELECT 语句
        String trimmedSql = sql.trim().toLowerCase();
        if (!trimmedSql.startsWith("select")) {
            return "错误：仅支持 SELECT 查询语句。";
        }
        
        // 防止危险操作
        if (trimmedSql.contains("drop") || trimmedSql.contains("delete") || 
            trimmedSql.contains("update") || trimmedSql.contains("insert")) {
            return "错误：检测到危险操作，已阻止执行。";
        }
        
        try {
            // 1. 清理 SQL：去掉末尾的分号，防止与 LIMIT 冲突
            sql = sql.trim();
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1);
            }

            // 2. 自动添加 LIMIT 限制（如果没有的话）
            if (!trimmedSql.contains("limit")) {
                sql = sql + " LIMIT 100";
            }
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            if (results.isEmpty()) {
                return "查询成功，但未返回任何数据。";
            }
            
            // 格式化结果为字符串，并强制提醒 AI 调用图表工具
            StringBuilder resultText = new StringBuilder();
            resultText.append("查询成功，返回 ").append(results.size()).append(" 条记录。\n");
            resultText.append("原始数据: ").append(results.toString()).append("\n");
            resultText.append("【重要提示】：如果用户需要可视化图表，请在你的回复最后一行输出一个 JSON，格式为：{\"type\": \"chart_event\", \"chartType\": \"line/bar/pie\", \"title\": \"标题\", \"rawData\": ");
            resultText.append(results.toString());
            resultText.append("}");
            return resultText.toString();
        } catch (Exception e) {
            return "查询执行失败: " + e.getMessage();
        }
    }

    /**
     * 渲染图表 - Function Calling 工具
     * 当需要展示图表时调用此工具，系统会自动将图表数据通过 SSE 发送给前端
     */
    @Tool("当用户需要可视化图表时必须调用此工具。参数：chartType(折线图line/柱状图bar/饼图pie), title(图表标题), xAxis(X轴配置，包含label和data数组), yAxis(Y轴配置，包含label和data数组), series(数据系列数组，每个元素包含name和data), rawData(原始查询结果)")
    public String renderChart(String chartType, String title, 
                             ChartRequest.AxisConfig xAxis, 
                             ChartRequest.AxisConfig yAxis,
                             List<ChartRequest.Series> series,
                             Object rawData) {
        log.debug("调用 renderChart 工具: chartType={}, title={}", chartType, title);
        
        // 构建图表请求对象
        ChartRequest chartRequest = new ChartRequest();
        chartRequest.setChartType(chartType);
        chartRequest.setTitle(title);
        chartRequest.setXAxis(xAxis);
        chartRequest.setYAxis(yAxis);
        chartRequest.setSeries(series);
        chartRequest.setRawData(rawData);
        
        // 如果 series 为空但 xAxis 和 yAxis 有数据，自动生成 series
        if ((series == null || series.isEmpty()) && xAxis != null && yAxis != null) {
            List<String> yData = yAxis.getData();
            if (yData != null && !yData.isEmpty()) {
                // 转换 yAxis 数据为 Object 类型
                List<Object> seriesData = new java.util.ArrayList<>();
                for (String val : yData) {
                    try {
                        seriesData.add(Double.parseDouble(val));
                    } catch (NumberFormatException e) {
                        seriesData.add(val);
                    }
                }
                
                ChartRequest.Series autoSeries = new ChartRequest.Series();
                autoSeries.setName(title != null ? title : "数据");
                autoSeries.setData(seriesData);
                chartRequest.setSeries(java.util.Collections.singletonList(autoSeries));
            }
        }
        
        // 存储到 ThreadLocal，供外部获取
        currentChartRequest.set(chartRequest);
        
        // 同时存储到实例变量，供异步环境使用
        this.asyncChartRequest = chartRequest;
        
        log.debug("renderChart 工具调用完成，图表数据已存储");
        
        // 返回成功标记（实际图表数据会通过 ToolExecution 机制传递）
        return "图表数据已准备就绪，将通过单独的事件发送给前端";
    }
    
    /**
     * 获取当前线程的图表请求（用于 StreamingBiAgent 或 Controller 获取）
     */
    public ChartRequest getCurrentChartRequest() {
        // 优先从异步变量获取
        if (asyncChartRequest != null) {
            ChartRequest request = asyncChartRequest;
            asyncChartRequest = null; // 使用后清理
            log.debug("从 asyncChartRequest 获取图表数据");
            return request;
        }
        
        // 其次从 ThreadLocal 获取
        ChartRequest request = currentChartRequest.get();
        currentChartRequest.remove(); // 使用后清理
        if (request != null) {
            log.debug("从 ThreadLocal 获取图表数据");
        }
        return request;
    }
}
