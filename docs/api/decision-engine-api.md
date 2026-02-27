# 决策引擎API接口文档

## 概述

本文档描述了加密货币交易系统决策引擎相关的API接口，包括聊天决策会话管理、风险控制、持仓管理、交易执行、市场数据查询等核心功能。

## 架构说明

决策引擎采用分层架构设计：
- **ChatController**: 聊天决策入口，提供多轮会话式决策交互
- **RiskControlController**: 风控模式控制，管理交易风格和风险偏好
- **PositionController**: 持仓管理，高级查询和统计持仓数据
- **TradingController**: 交易执行与市场数据查询
- **BalanceController**: 余额查询与管理
- **DashboardController**: 仪表板统计数据

## 核心API接口

### 1. 聊天决策接口 (ChatController)

聊天决策是多轮会话式的，用户可以通过聊天方式获取交易决策建议。

#### 1.1 获取会话列表

**接口地址**: `GET /chat/sessions`

**接口描述**: 获取当前用户的所有会话列表

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": "session_123",
      "userId": "user_456",
      "name": "BTC交易讨论",
      "messageCount": 10,
      "lastMessageTime": "2025-12-19T10:30:00Z",
      "createTime": "2025-12-18T09:00:00Z"
    }
  ]
}
```

#### 1.2 创建新会话

**接口地址**: `POST /chat/sessions`

**接口描述**: 创建一个新的聊天会话

**请求参数**:
```json
{
  "name": "新会话名称"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": "session_124",
    "userId": "user_456",
    "name": "新会话名称",
    "messageCount": 0,
    "createTime": "2025-12-19T11:00:00Z"
  }
}
```

#### 1.3 获取最新会话

**接口地址**: `GET /chat/sessions/latest`

**接口描述**: 获取最近创建的会话

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": "session_124",
    "userId": "user_456",
    "name": "新会话名称",
    "messageCount": 0,
    "createTime": "2025-12-19T11:00:00Z"
  }
}
```

#### 1.4 获取会话消息列表

**接口地址**: `GET /chat/sessions/{sessionId}/messages`

**接口描述**: 获取指定会话的所有消息

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": "msg_001",
      "sessionId": "session_123",
      "role": "user",
      "content": "帮我分析一下BTCUSDT的行情",
      "createTime": "2025-12-19T10:00:00Z"
    },
    {
      "id": "msg_002",
      "sessionId": "session_123",
      "role": "assistant",
      "content": "根据当前技术分析，BTCUSDT处于上升趋势...",
      "createTime": "2025-12-19T10:01:00Z"
    }
  ]
}
```

#### 1.5 发送消息（核心决策入口）

**接口地址**: `POST /chat/send`

**接口描述**: 发送消息并获取AI决策建议，这是核心的聊天决策入口

**请求参数**:
```json
{
  "sessionId": "session_123",
  "message": "帮我分析一下BTCUSDT的行情",
  "apiKeyId": 123,
  "vola": "0.05"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "messageId": "msg_003",
    "sessionId": "session_123",
    "response": "根据当前技术分析，BTCUSDT处于上升趋势，建议关注支撑位45000 USDT...",
    "decision": {
      "action": "BUY",
      "symbol": "BTC-USDT-SWAP",
      "confidence": 0.75,
      "reasoning": "基于技术分析和市场情绪"
    }
  }
}
```

#### 1.6 更新会话名称

**接口地址**: `PUT /chat/sessions/{sessionId}/name`

**接口描述**: 更新指定会话的名称

**请求参数**:
```json
{
  "name": "新会话名称"
}
```

#### 1.7 删除会话

**接口地址**: `DELETE /chat/sessions/{sessionId}`

**接口描述**: 删除指定的会话

---

### 2. 风控模式接口 (RiskControlController)

#### 2.1 获取风控配置信息

**接口地址**: `GET /risk-control/info`

**接口描述**: 获取当前风控系统配置信息

**响应示例**:
```json
{
  "success": true,
  "data": {
    "defaultMode": "CONSERVATIVE",
    "availableModes": ["CONSERVATIVE", "NORMAL", "AGGRESSIVE"],
    "defaultTradingStyle": "SAFE"
  }
}
```

#### 2.2 获取当前风控模式

**接口地址**: `GET /risk-control/current-mode`

**接口描述**: 获取当前用户的风控模式

**响应示例**:
```json
{
  "success": true,
  "data": {
    "mode": "CONSERVATIVE",
    "userId": "user_456",
    "updateTime": "2025-12-19T10:00:00Z"
  }
}
```

#### 2.3 设置风控模式

**接口地址**: `POST /risk-control/mode`

**接口描述**: 设置当前用户的风控模式

**请求参数**:
```json
{
  "mode": "NORMAL"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "mode": "NORMAL",
    "userId": "user_456",
    "updateTime": "2025-12-19T11:00:00Z"
  }
}
```

#### 2.4 重置风控模式

**接口地址**: `POST /risk-control/reset`

**接口描述**: 将风控模式重置为默认值

**响应示例**:
```json
{
  "success": true,
  "data": {
    "mode": "CONSERVATIVE",
    "userId": "user_456",
    "updateTime": "2025-12-19T11:00:00Z"
  }
}
```

#### 2.5 获取风控模式变更历史

**接口地址**: `GET /risk-control/history`

**接口描述**: 获取风控模式的变更历史记录

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": "user_456",
      "oldMode": "AGGRESSIVE",
      "newMode": "CONSERVATIVE",
      "updateTime": "2025-12-19T10:00:00Z"
    }
  ]
}
```

#### 2.6 获取当前交易风格

**接口地址**: `GET /risk-control/current-trading-style`

**接口描述**: 获取当前用户的交易风格

**响应示例**:
```json
{
  "success": true,
  "data": {
    "tradingStyle": "SAFE",
    "userId": "user_456",
    "updateTime": "2025-12-19T10:00:00Z"
  }
}
```

#### 2.7 设置交易风格

**接口地址**: `POST /risk-control/trading-style`

**接口描述**: 设置当前用户的交易风格

**请求参数**:
```json
{
  "tradingStyle": "AGGRESSIVE"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "tradingStyle": "AGGRESSIVE",
    "userId": "user_456",
    "updateTime": "2025-12-19T11:00:00Z"
  }
}
```

#### 2.8 获取交易风格变更历史

**接口地址**: `GET /risk-control/trading-style/history`

**接口描述**: 获取交易风格的变更历史记录

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": "user_456",
      "oldStyle": "SAFE",
      "newStyle": "AGGRESSIVE",
      "updateTime": "2025-12-19T10:00:00Z"
    }
  ]
}
```

---

### 3. 持仓管理接口 (PositionController)

#### 3.1 高级查询持仓数据

**接口地址**: `POST /trading/positions/query`

**接口描述**: 高级查询持仓数据，支持多条件筛选

**请求参数**:
```json
{
  "apiKeyId": 123,
  "symbol": "BTC-USDT-SWAP",
  "positionSide": "long",
  "page": 1,
  "size": 20
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "total": 5,
    "page": 1,
    "size": 20,
    "positions": [
      {
        "id": "pos_001",
        "apiKeyId": 123,
        "instId": "BTC-USDT-SWAP",
        "positionSide": "long",
        "avgOpenPrice": 45000.00,
        "currentPrice": 46000.00,
        "quantity": 0.5,
        "pnl": 500.00,
        "pnlRatio": 0.11,
        "openTime": "2025-12-18T10:00:00Z"
      }
    ]
  }
}
```

#### 3.2 高级统计持仓信息

**接口地址**: `POST /trading/positions/statistics/advanced`

**接口描述**: 获取持仓的高级统计信息

**请求参数**:
```json
{
  "apiKeyId": 123
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "totalPositions": 5,
    "totalPnL": 1250.00,
    "totalPnLRatio": 0.08,
    "largestPosition": {
      "symbol": "BTC-USDT-SWAP",
      "value": 23000.00
    },
    "averagePnL": 250.00
  }
}
```

---

### 4. 交易执行接口 (TradingController)

#### 4.1 下单交易

**接口地址**: `POST /trading/order`

**接口描述**: 执行交易下单操作

**请求参数**:
```json
{
  "apiKeyId": 123,
  "instId": "BTC-USDT-SWAP",
  "side": "buy",
  "orderType": "limit",
  "amount": 1000.00,
  "lever": 5,
  "px": 43250.00,
  "takeProfitPrice": 45000.00,
  "stopLossPrice": 41000.00,
  "posSide": "long",
  "bypassRiskControl": false
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "orderId": "OKX-20251219-001234",
    "status": "LIVE",
    "filledAmount": 0,
    "avgPrice": 0,
    "commission": 0,
    "createTime": "2025-12-19T10:30:00Z"
  }
}
```

#### 4.2 撤单操作

**接口地址**: `POST /trading/{apiKeyId}/cancel/{orderId}`

**接口描述**: 撤销未成交的订单

**响应示例**:
```json
{
  "success": true,
  "data": {
    "orderId": "OKX-20251219-001234",
    "status": "CANCELLED",
    "cancelTime": "2025-12-19T10:35:00Z"
  }
}
```

#### 4.3 获取活跃订单列表

**接口地址**: `GET /trading/orders/active`

**接口描述**: 获取当前所有活跃订单

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "orderId": "OKX-20251219-001234",
      "instId": "BTC-USDT-SWAP",
      "side": "buy",
      "orderType": "limit",
      "sz": 0.023,
      "px": 43250.00,
      "status": "live",
      "createTime": "2025-12-19T10:30:00Z"
    }
  ]
}
```

#### 4.4 获取当前委托订单

**接口地址**: `GET /trading/orders/{apiKeyId}/pending`

**接口描述**: 获取指定API密钥的当前委托订单

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "orderId": "OKX-20251219-001234",
      "instId": "BTC-USDT-SWAP",
      "side": "buy",
      "orderType": "limit",
      "sz": 0.023,
      "px": 43250.00,
      "status": "pending",
      "createTime": "2025-12-19T10:30:00Z"
    }
  ]
}
```

#### 4.5 获取历史订单列表

**接口地址**: `GET /trading/orders/{apiKeyId}/history`

**接口描述**: 获取历史订单记录

**请求参数**:
- `beginTime` (Long, 可选): 开始时间戳
- `endTime` (Long, 可选): 结束时间戳

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "orderId": "OKX-20251218-005678",
      "instId": "ETH-USDT-SWAP",
      "side": "sell",
      "orderType": "market",
      "sz": 1.5,
      "avgPx": 2250.00,
      "status": "filled",
      "pnl": 125.50,
      "commission": 2.25,
      "createTime": "2025-12-18T15:30:00Z"
    }
  ]
}
```

#### 4.6 获取实时仓位数据

**接口地址**: `GET /trading/positions/{apiKeyId}/live`

**接口描述**: 获取指定API密钥的实时仓位数据

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "instId": "BTC-USDT-SWAP",
      "positionSide": "long",
      "avgOpenPrice": 45000.00,
      "currentPrice": 46000.00,
      "quantity": 0.5,
      "pnl": 500.00,
      "pnlRatio": 0.11
    }
  ]
}
```

#### 4.7 获取历史持仓数据

**接口地址**: `GET /trading/positions/{apiKeyId}/history`

**接口描述**: 获取历史持仓记录

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "instId": "BTC-USDT-SWAP",
      "positionSide": "long",
      "avgOpenPrice": 45000.00,
      "closePrice": 46000.00,
      "quantity": 0.5,
      "pnl": 500.00,
      "openTime": "2025-12-18T10:00:00Z",
      "closeTime": "2025-12-19T10:00:00Z"
    }
  ]
}
```

#### 4.8 市价平仓

**接口地址**: `POST /trading/close-position`

**接口描述**: 以市价方式平掉指定仓位

**请求参数**:
```json
{
  "apiKeyId": 123,
  "instId": "BTC-USDT-SWAP",
  "positionSide": "long"
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "orderId": "OKX-20251219-001235",
    "status": "FILLED",
    "closePrice": 46150.00,
    "closeQuantity": 0.5,
    "pnl": 575.00,
    "closeTime": "2025-12-19T11:00:00Z"
  }
}
```

#### 4.9 设置全仓止盈止损

**接口地址**: `POST /trading/total-stop-loss`

**接口描述**: 设置全仓止盈止损

**请求参数**:
```json
{
  "apiKeyId": 123,
  "stopLossPrice": 41000.00,
  "takeProfitPrice": 50000.00
}
```

#### 4.10 获取合约资金费率

**接口地址**: `GET /trading/funding-rate/{instId}`

**接口描述**: 获取指定合约的资金费率

**响应示例**:
```json
{
  "success": true,
  "data": {
    "instId": "BTC-USDT-SWAP",
    "fundingRate": 0.0001,
    "nextFundingTime": "2025-12-19T16:00:00Z"
  }
}
```

#### 4.11 获取合约标记价格

**接口地址**: `GET /trading/mark-price/{instId}`

**接口描述**: 获取指定合约的标记价格

**响应示例**:
```json
{
  "success": true,
  "data": {
    "instId": "BTC-USDT-SWAP",
    "markPrice": 46000.00,
    "updateTime": "2025-12-19T10:30:00Z"
  }
}
```

#### 4.12 获取合约实时价格

**接口地址**: `GET /trading/instruments/{instId}/realtime-price`

**接口描述**: 获取指定合约的实时价格

**响应示例**:
```json
{
  "success": true,
  "data": {
    "instId": "BTC-USDT-SWAP",
    "lastPrice": 46050.00,
    "bidPrice": 46045.00,
    "askPrice": 46055.00,
    "volume24h": 1250000000,
    "change24h": 2.5,
    "updateTime": "2025-12-19T10:30:00Z"
  }
}
```

#### 4.13 技术指标计算

**接口地址**: `GET /trading/technical-indicators/{metricName}/{instId}`

**接口描述**: 计算指定合约的技术指标

**请求参数**:
- `period` (Integer, 可选): 周期
- `limit` (Integer, 可选): 数据条数

**响应示例**:
```json
{
  "success": true,
  "data": {
    "instId": "BTC-USDT-SWAP",
    "metricName": "MACD",
    "values": [125.5, 120.0, 115.0],
    "timestamps": ["2025-12-19T10:00:00Z", "2025-12-19T09:00:00Z", "2025-12-19T08:00:00Z"]
  }
}
```

#### 4.14 获取统一图表数据

**接口地址**: `POST /trading/unified-chart-data`

**接口描述**: 获取统一格式的K线图表数据

**请求参数**:
```json
{
  "instId": "BTC-USDT-SWAP",
  "bar": "1H",
  "limit": 100
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "instId": "BTC-USDT-SWAP",
    "bars": [
      {
        "timestamp": 1702978800000,
        "open": 45000.00,
        "high": 45500.00,
        "low": 44800.00,
        "close": 45200.00,
        "volume": 1250.5
      }
    ]
  }
}
```

---

### 5. 余额查询接口 (BalanceController)

#### 5.1 获取最新余额数据

**接口地址**: `GET /cex-balances/latest`

**接口描述**: 获取所有CEX的最新余额数据

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "apiKeyId": 123,
      "cexName": "OKX",
      "totalBalance": 50000.00,
      "availableBalance": 45000.00,
      "updateTime": "2025-12-19T10:30:00Z"
    }
  ]
}
```

#### 5.2 获取余额汇总信息

**接口地址**: `GET /cex-balances/summary`

**接口描述**: 获取所有CEX的余额汇总

**响应示例**:
```json
{
  "success": true,
  "data": {
    "totalBalance": 150000.00,
    "totalAvailable": 130000.00,
    "cexCount": 3
  }
}
```

#### 5.3 获取所有活跃的CEX名称

**接口地址**: `GET /cex-balances/active-cex`

**接口描述**: 获取所有活跃的CEX名称列表

**响应示例**:
```json
{
  "success": true,
  "data": ["OKX", "BINANCE", "BYBIT"]
}
```

#### 5.4 获取账户详情信息

**接口地址**: `GET /cex-balances/{apiKeyId}/account-details`

**接口描述**: 获取指定API密钥的账户详情

**响应示例**:
```json
{
  "success": true,
  "data": {
    "apiKeyId": 123,
    "cexName": "OKX",
    "accountType": "SWAP",
    "balance": 50000.00,
    "available": 45000.00,
    "marginUsed": 5000.00,
    "unrealizedPnL": 1000.00,
    "updateTime": "2025-12-19T10:30:00Z"
  }
}
```

---

### 6. 仪表板接口 (DashboardController)

#### 6.1 获取仪表板统计数据

**接口地址**: `GET /dashboard/statistics`

**接口描述**: 获取仪表板展示所需的统计数据

**响应示例**:
```json
{
  "success": true,
  "data": {
    "totalBalance": 150000.00,
    "totalPnL": 5000.00,
    "totalPnLRatio": 0.035,
    "activePositions": 5,
    "activeOrders": 2,
    "winRate": 0.65
  }
}
```

---

## 错误码说明

| 错误码 | 错误类型 | 描述 | HTTP状态码 |
|--------|----------|------|------------|
| CHAT_001 | 会话错误 | 会话不存在或已过期 | 404 |
| CHAT_002 | 消息错误 | 消息发送失败 | 500 |
| RISK_001 | 风控错误 | 风控模式设置失败 | 500 |
| RISK_002 | 风险超限 | 当前风险超出允许范围 | 403 |
| RISK_003 | 交易风格错误 | 不支持的交易风格 | 400 |
| POSITION_001 | 持仓错误 | 持仓查询失败 | 500 |
| TRADE_001 | 交易错误 | 交易执行失败 | 500 |
| TRADE_002 | 余额不足 | 账户余额不足 | 400 |
| TRADE_003 | 仓位限制 | 仓位超过限制 | 400 |
| TRADE_004 | 风控拦截 | 订单被风控系统拦截 | 403 |
| TRADE_005 | 订单不存在 | 订单不存在或已处理 | 404 |
| BALANCE_001 | 余额错误 | 余额查询失败 | 500 |
| DASHBOARD_001 | 仪表板错误 | 统计数据获取失败 | 500 |

## 请求头说明

所有API请求需要包含以下请求头：

```
Content-Type: application/json
Authorization: Bearer {access_token}
X-API-Key: {api_key}
X-Timestamp: {unix_timestamp}
X-Signature: {request_signature}
```

## 签名算法

请求签名使用HMAC-SHA256算法：

```
signature = HMAC-SHA256(
    method + url + timestamp + body,
    api_secret
)
```

## 更新日志

### v1.1.0 (2025-12-19)
- 新增多轮会话式聊天决策功能 (ChatController)
- 新增风控模式控制接口 (RiskControlController)
- 新增持仓高级查询和统计接口 (PositionController)
- 优化交易执行接口 (TradingController)
- 新增余额查询接口 (BalanceController)
- 新增仪表板统计数据接口 (DashboardController)

### v1.0.0 (2025-11-01)
- 初始版本发布
- 基础交易决策功能
- 核心API接口支持
