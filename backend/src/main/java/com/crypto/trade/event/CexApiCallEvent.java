package com.crypto.trade.event;

import com.crypto.trade.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CexApiCallEvent
 * 事件类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexApiCallEvent {

    /**
     * API类型（下单/平仓/取消订单/策略订单等）
     */
    private CexApiType apiType;

    /**
     * 交易所（OKX/币安/Bybit）
     */
    private CexExchange exchange;

    /**
     * HTTP方法（GET/POST/DELETE）
     */
    private CexHttpMethod httpMethod;

    /**
     * API路径
     */
    private String apiPath;

    /**
     * 请求参数JSON
     */
    private String requestParams;

    /**
     * 调用开始时间
     */
    private LocalDateTime callTime;

    /**
     * 响应时间
     */
    private LocalDateTime responseTime;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 合约代码
     */
    private String instId;

    /**
     * 订单类型
     */
    private CexOrderType orderType;

    /**
     * HTTP状态码
     */
    private Integer httpStatus;

    /**
     * 响应体JSON
     */
    private String responseBody;

    /**
     * 调用状态
     */
    private CexApiCallStatus status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * API Key ID
     */
    private Long apiKeyId;

    /**
     * 创建成功的API调用事件
     *
     * @param apiType       API类型
     * @param exchange      交易所
     * @param httpMethod    HTTP方法
     * @param apiPath       API路径
     * @param requestParams 请求参数
     * @param callTime      调用时间
     * @param responseTime  响应时间
     * @param durationMs    耗时（毫秒）
     * @param orderId       订单ID
     * @param instId        合约代码
     * @param httpStatus    HTTP状态码
     * @param responseBody  响应体
     * @param apiKeyId      API Key ID
     * @return 成功事件
     */
    public static CexApiCallEvent success(
            CexApiType apiType,
            CexExchange exchange,
            CexHttpMethod httpMethod,
            String apiPath,
            String requestParams,
            LocalDateTime callTime,
            LocalDateTime responseTime,
            Long durationMs,
            String orderId,
            String instId,
            Integer httpStatus,
            String responseBody,
            Long apiKeyId) {
        return CexApiCallEvent.builder()
                .apiType(apiType)
                .exchange(exchange)
                .httpMethod(httpMethod)
                .apiPath(apiPath)
                .requestParams(requestParams)
                .callTime(callTime)
                .responseTime(responseTime)
                .durationMs(durationMs)
                .orderId(orderId)
                .instId(instId)
                .httpStatus(httpStatus)
                .responseBody(responseBody)
                .status(CexApiCallStatus.SUCCESS)
                .apiKeyId(apiKeyId)
                .build();
    }

    /**
     * 创建失败的API调用事件
     *
     * @param apiType       API类型
     * @param exchange      交易所
     * @param httpMethod    HTTP方法
     * @param apiPath       API路径
     * @param requestParams 请求参数
     * @param callTime      调用时间
     * @param responseTime  响应时间
     * @param durationMs    耗时（毫秒）
     * @param instId        合约代码
     * @param errorMessage  错误信息
     * @param apiKeyId      API Key ID
     * @return 失败事件
     */
    public static CexApiCallEvent failed(
            CexApiType apiType,
            CexExchange exchange,
            CexHttpMethod httpMethod,
            String apiPath,
            String requestParams,
            LocalDateTime callTime,
            LocalDateTime responseTime,
            Long durationMs,
            String instId,
            String errorMessage,
            Long apiKeyId) {
        return CexApiCallEvent.builder()
                .apiType(apiType)
                .exchange(exchange)
                .httpMethod(httpMethod)
                .apiPath(apiPath)
                .requestParams(requestParams)
                .callTime(callTime)
                .responseTime(responseTime)
                .durationMs(durationMs)
                .instId(instId)
                .status(CexApiCallStatus.FAILED)
                .errorMessage(errorMessage)
                .apiKeyId(apiKeyId)
                .build();
    }
}
