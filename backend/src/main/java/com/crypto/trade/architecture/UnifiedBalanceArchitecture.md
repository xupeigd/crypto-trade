# 统一余额管理架构

## 概述

本架构通过建立统一余额服务（UnifiedBalanceService），消除了多个服务重复调用OKX API的问题，实现了高效、一致、可扩展的账户余额管理。

## 核心组件

### 1. UnifiedBalanceService (统一余额服务)

- **职责**: 单一的余额数据获取点
- **更新频率**: 每1分钟定时更新所有活跃API密钥的余额数据
- **功能**:
    - 并发获取所有活跃OKX API密钥的余额
    - 多层缓存管理（内存 + L2缓存）
    - 发布数据更新事件
    - 提供数据查询和刷新接口

### 2. BalanceUpdateEvent (数据更新事件)

- **职责**: 事件驱动的数据更新通知机制
- **功能**:
    - 余额数据更新时自动通知所有消费者
    - 松耦合架构，便于扩展新的数据消费者

### 3. BalanceDataConverter (数据转换工具)

- **职责**: 标准化的数据转换和验证
- **功能**:
    - AccountData到各实体的转换
    - 数据完整性验证
    - 统一的数据解析逻辑

### 4. 数据消费者

#### AccountEquityMonitorService (账户权益监控服务)

- **重构前**: 每30秒独立调用OKX API
- **重构后**: 监听BalanceUpdateEvent，自动更新缓存

#### OKXTradingService (交易服务)

- **重构前**: 在需要时调用OKX API获取余额
- **重构后**: 从UnifiedBalanceService获取缓存数据

#### OKXBalanceProcessor (余额处理器)

- **重构前**: 定时任务独立调用OKX API处理数据
- **重构后**: 监听BalanceUpdateEvent，自动存储到t_cex_balances表

## 数据流程

```
OKX API
    ↓ (每1分钟调用)
UnifiedBalanceService
    ↓ (发布事件)
BalanceUpdateEvent
    ↓ (通知消费者)
{
  AccountEquityMonitorService (更新缓存)
  OKXBalanceProcessor (存储到t_cex_balances)
}
```

## 性能优化

### API调用次数对比

- **重构前**: 每30秒 × 3个服务 = 每30秒调用3次API
- **重构后**: 每1分钟 × 1个服务 = 每1分钟调用1次API
- **减少率**: 83%

### 数据一致性

- **重构前**: 各服务数据时间差可达30秒
- **重构后**: 所有服务使用相同数据源，100%同步

## 缓存策略

### 多层缓存架构

1. **L1缓存**: 内存中的AccountEquityData（1分钟TTL）
2. **L2缓存**: AccountData（Caffeine，5分钟TTL）
3. **L3存储**: 数据库持久化

## 扩展性

### 添加新的数据消费者

只需实现@EventListener方法监听BalanceUpdateEvent：

```java
@EventListener
public void handleBalanceUpdateEvent(BalanceUpdateEvent event) {
    // 处理新的余额数据
}
```

### 添加新的交易所支持

1. 扩展UnifiedBalanceService支持新交易所
2. 添加对应的转换器
3. 保持事件驱动架构不变

## 配置说明

### 定时任务

- UnifiedBalanceService: 1分钟定时更新
- AccountEquityPersistenceService: 1分钟定时持久化（已合并到事件驱动）

### 线程池配置

- accountEquityTaskScheduler: 4线程，用于定时任务
- 并发处理: 使用CompletableFuture进行并发API调用

## 监控和日志

### 关键指标

- API调用次数和成功率
- 缓存命中率
- 数据更新延迟
- 事件处理成功率

### 日志级别

- DEBUG: 详细的数据转换和处理过程
- INFO: 成功的数据更新和存储操作
- WARN: 数据验证失败和处理异常
- ERROR: 严重错误和异常情况

## 向后兼容性

### 保持不变的接口

- CexBalanceController REST API接口
- 数据库表结构（t_cex_balances, t_account_equity_snapshot）
- 各服务的公共方法签名

### 配置兼容性

- 现有的application.yml配置保持不变
- 新增的配置项有合理默认值

## 故障处理

### 降级策略

- API调用失败时使用缓存数据
- 数据验证失败时跳过该条记录
- 事件处理异常时不影响其他消费者

### 恢复机制

- 自动重试失败的API调用
- 数据完整性检查和修复
- 异常情况下的手动刷新接口