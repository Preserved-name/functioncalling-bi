package com.example.function_calling.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 天气服务 - 模拟多个天气数据源
 */
@Service
public class WeatherService {

    private final Random random = new Random();

    /**
     * 获取当前天气信息
     * @param city 城市名称
     * @return 天气信息
     */
    public Map<String, Object> getCurrentWeather(String city) {
        // 模拟不同城市的天气数据
        Map<String, String> weatherTemplates = new HashMap<>();
        weatherTemplates.put("北京", "晴朗");
        weatherTemplates.put("上海", "多云");
        weatherTemplates.put("广州", "小雨");
        weatherTemplates.put("深圳", "阴天");
        weatherTemplates.put("杭州", "晴天");
        weatherTemplates.put("成都", "雾");
        
        String condition = weatherTemplates.getOrDefault(city, getRandomWeather());
        int temperature = getRandomTemperature(city);
        int humidity = 40 + random.nextInt(40);
        int windSpeed = 5 + random.nextInt(20);
        
        Map<String, Object> weather = new HashMap<>();
        weather.put("city", city);
        weather.put("condition", condition);
        weather.put("temperature", temperature);
        weather.put("humidity", humidity);
        weather.put("windSpeed", windSpeed);
        weather.put("unit", "摄氏度");
        
        return weather;
    }

    /**
     * 获取天气预报（未来几天）
     * @param city 城市名称
     * @param days 天数（1-7）
     * @return 天气预报列表
     */
    public java.util.List<Map<String, Object>> getWeatherForecast(String city, int days) {
        if (days < 1) days = 1;
        if (days > 7) days = 7;
        
        java.util.List<Map<String, Object>> forecast = new java.util.ArrayList<>();
        String[] conditions = {"晴朗", "多云", "小雨", "大雨", "阴天", "晴天", "雾", "雪"};
        
        for (int i = 0; i < days; i++) {
            Map<String, Object> dayForecast = new HashMap<>();
            dayForecast.put("date", getDateAfterDays(i));
            dayForecast.put("condition", conditions[random.nextInt(conditions.length)]);
            dayForecast.put("tempHigh", 15 + random.nextInt(15));
            dayForecast.put("tempLow", 5 + random.nextInt(10));
            dayForecast.put("humidity", 40 + random.nextInt(40));
            forecast.add(dayForecast);
        }
        
        return forecast;
    }

    /**
     * 根据城市名搜索天气
     * @param cityName 城市名称（支持模糊搜索）
     * @return 匹配的城市天气列表
     */
    public java.util.List<Map<String, Object>> searchWeatherByCity(String cityName) {
        // 模拟城市数据库
        String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉", "南京", "西安"};
        
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        
        for (String city : cities) {
            if (city.contains(cityName) || cityName.contains(city)) {
                results.add(getCurrentWeather(city));
            }
        }
        
        return results;
    }

    // 辅助方法
    private String getRandomWeather() {
        String[] weathers = {"晴朗", "多云", "小雨", "大雨", "阴天", "晴天", "雾"};
        return weathers[random.nextInt(weathers.length)];
    }

    private int getRandomTemperature(String city) {
        // 根据不同城市设置不同的温度范围
        Map<String, int[]> tempRanges = new HashMap<>();
        tempRanges.put("北京", new int[]{5, 25});
        tempRanges.put("上海", new int[]{10, 30});
        tempRanges.put("广州", new int[]{20, 35});
        tempRanges.put("深圳", new int[]{20, 35});
        tempRanges.put("成都", new int[]{10, 28});
        
        int[] range = tempRanges.getOrDefault(city, new int[]{10, 30});
        return range[0] + random.nextInt(range[1] - range[0]);
    }

    private String getDateAfterDays(int days) {
        java.time.LocalDate date = java.time.LocalDate.now().plusDays(days);
        return date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
