# 大模型调用完整流程文档

## 目录
1. [整体架构流程图](#1-整体架构流程图)
2. [多轮对话流程图](#2-多轮对话流程图)
3. [数据流转图](#3-数据流转图)
4. [工具调用流程图](#4-工具调用流程图)
5. [关键调用链路](#5-关键调用链路)
6. [数据库操作流程](#6-数据库操作流程)
7. [并发控制机制](#7-并发控制机制)
8. [核心类和方法](#8-核心类和方法)

---

## 1. 整体架构流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              入口层 (Controller)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  BotController.triggerBot()         [手动触发BOT]                            │
│  BotController.callModelWithCustomPrompt()  [自定义Prompt调用]              │
│  AutomaticTradeService.runAutomaticTrade()  [定时任务，每15分钟]             │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      AsyncTradingTaskService (异步执行)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  executeAsyncTradingTask(BotCallModelRequest)  [@Async线程池执行]            │
│  executeAsyncTradingTask(BotCallModelRequest, Long)  [@Async线程池执行]      │
│      │                                                                      │
│      ├─► 1. 创建 ChatSession (会话)                                         │
│      │                                                                      │
│      ├─► 2. 创建 LlmCallRecord (调用记录，状态: PROCESSING)                  │
│      │                                                                      │
│      ├─► 3. 生成 Prompt                                                     │
│      │   └─► AiDecisionService.generatePromptOnly()                         │
│      │       └─► PromptBuilder.buildPrompts()                                │
│      │           ├─► AccountInfoProcessor                                   │
│      │           ├─► PositionInfoProcessor                                  │
│      │           ├─► TechnicalIndicatorProcessor                             │
│      │           ├─► MarketDataProcessor                                    │
│      │           └─► StrategySuggestionProcessor                            │
│      │                                                                      │
│      └─► 4. executeAiCall()  [核心AI调用逻辑]                              │
│                                                                          │
│  executeAsyncTradingTaskWithPromptGeneration()  [自定义Prompt]             │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AI调用核心流程 (executeAiCall)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│  步骤1: 调用AI模型                                                          │
│  └─► AiDecisionService.callAiModelWithUnifiedFactory()                     │
│      └─► UnifiedModelFactory.callWithMessages(messages, modelName)         │
│          ├─► getModelConfig()  [从缓存/DB获取配置]                          │
│          └─► callDirectly()                                                 │
│              ├─► LocalModelCaller.call()  [本地模型: Ollama]                │
│              └─► RemoteModelCaller.call() [远端模型: API]                  │
│                  └─► 返回 AI 响应                                           │
│                                                                              │
│  步骤2: 解析AI响应                                                          │
│  ├─► ActionParser.parseActionPack()  [解析为ActionPack对象]                 │
│  ├─► ClearVisionUtils.clearingIllusions()  [幻觉处理]                       │
│  └─► TradeActionService.saveTradeActions()  [保存交易动作]                 │
│                                                                              │
│  步骤3: 检测工具调用                                                        │
│  └─► ActionParser.containsToolCall(actionPack)                              │
│      ├─► [否] → 执行最终交易 (单轮对话)                                     │
│      │   └─► TradeActionProcessor.processTradeActions()                     │
│      │       └─► 执行 BUY/SELL/HOLD 操作                                   │
│      │                                                                      │
│      └─► [是] → 进入多轮对话 (processAsyncConversationRecursive)           │
│                                                                              │
│  步骤4: 保存响应和ChatMessage                                               │
│  └─► LlmCallRecordService.updateCallRecordSuccessInNewTransaction()          │
│      └─► ChatService.createAiTradeMessagePair()                             │
│          ├─► 创建 user ChatMessage                                          │
│          └─► 创建 assistant ChatMessage                                     │
│          └─► 关联到 LlmCallRecord (userMessageId, assistantMessageId)       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 多轮对话流程图

> **实际实现说明**：多轮对话直接在 `AsyncTradingTaskService.processAsyncConversationRecursive()` 方法中用 while 循环实现，未使用 `MultiTurnConversationManager` 接口。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      多轮对话循环 (processAsyncConversationRecursive)         │
└─────────────────────────────────────────────────────────────────────────────┘

    while (currentRound <= MAX_ROUNDS) {
        
        ┌─────────────────────────────────────────────────────────────────────┐
        │ 第1步: 执行工具调用                                                 │
        └─────────────────────────────────────────────────────────────────────┘
        for (action in actions) {
            ToolExecutionResult result = executeToolAsync(action, sessionId, apiKeyId);
            └─► ToolExecutionManager.executeTools()
                └─► KLineQueryExecutor.execute()  [K线查询]
                    └─► 查询 OKX API 获取K线数据
            保存结果到 toolResults
        }
        
        ┌─────────────────────────────────────────────────────────────────────┐
        │ 第2步: 判断是否需要继续                                              │
        └─────────────────────────────────────────────────────────────────────┘
        ActionPack actionPack = ActionParser.parseActionPack(aiResponse);
        
        if (!ActionParser.containsToolCall(actionPack)) {
            // 无工具调用 = 最终决策
            └─► 执行最终交易 (BUY/SELL/HOLD)
            └─► llmCallRecordService.markConversationAsCompleted(chatSessionId)
            └─► break;  // 退出循环
        }
        
        if (currentRound >= MAX_ROUNDS) {
            // 达到最大轮次
            └─► llmCallRecordService.markConversationAsTerminated(...)
            └─► break;  // 退出循环
        }
        
        ┌─────────────────────────────────────────────────────────────────────┐
        │ 第3步: 创建账户快照                                                 │
        └─────────────────────────────────────────────────────────────────────┘
        TradeBalanceSnapshot snapshot = 
            TradeBalanceSnapshotService.createSnapshotWithAiLimits(...)
        TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(snapshot)
        
        ┌─────────────────────────────────────────────────────────────────────┐
        │ 第4步: 创建新的LlmCallRecord                                         │
        └─────────────────────────────────────────────────────────────────────┘
        LlmCallRecord newRecord = llmCallRecordService.createCallRecord(
            apiKeyId, modelName, chatSessionId, 
            currentParentId, currentRound + 1, "ASYNC_CONVERSATION", 
            null, null
        )
        currentParentId = newRecord.getId()
        
        ┌─────────────────────────────────────────────────────────────────────┐
        │ 第5步: 构建下一轮消息                                                │
        └─────────────────────────────────────────────────────────────────────┘
        List<Message> messages = buildNextTurnMessagesAsync(...)
        └─► AsyncTradingTaskService.buildNextTurnMessagesAsync():1114
            └─► 从 ChatMessage 表读取历史消息
            └─► 构建包含历史消息的新 messages 列表

        ┌─────────────────────────────────────────────────────────────────────┐
        │ 第6步: 再次调用AI模型                                                │
        └─────────────────────────────────────────────────────────────────────┘
        aiResponse = callAiModelWithUnifiedFactory(messages, modelName)
        └─► UnifiedModelFactory.callWithMessages()
        └─► 重复上述流程
        
        currentRound++
    }
```

**循环终止条件：**
1. 达到最大轮次限制（简单决策：5轮，复杂决策：15轮）
2. AI返回最终决策（BUY/SELL/HOLD，不包含QUERY）
3. AI明确表示完成
4. 超时保护（单轮3分钟超时）

**复杂决策判断标准：**
- 工具调用次数 >= 3
- AI响应长度 > 2000字符
- 当前轮次 >= 3

---

## 3. 数据流转图

```
┌──────────────┐
│  HTTP请求    │
│  /bot/trigger│
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│ BotController.triggerBot()                                       │
│ - 接收用户请求                                                  │
│ - 构建 BotCallModelRequest                                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ AsyncTradingTaskService.executeAsyncTradingTask()              │
│ [@Async线程池]                                                  │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ├──────────────────────┬─────────────────────┐
                         │                      │                     │
                         ▼                      ▼                     ▼
              ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
              │  ChatSession     │   │ LlmCallRecord   │   │  Prompt生成      │
              │  (新创建)         │   │  (新创建)        │   │                  │
              │  status=active   │   │  status=        │   │ PromptBuilder.   │
              │                  │   │  PROCESSING     │   │ buildPrompts()   │
              └──────────────────┘   └──────────────────┘   └──────────────────┘
                         │                      │                     │
                         └──────────────────────┴─────────────────────┘
                                                │
                                                ▼
                                    ┌──────────────────────────┐
                                    │ UnifiedModelFactory.    │
                                    │ callWithMessages()       │
                                    │                          │
                                    │ - 获取模型配置            │
                                    │ - 选择ModelCaller        │
                                    └──────────┬───────────────┘
                                               │
                                    ┌──────────┴──────────┐
                                    │                     │
                                    ▼                     ▼
                         ┌──────────────────┐  ┌──────────────────┐
                         │ LocalModelCaller │  │RemoteModelCaller│
                         │ (Ollama)         │  │ (API)            │
                         └────────┬─────────┘  └────────┬─────────┘
                                  │                     │
                                  └──────────┬──────────┘
                                             │
                                             ▼
                                    ┌──────────────────────┐
                                    │ AI 响应 (JSON)      │
                                    └──────────┬───────────┘
                                               │
                                    ┌──────────┴──────────┐
                                    │                     │
                                    ▼                     ▼
                         ┌──────────────────┐  ┌──────────────────┐
                         │ ActionParser     │  │ ClearVisionUtils │
                         │ parseActionPack()│  │ clearingIllusions│
                         └────────┬─────────┘  └──────────────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │ ActionPack       │
                         │ - actions[]      │
                         │ - decisionAction │
                         └────────┬─────────┘
                                  │
                      ┌─────────┴─────────┐
                      │                   │
               [包含工具调用?]       [最终决策?]
                      │                   │
               ┌─────┴─────┐           ┌──┴──┐
               │           │           │     │
              YES         NO         YES    NO
               │           │           │     │
               ▼           ▼           ▼     ▼
    ┌──────────────┐  ┌──────────┐ ┌──────┐  ┌──────┐
    │多轮对话循环  │  │直接交易  │ │终止  │  │继续  │
    │executeTool  │  │BUY/SELL  │ │循环  │  │循环  │
    └──────┬───────┘  └──────┬───┘ └──────┘  └──────┘
           │                 │
           ▼                 ▼
    ┌──────────────┐  ┌──────────────────┐
    │ QUERY工具    │  │ TradeAction      │
    │ 执行K线查询  │  │ Processor        │
    └──────┬───────┘  │ processTrade     │
           │          │ Actions()        │
           ▼          └──────────────────┘
    ┌──────────────┐           │
    │ K线数据      │           ▼
    └──────┬───────┘  ┌──────────────────┐
           │         │ UnifiedCexApi     │
           │         │ Service          │
           │         │ placeOrder()     │
           └─────────┼──────────────────┘
                     │
                     ▼
          ┌──────────────────────┐
          │ 更新LlmCallRecord    │
          │ status=SUCCESS      │
          │ 保存ChatMessage对    │
          │ (user + assistant)   │
          └──────────────────────┘
```

---

## 4. 工具调用流程图

```
┌─────────────────────────────────────────────────────────────────┐
│ 工具调用执行流程 (executeToolAsync)                             │
│              AsyncTradingTaskService:1015                        │
└─────────────────────────────────────────────────────────────────┘

ActionParser.ParsedAction
    │
    ├─ action: "QUERY" 或 "ATTENTION"
    ├─ instId: "BTC-USDT-SWAP"
    ├─ timeframe: "1h"
    └─ limit: 100
    │
    ▼
ToolExecutor.execute(action, sessionId, apiKeyId)
    │
    ├─► 1. 提取工具名称
    │   └─► getToolNameFromAction(action) → "k_line"
    │
    ├─► 2. 查找已注册的执行器
    │   └─► executors.get("k_line") → KLineQueryExecutor
    │
    ├─► 3. 构建工具参数
    │   └─► KLineParameters.builder()
    │       .instId("BTC-USDT-SWAP")
    │       .timeframe("1h")
    │       .limit(100)
    │       .build()
    │
    ├─► 4. 验证参数
    │   └─► executor.validateParameters(parameters)
    │
    └─► 5. 执行工具
        └─► KLineQueryExecutor.execute(parameters, apiKeyId)
            │
            ├─► 获取API密钥
            │   └─► ApiKeyService.getDecryptedKey(apiKeyId)
            │
            ├─► 调用OKX API
            │   └─► UnifiedCexApiService.getMarketCandle(
            │           instId, timeframe, limit
            │       )
            │
            └─► 返回K线数据
                └─► ToolExecutionResult.success(kLineData)
```

---

## 5. 关键调用链路

### 单轮对话完整调用链

```
BotController.triggerBot()
  └─► AsyncTradingTaskService.executeAsyncTradingTask()
      ├─► LlmCallRecordService.createChatSession()
      ├─► LlmCallRecordService.createCallRecord()
      ├─► AiDecisionService.generatePromptOnly()
      │   └─► PromptBuilder.buildPrompts()
      │       ├─► AccountInfoProcessor.process()
      │       ├─► PositionInfoProcessor.process()
      │       ├─► TechnicalIndicatorProcessor.process()
      │       ├─► MarketDataProcessor.process()
      │       └─► StrategySuggestionProcessor.process()
      └─► executeAiCall()
          ├─► AiDecisionService.callAiModelWithUnifiedFactory()
          │   └─► UnifiedModelFactory.callWithMessages()
          │       └─► LocalModelCaller.call() / RemoteModelCaller.call()
          ├─► ActionParser.parseActionPack()
          ├─► ClearVisionUtils.clearingIllusions()
          ├─► TradeActionService.saveTradeActions()
          └─► TradeActionProcessor.processTradeActions()
              └─► UnifiedCexApiService.placeOrder()
```

### 多轮对话完整调用链

```
BotController.triggerBot()
  └─► AsyncTradingTaskService.executeAsyncTradingTask()
      └─► executeAiCall()
          └─► [检测到工具调用]
              └─► processAsyncConversationRecursive()
                  └─► while (currentRound <= MAX_ROUNDS)
                      ├─► executeToolAsync()
                      │   └─► ToolExecutionManager.executeTools()
                      │       └─► KLineQueryExecutor.execute()
                      │
                      ├─► buildNextTurnPromptAsync()
                      │   └─► MultiTurnConversationService.buildNextTurnPrompt()
                      │       ├─► HistorySummarizer.summarize()
                      │       └─► MultiTurnPromptTemplate.buildNextTurnPrompt()
                      │
                      ├─► LlmCallRecordService.createCallRecord()
                      │
                      ├─► AiDecisionService.callAiModelWithUnifiedFactory()
                      │
                      └─► ActionParser.parseActionPack()
```

---

## 6. 数据库操作流程

### ChatSession 创建流程

```
创建 ChatSession
    │
    └─► ChatSessionRepository.save()
        └─► INSERT INTO t_chat_sessions
            (session_id, session_name, user_id, status, model_name, created_at)
```

### LlmCallRecord 创建和更新流程

```
创建 LlmCallRecord
    │
    └─► LlmCallRecordService.createCallRecord()
        └─► INSERT INTO t_llm_call_records
            (api_key_id, model_name, session_id, parent_id, round_number,
             call_source, status, created_at)

更新 LlmCallRecord
    │
    └─► LlmCallRecordService.updateCallRecordSuccessInNewTransaction()
        └─► UPDATE t_llm_call_records
            SET status='SUCCESS',
                response_content=?,
                user_message_id=?,
                assistant_message_id=?,
                decision_action=?,
                decision_price=?,
                decision_quantity=?,
                prompt_generation_time_ms=?,
                llm_call_time_ms=?,
                post_action_time_ms=?
            WHERE id=?
```

### ChatMessage 创建流程

```
保存 ChatMessage (user)
    │
    └─► ChatService.createAiTradeMessagePair()
        └─► INSERT INTO t_chat_messages
            (session_id, role, content, tokens_used, processing_time_ms)
            → role='user', content=prompt

保存 ChatMessage (assistant)
    │
    └─► ChatService.createAiTradeMessagePair()
        └─► INSERT INTO t_chat_messages
            (session_id, role, content, tokens_used, processing_time_ms)
            → role='assistant', content=aiResponse
```

---

## 7. 并发控制机制

### UnifiedModelFactory 并发控制

```
UnifiedModelFactory 并发控制
    │
    ├─► Semaphore (信号量)
    │   ├─► 每个模型独立的并发限制
    │   └─► 从 AIModelConfig.maxConcurrent 获取
    │
    ├─► ModelCallConfig (调用配置)
    │   ├─► timeout: 3分钟
    │   ├─► retryCount: 3次
    │   └─► retryDelay: 1秒
    │
    └─► Caffeine缓存 (配置缓存)
        ├─► 缓存时间: 5分钟
        └─► 最大缓存数: 100个模型
```

### AsyncTradingTaskService 异步控制

```
AsyncTradingTaskService 异步控制
    │
    ├─► @Async("tradingTaskExecutor")
    │   └─► 自定义线程池
    │       ├─► 核心线程数: 5
    │       ├─► 最大线程数: 20
    │       └─► 队列容量: 100
    │
    └─► 超时保护
        └─► 单轮调用超时: 3分钟
```

---

## 8. 核心类和方法

### 入口点

| 文件 | 方法 | 说明 |
|------|------|------|
| `BotController.java:152` | `triggerBot()` | 手动触发BOT（异步） |
| `BotController.java:393` | `callModelWithCustomPrompt()` | 使用自定义Prompt调用 |
| `AutomaticTradeService.java:38` | `runAutomaticTrade()` | 定时任务（每15分钟） |

### 核心服务

| 文件 | 方法 | 说明 |
|------|------|------|
| `AsyncTradingTaskService.java:86` | `executeAsyncTradingTask(BotCallModelRequest)` | 异步执行AI交易任务 |
| `AsyncTradingTaskService.java:184` | `executeAsyncTradingTask(BotCallModelRequest, Long)` | 异步执行AI交易任务（带sessionId） |
| `AsyncTradingTaskService.java:1318` | `executeAsyncTradingTaskWithPromptGeneration()` | 自定义Prompt执行任务 |
| `AsyncTradingTaskService.java:292` | `executeAiCall()` | 执行AI调用核心逻辑 |
| `AsyncTradingTaskService.java:546` | `processAsyncConversationRecursive()` | 异步递归处理多轮对话 |
| `AsyncTradingTaskService.java:1015` | `executeToolAsync()` | 执行工具调用 |
| `AsyncTradingTaskService.java:1114` | `buildNextTurnMessagesAsync()` | 构建下一轮消息 |

### 模型调用

| 文件 | 方法 | 说明 |
|------|------|------|
| `UnifiedModelFactory.java:350` | `callWithMessages()` | 根据消息列表调用（实际实现） |
| `UnifiedModelFactory.java:118` | `callWithModel()` | 根据模型名称调用（已废弃） |
| `UnifiedModelFactory.java:154` | `callWithConfig()` | 根据配置调用 |
| `LocalModelCaller.java:30` | `call()` | 本地模型调用（Ollama） |
| `RemoteModelCaller.java:38` | `call()` | 远端模型调用 |

### 多轮对话

| 文件 | 方法 | 说明 |
|------|------|------|
| `AsyncTradingTaskService.java:546` | `processAsyncConversationRecursive()` | 多轮对话实现（while循环） |
| `AsyncTradingTaskService.java:1114` | `buildNextTurnMessagesAsync()` | 从ChatMessage表读取历史消息 |
| `MultiTurnConversationService.java:67` | `needsMultiTurn()` | 判断是否需要多轮 |
| `MultiTurnConversationService.java:145` | `shouldContinue()` | 判断是否应该继续 |
| `ActionParser.java:33` | `parseActionPack()` | 解析AI响应动作 |

### 数据持久化

| 文件 | 方法 | 说明 |
|------|------|------|
| `LlmCallRecordService.java:82` | `createCallRecord()` | 创建调用记录 |
| `LlmCallRecordService.java:248` | `updateCallRecordSuccessInNewTransaction()` | 独立事务更新成功状态 |
| `ChatService.java:276` | `createAiTradeMessagePair()` | 创建AI交易消息对 |

### Prompt构建

| 文件 | 方法 | 说明 |
|------|------|------|
| `PromptBuilder.java:52` | `buildPromptWithSegments()` | 构建包含segments的Prompt |
| `MultiTurnPromptTemplate.java:63` | `buildNextTurnPrompt()` | 构建下一轮Prompt |

---

## 关键设计亮点

1. **异步执行**：使用 `@Async("tradingTaskExecutor")` 避免阻塞主线程
2. **独立事务**：`updateCallRecordSuccessInNewTransaction()` 使用 `REQUIRES_NEW` 确保响应立即持久化
3. **并发控制**：UnifiedModelFactory 使用 Semaphore 限制并发调用数
4. **模型缓存**：Caffeine 缓存模型配置5分钟
5. **超时保护**：单轮3分钟超时，防止任务卡死
6. **自动清理**：定时清理30分钟仍处于PROCESSING状态的记录
7. **可扩展性**：ToolExecutor 接口支持动态注册新工具

---

## 文件路径汇总

### 入口点
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/rest/controller/BotController.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/llm/AutomaticTradeService.java`

### 核心服务
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/AsyncTradingTaskService.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/AiDecisionService.java`

### 模型调用
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/UnifiedModelFactory.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/model/LocalModelCaller.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/model/RemoteModelCaller.java`

### 多轮对话
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/MultiTurnConversationService.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/conversation/ActionParser.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/conversation/ToolExecutionManagerImpl.java`

### 数据持久化
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/LlmCallRecordService.java`
- `/Users/page/IdeaProjects/claude/crypto-trade/backend/src/main/java/com/crypto/trade/service/ChatService.java`

---

*文档生成时间：2026-02-26*
*项目版本：dev-llm-trade-ops1.1*