package com.crypto.trade.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CancelOrderReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelOrderReq {

    String instId;  // 合约品种

}
