package com.crypto.trade.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OKXOrderData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OKXOrderData {

    /**
     * 客户端订单ID
     */
    @JsonProperty("clOrdId")
    private String clOrdId;

    /**
     * OKX订单ID
     */
    @JsonProperty("ordId")
    private String ordId;

    /**
     * 订单状态代码
     * "0" 表示成功，其他表示失败
     */
    @JsonProperty("sCode")
    private String sCode;

    /**
     * 订单状态消息
     */
    @JsonProperty("sMsg")
    private String sMsg;

    /**
     * 标签
     */
    @JsonProperty("tag")
    private String tag;

    /**
     * 数据时间戳
     */
    @JsonProperty("ts")
    private String ts;

    /**
     * 判断是否为成功订单
     */
    public boolean isSuccess() {
        return "0".equals(sCode);
    }

    /**
     * 判断是否有错误消息
     */
    public boolean hasErrorMessage() {
        return null != sMsg && !sMsg.trim().isEmpty();
    }
}