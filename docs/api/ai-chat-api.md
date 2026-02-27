# AI聊天助手后端接口文档

## 接口概述

本文档描述了加密货币交易AI聊天助手的后端API接口。所有接口基于RESTful风格，使用JSON格式进行数据交换。

- **基础URL**: `http://localhost:8080/chat`
- **内容类型**: `application/json`
- **字符编码**: `UTF-8`

## 通用响应格式

所有接口统一使用 `ApiResponse<T>` 包装返回结果：

```json
{
  "success": true,
  "data": { ... },
  "message": "操作成功",
  "code": 200
}
```

```json
{
  "success": false,
  "data": null,
  "message": "错误信息",
  "code": 500
}
```

## 数据模型

### TChatSession（聊天会话）

```json
{
  "sessionId": 1,
  "sessionName": "新对话 14:30:25",
  "userId": "default",
  "status": "active",
  "modelName": "deepseek-r1:14b",
  "createdTime": 1705319425000,
  "updatedTime": 1705320542000
}
```

**字段说明**：
- `sessionId`: 会话ID，数据库自动生成的主键
- `sessionName`: 会话名称，用户可自定义
- `userId`: 用户ID，默认为"default"
- `status`: 会话状态，"active"（活跃）或"inactive"（未激活）
- `modelName`: 使用的AI模型名称
- `createdTime`: 创建时间，毫秒时间戳
- `updatedTime`: 更新时间，毫秒时间戳

### TChatMessage（聊天消息）

```json
{
  "messageId": 123,
  "sessionId": 1,
  "userId": "default",
  "role": "user",
  "content": "今天比特币的价格趋势如何？",
  "tokensUsed": 150,
  "processingTimeMs": 2500,
  "createdTime": "2024-01-15 14:32:10"
}
```

**字段说明**：
- `messageId`: 消息ID，数据库自动生成的主键
- `sessionId`: 关联的会话ID
- `userId`: 用户ID
- `role`: 消息角色，"user"（用户）、"assistant"（AI助手）或"system"（系统）
- `content`: 消息内容
- `tokensUsed`: 使用的token数量（可选）
- `processingTimeMs`: AI处理耗时，毫秒（可选）
- `createdTime`: 创建时间，格式：yyyy-MM-dd HH:mm:ss

### SendMessageRequest（发送消息请求）

```json
{
  "sessionId": 1,
  "message": "今天比特币的价格趋势如何？",
  "userId": "default"
}
```

**字段说明**：
- `sessionId`: 会话ID，可为空，空时自动创建新会话
- `message`: 消息内容，必填
- `userId`: 用户ID，可选，默认为"default"

### SendMessageResponseModel（发送消息响应）

```json
{
  "success": true,
  "message": "消息发送成功",
  "sessionId": 1,
  "messageId": 124,
  "assistantReply": "根据当前市场分析...",
  "tokensUsed": 200,
  "processingTimeMs": 2800
}
```

**字段说明**：
- `success`: 操作是否成功
- `message`: 响应消息
- `sessionId`: 会话ID
- `messageId`: 消息ID
- `assistantReply`: 助手回复内容
- `tokensUsed`: 使用的token数量
- `processingTimeMs`: 处理时间（毫秒）

## API接口详情

### 1. 获取用户会话列表

获取指定用户的所有活跃聊天会话。

**接口信息**：
- **方法**: `GET`
- **路径**: `/chat/sessions`
- **描述**: 获取用户的所有活跃会话，按更新时间倒序排列

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| userId | String | 否 | "default" | 用户ID |

**请求示例**：
```bash
GET /chat/sessions?userId=default
GET /chat/sessions
```

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "sessionId": 3,
      "sessionName": "技术分析讨论",
      "userId": "default",
      "status": "active",
      "modelName": "deepseek-r1:14b",
      "createdTime": 1705317615000,
      "updatedTime": 1705320330000
    },
    {
      "sessionId": 1,
      "sessionName": "新对话 14:30:25",
      "userId": "default",
      "status": "active",
      "modelName": "deepseek-r1:14b",
      "createdTime": 1705319425000,
      "updatedTime": 1705320542000
    }
  ],
  "message": "操作成功",
  "code": 200
}
```

**状态码**：
- `200 OK`: 成功获取会话列表
- `500 Internal Server Error`: 服务器内部错误

---

### 2. 创建新会话

为指定用户创建一个新的聊天会话。

**接口信息**：
- **方法**: `POST`
- **路径**: `/chat/sessions`
- **描述**: 创建新的聊天会话

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| userId | String | 否 | "default" | 用户ID |
| sessionName | String | 否 | 自动生成 | 会话名称，格式："新对话 HH:mm:ss" |

**请求示例**：
```bash
POST /chat/sessions?userId=default&sessionName=市场分析讨论
POST /chat/sessions?userId=default
POST /chat/sessions
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "sessionId": 4,
    "sessionName": "市场分析讨论",
    "userId": "default",
    "status": "active",
    "modelName": "deepseek-r1:14b",
    "createdTime": 1705320605000,
    "updatedTime": 1705320605000
  },
  "message": "操作成功",
  "code": 200
}
```

**状态码**：
- `200 OK`: 成功创建会话
- `400 Bad Request`: 请求参数错误
- `500 Internal Server Error`: 服务器内部错误

---

### 3. 获取最新会话

获取指定用户的最新活跃会话。

**接口信息**：
- **方法**: `GET`
- **路径**: `/chat/sessions/latest`
- **描述**: 获取用户最新的活跃会话

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| userId | String | 否 | "default" | 用户ID |

**请求示例**：
```bash
GET /chat/sessions/latest?userId=default
GET /chat/sessions/latest
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "sessionId": 3,
    "sessionName": "技术分析讨论",
    "userId": "default",
    "status": "active",
    "modelName": "deepseek-r1:14b",
    "createdTime": 1705317615000,
    "updatedTime": 1705320330000
  },
  "message": "操作成功",
  "code": 200
}
```

**错误响应**：
```json
{
  "success": false,
  "data": null,
  "message": "未找到会话",
  "code": 500
}
```

**状态码**：
- `200 OK`: 成功获取最新会话
- `500 Internal Server Error`: 用户没有活跃会话或服务器错误

---

### 4. 获取会话消息

获取指定会话的所有消息记录。

**接口信息**：
- **方法**: `GET`
- **路径**: `/chat/sessions/{sessionId}/messages`
- **描述**: 获取指定会话的所有消息，按时间正序排列

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | Long | 是 | 会话ID |

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| userId | String | 否 | "default" | 用户ID |

**请求示例**：
```bash
GET /chat/sessions/3/messages
GET /chat/sessions/3/messages?userId=default
```

**响应示例**：
```json
{
  "success": true,
  "data": [
    {
      "messageId": 45,
      "sessionId": 3,
      "userId": "default",
      "role": "user",
      "content": "请分析一下以太坊的技术指标",
      "tokensUsed": null,
      "processingTimeMs": null,
      "createdTime": "2024-01-15 16:20:15"
    },
    {
      "messageId": 46,
      "sessionId": 3,
      "userId": "default",
      "role": "assistant",
      "content": "根据当前技术指标分析，以太坊...",
      "tokensUsed": 180,
      "processingTimeMs": 3200,
      "createdTime": "2024-01-15 16:20:18"
    }
  ],
  "message": "操作成功",
  "code": 200
}
```

**状态码**：
- `200 OK`: 成功获取消息列表
- `400 Bad Request`: 会话ID无效
- `500 Internal Server Error`: 服务器内部错误

---

### 5. 发送消息

向指定会话发送消息，并获得AI助手的回复。

**接口信息**：
- **方法**: `POST`
- **路径**: `/chat/send`
- **描述**: 发送消息到指定会话，自动生成AI回复

**请求体** (JSON)：
```json
{
  "sessionId": 3,
  "message": "今天的比特币趋势如何？",
  "userId": "default"
}
```

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| sessionId | Long | 否 | null | 会话ID，为空时自动创建新会话 |
| message | String | 是 | - | 用户消息内容 |
| userId | String | 否 | "default" | 用户ID |

**请求示例**：
```bash
POST /chat/send
Content-Type: application/json

{"sessionId": 3, "message": "今天的比特币趋势如何？", "userId": "default"}

POST /chat/send
Content-Type: application/json

{"message": "新手如何开始投资加密货币？"}
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "success": true,
    "message": "消息发送成功",
    "sessionId": 3,
    "messageId": 47,
    "assistantReply": "根据当前市场分析，比特币今日呈现...",
    "tokensUsed": 200,
    "processingTimeMs": 2800
  },
  "message": "操作成功",
  "code": 200
}
```

**错误响应**：
```json
{
  "success": false,
  "data": null,
  "message": "Session not found: 999",
  "code": 500
}
```

**状态码**：
- `200 OK`: 成功发送消息并获得回复
- `400 Bad Request`: 请求参数错误或会话不存在
- `500 Internal Server Error`: 服务器内部错误或AI服务异常

---

### 6. 更新会话名称

修改指定会话的名称。

**接口信息**：
- **方法**: `PUT`
- **路径**: `/chat/sessions/{sessionId}/name`
- **描述**: 更新会话名称

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | Long | 是 | 会话ID |

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| sessionName | String | 是 | - | 新的会话名称 |
| userId | String | 否 | "default" | 用户ID |

**请求示例**：
```bash
PUT /chat/sessions/3/name?sessionName=投资策略讨论&userId=default
PUT /chat/sessions/3/name?sessionName=风险管理
```

**响应示例**：
```json
{
  "success": true,
  "data": {
    "sessionId": 3,
    "sessionName": "投资策略讨论",
    "userId": "default",
    "status": "active",
    "modelName": "deepseek-r1:14b",
    "createdTime": 1705317615000,
    "updatedTime": 1705320600000
  },
  "message": "操作成功",
  "code": 200
}
```

**状态码**：
- `200 OK`: 成功更新会话名称
- `400 Bad Request`: 会话不存在或无权限修改
- `500 Internal Server Error`: 服务器内部错误

---

### 7. 删除会话

删除（归档）指定的聊天会话。

**接口信息**：
- **方法**: `DELETE`
- **路径**: `/chat/sessions/{sessionId}`
- **描述**: 删除指定会话（实际操作是状态改为inactive）

**路径参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| sessionId | Long | 是 | 会话ID |

**请求参数**：
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| userId | String | 否 | "default" | 用户ID |

**请求示例**：
```bash
DELETE /chat/sessions/3?userId=default
DELETE /chat/sessions/3
```

**响应示例**：
```json
{
  "success": true,
  "data": "会话删除成功",
  "message": "操作成功",
  "code": 200
}
```

**错误响应**：
```json
{
  "success": false,
  "data": null,
  "message": "Session not found or unauthorized: 999",
  "code": 500
}
```

**状态码**：
- `200 OK`: 成功删除会话
- `400 Bad Request`: 会话不存在或无权限删除
- `500 Internal Server Error`: 服务器内部错误

---

## 错误处理

所有接口在出现错误时都会返回相应的HTTP状态码和错误信息：

- **400 Bad Request**: 请求参数错误、会话不存在、无权限操作
- **404 Not Found**: 资源不存在
- **500 Internal Server Error**: 服务器内部错误、数据库异常、AI服务异常

## 使用示例

### 典型的聊天流程

1. **创建新会话**
```bash
POST /chat/sessions?sessionName=今日行情分析
```

2. **发送第一条消息**
```bash
POST /chat/send
Content-Type: application/json

{"sessionId": 1, "message": "今天比特币表现如何？"}
```

3. **继续对话**
```bash
POST /chat/send
Content-Type: application/json

{"sessionId": 1, "message": "有什么投资建议吗？"}
```

4. **获取历史消息**
```bash
GET /chat/sessions/1/messages
```

5. **更新会话名称**
```bash
PUT /chat/sessions/1/name?sessionName=比特币投资分析
```

## 注意事项

1. **默认用户**: 如果不指定userId，系统会使用"default"作为默认用户
2. **自动创建会话**: 发送消息时如果sessionId为空，系统会自动创建新会话
3. **会话状态**: 删除会话实际上是将其状态改为"inactive"，数据仍然保留
4. **响应格式**: 所有接口都使用ApiResponse<T>包装返回结果
5. **时间格式**: createdTime/updatedTime使用毫秒时间戳，createdTime在消息详情中使用"yyyy-MM-dd HH:mm:ss"格式
6. **错误重试**: 建议在AI服务异常时实现错误重试机制

## 版本信息

- **API版本**: v1.0
- **最后更新**: 2026-02-26
- **维护团队**: Crypto Trade Development Team
