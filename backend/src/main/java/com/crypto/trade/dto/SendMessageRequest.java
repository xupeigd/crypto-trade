package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SendMessageRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 用户ID
     */
    private String userId;
}