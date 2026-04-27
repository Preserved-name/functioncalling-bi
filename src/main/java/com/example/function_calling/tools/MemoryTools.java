package com.example.function_calling.tools;

import com.example.function_calling.service.ConversationService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 记忆工具类 - 让 AI 能够主动存储和检索用户信息
 */
@Component
public class MemoryTools {

    private final ConversationService conversationService;

    public MemoryTools(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 保存用户偏好或信息
     */
    @Tool("保存用户的偏好设置或个人信息，例如：用户的姓名、喜好、城市等。参数：key是信息的键名，value是信息值")
    public String saveUserPreference(String sessionId, String key, String value) {
        conversationService.saveUserPreference(sessionId, key, value);
        return "已保存：" + key + " = " + value;
    }

    /**
     * 获取用户偏好
     */
    @Tool("获取用户的特定偏好或信息。参数：key是要查询的信息键名")
    public String getUserPreference(String sessionId, String key) {
        Object value = conversationService.getUserPreference(sessionId, key);
        if (value == null) {
            return "未找到信息：" + key;
        }
        return key + " = " + value.toString();
    }

    /**
     * 获取所有用户偏好
     */
    @Tool("获取用户的所有偏好和信息")
    public String getAllUserPreferences(String sessionId) {
        var prefs = conversationService.getAllUserPreferences(sessionId);
        if (prefs.isEmpty()) {
            return "暂无用户偏好信息";
        }
        
        StringBuilder sb = new StringBuilder("用户偏好信息：\n");
        prefs.forEach((key, value) -> {
            sb.append("- ").append(key).append(": ").append(value).append("\n");
        });
        return sb.toString();
    }

    /**
     * 清除用户记忆
     */
    @Tool("清除用户的所有记忆和偏好信息")
    public String clearMemory(String sessionId) {
        conversationService.clearMemory(sessionId);
        return "已清除所有记忆";
    }
}
