package com.crypto.trade.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OKXOrderResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OKXOrderResponse {

    /**
     * 响应代码
     * "0" 表示成功，其他表示失败
     */
    @JsonProperty("code")
    private String code;

    /**
     * 订单数据数组
     */
    @JsonProperty("data")
    private List<OKXOrderData> data;

    /**
     * 请求开始时间戳
     */
    @JsonProperty("inTime")
    private String inTime;

    /**
     * 响应消息
     */
    @JsonProperty("msg")
    private String msg;

    /**
     * 响应结束时间戳
     */
    @JsonProperty("outTime")
    private String outTime;

    /**
     * 判断是否为成功响应
     */
    public boolean isSuccess() {
        return "0".equals(code);
    }

    /**
     * 获取第一个订单数据（如果存在）
     */
    public OKXOrderData getFirstOrderData() {
        if (null != data && !data.isEmpty()) {
            return data.get(0);
        }
        return null;
    }
}