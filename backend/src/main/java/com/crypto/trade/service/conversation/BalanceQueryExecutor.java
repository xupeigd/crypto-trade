package com.crypto.trade.service.conversation;

import com.crypto.trade.dto.cex.model.CexAccountBalance;
import com.crypto.trade.dto.cex.model.CexAccountPortfolio;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BalanceQueryExecutor
 * 余额查询工具执行器
 *
 * @author page
 * @date 2026-03-12
 */
@Slf4j
@Component
public class BalanceQueryExecutor implements ToolExecutor {

    @Autowired
    private UnifiedBalanceService unifiedBalanceService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Override
    public ToolExecutionResult execute(ToolParameters parameters, Long apiKeyId) {
        long startTime = System.currentTimeMillis();

        try {
            BalanceInfoParameters balanceParams = (BalanceInfoParameters) parameters;

            // 获取API密钥
            if (apiKeyId == null) {
                apiKeyId = 1L;
                try {
                    var defaultApiKey = apiKeyService.getDefaultApiKey();
                    if (defaultApiKey != null) {
                        apiKeyId = defaultApiKey.getKeyId();
                    }
                } catch (Exception e) {
                    log.warn("获取默认API密钥失败，使用默认值: {}", e.getMessage());
                }
            }

            // 获取余额数据
            CexAccountPortfolio portfolio = unifiedBalanceService.getLatestBalanceData(apiKeyId);

            if (portfolio == null || portfolio.getBalances() == null || portfolio.getBalances().isEmpty()) {
                long processingTime = System.currentTimeMillis() - startTime;
                return ToolExecutionResult.success("账户余额：无数据", processingTime, getToolName());
            }

            // 构建Markdown表格返回
            StringBuilder sb = new StringBuilder();
            sb.append("### 账户余额\n\n");
            sb.append("| 币种 | 总余额 | 可用 | 冻结 | 权益(USD) | 已用保证金 |\n");
            sb.append("|------|--------|------|------|-----------|----------|\n");

            List<CexAccountBalance> balances = portfolio.getBalances();
            for (CexAccountBalance balance : balances) {
                sb.append("| ").append(balance.getCurrency()).append(" | ");
                sb.append(formatBalance(balance.getTotalBalance())).append(" | ");
                sb.append(formatBalance(balance.getAvailableBalance())).append(" | ");
                sb.append(formatBalance(balance.getFrozenBalance())).append(" | ");
                sb.append(formatBalance(balance.getEquityInUsd())).append(" | ");
                sb.append(formatBalance(balance.getUsedMargin())).append(" |\n");
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("余额查询成功 - apiKeyId: {}, 币种数: {}, 耗时: {}ms", apiKeyId, balances.size(), processingTime);
            return ToolExecutionResult.success(sb.toString(), processingTime, getToolName());

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("余额查询失败", e);
            return ToolExecutionResult.failure("余额查询失败: " + e.getMessage(), processingTime, getToolName());
        }
    }

    /**
     * 格式化余额
     */
    private String formatBalance(java.math.BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    @Override
    public String getToolName() {
        return "balance_info";
    }

    @Override
    public Class<? extends ToolParameters> getParameterType() {
        return BalanceInfoParameters.class;
    }

    @Override
    public boolean validateParameters(ToolParameters parameters) {
        return parameters instanceof BalanceInfoParameters;
    }
}
