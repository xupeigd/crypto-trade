package com.crypto.trade.entity;

import com.crypto.trade.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CexApiCallRecord
 * 实体类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Entity
@Table(name = "t_cex_api_call_records", indexes = {
        @Index(name = "idx_api_type", columnList = "api_type"),
        @Index(name = "idx_exchange", columnList = "exchange"),
        @Index(name = "idx_call_time", columnList = "call_time"),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_inst_id", columnList = "inst_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_create_time", columnList = "create_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexApiCallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * API类型（下单/平仓/取消订单）
     */
    @Column(name = "api_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CexApiType apiType;

    /**
     * 交易所（OKX/币安/Bybit）
     */
    @Column(name = "exchange", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CexExchange exchange;

    /**
     * HTTP方法（GET/POST/DELETE）
     */
    @Column(name = "http_method", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private CexHttpMethod httpMethod;

    /**
     * API路径
     */
    @Column(name = "api_path", nullable = false, length = 500)
    private String apiPath;

    /**
     * 请求参数JSON
     */
    @Column(name = "request_params", columnDefinition = "LONGTEXT")
    private String requestParams;

    /**
     * 调用开始时间
     */
    @Column(name = "call_time", nullable = false)
    private LocalDateTime callTime;

    /**
     * HTTP状态码
     */
    @Column(name = "http_status")
    private Integer httpStatus;

    /**
     * 响应体JSON
     */
    @Column(name = "response_body", columnDefinition = "LONGTEXT")
    private String responseBody;

    /**
     * 响应时间
     */
    @Column(name = "response_time")
    private LocalDateTime responseTime;

    /**
     * 耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * 订单ID
     */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /**
     * 合约代码
     */
    @Column(name = "inst_id", length = 100)
    private String instId;

    /**
     * 订单类型
     */
    @Column(name = "order_type", length = 50)
    @Enumerated(EnumType.STRING)
    private CexOrderType orderType;

    /**
     * 调用状态
     */
    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CexApiCallStatus status;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * API Key ID
     */
    @Column(name = "api_key_id")
    private Long apiKeyId;

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (null == createTime) {
            createTime = now;
        }
        if (null == updateTime) {
            updateTime = now;
        }
        // 如果状态未设置，默认为调用中
        if (null == status) {
            status = CexApiCallStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ========== 业务方法 ==========

    /**
     * 判断是否为成功的调用
     *
     * @return true表示成功
     */
    public boolean isSuccess() {
        return CexApiCallStatus.SUCCESS == status;
    }

    /**
     * 判断是否为调用中状态
     *
     * @return true表示调用中
     */
    public boolean isPending() {
        return CexApiCallStatus.PENDING == status;
    }

    /**
     * 判断是否为失败状态
     *
     * @return true表示失败
     */
    public boolean isFailed() {
        return CexApiCallStatus.FAILED == status;
    }

    /**
     * 判断是否为超时状态
     *
     * @return true表示超时
     */
    public boolean isTimeout() {
        return CexApiCallStatus.TIMEOUT == status;
    }

    /**
     * 判断是否为下单操作
     *
     * @return true表示下单
     */
    public boolean isPlaceOrder() {
        return CexApiType.PLACE_ORDER == apiType;
    }

    /**
     * 判断是否为平仓操作
     *
     * @return true表示平仓
     */
    public boolean isClosePosition() {
        return CexApiType.CLOSE_POSITION == apiType;
    }

    /**
     * 判断是否为取消订单操作
     *
     * @return true表示取消订单
     */
    public boolean isCancelOrder() {
        return CexApiType.CANCEL_ORDER == apiType || CexApiType.CANCEL == apiType;
    }

    /**
     * 判断是否为设置策略订单操作
     *
     * @return true表示设置策略订单
     */
    public boolean isSetAlgoOrder() {
        return CexApiType.SET_ALGO_ORDER == apiType;
    }

    /**
     * 判断是否为修改策略订单操作
     *
     * @return true表示修改策略订单
     */
    public boolean isAmendAlgoOrder() {
        return CexApiType.AMEND_ALGO_ORDER == apiType;
    }

    /**
     * 判断是否为取消策略订单操作
     *
     * @return true表示取消策略订单
     */
    public boolean isCancelAlgoOrder() {
        return CexApiType.CANCEL_ALGO_ORDER == apiType;
    }

    /**
     * 判断是否为策略订单相关操作
     * <p>
     * 包括设置、修改、取消策略订单
     * </p>
     *
     * @return true表示策略订单相关操作
     */
    public boolean isAlgoOrderOperation() {
        return null != apiType && apiType.isAlgoOrder();
    }

    /**
     * 标记为成功
     */
    public void markAsSuccess() {
        this.status = CexApiCallStatus.SUCCESS;
    }

    /**
     * 标记为失败
     *
     * @param errorMessage 错误信息
     */
    public void markAsFailed(String errorMessage) {
        this.status = CexApiCallStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 标记为超时
     */
    public void markAsTimeout() {
        this.status = CexApiCallStatus.TIMEOUT;
    }

    /**
     * 计算耗时
     * <p>
     * 基于callTime和responseTime自动计算耗时
     * </p>
     */
    public void calculateDuration() {
        if (null != callTime && null != responseTime) {
            this.durationMs = java.time.Duration.between(callTime, responseTime).toMillis();
        }
    }
}
