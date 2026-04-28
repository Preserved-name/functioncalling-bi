package com.example.function_calling.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Schema 提取器 - 从 MySQL 自动获取数据库元数据
 */
@Service
public class SchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractor.class);

    @Autowired
    private DataSource dataSource;

    // 优化 3：Schema 缓存机制
    private final AtomicReference<String> schemaCache = new AtomicReference<>();

    /**
     * 应用启动时加载并缓存 Schema
     */
    @PostConstruct
    public void init() {
        try {
            String schema = extractAllSchemasFromDB();
            schemaCache.set(schema);
            log.info("数据库 Schema 已预加载并缓存，长度: {}", schema.length());
        } catch (Exception e) {
            log.error("初始化 Schema 缓存失败", e);
        }
    }

    /**
     * 获取所有表的 Schema 信息（带缓存）
     */
    public String extractAllSchemas() {
        String cached = schemaCache.get();
        if (cached != null) {
            return cached;
        }
        // 如果缓存为空，重新加载
        String schema = extractAllSchemasFromDB();
        schemaCache.set(schema);
        return schema;
    }

    /**
     * 从数据库实际提取 Schema 的逻辑
     */
    private String extractAllSchemasFromDB() {
        StringBuilder schemaText = new StringBuilder();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // 获取所有表
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableComment = tables.getString("REMARKS");
                
                // 跳过系统表
                if (tableName.toLowerCase().startsWith("sys_") || 
                    tableName.toLowerCase().startsWith("information_schema")) {
                    continue;
                }
                
                schemaText.append("表名: ").append(tableName);
                if (tableComment != null && !tableComment.isEmpty()) {
                    schemaText.append(" (").append(tableComment).append(")");
                }
                schemaText.append("\n");
                
                // 获取该表的所有字段
                ResultSet columns = metaData.getColumns(null, null, tableName, "%");
                while (columns.next()) {
                    String colName = columns.getString("COLUMN_NAME");
                    String colType = columns.getString("TYPE_NAME");
                    String colComment = columns.getString("REMARKS");
                    
                    schemaText.append("  - 字段: ").append(colName)
                              .append(", 类型: ").append(colType);
                    
                    if (colComment != null && !colComment.isEmpty()) {
                        schemaText.append(", 说明: ").append(colComment);
                    }
                    schemaText.append("\n");
                }
                schemaText.append("\n---\n");
            }
        } catch (Exception e) {
            throw new RuntimeException("提取 Schema 失败: " + e.getMessage(), e);
        }
        
        return schemaText.toString();
    }

    /**
     * 根据关键词过滤相关的表（用于减少 Token 消耗）
     */
    public String extractRelevantSchemas(String userQuestion) {
        // 简单实现：提取所有表，实际项目中可以根据问题关键词智能筛选
        return extractAllSchemas();
    }

    /**
     * 获取表的枚举值映射（如果有的话）
     */
    public Map<String, String> getEnumMappings(String tableName) {
        // 这里可以扩展：从配置或数据库中读取字段的枚举值映射
        Map<String, String> mappings = new HashMap<>();
        
        // 示例：
        if ("orders".equals(tableName)) {
            mappings.put("status", "1=待支付, 2=已支付, 3=已取消, 4=已完成");
        }
        
        return mappings;
    }
}
