package com.example.function_calling.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * AI 统一响应模型
 * 支持三种类型：chat（纯文本）、chart（图表）、action（前端指令）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiResponse {
    
    private String version;
    private String requestId;
    private String sessionId;
    private ResponseType type;
    private Content content;
    private Meta meta;
    
    public AiResponse() {}
    
    public AiResponse(String version, String requestId, String sessionId, ResponseType type, Content content, Meta meta) {
        this.version = version;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.type = type;
        this.content = content;
        this.meta = meta;
    }
    
    // Getters and Setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public ResponseType getType() { return type; }
    public void setType(ResponseType type) { this.type = type; }
    
    public Content getContent() { return content; }
    public void setContent(Content content) { this.content = content; }
    
    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String version;
        private String requestId;
        private String sessionId;
        private ResponseType type;
        private Content content;
        private Meta meta;
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder type(ResponseType type) {
            this.type = type;
            return this;
        }
        
        public Builder content(Content content) {
            this.content = content;
            return this;
        }
        
        public Builder meta(Meta meta) {
            this.meta = meta;
            return this;
        }
        
        public AiResponse build() {
            return new AiResponse(version, requestId, sessionId, type, content, meta);
        }
    }
    
    /**
     * 响应类型枚举
     */
    public enum ResponseType {
        CHAT("chat"),
        CHART("chart"),
        ACTION("action");
        
        private final String value;
        
        ResponseType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * 内容结构
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {
        private String text;
        private String format;
        private ChartData data;
        private ActionData action;
        
        public Content() {}
        
        public Content(String text, String format, ChartData data, ActionData action) {
            this.text = text;
            this.format = format;
            this.data = data;
            this.action = action;
        }
        
        // Getters and Setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public ChartData getData() { return data; }
        public void setData(ChartData data) { this.data = data; }
        
        public ActionData getAction() { return action; }
        public void setAction(ActionData action) { this.action = action; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String text;
            private String format;
            private ChartData data;
            private ActionData action;
            
            public Builder text(String text) {
                this.text = text;
                return this;
            }
            
            public Builder format(String format) {
                this.format = format;
                return this;
            }
            
            public Builder data(ChartData data) {
                this.data = data;
                return this;
            }
            
            public Builder action(ActionData action) {
                this.action = action;
                return this;
            }
            
            public Content build() {
                return new Content(text, format, data, action);
            }
        }
    }
    
    /**
     * 图表数据结构
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChartData {
        private String chartType;
        private String title;
        @com.fasterxml.jackson.annotation.JsonProperty("xAxis")
        private AxisConfig xAxis;
        @com.fasterxml.jackson.annotation.JsonProperty("yAxis")
        private AxisConfig yAxis;
        private List<Series> series;
        private Object rawData;
        
        public ChartData() {}
        
        // Getters and Setters
        public String getChartType() { return chartType; }
        public void setChartType(String chartType) { this.chartType = chartType; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        @com.fasterxml.jackson.annotation.JsonProperty("xAxis")
        public AxisConfig getXAxis() { return xAxis; }
        public void setXAxis(AxisConfig xAxis) { this.xAxis = xAxis; }
        
        @com.fasterxml.jackson.annotation.JsonProperty("yAxis")
        public AxisConfig getYAxis() { return yAxis; }
        public void setYAxis(AxisConfig yAxis) { this.yAxis = yAxis; }
        
        public List<Series> getSeries() { return series; }
        public void setSeries(List<Series> series) { this.series = series; }
        
        public Object getRawData() { return rawData; }
        public void setRawData(Object rawData) { this.rawData = rawData; }
        
        /**
         * 坐标轴配置
         */
        public static class AxisConfig {
            private String label;
            private List<String> data;
            
            public AxisConfig() {}
            
            public AxisConfig(String label, List<String> data) {
                this.label = label;
                this.data = data;
            }
            
            public String getLabel() { return label; }
            public void setLabel(String label) { this.label = label; }
            
            public List<String> getData() { return data; }
            public void setData(List<String> data) { this.data = data; }
            
            public static Builder builder() {
                return new Builder();
            }
            
            public static class Builder {
                private String label;
                private List<String> data;
                
                public Builder label(String label) {
                    this.label = label;
                    return this;
                }
                
                public Builder data(List<String> data) {
                    this.data = data;
                    return this;
                }
                
                public AxisConfig build() {
                    return new AxisConfig(label, data);
                }
            }
        }
        
        /**
         * 数据系列
         */
        public static class Series {
            private String name;
            private List<Object> data;
            private String color;
            private Boolean smooth;
            
            public Series() {}
            
            public Series(String name, List<Object> data, String color, Boolean smooth) {
                this.name = name;
                this.data = data;
                this.color = color;
                this.smooth = smooth;
            }
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public List<Object> getData() { return data; }
            public void setData(List<Object> data) { this.data = data; }
            
            public String getColor() { return color; }
            public void setColor(String color) { this.color = color; }
            
            public Boolean getSmooth() { return smooth; }
            public void setSmooth(Boolean smooth) { this.smooth = smooth; }
            
            public static Builder builder() {
                return new Builder();
            }
            
            public static class Builder {
                private String name;
                private List<Object> data;
                private String color;
                private Boolean smooth;
                
                public Builder name(String name) {
                    this.name = name;
                    return this;
                }
                
                public Builder data(List<Object> data) {
                    this.data = data;
                    return this;
                }
                
                public Builder color(String color) {
                    this.color = color;
                    return this;
                }
                
                public Builder smooth(Boolean smooth) {
                    this.smooth = smooth;
                    return this;
                }
                
                public Series build() {
                    return new Series(name, data, color, smooth);
                }
            }
        }
    }
    
    /**
     * 前端指令数据
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActionData {
        private String command;
        private Map<String, Object> params;
        private SecurityConfig security;
        
        public ActionData() {}
        
        public ActionData(String command, Map<String, Object> params, SecurityConfig security) {
            this.command = command;
            this.params = params;
            this.security = security;
        }
        
        // Getters and Setters
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        
        public SecurityConfig getSecurity() { return security; }
        public void setSecurity(SecurityConfig security) { this.security = security; }
        
        /**
         * 安全配置
         */
        public static class SecurityConfig {
            private Boolean whitelist;
            private Boolean requiresConfirmation;
            private String confirmationMessage;
            
            public SecurityConfig() {}
            
            public SecurityConfig(Boolean whitelist, Boolean requiresConfirmation, String confirmationMessage) {
                this.whitelist = whitelist;
                this.requiresConfirmation = requiresConfirmation;
                this.confirmationMessage = confirmationMessage;
            }
            
            public Boolean getWhitelist() { return whitelist; }
            public void setWhitelist(Boolean whitelist) { this.whitelist = whitelist; }
            
            public Boolean getRequiresConfirmation() { return requiresConfirmation; }
            public void setRequiresConfirmation(Boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
            
            public String getConfirmationMessage() { return confirmationMessage; }
            public void setConfirmationMessage(String confirmationMessage) { this.confirmationMessage = confirmationMessage; }
            
            public static Builder builder() {
                return new Builder();
            }
            
            public static class Builder {
                private Boolean whitelist;
                private Boolean requiresConfirmation;
                private String confirmationMessage;
                
                public Builder whitelist(Boolean whitelist) {
                    this.whitelist = whitelist;
                    return this;
                }
                
                public Builder requiresConfirmation(Boolean requiresConfirmation) {
                    this.requiresConfirmation = requiresConfirmation;
                    return this;
                }
                
                public Builder confirmationMessage(String confirmationMessage) {
                    this.confirmationMessage = confirmationMessage;
                    return this;
                }
                
                public SecurityConfig build() {
                    return new SecurityConfig(whitelist, requiresConfirmation, confirmationMessage);
                }
            }
        }
    }
    
    /**
     * 元数据
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {
        private Long timestamp;
        private String agentName;
        private String intent;
        private String intentDescription;
        
        public Meta() {}
        
        public Meta(Long timestamp, String agentName, String intent, String intentDescription) {
            this.timestamp = timestamp;
            this.agentName = agentName;
            this.intent = intent;
            this.intentDescription = intentDescription;
        }
        
        // Getters and Setters
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        
        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        
        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        
        public String getIntentDescription() { return intentDescription; }
        public void setIntentDescription(String intentDescription) { this.intentDescription = intentDescription; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private Long timestamp;
            private String agentName;
            private String intent;
            private String intentDescription;
            
            public Builder timestamp(Long timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public Builder agentName(String agentName) {
                this.agentName = agentName;
                return this;
            }
            
            public Builder intent(String intent) {
                this.intent = intent;
                return this;
            }
            
            public Builder intentDescription(String intentDescription) {
                this.intentDescription = intentDescription;
                return this;
            }
            
            public Meta build() {
                return new Meta(timestamp, agentName, intent, intentDescription);
            }
        }
    }
}
