package com.crypto.trade.service.trading;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * MarketOrderStrategy
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Component
public class MarketOrderStrategy implements OrderStrategy {

    @Override
    public String getOrderType() {
        return "market";
    }

    @Override
    public ValidationResult validateOrderParams(Map<String, Object> params) {
        // 检查必需参数
        if (params.get("instId") == null || params.get("instId").toString().trim().isEmpty()) {
            return ValidationResult.failure("合约ID不能为空");
        }

        if (params.get("side") == null || params.get("side").toString().trim().isEmpty()) {
            return ValidationResult.failure("订单方向不能为空");
        }

        if (params.get("sz") == null) {
            return ValidationResult.failure("订单数量不能为空");
        }

        try {
            BigDecimal size = new BigDecimal(params.get("sz").toString());
            if (size.compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.failure("订单数量必须大于0");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.failure("订单数量格式错误");
        }

        // 市价单不需要价格，所以不需要验证价格参数
        return ValidationResult.success();
    }

    @Override
    public Map<String, Object> buildOrderParams(Map<String, Object> baseParams) {
        Map<String, Object> params = new HashMap<>(baseParams);

        // 设置订单类型为市价单
        params.put("ordType", "market");

        // 市价单不需要设置价格
        params.remove("px");

        return params;
    }

    @Override
    public String getDescription() {
        return "市价单 - 以当前市场最优价格立即成交";
    }

    /**
     * 检查是否适合使用市价单
     *
     * @param params 订单参数
     * @return 是否适合
     */
    public boolean isSuitableForMarketOrder(Map<String, Object> params) {
        // 市价单适合以下情况：
        // 1. 需要立即成交
        // 2. 对价格敏感度较低
        // 3. 市场流动性较好
        // 4. 小额订单

        try {
            BigDecimal size = new BigDecimal(params.get("sz").toString());
            // 大额订单建议使用限价单以避免滑点
            return size.compareTo(new BigDecimal("1000")) < 0; // 可调整阈值
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 估算市价单的滑点
     *
     * @param params      订单参数
     * @param marketDepth 市场深度数据
     * @return 估算的滑点百分比
     */
    public BigDecimal estimateSlippage(Map<String, Object> params, Map<String, Object> marketDepth) {
        // 这里可以基于市场深度数据估算滑点
        // 简化实现，实际应该根据订单簿深度计算
        try {
            BigDecimal size = new BigDecimal(params.get("sz").toString());
            BigDecimal baseSlippage = new BigDecimal("0.01"); // 基础滑点0.01%

            // 根据订单大小调整滑点
            BigDecimal sizeMultiplier = size.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            return baseSlippage.multiply(sizeMultiplier);
        } catch (Exception e) {
            return new BigDecimal("0.05"); // 默认0.05%滑点
        }
    }
}