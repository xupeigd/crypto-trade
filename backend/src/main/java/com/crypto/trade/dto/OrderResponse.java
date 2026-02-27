package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OrderResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {

    /**
     * 订单ID（交易所返回）
     */
    String orderId;

    /**
     * 客户端订单ID
     */
    String clOrdId;

    /**
     * 订单状态
     */
    String sCode;

    /**
     * 订单状态消息
     */
    String sMsg;

    /**
     * 是否成功
     */
    Boolean success;

    /**
     * 响应数据
     */
    Object data;
}