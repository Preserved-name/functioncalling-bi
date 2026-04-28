package com.example.function_calling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置属性
 */
@Component
@ConfigurationProperties(prefix = "ai.security")
public class SecurityConfig {
    
    /**
     * 允许跳转的 URL 前缀白名单
     */
    private List<String> allowedUrlPrefixes = new ArrayList<>();
    
    /**
     * 允许打开的弹窗 ID 白名单
     */
    private List<String> allowedModalIds = new ArrayList<>();
    
    /**
     * 是否需要用户确认危险操作
     */
    private boolean requireConfirmationForDangerousActions = true;
    
    public List<String> getAllowedUrlPrefixes() {
        return allowedUrlPrefixes;
    }
    
    public void setAllowedUrlPrefixes(List<String> allowedUrlPrefixes) {
        this.allowedUrlPrefixes = allowedUrlPrefixes;
    }
    
    public List<String> getAllowedModalIds() {
        return allowedModalIds;
    }
    
    public void setAllowedModalIds(List<String> allowedModalIds) {
        this.allowedModalIds = allowedModalIds;
    }
    
    public boolean isRequireConfirmationForDangerousActions() {
        return requireConfirmationForDangerousActions;
    }
    
    public void setRequireConfirmationForDangerousActions(boolean requireConfirmationForDangerousActions) {
        this.requireConfirmationForDangerousActions = requireConfirmationForDangerousActions;
    }
}
