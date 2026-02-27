package com.crypto.trade.service.conversation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TradingConfigProperties
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "ai.trading")
public class TradingConfigProperties {

    /**
     * 是否启用自动交易执行
     * 默认开启,与application.yml保持一致
     * 如需关闭交易,请修改application.yml中的AI_TRADING_AUTO_EXECUTION环境变量
     */
    private Boolean autoExecution = true;

    /**
     * 执行模式
     * - dry-run: 模拟执行，不实际下单
     * - live: 实盘执行，真实下单
     */
    private ExecutionMode executionMode = ExecutionMode.LIVE;

    /**
     * 最低置信度要求
     * 只有当决策的置信度 >= 此值时才执行交易
     * 与application.yml保持一致(默认60)
     */
    private Integer minConfidence = 60;

    /**
     * 单次最大交易金额比例（0-1之间）
     * 例如：0.1表示单次最多使用可用资金的10%
     */
    private Double maxAmountRatio = 0.1;

    /**
     * 最小交易金额（USDT）
     * 低于此金额不执行交易
     */
    private Double minAmount = 20.0;

    /**
     * 最大交易金额（USDT）
     * 超过此金额不执行交易
     */
    private Double maxAmount = 50.0;

    /**
     * AI交易账户最大可用金额限制（USDT）
     * 用于限制AI交易时的账户总权益，避免AI误以为有大量资金可用
     * 默认200 USDT
     */
    private Double maxAccountBalance = 200.0;

    /**
     * 默认杠杆倍数
     * 如果决策中未指定杠杆，使用此值
     */
    private Integer defaultLeverage = 3;

    /**
     * 是否启用风控检查
     */
    private Boolean enableRiskControl = true;

    /**
     * 验证配置是否有效
     *
     * @return 配置是否有效
     */
    public boolean isValid() {
        // 检查auto-execution配置,如果为false输出警告
        if (autoExecution != null && !autoExecution) {
            log.warn("【交易配置警告】auto-execution=false,自动交易已关闭！交易将不会执行。");
            log.warn("【交易配置警告】如需启用交易,请设置环境变量: AI_TRADING_AUTO_EXECUTION=true");
        }

        // 检查execution-mode配置
        if (executionMode != null && executionMode == ExecutionMode.DRY_RUN) {
            log.warn("【交易配置警告】execution-mode=dry-run,当前为模拟模式,不会真实下单！");
            log.warn("【交易配置警告】如需实盘交易,请设置环境变量: AI_TRADING_EXECUTION_MODE=live");
        }

        return autoExecution != null &&
                executionMode != null &&
                minConfidence != null && minConfidence >= 0 && minConfidence <= 100 &&
                maxAmountRatio != null && maxAmountRatio > 0 && maxAmountRatio <= 1 &&
                minAmount != null && minAmount > 0 &&
                maxAmount != null && maxAmount > minAmount &&
                defaultLeverage != null && defaultLeverage >= 1 &&
                enableRiskControl != null;
    }

    /**
     * 检查是否为实盘模式
     *
     * @return 是否为实盘模式
     */
    public boolean isLiveMode() {
        return ExecutionMode.LIVE.equals(executionMode);
    }

    /**
     * 检查是否为模拟模式
     *
     * @return 是否为模拟模式
     */
    public boolean isDryRunMode() {
        return ExecutionMode.DRY_RUN.equals(executionMode);
    }

    /**
     * 执行模式枚举
     */
    public enum ExecutionMode {
        /**
         * 模拟执行模式 - 验证参数但不真实下单
         */
        DRY_RUN,

        /**
         * 实盘执行模式 - 真实下单交易
         */
        LIVE
    }
}
