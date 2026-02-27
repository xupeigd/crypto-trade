package com.crypto.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HistoryOrdersPageRequest
 * 请求对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryOrdersPageRequest {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 查询天数
     * 默认查询最近7天的订单
     */
    private Integer days;

    /**
     * 页码
     * 从0开始
     */
    private Integer page;

    /**
     * 每页大小
     * 默认20条
     */
    private Integer size;
}
