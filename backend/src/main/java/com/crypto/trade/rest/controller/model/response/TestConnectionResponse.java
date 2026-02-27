package com.crypto.trade.rest.controller.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TestConnectionResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestConnectionResponse {

    /**
     * 测试是否成功
     */
    Boolean success;

    /**
     * 测试结果消息
     */
    String message;

}