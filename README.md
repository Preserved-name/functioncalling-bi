# 多 Agent 智能分发系统

这是一个基于 LangChain4j 框架设计的多 Agent 智能分发系统，能够识别用户意图并将问题分发给不同的专业 Agent 处理。

## 系统架构

```
用户请求 → 意图识别器 → Agent分发器 → 专业Agent → 返回结果
                    ↓
    [天气/数学/知识/代码/数据分析/通用]
```

## 功能特性

### 1. 意图识别
- 自动识别用户问题的意图类型
- 支持6种意图：天气查询、数学计算、知识问答、代码助手、数据分析、通用对话

### 2. 专业 Agent
- **天气助手**：处理天气相关查询（支持 Function Calling）
- **数学助手**：解答数学计算问题
- **知识助手**：回答知识性问题
- **代码助手**：提供编程帮助
- **数据分析助手**：连接 MySQL 数据库，执行自然语言查询（ChatBI）
- **通用助手**：处理其他对话（支持记忆功能）

### 3. 阿里云百炼集成
- 使用阿里云通义千问模型（qwen-plus）
- 通过 OpenAI 兼容接口调用

## 配置说明

在 `application.properties` 中配置阿里云百炼 API Key：

```properties
aliyun.bailian.api-key=your-api-key-here
```

或者设置环境变量：
```bash
export BAI_LIAN_API_KEY=your-api-key-here
```

## 运行项目

```bash
# 设置环境变量（替换为你的阿里云百炼 API Key）
$env:BAI_LIAN_API_KEY="your-api-key-here"

# 启动应用
mvn spring-boot:run
```

启动后访问：http://localhost:8080

## 界面预览

系统提供了一个现代化的聊天界面，包含：

### 🎨 界面特点
- **左右对话气泡**：用户消息在右侧，AI 回复在左侧
- **流式输出**：AI 回答实时逐字显示，类似打字效果
- **侧边栏**：快捷示例问题列表
- **意图识别展示**：每条 AI 消息下方显示识别的意图和处理 Agent
- **响应式设计**：支持移动端和桌面端

### ✨ 主要功能
- 📝 实时聊天对话
- 💡 示例问题快捷发送
- 🎯 意图识别结果展示
- 🤖 Agent 处理信息显示
- ⚡ 流式输出，即时反馈

直接访问 http://localhost:8080 即可体验！

## API 接口

### 1. 健康检查
```bash
GET http://localhost:8080/api/health
```

### 2. 聊天接口
```bash
POST http://localhost:8080/api/chat
Content-Type: application/json

{
  "message": "你的问题"
}
```

### 响应示例
```json
{
  "intent": "MATH",
  "intentDescription": "数学计算",
  "agentName": "数学助手",
  "response": "AI 的回答内容"
}
```

## 测试示例

### 天气查询
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "今天北京天气怎么样？"}'
```

### 数学计算
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "计算 123 * 456 等于多少？"}'
```

### 知识问答
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "什么是人工智能？"}'
```

### 代码帮助
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "如何用 Java 实现快速排序？"}'
```

### 数据分析（ChatBI）
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "数据库中有哪些表？"}'
```

**注意**：使用 ChatBI 前需要在 `application.properties` 中配置 MySQL 数据库连接信息。详见 [CHATBI_GUIDE.md](CHATBI_GUIDE.md)

## 技术栈

- Spring Boot 4.0.6
- LangChain4j 0.34.0
- Java 21
- 阿里云百炼（通义千问）

## 扩展开发

添加新的 Agent 非常简单：

1. 在 `Intent` 枚举中添加新的意图类型
2. 创建新的 Agent 类实现 `Agent` 接口
3. 使用 `@Component` 注解让 Spring 自动注册

系统会自动发现并注册新的 Agent！
