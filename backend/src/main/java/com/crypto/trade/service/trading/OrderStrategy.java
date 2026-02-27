package com.crypto.trade.service.trading;

import lombok.Getter;

import java.util.Map;

/**
 * OrderStrategy
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface OrderStrategy {

    /**
     * 获取订单类型
     *
     * @return 订单类型字符串
     */
    String getOrderType();

    /**
     * 验证订单参数
     *
     * @param params 订单参数
     * @return 验证结果
     */
    ValidationResult validateOrderParams(Map<String, Object> params);

    /**
     * 构建订单参数
     *
     * @param baseParams 基础参数
     * @return 构建后的订单参数
     */
    Map<String, Object> buildOrderParams(Map<String, Object> baseParams);

    /**
     * 获取订单描述
     *
     * @return 订单描述
     */
    String getDescription();

    /**
     * 验证结果类
     */
    @Getter
    class ValidationResult {

        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

    }
}