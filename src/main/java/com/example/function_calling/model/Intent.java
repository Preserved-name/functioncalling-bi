package com.example.function_calling.model;

public enum Intent {
    WEATHER("天气查询"),
    MATH("数学计算"),
    KNOWLEDGE("知识问答"),
    CODE("代码助手"),
    BI_ANALYSIS("数据分析"),
    GENERAL("通用对话");

    private final String description;

    Intent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
