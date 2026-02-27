package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.CurrentModeResponse;
import com.crypto.trade.entity.RiskControlOrder;
import com.crypto.trade.entity.RiskMode;
import com.crypto.trade.model.RiskControlOrderModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.RiskControlOrderService;
import com.crypto.trade.service.RiskControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RiskControlOrderController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/risk-control")
public class RiskControlOrderController {

    @Autowired
    RiskControlOrderService riskControlOrderService;
    @Autowired
    RiskControlService riskControlService;

    /**
     * 获取待审核订单列表
     *
     * @return 待审核订单列表
     */
    @GetMapping("/orders/pending")
    public ApiResponse<List<RiskControlOrderModel>> getPendingOrders() {
        try {
            List<RiskControlOrder> orders = riskControlOrderService.getPendingOrders();
            return ApiResponse.ok(CollectionUtils.isEmpty(orders) ? Collections.emptyList()
                    : orders.stream()
                    .map(v -> {
                        RiskControlOrderModel risk = new RiskControlOrderModel();
                        BeanUtils.copyProperties(v, risk);
                        risk.setSide(v.getSide().name());
                        risk.setPosSide(v.getPosSide());
                        risk.setOrderType(v.getOrderType().name());
                        risk.setAuditStatus(v.getAuditStatus().name());
                        risk.setAuditTime(null == v.getAuditTime() ? null
                                : v.getAuditTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
                        risk.setCreateTime(null == v.getCreateTime() ? null :
                                v.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
                        risk.setUpdateTime(null == v.getUpdateTime() ? null :
                                v.getUpdateTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
                        return risk;
                    })
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("获取待审核订单列表失败", e);
            return ApiResponse.fail("获取待审核订单失败: " + e.getMessage());
        }
    }

    /**
     * 审核通过订单
     *
     * @param orderId 订单ID
     * @return 审核结果
     */
    @PostMapping("/orders/{orderId}/approve")
    public ApiResponse<Void> approveOrder(@PathVariable Long orderId) {
        try {
            riskControlOrderService.approveOrder(orderId, "system");
            log.debug("审核通过订单成功: orderId={}", orderId);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("审核通过订单失败: orderId={}, error={}", orderId, e.getMessage(), e);
            return ApiResponse.fail("审核通过订单失败");
        }
    }

    /**
     * 驳回订单
     *
     * @param orderId 订单ID
     * @param request 请求参数（包含驳回原因）
     * @return 驳回结果
     */
    @PostMapping("/orders/{orderId}/reject")
    public ApiResponse<Void> rejectOrder(@PathVariable Long orderId, @RequestBody Map<String, String> request) {
        try {
            String reason = request.getOrDefault("reason", "无驳回原因");
            if (!StringUtils.hasText(reason.trim())) {
                reason = "无驳回原因";
            }
            riskControlOrderService.rejectOrder(orderId, "system", reason);
            log.debug("驳回订单成功: orderId={}, reason={}", orderId, reason);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("驳回订单失败: orderId={}, error={}", orderId, e.getMessage());
            return ApiResponse.fail("驳回订单失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前风控模式
     *
     * @return 当前风控模式信息
     */
    @GetMapping("/orders/current-mode")
    public ApiResponse<CurrentModeResponse> getCurrentMode() {
        try {
            RiskMode currentMode = riskControlService.getCurrentMode();
            CurrentModeResponse response = CurrentModeResponse.fromRiskMode(currentMode);
            log.debug("获取当前风控模式成功: mode={}", currentMode);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("获取当前风控模式失败", e);
            return ApiResponse.fail("获取当前风控模式失败: " + e.getMessage());
        }
    }

    /**
     * 【新增】根据actionId查询风控订单
     *
     * @param actionId TradeAction的ID
     * @return 风控订单信息
     */
    @GetMapping("/orders/by-action-id/{actionId}")
    public ApiResponse<RiskControlOrderModel> getOrderByActionId(@PathVariable Long actionId) {
        try {
            RiskControlOrderModel order = riskControlOrderService.getOrderByActionId(actionId);
            if (order == null) {
                log.warn("未找到风控订单 - actionId: {}", actionId);
                return ApiResponse.fail("未找到风控订单");
            }
            log.debug("查询风控订单成功 - actionId: {}, orderId: {}", actionId, order.getOrderId());
            return ApiResponse.ok(order);
        } catch (Exception e) {
            log.error("查询风控订单失败 - actionId: {}", actionId, e);
            return ApiResponse.fail("查询风控订单失败: " + e.getMessage());
        }
    }

    /**
     * 【新增】根据recordId查询所有关联的风控订单
     *
     * @param recordId LlmCallRecord的ID
     * @return 风控订单列表
     */
    @GetMapping("/orders/by-record-id/{recordId}")
    public ApiResponse<List<RiskControlOrderModel>> getOrdersByRecordId(@PathVariable Long recordId) {
        try {
            List<RiskControlOrderModel> orders = riskControlOrderService.getOrdersByRecordId(recordId);
            log.debug("查询风控订单列表成功 - recordId: {}, count: {}", recordId, orders.size());
            return ApiResponse.ok(orders);
        } catch (Exception e) {
            log.error("查询风控订单列表失败 - recordId: {}", recordId, e);
            return ApiResponse.fail("查询风控订单列表失败: " + e.getMessage());
        }
    }
}