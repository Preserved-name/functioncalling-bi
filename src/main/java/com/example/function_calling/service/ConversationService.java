package com.example.function_calling.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理服务 - 管理用户对话历史和记忆
 */
@Service
public class ConversationService {

    // 存储每个会话的记忆（sessionId -> ChatMemory）
    private final Map<String, MessageWindowChatMemory> conversationMemories = new ConcurrentHashMap<>();
    
    // 存储用户偏好信息（sessionId -> user preferences）
    private final Map<String, Map<String, Object>> userPreferences = new ConcurrentHashMap<>();
    
    // 默认记忆窗口大小（保留最近的消息数量）
    private static final int DEFAULT_MEMORY_SIZE = 20;

    /**
     * 获取或创建会话记忆
     */
    public MessageWindowChatMemory getOrCreateMemory(String sessionId) {
        return conversationMemories.computeIfAbsent(sessionId, 
            id -> MessageWindowChatMemory.withMaxMessages(DEFAULT_MEMORY_SIZE));
    }

    /**
     * 添加用户消息到记忆
     */
    public void addUserMessage(String sessionId, String message) {
        MessageWindowChatMemory memory = getOrCreateMemory(sessionId);
        memory.add(UserMessage.from(message));
    }

    /**
     * 添加 AI 回复到记忆
     */
    public void addAiMessage(String sessionId, String message) {
        MessageWindowChatMemory memory = getOrCreateMemory(sessionId);
        memory.add(AiMessage.from(message));
    }

    /**
     * 获取会话历史
     */
    public java.util.List<ChatMessage> getConversationHistory(String sessionId) {
        MessageWindowChatMemory memory = conversationMemories.get(sessionId);
        if (memory == null) {
            return java.util.Collections.emptyList();
        }
        return memory.messages();
    }

    /**
     * 保存用户偏好
     */
    public void saveUserPreference(String sessionId, String key, Object value) {
        userPreferences.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                      .put(key, value);
    }

    /**
     * 获取用户偏好
     */
    public Object getUserPreference(String sessionId, String key) {
        Map<String, Object> prefs = userPreferences.get(sessionId);
        if (prefs == null) {
            return null;
        }
        return prefs.get(key);
    }

    /**
     * 获取所有用户偏好
     */
    public Map<String, Object> getAllUserPreferences(String sessionId) {
        return userPreferences.getOrDefault(sessionId, new ConcurrentHashMap<>());
    }

    /**
     * 清除会话记忆
     */
    public void clearMemory(String sessionId) {
        conversationMemories.remove(sessionId);
        userPreferences.remove(sessionId);
    }

    /**
     * 获取记忆中的消息数量
     */
    public int getMemorySize(String sessionId) {
        MessageWindowChatMemory memory = conversationMemories.get(sessionId);
        if (memory == null) {
            return 0;
        }
        return memory.messages().size();
    }

    /**
     * 生成系统提示，包含用户偏好信息
     */
    public String generateSystemPrompt(String sessionId) {
        Map<String, Object> prefs = getAllUserPreferences(sessionId);
        if (prefs.isEmpty()) {
            return "你是一个友好、专业的AI助手。";
        }
        
        StringBuilder prompt = new StringBuilder("你是一个友好、专业的AI助手。\n\n");
        prompt.append("已知用户信息：\n");
        
        prefs.forEach((key, value) -> {
            prompt.append("- ").append(key).append(": ").append(value).append("\n");
        });
        
        prompt.append("\n请根据这些信息提供更个性化的回答。");
        return prompt.toString();
    }
}
