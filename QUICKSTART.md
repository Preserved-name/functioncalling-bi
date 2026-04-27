# 快速启动指南

## 前置要求

1. Java 21 或更高版本
2. Maven 3.6+
3. 阿里云百炼 API Key

## 启动步骤

### 1. 配置 API Key

**Windows PowerShell:**
```powershell
$env:BAI_LIAN_API_KEY="your-api-key-here"
```

**Windows CMD:**
```cmd
set BAI_LIAN_API_KEY=your-api-key-here
```

**Linux/Mac:**
```bash
export BAI_LIAN_API_KEY="your-api-key-here"
```

### 2. 编译并运行

```bash
mvn spring-boot:run
```

### 3. 访问系统

打开浏览器访问：**http://localhost:8080**

## 新功能特性

### 🎨 现代化聊天界面
- **左右对话布局**：用户消息在右，AI 回复在左
- **流式输出**：AI 回答实时逐字显示，类似打字机效果
- **侧边栏快捷示例**：快速选择常见问题
- **意图识别展示**：每条消息下方显示识别的意图和处理的 Agent
- **响应式设计**：完美支持手机和电脑

### ⚡ 流式输出
- 实时显示 AI 思考过程
- 逐字显示回答内容
- 更流畅的用户体验
- 减少等待焦虑

### 💬 对话功能
- 支持多轮对话
- 自动滚动到最新消息
- Shift+Enter 换行，Enter 发送
- 输入框自动调整高度

## 测试示例

点击侧边栏的示例问题，或手动输入：

- 🌤️ 今天北京天气怎么样？
- 🔢 计算 123 * 456 等于多少？
- 📚 什么是人工智能？
- 💻 如何用 Java 实现快速排序？
- 👋 你好，很高兴认识你

## API 接口

### 普通聊天（非流式）
```bash
POST http://localhost:8080/api/chat
Content-Type: application/json

{
  "message": "你的问题"
}
```

### 流式聊天
```bash
POST http://localhost:8080/api/chat/stream
Content-Type: application/json

{
  "message": "你的问题"
}
```

响应格式：Server-Sent Events (SSE)

## 常见问题

**Q: 如何获取阿里云百炼 API Key？**  
A: 访问阿里云官网，注册账号并开通百炼服务，在控制台创建 API Key。

**Q: 端口被占用怎么办？**  
A: 在 `application.properties` 中添加：`server.port=8081`

**Q: 如何添加新的 Agent？**  
A: 
1. 在 `Intent` 枚举中添加新的意图类型
2. 创建新的 Agent 类实现 `Agent` 接口
3. 使用 `@Component` 注解让 Spring 自动注册

系统会自动发现并注册新的 Agent！
