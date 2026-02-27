package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * StrategySuggestionProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategySuggestionProcessor
        extends AbstractPromptProcessor {

    @Override
    public String getName() {
        return "StrategySuggestionProcessor";
    }

    @Override
    public int getPriority() {
        return 50; // 较高优先级，在技术指标之后
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return getBooleanParameter("enableStrategySuggestion", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用策略建议
            boolean enableStrategySuggestion = getBooleanParameter("enableStrategySuggestion", true);
            if (!enableStrategySuggestion) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取策略类型（默认为动量策略）
            String strategyType = getStringParameter("strategyType", "momentum");

            // 生成通用策略建议
            String content = generateStrategySuggestion(strategyType);

            return SegmentModel.builder()
                    .title("策略建议")
                    .content(content)
                    .category(SegmentModel.Category.ANALYSIS)
                    .priority(SegmentModel.Priority.HIGH)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("strategyType", strategyType)
                    .addMetadata("dataStatus", "SUCCESS")
                    .build();
        });
    }

    /**
     * 生成策略建议
     */
    private String generateStrategySuggestion(String strategyType) {

//        suggestion.append("""
//                #Strategy Logic 你必须实时评估市场状态（Market Regime）并切换策略：\n
//                - 1.趋势状态 (Trend Mode)： 若 $ADX > 25$ 且价格运行在 $EMA(20)$ 之上/下。\n
//                   - 动量逻辑： 寻找价格突破布林带上/下轨的机会，配合 MACD 柱状图放大进行跟单。\n
//                - 2.震荡状态 (Range Mode)： 若 $ADX < 20$ 或布林带走平。\n
//                   - 回归逻辑： 寻找 $RSI > 70$ 或 $RSI < 30$ 的超买超卖信号，在布林带边界进行反向交易。\n
//                - 3.极端保护： 若 $Z-Score > 3$ 或 $< -3$，无论当前何种模式，强制执行反向平仓或减仓。\n """);

        String suggestion = """
                #### 多周期共振策略
                
                #### trategy Framework \n
                你必须严格遵循以下两个周期的逻辑层级：
                
                 - 1. 4H 趋势过滤器 (The Anchor): * 使用 EMA(20) 和 ADX 判断大趋势。
                
                     - 若 4H 价格 > EMA(20) 且 ADX > 25，仅允许执行 Long 信号。
                
                     - 若 4H 价格 < EMA(20) 且 ADX > 25，仅允许执行 Short 信号。
                
                     - 若 4H ADX < 20，视为震荡，允许执行 5m 的均值回归双向交易。
                
                 - 2. 5m 执行引擎 (The Trigger):
                
                     - 动量模式 (与4H同向): 当 5m 价格放量突破布林带中轨或上轨，且 MACD 金叉时入场。
                
                     - 回归模式 (仅在4H震荡或极度偏离时): 当 5m 价格触及布林带边界、RSI 超买/超卖且 Z-Score > 2.5 时入场。
                
                
                #### Operational Rules
                
                 - 共振原则： 优先寻找 5m 信号方向与 4H 趋势方向一致的机会。
                
                 - 反向屏蔽： 除非 5m 出现极端的回归信号（Z-Score > 3），否则严禁逆 4H 大趋势做单。
                
                 - 止损逻辑： 止损位必须设在 1H 最近一个波动低点/高点(不得低于3%) ，或布林带另一侧轨道。
                
                 - 止盈逻辑：
                    - 止盈位置设置为1H涨跌的1.2倍(注意，盈利无法覆盖成本时,注意手续费，不得开仓)。
                
                    - 持仓时间大于0.5H，只要未结盈亏超过0.1U或超过成本0.8%，即止盈锁定利润。
                
                """;

        return suggestion;
    }

    /**
     * 获取策略显示名称
     */
    private String getStrategyDisplayName(String strategyType) {
        return switch (strategyType.toLowerCase()) {
            case "momentum" -> "动量策略";
            case "mean_reversion" -> "均值回归策略";
            case "trend_following" -> "趋势跟踪策略";
            case "breakout" -> "突破策略";
            default -> "动量策略";
        };
    }

    /**
     * 获取策略描述
     */
    private String getStrategyDescription(String strategyType) {
        return switch (strategyType.toLowerCase()) {
            case "momentum" ->
                    "动量策略：基于价格动量进行交易决策，在价格呈现强劲上涨或下跌趋势时跟进，在动量减弱时及时退出。";
            case "mean_reversion" ->
                    "均值回归策略：基于价格回归均值的特性进行交易，在价格偏离均值较大时反向操作，等待价格回归均值获利。";
            case "trend_following" -> "趋势跟踪策略：跟随市场趋势进行交易，在趋势确认后进场，在趋势反转信号出现时退出。";
            case "breakout" -> "突破策略：在价格突破关键阻力位或支撑位时进场，利用突破后的动能获利。";
            default -> "动量策略：基于价格动量进行交易决策，在价格呈现强劲上涨或下跌趋势时跟进，在动量减弱时及时退出。";
        };
    }

    /**
     * 获取策略执行原则
     */
    private String getStrategyExecutionPrinciples(String strategyType) {
        return switch (strategyType.toLowerCase()) {
            case "momentum" -> """
                    - **进场时机**：等待价格突破关键位置或出现强劲的单边走势，成交量配合放大
                    - **止损设置**：进场后立即设置止损，通常为进场价的2-3%或近期低点/高点
                    - **移动止损**：盈利后逐步上移止损位，保护已实现利润，通常采用20-30%的回撤比例
                    - **离场时机**：动量减弱时（如价格涨跌幅度减小、成交量萎缩）及时离场
                    - **仓位管理**：单次仓位不超过总资金的20%，根据市场强度调整仓位大小
                    """;
            case "mean_reversion" -> """
                    - **进场时机**：价格偏离均值（如MA20、MA60）超过3%时考虑反向建仓
                    - **止损设置**：当价格继续偏离，偏离度达到5-6%时严格止损
                    - **止盈设置**：当价格回归均值附近时止盈，或等待价格向均值另一侧偏离
                    - **时间周期**：均值回归通常在1-3个交易日内完成，避免长期持有
                    - **仓位管理**：均值回归策略风险较高，单次仓位控制在10-15%以内
                    """;
            case "trend_following" -> """
                    - **趋势确认**：使用多条均线（如MA20、MA60、MA120）配合，金叉/死叉确认趋势
                    - **进场时机**：趋势确认后等待回调至支撑位/阻力位进场，避免追涨杀跌
                    - **止损设置**：止损设置在趋势反转的关键位置，通常为前低/前高
                    - **移动止损**：趋势延续过程中，使用动态止损（如ATR指标或均线）锁定利润
                    - **离场时机**：趋势反转信号明确时（如均线死叉/金叉、跌破/突破关键位置）离场
                    - **仓位管理**：趋势形成初期轻仓试探，确认后逐步加仓，但总仓位不超过30%
                    """;
            case "breakout" -> """
                    - **关键位置识别**：识别重要的支撑位、阻力位、前期高点/低点
                    - **突破确认**：突破后需要成交量配合，价格应稳定在突破位置上方/下方
                    - **假突破防范**：设置突破失败止损，通常为突破位置的反向2-3%
                    - **进场时机**：突破确认后立即进场，或等待价格回踩突破位置确认后再进场
                    - **止盈设置**：第一目标位为突破高度的1:1或1:1.5，后续根据市场表现调整
                    - **仓位管理**：突破策略成功率高但波动大，单次仓位控制在15-20%
                    """;
            default -> """
                    - **进场时机**：等待价格突破关键位置或出现强劲的单边走势
                    - **止损设置**：进场后立即设置止损，通常为进场价的2-3%
                    - **移动止损**：盈利后逐步上移止损位，保护已实现利润
                    - **离场时机**：动量减弱时及时离场
                    - **仓位管理**：单次仓位不超过总资金的20%
                    """;
        };
    }

    /**
     * 获取策略风险提示
     */
    private String getStrategyRiskWarnings(String strategyType) {
        return switch (strategyType.toLowerCase()) {
            case "momentum" -> """
                    - **趋势反转风险**：市场突然反转可能导致快速亏损，需严格执行止损
                    - **追高风险**：在行情末端进场容易被套，需警惕成交量萎缩等动量减弱信号
                    - **假突破风险**：价格短暂突破后可能迅速回落，需确认突破有效性
                    - **滑点风险**：在快速行情中可能面临较大滑点，影响实际成交价格
                    """;
            case "mean_reversion" -> """
                    - **趋势延续风险**：在强趋势中，价格可能持续偏离均值而非回归，需严格止损
                    - **均值漂移风险**：市场结构变化可能导致均值发生漂移，历史参考失效
                    - **时间成本风险**：均值回归可能需要较长时间，占用资金的机会成本较高
                    - **反弹失败风险**：价格可能短暂反弹后继续偏离，避免过早加仓
                    """;
            case "trend_following" -> """
                    - **震荡市场亏损**：在震荡市场中频繁止损，造成连续亏损
                    - **趋势识别滞后**：趋势信号往往滞后于市场实际转折，可能错过最佳进场/离场点
                    - **回撤控制困难**：趋势跟踪策略在趋势反转时可能出现较大回撤
                    - **心理压力**：长期持仓需要较强的心理承受能力，避免中途离场
                    """;
            case "breakout" -> """
                    - **假突破风险**：市场常出现假突破，价格短暂突破后迅速回落
                    - **流动性风险**：突破时可能出现流动性不足，导致成交困难或滑点较大
                    - **突破失败止损**：突破失败后需立即止损，避免扩大亏损
                    - **时机把握困难**：过早进场可能遇到假突破，过晚进场可能错过最佳价格
                    """;
            default -> """
                    - **市场风险**：市场波动可能导致策略失效，需密切关注市场变化
                    - **执行风险**：实际交易中可能面临滑点、延迟等问题，影响策略执行效果
                    - **资金风险**：过度集中仓位可能导致单一风险暴露，需合理分散
                    - **情绪风险**：避免情绪化交易，严格执行策略规则
                    """;
        };
    }
}
