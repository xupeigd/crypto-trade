package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.PositionQueryReq;
import com.crypto.trade.model.request.PositionStatisticsReq;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.service.market.PositionQueryService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PositionController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/trading/positions")
public class PositionController {

    @Resource
    ApiKeyRepository apiKeyRepository;
    @Resource
    PositionQueryService positionQueryService;
    @Resource
    UnifiedPositionService unifiedPositionService;  // ✅ 新增：注入UnifiedPositionService

    /**
     * 高级查询持仓数据
     *
     * @param request 查询请求
     * @return ApiResponse<List < PositionModel>> 持仓数据的统一响应格式
     */
    @PostMapping("/query")
    public ApiResponse<List<PositionModel>> queryPositions(@Valid @RequestBody PositionQueryReq request) {
        try {
            log.debug("高级查询持仓数据 - 供应商: {}, 条件: {}",
                    request.getVendorOrDefault(), request.hasQueryConditions() ? "有" : "无");

            // 验证请求参数
            if (!request.isValid()) {
                log.warn("查询请求参数验证失败");
                return ApiResponse.fail("查询请求参数验证失败");
            }

            // 获取第一个活跃的API Key（简化处理）
            List<ApiKey> activeApiKeys = apiKeyRepository.findByStatus("active");
            if (activeApiKeys.isEmpty()) {
                log.warn("查询持仓数据失败 - 无活跃API Key");
                return ApiResponse.fail("无活跃API Key");
            }
            ApiKey apiKey = activeApiKeys.get(0);

            // ✅ 优化：使用智能查询方法，Service层决定缓存策略
            // Controller层不再关心缓存逻辑
            List<PositionModel> positions = positionQueryService.queryPositions(apiKey, request);
            log.info("智能查询持仓数据 - keyId: {}, 结果数: {}", apiKey.getKeyId(), positions.size());

            log.debug("高级查询持仓数据成功 - 查询条件: {}, 结果数量: {}",
                    request.hasQueryConditions() ? "有" : "无", positions.size());
            return ApiResponse.ok(positions);
        } catch (Exception e) {
            log.error("高级查询持仓数据失败", e);
            return ApiResponse.fail("查询持仓数据失败: " + e.getMessage());
        }
    }

    /**
     * 高级统计持仓信息
     *
     * @param request 统计请求
     * @return ApiResponse<PositionQueryService.PositionStatisticsModel> 统计信息的统一响应格式
     */
    @PostMapping("/statistics/advanced")
    public ApiResponse<PositionQueryService.PositionStatisticsModel> getAdvancedStatistics(@Valid @RequestBody PositionStatisticsReq request) {
        try {
            log.debug("高级统计持仓信息 - 供应商: {}, 维度: {}", request.getVendorOrDefault(), request.getDimensionOrDefault());

            // 验证请求参数
            if (!request.isValid()) {
                log.warn("统计请求参数验证失败");
                return ApiResponse.fail("统计请求参数验证失败");
            }

            // 获取第一个活跃的API Key（简化处理）
            List<ApiKey> activeApiKeys = apiKeyRepository.findByStatus("active");
            if (activeApiKeys.isEmpty()) {
                log.warn("统计持仓信息失败 - 无活跃API Key");
                return ApiResponse.fail("无活跃API Key");
            }
            ApiKey apiKey = activeApiKeys.get(0);

            // ✅ 优化：使用智能统计方法，Service层自动处理缓存和空值
            // Controller层不再关心缓存逻辑和空值判断
            PositionQueryService.PositionStatisticsModel statistics = unifiedPositionService.getPositionStatistics(apiKey.getKeyId());
            log.info("智能计算统计数据 - keyId: {}, 总持仓数: {}", apiKey.getKeyId(), statistics.getTotalPositions());
            log.debug("高级统计持仓信息成功 - 统计类型: {}, 总持仓数: {}", request.getStatisticsTypeDescription(), statistics.getTotalPositions());
            return ApiResponse.ok(statistics);
        } catch (Exception e) {
            log.error("高级统计持仓信息失败", e);
            return ApiResponse.fail("统计持仓信息失败: " + e.getMessage());
        }
    }


}