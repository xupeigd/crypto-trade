# 后端日志级别优化方案

## 现状分析

### 统计数据
- **总文件数**：51个Java文件使用了info级别日志
- **总info日志数**：348条
- **重点文件**：AsyncTradingTaskService.java（65条info日志）

### 问题分析
1. **info级别泛滥**：大量详细的调试信息使用info级别
2. **日志噪音大**：生产环境无法快速识别关键业务日志
3. **性能影响**：过多的info日志影响系统性能
4. **调试困难**：关键信息淹没在海量的info日志中

## 优化策略

### 日志级别规范

| 级别 | 用途 | 示例 |
|------|------|------|
| **error** | 错误和异常 | API调用失败、数据库连接失败、业务流程中断 |
| **warn** | 警告信息 | 配置缺失、重试操作、降级服务 |
| **info** | 关键业务流程 | 任务开始/完成、重要状态变更、交易执行 |
| **debug** | 详细调试信息 | 方法入参、返回值、中间处理步骤、性能指标 |
| **trace** | 最详细的跟踪信息 | 方法调用链、详细的数据处理过程 |

### 优化原则

1. **info级别**：只记录关键业务流程节点
   - 任务开始/完成
   - 重要状态变更
   - 交易执行
   - 用户操作

2. **debug级别**：记录详细的调试信息
   - 方法入参和返回值
   - 中间处理步骤
   - 性能指标（耗时）
   - 数据处理细节

3. **warn级别**：记录可恢复的异常情况
   - 配置缺失
   - 重试操作
   - 降级服务
   - 数据异常但不影响主流程

4. **error级别**：记录无法恢复的错误
   - API调用失败
   - 数据库连接失败
   - 业务流程中断
   - 关键组件异常

## 具体优化方案

### AsyncTradingTaskService.java（65条info日志）

#### 应该保持info（约15条）
```java
// 1. 任务开始/完成
log.info("开始执行异步AI交易任务 - sessionId: {}, apiKeyId: {}", sessionId, request.getApiKeyId());
log.info("异步AI交易任务完成 - sessionId: {}", sessionId);

// 2. 关键业务流程
log.info("创建 LlmCallRecord - id: {}, taskId: {}, apiKeyId: {}, chatSessionId: {}",
        callRecordId, taskId, request.getApiKeyId(), chatSessionId);
log.info("AI调用完成 - 耗时: {}ms", llmCallTimeMs);

// 3. 工具调用检测
log.info("【异步多轮】检测到工具调用,启动异步递归对话处理 - callRecordId: {}, chatSessionId: {}", callRecordId, chatSessionId);
log.info("【异步单轮】第一轮无工具调用,直接执行交易 - callRecordId: {}, actionCount: {}", callRecordId, actionCount);

// 4. 交易执行
log.info("【异步单轮】调用TradeActionProcessor执行交易 - actionCount: {}", actionCount.size());
log.info("【异步单轮】交易执行完成 - callRecordId: {}, resultCount: {}", callRecordId, resultCount);
```

#### 应该降级为debug（约50条）
```java
// 1. 调用类型识别（详细调试信息）
log.debug("识别为定时任务调用 - taskId: {}", taskId);
log.debug("识别为手动触发调用 - taskId: {}", taskId);
log.debug("识别为API直接调用 - taskId: {}", taskId);

// 2. 详细处理步骤
log.debug("使用指定snapshotId更新recordId成功 - snapshotId: {}, recordId: {}, apiKeyId: {}",
        snapshotId, callRecordId, apiKeyId);
log.debug("【异步第1轮】TradeAction保存完成，获得id - callRecordId: {}, actionCount: {}", callRecordId, actionCount);
log.debug("【异步第1轮】aiResponse已更新为含id的版本 - callRecordId: {}, 原始长度: {}, 新长度: {}",
        callRecordId, originalLength, processedJson.length());

// 3. 耗时信息
log.debug("后置处理完成 - 耗时: {}ms", postActionTimeMs);
log.debug("异步任务AI响应已保存 - callRecordId: {}, prompt耗时: {}ms, llm耗时: {}ms, post耗时: {}ms",
        callRecordId, promptTimeMs, llmCallTimeMs, postActionTimeMs);

// 4. 详细记录关联
log.debug("异步任务ChatMessage已创建 - chatSessionId: {}, callRecordId: {}, userMsgId: {}, assistantMsgId: {}",
        chatSessionId, callRecordId, userMessageId, assistantMessageId);
log.debug("异步任务LlmCallRecord已关联messageId - callRecordId: {}, userMsgId: {}, assistantMsgId: {}",
        callRecordId, userMessageId, assistantMessageId);

// 5. 多轮对话详细流程
log.debug("【异步递归】第{}轮对话开始 - sessionId: {}, parentId: {}", currentRound, sessionId, currentParentId);
log.debug("【异步递归】第{}轮工具执行完成 - 成功: {}/{}", currentRound, successCount, totalCount);
```

### 其他文件优化建议

#### UnifiedModelFactory.java（7条info日志）
- **保持info**：模型调用成功/失败（2条）
- **降级为debug**：详细配置信息、并发控制（5条）

#### RemoteModelCaller.java（3条info日志）
- **保持info**：调用成功/失败（1条）
- **降级为debug**：请求构建、响应解析（2条）

#### ToolResultFormatter.java（2条info日志）
- **全部降级为debug**：格式化过程的详细信息

## 优化效果

### 预期改进
1. **日志量减少约70%**：从348条info日志减少到约100条
2. **可读性提升**：生产环境日志更加清晰
3. **性能改善**：减少I/O开销
4. **调试便利**：开发环境可以启用debug级别查看详细信息

### 配置建议

#### 开发环境（application-dev.yml）
```yaml
logging:
  level:
    com.crypto.trade: DEBUG
    org.springframework: INFO
    org.hibernate: DEBUG
```

#### 生产环境（application-prod.yml）
```yaml
logging:
  level:
    com.crypto.trade: INFO
    org.springframework: WARN
    org.hibernate: WARN
```

#### 测试环境（application-test.yml）
```yaml
logging:
  level:
    com.crypto.trade: DEBUG
    org.springframework: INFO
    org.hibernate: INFO
```

## 执行计划

1. **第一阶段**：优化AsyncTradingTaskService.java（影响最大）
2. **第二阶段**：优化其他服务类文件
3. **第三阶段**：优化工具类和配置类
4. **第四阶段**：验证和测试

## 验证标准

1. **info日志数量**：减少到100条以内
2. **关键流程覆盖**：所有关键业务流程都有info日志
3. **debug日志完整性**：所有详细信息都有debug日志
4. **性能指标**：日志I/O时间减少30%以上