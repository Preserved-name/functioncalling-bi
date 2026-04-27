package com.example.function_calling.tools;

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
     * 生成图表数据配置
     */
    @Tool("当用户需要可视化图表时调用。根据查询结果返回结构化的图表数据。参数：chartType(折线图line/柱状图bar/饼图pie), title(图表标题), rawData(原始查询结果的JSON字符串)")
    public String generateChartData(String chartType, String title, String rawData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> chartData = new HashMap<>();
            chartData.put("type", "chart_event");
            chartData.put("chartType", chartType);
            chartData.put("title", title);
            // 尝试解析 rawData 并转换为 ECharts 能用的格式，这里简单透传，前端可根据需要处理
            chartData.put("rawData", rawData);
            return mapper.writeValueAsString(chartData);
        } catch (Exception e) {
            return "{\"error\": \"生成图表数据失败\"}";
        }
    }
}
