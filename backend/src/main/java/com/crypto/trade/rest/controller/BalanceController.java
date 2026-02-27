package com.crypto.trade.rest.controller;

import com.crypto.trade.model.AccountDetailModel;
import com.crypto.trade.model.BalanceSummaryModel;
import com.crypto.trade.model.CexBalanceModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.BalanceService;
import com.crypto.trade.service.unified.UnifiedBalanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            AccountDetailModel detail = unifiedBalanceService.getAccountUsdtDetail(apiKeyId);
            return ApiResponse.ok(detail);
        } catch (Exception e) {
            log.error("获取账户详情失败，apiKeyId: {}", apiKeyId, e);
            return ApiResponse.ok(AccountDetailModel.empty());
        }
    }

}