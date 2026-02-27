package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.TradeBalanceSnapshot;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.TradeBalanceSnapshotService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.conversation.TradingConfigProperties;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AccountInfoProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class AccountInfoProcessor
        extends AbstractPromptProcessor {

    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    TradingConfigProperties tradingConfigProperties;
    @Autowired
    TradeBalanceSnapshotService tradeBalanceSnapshotService;
    @Autowired
    ApiKeyService apiKeyService;

    @Override
    public String getName() {
        return "AccountInfoProcessor";
    }

    @Override
    public int getPriority() {
        return 20; // 中高优先级
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        // 总是尝试执行，让处理器自己决定是否需要获取数据
        return getBooleanParameter("enableAccountInfo", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用账户信息
            boolean enableAccountInfo = getBooleanParameter("enableAccountInfo", true);
            if (!enableAccountInfo) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取输出格式参数
            String outputFormat = getStringParameter("outputFormat", "TEXT"); // TEXT, TABLE, JSON

            // 先计算总盈亏：当前权益 - 最早的快照权益
            // 需要先获取原始账户数据来计算总盈亏
            AccountDetailModel originalAccountDetail = unifiedBalanceService.getAccountUsdtDetail(context.getApiKeyId());
            if (originalAccountDetail == null) {
                return SegmentModel.builder()
                        .title("账户信息")
                        .content("无法获取账户数据")
                        .category(SegmentModel.Category.DATA)
                        .priority(SegmentModel.Priority.HIGH)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("enableAccountInfo", enableAccountInfo)
                        .addMetadata("dataStatus", "UNAVAILABLE")
                        .build();
            }

            BigDecimal totalPnl = tradeBalanceSnapshotService.calculateTotalPnl(
                    originalAccountDetail.getTotalEquity(), context.getApiKeyId());

            // 获取账户数据（传入总盈亏用于快照创建）
            AccountDetailModel accountDetail = getAccountData(context, totalPnl);

            if (accountDetail == null) {
                return SegmentModel.builder()
                        .title("账户信息")
                        .content("无法获取账户数据")
                        .category(SegmentModel.Category.DATA)
                        .priority(SegmentModel.Priority.HIGH)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("enableAccountInfo", enableAccountInfo)
                        .addMetadata("dataStatus", "UNAVAILABLE")
                        .build();
            }

            // 重新计算可用余额：min((AI账户最大可用金额限制 - 总盈亏), 真实可用余额)
            recalculateAvailableBalanceWithTotalPnl(accountDetail, totalPnl);

            // 根据输出格式生成内容
            String content = switch (outputFormat.toUpperCase()) {
                case "TABLE" -> formatAsTable(accountDetail, totalPnl);
                case "JSON" -> formatAsJson(accountDetail, totalPnl);
                default -> formatAsText(accountDetail, totalPnl);
            };

            return SegmentModel.builder()
                    .title("账户信息")
                    .content(content)
                    .category(SegmentModel.Category.DATA)
                    .priority(SegmentModel.Priority.HIGH)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("enableAccountInfo", enableAccountInfo)
                    .addMetadata("outputFormat", outputFormat)
                    .addMetadata("dataStatus", "SUCCESS")
                    .build();
        });
    }

    /**
     * 获取账户数据
     */
    private AccountDetailModel getAccountData(PromptContext context, BigDecimal totalPnl) {
        try {
            log.debug("开始获取账户数据");

            // 首先尝试从context获取账户数据
            AccountDetailModel accountDetail = context.getAccountDetail();

            // 如果context中没有账户数据，主动获取
            if (accountDetail == null) {
                accountDetail = unifiedBalanceService.getAccountUsdtDetail(context.getApiKeyId());
            }

            if (accountDetail != null) {
                log.debug("账户数据获取成功");

                // 应用AI交易资金限制
                accountDetail = applyAiFundingLimits(accountDetail);

                // 记录账户余额快照
                recordTradeBalanceSnapshot(context, accountDetail, totalPnl);

                return accountDetail;
            } else {
                log.warn("账户数据为空");
                return null;
            }

        } catch (Exception e) {
            log.error("获取账户数据失败", e);
            return null;
        }
    }

    /**
     * 应用AI交易资金限制
     * 当账户实际余额超过AI配置的最大可动用资金时，调整总权益和相关指标
     *
     * @param accountDetail 原始账户数据
     * @return 应用AI资金限制后的账户数据
     */
    private AccountDetailModel applyAiFundingLimits(AccountDetailModel accountDetail) {
        // 获取AI交易账户最大可用金额限制
        Double maxAccountBalance = tradingConfigProperties.getMaxAccountBalance();
        if (maxAccountBalance == null || maxAccountBalance <= 0) {
            // 如果未配置AI账户最大可用金额限制，直接返回原始数据
            log.debug("AI交易账户最大可用金额限制未配置，使用原始账户数据");
            return accountDetail;
        }

        BigDecimal aiMaxBalance = new BigDecimal(maxAccountBalance);
        log.debug("AI交易账户最大可用金额限制: {} USDT", aiMaxBalance);

        // 获取原始账户数据
        BigDecimal originalTotalEquity = accountDetail.getTotalEquity();
        BigDecimal usedMargin = accountDetail.getUsedMargin();
        BigDecimal unrealizedPnl = accountDetail.getUnrealizedPnl();

        if (originalTotalEquity == null) {
            log.warn("账户总权益为空，无法应用AI资金限制");
            return accountDetail;
        }

        // 计算显示权益：不超过AI账户最大可用金额限制
        BigDecimal displayTotalEquity = originalTotalEquity.min(aiMaxBalance);

        // 如果原始总权益未超过AI限制，不需要调整
        if (displayTotalEquity.compareTo(originalTotalEquity) == 0) {
            log.debug("账户总权益未超过AI限制，无需调整");
            return accountDetail;
        }

        log.info("账户总权益超过AI限制，真实权益: {} USDT, 显示权益调整为: {} USDT",
                originalTotalEquity, displayTotalEquity);

        // 计算真实的可用余额（真实总权益 - 已用保证金）
        BigDecimal realAvailableBalance = originalTotalEquity.subtract(usedMargin);
        realAvailableBalance = realAvailableBalance.max(BigDecimal.ZERO);

        // 计算AI限制后的可用余额（maxAvailableAmount - 已用保证金）
        BigDecimal aiLimitedAvailableBalance = aiMaxBalance.subtract(usedMargin);
        aiLimitedAvailableBalance = aiLimitedAvailableBalance.max(BigDecimal.ZERO);

        // 取两者最小值作为最终的可用余额
        BigDecimal calculatedAvailableBalance = realAvailableBalance.min(aiLimitedAvailableBalance);

        // 基于min(真实总权益, AI最大余额)计算保证金使用率
        BigDecimal calculatedMarginRatio = BigDecimal.ZERO;
        BigDecimal marginRatioDenominator = originalTotalEquity.min(aiMaxBalance);
        if (marginRatioDenominator.compareTo(BigDecimal.ZERO) > 0) {
            calculatedMarginRatio = usedMargin
                    .divide(marginRatioDenominator, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // 创建新的账户数据对象（不修改原始对象）
        AccountDetailModel adjustedAccountDetail = new AccountDetailModel();
        adjustedAccountDetail.setTotalEquity(originalTotalEquity);  // 保持真实权益
        adjustedAccountDetail.setDisplayTotalEquity(displayTotalEquity);  // 设置显示权益
        adjustedAccountDetail.setAvailableBalance(calculatedAvailableBalance);  // 使用min()计算的结果
        adjustedAccountDetail.setUsedMargin(usedMargin);
        adjustedAccountDetail.setUnrealizedPnl(unrealizedPnl);
        adjustedAccountDetail.setMarginRatio(calculatedMarginRatio);
        adjustedAccountDetail.setLastUpdateTime(accountDetail.getLastUpdateTime());

        log.debug("应用AI资金限制后: 真实总权益={}, 显示总权益={}, 真实可用={}, AI限制可用={}, 最终可用={}, 保证金使用率={}%(分母={})",
                originalTotalEquity, displayTotalEquity, realAvailableBalance, aiLimitedAvailableBalance, calculatedAvailableBalance, calculatedMarginRatio, marginRatioDenominator);

        return adjustedAccountDetail;
    }

    /**
     * 根据总盈亏重新计算显示权益和可用余额
     * 显示权益计算逻辑：min(AI账户最大可用金额限制 + 总盈亏, 真实总权益)
     * 可用余额计算逻辑：min(AI账户最大可用金额限制 + 总盈亏 - 已用保证金, 真实可用余额)
     *
     * @param accountDetail 账户详情
     * @param totalPnl      总盈亏
     */
    private void recalculateAvailableBalanceWithTotalPnl(AccountDetailModel accountDetail, BigDecimal totalPnl) {
        // 获取AI交易账户最大可用金额限制
        Double maxAccountBalance = tradingConfigProperties.getMaxAccountBalance();
        if (maxAccountBalance == null || maxAccountBalance <= 0 || totalPnl == null) {
            // 如果未配置AI账户最大可用金额限制或总盈亏为空，不重新计算
            return;
        }

        BigDecimal aiMaxBalance = new BigDecimal(maxAccountBalance);
        BigDecimal originalTotalEquity = accountDetail.getTotalEquity();
        BigDecimal usedMargin = accountDetail.getUsedMargin() != null ? accountDetail.getUsedMargin() : BigDecimal.ZERO;

        // 计算AI限制调整后的显示权益：min(AI账户最大可用金额限制 + 总盈亏, 真实总权益)
        BigDecimal aiAdjustedTotalEquity = aiMaxBalance.add(totalPnl);
        aiAdjustedTotalEquity = aiAdjustedTotalEquity.max(BigDecimal.ZERO);
        BigDecimal displayTotalEquity = aiAdjustedTotalEquity.min(originalTotalEquity);

        log.debug("根据总盈亏重新计算显示权益: AI限制调整权益={}, 真实权益={}, 最终显示权益={}, 总盈亏={}",
                aiAdjustedTotalEquity, originalTotalEquity, displayTotalEquity, totalPnl);

        // 计算AI限制调整后的可用余额：min(AI账户最大可用金额限制 + 总盈亏 - 已用保证金, 真实可用余额)
        BigDecimal aiAdjustedAvailableBalance = aiMaxBalance.add(totalPnl).subtract(usedMargin);
        aiAdjustedAvailableBalance = aiAdjustedAvailableBalance.max(BigDecimal.ZERO);

        // 计算真实的可用余额（真实总权益 - 已用保证金）
        BigDecimal realAvailableBalance = originalTotalEquity.subtract(usedMargin);
        realAvailableBalance = realAvailableBalance.max(BigDecimal.ZERO);

        // 取两者最小值作为最终的可用余额
        BigDecimal finalAvailableBalance = aiAdjustedAvailableBalance.min(realAvailableBalance);

        log.debug("根据总盈亏重新计算可用余额: AI限制调整可用={}, 真实可用={}, 最终可用={}, 总盈亏={}",
                aiAdjustedAvailableBalance, realAvailableBalance, finalAvailableBalance, totalPnl);

        // 更新账户详情的显示权益和可用余额
        accountDetail.setDisplayTotalEquity(displayTotalEquity);
        accountDetail.setAvailableBalance(finalAvailableBalance);

        // 重新计算保证金使用率
        BigDecimal calculatedMarginRatio = BigDecimal.ZERO;
        if (displayTotalEquity.compareTo(BigDecimal.ZERO) > 0) {
            calculatedMarginRatio = usedMargin
                    .divide(displayTotalEquity, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        accountDetail.setMarginRatio(calculatedMarginRatio);
    }

    /**
     * 格式化为文本形式
     */
    private String formatAsText(AccountDetailModel accountDetail, BigDecimal totalPnl) {
        String sb = String.format("总盈亏: %s USDT\n", totalPnl != null ? formatBigDecimal(totalPnl) : "N/A") +
                String.format("总权益: %s USDT\n",
                        formatBigDecimal(accountDetail.getDisplayTotalEquity() != null ? accountDetail.getDisplayTotalEquity() : accountDetail.getTotalEquity())) +
                String.format("可用余额: %s USDT\n", formatBigDecimal(accountDetail.getAvailableBalance())) +
                String.format("已用保证金: %s USDT\n", formatBigDecimal(accountDetail.getUsedMargin())) +
                String.format("未实现盈亏: %s USDT\n", formatBigDecimal(accountDetail.getUnrealizedPnl())) +
                String.format("保证金使用率: %s", formatBigDecimal(accountDetail.getMarginRatio()) + "%");
        return sb;
    }

    /**
     * 格式化为表格形式
     */
    private String formatAsTable(AccountDetailModel accountDetail, BigDecimal totalPnl) {
        StringBuilder tableContent = new StringBuilder();
        tableContent.append("### 账户信息概览\n\n");
        tableContent.append("| 指标 | 数值 | 备注 |\n");
        tableContent.append("|------|------|------|\n");

        // 总盈亏根据正负值添加颜色标记
        String totalPnlColor = "";
        if (totalPnl != null) {
            if (totalPnl.compareTo(BigDecimal.ZERO) > 0) {
                totalPnlColor = " 🟢"; // 盈利
            } else if (totalPnl.compareTo(BigDecimal.ZERO) < 0) {
                totalPnlColor = " 🔴"; // 亏损
            }
        }
        tableContent.append(String.format("| 总盈亏 | %s USDT%s | 自开始交易以来的总盈亏 |\n",
                totalPnl != null ? formatBigDecimal(totalPnl) : "N/A", totalPnlColor));

        tableContent.append(String.format("| 总权益 | %s USDT | 账户总资产 |\n",
                formatBigDecimal(accountDetail.getDisplayTotalEquity() != null ? accountDetail.getDisplayTotalEquity() : accountDetail.getTotalEquity())));
        tableContent.append(String.format("| 可用余额 | %s USDT | 可用于开仓的资金 |\n",
                formatBigDecimal(accountDetail.getAvailableBalance())));
        tableContent.append(String.format("| 已用保证金 | %s USDT | 当前持仓占用的保证金 |\n",
                formatBigDecimal(accountDetail.getUsedMargin())));

        // 未实现盈亏根据正负值添加颜色标记
        String pnlColor = "";
        BigDecimal pnl = accountDetail.getUnrealizedPnl();
        if (pnl != null) {
            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                pnlColor = " 🟢"; // 盈利
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                pnlColor = " 🔴"; // 亏损
            }
        }
        tableContent.append(String.format("| 未实现盈亏 | %s USDT%s | 持仓未结算盈亏 |\n",
                formatBigDecimal(accountDetail.getUnrealizedPnl()), pnlColor));

        // 保证金使用率添加警告标记
        String marginWarning = "";
        BigDecimal marginRatio = accountDetail.getMarginRatio();
        if (marginRatio != null) {
            if (marginRatio.compareTo(new BigDecimal("80")) > 0) {
                marginWarning = " ⚠️高风险";
            } else if (marginRatio.compareTo(new BigDecimal("60")) > 0) {
                marginWarning = " ⚠️中风险";
            }
        }
        tableContent.append(String.format("| 保证金使用率 | %s%%%s | 资金使用程度 |\n",
                formatBigDecimal(accountDetail.getMarginRatio()), marginWarning));

        return tableContent.toString();
    }

    /**
     * 格式化为JSON形式
     */
    private String formatAsJson(AccountDetailModel accountDetail, BigDecimal totalPnl) {
        BigDecimal totalEquity = accountDetail.getDisplayTotalEquity() != null ? accountDetail.getDisplayTotalEquity() : accountDetail.getTotalEquity();
        String totalPnlStr = totalPnl != null ? String.format("  \"totalPnl\": %s,\n", totalPnl) : "  \"totalPnl\": null,\n";
        String jsonContent = "### 账户信息 (JSON格式)\n\n" +
                "```json\n" +
                "{\n" +
                totalPnlStr +
                String.format("  \"totalEquity\": %s,\n", totalEquity) +
                String.format("  \"availableBalance\": %s,\n", accountDetail.getAvailableBalance()) +
                String.format("  \"usedMargin\": %s,\n", accountDetail.getUsedMargin()) +
                String.format("  \"unrealizedPnl\": %s,\n", accountDetail.getUnrealizedPnl()) +
                String.format("  \"marginRatio\": %s\n", accountDetail.getMarginRatio()) +
                "}\n" +
                "```";
        return jsonContent;
    }

    /**
     * 记录账户余额快照
     * 在每次获取账户数据并应用AI资金限制后记录快照
     * 快照ID会保存到context中，供后续AI调用时关联recordId
     *
     * @param context       Prompt上下文
     * @param accountDetail 账户详情（已应用AI资金限制）
     * @param totalPnl      总盈亏（可选）
     */
    private void recordTradeBalanceSnapshot(PromptContext context, AccountDetailModel accountDetail, BigDecimal totalPnl) {
        try {
            // 修复P0级bug：统一由processAsyncConversationRecursive管理snapshot创建
            // INITIAL场景：由AccountInfoProcessor创建并记录snapshot
            // REPLAY/REPLY场景：由processAsyncConversationRecursive统一创建，这里跳过
            if ("INITIAL".equals(context.getSource()) && context.getCurrentSnapshotId() != null) {
                log.debug("INITIAL场景已记录快照，跳过重复记录，snapshotId: {}", context.getCurrentSnapshotId());
                return;
            }

            // 非INITIAL场景（REPLAY/REPLY）：跳过snapshot创建
            // 因为processAsyncConversationRecursive会在AI调用前统一创建snapshot
            // 如果这里也创建，会导致snapshot的ID保存在临时context对象中丢失
            if (!"INITIAL".equals(context.getSource())) {
                log.debug("非INITIAL场景(source={})，跳过snapshot创建，由递归处理逻辑统一管理", context.getSource());
                return;
            }

            // 获取AI交易资金限制
            Double maxAccountBalance = tradingConfigProperties.getMaxAccountBalance();
            BigDecimal maxAvailableAmount = (maxAccountBalance != null && maxAccountBalance > 0)
                    ? new BigDecimal(maxAccountBalance)
                    : null;

            ApiKey apiKey = apiKeyService.getDecryptedKey(context.getApiKeyId());

            // 获取交易所名称（从ApiKey或配置中获取）
            String cexName = apiKey.getCexName();

            // ✅ 使用统一方法创建快照（已包含AI限制逻辑）
            TradeBalanceSnapshot snapshot = TradeBalanceSnapshotService.createSnapshotWithAiLimits(context.getApiKeyId(),
                    cexName, accountDetail, context.getSource(), maxAvailableAmount, totalPnl);

            // 验证快照数据
            if (snapshot != null && tradeBalanceSnapshotService.validateSnapshot(snapshot)) {
                // 同步保存快照以获取snapshotId
                TradeBalanceSnapshot savedSnapshot = tradeBalanceSnapshotService.saveSnapshot(snapshot);

                // 保存快照ID到context，供后续使用
                context.setCurrentSnapshotId(savedSnapshot.getSnapshotId());

                log.debug("已记录账户余额快照并保存ID到context，snapshotId: {}, apiKeyId: {}, source: {}",
                        savedSnapshot.getSnapshotId(), context.getApiKeyId(), context.getSource());
            } else {
                log.warn("快照数据验证失败或为空，跳过记录，apiKeyId: {}", context.getApiKeyId());
            }

        } catch (Exception e) {
            // 快照记录失败不应该影响主流程，仅记录日志
            log.error("记录账户余额快照失败，apiKeyId: {}", context.getApiKeyId(), e);
        }
    }

}