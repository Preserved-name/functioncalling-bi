# 记忆功能说明

## 🎯 概述

记忆功能让 AI 助手能够记住对话历史和用户偏好，提供更个性化、更连贯的对话体验。

## 🏗️ 架构设计

```
用户会话 → ConversationService → MessageWindowChatMemory
                ↓
        存储对话历史 + 用户偏好
                ↓
        AI 可以访问上下文信息
```

## 📦 核心组件

### 1. ConversationService（会话管理服务）
**位置**: `com.example.function_calling.service.ConversationService`

#### 主要功能：
- **会话记忆管理**：为每个会话 ID 维护独立的对话历史
- **用户偏好存储**：保存用户的个人信息和偏好设置
- **记忆窗口控制**：自动保留最近 N 条消息（默认 20 条）
- **系统提示生成**：根据用户偏好生成个性化的系统提示

#### 关键方法：

```java
// 获取或创建会话记忆
MessageWindowChatMemory getOrCreateMemory(String sessionId)

// 添加用户消息到记忆
void addUserMessage(String sessionId, String message)

// 添加 AI 回复到记忆
void addAiMessage(String sessionId, String message)

// 保存用户偏好
void saveUserPreference(String sessionId, String key, Object value)

// 获取用户偏好
Object getUserPreference(String sessionId, String key)

// 清除会话记忆
void clearMemory(String sessionId)
```

### 2. MemoryTools（记忆工具类）
**位置**: `com.example.function_calling.tools.MemoryTools`

提供 Function Calling 接口，让 AI 能够主动管理记忆：

#### 可用工具：

1. **saveUserPreference** - 保存用户信息
   ```
   描述：保存用户的偏好设置或个人信息
   参数：sessionId, key（键名）, value（值）
   示例：AI 可以调用此工具记住用户的名字、城市等
   ```

2. **getUserPreference** - 获取用户信息
   ```
   描述：获取用户的特定偏好或信息
   参数：sessionId, key（要查询的键名）
   示例：AI 可以查询之前保存的用户信息
   ```

3. **getAllUserPreferences** - 获取所有偏好
   ```
   描述：获取用户的所有偏好和信息
   参数：sessionId
   示例：AI 可以了解用户的全部已知信息
   ```

4. **clearMemory** - 清除记忆
   ```
   描述：清除用户的所有记忆和偏好信息
   参数：sessionId
   示例：用户要求忘记所有信息时调用
   ```

### 3. GeneralAgent（通用 Agent - 已升级）
**位置**: `com.example.function_calling.agent.GeneralAgent`

现在支持：
- ✅ 对话历史记忆
- ✅ 用户偏好存储
- ✅ Function Calling 工具调用
- ✅ 会话级别的隔离

## 💡 使用场景

### 场景 1：记住用户姓名

**对话流程**：
```
用户：我叫张三
AI：[调用 saveUserPreference] 已保存：name = 张三
AI：好的，张三，我记住了你的名字。

用户：我今天心情不错
AI：很高兴听到你心情不错，张三！有什么我可以帮你的吗？
```

### 场景 2：记住用户所在城市

**对话流程**：
```
用户：我在北京
AI：[调用 saveUserPreference] 已保存：city = 北京
AI：好的，我记住你在北京了。

用户：今天天气怎么样？
AI：[知道用户在北京，自动查询北京天气]
AI：北京今天天气晴朗，温度18°C...
```

### 场景 3：多轮对话上下文

**对话流程**：
```
用户：我想学习编程
AI：很好的想法！你想学习哪种编程语言呢？

用户：Python 吧
AI：[记住用户对 Python 感兴趣]
AI：Python 是很好的选择！你是完全零基础吗？

用户：是的，我没有任何编程经验
AI：[记住用户是初学者]
AI：明白了，我会从基础开始为你讲解...
```

### 场景 4：个性化建议

**对话流程**：
```
用户：我喜欢吃辣
AI：[保存偏好：food_preference = 辣]
AI：好的，我记住你喜欢吃辣了。

用户：推荐一些晚餐
AI：[基于用户喜欢辣的偏好]
AI：既然你喜欢吃辣，我推荐你试试麻婆豆腐、水煮鱼...
```

## 🔧 技术实现

### 1. 会话 ID 管理

**前端**（JavaScript）：
```javascript
// 生成或获取会话 ID
function getSessionId() {
    let sessionId = localStorage.getItem('chat_session_id');
    if (!sessionId) {
        sessionId = 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('chat_session_id', sessionId);
    }
    return sessionId;
}

// 发送消息时携带会话 ID
fetch('/api/chat/stream', {
    body: JSON.stringify({ 
        message: message,
        sessionId: getSessionId()
    })
})
```

**后端**：
```java
// ChatRequest 包含 sessionId
public static class ChatRequest {
    private String message;
    private String sessionId; // 会话 ID
    // getters and setters...
}
```

### 2. 记忆存储

使用 `ConcurrentHashMap` 实现线程安全的内存存储：

```java
// 会话记忆存储
private final Map<String, MessageWindowChatMemory> conversationMemories = new ConcurrentHashMap<>();

// 用户偏好存储
private final Map<String, Map<String, Object>> userPreferences = new ConcurrentHashMap<>();
```

### 3. LangChain4j 集成

使用 `AiServices` 和 `ChatMemoryProvider`：

```java
ChatAssistant assistant = AiServices.builder(ChatAssistant.class)
        .chatLanguageModel(chatModel)
        .chatMemoryProvider(memoryId -> conversationService.getOrCreateMemory(sessionId))
        .tools(memoryTools)  // 注册记忆工具
        .build();
```

## 🎨 前端界面

### 清除记忆按钮

在侧边栏底部添加了"清除记忆"按钮：
- 🗑️ 点击后清除本地会话 ID
- 刷新页面重新开始
- 服务器端的记忆也会在下次创建新会话时清理

### 样式特点

```css
.clear-memory-btn {
    background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
    color: white;
    border-radius: 8px;
    transition: all 0.2s ease;
}

.clear-memory-btn:hover {
    transform: scale(1.05);
    box-shadow: 0 3px 10px rgba(238, 90, 111, 0.3);
}
```

## 📊 记忆类型

### 1. 短期记忆（对话历史）
- **存储内容**：最近的对话消息
- **容量限制**：默认保留最近 20 条消息
- **作用**：保持对话连贯性，理解上下文
- **自动管理**：超出限制时自动删除最旧的消息

### 2. 长期记忆（用户偏好）
- **存储内容**：用户的个人信息、偏好设置
- **容量限制**：无限制（直到手动清除）
- **作用**：提供个性化服务
- **管理方式**：AI 自动保存或通过工具调用

## 🚀 使用示例

### 示例 1：基础对话记忆

```
用户：你好
AI：你好！有什么可以帮助你的吗？

用户：我刚才问的问题你还记得吗？
AI：记得，你刚才说了"你好"，然后我问你有什么可以帮助你的。
```

### 示例 2：用户信息记忆

```
用户：我叫李明，今年25岁
AI：[调用 saveUserPreference]
AI：好的，李明，我记住你今年25岁了。

用户：你觉得我这个年龄应该做什么？
AI：[获取用户年龄信息]
AI：李明，25岁是一个很好的年纪，你可以...
```

### 示例 3：偏好记忆

```
用户：我喜欢看科幻电影
AI：[保存偏好]
AI：好的，我记住你喜欢科幻电影了。

用户：推荐一部电影
AI：[基于偏好推荐]
AI：既然你喜欢科幻电影，我推荐你看《星际穿越》...
```

### 示例 4：清除记忆

```
用户：清除我的所有记忆
AI：[调用 clearMemory]
AI：好的，我已经清除了所有关于你的记忆。

用户：我叫什么名字？
AI：抱歉，我不知道你的名字。你能告诉我吗？
```

## 🔐 隐私与安全

### 当前实现
- **内存存储**：所有数据存储在服务器内存中
- **会话隔离**：不同会话 ID 的数据完全隔离
- **临时性**：服务器重启后所有记忆丢失

### 生产环境建议
1. **持久化存储**：使用数据库保存用户偏好
2. **加密存储**：敏感信息加密后存储
3. **过期策略**：设置记忆的有效期
4. **用户控制**：提供查看和管理记忆的界面
5. **合规性**：遵循 GDPR 等隐私法规

## 🎓 最佳实践

### 1. 何时使用记忆
✅ 适合：
- 多轮对话需要上下文
- 个性化推荐服务
- 用户偏好设置
- 学习计划跟踪

❌ 不适合：
- 敏感信息（密码、银行卡等）
- 一次性对话
- 匿名咨询

### 2. 记忆管理策略
- **定期清理**：清除长时间未使用的会话
- **容量控制**：限制单个会话的记忆大小
- **用户授权**：重要信息保存前征得用户同意
- **透明化**：让用户知道记住了什么信息

### 3. 提示词优化
在系统提示中包含用户信息：
```
你是一个友好、专业的AI助手。

已知用户信息：
- name: 张三
- city: 北京
- interest: 编程

请根据这些信息提供更个性化的回答。
```

## 🔮 未来扩展

### 1. 持久化存储
```java
// 使用数据库存储
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    List<UserPreference> findBySessionId(String sessionId);
}
```

### 2. 向量记忆
```java
// 使用向量数据库存储语义记忆
VectorStore vectorStore = PgVectorStore.builder()
        .dataSource(dataSource)
        .table("memories")
        .build();
```

### 3. 记忆摘要
```java
// 定期总结对话内容
String summary = aiService.summarizeConversation(history);
memory.addSummary(summary);
```

### 4. 跨设备同步
```javascript
// 使用用户账号而非设备 ID
const sessionId = userAccount.id + '_' + Date.now();
```

## 📝 测试建议

启动应用后，尝试以下对话：

1. **测试基本记忆**：
   ```
   用户：我叫张三
   用户：我叫什么名字？
   ```

2. **测试上下文理解**：
   ```
   用户：我想学编程
   用户：从哪里开始？
   ```

3. **测试偏好保存**：
   ```
   用户：我喜欢吃辣
   用户：推荐一些菜
   ```

4. **测试清除记忆**：
   ```
   点击"清除记忆"按钮
   用户：我叫什么名字？
   ```

## 💫 总结

记忆功能让 AI 助手更加智能和个性化：
- ✅ 记住对话历史，保持上下文连贯
- ✅ 存储用户偏好，提供个性化服务
- ✅ Function Calling 让 AI 主动管理记忆
- ✅ 会话隔离保护用户隐私
- ✅ 简单易用的清除功能

通过合理使用记忆功能，可以大大提升用户体验！
