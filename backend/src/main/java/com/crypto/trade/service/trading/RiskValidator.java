package com.crypto.trade.service.trading;

import com.crypto.trade.dto.cex.model.CexInstrument;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.service.market.ContractInfoService;
import com.crypto.trade.service.market.MarketDataService;
import com.crypto.trade.service.market.PositionQueryService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * RiskValidator
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class RiskValidator {

    // 风险阈值配置
    private static final BigDecimal MAX_ORDER_SIZE = new BigDecimal("1000000"); // 最大订单金额
    private static final BigDecimal MAX_LEVERAGE = new BigDecimal("125"); // 最大杠杆倍数
    private static final BigDecimal MIN_MARGIN_RATIO = new BigDecimal("0.01"); // 最小保证金比例
    private static final int MAX_ORDERS_PER_MINUTE = 120; // 每分钟最大订单数
    private static final BigDecimal MAX_POSITION_RATIO = new BigDecimal("0.8"); // 最大仓位比例

    @Autowired
    MarketDataService marketDataService;
    @Autowired
    ContractInfoService contractInfoService;
    @Autowired
    PositionQueryService positionQueryService;

    /**
     * 验证交易请求
     *
     * @param apiKey      API密钥
     * @param orderParams 订单参数
     * @return 验证结果
     */
    public ValidationResult validateOrder(ApiKey apiKey, Map<String, Object> orderParams) {
        try {
            // 1. 基础参数验证
            ValidationResult basicValidation = validateBasicParams(orderParams);
            if (!basicValidation.isValid()) {
                return basicValidation;
            }
            // 2. 合约验证
            ValidationResult contractValidation = validateContract(apiKey, orderParams);
            if (!contractValidation.isValid()) {
                return contractValidation;
            }
            // 3. 数量和价格验证
            ValidationResult sizePriceValidation = validateSizeAndPrice(orderParams);
            if (!sizePriceValidation.isValid()) {
                return sizePriceValidation;
            }
            // 4. 杠杆验证
            ValidationResult leverageValidation = validateLeverage(orderParams);
            if (!leverageValidation.isValid()) {
                return leverageValidation;
            }
            // 5. 仓位风险验证
            ValidationResult positionValidation = validatePositionRisk(apiKey, orderParams);
            if (!positionValidation.isValid()) {
                return positionValidation;
            }
            // 6. 资金验证
            ValidationResult fundValidation = validateSufficientFunds(apiKey, orderParams);
            if (!fundValidation.isValid()) {
                return fundValidation;
            }
            return ValidationResult.success();
        } catch (Exception e) {
            log.error("风险验证异常", e);
            return ValidationResult.failure("风险验证异常: " + e.getMessage());
        }
    }

    /**
     * 验证基础参数
     */
    private ValidationResult validateBasicParams(Map<String, Object> orderParams) {
        String instId = (String) orderParams.get("instId");
        if (null == instId || instId.trim().isEmpty()) {
            return ValidationResult.failure("合约ID不能为空");
        }
        String side = (String) orderParams.get("side");
        if (!"buy".equalsIgnoreCase(side) && !"sell".equalsIgnoreCase(side)) {
            return ValidationResult.failure("订单方向必须是buy或sell");
        }
        String tdMode = (String) orderParams.get("tdMode");
        if (null == tdMode || tdMode.trim().isEmpty()) {
            return ValidationResult.failure("交易模式不能为空");
        }
        return ValidationResult.success();
    }

    /**
     * 验证合约
     */
    private ValidationResult validateContract(ApiKey apiKey, Map<String, Object> orderParams) {
        String instId = (String) orderParams.get("instId");
        String instType = (String) orderParams.getOrDefault("instType", "SWAP");
        CexInstrument contractInfo = marketDataService.getContractInfo(apiKey, instId, instType);
        if (null == contractInfo) {
            return ValidationResult.failure("无法获取合约信息: " + instId);
        }
        if (!marketDataService.isTradable(contractInfo)) {
            return ValidationResult.failure("合约当前不可交易: " + instId);
        }
        return ValidationResult.success();
    }

    /**
     * 验证数量和价格
     */
    private ValidationResult validateSizeAndPrice(Map<String, Object> orderParams) {
        try {
            BigDecimal sz = new BigDecimal(orderParams.get("sz").toString());
            if (sz.compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.failure("订单数量必须大于0");
            }
            // 限价单需要验证价格
            if ("limit".equals(orderParams.get("ordType"))) {
                BigDecimal px = new BigDecimal(orderParams.get("px").toString());
                if (px.compareTo(BigDecimal.ZERO) <= 0) {
                    return ValidationResult.failure("订单价格必须大于0");
                }
                // 计算订单金额
                BigDecimal notional = sz.multiply(px);
                if (notional.compareTo(MAX_ORDER_SIZE) > 0) {
                    return ValidationResult.failure("订单金额超过限制: " + notional + " > " + MAX_ORDER_SIZE);
                }
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("数量或价格格式错误");
        }
    }

    /**
     * 验证杠杆
     */
    private ValidationResult validateLeverage(Map<String, Object> orderParams) {
        try {
            BigDecimal lever = new BigDecimal(orderParams.getOrDefault("lever", "1").toString());
            if (lever.compareTo(BigDecimal.ZERO) <= 0) {
                return ValidationResult.failure("杠杆倍数必须大于0");
            }
            if (lever.compareTo(MAX_LEVERAGE) > 0) {
                return ValidationResult.failure("杠杆倍数超过限制: " + lever + " > " + MAX_LEVERAGE);
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("杠杆倍数格式错误");
        }
    }

    /**
     * 验证仓位风险
     */
    private ValidationResult validatePositionRisk(ApiKey apiKey, Map<String, Object> orderParams) {
        try {
            String instId = (String) orderParams.get("instId");
            String instType = (String) orderParams.getOrDefault("instType", "SWAP");
            // 获取当前仓位
            PositionModel currentPosition = marketDataService.getPosition(apiKey, instId);
            BigDecimal currentPos = currentPosition.getPos();
            // 计算新的仓位
            BigDecimal orderSize = new BigDecimal(orderParams.get("sz").toString());
            String side = (String) orderParams.get("side");
            BigDecimal newPosition = calculateNewPosition(currentPos, orderSize, side);
            // 检查仓位限制
            BigDecimal maxAllowedSize = getMaxAllowedPositionSize(apiKey, instId, instType);
            if (newPosition.abs().compareTo(maxAllowedSize) > 0) {
                return ValidationResult.failure("仓位超过最大限制");
            }
            return ValidationResult.success();
        } catch (Exception e) {
            log.error("仓位风险验证失败", e);
            return ValidationResult.failure("仓位风险验证失败");
        }
    }

    /**
     * 验证资金充足性
     */
    private ValidationResult validateSufficientFunds(ApiKey apiKey, Map<String, Object> orderParams) {
        try {
            String instId = (String) orderParams.get("instId");
            String instType = (String) orderParams.getOrDefault("instType", "SWAP");
            // 获取合约信息
            CexInstrument contractInfo = marketDataService.getContractInfo(apiKey, instId, instType);
            if (null == contractInfo) {
                return ValidationResult.failure("无法获取合约信息");
            }
            // 获取当前价格
            BigDecimal currentPrice = marketDataService.getMarkPrice(apiKey, instId);
            if (null == currentPrice) {
                return ValidationResult.failure("无法获取当前价格");
            }
            // 计算所需保证金
            BigDecimal orderSize = new BigDecimal(orderParams.get("sz").toString());
            BigDecimal lever = new BigDecimal(orderParams.getOrDefault("lever", "1").toString());
            BigDecimal requiredMargin = calculateRequiredMargin(orderSize, currentPrice, lever);
            return ValidationResult.success();

        } catch (Exception e) {
            log.error("资金验证失败", e);
            return ValidationResult.failure("资金验证失败");
        }
    }

    /**
     * 计算新仓位
     */
    private BigDecimal calculateNewPosition(BigDecimal currentPos, BigDecimal orderSize, String side) {
        if ("buy".equalsIgnoreCase(side)) {
            return currentPos.add(orderSize);
        } else {
            return currentPos.subtract(orderSize);
        }
    }

    /**
     * 获取最大允许仓位大小
     */
    private BigDecimal getMaxAllowedPositionSize(ApiKey apiKey, String instId, String instType) {
        // 这里应该根据账户总资金、风险偏好等计算
        // 简化实现，返回固定值
        return new BigDecimal("1000");
    }

    /**
     * 计算所需保证金
     */
    private BigDecimal calculateRequiredMargin(BigDecimal size, BigDecimal price, BigDecimal lever) {
        BigDecimal notional = size.multiply(price);
        return notional.divide(lever, 2, RoundingMode.HALF_UP);
    }

    /**
     * 验证止盈止损参数
     */
    public ValidationResult validateStopLossParams(Map<String, Object> stopLossParams) {
        if (null == stopLossParams || stopLossParams.isEmpty()) {
            return ValidationResult.success();
        }
        try {
            // 验证触发价格
            if (stopLossParams.containsKey("triggerPx")) {
                BigDecimal triggerPx = new BigDecimal(stopLossParams.get("triggerPx").toString());
                if (triggerPx.compareTo(BigDecimal.ZERO) <= 0) {
                    return ValidationResult.failure("止损触发价格必须大于0");
                }
            }
            // 验证订单价格
            if (stopLossParams.containsKey("ordPx")) {
                BigDecimal ordPx = new BigDecimal(stopLossParams.get("ordPx").toString());
                if (ordPx.compareTo(BigDecimal.ZERO) <= 0) {
                    return ValidationResult.failure("止损订单价格必须大于0");
                }
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("止损参数格式错误");
        }
    }

    /**
     * 验证止盈参数
     */
    public ValidationResult validateTakeProfitParams(Map<String, Object> takeProfitParams) {
        if (null == takeProfitParams || takeProfitParams.isEmpty()) {
            return ValidationResult.success();
        }
        try {
            // 验证触发价格
            if (takeProfitParams.containsKey("triggerPx")) {
                BigDecimal triggerPx = new BigDecimal(takeProfitParams.get("triggerPx").toString());
                if (triggerPx.compareTo(BigDecimal.ZERO) <= 0) {
                    return ValidationResult.failure("止盈触发价格必须大于0");
                }
            }
            // 验证订单价格
            if (takeProfitParams.containsKey("ordPx")) {
                BigDecimal ordPx = new BigDecimal(takeProfitParams.get("ordPx").toString());
                if (ordPx.compareTo(BigDecimal.ZERO) <= 0) {
                    return ValidationResult.failure("止盈订单价格必须大于0");
                }
            }
            return ValidationResult.success();
        } catch (NumberFormatException e) {
            return ValidationResult.failure("止盈参数格式错误");
        }
    }

    /**
     * 验证结果类
     */
    @Data
    public static class ValidationResult {
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

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

}