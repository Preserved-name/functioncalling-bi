package com.example.function_calling.controller;

import com.example.function_calling.service.AgentDispatcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);
    private final AgentDispatcher agentDispatcher;
    private final StreamingChatLanguageModel streamingModel;

    public ChatController(AgentDispatcher agentDispatcher, StreamingChatLanguageModel streamingModel) {
        this.agentDispatcher = agentDispatcher;
        this.streamingModel = streamingModel;
    }

    /**
     * 普通聊天接口（非流式）
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        AgentDispatcher.AgentResponse response = agentDispatcher.dispatch(request.getMessage());
        
        Map<String, Object> result = new HashMap<>();
        result.put("intent", response.getIntent().name());
        result.put("intentDescription", response.getIntent().getDescription());
        result.put("agentName", response.getAgentName());
        result.put("response", response.getResponse());
        
        return result;
    }

    /**
     * 流式聊天接口
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时
        
        CompletableFuture.runAsync(() -> {
            try {
                // 发送意图识别信息
                String message = request.getMessage();
                var intent = agentDispatcher.recognizeIntent(message);
                var agent = agentDispatcher.getAgentByIntent(intent);
                
                Map<String, Object> metaInfo = new HashMap<>();
                metaInfo.put("type", "meta");
                metaInfo.put("intent", intent.name());
                metaInfo.put("intentDescription", intent.getDescription());
                metaInfo.put("agentName", agent.getName());
                emitter.send(SseEmitter.event()
                        .name("meta")
                        .data(metaInfo));
                
                // 流式发送回答（逐字符发送，立即flush）
                String response = agent.handle(message);
                
                StringBuilder accumulatedResponse = new StringBuilder();
                for (int i = 0; i < response.length(); i++) {
                    char c = response.charAt(i);
                    accumulatedResponse.append(c);
                    
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("type", "chunk");
                    chunkData.put("content", String.valueOf(c));
                    chunkData.put("accumulated", accumulatedResponse.toString());
                    
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(chunkData));
                    
                    // 每5个字符稍微延迟，避免网络拥堵
                    if (i % 10 == 0) { // 每10个字符延迟1ms
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                // 检查是否包含图表事件标记
                String responseStr = accumulatedResponse.toString();
                String cleanResponse = responseStr;
                boolean hasChart = false;
                
                if (responseStr.contains("\"type\": \"chart_event\"")) {
                    try {
                        log.info("检测到图表数据，正在解析...");
                        // 提取 JSON 部分
                        int start = responseStr.indexOf("{");
                        int end = responseStr.lastIndexOf("}") + 1;
                        if (start != -1 && end > start) {
                            String chartJson = responseStr.substring(start, end);
                            log.debug("发送图表事件: {}", chartJson);
                            
                            // 提取 JSON 之前的纯文本部分
                            cleanResponse = responseStr.substring(0, start).trim();
                            hasChart = true;
                            
                            // 发送图表事件
                            emitter.send(SseEmitter.event()
                                    .name("chart")
                                    .data(chartJson));
                        }
                    } catch (Exception e) {
                        log.error("解析或发送图表数据失败", e);
                    }
                }
                
                // 发送完成信号（使用清理后的文本）
                Map<String, Object> completeData = new HashMap<>();
                completeData.put("type", "complete");
                completeData.put("response", cleanResponse);
                
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(completeData));
                
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        return result;
    }

    public static class ChatRequest {
        private String message;
        private String sessionId; // 会话 ID，用于记忆功能

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
