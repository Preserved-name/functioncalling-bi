package com.example.function_calling.agent;

import com.example.function_calling.model.Intent;

public interface Agent {
    
    /**
     * 获取 Agent 支持的意图类型
     */
    Intent getSupportedIntent();
    
    /**
     * 处理用户问题
     */
    String handle(String userMessage);
    
    /**
     * 获取 Agent 名称
     */
    String getName();
}
