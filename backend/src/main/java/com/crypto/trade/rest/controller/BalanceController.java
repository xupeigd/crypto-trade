package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DryRunPosition;
import com.crypto.trade.entity.ExecutionMode;
import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.model.BalanceSummaryModel;
import com.crypto.trade.model.CexBalanceModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.BalanceService;
import com.crypto.trade.service.DryRunPositionService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.market.UnifiedPriceDataService;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import com.crypto.trade.service.ExecutionModeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BalanceController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/cex-balances")
public class BalanceController {

    @Autowired
    BalanceService balanceService;
    @Autowired
    UnifiedBalanceService unifiedBalanceService;
    @Autowired
    ExecutionModeResolver executionModeResolver;
    @Autowired
    DryRunPositionService dryRunPositionService;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    UnifiedPriceDataService priceDataService;

    /**
     * 获取最新余额数据
     */
    @GetMapping("/latest")
    public ApiResponse<List<CexBalanceModel>> getLatestBalances(@RequestParam(defaultValue = "ALL") String cexName) {
        log.debug("获取CEX余额数据，cexName: {}", cexName);
        try {
            List<CexBalanceModel> balances = balanceService.getLatestBalancesByCexModel(cexName);
            return ApiResponse.ok(balances);
        } catch (Exception e) {
            log.error("获取最新余额数据失败", e);
            return ApiResponse.fail("获取最新余额数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取余额汇总信息
     */
    @GetMapping("/summary")
    public ApiResponse<BalanceSummaryModel> getBalanceSummary(@RequestParam(defaultValue = "ALL") String cexName) {
        log.debug("获取余额汇总信息，cexName: {}", cexName);
        try {
            BalanceSummaryModel summary = balanceService.getBalanceSummaryModel(cexName);
            return ApiResponse.ok(summary);
        } catch (Exception e) {
            log.error("获取余额汇总信息失败", e);
            return ApiResponse.fail("获取余额汇总信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃的CEX名称
     */
    @GetMapping("/active-cex")
    public ApiResponse<List<String>> getActiveCexNames() {
        log.debug("获取所有活跃的CEX名称");
        try {
            List<String> cexNames = balanceService.getActiveCexNames();
            return ApiResponse.ok(cexNames);
        } catch (Exception e) {
            log.error("获取活跃CEX名称失败", e);
            return ApiResponse.fail("获取活跃CEX名称失败: " + e.getMessage());
        }
    }

    /**
     * 获取账户详情信息
     * 包含账户权益、已用保证金、可用余额、未实现盈亏和保证金使用率
     */
    @GetMapping("/{apiKeyId}/account-details")
    public ApiResponse<AccountDetailModel> getAccountDetails(@PathVariable Long apiKeyId) {
        log.debug("获取账户详情信息，apiKeyId: {}", apiKeyId);
        try {
            // 判断执行模式
            ExecutionMode executionMode = executionModeResolver.resolveByApiKeyId(apiKeyId);
            log.debug("获取账户详情 - keyId: {}, executionMode: {}", apiKeyId, executionMode);

            if (ExecutionMode.DRY_RUN.equals(executionMode)) {
                // Dry Run 模式：计算模拟盘账户权益
                AccountDetailModel detail = getDryRunAccountDetails(apiKeyId);
                return ApiResponse.ok(detail);
            }

            // Live 模式：获取实盘账户详情
            AccountDetailModel detail = unifiedBalanceService.getAccountUsdtDetail(apiKeyId);
            return ApiResponse.ok(detail);
        } catch (Exception e) {
            log.error("获取账户详情失败，apiKeyId: {}", apiKeyId, e);
            return ApiResponse.ok(AccountDetailModel.empty());
        }
    }

    /**
     * 获取 Dry Run 模式的账户详情
     * 模拟盘权益 = 实盘账户余额 + 模拟交易总盈亏
     */
    private AccountDetailModel getDryRunAccountDetails(Long apiKeyId) {
        AccountDetailModel response = new AccountDetailModel();

        try {
            // 1. 获取实盘账户余额作为基础
            AccountDetailModel liveDetail = unifiedBalanceService.getAccountUsdtDetail(apiKeyId);
            BigDecimal baseEquity = liveDetail.getTotalEquity() != null ? liveDetail.getTotalEquity() : BigDecimal.ZERO;
            BigDecimal baseAvailable = liveDetail.getAvailableBalance() != null ? liveDetail.getAvailableBalance() : BigDecimal.ZERO;

            // 2. 获取已实现盈亏
            BigDecimal realizedPnl = dryRunPositionService.getTotalRealizedPnl(apiKeyId);
            if (realizedPnl == null) {
                realizedPnl = BigDecimal.ZERO;
            }

            // 3. 计算未实现盈亏（需要实时标记价格）
            BigDecimal unrealizedPnl = BigDecimal.ZERO;
            BigDecimal usedMargin = BigDecimal.ZERO;
            List<DryRunPosition> openPositions = dryRunPositionService.getOpenPositions(apiKeyId);

            if (!openPositions.isEmpty()) {
                ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
                Map<String, BigDecimal> markPrices = new HashMap<>();

                // 获取所有持仓合约的标记价格
                for (DryRunPosition pos : openPositions) {
                    if (!markPrices.containsKey(pos.getInstId())) {
                        try {
                            BigDecimal markPx = priceDataService.getMarkPrice(apiKey, pos.getInstId());
                            if (markPx != null && markPx.compareTo(BigDecimal.ZERO) > 0) {
                                markPrices.put(pos.getInstId(), markPx);
                            }
                        } catch (Exception e) {
                            log.warn("获取标记价格失败 - instId: {}, error: {}", pos.getInstId(), e.getMessage());
                        }
                    }
                }

                // 计算总未实现盈亏和保证金
                for (DryRunPosition pos : openPositions) {
                    BigDecimal markPx = markPrices.get(pos.getInstId());
                    if (markPx != null) {
                        BigDecimal posUnrealizedPnl = dryRunPositionService.calculateUnrealizedPnl(pos, markPx);
                        unrealizedPnl = unrealizedPnl.add(posUnrealizedPnl);
                    }
                    if (pos.getMargin() != null) {
                        usedMargin = usedMargin.add(pos.getMargin());
                    }
                }
            }

            // 4. 计算模拟盘权益
            // 总盈亏 = 已实现盈亏 + 未实现盈亏
            BigDecimal totalPnl = realizedPnl.add(unrealizedPnl);
            // 模拟盘权益 = 实盘余额 + 总盈亏
            BigDecimal dryRunEquity = baseEquity.add(totalPnl);
            // 可用余额 = 实盘可用 - 占用保证金 + 未实现盈亏（这里简化处理）
            BigDecimal dryRunAvailable = baseAvailable.subtract(usedMargin).add(unrealizedPnl);
            if (dryRunAvailable.compareTo(BigDecimal.ZERO) < 0) {
                dryRunAvailable = BigDecimal.ZERO;
            }

            // 5. 计算保证金使用率
            BigDecimal marginRatio = BigDecimal.ZERO;
            if (dryRunEquity.compareTo(BigDecimal.ZERO) > 0) {
                marginRatio = usedMargin.divide(dryRunEquity, 8, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            // 6. 构建响应
            response.setTotalEquity(dryRunEquity);
            response.setAvailableBalance(dryRunAvailable);
            response.setUsedMargin(usedMargin);
            response.setUnrealizedPnl(unrealizedPnl);
            response.setMarginRatio(marginRatio);
            response.setLastUpdateTime(java.time.LocalDateTime.now());

            log.debug("【Dry Run】账户详情 - keyId: {}, equity: {}, available: {}, usedMargin: {}, unrealizedPnl: {}, realizedPnl: {}",
                    apiKeyId, dryRunEquity, dryRunAvailable, usedMargin, unrealizedPnl, realizedPnl);

        } catch (Exception e) {
            log.error("计算 Dry Run 账户详情失败", e);
        }

        return response;
    }

}