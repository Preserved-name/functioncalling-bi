package com.example.function_calling.service;

import com.example.function_calling.config.SecurityConfig;
import com.example.function_calling.model.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前端指令执行器
 * 负责验证和执行 AI 返回的前端指令（navigate、openModal 等）
 */
@Service
public class ActionExecutor {
    
    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);
    
    private final SecurityConfig securityConfig;
    
    public ActionExecutor(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }
    
    /**
     * 验证并执行指令
     * 
     * @param actionData 指令数据
     * @return 执行结果
     */
    public ActionResult execute(AiResponse.ActionData actionData) {
        if (actionData == null) {
            return ActionResult.error("指令数据为空");
        }
        
        String command = actionData.getCommand();
        if (command == null || command.isEmpty()) {
            return ActionResult.error("指令类型为空");
        }
        
        // 验证安全性
        SecurityCheckResult securityCheck = checkSecurity(actionData);
        if (!securityCheck.isAllowed()) {
            log.warn("指令被安全策略阻止: command={}, reason={}", command, securityCheck.getReason());
            return ActionResult.error("指令被安全策略阻止: " + securityCheck.getReason());
        }
        
        // 根据指令类型执行
        switch (command.toLowerCase()) {
            case "navigate":
                return handleNavigate(actionData.getParams());
            case "openmodal":
                return handleOpenModal(actionData.getParams());
            case "closemodal":
                return handleCloseModal(actionData.getParams());
            case "refresh":
                return handleRefresh(actionData.getParams());
            case "custom":
                return handleCustom(actionData);
            default:
                return ActionResult.error("不支持的指令类型: " + command);
        }
    }
    
    /**
     * 安全检查
     */
    private SecurityCheckResult checkSecurity(AiResponse.ActionData actionData) {
        String command = actionData.getCommand();
        Map<String, Object> params = actionData.getParams();
        
        // 检查是否需要用户确认
        if (actionData.getSecurity() != null && actionData.getSecurity().getRequiresConfirmation()) {
            log.info("指令需要用户确认: command={}", command);
            return SecurityCheckResult.requiresConfirmation(
                actionData.getSecurity().getConfirmationMessage()
            );
        }
        
        // 检查白名单
        switch (command.toLowerCase()) {
            case "navigate":
                if (params != null && params.containsKey("url")) {
                    String url = (String) params.get("url");
                    if (!isUrlWhitelisted(url)) {
                        return SecurityCheckResult.denied("URL 不在白名单中: " + url);
                    }
                }
                break;
            case "openmodal":
                if (params != null && params.containsKey("modalId")) {
                    String modalId = (String) params.get("modalId");
                    if (!isModalWhitelisted(modalId)) {
                        return SecurityCheckResult.denied("弹窗 ID 不在白名单中: " + modalId);
                    }
                }
                break;
        }
        
        return SecurityCheckResult.allowed();
    }
    
    /**
     * 检查 URL 是否在白名单中
     */
    private boolean isUrlWhitelisted(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // 检查是否为相对路径
        if (url.startsWith("/")) {
            return securityConfig.getAllowedUrlPrefixes().stream()
                .anyMatch(prefix -> url.startsWith(prefix));
        }
        
        // 绝对路径需要更严格的检查（可选：完全禁止）
        log.warn("检测到绝对路径 URL: {}", url);
        return false;
    }
    
    /**
     * 检查弹窗 ID 是否在白名单中
     */
    private boolean isModalWhitelisted(String modalId) {
        return modalId != null && securityConfig.getAllowedModalIds().contains(modalId);
    }
    
    /**
     * 处理页面跳转
     */
    private ActionResult handleNavigate(Map<String, Object> params) {
        if (params == null || !params.containsKey("url")) {
            return ActionResult.error("缺少必需参数: url");
        }
        
        String url = (String) params.get("url");
        log.info("执行页面跳转: url={}", url);
        
        return ActionResult.success("navigate", params);
    }
    
    /**
     * 处理打开弹窗
     */
    private ActionResult handleOpenModal(Map<String, Object> params) {
        if (params == null || !params.containsKey("modalId")) {
            return ActionResult.error("缺少必需参数: modalId");
        }
        
        String modalId = (String) params.get("modalId");
        log.info("打开弹窗: modalId={}", modalId);
        
        return ActionResult.success("openModal", params);
    }
    
    /**
     * 处理关闭弹窗
     */
    private ActionResult handleCloseModal(Map<String, Object> params) {
        log.info("关闭弹窗");
        return ActionResult.success("closeModal", params);
    }
    
    /**
     * 处理刷新
     */
    private ActionResult handleRefresh(Map<String, Object> params) {
        log.info("刷新页面");
        return ActionResult.success("refresh", params);
    }
    
    /**
     * 处理自定义指令
     */
    private ActionResult handleCustom(AiResponse.ActionData actionData) {
        log.info("执行自定义指令: command={}", actionData.getCommand());
        return ActionResult.success("custom", actionData.getParams());
    }
    
    /**
     * 指令执行结果
     */
    public static class ActionResult {
        private final boolean success;
        private final String command;
        private final Map<String, Object> params;
        private final String errorMessage;
        private final boolean requiresConfirmation;
        private final String confirmationMessage;
        
        private ActionResult(boolean success, String command, Map<String, Object> params,
                           String errorMessage, boolean requiresConfirmation, String confirmationMessage) {
            this.success = success;
            this.command = command;
            this.params = params;
            this.errorMessage = errorMessage;
            this.requiresConfirmation = requiresConfirmation;
            this.confirmationMessage = confirmationMessage;
        }
        
        public static ActionResult success(String command, Map<String, Object> params) {
            return new ActionResult(true, command, params, null, false, null);
        }
        
        public static ActionResult error(String message) {
            return new ActionResult(false, null, null, message, false, null);
        }
        
        public static ActionResult requiresConfirmation(String message) {
            return new ActionResult(false, null, null, null, true, message);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getCommand() { return command; }
        public Map<String, Object> getParams() { return params; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public String getConfirmationMessage() { return confirmationMessage; }
    }
    
    /**
     * 安全检查结果
     */
    private static class SecurityCheckResult {
        private final boolean allowed;
        private final String reason;
        private final boolean requiresConfirmation;
        private final String confirmationMessage;
        
        private SecurityCheckResult(boolean allowed, String reason, 
                                   boolean requiresConfirmation, String confirmationMessage) {
            this.allowed = allowed;
            this.reason = reason;
            this.requiresConfirmation = requiresConfirmation;
            this.confirmationMessage = confirmationMessage;
        }
        
        public static SecurityCheckResult allowed() {
            return new SecurityCheckResult(true, null, false, null);
        }
        
        public static SecurityCheckResult denied(String reason) {
            return new SecurityCheckResult(false, reason, false, null);
        }
        
        public static SecurityCheckResult requiresConfirmation(String message) {
            return new SecurityCheckResult(false, null, true, message);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public String getConfirmationMessage() { return confirmationMessage; }
    }
}
