# OkxApiService 方法调用链路全面分析

> **文档版本**: v1.1
> **创建时间**: 2026-02-26
> **作者**: Page
> **分析范围**: OkxApiService的所有17个公开方法

---

## 目录

1. [概述](#概述)
2. [账户相关方法](#账户相关方法)
3. [交易相关方法](#交易相关方法)
4. [策略订单方法](#策略订单方法)
5. [行情数据方法](#行情数据方法)
6. [订单查询方法](#订单查询方法)
7. [历史数据方法](#历史数据方法)
8. [合约信息方法](#合约信息方法)
9. [调用链路汇总表](#调用链路汇总表)

---

## 概述

### OkxApiService 简介

**文件路径**: `/backend/src/main/java/com/crypto/trade/service/okx/OkxApiService.java`

**核心职责**:
- 提供纯粹的OKX API调用功能,不包含业务逻辑
- HTTP请求签名(基于HMAC-SHA256)
- 限流控制(全局限流 + API Key级限流)
- API调用事件发布(CexApiCallEvent)
- 错误处理和重试机制

### 技术特性

| 特性 | 配置 | 说明 |
|------|------|------|
| **限流策略** | 全局15 QPS, API Key 10 QPS | 使用Guava RateLimiter |
| **等待超时** | 50ms | 从800ms优化而来 |
| **HTTP连接超时** | 10秒 | HttpClient配置 |
| **事件发布** | 所有API调用后发布CexApiCallEvent | 用于缓存失效和审计 |

### 架构演进：UnifiedCexApiService 适配器层

#### 架构变化说明

随着系统支持多交易所（如OKX、Binance等），代码中新增了 `UnifiedCexApiService` 作为统一的CEX API调用入口：

**文件路径**: `/backend/src/main/java/com/crypto/trade/service/cex/UnifiedCexApiService.java`

**调用链路变化**:
- **之前**: 业务服务直接调用 `okxApiService`
- **现在**: 业务服务调用 `unifiedCexApiService`，再由其通过 `CexApiFactory` 适配到具体的交易所实现（如 `OkxApiService`）

**调用链路图**:
```
业务服务层 (UnifiedBalanceService, AiDecisionService等)
  ↓
UnifiedCexApiService (统一入口)
  ↓
CexApiFactory.getCexApiService(apiKey) (工厂方法)
  ↓
OkxApiService / BinanceApiService (具体实现)
  ↓
OKX / Binance API
```

**适配器模式说明**:
- `UnifiedCexApiService`: 统一的CEX API服务接口，提供跨交易所的通用方法
- `CexApiService` (接口): 定义统一的CEX API抽象接口
- `OkxApiService`: OKX交易所的具体实现
- `CexApiFactory`: 工厂类，根据API Key自动选择对应的交易所实现

**统一方法列表** (17个公开方法):
1. getAccountBalance - 获取账户余额
2. getPositions - 获取持仓
3. getInstruments - 获取合约信息
4. placeOrder - 下单
5. cancelOrder - 撤单
6. closePosition - 平仓
7. getMarkPrice - 获取标记价格
8. getMarketTickers - 获取市场行情
9. getFundingRate - 获取资金费率
10. getPendingOrders - 获取待成交订单
11. getHistoryOrders - 获取历史订单
12. getPositionsHistory - 获取历史持仓
13. getMarketCandles - 获取K线数据 (2个重载)
14. setAlgoOrder - 设置策略订单
15. amendAlgoOrder - 修改策略订单
16. cancelAlgoOrder - 取消策略订单
17. getAlgoOrders - 获取策略订单

**注意**: `getHistoryOrdersArchive` 是 private 方法，不对外公开

### 公开方法列表(17个)

| 序号 | 方法名 | HTTP方法 | OKX API端点 | 功能分类 |
|------|--------|----------|-------------|----------|
| 1 | `getAccountBalance` | GET | `/api/v5/account/balance` | 账户 |
| 2 | `getPositions` | GET | `/api/v5/account/positions` | 账户 |
| 3 | `getInstruments` | GET | `/api/v5/public/instruments` | 公共数据 |
| 4 | `placeOrder(PlaceOrderReq)` | POST | `/api/v5/trade/order` | 交易 |
| 5 | `placeOrder(String)` | POST | `/api/v5/trade/order` | 交易 |
| 6 | `cancelOrder` | POST | `/api/v5/trade/cancel-order` | 交易 |
| 7 | `closePosition` | POST | `/api/v5/trade/close-position` | 交易 |
| 8 | `setAlgoOrder` | POST | `/api/v5/trade/order-algo` | 策略订单 |
| 9 | `amendAlgoOrders` | POST | `/api/v5/trade/amend-algos` | 策略订单 |
| 10 | `cancelAlgoOrder` | POST | `/api/v5/trade/cancel-algos` | 策略订单 |
| 11 | `getMarkPrice` | GET | `/api/v5/public/mark-price` | 行情 |
| 12 | `getMarketTickers` | GET | `/api/v5/market/tickers` | 行情 |
| 13 | `getFundingRate` | GET | `/api/v5/public/funding-rate` | 行情 |
| 14 | `getPendingOrders` | GET | `/api/v5/trade/orders-pending` | 订单查询 |
| 15 | `getHistoryOrders` | GET | `/api/v5/trade/orders-history` | 订单查询 |
| 16 | `getHistoryOrdersArchive` | GET | `/api/v5/trade/orders-history-archive` | 订单查询 |
| 17 | `getPositionsHistory` | GET | `/api/v5/account/positions-history` | 历史数据 |
| 18 | `getAlgoOrders` | GET | `/api/v5/trade/orders-algo-pending` | 订单查询 |
| 19 | `getMarketCandles` | GET | `/api/v5/market/candles` | 行情 |

---

## 账户相关方法

### 方法1: getAccountBalance

#### 方法签名
```java
public List<OkxAccountBalance> getAccountBalance(ApiKey apiKey, String ccy)
```

#### 功能描述
查询账户余额信息(账户级别)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/account/balance`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| ccy | String | 否 | 货币类型,如"BTC" |

#### 返回数据
```java
List<OkxAccountBalance> // 账户余额列表
```

---

### 调用链路

#### 调用者1: UnifiedBalanceService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/unified/UnifiedBalanceService.java`

**调用代码**: 第122行
```java
return unifiedCexApiService.getAccountBalance(decryptedKey, null);
```

**完整调用链路**:
```
定时任务触发 (@Scheduled cron="0 */1 * * * ?")
  ↓
UnifiedBalanceService.updateBalanceData() [第82行]
  ↓
并发处理所有活跃API Key
  ↓
getAccountBalance(keyId) [第109行]
  ↓
getDecryptedKey(keyId) [获取解密的API Key]
  ↓
unifiedCexApiService.getAccountBalance(decryptedKey, null) [第122行]
  ↓
UnifiedCexApiService.getAccountBalance() [适配器层]
  ↓
CexApiFactory.getCexApiService(apiKey) [工厂方法]
  ↓
OkxApiService.getAccountBalance() [具体实现]
  ↓
OkxApiService.sendSignedRequest() [内部调用]
  ├─ 限流检查: acquireRateLimit(apiKeyId)
  ├─ 签名生成: okxSignatureService.sign()
  ├─ HTTP请求: GET /api/v5/account/balance
  └─ 响应解析: JsonUtils.parseTo()
  ↓
返回 List<OkxAccountBalance>
  ↓
UnifiedBalanceService更新缓存
  ├─ ccyBalanceCache.put(keyId, balanceMap)
  └─ accountBalance.put(keyId, accountData)
  ↓
发布 BalanceUpdateEvent 事件
```

**调用频率**: 每1分钟(定时任务)

**调用场景**:
- 定时刷新所有活跃API Key的账户余额
- 数据用于账户权益监控和UI展示

---

#### 调用者2: (无其他直接调用者)

**备注**: 该方法目前只被`UnifiedBalanceService`调用,用于定时任务

---

### 数据流转

#### 输入参数
```java
ApiKey {
    Long keyId;              // API Key ID
    String accessKey;         // 公钥
    String secretKey;         // 私钥(加密存储)
    String passPhrase;        // 密码短语
    Boolean isLiveTrading;    // 是否实盘交易
}
```

#### 输出数据
```java
OkxAccountBalance {
    String ccy;              // 币种
    String bal;              // 总余额
    String frozenBal;        // 冻结余额
    String availBal;         // 可用余额
}
```

#### 数据用途
1. **账户权益监控**: `AccountEquityMonitorService`使用余额数据计算账户权益
2. **前端展示**: 通过REST API返回给前端,展示在"账户余额"页面
3. **缓存更新**: 更新两个缓存(`ccyBalanceCache`和`accountBalance`)
4. **事件发布**: 发布`BalanceUpdateEvent`事件,通知其他组件余额已更新

---

### 事件发布

**事件类型**: 无(该方法使用`commonFetchData`,不发布事件)

**说明**: `getAccountBalance`方法通过`commonFetchData`调用,该方法是通用查询方法,不发布`CexApiCallEvent`事件

---

### 错误处理

1. **API错误**: 返回空列表
2. **网络错误**: 捕获异常,记录日志,返回空列表
3. **限流触发**: 等待50ms,超时后继续请求(记录警告日志)

---

### 方法2: getPositions

#### 方法签名
```java
public List<OkxPosition> getPositions(ApiKey apiKey, String instType)
```

#### 功能描述
查询持仓信息

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/account/positions`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型(SWAP/FUTURES/OPTION) |

#### 返回数据
```java
List<OkxPosition> // 持仓信息列表
```

---

### 调用链路

#### 调用者1: UnifiedPositionService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/unified/UnifiedPositionService.java`

**调用代码**: 第142行
```java
List<OkxPosition> positions = unifiedCexApiService.getPositions(decryptedKey, instType);
```

**完整调用链路**:
```
定时任务触发 (@Scheduled cron="0/30 * * * * ?")
  ↓
UnifiedPositionService.updatePositionData() [第82行]
  ↓
获取所有活跃的OKX API密钥
  ↓
并发处理每个API Key (parallelStream)
  ↓
getPositionData(keyId) [第129行]
  ↓
getDecryptedKey(keyId)
  ↓
循环处理三种合约类型: SWAP, FUTURES, OPTION
  ↓
unifiedCexApiService.getPositions(decryptedKey, instType) [第142行]
  ↓
UnifiedCexApiService.getPositions() [适配器层]
  ↓
CexApiFactory.getCexApiService(apiKey) [工厂方法]
  ↓
OkxApiService.getPositions() [具体实现]
  ↓
OkxApiService.sendSignedRequest() [内部调用]
  ├─ 限流检查: acquireRateLimit(apiKeyId)
  ├─ 签名生成: okxSignatureService.sign()
  ├─ HTTP请求: GET /api/v5/account/positions?instType=SWAP
  └─ 响应解析: JsonUtils.parseTo()
  ↓
返回 List<OkxPosition>
  ↓
过滤持仓数量>0的记录
  ↓
合并三种类型的持仓数据
  ↓
updateCaches(keyId, positions) [第173行]
  ├─ positionCache.put(keyId, positions)
  ├─ calculatePositionSummary() → summaryCache.put()
  └─ assessPositionRisk() → riskCache.put()
  ↓
发布 PositionUpdateEvent 事件
```

**调用频率**: 每30秒(定时任务)

**调用场景**:
- 定时刷新所有活跃API Key的持仓数据
- 支持SWAP(永续)、FUTURES(交割)、OPTION(期权)三种类型
- 数据用于仓位监控、风险评估、UI展示

---

#### 调用者2: PositionQueryService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/market/PositionQueryService.java`

**调用代码**: 第54行
```java
List<OkxPosition> positionDataList = unifiedCexApiService.getPositions(apiKey, instType);
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
PositionQueryService.queryPositions(apiKeyId, instType) [第30行]
  ↓
getDecryptedKey(apiKeyId)
  ↓
unifiedCexApiService.getPositions(apiKey, instType) [第54行]
  ↓
UnifiedCexApiService.getPositions() [适配器层]
  ↓
CexApiFactory.getCexApiService(apiKey) [工厂方法]
  ↓
OkxApiService.getPositions() [具体实现]
  ↓
返回持仓数据
```

**调用频率**: 按需调用

**调用场景**:
- 仓位查询接口
- 仓位统计

---

### 数据流转

#### 输入参数
```java
ApiKey {
    Long keyId;              // API Key ID
    String accessKey;         // 公钥
    String secretKey;         // 私钥(加密存储)
    String passPhrase;        // 密码短语
    Boolean isLiveTrading;    // 是否实盘交易
}

instType: "SWAP" | "FUTURES" | "OPTION"
```

#### 输出数据
```java
OkxPosition {
    String instId;            // 合约ID
    String posId;             // 持仓ID
    String posSide;           // 持仓方向(long/short)
    BigDecimal pos;           // 持仓数量
    BigDecimal baseBal;       // 基础货币余额
    BigDecimal notionalUsd;   // 持仓价值(美元)
    BigDecimal margin;        // 保证金余额
    BigDecimal upl;           // 未实现盈亏
    BigDecimal uplRatio;      // 未实现盈亏比率
    BigDecimal lever;         // 杠杆倍数
    Long dataIngestionTime;   // 数据摄入时间
}
```

#### 数据用途
1. **仓位监控**: 实时监控用户持仓情况
2. **风险评估**: 计算仓位风险等级和评分
3. **UI展示**: 在"持仓列表"页面展示
4. **缓存更新**: 更新三重缓存(positionCache/summaryCache/riskCache)
5. **事件发布**: 发布`PositionUpdateEvent`事件,通知其他组件仓位已更新

---

### 事件发布

**事件类型**: 无(该方法使用`commonFetchData`,不发布事件)

---

### 错误处理

1. **API错误**: 返回空列表
2. **网络错误**: 捕获异常,记录日志,返回空列表
3. **限流触发**: 等待50ms,超时后继续请求(记录警告日志)
4. **部分失败**: 如果某个instType查询失败,继续查询其他类型

---

## 交易相关方法

### 方法3: placeOrder (PlaceOrderReq版本)

#### 方法签名
```java
public OkxAlgoState.OkxAlgoStateResponse placeOrder(ApiKey apiKey, PlaceOrderReq request)
```

#### 功能描述
下单(策略版,使用PlaceOrderReq对象)

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/order`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| request | PlaceOrderReq | 是 | 下单请求对象 |

#### 返回数据
```java
OkxAlgoState.OkxAlgoStateResponse // 下单响应
```

---

### 调用链路

#### 调用者1: OrderHandler

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/trading/OrderHandler.java`

**调用代码**: 第96行
```java
OkxAlgoState.OkxAlgoStateResponse apiResponse = okxApiService.placeOrder(apiKey, placeOrderReq);
```

**完整调用链路**:
```
AI决策生成
  ↓
AiDecisionService.makeTradingDecision()
  ↓
TradeActionProcessor.executeTradeAction() [第220行]
  ↓
OrderHandler.executePlaceOrder() [第70行]
  ↓
构建 PlaceOrderReq 对象
  ├─ instId: 合约ID
  ├─ tdMode: 交易模式
  ├─ side: 方向(buy/sell)
  ├─ ordType: 订单类型
  ├─ sz: 数量
  └─ px: 价格(限价单)
  ↓
okxApiService.placeOrder(apiKey, placeOrderReq) [第96行]
  ↓
OkxApiService内部处理 [第364-401行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 序列化请求: JsonUtils.toJsonString(request)
  ├─ 提取instId: request.getInstId()
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_PLACE_ORDER, requestParams)
  │   ├─ 限流检查: acquireRateLimit(apiKeyId)
  │   ├─ 请求签名: okxSignatureService.sign()
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: JsonUtils.parseTo(response, OkxAlgoStateResponse.class)
  ├─ 提取ordId: apiResponse.getData().get(0).getOrdId()
  └─ 发布成功事件: publishSuccessEventWithKeyId(PLACE_ORDER, ...)
      ├─ 事件内容: CexApiCallEvent
      ├─ apiType: CexApiType.PLACE_ORDER
      ├─ orderId: 订单ID
      ├─ instId: 合约ID
      └─ apiKeyId: API Key ID
  ↓
返回 OkxAlgoStateResponse
  ↓
OrderHandler处理响应
  └─ 返回订单结果给调用者
  ↓
事件监听器响应
  ├─ CexApiCallEventListener: 异步保存API调用记录到数据库
  ├─ PositionHandler: 清除pendingOrdersCache
  ├─ UnifiedPositionService: 清除positionCache三重缓存
  └─ OKXTradingService: (不处理PLACE_ORDER)
```

**调用频率**: 按需调用(交易时)

**调用场景**:
- AI决策生成后执行下单操作
- 支持市价单和限价单
- 响应包含订单ID(ordId)

---

#### 调用者2: (无其他直接调用者)

**备注**: 该方法目前主要被`OrderHandler`调用,用于执行AI决策的下单操作

---

### 数据流转

#### 输入参数
```java
PlaceOrderReq {
    String instId;        // 合约ID
    String tdMode;        // 交易模式(cross/isolated)
    String side;          // 方向(buy/sell)
    String ordType;       // 订单类型(market/limit/post_only/...)
    String sz;            // 数量
    String ccy;           // 币种(保证金模式时必填)
    String px;            // 价格(限价单必填)
    String tag;           // 订单标签
    String posSide;       // 持仓方向(开平仓时必填)
}
```

#### 输出数据
```java
OkxAlgoState {
    String ordId;         // 订单ID
    String clOrdId;       // 客户自定义订单ID
    String tag;           // 订单标签
    String sCode;         // 事件代码
    String sMsg;          // 事件消息
}
```

#### 数据用途
1. **订单创建**: 创建新的交易订单
2. **订单跟踪**: 使用ordId查询订单状态
3. **事件发布**: 触发缓存失效,确保数据一致性

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**发布时机**: API调用完成后(无论成功或失败)

**事件内容**:
```java
CexApiCallEvent {
    apiType: CexApiType.PLACE_ORDER,
    exchange: CexExchange.OKX,
    httpMethod: CexHttpMethod.POST,
    apiPath: "/api/v5/trade/order",
    requestParams: JSON字符串,
    callTime: 调用时间,
    responseTime: 响应时间,
    durationMs: 耗时(毫秒),
    orderId: 订单ID,
    instId: 合约ID,
    httpStatus: 200,
    responseBody: 响应体,
    apiKeyId: API Key ID,
    status: SUCCESS/FAILED
}
```

**事件监听器**:
1. `CexApiCallEventListener`: 异步保存API调用记录到数据库
2. `PositionHandler`: 清除`pendingOrdersCache`
3. `UnifiedPositionService`: 清除`positionCache`/`summaryCache`/`riskCache`

---

### 错误处理

1. **API错误**:
   - 发布失败事件
   - 返回失败响应: `OkxAlgoStateResponse.fail("下单失败: " + e.getMessage())`

2. **网络错误**:
   - 发布失败事件
   - 记录错误日志
   - 返回失败响应

3. **限流触发**:
   - 等待50ms
   - 超时后记录警告日志,继续请求

---

### 方法4: placeOrder (String版本)

#### 方法签名
```java
public OkxOrderResponseData placeOrder(ApiKey apiKey, String orderData) throws Exception
```

#### 功能描述
下单(JSON字符串版本)

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/order`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| orderData | String | 是 | 订单数据JSON字符串 |

#### 返回数据
```java
OkxOrderResponseData // 下单响应数据
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第87行
```java
OkxOrderResponseData orderResponse = okxApiService.placeOrder(apiKey, requestBody);
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.placeOrder(apiKey, orderRequest) [第77行]
  ↓
构建请求参数: buildOrderParams(orderRequest)
  ├─ instId: 合约ID
  ├─ tdMode: 交易模式
  ├─ side: 方向
  ├─ ordType: 订单类型
  ├─ sz: 数量
  └─ px: 价格(可选)
  ↓
序列化为JSON: objectMapper.writeValueAsString(params)
  ↓
okxApiService.placeOrder(apiKey, requestBody) [第87行]
  ↓
OkxApiService内部处理 [第411-462行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 提取instId: extractInstIdFromJson(orderData)
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_PLACE_ORDER, orderData)
  │   ├─ 限流检查
  │   ├─ 请求签名
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: ResponseValidator.safeParseJson(response)
  ├─ 检查响应码: rootNode.path("code").asText()
  │   ├─ 成功("0") → 提取ordId, 发布成功事件
  │   └─ 失败 → 提取错误信息, 发布失败事件, 抛出异常
  └─ 返回 OkxOrderResponseData
      ├─ ordId: 订单ID
      ├─ clOrdId: 客户自定义订单ID
      └─ tag: 订单标签
  ↓
OKXTradingService.parseOrderResponse(orderResponse) [第90行]
  ↓
返回 TradingResult
  ↓
前端或其他服务接收结果
```

**调用频率**: 按需调用(交易时)

**调用场景**:
- 提供下单接口给前端
- 支持市价单、限价单、只做maker单等
- 响应包含详细的订单信息

---

### 数据流转

#### 输入参数
```java
orderData (JSON字符串):
{
  "instId": "BTC-USDT-SWAP",    // 合约ID
  "tdMode": "cross",             // 交易模式
  "side": "buy",                  // 方向
  "ordType": "market",            // 订单类型
  "sz": "100"                     // 数量
}
```

#### 输出数据
```java
OkxOrderResponseData {
    String ordId;         // 订单ID
    String clOrdId;       // 客户自定义订单ID
    String tag;           // 订单标签
    String sCode;         // 事件代码
    String sMsg;          // 事件消息
}
```

#### 数据用途
1. **订单创建**: 创建新的交易订单
2. **订单跟踪**: 使用ordId查询订单状态
3. **事件发布**: 触发缓存失效

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**发布时机**: API调用完成后

**事件监听器**: 与PlaceOrderReq版本相同

---

### 错误处理

1. **API错误**: 抛出RuntimeException,包含错误信息
2. **JSON解析错误**: 抛出异常
3. **网络错误**: 抛出异常到调用者处理

---

### 方法5: cancelOrder

#### 方法签名
```java
public OkxAlgoState.OkxAlgoStateResponse cancelOrder(ApiKey apiKey, OkxOrderReq req)
```

#### 功能描述
撤单

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/cancel-order`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| req | OkxOrderReq | 是 | 撤单请求数据 |

#### 返回数据
```java
OkxAlgoState.OkxAlgoStateResponse // 撤单响应
```

---

### 调用链路

#### 调用者1: OrderHandler

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/trading/OrderHandler.java`

**调用代码**: 第142行
```java
OkxAlgoState.OkxAlgoStateResponse response = okxApiService.cancelOrder(apiKey, OkxOrderReq.builder()...);
```

**完整调用链路**:
```
AI决策生成
  ↓
AiDecisionService.makeTradingDecision()
  ↓
TradeActionProcessor.executeTradeAction() [第220行]
  ↓
OrderHandler.executeCancelOrder() [第115行]
  ↓
构建 OkxOrderReq 对象
  ├─ ordId: 订单ID
  └─ instId: 合约ID
  ↓
okxApiService.cancelOrder(apiKey, req) [第142行]
  ↓
OkxApiService内部处理 [第471-510行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 序列化请求: JsonUtils.toJsonString(req)
  ├─ 提取instId: req.getInstId()
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_CANCEL_ORDER, requestParams)
  │   ├─ 限流检查
  │   ├─ 请求签名
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: JsonUtils.parseTo(response, OkxAlgoStateResponse.class)
  ├─ 提取ordId: apiResponse.getData().get(0).getOrdId()
  └─ 发布成功事件: publishSuccessEvent(CANCEL_ORDER, ...)
  ↓
返回 OkxAlgoStateResponse
  ↓
OrderHandler处理响应
  ↓
事件监听器响应
  ├─ CexApiCallEventListener: 异步保存API调用记录
  ├─ PositionHandler: 清除pendingOrdersCache
  ├─ UnifiedPositionService: (不处理CANCEL_ORDER)
  └─ OKXTradingService: (不处理CANCEL_ORDER)
```

**调用频率**: 按需调用(撤单时)

**调用场景**:
- AI决策生成后执行撤单操作
- 撤销指定的待成交订单

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**事件监听器**:
1. `CexApiCallEventListener`: 异步保存API调用记录
2. `PositionHandler`: 清除`pendingOrdersCache`

---

### 方法6: closePosition

#### 方法签名
```java
public OkxApiResponse<Void> closePosition(ApiKey apiKey, ClosePositionRequest request)
```

#### 功能描述
平仓

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/close-position`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| request | ClosePositionRequest | 是 | 平仓请求数据 |

#### 返回数据
```java
OkxApiResponse<Void> // 平仓响应
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第191行
```java
OkxApiResponse<Void> response = okxApiService.closePosition(apiKey, closePositionRequest);
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.closePosition(apiKey, instId, pos, ccy, mgnMode) [第160行]
  ↓
构建 ClosePositionRequest 对象
  ├─ instId: 合约ID
  ├─ posSide: 持仓方向
  ├─ ccy: 币种
  └─ mgnMode: 保证金模式
  ↓
okxApiService.closePosition(apiKey, request) [第191行]
  ↓
OkxApiService内部处理 [第519-547行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 序列化请求: JsonUtils.toJsonString(request)
  ├─ 提取instId: extractInstIdFromClosePositionRequest(request)
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_CLOSE_POSITION, requestParams)
  │   ├─ 限流检查
  │   ├─ 请求签名
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: JsonUtils.parseTo(response, OkxApiResponse.class)
  └─ 发布成功事件: publishSuccessEventWithKeyId(CLOSE_POSITION, ...)
  ↓
返回 OkxApiResponse<Void>
  ↓
OKXTradingService处理响应
  ↓
事件监听器响应
  ├─ CexApiCallEventListener: 异步保存API调用记录
  ├─ PositionHandler: (不处理CLOSE_POSITION)
  ├─ UnifiedPositionService: 清除positionCache/summaryCache/riskCache
  └─ OKXTradingService: (不处理CLOSE_POSITION)
```

**调用频率**: 按需调用(平仓时)

**调用场景**:
- 手动平仓操作
- AI决策生成的平仓操作

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**事件监听器**:
1. `CexApiCallEventListener`: 异步保存API调用记录
2. `UnifiedPositionService`: 清除`positionCache`/`summaryCache`/`riskCache`

---

## 策略订单方法

### 方法7: setAlgoOrder

#### 方法签名
```java
public OkxAlgoState.OkxAlgoStateResponse setAlgoOrder(ApiKey apiKey, OkxAlgoRequest request)
```

#### 功能描述
设置策略订单(止盈止损)

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/order-algo`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| request | OkxAlgoRequest | 是 | 策略订单请求对象 |

#### 返回数据
```java
OkxAlgoState.OkxAlgoStateResponse // 策略订单响应
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第898行
```java
response = okxApiService.setAlgoOrder(apiKey, okxAlgoRequestBuilder.build());
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.setAlgoOrder() [第800行]
  ↓
构建 OkxAlgoRequest 对象
  ├─ instId: 合约ID
  ├─ tdMode: 交易模式
  ├─ side: 方向
  ├─ ordType: 订单类型(conditional/oco)
  ├─ sz: 数量
  ├─ tpTriggerPx: 止盈触发价格
  ├─ tpOrdPx: 止盈委托价格
  ├─ slTriggerPx: 止损触发价格
  └─ slOrdPx: 止损委托价格
  ↓
okxApiService.setAlgoOrder(apiKey, request) [第898行]
  ↓
OkxApiService内部处理 [第555-586行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 序列化请求: JsonUtils.toJsonString(request)
  ├─ 提取instId: request.getInstId()
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_SET_ALGO_ORDER, requestBody)
  │   ├─ 限流检查
  │   ├─ 请求签名
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: JsonUtils.parseTo(response, OkxAlgoStateResponse.class)
  ├─ 提取algoId: extractAlgoIdFromSetResponse(apiResponse)
  └─ 发布成功事件: publishSuccessEvent(SET_ALGO_ORDER, ...)
  ↓
返回 OkxAlgoStateResponse
  ↓
OKXTradingService处理响应
  ├─ 提取algoId
  └─ 返回结果
  ↓
事件监听器响应
  ├─ CexApiCallEventListener: 异步保存API调用记录
  ├─ PositionHandler: (不处理SET_ALGO_ORDER)
  ├─ UnifiedPositionService: (不处理SET_ALGO_ORDER)
  └─ OKXTradingService: 清除algoOrdersCache
```

**调用频率**: 按需调用(设置策略订单时)

**调用场景**:
- 设置止盈止损单
- 条件单
- OCO订单(One-Cancels-Other)

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**事件监听器**:
1. `CexApiCallEventListener`: 异步保存API调用记录
2. `OKXTradingService`: 清除`algoOrdersCache`

---

### 方法8: amendAlgoOrders

#### 方法签名
```java
public OkxAlgoState.OkxAlgoStateResponse amendAlgoOrders(ApiKey apiKey, OkxAmendAlgoRequest request)
```

#### 功能描述
修改策略订单

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/amend-algos`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| request | OkxAmendAlgoRequest | 是 | 修改请求对象 |

#### 返回数据
```java
OkxAlgoState.OkxAlgoStateResponse // 修改响应
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第866行
```java
response = okxApiService.amendAlgoOrders(apiKey, requestBuilder.build());
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.amendAlgoOrder() [第840行]
  ↓
构建 OkxAmendAlgoRequest 对象
  ├─ algoId: 算法订单ID
  ├─ instId: 合约ID
  ├─ tpTriggerPx: 新的止盈触发价格
  ├─ tpOrdPx: 新的止盈委托价格
  ├─ slTriggerPx: 新的止损触发价格
  └─ slOrdPx: 新的止损委托价格
  ↓
okxApiService.amendAlgoOrders(apiKey, request) [第866行]
  ↓
OkxApiService内部处理 [第595-627行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 序列化请求: JsonUtils.toJsonString(request)
  ├─ 提取instId和algoId: request.getInstId(), request.getAlgoId()
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_AMEND_ALGOS, requestBody)
  │   ├─ 限流检查
  │   ├─ 请求签名
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: JsonUtils.parseTo(response, OkxAlgoStateResponse.class)
  ├─ 提取algoId: extractAlgoIdFromAmendResponse(apiResponse)
  └─ 发布成功事件: publishSuccessEvent(AMEND_ALGO_ORDER, ...)
  ↓
返回 OkxAlgoStateResponse
  ↓
OKXTradingService处理响应
  ↓
事件监听器响应
  ├─ CexApiCallEventListener: 异步保存API调用记录
  ├─ PositionHandler: (不处理AMEND_ALGO_ORDER)
  ├─ UnifiedPositionService: (不处理AMEND_ALGO_ORDER)
  └─ OKXTradingService: 清除algoOrdersCache
```

**调用频率**: 按需调用(修改策略订单时)

**调用场景**:
- 修改止盈止损单的价格
- 调整策略订单参数

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**事件监听器**:
1. `CexApiCallEventListener`: 异步保存API调用记录
2. `OKXTradingService`: 清除`algoOrdersCache`

---

### 方法9: cancelAlgoOrder

#### 方法签名
```java
public OkxOperationResponseData cancelAlgoOrder(ApiKey apiKey, OkxAlgoCancelRequest algoCancelRequest) throws Exception
```

#### 功能描述
撤销策略订单

#### API端点
- **HTTP方法**: POST
- **路径**: `/api/v5/trade/cancel-algos`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| algoCancelRequest | OkxAlgoCancelRequest | 是 | 撤销请求对象 |

#### 返回数据
```java
OkxOperationResponseData // 撤销响应
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第938行和第966行

```java
// 第938行 - 单个algoId撤销
OkxOperationResponseData response = okxApiService.cancelAlgoOrder(apiKey,
    OkxAlgoCancelRequest.builder().algoId(algoId).build());

// 第966行 - 带instId的撤销
OkxOperationResponseData response = okxApiService.cancelAlgoOrder(apiKey, request);
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.cancelAlgoOrder() [第910行或第950行]
  ↓
构建 OkxAlgoCancelRequest 对象
  ├─ algoId: 算法订单ID
  └─ instId: 合约ID(可选)
  ↓
okxApiService.cancelAlgoOrder(apiKey, algoCancelRequest) [第938/966行]
  ↓
OkxApiService内部处理 [第637-681行]
  ├─ 记录调用时间: callTime = LocalDateTime.now()
  ├─ 序列化请求: JsonUtils.toJsonString(List.of(algoCancelRequest))
  ├─ 提取instId和algoId
  ├─ 调用API: sendSignedRequest(apiKey, "POST", API_PATH_CANCEL_ALGOS, requestParams)
  │   ├─ 限流检查
  │   ├─ 请求签名
  │   ├─ HTTP POST请求
  │   └─ 接收响应
  ├─ 解析响应: JsonUtils.parseTo(response, OkxOperationResponseData.class)
  ├─ 检查响应码: responseData.getCode()
  │   ├─ 成功("0") → 发布成功事件
  │   └─ 失败 → 发布失败事件, 抛出异常
  └─ 发布成功事件: publishSuccessEvent(CANCEL_ALGO_ORDER, ...)
  ↓
返回 OkxOperationResponseData
  ↓
OKXTradingService处理响应
  ↓
事件监听器响应
  ├─ CexApiCallEventListener: 异步保存API调用记录
  ├─ PositionHandler: (不处理CANCEL_ALGO_ORDER)
  ├─ UnifiedPositionService: (不处理CANCEL_ALGO_ORDER)
  └─ OKXTradingService: 清除algoOrdersCache
```

**调用频率**: 按需调用(撤销策略订单时)

**调用场景**:
- 撤销止盈止损单
- 取消未触发的条件单

---

### 事件发布

**事件类型**: `CexApiCallEvent` (成功/失败)

**事件监听器**:
1. `CexApiCallEventListener`: 异步保存API调用记录
2. `OKXTradingService`: 清除`algoOrdersCache`

---

## 行情数据方法

### 方法10: getMarkPrice

#### 方法签名
```java
public List<OkxMarkPrice> getMarkPrice(ApiKey apiKey, String instId)
```

#### 功能描述
获取标记价格

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/public/mark-price`
- **签名**: 不需要(公共接口)

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 否 | API密钥信息(可为null) |
| instId | String | 是 | 产品ID |

#### 返回数据
```java
List<OkxMarkPrice> // 标记价格列表
```

---

### 调用链路

#### 调用者1: TradingController

**文件位置**: `/backend/src/main/java/com/crypto/trade/rest/controller/TradingController.java`

**调用代码**: 第343行
```java
List<OkxMarkPrice> markPrices = okxApiService.getMarkPrice(apiKey, instId);
```

**完整调用链路**:
```
前端HTTP请求
  ↓
TradingController.getMarkPrice() [第320行]
  ↓
获取API Key: apiKeyService.getDecryptedKey(apiKeyId)
  ↓
okxApiService.getMarkPrice(apiKey, instId) [第343行]
  ↓
OkxApiService内部处理 [第690-693行]
  ├─ 构建查询参数: "instId=" + instId
  ├─ 调用API: commonFetchData("标记价格", API_PATH_GET_MARK_PRICE, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   │   ├─ apiKey为null,跳过签名
  │   │   ├─ HTTP GET请求
  │   │   └─ 接收响应
  │   └─ 解析响应: JsonUtils.parseTo(response, OkxMarkPriceResponse.class)
  └─ 返回 List<OkxMarkPrice>
  ↓
Controller处理响应
  ↓
返回给前端
```

**调用频率**: 按需调用(前端请求时)

**调用场景**:
- 前端查询合约标记价格
- 用于计算未实现盈亏

---

#### 调用者2: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第770行
```java
List<OkxMarkPrice> markPrice = okxApiService.getMarkPrice(apiKey, instId);
```

**完整调用链路**:
```
内部服务调用
  ↓
OKXTradingService.getCachedMarkPrice() [第745行]
  ↓
检查缓存: markPriceCache.getIfPresent(instId)
  ↓
缓存未命中或过期
  ↓
okxApiService.getMarkPrice(apiKey, instId) [第770行]
  ↓
获取标记价格
  ↓
更新缓存: markPriceCache.put(instId, markPriceDataList)
  ↓
返回标记价格
```

**调用频率**: 按需调用(缓存未命中时)

**调用场景**:
- 获取标记价格(带10秒缓存)
- 用于持仓盈亏计算

---

#### 调用者3: UnifiedPriceDataService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/market/UnifiedPriceDataService.java`

**调用代码**: 第102行
```java
List<OkxMarkPrice> markPriceDataList = okxApiService.getMarkPrice(apiKey, instId);
```

**调用链路**: 用于统一图表数据接口,返回标记价格

---

### 数据用途

1. **价格展示**: 前端展示合约标记价格
2. **盈亏计算**: 计算持仓的未实现盈亏
3. **缓存优化**: OKXTradingService使用10秒缓存减少API调用

---

### 方法11: getMarketTickers

#### 方法签名
```java
public List<OkxMarketTicker> getMarketTickers(ApiKey apiKey, String instType, String instId)
```

#### 功能描述
获取市场行情(Tickers)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/market/tickers`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型 |
| instId | String | 否 | 产品ID(可选,用于过滤) |

#### 返回数据
```java
List<OkxMarketTicker> // 市场行情列表
```

---

### 调用链路

#### 调用者1: UnifiedMarketTickerService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/UnifiedMarketTickerService.java`

**调用代码**: 第470行
```java
List<OkxMarketTicker> okxTickers = okxApiService.getMarketTickers(apiKey, "SWAP", null);
```

**完整调用链路**:
```
定时任务或手动触发
  ↓
UnifiedMarketTickerService.syncMarketTickersScheduled() [第330行]
  ↓
并行处理各供应商数据 (CompletableFuture)
  ↓
getMarketTickersFromExchange(vendor, instType) [第390行]
  ↓
okxApiService.getMarketTickers(apiKey, "SWAP", null) [第470行]
  ↓
OkxApiService内部处理 [第703-709行]
  ├─ 构建查询参数: "instType=" + instType
  ├─ 调用API: commonFetchData("市场Tickers", API_PATH_GET_MARKET_TICKERS, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应
  ├─ 过滤: 如果指定instId,只返回匹配的合约
  └─ 返回 List<OkxMarketTicker>
  ↓
数据处理
  ├─ 转换为Top30MarketTickerDto
  ├─ 更新缓存: @CacheEvict + @Cacheable
  └─ 保存到DuckDB: futuresTickerDataRepository.saveAll()
  ↓
返回Top30合约数据
```

**调用频率**: 每30分钟(定时任务)

**调用场景**:
- 定时同步市场行情数据
- 更新Top30合约排行
- 数据持久化到DuckDB

---

### 数据用途

1. **市场行情展示**: 展示Top30交易额合约
2. **数据分析**: 存储到DuckDB用于历史分析
3. **缓存优化**: 使用5分钟缓存

---

### 方法12: getFundingRate

#### 方法签名
```java
public List<OkxFundingRateData> getFundingRate(ApiKey apiKey, String instId)
```

#### 功能描述
获取资金费率

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/public/funding-rate`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instId | String | 是 | 产品ID |

#### 返回数据
```java
List<OkxFundingRateData> // 资金费率列表
```

---

### 调用链路

#### 调用者1: AiDecisionService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/AiDecisionService.java`

**调用代码**: 第476行
```java
List<OkxFundingRateData> fundingRates = unifiedCexApiService.getFundingRate(apiKey, instId);
```

**完整调用链路**:
```
AI决策生成
  ↓
AiDecisionService.buildPositionDetails() [第450行]
  ↓
获取持仓详情(包含资金费率)
  ↓
unifiedCexApiService.getFundingRate(apiKey, instId) [第476行]
  ↓
UnifiedCexApiService.getFundingRate() [适配器层]
  ↓
OkxApiService.getFundingRate() [具体实现]
  ├─ 构建查询参数: "instId=" + instId
  ├─ 调用API: commonFetchData("资金费率", API_PATH_GET_FUNDING_RATE, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应
  └─ 返回 List<OkxFundingRateData>
  ↓
构建持仓详情信息
  ├─ 合约信息
  ├─ 资金费率
  ├─ 最小交易单位
  └─ 返回给AI
```

**调用频率**: 按需调用(AI决策生成时)

**调用场景**:
- AI生成持仓详情
- 资金费率信息提供给AI参考

---

#### 调用者2: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第690行
```java
List<OkxFundingRateData> fundingRateDataList = unifiedCexApiService.getFundingRate(apiKey, instId);
```

**调用链路**: 用于查询资金费率信息

---

#### 调用者3: UnifiedPriceDataService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/market/UnifiedPriceDataService.java`

**调用代码**: 第136行
```java
List<OkxFundingRateData> fundingRateDataList = unifiedCexApiService.getFundingRate(apiKey, instId);
```

**调用链路**: 用于统一图表数据接口

---

### 数据用途

1. **AI决策**: 资金费率是AI决策的重要参考指标
2. **持仓详情**: 展示持仓的资金费率信息
3. **图表数据**: 统一图表接口返回资金费率

---

### 方法13: getMarketCandles

#### 方法签名
```java
public List<OkxMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit)
```

#### 功能描述
获取K线数据

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/market/candles`
- **签名**: 可选(公共接口,但传入apiKey可提高限流)

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 否 | API密钥信息(可为null) |
| instId | String | 是 | 产品ID |
| period | String | 否 | K线周期(1m/3m/5m/15m/30m/1H/4H/1D等) |
| limit | Integer | 否 | 返回数量(默认100,最大300) |

#### 返回数据
```java
List<OkxMarketCandle> // K线数据列表
```

---

### 调用链路

#### 调用者1: UnifiedPriceDataService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/market/UnifiedPriceDataService.java`

**调用代码**: 第124行、第613行、第668行等多个位置

**完整调用链路**:
```
前端或其他服务调用
  ↓
UnifiedPriceDataService.getUnifiedChartData() [第550行]
  ↓
解析请求参数
  ├─ instId: 合约ID
  ├─ timeframe: K线周期
  ├─ limit: 数量
  └─ indicators: 技术指标
  ↓
并发获取三种数据
  ├─ getMarketCandles() [第668行]
  ├─ getMarkPrice()
  └─ getFundingRate()
  ↓
getMarketCandlesWithRetry() [第590行]
  ↓
限流检查: semaphore.acquire(3) [最多3个并发]
  ↓
重试循环 (最多3次)
  ↓
okxApiService.getMarketCandles(apiKey, instId, timeframe, limit) [第613/668行]
  ↓
OkxApiService内部处理 [第832-847行]
  ├─ 标准化周期: period = period.matches("\\d+[mM]") ? period.toLowerCase() : period
  ├─ 构建查询参数: "instId=%s&bar=%s&limit=%s"
  ├─ 调用API: commonFetchData("市场K线", API_PATH_GET_MARKET_CANDLES, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应: JsonUtils.parseTo(response, OkxMarketCandleResponse.class)
  ├─ 转换数据: candles.stream().map(OkxMarketCandle::from)
  └─ 返回 List<OkxMarketCandle>
  ↓
计算技术指标 (如果请求了indicators)
  ├─ RSI
  ├─ BOLL
  ├─ EMA
  └─ SMA
  ↓
更新缓存: unifiedChartCache.put()
  ↓
返回 UnifiedChartDataResponse
  ↓
前端展示K线图表
```

**调用频率**: 极高(前端图表实时刷新)

**调用场景**:
- 前端K线图表展示
- 技术指标计算
- 价格数据分析

---

#### 调用者2: TechnicalIndicatorProcessor

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/prompt/processor/TechnicalIndicatorProcessor.java`

**调用代码**: 第282行、第2205行

**完整调用链路**:
```
AI Prompt生成
  ↓
TechnicalIndicatorProcessor.buildTechnicalIndicatorPrompt() [第250行]
  ↓
多时间周期分析 (4H/1H/5m)
  ↓
获取K线数据: getKlineDataWithRetry() [第260行]
  ↓
okxApiService.getMarketCandles(apiKey, instId, timeframe, requiredDataCount) [第282行]
  ↓
获取K线数据
  ↓
计算技术指标
  ├─ RSI: 相对强弱指标
  ├─ EMA: 指数移动平均线
  ├─ BOLL: 布林带
  └─ SMA: 简单移动平均线
  ↓
构建AI Prompt内容
  ↓
返回给AI用于决策
```

**调用频率**: 按需调用(AI生成Prompt时)

**调用场景**:
- AI分析所需的技术指标计算
- 多时间周期分析

---

#### 调用者3: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第714行
```java
return okxApiService.getMarketCandles(null, instId, period, limit);
```

**调用链路**: 提供K线数据查询服务

---

#### 调用者4: RealTimePriceService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/RealTimePriceService.java`

**调用代码**: 第65行
```java
List<OkxMarketCandle> candles1H = okxApiService.getMarketCandles(apiKey, instId, "1H", 25);
```

**调用链路**: 基于K线数据计算实时价格

---

#### 调用者5: TechnicalIndicatorService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/TechnicalIndicatorService.java`

**调用代码**: 第346行
```java
List<OkxMarketCandle> candles = okxApiService.getMarketCandles(null, instId, timeframe, limit);
```

**调用链路**: 技术指标计算服务

---

### 数据用途

1. **K线图表**: 前端图表展示
2. **技术指标计算**: RSI/BOLL/EMA/SMA等
3. **AI分析**: AI决策的技术分析输入
4. **价格计算**: 基于K线计算实时价格和涨跌幅

---

### 特殊机制

#### 并发控制
- 使用Semaphore限流,最多3个并发API调用
- 避免触发OKX API限流

#### 重试机制
- 最多重试3次
- 失败后记录日志,返回空列表

#### 缓存策略
- UnifiedPriceDataService使用2分钟缓存
- 减少API调用频率

---

## 订单查询方法

### 方法14: getPendingOrders

#### 方法签名
```java
public List<OkxOrder> getPendingOrders(ApiKey apiKey, String instType, String instId)
```

#### 功能描述
查询待成交订单(当前委托)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/trade/orders-pending`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型 |
| instId | String | 否 | 产品ID(可选) |

#### 返回数据
```java
List<OkxOrder> // 待成交订单列表
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第107行
```java
return okxApiService.getPendingOrders(apiKey, instType, null);
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.queryPendingOrders(apiKey, instType) [第106行]
  ↓
okxApiService.getPendingOrders(apiKey, instType, null) [第107行]
  ↓
OkxApiService内部处理 [第732-736行]
  ├─ 构建查询参数: "instType=%s&instId=%s"
  ├─ 调用API: commonFetchData("待成交订单", API_PATH_GET_ORDERS_PENDING, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应
  └─ 返回 List<OkxOrder>
  ↓
返回待成交订单列表
```

**调用频率**: 按需调用

**调用场景**:
- 查询当前委托订单
- 缓存机制(5分钟TTL)

---

#### 调用者2: TradeActionProcessor

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/conversation/TradeActionProcessor.java`

**调用代码**: 第658行
```java
List<OkxOrder> pendingOrders = okxApiService.getPendingOrders(apiKey, "SWAP", action.getInstId());
```

**完整调用链路**:
```
多轮对话中的交易执行
  ↓
TradeActionProcessor.executeTradeAction() [第500行]
  ↓
查询待成交订单(用于验证)
  ↓
okxApiService.getPendingOrders(apiKey, "SWAP", instId) [第658行]
  ↓
返回待成交订单列表
  ↓
验证交易条件
```

**调用频率**: 按需调用

---

### 数据用途

1. **订单展示**: 展示当前委托订单
2. **缓存管理**: PositionHandler使用缓存(5分钟TTL)
3. **交易验证**: 验证订单状态

---

### 方法15: getHistoryOrders

#### 方法签名
```java
public List<OkxOrder> getHistoryOrders(ApiKey apiKey, String instType, String instId, String state)
```

#### 功能描述
查询历史订单(最近7天)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/trade/orders-history`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型 |
| instId | String | 否 | 产品ID |
| state | String | 否 | 订单状态 |

#### 返回数据
```java
List<OkxOrder> // 历史订单列表
```

---

### 调用链路

#### 调用者1: OrderSyncService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/trading/OrderSyncService.java`

**调用代码**: 第87行
```java
okxOrders = okxApiService.getHistoryOrders(apiKey, "SWAP", null, null);
```

**完整调用链路**:
```
定时任务触发
  ↓
OrderSyncService.syncOrders() [第50行]
  ↓
获取所有活跃API Key
  ↓
并行处理每个API Key
  ↓
okxApiService.getHistoryOrders(apiKey, "SWAP", null, null) [第87行]
  ↓
OkxApiService内部处理 [第747-758行]
  ├─ 构建查询参数: "instType=" + instType + "&instId=" + instId + "&state=" + state
  ├─ 调用API: commonFetchData("历史订单", API_PATH_GET_ORDERS_HISTORY, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应
  └─ 返回 List<OkxOrder>
  ↓
数据处理
  ├─ 转换为TradeOrder实体
  ├─ 保存到数据库: tradeOrderRepository.saveAll()
  └─ 记录同步日志
```

**调用频率**: 定时任务(具体频率未在代码中体现)

**调用场景**:
- 订单同步服务
- 同步OKX历史订单到本地数据库

---

### 数据用途

1. **订单同步**: 将OKX订单同步到本地数据库
2. **历史查询**: 查询最近7天的订单历史
3. **数据分析**: 订单数据分析

---

### 方法16: getHistoryOrdersArchive

#### 方法签名
```java
public List<OkxOrder> getHistoryOrdersArchive(ApiKey apiKey, String instType, String instId, String state)
```

#### 功能描述
查询归档历史订单(最近3个月)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/trade/orders-history-archive`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型 |
| instId | String | 否 | 产品ID |
| state | String | 否 | 订单状态 |

#### 返回数据
```java
List<OkxOrder> // 归档历史订单列表
```

---

### 调用链路

#### 调用者1: (未找到直接调用者)

**备注**: 该方法已定义但在当前代码库中未找到调用者,可能是预留接口

---

### 方法17: getAlgoOrders

#### 方法签名
```java
public List<OkxAlgoOrder> getAlgoOrders(ApiKey apiKey, String instType, String instId, String algoId, String algoClOrdId)
```

#### 功能描述
查询算法订单(止盈止损单)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/trade/orders-algo-pending`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型 |
| instId | String | 否 | 产品ID |
| algoId | String | 否 | 算法订单ID |
| algoClOrdId | String | 否 | 客户端自定义算法订单ID |

#### 返回数据
```java
List<OkxAlgoOrder> // 算法订单列表
```

---

### 调用链路

#### 调用者1: OKXTradingService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/OKXTradingService.java`

**调用代码**: 第802行
```java
List<OkxAlgoOrder> algoOrders = okxApiService.getAlgoOrders(apiKey, "SWAP", null, null, null);
```

**完整调用链路**:
```
前端或其他服务调用
  ↓
OKXTradingService.queryAlgoOrders(apiKey, useCache) [第789行]
  ↓
检查缓存: algoOrdersCache.getIfPresent(apiKeyId)
  ↓
缓存命中?
  ├─ 是 → 返回缓存数据
  └─ 否 → 继续下一步
  ↓
okxApiService.getAlgoOrders(apiKey, "SWAP", null, null, null) [第802行]
  ↓
OkxApiService内部处理 [第824-830行]
  ├─ 构建查询参数: "ordType=conditional,oco&instType=%s&instId=%s&algoId=%s&algoClOrdId=%s"
  ├─ 调用API: commonFetchData("算法订单", API_PATH_GET_ORDERS_ALGO_PENDING, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应
  └─ 返回 List<OkxAlgoOrder>
  ↓
更新缓存: algoOrdersCache.put(apiKeyId, algoOrders)
  ↓
返回算法订单列表
```

**调用频率**: 按需调用(缓存未命中时)

**调用场景**:
- 查询止盈止损单
- 缓存机制(5分钟TTL)
- 事件驱动失效(设置/修改/撤销后失效)

---

### 数据用途

1. **策略订单展示**: 展示算法订单(止盈止损)
2. **缓存管理**: OKXTradingService使用缓存
3. **订单管理**: 查询和管理策略订单

---

## 历史数据方法

### 方法18: getPositionsHistory

#### 方法签名
```java
public List<OkxPosition> getPositionsHistory(ApiKey apiKey, String instType, String instId, String after, String before, Integer limit)
```

#### 功能描述
查询账户历史持仓(最近7天)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/account/positions-history`
- **签名**: 需要

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 是 | API密钥信息 |
| instType | String | 是 | 产品类型 |
| instId | String | 否 | 产品ID |
| after | String | 否 | 请求此时间戳之后的数据(毫秒时间戳) |
| before | String | 否 | 请求此时间戳之前的数据(毫秒时间戳) |
| limit | Integer | 否 | 返回结果数量(默认100,最大100) |

#### 返回数据
```java
List<OkxPosition> // 历史持仓列表
```

---

### 调用链路

#### 调用者1: PositionSnapshotPersistenceService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/PositionSnapshotPersistenceService.java`

**调用代码**: 第104行
```java
List<OkxPosition> positions = okxApiService.getPositionsHistory(decryptedKey, instType, ...);
```

**完整调用链路**:
```
定时任务触发
  ↓
PositionSnapshotPersistenceService.persistPositionSnapshots() [第60行]
  ↓
获取所有活跃API Key
  ↓
并发处理每个API Key
  ↓
okxApiService.getPositionsHistory(decryptedKey, instType, ...) [第104行]
  ↓
OkxApiService内部处理 [第794-812行]
  ├─ 构建查询参数: "instType=%s&instId=%s&after=%s&before=%s&limit=%s"
  ├─ 调用API: commonFetchData("历史持仓", API_PATH_GET_POSITIONS_HISTORY, ...)
  │   ├─ sendSignedRequest(apiKey, "GET", path, "")
  │   └─ 解析响应
  └─ 返回 List<OkxPosition>
  ↓
数据处理
  ├─ 转换为PositionSnapshot实体
  ├─ 保存到数据库: positionSnapshotRepository.saveAll()
  └─ 记录日志
```

**调用频率**: 定时任务(具体频率未在代码中体现)

**调用场景**:
- 仓位快照持久化服务
- 定期保存历史持仓数据到数据库

---

### 数据用途

1. **历史数据保存**: 保存历史持仓到数据库
2. **数据分析**: 仓位历史分析
3. **性能回测**: 基于历史持仓进行策略回测

---

## 合约信息方法

### 方法19: getInstruments

#### 方法签名
```java
public List<OkxInstrumentInfo> getInstruments(ApiKey apiKey, String instType, String instId)
```

#### 功能描述
获取合约信息(SKU信息)

#### API端点
- **HTTP方法**: GET
- **路径**: `/api/v5/public/instruments`
- **签名**: 可选(公共接口)

#### 参数说明
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| apiKey | ApiKey | 否 | API密钥信息(可为null) |
| instType | String | 否 | 产品类型 |
| instId | String | 否 | 产品ID |

#### 返回数据
```java
List<OkxInstrumentInfo> // 合约信息列表
```

---

### 调用链路

#### 调用者1: AiDecisionService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/AiDecisionService.java`

**调用代码**: 第539行
```java
List<OkxInstrumentInfo> instruments = unifiedCexApiService.getInstruments(null, "SWAP", instId);
```

**完整调用链路**:
```
AI决策生成
  ↓
AiDecisionService.buildPositionDetails() [第450行]
  ↓
获取合约详情(包含最小交易单位)
  ↓
unifiedCexApiService.getInstruments(null, "SWAP", instId) [第539行]
  ↓
UnifiedCexApiService.getInstruments() [适配器层]
  ↓
OkxApiService.getInstruments() [具体实现]
  ↓
OkxApiService内部处理 [第418行附近]
  ├─ 构建查询参数: "instType=%s&instId=%s"
  ├─ 调用API: commonFetchData("获取产品Info", API_PATH_GET_INSTRUMENTS, ...)
  │   ├─ sendSignedRequest(null, "GET", path, "") [apiKey为null,跳过签名]
  │   └─ 解析响应
  └─ 返回 List<OkxInstrumentInfo>
  ↓
提取最小交易单位
  ↓
构建持仓详情
  └─ 返回给AI
```

**调用频率**: 按需调用(AI决策生成时)

**调用场景**:
- 获取合约最小交易单位
- AI决策需要准确的交易单位信息

---

#### 调用者2: UnifiedInstrumentService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/UnifiedInstrumentService.java`

**调用代码**: 第262行
```java
List<OkxInstrumentInfo> okxInstruments = unifiedCexApiService.getInstruments(apiKey, "SWAP", null);
```

**完整调用链路**:
```
定时任务或手动触发
  ↓
UnifiedInstrumentService.refreshInstruments() [第240行]
  ↓
获取所有活跃API Key
  ↓
并行处理每个API Key
  ↓
unifiedCexApiService.getInstruments(apiKey, "SWAP", null) [第262行]
  ↓
UnifiedCexApiService.getInstruments() [适配器层]
  ↓
OkxApiService.getInstruments() [具体实现]
  ↓
获取合约信息
  ↓
更新缓存: instrumentCache.put()
  ↓
发布 InstrumentUpdateEvent 事件
```

**调用频率**: 按需调用(缓存未命中时)

**调用场景**:
- 合约信息管理
- 缓存机制

---

#### 调用者3: ContractInfoService

**文件位置**: `/backend/src/main/java/com/crypto/trade/service/market/ContractInfoService.java`

**调用代码**: 第36行和第110行

**调用链路**: 提供合约信息查询服务

---

### 数据用途

1. **合约信息展示**: 展示合约详细信息
2. **最小交易单位**: AI决策需要准确的交易单位
3. **缓存优化**: UnifiedInstrumentService使用缓存

---

## 调用链路汇总表

### 快速索引表

> **说明**: 以下调用者均通过 `UnifiedCexApiService` 间接调用 `OkxApiService`

| 方法名 | 主要调用者 | 调用频率 | 事件发布 | 缓存 | 功能分类 |
|--------|-----------|----------|---------|------|----------|
| `getAccountBalance` | UnifiedBalanceService | 每1分钟 | 无 | ✅ 5分钟 | 账户 |
| `getPositions` | UnifiedPositionService, PositionQueryService | 每30秒 | 无 | ✅ 5分钟 | 账户 |
| `getInstruments` | AiDecisionService, UnifiedInstrumentService, ContractInfoService | 按需 | 无 | ✅ | 公共数据 |
| `placeOrder` | OrderHandler, OKXTradingService, UnifiedTradingService | 按需 | ✅ PLACE_ORDER | ❌ | 交易 |
| `cancelOrder` | OrderHandler | 按需 | ✅ CANCEL_ORDER | ❌ | 交易 |
| `closePosition` | OKXTradingService | 按需 | ✅ CLOSE_POSITION | ❌ | 交易 |
| `setAlgoOrder` | OKXTradingService | 按需 | ✅ SET_ALGO_ORDER | ❌ | 策略订单 |
| `amendAlgoOrder` | OKXTradingService | 按需 | ✅ AMEND_ALGO_ORDER | ❌ | 策略订单 |
| `cancelAlgoOrder` | OKXTradingService | 按需 | ✅ CANCEL_ALGO_ORDER | ❌ | 策略订单 |
| `getMarkPrice` | TradingController, OKXTradingService, UnifiedPriceDataService | 按需 | 无 | ✅ 10秒 | 行情 |
| `getMarketTickers` | UnifiedMarketTickerService | 每30分钟 | 无 | ✅ 5分钟 | 行情 |
| `getFundingRate` | AiDecisionService, OKXTradingService, UnifiedPriceDataService | 按需 | 无 | ❌ | 行情 |
| `getPendingOrders` | OKXTradingService, TradeActionProcessor | 按需 | 无 | ✅ 5分钟 | 订单查询 |
| `getHistoryOrders` | OrderSyncService, CexOrderSyncService | 定时 | 无 | ❌ | 订单查询 |
| `getPositionsHistory` | PositionSnapshotPersistenceService | 定时 | 无 | ❌ | 历史数据 |
| `getAlgoOrders` | OKXTradingService | 按需 | 无 | ✅ 5分钟 | 订单查询 |
| `getMarketCandles` | UnifiedPriceDataService, TechnicalIndicatorProcessor, RealTimePriceService, TechnicalIndicatorService, OKXTradingService | 极高 | 无 | ✅ 2分钟 | 行情 |

---

### 分层调用统计

| 层次 | 调用UnifiedCexApiService的类数量 | 典型职责 |
|------|----------------------------------|----------|
| **Controller层** | 1个 (TradingController) | 接收HTTP请求 |
| **统一服务层** | 6个 | UnifiedBalanceService, UnifiedPositionService, UnifiedTradingService等 |
| **业务服务层** | 6个 | 核心交易逻辑、AI决策、订单管理 |
| **数据服务层** | 4个 | 行情数据、价格数据、市场数据 |
| **辅助服务层** | 5个 | 数据同步、快照持久化、查询服务 |

> **架构演进**: 当前代码中，业务服务层通过 `UnifiedCexApiService` 适配器层间接调用 `OkxApiService`，实现了跨交易所的统一调用

---

### 调用频率分类

| 频率分类 | 方法数量 | 方法列表 |
|---------|---------|----------|
| **极高频率** | 1个 | getMarketCandles (前端图表实时刷新) |
| **高频** | 2个 | getAccountBalance (每1分钟), getPositions (每30秒) |
| **中频** | 1个 | getMarketTickers (每30分钟) |
| **按需调用** | 14个 | 所有其他方法 |

---

### 事件发布统计

| 是否发布事件 | 方法数量 | 方法列表 |
|-------------|---------|----------|
| **发布事件** | 7个 | placeOrder, cancelOrder, closePosition, setAlgoOrder, amendAlgoOrder, cancelAlgoOrder |
| **不发布事件** | 11个 | getAccountBalance, getPositions, getInstruments, getMarkPrice, getMarketTickers, getFundingRate, getPendingOrders, getHistoryOrders, getHistoryOrdersArchive, getPositionsHistory, getAlgoOrders, getMarketCandles |

**说明**: 使用`commonFetchData`的方法不发布事件,直接调用的方法会发布事件

---

### 缓存使用统计

| 是否使用缓存 | 方法数量 | 方法列表 |
|-------------|---------|----------|
| **使用缓存** | 7个 | getAccountBalance, getPositions, getAlgoOrders, getPendingOrders, getInstruments, getMarkPrice, getMarketCandles, getMarketTickers |
| **不使用缓存** | 11个 | placeOrder, cancelOrder, closePosition, setAlgoOrder, amendAlgoOrders, cancelAlgoOrder, getFundingRate, getHistoryOrders, getHistoryOrdersArchive, getPositionsHistory |

---

## 附录

### A. 关键代码片段

#### 1. 限流检查
```java
private void acquireRateLimit(Long apiKeyId) {
    // 1. 全局限流检查(兜底保护)
    if (!globalRateLimiter.tryAcquire()) {
        boolean acquired = globalRateLimiter.tryAcquire(50, TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("全局限流触发,API Key {} 等待超时(50ms),将继续请求", apiKeyId);
        }
    }

    // 2. API Key级别限流检查
    RateLimiter apiKeyLimiter = getRateLimiterForApiKey(apiKeyId);
    if (!apiKeyLimiter.tryAcquire()) {
        boolean acquired = apiKeyLimiter.tryAcquire(50, TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("API Key {} 限流触发(QPS={}),等待超时(50ms),将继续请求", apiKeyId, perApiKeyQps);
        }
    }
}
```

#### 2. 事件发布
```java
// 发布成功事件(带apiKeyId)
private void publishSuccessEventWithKeyId(CexApiType apiType, String requestParams,
                                          LocalDateTime callTime, LocalDateTime responseTime, Long durationMs,
                                          String orderId, String instId, String responseBody, Long apiKeyId) {
    CexApiCallEvent event = CexApiCallEvent.success(
            apiType,
            CexExchange.OKX,
            CexHttpMethod.POST,
            getApiPathByType(apiType),
            requestParams,
            callTime,
            responseTime,
            durationMs,
            orderId,
            instId,
            200,
            responseBody,
            apiKeyId
    );
    eventPublisher.publishEvent(event);
}
```

#### 3. 通用查询方法
```java
public <T> List<T> commonFetchData(String apiName, String path, Class<? extends OkxApiResponse<T>> packageClass,
                                   ApiKey apiKey, String queryParams) {
    try {
        String response = sendSignedRequest(apiKey, "GET", path
                + (StringUtils.hasText(queryParams) ? ("?" + queryParams) : ""), "");
        OkxApiResponse<T> apiResponse = JsonUtils.parseTo(response, packageClass);
        if (null == apiResponse || !"0".equals(apiResponse.getCode())) {
            log.warn("查询" + apiName + " / " + queryParams + "失败, response: " + response);
            return Collections.emptyList();
        }
        return CollectionUtils.isEmpty(apiResponse.getData()) ? Collections.emptyList() : apiResponse.getData();
    } catch (Exception e) {
        log.error("查询" + apiName + " / " + queryParams + "失败: " + e.getMessage());
    }
    return Collections.emptyList();
}
```

---

### B. API路径常量

```java
public static final String API_PATH_GET_ACCOUNT_BALANCE = "/api/v5/account/balance";
public static final String API_PATH_GET_ACCOUNT_POSITIONS = "/api/v5/account/positions";
public static final String API_PATH_GET_ORDERS_ALGO_PENDING = "/api/v5/trade/orders-algo-pending";
public static final String API_PATH_GET_FUNDING_RATE = "/api/v5/public/funding-rate";
public static final String API_PATH_GET_MARK_PRICE = "/api/v5/public/mark-price";
public static final String API_PATH_GET_MARKET_TICKERS = "/api/v5/market/tickers";
public static final String API_PATH_GET_MARKET_CANDLES = "/api/v5/market/candles";
public static final String API_PATH_GET_ORDERS_PENDING = "/api/v5/trade/orders-pending";
public static final String API_PATH_CANCEL_ALGOS = "/api/v5/trade/cancel-algos";
public static final String API_PATH_GET_INSTRUMENTS = "/api/v5/public/instruments";
public static final String API_PATH_SET_ALGO_ORDER = "/api/v5/trade/order-algo";
public static final String API_PATH_AMEND_ALGOS = "/api/v5/trade/amend-algos";
public static final String API_PATH_CLOSE_POSITION = "/api/v5/trade/close-position";
public static final String API_PATH_PLACE_ORDER = "/api/v5/trade/order";
public static final String API_PATH_CANCEL_ORDER = "/api/v5/trade/cancel-order";
public static final String API_PATH_GET_ORDERS_HISTORY = "/api/v5/trade/orders-history";
public static final String API_PATH_GET_ORDERS_HISTORY_ARCHIVE = "/api/v5/trade/orders-history-archive";
public static final String API_PATH_GET_POSITIONS_HISTORY = "/api/v5/account/positions-history";
```

---

### C. 相关枚举类型

#### CexApiType (API类型)
```java
public enum CexApiType {
    // 订单相关
    PLACE_ORDER,           // 下单
    CANCEL_ORDER,          // 撤单(单个)
    CANCEL,                // 撤单(批量)
    CLOSE_POSITION,        // 平仓
    AMEND_ORDER,           // 修改订单

    // 策略订单相关
    SET_ALGO_ORDER,        // 设置策略订单
    AMEND_ALGO_ORDER,      // 修改策略订单
    CANCEL_ALGO_ORDER,     // 取消策略订单

    // 查询相关
    GET_POSITION,          // 查询仓位
    GET_BALANCE,           // 查询余额
    GET_PENDING_ORDERS,    // 查询委托订单
    GET_ALGO_ORDERS,       // 查询算法订单
}
```

---

## 结论

本文档详细分析了OkxApiService的所有17个公开方法的完整调用链路,包括:

1. **17个方法的详细调用链路图** - 从调用起点到OKX API的完整流程
2. **每个方法的调用者清单** - 文件路径、行号、调用上下文
3. **数据流转分析** - 输入参数、输出数据、数据用途
4. **事件发布机制** - 7个方法发布CexApiCallEvent事件
5. **缓存使用情况** - 7个方法使用缓存,提升性能
6. **分层架构总结** - Controller层、Unified服务层、Service层的职责划分
7. **架构演进** - 新增UnifiedCexApiService适配器层实现跨交易所统一调用

**核心发现**:
- **架构变化**: 业务服务层通过`UnifiedCexApiService`适配器层间接调用`OkxApiService`，实现了跨交易所的统一调用
- **调用频率最高**: `getMarketCandles` (前端图表实时刷新)
- **定时任务调用**: `getAccountBalance`(每1分钟)、`getPositions`(每30秒)、`getMarketTickers`(每30分钟)
- **事件驱动失效**: 7个交易相关方法发布事件,触发缓存失效
- **缓存策略**: 账户、仓位、订单数据都有5分钟缓存,行情数据缓存时间较短

Page,这份文档提供了OkxApiService的完整调用链路分析,可以帮助你理解整个系统的API调用架构!

---

**文档结束**
