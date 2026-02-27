package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.RiskModeHistory;
import com.crypto.trade.entity.TradingStyle;
import com.crypto.trade.entity.TradingStyleHistory;
import com.crypto.trade.model.CurrentModeModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.rest.controller.model.request.ResetRiskModeRequest;
import com.crypto.trade.rest.controller.model.request.ResetTradingStyleRequest;
import com.crypto.trade.rest.controller.model.request.SetRiskModeRequest;
import com.crypto.trade.rest.controller.model.request.SetTradingStyleRequest;
import com.crypto.trade.rest.controller.model.response.TradingStyleResponse;
import com.crypto.trade.service.RiskControlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * RiskControlController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/risk-control")
public class RiskControlController {

    @Autowired
    RiskControlService riskControlService;

    /**
     * 获取风控配置信息
     * 包含风控模式和交易风格信息
     *
     * @return 风控配置信息
     */
    @GetMapping("/info")
    public ApiResponse<RiskControlService.RiskControlInfo> getRiskControlInfo() {
        try {
            log.debug("获取风控配置信息");
            RiskControlService.RiskControlInfo info = riskControlService.getRiskControlInfo();
            log.debug("获取风控配置信息成功: 风控模式={}, 交易风格={}",
                    info.getCurrentMode(), info.getCurrentTradingStyle());
            return ApiResponse.ok(info);
        } catch (Exception e) {
            log.error("获取风控配置信息失败", e);
            return ApiResponse.fail("获取风控配置信息失败: " + e.getMessage());
        }
    }

    // ========== 风控模式相关端点 ==========

    /**
     * 获取当前风控模式
     *
     * @return 当前风控模式
     */
    @GetMapping("/current-mode")
    public ApiResponse<CurrentModeModel> getCurrentMode() {
        try {
            log.debug("获取当前风控模式");
            var currentMode = riskControlService.getCurrentMode();

            // 使用类型安全的Model替代Map
            CurrentModeModel result = CurrentModeModel.fromRiskMode(currentMode);

            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("获取当前风控模式失败", e);
            return ApiResponse.fail("获取当前风控模式失败: " + e.getMessage());
        }
    }

    /**
     * 设置风控模式
     *
     * @param request     设置风控模式请求
     * @param httpRequest HTTP请求对象
     * @return 设置结果
     */
    @PostMapping("/mode")
    public ApiResponse<RiskModeHistory> setRiskMode(@RequestBody SetRiskModeRequest request, HttpServletRequest httpRequest) {
        try {
            String modeStr = request.getMode();
            String reason = request.getReason();
            if (null == reason || reason.trim().isEmpty()) {
                reason = "手动设置";
            }
            if (null == modeStr || modeStr.trim().isEmpty()) {
                return ApiResponse.fail("风控模式不能为空");
            }
            // 解析风控模式枚举
            var newMode = Arrays.stream(com.crypto.trade.entity.RiskMode.values())
                    .filter(mode -> mode.name().equals(modeStr))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的风控模式: " + modeStr));
            log.debug("设置风控模式: {} -> {}, 原因: {}",
                    riskControlService.getCurrentMode(), newMode, reason);
            RiskModeHistory history = riskControlService.setRiskMode(newMode, reason, httpRequest);
            log.debug("风控模式设置成功: {}", newMode);
            return ApiResponse.ok(history);
        } catch (IllegalArgumentException e) {
            log.warn("设置风控模式失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("设置风控模式失败", e);
            return ApiResponse.fail("设置风控模式失败: " + e.getMessage());
        }
    }

    /**
     * 重置为默认风控模式
     *
     * @param request     重置请求
     * @param httpRequest HTTP请求对象
     * @return 重置结果
     */
    @PostMapping("/reset")
    public ApiResponse<RiskModeHistory> resetToDefault(@RequestBody ResetRiskModeRequest request, HttpServletRequest httpRequest) {
        try {
            String reason = request.getReason();
            if (null == reason || reason.trim().isEmpty()) {
                reason = "重置为默认模式";
            }
            log.debug("重置为默认风控模式，原因: {}", reason);
            RiskModeHistory history = riskControlService.resetToDefault(reason, httpRequest);
            log.debug("风控模式重置成功: {}", history.getNewMode());
            return ApiResponse.ok(history);
        } catch (IllegalArgumentException e) {
            log.warn("重置风控模式失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("重置风控模式失败", e);
            return ApiResponse.fail("重置风控模式失败: " + e.getMessage());
        }
    }

    /**
     * 获取风控模式变更历史记录
     *
     * @param limit 查询条数限制
     * @return 历史记录列表
     */
    @GetMapping("/history")
    public ApiResponse<List<RiskModeHistory>> getHistory(@RequestParam(required = false) Integer limit) {
        try {
            log.debug("获取风控模式变更历史记录，limit: {}", limit);
            List<RiskModeHistory> history;
            if (null != limit && limit > 0) {
                history = riskControlService.getRiskModeHistory(limit);
            } else {
                history = riskControlService.getRiskModeHistory();
            }
            log.debug("获取风控模式变更历史记录成功，条数: {}", history.size());
            return ApiResponse.ok(history);
        } catch (Exception e) {
            log.error("获取风控模式变更历史记录失败", e);
            return ApiResponse.fail("获取风控模式变更历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近一次风控模式变更
     *
     * @return 最近一次变更记录
     */
    @GetMapping("/history/last")
    public ApiResponse<RiskModeHistory> getLastChange() {
        try {
            log.debug("获取最近一次风控模式变更");
            RiskModeHistory lastChange = riskControlService.getLastChange();
            log.debug("获取最近一次风控模式变更成功: {}", null != lastChange ? "有记录" : "无记录");
            return ApiResponse.ok(lastChange);
        } catch (Exception e) {
            log.error("获取最近一次风控模式变更失败", e);
            return ApiResponse.fail("获取最近一次风控模式变更失败: " + e.getMessage());
        }
    }

    // ========== 交易风格相关端点 ==========

    /**
     * 获取当前交易风格
     *
     * @return 当前交易风格
     */
    @GetMapping("/current-trading-style")
    public ApiResponse<TradingStyleResponse> getCurrentTradingStyle() {
        try {
            log.debug("获取当前交易风格");
            var currentStyle = riskControlService.getCurrentTradingStyle();
            TradingStyleResponse response = TradingStyleResponse.builder()
                    .style(currentStyle)
                    .description(currentStyle.getDescription())
                    .maxLossRate(currentStyle.getMaxLossRate())
                    .targetProfitRate(currentStyle.getTargetProfitRate())
                    .build();
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("获取当前交易风格失败", e);
            return ApiResponse.fail("获取当前交易风格失败: " + e.getMessage());
        }
    }

    /**
     * 设置交易风格
     *
     * @param request     设置交易风格请求
     * @param httpRequest HTTP请求对象
     * @return 设置结果
     */
    @PostMapping("/trading-style")
    public ApiResponse<TradingStyleHistory> setTradingStyle(@RequestBody SetTradingStyleRequest request, HttpServletRequest httpRequest) {
        try {
            String styleStr = request.getStyle();
            String reason = request.getReason();
            if (null == reason || reason.trim().isEmpty()) {
                reason = "手动设置交易风格";
            }
            if (null == styleStr || styleStr.trim().isEmpty()) {
                return ApiResponse.fail("交易风格不能为空");
            }
            // 解析交易风格枚举
            TradingStyle newStyle = Arrays.stream(TradingStyle.values())
                    .filter(style -> style.name().equals(styleStr))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的交易风格: " + styleStr));
            log.debug("设置交易风格: {} -> {}, 原因: {}",
                    riskControlService.getCurrentTradingStyle(), newStyle, reason);
            TradingStyleHistory history = riskControlService.setTradingStyle(newStyle, reason, httpRequest);
            log.debug("交易风格设置成功: {}", newStyle);
            return ApiResponse.ok(history);
        } catch (IllegalArgumentException e) {
            log.warn("设置交易风格失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("设置交易风格失败", e);
            return ApiResponse.fail("设置交易风格失败: " + e.getMessage());
        }
    }

    /**
     * 重置交易风格为默认值
     *
     * @param request     重置请求
     * @param httpRequest HTTP请求对象
     * @return 重置结果
     */
    @PostMapping("/trading-style/reset")
    public ApiResponse<TradingStyleHistory> resetTradingStyleToDefault(@RequestBody ResetTradingStyleRequest request, HttpServletRequest httpRequest) {
        try {
            String reason = request.getReason();
            if (null == reason || reason.trim().isEmpty()) {
                reason = "重置为默认交易风格";
            }
            log.debug("重置为默认交易风格，原因: {}", reason);
            TradingStyleHistory history = riskControlService.resetTradingStyleToDefault(reason, httpRequest);
            log.debug("交易风格重置成功: {}", history.getNewStyle());
            return ApiResponse.ok(history);
        } catch (IllegalArgumentException e) {
            log.warn("重置交易风格失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("重置交易风格失败", e);
            return ApiResponse.fail("重置交易风格失败: " + e.getMessage());
        }
    }

    /**
     * 获取交易风格变更历史记录
     *
     * @param limit 查询条数限制
     * @return 历史记录列表
     */
    @GetMapping("/trading-style/history")
    public ApiResponse<List<TradingStyleHistory>> getTradingStyleHistory(@RequestParam(required = false) Integer limit) {
        try {
            log.debug("获取交易风格变更历史记录，limit: {}", limit);
            List<TradingStyleHistory> history;
            if (null != limit && limit > 0) {
                history = riskControlService.getTradingStyleHistory(limit);
            } else {
                history = riskControlService.getTradingStyleHistory();
            }
            log.debug("获取交易风格变更历史记录成功，条数: {}", history.size());
            return ApiResponse.ok(history);
        } catch (Exception e) {
            log.error("获取交易风格变更历史记录失败", e);
            return ApiResponse.fail("获取交易风格变更历史记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近一次交易风格变更
     *
     * @return 最近一次变更记录
     */
    @GetMapping("/trading-style/history/last")
    public ApiResponse<TradingStyleHistory> getLastTradingStyleChange() {
        try {
            log.debug("获取最近一次交易风格变更");
            TradingStyleHistory lastChange = riskControlService.getLastTradingStyleChange();
            log.debug("获取最近一次交易风格变更成功: {}", null != lastChange ? "有记录" : "无记录");
            return ApiResponse.ok(lastChange);
        } catch (Exception e) {
            log.error("获取最近一次交易风格变更失败", e);
            return ApiResponse.fail("获取最近一次交易风格变更失败: " + e.getMessage());
        }
    }
}