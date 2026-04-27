# ChatBI Agent 使用指南

## 🎯 概述

ChatBI Agent 是一个智能数据分析助手，能够理解自然语言问题，自动查询 MySQL 数据库并返回分析结果。它利用 LangChain4j 的 Function Calling 能力，让 AI 能够自主决定何时查看表结构、何时执行 SQL 查询。

## 📦 已完成的配置

### 1. 依赖添加
已在 `pom.xml` 中添加：
- `spring-boot-starter-jdbc`：Spring JDBC 支持
- `mysql-connector-j`：MySQL 8.0 驱动程序

### 2. 数据库配置
在 `application.properties` 中添加了 MySQL 配置：
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

**⚠️ 重要**：请将上述配置修改为你实际的数据库连接信息！

### 3. 核心组件

#### SchemaExtractor（元数据提取器）
- **位置**：`com.example.function_calling.service.SchemaExtractor`
- **功能**：自动从 MySQL 中提取所有表的元数据（表名、字段名、类型、注释）
- **特点**：
  - 使用 JDBC `DatabaseMetaData` 接口
  - 自动过滤系统表
  - 支持读取表和字段的注释

#### BiTools（BI 工具类）
- **位置**：`com.example.function_calling.tools.BiTools`
- **提供的工具**：
  1. `getDatabaseSchema()`：获取数据库表结构信息
  2. `executeQuery(String sql)`：执行 SQL 查询（只读，自动限制 100 行）
- **安全特性**：
  - 仅允许 SELECT 语句
  - 阻止 DROP、DELETE、UPDATE、INSERT 等危险操作
  - 自动添加 LIMIT 100 防止大数据量

#### BiAgent（数据分析 Agent）
- **位置**：`com.example.function_calling.agent.BiAgent`
- **意图类型**：`BI_ANALYSIS`
- **功能**：处理数据分析类问题，自动调用 BiTools 中的工具

## 🚀 使用步骤

### 第一步：配置数据库连接

编辑 `src/main/resources/application.properties`，修改以下配置：

```properties
# 将 your_database 替换为你的数据库名
spring.datasource.url=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true

# 替换为你的数据库用户名
spring.datasource.username=root

# 替换为你的数据库密码
spring.datasource.password=your_password
```

### 第二步：准备测试数据（可选）

如果你还没有测试数据，可以创建一个简单的示例表：

```sql
CREATE DATABASE IF NOT EXISTS test_bi;
USE test_bi;

CREATE TABLE employees (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) COMMENT '员工姓名',
    department VARCHAR(50) COMMENT '部门',
    salary DECIMAL(10, 2) COMMENT '薪资',
    hire_date DATE COMMENT '入职日期',
    status INT COMMENT '状态：1=在职，2=离职'
) COMMENT='员工信息表';

INSERT INTO employees (name, department, salary, hire_date, status) VALUES
('张三', '技术部', 15000.00, '2020-01-15', 1),
('李四', '销售部', 12000.00, '2019-06-20', 1),
('王五', '技术部', 18000.00, '2018-03-10', 1),
('赵六', '人事部', 13000.00, '2021-07-01', 1),
('孙七', '销售部', 11000.00, '2022-02-15', 2);
```

然后更新配置文件中的数据库名为 `test_bi`。

### 第三步：启动应用

```bash
mvn spring-boot:run
```

### 第四步：测试 ChatBI

访问 http://localhost:8080，在聊天界面中尝试以下问题：

#### 基础查询
- "数据库中有哪些表？"
- "employees 表有哪些字段？"
- "显示所有员工的信息"

#### 统计分析
- "技术部有多少员工？"
- "所有员工的平均薪资是多少？"
- "每个部门的员工数量"
- "薪资最高的员工是谁？"

#### 条件查询
- "显示在职的员工"
- "找出薪资大于 15000 的员工"
- "2020年之后入职的员工有哪些？"

## 💡 工作原理

### 1. 意图识别
当用户提问时，`IntentRecognizer` 会分析问题：
- 包含"查询"、"统计"、"多少"、"平均"等关键词 → 识别为 `BI_ANALYSIS`

### 2. Agent 分发
`AgentDispatcher` 将问题路由到 `BiAgent`

### 3. Function Calling
`BiAgent` 使用 LangChain4j 的 `AiServices` 创建助手，注册了 `BiTools`：

```java
BiAssistant assistant = AiServices.builder(BiAssistant.class)
        .chatLanguageModel(chatModel)
        .tools(biTools)  // 注册工具
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();
```

### 4. 自主决策
AI 会根据问题自主决定：
- **如果需要了解表结构** → 调用 `getDatabaseSchema()`
- **如果需要查询数据** → 生成 SQL 并调用 `executeQuery(sql)`

### 5. 安全执行
`executeQuery` 方法会：
1. 检查 SQL 是否以 SELECT 开头
2. 检查是否包含危险关键字
3. 自动添加 LIMIT 100
4. 执行查询并格式化结果

## 🔒 安全特性

### 当前实现的安全措施
1. **只读权限**：仅允许 SELECT 语句
2. **关键字过滤**：阻止 DROP、DELETE、UPDATE、INSERT
3. **行数限制**：自动添加 LIMIT 100
4. **异常捕获**：查询失败时返回错误信息而非堆栈跟踪

### 生产环境建议增强
1. **数据库用户权限**：创建只读数据库用户
2. **SQL 解析验证**：使用 SQL 解析器（如 JSqlParser）进行更严格的校验
3. **查询超时**：设置查询执行超时时间
4. **速率限制**：限制单个用户的查询频率
5. **审计日志**：记录所有执行的 SQL 语句

## 🎨 前端集成

已在前端侧边栏添加"数据分析"示例：
- 📊 图标标识
- 点击示例："数据库中有哪些表？"
- 自动触发 BI Agent

## 📊 示例对话流程

**用户**：技术部有多少员工？

**AI 内部流程**：
1. 识别意图：BI_ANALYSIS
2. 调用 BiAgent
3. AI 分析：需要先了解表结构
4. 调用 `getDatabaseSchema()` 工具
5. 获取到 employees 表信息
6. AI 生成 SQL：`SELECT COUNT(*) FROM employees WHERE department = '技术部' AND status = 1`
7. 调用 `executeQuery(sql)` 工具
8. 获取查询结果：3
9. 生成自然语言回答："技术部目前有 3 名在职员工。"

**AI 回答**：技术部目前有 3 名在职员工。

## 🔧 扩展开发

### 添加更多 BI 工具

在 `BiTools` 中添加新方法：

```java
@Tool("计算指定字段的统计信息（平均值、最大值、最小值、总和）")
public String calculateStatistics(String tableName, String columnName) {
    String sql = String.format(
        "SELECT AVG(%s), MAX(%s), MIN(%s), SUM(%s) FROM %s",
        columnName, columnName, columnName, columnName, tableName
    );
    // 执行查询...
}
```

### 支持图表生成

让 AI 返回 ECharts 配置：

```java
@Tool("生成柱状图数据")
public String generateBarChartData(String sql) {
    // 执行查询
    // 返回 ECharts option JSON
}
```

### 智能表筛选

改进 `SchemaExtractor`，根据问题关键词只返回相关表：

```java
public String extractRelevantSchemas(String userQuestion) {
    // 分析问题中的关键词
    // 只返回匹配的表结构
    // 减少 Token 消耗
}
```

## ⚠️ 注意事项

1. **数据库兼容性**：当前针对 MySQL 8.0 优化，其他数据库可能需要调整
2. **中文注释**：确保数据库表和字段有清晰的中文注释，有助于 AI 理解
3. **Token 限制**：如果表很多，Schema 信息可能很长，考虑实现智能筛选
4. **性能影响**：频繁的 Schema 提取可能影响数据库性能，建议缓存结果
5. **隐私保护**：不要在生产环境暴露敏感数据，做好脱敏处理

## 🐛 常见问题

### Q1: 编译报错找不到 DataSource
**A**: 确保已添加 `spring-boot-starter-jdbc` 依赖，并且 `application.properties` 中配置了正确的数据库连接信息。

### Q2: AI 无法正确生成 SQL
**A**: 
- 检查数据库表和字段是否有清晰的注释
- 在 Prompt 中添加更多 Few-Shot 示例
- 考虑微调模型或使用更强大的 LLM

### Q3: 查询很慢
**A**:
- 检查数据库是否有合适的索引
- 启用查询缓存
- 限制返回的行数

### Q4: 如何支持多数据库？
**A**: 
- 使用 Spring 的多数据源配置
- 为每种数据库创建独立的 SchemaExtractor
- 在 BiTools 中根据数据源类型选择对应的提取器

## 📝 总结

ChatBI Agent 让你能够通过自然语言与数据库交互，无需编写 SQL。它的核心优势：

✅ **自动化**：自动提取元数据，无需手动配置  
✅ **智能化**：AI 自主决定何时调用什么工具  
✅ **安全性**：多层安全检查，防止危险操作  
✅ **易用性**：前端集成，开箱即用  
✅ **可扩展**：轻松添加新的 BI 工具  

开始使用 ChatBI，让数据分析变得更简单！🚀
