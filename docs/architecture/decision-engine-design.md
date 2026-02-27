# 交易决策引擎架构设计

## 概述

本文档描述了加密货币交易系统的决策引擎架构，采用基于 REST Controller 的分层设计，通过多轮会话式聊天交互实现智能交易决策。

## 架构设计

系统采用 Controller-Service-Repository 三层架构，通过 REST API 提供交易决策能力。核心决策流程以 ChatController 为入口，支持多轮会话式交互。

### Controller 层

| Controller | 职责 | 文件路径 |
|------------|------|----------|
| ChatController | 聊天决策入口 | backend/src/main/java/com/crypto/trade/rest/controller/ChatController.java |
| RiskControlController | 风控模式控制 | backend/src/main/java/com/crypto/trade/rest/controller/RiskControlController.java |
| PositionController | 持仓管理 | backend/src/main/java/com/crypto/trade/rest/controller/PositionController.java |
| TradingController | 交易执行与市场数据 | backend/src/main/java/com/crypto/trade/rest/controller/TradingController.java |
| BalanceController | 余额查询 | backend/src/main/java/com/crypto/trade/rest/controller/BalanceController.java |
| DashboardController | 仪表板统计 | backend/src/main/java/com/crypto/trade/rest/controller/DashboardController.java |

### 各 Controller 职责详解

#### 1. ChatController（聊天控制器）
**职责**: 多轮会话式聊天决策入口

**核心功能**:
- 用户会话管理（创建、查询、删除会话）
- 聊天消息发送与接收
- 与 ChatService 协作处理自然语言交易指令

```java
@RestController
@RequestMapping("/chat")
public class ChatController {
    // 获取用户会话列表
    GET /chat/sessions
    // 创建新会话
    POST /chat/sessions
    // 获取最新会话
    GET /chat/sessions/latest
    // 发送消息
    POST /chat/messages
}
```

#### 2. RiskControlController（风控控制器）
**职责**: 风控模式和交易风格管理

**核心功能**:
- 获取当前风控配置信息
- 风控模式切换（保守型/正常型/激进型）
- 交易风格设置
- 风控历史记录查询

```java
@RestController
@RequestMapping("/risk-control")
public class RiskControlController {
    // 获取风控配置信息
    GET /risk-control/info
    // 获取当前风控模式
    GET /risk-control/mode
    // 设置风控模式
    POST /risk-control/mode
    // 获取交易风格
    GET /risk-control/style
    // 设置交易风格
    POST /risk-control/style
}
```

#### 3. PositionController（持仓控制器）
**职责**: 持仓数据查询与管理

**核心功能**:
- 高级持仓查询（支持多条件筛选）
- 持仓统计数据分析
- 与 UnifiedPositionService 集成获取统一持仓数据

```java
@RestController
@RequestMapping("/trading/positions")
public class PositionController {
    // 高级查询持仓数据
    POST /trading/positions/query
    // 持仓统计
    POST /trading/positions/statistics
}
```

#### 4. TradingController（交易控制器）
**职责**: 交易执行与市场数据

**核心功能**:
- 市价单/限价单下单
- 止损止盈设置
- 仓位平仓
- 订单撤销与修改
- 市场行情数据查询
- K线数据获取
- 实时价格监控

```java
@RestController
@RequestMapping("/trading")
public class TradingController {
    // 市价下单
    POST /trading/order/market
    // 限价下单
    POST /trading/order/limit
    // 撤销订单
    POST /trading/order/cancel
    // 平仓
    POST /trading/close-position
    // 获取市场行情
    GET /trading/ticker
    // 获取K线数据
    GET /trading/kline
}
```

#### 5. BalanceController（余额控制器）
**职责**: 账户余额查询与管理

**核心功能**:
- 最新余额数据查询
- 余额汇总信息
- 分交易所余额统计
- 与 UnifiedBalanceService 集成

```java
@RestController
@RequestMapping("/cex-balances")
public class BalanceController {
    // 获取最新余额数据
    GET /cex-balances/latest
    // 获取余额汇总信息
    GET /cex-balances/summary
    // 获取账户详情
    GET /cex-balances/detail
}
```

#### 6. DashboardController（仪表板控制器）
**职责**: 系统统计与监控

**核心功能**:
- 活跃任务统计
- API Key 状态统计
- 交易成功率统计
- 系统整体健康状态

```java
@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    // 获取仪表板统计数据
    GET /dashboard/statistics
}
```

### 多轮会话式聊天决策流程

```
用户 -> ChatController -> ChatService -> [AI模型]
                                    |
                                    v
                            [数据收集服务]
                                    |
                    +---------------+---------------+
                    |               |               |
                    v               v               v
            BalanceController  PositionController  TradingController
                    |               |               |
                    +---------------+---------------+
 v
                            [                                    |
                                   决策结果生成]
                                    |
                                    v
                            用户响应
```

**决策流程说明**:

1. **会话管理**: 用户通过 ChatController 创建或选择会话
2. **消息解析**: ChatService 解析用户自然语言指令
3. **数据收集**: 根据决策需要，调用 BalanceController、PositionController、TradingController 获取必要数据
4. **AI决策**: 将上下文信息发送给 AI 模型进行决策
5. **结果执行**: 根据 AI 决策结果，调用 TradingController 执行交易
6. **响应反馈**: 将决策结果和执行状态反馈给用户

### Service 层核心服务

| Service | 职责 |
|---------|------|
| ChatService | 聊天会话管理、消息处理 |
| RiskControlService | 风控逻辑处理 |
| PositionQueryService | 持仓数据查询 |
| BalanceService | 余额数据处理 |
| DashboardService | 统计数据聚合 |
| UnifiedBalanceService | 统一余额服务 |
| UnifiedPositionService | 统一持仓服务 |
| UnifiedPriceDataService | 统一价格数据服务 |

## 架构优势

### 1. 职责分离清晰
- 每个 Controller 专注特定领域
- Service 层处理业务逻辑
- 数据访问通过 Repository 层

### 2. 可扩展性
- 新的交易策略可以通过扩展 Service 实现
- 新的数据源可以通过添加 Service 集成
- Controller 层易于添加新的 API 端点

### 3. 可维护性
- 代码结构清晰，易于理解
- 单元测试友好
- 依赖注入便于模块替换

### 4. 多轮会话支持
- 上下文信息完整保留
- 支持复杂的多轮交互决策
- 用户体验友好

## 部署和监控

### 关键指标
- API 响应时间：目标<200ms
- 决策成功率：目标>95%
- 系统可用性：目标>99.9%

### 监控点
- 各 Controller 的调用次数和耗时
- 交易执行结果分布
- AI 决策置信度分布
- 异常发生频率和类型
