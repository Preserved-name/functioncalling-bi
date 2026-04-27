# Function Calling 功能说明

## 🎯 概述

Function Calling（函数调用）是大语言模型的一项重要功能，允许 AI 模型在需要时调用外部工具或 API 来获取实时数据或执行特定操作。

在本项目中，我们为 **WeatherAgent** 实现了 Function Calling 功能，让 AI 能够自动调用天气查询工具获取真实的天气数据。

## 🏗️ 架构设计

```
用户提问 → WeatherAgent → LangChain4j AiServices
                              ↓
                         分析意图，决定是否需要调用工具
                              ↓
                    ┌─────────┴──────────┐
                    ↓                    ↓
            调用 WeatherTools      直接回答
                    ↓
            返回天气数据
                    ↓
            生成最终回答
```

## 📦 核心组件

### 1. WeatherService（天气服务层）
**位置**: `com.example.function_calling.service.WeatherService`

提供三个模拟的天气数据源方法：

#### getCurrentWeather(String city)
获取指定城市的当前天气信息
- 输入：城市名称（如"北京"）
- 输出：包含温度、湿度、天气状况等的 Map

#### getWeatherForecast(String city, int days)
获取未来几天的天气预报
- 输入：城市名称 + 天数（1-7）
- 输出：天气预报列表

#### searchWeatherByCity(String cityName)
根据城市名搜索天气（支持模糊匹配）
- 输入：城市名称（可部分匹配）
- 输出：匹配城市的天气列表

### 2. WeatherTools（工具类 - Function Calling 入口）
**位置**: `com.example.function_calling.tools.WeatherTools`

使用 `@Tool` 注解标记可供 AI 调用的方法：

```java
@Tool("获取指定城市的当前天气信息，包括温度、湿度、天气状况等")
public String getCurrentWeather(String city) {
    // 调用 WeatherService 获取数据
    // 返回格式化后的天气信息
}

@Tool("获取指定城市未来几天的天气预报，days参数为1-7天")
public String getWeatherForecast(String city, int days) {
    // 获取天气预报
    // 返回格式化后的预报信息
}

@Tool("根据城市名称搜索天气信息，支持模糊匹配城市名")
public String searchWeatherByCity(String cityName) {
    // 搜索匹配的城市
    // 返回多个城市的天气信息
}
```

### 3. WeatherAgent（天气 Agent）
**位置**: `com.example.function_calling.agent.WeatherAgent`

使用 LangChain4j 的 `AiServices` 创建支持 Function Calling 的助手：

```java
WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
        .chatLanguageModel(chatModel)
        .tools(weatherTools)  // 注册工具
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();

return assistant.chat(userMessage);
```

## 🔄 工作流程

### 示例 1：查询当前天气

**用户输入**: "北京今天天气怎么样？"

**执行流程**:
1. AI 分析用户问题，识别出需要查询北京的天气
2. AI 决定调用 `getCurrentWeather(city="北京")` 工具
3. LangChain4j 执行工具调用，获取天气数据
4. 工具返回格式化的天气信息：
   ```
   📍 城市：北京
   🌤️ 天气：晴朗
   🌡️ 温度：18°C
   💧 湿度：55%
   💨 风速：12 km/h
   ```
5. AI 基于工具返回的数据，生成自然语言的回答
6. 返回给用户："北京今天天气晴朗，温度18°C，湿度55%，风速12 km/h。是个好天气！"

### 示例 2：查询天气预报

**用户输入**: "上海未来3天的天气预报"

**执行流程**:
1. AI 识别需要查询上海未来3天的预报
2. 调用 `getWeatherForecast(city="上海", days=3)` 工具
3. 工具返回3天的预报数据
4. AI 整理并生成友好的预报信息

### 示例 3：模糊搜索城市

**用户输入**: "帮我查一下广州的天气"

**执行流程**:
1. AI 识别需要查询广州天气
2. 调用 `searchWeatherByCity(cityName="广州")` 工具
3. 工具返回匹配的城市天气（可能包含多个"广州"相关城市）
4. AI 整理并展示结果

## 🎨 支持的查询方式

### 1. 直接查询
- "北京天气怎么样？"
- "上海今天的气温多少？"
- "深圳现在的天气"

### 2. 预报查询
- "北京未来5天天气预报"
- "上海下周天气如何？"
- "广州未来一周的天气"

### 3. 比较查询
- "北京和上海哪个更热？"
- "对比一下广州和深圳的天气"

### 4. 建议查询
- "今天北京适合出门吗？"
- "上海明天需要带伞吗？"
- "成都这个天气适合什么活动？"

## 💡 技术要点

### 1. @Tool 注解
- 标记的方法必须是 public
- 方法参数会自动映射到 AI 的调用请求
- 方法描述会帮助 AI 理解何时调用该工具

### 2. AiServices
- 自动处理工具调用逻辑
- 管理对话历史（ChatMemory）
- 支持多轮对话上下文

### 3. 工具返回值
- 返回字符串格式的结构化数据
- 包含 emoji 图标提高可读性
- AI 会基于这些数据生成自然语言回答

## 🔧 扩展开发

### 添加新的工具方法

1. 在 `WeatherTools` 中添加新方法：

```java
@Tool("获取空气质量指数")
public String getAirQuality(String city) {
    // 实现逻辑
    return "AQI 数据...";
}
```

2. 无需修改其他代码，AI 会自动发现并使用新工具！

### 添加新的天气数据源

在 `WeatherService` 中添加新方法：

```java
public Map<String, Object> getHourlyForecast(String city) {
    // 实现逐小时预报
}
```

然后在 `WeatherTools` 中包装成 `@Tool` 方法。

## 📊 模拟数据说明

由于没有接入真实的天气 API，当前使用模拟数据：

- **固定模板**：部分城市有固定的天气模式
  - 北京：晴朗
  - 上海：多云
  - 广州：小雨
  - 等等...

- **随机生成**：温度、湿度、风速等在合理范围内随机

- **可扩展**：可以轻松替换为真实的天气 API（如和风天气、OpenWeatherMap 等）

## 🚀 实际应用场景

### 1. 智能客服
- 用户询问天气 → 自动调用天气 API → 返回准确信息

### 2. 旅行助手
- 规划行程时自动查询目的地天气
- 根据天气给出穿衣建议

### 3. 农业咨询
- 农民询问天气 → 提供详细预报
- 结合农事活动给出建议

### 4. 活动策划
- 户外活动前查询天气
- 根据天气调整活动方案

## 🎓 学习价值

通过这个项目可以学习：
- ✅ LangChain4j Function Calling 机制
- ✅ @Tool 注解的使用
- ✅ AiServices 构建 AI 助手
- ✅ 工具方法的设计模式
- ✅ AI 与外部系统的集成
- ✅ 结构化数据的处理和展示

## 🔮 未来改进

1. **接入真实 API**：替换模拟数据为真实天气 API
2. **更多工具**：添加日历、新闻、股票等工具
3. **缓存机制**：缓存天气数据减少 API 调用
4. **国际化**：支持多城市、多语言
5. **预警功能**：恶劣天气预警通知

## 📝 测试建议

启动应用后，在聊天界面尝试以下问题：

1. "北京今天天气怎么样？"
2. "上海未来3天的天气预报"
3. "帮我查一下广州的天气"
4. "深圳和杭州哪个城市今天更热？"
5. "成都明天适合户外运动吗？"

观察 AI 是否正确调用工具并返回准确的天气信息！
