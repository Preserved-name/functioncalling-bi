package com.example.function_calling.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 图表请求参数 - 用于 Function Calling
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChartRequest {
    
    private String chartType;  // bar, line, pie
    private String title;
    private AxisConfig xAxis;
    private AxisConfig yAxis;
    private List<Series> series;
    private Object rawData;
    
    public ChartRequest() {}
    
    public ChartRequest(String chartType, String title, AxisConfig xAxis, 
                       AxisConfig yAxis, List<Series> series, Object rawData) {
        this.chartType = chartType;
        this.title = title;
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        this.series = series;
        this.rawData = rawData;
    }
    
    // Getters and Setters
    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public AxisConfig getXAxis() { return xAxis; }
    public void setXAxis(AxisConfig xAxis) { this.xAxis = xAxis; }
    
    public AxisConfig getYAxis() { return yAxis; }
    public void setYAxis(AxisConfig yAxis) { this.yAxis = yAxis; }
    
    public List<Series> getSeries() { return series; }
    public void setSeries(List<Series> series) { this.series = series; }
    
    public Object getRawData() { return rawData; }
    public void setRawData(Object rawData) { this.rawData = rawData; }
    
    /**
     * 坐标轴配置
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
    }
    
    /**
     * 数据系列
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
    }
}
