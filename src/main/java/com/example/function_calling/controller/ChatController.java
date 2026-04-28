package com.example.function_calling.controller;

import com.example.function_calling.model.AiResponse;
import com.example.function_calling.service.ActionExecutor;
import com.example.function_calling.service.AgentDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final AgentDispatcher agentDispatcher;
    private final ActionExecutor actionExecutor;
    private final com.example.function_calling.agent.StreamingBiAgent streamingBiAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(AgentDispatcher agentDispatcher, ActionExecutor actionExecutor, 
                         com.example.function_calling.agent.StreamingBiAgent streamingBiAgent) {
        this.agentDispatcher = agentDispatcher;
        this.actionExecutor = actionExecutor;
        this.streamingBiAgent = streamingBiAgent;
    }

    /**
     * 普通聊天接口（非流式）- 返回统一响应格式
     */
    @PostMapping("/chat")
    public AiResponse chat(@RequestBody ChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        
        // 分发到对应 Agent（传递 sessionId）
        AgentDispatcher.AgentResponse response = agentDispatcher.dispatch(request.getMessage(), sessionId);
        
        // 尝试解析 AI 返回的内容，判断是否为结构化 JSON
        AiResponse aiResponse = parseAiResponse(response, requestId, sessionId);
        
        return aiResponse;
    }

    /**
     * 流式聊天接口 - 支持多类型响应（真正流式）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时
        String requestId = UUID.randomUUID().toString();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 发送元信息
                var intent = agentDispatcher.recognizeIntent(request.getMessage());
                var agent = agentDispatcher.getAgentByIntent(intent);
                
                Map<String, Object> metaInfo = new HashMap<>();
                metaInfo.put("requestId", requestId);
                metaInfo.put("sessionId", sessionId);
                metaInfo.put("intent", intent.name());
                metaInfo.put("intentDescription", intent.getDescription());
                metaInfo.put("agentName", agent.getName());
                
                sendSseEvent(emitter, "meta", metaInfo);
                
                // 2. 根据 Agent 类型选择流式策略
                if (intent == com.example.function_calling.model.Intent.BI_ANALYSIS) {
                    // BI Agent：使用真正的流式输出（传递 sessionId）
                    handleStreamingBiAgent(emitter, request.getMessage(), requestId, sessionId, intent, agent.getName());
                } else {
                    // 其他 Agent：使用原有方式（等待完整响应后流式发送，传递 sessionId）
                    String responseText;
                    if (agent instanceof com.example.function_calling.agent.WeatherAgent) {
                        responseText = ((com.example.function_calling.agent.WeatherAgent) agent).handleWithSession(sessionId, request.getMessage());
                    } else if (agent instanceof com.example.function_calling.agent.MathAgent) {
                        responseText = ((com.example.function_calling.agent.MathAgent) agent).handleWithSession(sessionId, request.getMessage());
                    } else if (agent instanceof com.example.function_calling.agent.KnowledgeAgent) {
                        responseText = ((com.example.function_calling.agent.KnowledgeAgent) agent).handleWithSession(sessionId, request.getMessage());
                    } else if (agent instanceof com.example.function_calling.agent.CodeAgent) {
                        responseText = ((com.example.function_calling.agent.CodeAgent) agent).handleWithSession(sessionId, request.getMessage());
                    } else if (agent instanceof com.example.function_calling.agent.GeneralAgent) {
                        responseText = ((com.example.function_calling.agent.GeneralAgent) agent).handleWithSession(sessionId, request.getMessage());
                    } else {
                        responseText = agent.handle(request.getMessage());
                    }
                    handleStreamingText(emitter, responseText);
                }
                
                // 3. 发送完成信号
                Map<String, Object> completeData = new HashMap<>();
                completeData.put("status", "success");
                completeData.put("requestId", requestId);
                sendSseEvent(emitter, "complete", completeData);
                
                emitter.complete();
            } catch (IOException e) {
                log.error("流式响应错误", e);
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * 处理 BI Agent 的真正流式输出（使用 Function Calling）
     */
    private void handleStreamingBiAgent(SseEmitter emitter, String userMessage, 
                                        String requestId, String sessionId, 
                                        com.example.function_calling.model.Intent intent, String agentName) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        streamingBiAgent.handleStreamingWithSession(sessionId, userMessage,
            // onToken: 实时推送每个 token（只发送文本）
            token -> {
                try {
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("delta", token);
                    sendSseEvent(emitter, "chunk", chunkData);
                    fullResponse.append(token);
                } catch (IOException e) {
                    log.error("发送 token 失败", e);
                }
            },
            // onChart: 收到图表数据时触发
            chartRequest -> {
                try {
                    log.info("========== 收到图表数据 ==========");
                    log.info("图表类型: {}", chartRequest.getChartType());
                    log.info("图表标题: {}", chartRequest.getTitle());
                    log.info("X轴标签: {}", chartRequest.getXAxis() != null ? chartRequest.getXAxis().getLabel() : "null");
                    log.info("Y轴标签: {}", chartRequest.getYAxis() != null ? chartRequest.getYAxis().getLabel() : "null");
                    log.info("Series数量: {}", chartRequest.getSeries() != null ? chartRequest.getSeries().size() : 0);
                    log.info("正在发送 event:chart...");
                    // 直接发送完整的图表数据
                    sendSseEvent(emitter, "chart", chartRequest);
                    log.info("event:chart 已发送");
                } catch (IOException e) {
                    log.error("发送图表数据失败", e);
                }
            },
            // onComplete: 完成回调
            completeText -> {
                try {
                    // 不再需要解析 JSON，因为图表已经通过 Function Calling 分离
                    log.debug("流式输出完成，总长度: {}", completeText.length());
                } catch (Exception e) {
                    log.error("处理完整响应失败", e);
                } finally {
                    latch.countDown();
                }
            }
        );
        
        // 等待流式完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 尝试解析结构化响应
     */
    private AiResponse tryParseStructuredResponse(String responseText, String requestId, 
                                                   String sessionId, com.example.function_calling.model.Intent intent, String agentName) {
        try {
            // 查找 JSON 部分（可能在文本后面）
            int jsonStart = responseText.indexOf("{");
            if (jsonStart == -1) {
                return null;
            }
            
            // 提取 JSON 部分（从最后一个 { 开始，确保是完整的 JSON）
            String jsonPart = responseText.substring(jsonStart);
            
            // 尝试找到 JSON 的结束位置
            int braceCount = 0;
            int jsonEnd = -1;
            for (int i = 0; i < jsonPart.length(); i++) {
                char c = jsonPart.charAt(i);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        jsonEnd = i + 1;
                        break;
                    }
                }
            }
            
            if (jsonEnd == -1) {
                log.debug("未找到完整的 JSON 结构");
                return null;
            }
            
            jsonPart = jsonPart.substring(0, jsonEnd);
            JsonNode jsonNode = objectMapper.readTree(jsonPart);
            
            // 检查是否有 type 字段
            if (!jsonNode.has("type")) {
                return null;
            }
            
            String type = jsonNode.get("type").asText();
            
            // 构建统一的 AiResponse
            AiResponse.Builder builder = AiResponse.builder()
                .version("1.0")
                .requestId(requestId)
                .sessionId(sessionId)
                .meta(AiResponse.Meta.builder()
                    .timestamp(System.currentTimeMillis())
                    .agentName(agentName)
                    .intent(intent.name())
                    .intentDescription(intent.getDescription())
                    .build());
            
            switch (type.toLowerCase()) {
                case "chart":
                    builder.type(AiResponse.ResponseType.CHART);
                    // 解析图表数据
                    AiResponse.Content content = parseChartContent(jsonNode);
                    builder.content(content);
                    break;
                case "action":
                    builder.type(AiResponse.ResponseType.ACTION);
                    AiResponse.Content actionContent = parseActionContent(jsonNode);
                    builder.content(actionContent);
                    break;
                case "chat":
                default:
                    builder.type(AiResponse.ResponseType.CHAT);
                    String text = jsonNode.has("content") && jsonNode.get("content").has("text") 
                        ? jsonNode.get("content").get("text").asText() 
                        : responseText;
                    builder.content(AiResponse.Content.builder().text(text).build());
                    break;
            }
            
            return builder.build();
        } catch (Exception e) {
            log.debug("无法解析为结构化响应，使用纯文本模式", e);
            return null;
        }
    }

    /**
     * 解析图表内容
     */
    private AiResponse.Content parseChartContent(JsonNode jsonNode) {
        try {
            JsonNode contentNode = jsonNode.get("content");
            if (contentNode == null) {
                return AiResponse.Content.builder().text("").build();
            }
            
            String text = contentNode.has("text") ? contentNode.get("text").asText() : "";
            JsonNode dataNode = contentNode.get("data");
            
            if (dataNode == null) {
                return AiResponse.Content.builder().text(text).build();
            }
            
            // 解析图表数据
            AiResponse.ChartData chartData = objectMapper.treeToValue(dataNode, AiResponse.ChartData.class);
            
            // 如果 series 为空，根据 xAxis 和 yAxis 自动生成
            if ((chartData.getSeries() == null || chartData.getSeries().isEmpty()) 
                && chartData.getXAxis() != null && chartData.getYAxis() != null) {
                
                List<String> xData = chartData.getXAxis().getData();
                List<String> yData = chartData.getYAxis().getData();
                
                if (xData != null && yData != null && !yData.isEmpty()) {
                    // 转换 yAxis 数据为 Object 类型
                    List<Object> seriesData = new java.util.ArrayList<>();
                    for (String val : yData) {
                        try {
                            seriesData.add(Double.parseDouble(val));
                        } catch (NumberFormatException e) {
                            seriesData.add(val);
                        }
                    }
                    
                    AiResponse.ChartData.Series series = AiResponse.ChartData.Series.builder()
                        .name(chartData.getTitle() != null ? chartData.getTitle() : "数据")
                        .data(seriesData)
                        .build();
                    
                    chartData.setSeries(java.util.Collections.singletonList(series));
                }
            }
            
            return AiResponse.Content.builder()
                .text(text)
                .data(chartData)
                .build();
        } catch (Exception e) {
            log.error("解析图表数据失败", e);
            return AiResponse.Content.builder().text("").build();
        }
    }

    /**
     * 解析指令内容
     */
    private AiResponse.Content parseActionContent(JsonNode jsonNode) {
        try {
            JsonNode contentNode = jsonNode.get("content");
            if (contentNode == null) {
                return AiResponse.Content.builder().text("").build();
            }
            
            String text = contentNode.has("text") ? contentNode.get("text").asText() : "";
            JsonNode actionNode = contentNode.get("action");
            
            if (actionNode == null) {
                return AiResponse.Content.builder().text(text).build();
            }
            
            // 解析指令数据
            AiResponse.ActionData actionData = objectMapper.treeToValue(actionNode, AiResponse.ActionData.class);
            
            return AiResponse.Content.builder()
                .text(text)
                .action(actionData)
                .build();
        } catch (Exception e) {
            log.error("解析指令数据失败", e);
            return AiResponse.Content.builder().text("").build();
        }
    }

    /**
     * 处理结构化响应
     */
    private void handleStructuredResponse(SseEmitter emitter, AiResponse response) throws IOException {
        // 先发送文本部分（如果有）
        if (response.getContent() != null && response.getContent().getText() != null) {
            String text = response.getContent().getText();
            for (int i = 0; i < text.length(); i++) {
                Map<String, Object> chunkData = new HashMap<>();
                chunkData.put("delta", String.valueOf(text.charAt(i)));
                sendSseEvent(emitter, "chunk", chunkData);
                
                // 每10个字符稍微延迟
                if (i % 10 == 0) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // 根据类型发送结构化数据
        if (response.getType() == AiResponse.ResponseType.CHART && response.getContent().getData() != null) {
            sendSseEvent(emitter, "chart", response.getContent().getData());
        } else if (response.getType() == AiResponse.ResponseType.ACTION && response.getContent().getAction() != null) {
            // 验证并执行指令
            ActionExecutor.ActionResult result = actionExecutor.execute(response.getContent().getAction());
            if (result.isSuccess()) {
                sendSseEvent(emitter, "action", result);
            } else if (result.isRequiresConfirmation()) {
                Map<String, Object> confirmData = new HashMap<>();
                confirmData.put("requiresConfirmation", true);
                confirmData.put("message", result.getConfirmationMessage());
                sendSseEvent(emitter, "confirm", confirmData);
            }
        }
    }

    /**
     * 处理流式文本
     */
    private void handleStreamingText(SseEmitter emitter, String text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("delta", String.valueOf(text.charAt(i)));
            sendSseEvent(emitter, "chunk", chunkData);
            
            if (i % 10 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 发送 SSE 事件
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
            .name(eventName)
            .data(data));
    }

    /**
     * 解析 AI 响应为统一格式
     */
    private AiResponse parseAiResponse(AgentDispatcher.AgentResponse response, String requestId, String sessionId) {
        String responseText = response.getResponse();
        
        // 尝试解析为结构化响应
        AiResponse structuredResponse = tryParseStructuredResponse(responseText, requestId, sessionId, 
            response.getIntent(), response.getAgentName());
        
        if (structuredResponse != null) {
            return structuredResponse;
        }
        
        // 默认作为纯文本响应
        return AiResponse.builder()
            .version("1.0")
            .requestId(requestId)
            .sessionId(sessionId)
            .type(AiResponse.ResponseType.CHAT)
            .content(AiResponse.Content.builder()
                .text(responseText)
                .format("plain")
                .build())
            .meta(AiResponse.Meta.builder()
                .timestamp(System.currentTimeMillis())
                .agentName(response.getAgentName())
                .intent(response.getIntent().name())
                .intentDescription(response.getIntent().getDescription())
                .build())
            .build();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        return result;
    }

    public static class ChatRequest {
        private String message;
        private String sessionId;

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
