package com.crypto.trade.service.trading;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * LimitOrderStrategy
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Component
public class LimitOrderStrategy implements OrderStrategy {

    @Override
    public String getOrderType() {
        return "limit";
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

        if (params.get("px") == null) {
            return ValidationResult.failure("限价单价格不能为空");
        }

        try {
            BigDecimal size = new BigDecimal(params.get("sz").toString());
            if (size.compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.failure("订单数量必须大于0");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.failure("订单数量格式错误");
        }

        try {
            BigDecimal price = new BigDecimal(params.get("px").toString());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.failure("订单价格必须大于0");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.failure("订单价格格式错误");
        }

        return ValidationResult.success();
    }

    @Override
    public Map<String, Object> buildOrderParams(Map<String, Object> baseParams) {
        Map<String, Object> params = new HashMap<>(baseParams);

        // 设置订单类型为限价单
        params.put("ordType", "limit");

        // 确保价格参数存在且格式正确
        if (params.get("px") != null) {
            try {
                BigDecimal price = new BigDecimal(params.get("px").toString());
                params.put("px", price.toPlainString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("价格格式错误: " + params.get("px"));
            }
        }

        return params;
    }

    @Override
    public String getDescription() {
        return "限价单 - 指定价格，仅在达到或优于指定价格时成交";
    }

    /**
     * 检查价格是否合理
     *
     * @param price       订单价格
     * @param marketPrice 当前市场价格
     * @param side        订单方向
     * @return 价格是否合理
     */
    public boolean isPriceReasonable(BigDecimal price, BigDecimal marketPrice, String side) {
        if (null == price || null == marketPrice || marketPrice.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal priceDiff = price.subtract(marketPrice).abs();
        BigDecimal priceRatio = priceDiff.divide(marketPrice, 4, RoundingMode.HALF_UP);

        // 价格偏离超过5%认为不合理
        return priceRatio.compareTo(new BigDecimal("0.05")) <= 0;
    }

    /**
     * 建议限价单价格
     *
     * @param marketPrice      市场价格
     * @param side             订单方向
     * @param priceImprovement 价格改进幅度（百分比）
     * @return 建议价格
     */
    public BigDecimal suggestPrice(BigDecimal marketPrice, String side, BigDecimal priceImprovement) {
        if (null == marketPrice || marketPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal improvement = null != priceImprovement ? priceImprovement : new BigDecimal("0.01"); // 默认1%改进
        BigDecimal adjustment = marketPrice.multiply(improvement.divide(new BigDecimal("100")));

        if ("buy".equalsIgnoreCase(side)) {
            // 买单：价格略低于市场价
            return marketPrice.subtract(adjustment);
        } else if ("sell".equalsIgnoreCase(side)) {
            // 卖单：价格略高于市场价
            return marketPrice.add(adjustment);
        }

        return marketPrice;
    }

    /**
     * 计算限价单的预期成交价格
     *
     * @param limitPrice  限价
     * @param marketPrice 当前市场价格
     * @param side        订单方向
     * @return 预期成交价格
     */
    public BigDecimal calculateExpectedPrice(BigDecimal limitPrice, BigDecimal marketPrice, String side) {
        if (null == limitPrice || null == marketPrice) {
            return null;
        }

        if ("buy".equalsIgnoreCase(side)) {
            // 买单：以较低的限价或市场价成交
            return limitPrice.compareTo(marketPrice) <= 0 ? limitPrice : marketPrice;
        } else if ("sell".equalsIgnoreCase(side)) {
            // 卖单：以较高的限价或市场价成交
            return limitPrice.compareTo(marketPrice) >= 0 ? limitPrice : marketPrice;
        }

        return limitPrice;
    }

    /**
     * 估算限价单的成交概率
     *
     * @param limitPrice       限价
     * @param marketPrice      市场价格
     * @param side             订单方向
     * @param marketVolatility 市场波动率
     * @return 成交概率（0-1之间）
     */
    public BigDecimal estimateFillProbability(BigDecimal limitPrice, BigDecimal marketPrice, String side, BigDecimal marketVolatility) {
        if (null == limitPrice || null == marketPrice) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceRatio = limitPrice.divide(marketPrice, 4, RoundingMode.HALF_UP);
        BigDecimal distanceFromMarket = priceRatio.subtract(BigDecimal.ONE).abs();

        // 距离市场价越远，成交概率越低
        // 波动率越高，成交概率越高
        BigDecimal baseProbability = BigDecimal.ONE.subtract(distanceFromMarket.multiply(new BigDecimal("2")));
        BigDecimal volatilityBonus = null != marketVolatility ? marketVolatility.multiply(new BigDecimal("0.5")) : BigDecimal.ZERO;

        BigDecimal probability = baseProbability.add(volatilityBonus);

        // 确保概率在0-1之间
        probability = probability.max(BigDecimal.ZERO).min(BigDecimal.ONE);

        return probability;
    }
}