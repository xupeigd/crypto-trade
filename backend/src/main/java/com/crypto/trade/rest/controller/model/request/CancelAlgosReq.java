package com.crypto.trade.rest.controller.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CancelAlgosReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelAlgosReq {

    /**
     * API Key ID
     */
    Integer apiKeyId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 算法订单ID
     */
    String algoId;
}