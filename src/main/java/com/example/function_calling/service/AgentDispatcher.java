package com.example.function_calling.service;

import com.example.function_calling.agent.Agent;
import com.example.function_calling.model.Intent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentDispatcher {

    private final IntentRecognizer intentRecognizer;
    private final Map<Intent, Agent> agentMap;

    public AgentDispatcher(IntentRecognizer intentRecognizer, List<Agent> agents) {
        this.intentRecognizer = intentRecognizer;
        // 将 Agent 列表转换为 Map，key 为意图类型
        this.agentMap = agents.stream()
                .collect(Collectors.toMap(Agent::getSupportedIntent, agent -> agent));
    }

    /**
     * 根据用户问题分发到对应的 Agent
     */
    public AgentResponse dispatch(String userMessage) {
        return dispatch(userMessage, "default-session");
    }
    
    /**
     * 根据用户问题分发到对应的 Agent（带会话 ID）
     */
    public AgentResponse dispatch(String userMessage, String sessionId) {
        // 1. 识别意图
        Intent intent = intentRecognizer.recognize(userMessage);
        
        // 2. 获取对应的 Agent
        Agent agent = agentMap.get(intent);
        if (agent == null) {
            agent = agentMap.get(Intent.GENERAL);
        }
        
        // 3. 处理问题（传递 sessionId）
        String response;
        if (agent instanceof com.example.function_calling.agent.WeatherAgent) {
            response = ((com.example.function_calling.agent.WeatherAgent) agent).handleWithSession(sessionId, userMessage);
        } else if (agent instanceof com.example.function_calling.agent.MathAgent) {
            response = ((com.example.function_calling.agent.MathAgent) agent).handleWithSession(sessionId, userMessage);
        } else if (agent instanceof com.example.function_calling.agent.KnowledgeAgent) {
            response = ((com.example.function_calling.agent.KnowledgeAgent) agent).handleWithSession(sessionId, userMessage);
        } else if (agent instanceof com.example.function_calling.agent.CodeAgent) {
            response = ((com.example.function_calling.agent.CodeAgent) agent).handleWithSession(sessionId, userMessage);
        } else if (agent instanceof com.example.function_calling.agent.StreamingBiAgent) {
            response = ((com.example.function_calling.agent.StreamingBiAgent) agent).handleWithSession(sessionId, userMessage);
        } else if (agent instanceof com.example.function_calling.agent.RagAgent) {
            response = ((com.example.function_calling.agent.RagAgent) agent).handleWithSession(sessionId, userMessage);
        } else if (agent instanceof com.example.function_calling.agent.GeneralAgent) {
            response = ((com.example.function_calling.agent.GeneralAgent) agent).handleWithSession(sessionId, userMessage);
        } else {
            response = agent.handle(userMessage);
        }
        
        return new AgentResponse(intent, agent.getName(), response);
    }

    /**
     * 识别意图（公开方法）
     */
    public Intent recognizeIntent(String userMessage) {
        return intentRecognizer.recognize(userMessage);
    }

    /**
     * 根据意图获取 Agent（公开方法）
     */
    public Agent getAgentByIntent(Intent intent) {
        Agent agent = agentMap.get(intent);
        if (agent == null) {
            agent = agentMap.get(Intent.GENERAL);
        }
        return agent;
    }

    /**
     * Agent 响应结果
     */
    public static class AgentResponse {
        private final Intent intent;
        private final String agentName;
        private final String response;

        public AgentResponse(Intent intent, String agentName, String response) {
            this.intent = intent;
            this.agentName = agentName;
            this.response = response;
        }

        public Intent getIntent() {
            return intent;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getResponse() {
            return response;
        }
    }
}
