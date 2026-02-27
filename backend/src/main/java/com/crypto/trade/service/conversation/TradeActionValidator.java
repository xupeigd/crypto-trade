package com.crypto.trade.service.conversation;

import com.crypto.trade.enums.OpenCloseType;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * TradeActionValidator
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class TradeActionValidator {

    // 误差容忍度: 5%
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");
    // 缓冲空间比例: 110% (含10%缓冲)
    private static final BigDecimal BUFFER_RATIO = new BigDecimal("1.10");
    // 最小杠杆倍数
    private static final int MIN_LEVERAGE = 1;
    // 最大杠杆倍数 (OKX限制)
    private static final int MAX_LEVERAGE = 125;
    // 最小置信度
    private static final int MIN_CONFIDENCE = 1;
    // 最大置信度
    private static final int MAX_CONFIDENCE = 100;

    @Autowired
    UnifiedBalanceService unifiedBalanceService;

    /**
     * 验证交易动作
     *
     * @param action   待验证的动作
     * @param apiKeyId API密钥ID
     * @return 验证结果
     */
    public ValidationResult validate(ActionParser.ParsedAction action, Long apiKeyId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 基础字段验证
        validateBasicFields(action, errors, warnings);

        // 2. 金额计算验证
        validateAmountCalculation(action, errors, warnings);

        // 3. 账户余额验证
        validateAvailableBalance(action, apiKeyId, errors, warnings);

        // 4. 业务逻辑验证
        validateBusinessLogic(action, errors, warnings);

        // 构建验证结果
        boolean isValid = errors.isEmpty();
        ValidationResult result = new ValidationResult(isValid, errors, warnings);

        // 记录验证结果
        if (isValid) {
            if (!warnings.isEmpty()) {
                log.warn("【业务逻辑验证-通过但有警告】instId: {}, action: {}, warnings: {}",
                        action.getInstId(), action.getAction(), warnings);
            } else {
                log.info("【业务逻辑验证-通过】instId: {}, action: {}",
                        action.getInstId(), action.getAction());
            }
        } else {
            log.error("【业务逻辑验证-失败】instId: {}, action: {}, errors: {}",
                    action.getInstId(), action.getAction(), errors);
        }

        return result;
    }

    /**
     * 验证基础字段
     * <p>
     * 检查字段是否为空、是否在合理范围内
     * </p>
     */
    private void validateBasicFields(ActionParser.ParsedAction action,
                                     List<String> errors,
                                     List<String> warnings) {
        String instId = action.getInstId();
        if (instId == null || instId.trim().isEmpty()) {
            errors.add("合约代码(instId)不能为空");
            return; // 合约代码为空时无法继续验证
        }

        // 置信度验证
        Integer confidence = action.getConfidence();
        if (confidence == null) {
            warnings.add("置信度(confidence)未设置,使用默认值");
        } else {
            if (confidence < MIN_CONFIDENCE || confidence > MAX_CONFIDENCE) {
                errors.add(String.format("置信度(%d)超出合理范围[%d, %d]",
                        confidence, MIN_CONFIDENCE, MAX_CONFIDENCE));
            }
        }

        // 杠杆倍数验证
        Integer lever = action.getLever();
        if (lever == null) {
            warnings.add("杠杆倍数(lever)未设置,可能使用默认值");
        } else {
            if (lever < MIN_LEVERAGE || lever > MAX_LEVERAGE) {
                errors.add(String.format("杠杆倍数(%d)超出合理范围[%d, %d]",
                        lever, MIN_LEVERAGE, MAX_LEVERAGE));
            }
        }

        // 数量验证
        BigDecimal quantity = action.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("交易数量(quantity)必须大于0");
        }

        // 价格验证(仅限价单)
        BigDecimal price = action.getPrice();
        if (price != null && price.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("限价单价格(price)必须大于0");
        }
    }

    /**
     * 验证金额计算一致性
     * <p>
     * 检查amount是否与(price × quantity) / lever的计算结果一致
     * </p>
     * <p>
     * 注意:
     * <ul>
     *   <li>限价单: amount = (price × quantity) / lever</li>
     *   <li>市价单: amount应该直接指定,不进行计算验证</li>
     * </ul>
     * </p>
     */
    private void validateAmountCalculation(ActionParser.ParsedAction action,
                                           List<String> errors,
                                           List<String> warnings) {
        BigDecimal amount = action.getAmount();
        BigDecimal quantity = action.getQuantity();
        BigDecimal price = action.getPrice();
        Integer lever = action.getLever();

        // 如果缺少关键字段,跳过金额验证
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 已在基础字段验证中报错
        }

        // 市价单验证
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            // 市价单: amount应该等于quantity(开仓时的成本金额)
            if (amount != null && amount.compareTo(quantity) != 0) {
                warnings.add(String.format(
                        "市价单建议amount与quantity保持一致, 当前amount=%s, quantity=%s",
                        amount, quantity));
            }
            return;
        }

        // 限价单验证: amount = (price × quantity) / lever
        if (lever == null || lever <= 0) {
            warnings.add("杠杆倍数未设置,无法验证金额计算");
            return;
        }

        // 计算期望金额
        BigDecimal expectedAmount = price.multiply(quantity)
                .divide(BigDecimal.valueOf(lever), 2, RoundingMode.HALF_UP);

        // 如果未设置amount,生成警告
        if (amount == null) {
            warnings.add(String.format(
                    "未设置amount,根据计算建议amount=%s (price=%s × quantity=%s / lever=%d)",
                    expectedAmount, price, quantity, lever));
            return;
        }

        // 验证amount与计算结果是否一致(允许5%误差)
        BigDecimal difference = expectedAmount.subtract(amount).abs();
        BigDecimal tolerance = expectedAmount.multiply(TOLERANCE);

        if (difference.compareTo(tolerance) > 0) {
            errors.add(String.format(
                    "金额计算不一致 - 期望amount≈%s (price=%s × quantity=%s / lever=%d), 实际amount=%s, 差异=%s (%.1f%%)",
                    expectedAmount, price, quantity, lever, amount, difference,
                    difference.divide(expectedAmount, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))));
        } else {
            log.debug("金额计算验证通过 - amount={}, 期望≈{}, 误差={}",
                    amount, expectedAmount, difference);
        }
    }

    /**
     * 验证账户可用金额
     * <p>
     * 验证规则:
     * <ul>
     *   <li>1. 只对开仓操作进行余额验证,平仓跳过</li>
     *   <li>2. 查询账户USDT可用余额</li>
     *   <li>3. 比较交易金额与可用余额(含10%缓冲)</li>
     *   <li>4. 可用余额不足时返回错误</li>
     * </ul>
     * </p>
     */
    private void validateAvailableBalance(ActionParser.ParsedAction action,
                                          Long apiKeyId,
                                          List<String> errors,
                                          List<String> warnings) {
        // 平仓操作跳过余额验证
        if (OpenCloseType.CLOSE.equals(action.getOpenClose())) {
            log.debug("平仓操作,跳过余额验证 - instId: {}", action.getInstId());
            return;
        }

        // 获取交易金额(使用quantity字段,开仓时为成本金额)
        BigDecimal tradeAmount = action.getQuantity();
        if (tradeAmount == null || tradeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 已在基础字段验证中报错
        }

        // 查询账户可用余额
        AccountDetailModel accountDetail = unifiedBalanceService.getAccountUsdtDetail(apiKeyId);
        if (accountDetail == null) {
            errors.add("无法获取账户余额信息,请稍后重试");
            return;
        }

        BigDecimal availableBalance = accountDetail.getAvailableBalance();
        if (availableBalance == null) {
            errors.add("账户可用余额数据异常");
            return;
        }

        // 计算所需金额(含10%缓冲)
        // 缓冲原因: 手续费、滑点、价格波动等
        BigDecimal requiredAmount = tradeAmount.multiply(BUFFER_RATIO);

        // 比较交易金额与可用余额
        if (requiredAmount.compareTo(availableBalance) > 0) {
            BigDecimal deficit = requiredAmount.subtract(availableBalance);
            errors.add(String.format(
                    "账户可用余额不足 - 需要: %s USDT (含10%%缓冲), 可用: %s USDT, 差额: %s USDT",
                    requiredAmount.setScale(2, RoundingMode.HALF_UP),
                    availableBalance.setScale(2, RoundingMode.HALF_UP),
                    deficit.setScale(2, RoundingMode.HALF_UP)
            ));
        } else {
            log.debug("余额验证通过 - 可用: {} USDT, 需要: {} USDT",
                    availableBalance.setScale(2, RoundingMode.HALF_UP),
                    requiredAmount.setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * 验证业务逻辑一致性
     * <p>
     * 检查开平仓参数、止盈止损逻辑是否符合交易规则
     * </p>
     */
    private void validateBusinessLogic(ActionParser.ParsedAction action,
                                       List<String> errors,
                                       List<String> warnings) {
        BigDecimal price = action.getPrice();
        BigDecimal takeProfit = action.getTakeProfit();
        BigDecimal stopLoss = action.getStopLoss();
        String posSide = action.getPosSide();
        ActionParser.ActionType actionType = action.getAction();

        // 只验证有价格的交易动作
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 验证止盈止损逻辑
        if (takeProfit != null || stopLoss != null) {
            if ("BUY".equalsIgnoreCase(actionType.name())) {
                // 买入操作: 止盈 > 开仓价 > 止损
                if (takeProfit != null && takeProfit.compareTo(price) <= 0) {
                    errors.add(String.format(
                            "买入操作的止盈价格(%s)必须高于开仓价格(%s)",
                            takeProfit, price));
                }
                if (stopLoss != null && stopLoss.compareTo(price) >= 0) {
                    errors.add(String.format(
                            "买入操作的止损价格(%s)必须低于开仓价格(%s)",
                            stopLoss, price));
                }
            } else if ("SELL".equalsIgnoreCase(actionType.name())) {
                // 卖出操作: 开仓价 > 止盈, 开仓价 > 止损
                if (takeProfit != null && takeProfit.compareTo(price) >= 0) {
                    warnings.add(String.format(
                            "卖出操作的止盈价格(%s)通常应该低于开仓价格(%s)",
                            takeProfit, price));
                }
                if (stopLoss != null && stopLoss.compareTo(price) >= 0) {
                    warnings.add(String.format(
                            "卖出操作的止损价格(%s)通常应该低于开仓价格(%s)",
                            stopLoss, price));
                }
            }
        }

        // 验证持仓方向
        if (posSide != null && !"long".equalsIgnoreCase(posSide) && !"short".equalsIgnoreCase(posSide)) {
            warnings.add(String.format("持仓方向(%s)不是标准值(long/short)", posSide));
        }
    }

    /**
     * 验证结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        /**
         * 是否通过验证
         */
        private boolean valid;

        /**
         * 错误列表(必须修复)
         */
        private List<String> errors;

        /**
         * 警告列表(建议修复)
         */
        private List<String> warnings;

        /**
         * 获取验证摘要
         */
        public String getSummary() {
            if (valid) {
                return warnings.isEmpty() ? "验证通过" :
                        String.format("验证通过但有%d个警告", warnings.size());
            } else {
                return String.format("验证失败: %d个错误, %d个警告",
                        errors.size(), warnings.size());
            }
        }
    }
}
