package com.example.function_calling.tools;

import com.example.function_calling.service.WeatherService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 天气工具类 - 提供 Function Calling 方法
 */
@Component
public class WeatherTools {

    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * 获取指定城市的当前天气
     */
    @Tool("获取指定城市的当前天气信息，包括温度、湿度、天气状况等")
    public String getCurrentWeather(String city) {
        Map<String, Object> weather = weatherService.getCurrentWeather(city);
        return formatCurrentWeather(weather);
    }

    /**
     * 获取指定城市的天气预报
     */
    @Tool("获取指定城市未来几天的天气预报，days参数为1-7天")
    public String getWeatherForecast(String city, int days) {
        List<Map<String, Object>> forecast = weatherService.getWeatherForecast(city, days);
        return formatForecast(forecast, city);
    }

    /**
     * 搜索城市天气
     */
    @Tool("根据城市名称搜索天气信息，支持模糊匹配城市名")
    public String searchWeatherByCity(String cityName) {
        List<Map<String, Object>> results = weatherService.searchWeatherByCity(cityName);
        
        if (results.isEmpty()) {
            return "未找到城市：" + cityName + " 的天气信息。可查询的城市包括：北京、上海、广州、深圳、杭州、成都、重庆、武汉、南京、西安";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 个匹配城市的天气：\n\n");
        
        for (Map<String, Object> weather : results) {
            sb.append(formatCurrentWeather(weather));
            sb.append("\n---\n");
        }
        
        return sb.toString();
    }

    // 格式化当前天气
    private String formatCurrentWeather(Map<String, Object> weather) {
        return String.format(
                "📍 城市：%s\n" +
                "🌤️ 天气：%s\n" +
                "🌡️ 温度：%d°C\n" +
                "💧 湿度：%d%%\n" +
                "💨 风速：%d km/h",
                weather.get("city"),
                weather.get("condition"),
                weather.get("temperature"),
                weather.get("humidity"),
                weather.get("windSpeed")
        );
    }

    // 格式化的预报
    private String formatForecast(List<Map<String, Object>> forecast, String city) {
        StringBuilder sb = new StringBuilder();
        sb.append("📍 ").append(city).append(" 未来").append(forecast.size()).append("天天气预报：\n\n");
        
        for (Map<String, Object> day : forecast) {
            sb.append(String.format(
                    """
                            📅 日期：%s
                            🌤️ 天气：%s
                            🌡️ 温度：%s°C ~ %s°C
                            💧 湿度：%s%%
                            ---
                            """,
                    day.get("date"),
                    day.get("condition"),
                    day.get("tempHigh"),
                    day.get("tempLow"),
                    day.get("humidity")
            ));
        }
        
        return sb.toString();
    }
}
