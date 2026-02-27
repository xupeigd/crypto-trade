# 多轮会话重构架构文档

## 目录
1. [整体架构流程图](#1-整体架构流程图)
2. [多轮会话状态机流程图](#2-多轮会话状态机流程图)
3. [工具调用执行流程图](#3-工具调用执行流程图)
4. [交易执行触发流程图](#4-交易执行触发流程图)
5. [详细调用链路](#5-详细调用链路)
6. [数据流转图](#6-数据流转图)
7. [组件关系图](#7-组件关系图)

---

## 1. 整体架构流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Controller Layer                                │
│                           BotController.executeAsyncTradingTask()            │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AsyncTradingTaskService                           │
│                         executeAsyncTradingTask()                            │
│                                                                              │
│  1. 构建 ConversationRequest                                                │
│  2. 调用 MultiTurnConversationManager.startConversation()                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       MultiTurnConversationManagerImpl                      │
│                            startConversation()                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                       ┌───────────────┴───────────────┐
                       │                               │
                       ▼                               ▼
              创建 ChatSession                    创建 ConversationContext
                       │                               │
                       └───────────────┬───────────────┘
                                       │
                                       ▼
                              executeFirstRound()
                                       │
                       ┌───────────────┴───────────────┐
                       │                               │
                       ▼                               ▼
              调用 UnifiedModelFactory              创建 LlmCallRecord
                       │                               │
                       └───────────────┬───────────────┘
                                       │
                                       ▼
                               获取 AI 响应
                                       │
                                       ▼
                       ┌───────────────┴───────────────┐
                       │                               │
                       ▼                               ▼
                   解析响应                        保存 ChatMessage
              ActionParser.parseActionPack()                │
                       │                               │
                       └───────────────┬───────────────┘
                                       │
                                       ▼
                     判断是否需要工具调用？(shouldContinue)
                                       │
                      ┌───────────────┴───────────────┐
                      │                               │
                     YES                              NO
                      │                               │
                      ▼                               ▼
              executeToolCalls()               completeConversation()
                      │                               │
                      ▼                               ▼
         ┌────────────────────────┐         返回最终响应
         │  ToolExecutionManager  │
         │    executeTools()      │
         └────────────────────────┘
                      │
                      ▼
         ┌────────────────────────┐
         │  ToolExecutor (注册的)  │
         │  - KLineQueryExecutor  │
         │  - AttentionExecutor   │
         └────────────────────────┘
                      │
                      ▼
                 返回工具结果
                      │
                      ▼
            保存到 ConversationContext
                      │
                      ▼
            构建下一轮 Prompt
         buildNextPrompt()
                      │
                      ▼
              executeNextRound()
                      │
                      ▼
            ┌─────────┴─────────┐
            │                   │
            ▼                   ▼
       调用 AI            判断是否需要
                           继续工具调用？
            │                   │
            │         ┌─────────┴─────────┐
            │         │                   │
            │        YES                 NO
            │         │                   │
            │         ▼                   ▼
            │  executeToolCalls()  检测最终决策？
            │         │                   │
            │         │         ┌─────────┴─────────┐
            │         │         │                   │
            │         │        YES                 NO
            │         │         │                   │
            │         │         ▼                   ▼
            │         │  执行交易决策         继续工具调用
            │         │  TradeActionProcessor    ...
            │         │         │
            │         │         └─────────────────┘
            │         │
            └─────────┴─────────┘
```

---

## 2. 多轮会话状态机流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Conversation State Machine                            │
└─────────────────────────────────────────────────────────────────────────────┘

    INITIALIZED ──→ IN_PROGRESS ──→ WAITING_FOR_TOOL ──→ IN_PROGRESS
         │                │                │                │
         │                │                │                │
         │                │                ▼                │
         │                │          COMPLETED ──────────────┘
         │                │                │
         │                │                │
         │                │                ▼
         │                │          (返回最终响应)
         │                │
         │                └────────→ TERMINATED (达到最大轮次)
         │
         └────────────────→ TERMINATED (异常/错误)
```

### 状态说明

| 状态 | 说明 | 触发条件 |
|------|------|----------|
| INITIALIZED | 会话已初始化 | 创建会话时 |
| IN_PROGRESS | 对话进行中 | AI调用开始时 |
| WAITING_FOR_TOOL | 等待工具执行 | 检测到工具调用时 |
| COMPLETED | 会话已完成 | 无工具调用或有最终决策时 |
| TERMINATED | 会话已终止 | 达到最大轮次或异常时 |
| ERROR | 会话错误 | 执行过程中发生异常时 |

---

## 3. 工具调用执行流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Tool Execution Flow                                  │
└─────────────────────────────────────────────────────────────────────────────┘

    ActionParser.ParsedAction[]
              │
              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ToolExecutionManager.executeTools()                       │
│                                                                              │
│  for each action in actions:                                                 │
│      1. 提取工具名称 (getToolNameFromAction)                                │
│      2. 查找已注册的执行器 (executors.get(toolName))                        │
│      3. 构建工具参数 (buildParameters)                                       │
│      4. 验证参数 (executor.validateParameters)                               │
│      5. 执行工具 (executor.execute)                                         │
│      6. 返回结果 (ToolExecutionResult)                                      │
└─────────────────────────────────────────────────────────────────────────────┘
              │
              ▼
    List<ToolExecutionResult>
              │
              │
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
 QUERY工具         ATTENTION工具
    │                   │
    ▼                   ▼
KLineQueryExecutor  AttentionExecutor
    │                   │
    ▼                   ▼
调用OKX API         标记关注币种
获取K线数据                │
    │                   │
    └─────────┬─────────┘
              │
              ▼
    ToolExecutionResult
    {
        toolName: "k_line" / "attention"
        success: true/false
        data: { k线数据 / 标记结果 }
        processingTimeMs: 123
        errorMessage: null
    }
```

### 工具执行器接口

```java
public interface ToolExecutor<T extends ToolParameters> {
    String getToolName();
    Class<T> getParameterType();
    boolean validateParameters(T parameters);
    ToolExecutionResult execute(T parameters, Long apiKeyId);
}
```

---

## 4. 交易执行触发流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Trade Execution Trigger Flow                              │
└─────────────────────────────────────────────────────────────────────────────┘

    AI 响应 (无工具调用)
              │
              ▼
    ActionParser.parseActionPack()
              │
              ▼
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
包含BUY/SELL/HOLD？   包含QUERY/ATTENTION？
    │                   │
    YES                 NO
    │                   │
    ▼                   ▼
检测为最终决策      继续工具调用
    │
    ▼
    TradeActionProcessor.processTradeActions()
    │
    ▼
    ┌─────────┴─────────┐
    │                   │
    ▼                   ▼
 BUY/SELL操作       HOLD操作
    │                   │
    ▼                   ▼
调用UnifiedCexApiService  更新决策状态
执行实际交易                │
    │                   │
    ▼                   ▼
保存TradeAction        返回响应
    │
    ▼
    TradeActionService.save()
    │
    ▼
    返回交易结果
    │
    ▼
completeConversation()
    │
    ▼
    标记会话完成
    │
    ▼
    返回最终响应
```

---

## 5. 详细调用链路

### 调用链路1：启动多轮会话（第一轮）

```
BotController.executeAsyncTradingTask()
    │
    ├─→ 构建初始Prompt
    │
    ├─→ ConversationRequest.builder()
    │       .apiKeyId(apiKeyId)
    │       .modelName(modelName)
    │       .initialPrompt(initialPrompt)
    │       .callSource("BOT")
    │       .maxRounds(10)
    │       .build()
    │
    ├─→ MultiTurnConversationManager.startConversation(request)
    │       │
    │       ├─→ ChatService.createChatSession()
    │       │       └─→ 保存 ChatSession 到数据库
    │       │
    │       ├─→ ConversationContext.builder()
    │       │       .sessionId(sessionId)
    │       │       .currentRound(0)
    │       │       .state(INITIALIZED)
    │       │       .build()
    │       │
    │       ├─→ executeFirstRound(context, request)
    │       │       │
    │       │       ├─→ LlmCallRecordService.createCallRecord()
    │       │       │       └─→ 创建 LlmCallRecord (round=1, parentId=null)
    │       │       │
    │       │       ├─→ UnifiedModelFactory.callWithModel(prompt, modelName)
    │       │       │       ├─→ ModelCaller.call()
    │       │       │       │   ├─→ LocalModelCaller (本地模型)
    │       │       │       │   └─→ RemoteModelCaller (远程API)
    │       │       │       └─→ 返回 AI 响应
    │       │       │
    │       │       ├─→ ActionParser.parseActionPack(aiResponse)
    │       │       │       └─→ 解析为 ActionPack (包含actions列表)
    │       │       │
    │       │       ├─→ ChatService.createAiTradeMessagePair()
    │       │       │       ├─→ 保存 user ChatMessage
    │       │       │       └─→ 保存 assistant ChatMessage
    │       │       │
    │       │       ├─→ LlmCallRecordService.updateCallRecordSuccessInNewTransaction()
    │       │       │       └─→ 更新 messageIds, responseContent, processingTime
    │       │       │
    │       │       ├─→ shouldContinue(context, aiResponse)
    │       │       │   └─→ 判断是否包含工具调用
    │       │       │
    │       │       └─→ 更新 ConversationContext
    │       │               .currentRound = 1
    │       │               .currentResponse = aiResponse
    │       │               .state = IN_PROGRESS 或 WAITING_FOR_TOOL
    │       │
    │       └─→ 返回 ConversationContext
    │
    └─→ 返回会话ID
```

### 调用链路2：执行工具调用

```
MultiTurnConversationManager.executeToolCalls(context, actionPack)
    │
    ├─→ ToolExecutionManager.executeTools(actions, sessionId, apiKeyId)
    │       │
    │       └─→ for each action in actions:
    │               │
    │               ├─→ getToolNameFromAction(action)
    │               │       └─→ "k_line" / "attention"
    │               │
    │               ├─→ executors.get(toolName)
    │               │       └─→ 获取 ToolExecutor
    │               │
    │               ├─→ buildParameters(action, executor)
    │               │       └─→ 构建工具参数对象
    │               │
    │               ├─→ executor.validateParameters(parameters)
    │               │       └─→ 验证参数合法性
    │               │
    │               ├─→ executor.execute(parameters, apiKeyId)
    │               │       │
    │               │       ├─→ KLineQueryExecutor.execute()
    │               │       │   │
    │               │       │   ├─→ UnifiedCexApiService.getKLine()
    │               │       │   │   │
    │               │       │   │   ├─→ OkxCexService.getMarketCandle()
    │               │       │   │   │
    │               │       │   │   └─→ 返回K线数据
    │               │       │   │
    │               │       │   └─→ ToolExecutionResult.success(data)
    │               │       │
    │               │       └─→ AttentionExecutor.execute()
    │               │           │
    │               │           ├─→ 标记关注币种
    │               │           │
    │               │           └─→ ToolExecutionResult.success(data)
    │               │
    │               └─→ ToolExecutionResult
    │                       .toolName = toolName
    │                       .success = true/false
    │                       .data = 结果数据
    │                       .processingTimeMs = 耗时
    │
    ├─→ 更新 ConversationContext
    │       .toolResults = results
    │       .totalToolCalls += results.size()
    │
    └─→ 判断是否达到最大轮次
            │
            └─→ 是：terminateConversation()
            │
            └─→ 否：buildNextPrompt() → executeNextRound()
```

### 调用链路3：执行下一轮对话

```
MultiTurnConversationManager.executeNextRound(context, nextPrompt)
    │
    ├─→ LlmCallRecordService.createCallRecord()
    │       │
    │       ├─→ sessionId = context.sessionId
    │       ├─→ parentId = context.customData.parentRecordId
    │       ├─→ roundNumber = context.currentRound + 1
    │       └─→ 创建新的 LlmCallRecord
    │
    ├─→ UnifiedModelFactory.callWithModel(nextPrompt, modelName)
    │       └─→ 调用 AI 模型
    │
    ├─→ ActionParser.parseActionPack(aiResponse)
    │       └─→ 解析响应
    │
    ├─→ ChatService.createAiTradeMessagePair()
    │       └─→ 保存消息对
    │
    ├─→ LlmCallRecordService.updateCallRecordSuccessInNewTransaction()
    │       └─→ 更新调用记录
    │
    ├─→ 更新 ConversationContext
    │       .currentRound += 1
    │       .currentPrompt = nextPrompt
    │       .currentResponse = aiResponse
    │       .toolResults = [] (清空上一轮结果)
    │
    ├─→ shouldContinue(context, aiResponse)
    │   │
    │   ├─→ 检查工具调用
    │   ├─→ 检查最大轮次
    │   └─→ 检查最终决策
    │
    └─→ 根据判断结果：
            │
            ├─→ 有工具调用 → executeToolCalls()
            │
            ├─→ 有最终决策 → TradeActionProcessor.processTradeActions()
            │                     │
            │                     ├─→ 调用 UnifiedCexApiService
            │                     │   └─→ 执行实际交易
            │                     │
            │                     ├─→ 保存 TradeAction
            │                     │
            │                     └─→ completeConversation()
            │
            └─→ 达到最大轮次 → terminateConversation()
```

### 调用链路4：构建下一轮Prompt

```
MultiTurnConversationManager.buildNextPrompt(context)
    │
    ├─→ LlmCallRecordService.getConversationHistory(sessionId)
    │       └─→ 获取历史调用记录
    │
    ├─→ HistorySummarizer.summarize(history)
    │       │
    │       ├─→ 提取历史ChatMessage
    │       │       └─→ ChatService.getMessagesBySession()
    │       │
    │       ├─→ 生成历史摘要
    │       │   │
    │       │   ├─→ 提取关键决策
    │       │   ├─→ 提取工具调用
    │       │   └─→ 提取工具结果
    │       │
    │       └─→ 返回摘要文本
    │
    ├─→ buildPromptContext(context)
    │       │
    │       ├─→ PromptContext.builder()
    │       │       .apiKeyId(apiKeyId)
    │       │       .source("MULTI_TURN")
    │       │       .build()
    │       │
    │       └─→ 加载业务上下文数据
    │           ├─→ 当前持仓
    │           ├─→ 市场数据
    │           └─→ 策略配置
    │
    ├─→ MultiTurnPromptTemplate.buildNextTurnPrompt()
    │       │
    │       ├─> 追加历史摘要
    │       ├─> 追加业务上下文
    │       ├─> 追加当前轮次说明
    │       ├─> 追加上轮响应
    │       ├─> 追加工具执行结果
    │       ├─> 追加思考模式要求
    │       └─> 追加决策要求
    │
    └─→ 返回完整的下一轮Prompt
```

### 调用链路5：交易执行

```
MultiTurnConversationManager（检测到最终决策）
    │
    ├─→ ActionParser.parseActionPack(aiResponse)
    │       │
    │       └─→ 提取交易动作
    │           ├─→ BUY
    │           ├─→ SELL
    │           └─→ HOLD
    │
    ├─→ TradeActionProcessor.processTradeActions(actionPack, apiKeyId, tradeActions)
    │       │
    │       └─→ for each action in actions:
    │               │
    │               ├─→ BUY/SELL 操作
    │               │   │
    │               │   ├─→ UnifiedCexApiService.placeOrder()
    │               │   │   │
    │               │   │   ├─→ OkxCexService.placeOrder()
    │               │   │   │
    │               │   │   └─→ 返回订单信息
    │               │   │
    │               │   └─→ 保存 TradeAction
    │               │
    │               └─→ HOLD 操作
    │                   │
    │                   └─→ 更新决策状态为HOLD
    │
    ├─→ TradeActionService.save(tradeActions)
    │       └─→ 批量保存交易动作
    │
    └─→ completeConversation(sessionId, finalResponse)
            │
            ├─> 更新 ConversationContext.state = COMPLETED
            ├─> ChatSession.status = completed
            └─> 清理缓存
```

---

## 6. 数据流转图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Data Flow Diagram                                 │
└─────────────────────────────────────────────────────────────────────────────┘

第1轮对话:
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  User Prompt │ ───> │  AI Response │ ───> │  ChatMessage │
│  (initial)   │      │  (第1轮)     │      │  (user+ass)  │
└──────────────┘      └──────────────┘      └──────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│LlmCallRecord │      │LlmCallRecord │      │  Database    │
│ round=1      │      │ userMsgId    │      │  持久化      │
│ parentId=null│      │ assMsgId     │      └──────────────┘
└──────────────┘      └──────────────┘
       │
       ▼
ConversationContext
{
  sessionId: 123
  currentRound: 1
  state: WAITING_FOR_TOOL
  toolResults: []
}

第2轮对话 (工具调用后):
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Next Prompt │ ───> │  AI Response │ ───> │  ChatMessage │
│  (含工具结果) │      │  (第2轮)     │      │  (user+ass)  │
└──────────────┘      └──────────────┘      └──────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│LlmCallRecord │      │LlmCallRecord │      │  Database    │
│ round=2      │      │ userMsgId    │      │  持久化      │
│ parentId=1   │      │ assMsgId     │      └──────────────┘
└──────────────┘      └──────────────┘
       │
       ▼
ConversationContext
{
  sessionId: 123
  currentRound: 2
  state: IN_PROGRESS
  toolResults: [ToolResult1, ToolResult2]
}

第N轮对话 (最终决策):
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Final Prompt│ ───> │  AI Response │ ───> │  ChatMessage │
│  (含历史摘要) │      │  (最终)      │      │  (user+ass)  │
└──────────────┘      └──────────────┘      └──────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│LlmCallRecord │      │LlmCallRecord │      │  Database    │
│ round=N      │      │ userMsgId    │      │  持久化      │
│ parentId=N-1 │      │ assMsgId     │      └──────────────┘
└──────────────┘      └──────────────┘
       │
       ▼
ConversationContext
{
  sessionId: 123
  currentRound: N
  state: COMPLETED
  toolResults: []
}
       │
       ▼
┌──────────────┐
│TradeAction   │
│(BUY/SELL)    │
└──────────────┘
       │
       ▼
┌──────────────┐
│  Database    │
│  交易记录    │
└──────────────┘
```

---

## 7. 组件关系图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Component Relationships                              │
└─────────────────────────────────────────────────────────────────────────────┘

                    BotController
                          │
                          ▼
            ┌─────────────────────────┐
            │ AsyncTradingTaskService │
            └─────────────────────────┘
                          │
                          ▼
    ┌───────────────────────────────────────────────┐
    │   MultiTurnConversationManager (接口)         │
    └───────────────────────────────────────────────┘
                          │
                          ▼
    ┌───────────────────────────────────────────────┐
    │ MultiTurnConversationManagerImpl (实现)       │
    ├───────────────────────────────────────────────┤
    │                                               │
    │  依赖:                                        │
    │  - UnifiedModelFactory                        │
    │  - ToolExecutionManager                       │
    │  - LlmCallRecordService                       │
    │  - HistorySummarizer                          │
    │  - MultiTurnPromptTemplate                    │
    │  - ChatService                                │
    │  - TradeActionProcessor                       │
    └───────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ToolExecutionMgr │ │UnifiedModelFtry │ │ LlmCallRecord   │
│                 │ │                 │ │ Service         │
│ 依赖:           │ │                 │ │                 │
│ - ToolExecutor  │ │ - LocalCaller   │ │ - ChatService   │
│   (KLineQuery)  │ │ - RemoteCaller  │ │ - Repository    │
│   (Attention)   │ │                 │ │                 │
└─────────────────┘ └─────────────────┘ └─────────────────┘
          │
          ▼
┌─────────────────┐
│  ToolExecutor   │
│  (接口)         │
└─────────────────┘
          │
          ├──────────────────┐
          ▼                  ▼
┌─────────────────┐ ┌─────────────────┐
│KLineQueryExec   │ │AttentionExecutor│
│                 │ │                 │
│依赖:            │ │依赖:            │
│- UnifiedCexApi  │ │- (无外部依赖)   │
│  Service        │ │                 │
└─────────────────┘ └─────────────────┘
                          │
                          ▼
                ┌─────────────────┐
                │UnifiedCexApi    │
                │Service          │
                └─────────────────┘
                          │
                          ▼
                ┌─────────────────┐
                │  Database       │
                │  (MySQL)        │
                └─────────────────┘
```

---

## 新增组件说明

### 1. MultiTurnConversationManager
通用多轮会话管理器接口，负责：
- 启动新的多轮会话
- 继续现有会话（执行下一轮）
- 获取会话状态
- 标记会话完成/终止
- 判断是否需要继续对话

### 2. ToolExecutionManager
工具调用管理器，负责：
- 注册/注销工具执行器
- 执行单个/批量工具调用
- 工具参数验证
- 结果统一封装

### 3. ConversationRequest
会话请求参数，包含：
- API密钥ID
- 模型名称
- 初始Prompt
- 调用来源
- 最大轮数
- 会话名称
- 自定义参数
- Prompt上下文

### 4. ConversationContext
会话上下文，保存：
- 会话ID
- 当前轮次
- 会话状态
- 历史记录
- 当前Prompt和响应
- 工具执行结果
- 累计工具调用次数
- 自定义数据

---

## 重构优势

1. **统一接口** - 所有大模型调用都使用相同的多轮会话接口
2. **可扩展性** - 通过工具注册机制轻松添加新工具
3. **状态管理** - 完整的会话状态机管理
4. **可测试性** - 接口清晰，易于单元测试
5. **解耦合** - 业务逻辑与AI调用逻辑分离
6. **可维护性** - 清晰的代码结构和职责划分

---

## 文档版本

- 版本：v1.0
- 创建日期：2026-02-03
- 作者：iFlow CLI