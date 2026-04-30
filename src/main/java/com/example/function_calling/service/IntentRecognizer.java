package com.example.function_calling.service;

import com.example.function_calling.model.Intent;
import org.springframework.stereotype.Service;

@Service
public class IntentRecognizer {

    /**
     * 优化 1：移除 LLM 意图识别，改为基于关键词规则匹配（0ms 延迟）
     */
    public Intent recognize(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return Intent.GENERAL;
        }
        String msg = userMessage.toLowerCase();

        // BI_ANALYSIS: 数据库查询、数据分析、统计、图表等
        if (msg.contains("查询") || msg.contains("统计") || msg.contains("分析") || 
            msg.contains("图表") || msg.contains("趋势") || msg.contains("对比") ||
            msg.contains("销售") || msg.contains("订单") || msg.contains("数据") ||
            msg.contains("报表") || msg.contains("可视化") || msg.contains("分布")) {
            return Intent.BI_ANALYSIS;
        }

        // WEATHER: 天气相关
        if (msg.contains("天气") || msg.contains("气温") || msg.contains("下雨") || 
            msg.contains("晴天") || msg.contains("阴天") || msg.contains("温度")) {
            return Intent.WEATHER;
        }

        // MATH: 数学计算
        if (msg.contains("计算") || msg.contains("等于") || msg.contains("多少") || 
            msg.matches(".*\\d+\\s*[*+/\\-]\\s*\\d+.*")) {
            return Intent.MATH;
        }

        // CODE: 编程相关
        if (msg.contains("代码") || msg.contains("编程") || msg.contains("java") || 
            msg.contains("python") || msg.contains("函数") || msg.contains("算法")) {
            return Intent.CODE;
        }

        // RAG: 知识库检索（优先于通用知识问答）
        if (msg.contains("知识库") || msg.contains("文档中") || msg.contains("根据文档") ||
            msg.contains("根据资料") || msg.contains("检索") || msg.contains("资料库") ||
            msg.contains("公司文档") || msg.contains("内部文档")) {
            return Intent.RAG;
        }

        // KNOWLEDGE: 知识问答（什么是、为什么、如何等）
        if (msg.contains("是什么") || msg.contains("为什么") || msg.contains("怎么做") ||
            msg.contains("介绍一下") || msg.contains("定义")) {
            return Intent.KNOWLEDGE;
        }

        return Intent.GENERAL;
    }
}
