package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.cex.adapter.CexPositionAdapter;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DryRunPosition;
import com.crypto.trade.entity.ExecutionMode;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.PositionQueryReq;
import com.crypto.trade.model.request.PositionStatisticsReq;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.service.DryRunPositionService;
import com.crypto.trade.service.ExecutionModeResolver;
import com.crypto.trade.service.market.PositionQueryService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    UnifiedPositionService unifiedPositionService;
    @Resource
    ExecutionModeResolver executionModeResolver;
    @Resource
    DryRunPositionService dryRunPositionService;

    /**
     * 高级查询持仓数据
     *
     * @param request 查询请求
     * @return ApiResponse<List < PositionModel>> 持仓数据的统一响应格式
     */
    @PostMapping("/query")
    public ApiResponse<List<PositionModel>> queryPositions(@Valid @RequestBody PositionQueryReq request) {
        try {
            log.debug("高级查询持仓数据 - 供应商: {}, ALL: {}, 条件: {}",
                    request.getVendorOrDefault(), request.isAll(), request.hasQueryConditions() ? "有" : "无");

            // 验证请求参数
            if (!request.isValid()) {
                log.warn("查询请求参数验证失败");
                return ApiResponse.fail("查询请求参数验证失败");
            }

            // 获取所有活跃的API Key
            List<ApiKey> activeApiKeys = apiKeyRepository.findByStatus("active");
            if (activeApiKeys.isEmpty()) {
                log.warn("查询持仓数据失败 - 无活跃API Key");
                return ApiResponse.fail("无活跃API Key");
            }

            // 根据vendor过滤API Key
            List<ApiKey> filteredApiKeys;
            if (request.isAll()) {
                // ALL选项：使用所有活跃API Key
                filteredApiKeys = activeApiKeys;
                log.info("查询所有交易所持仓 - API Key数量: {}", filteredApiKeys.size());
            } else {
                // 非ALL：根据vendor过滤
                final String vendor = request.getVendorOrDefault();
                filteredApiKeys = activeApiKeys.stream()
                        .filter(k -> vendor.equalsIgnoreCase(k.getCexName()))
                        .collect(Collectors.toList());
                if (filteredApiKeys.isEmpty()) {
                    log.warn("查询持仓数据失败 - 未找到匹配的API Key: {}", vendor);
                    return ApiResponse.fail("未找到匹配的API Key: " + vendor);
                }
            }

            // 遍历每个API Key查询持仓并合并结果
            List<PositionModel> allPositions = new ArrayList<>();
            for (ApiKey apiKey : filteredApiKeys) {
                // 判断执行模式
                ExecutionMode executionMode = executionModeResolver.resolveByApiKeyId(apiKey.getKeyId());
                log.debug("查询持仓数据 - keyId: {}, executionMode: {}", apiKey.getKeyId(), executionMode);

                List<PositionModel> positions;
                if (ExecutionMode.DRY_RUN.equals(executionMode)) {
                    // Dry Run 模式：返回模拟持仓
                    positions = getDryRunPositions(apiKey.getKeyId());
                    log.info("【Dry Run】返回模拟持仓 - keyId: {}, 结果数: {}", apiKey.getKeyId(), positions.size());
                } else {
                    // 使用UnifiedPositionService的缓存数据
                    List<CexPosition> cexPositions = unifiedPositionService.getLatestPositionData(apiKey.getKeyId(), true);
                    List<PositionModel> cachedPositions = CexPositionAdapter.toPositionModelList(cexPositions, apiKey.getCexName());
                    // 应用筛选、排序、分页
                    positions = applyQueryFilters(cachedPositions, request);
                    positions = applySorting(positions, request);
                    positions = applyPagination(positions, request);
                    log.info("【Live】返回交易所持仓 - keyId: {}, 结果数: {}", apiKey.getKeyId(), positions.size());
                }
                allPositions.addAll(positions);
            }

            log.debug("高级查询持仓数据成功 - 查询条件: {}, 结果数量: {}",
                    request.hasQueryConditions() ? "有" : "无", allPositions.size());
            return ApiResponse.ok(allPositions);
        } catch (Exception e) {
            log.error("高级查询持仓数据失败", e);
            return ApiResponse.fail("查询持仓数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取模拟持仓数据
     * 将 DryRunPosition 转换为 PositionModel
     *
     * @param apiKeyId API Key ID
     * @return 模拟持仓列表
     */
    private List<PositionModel> getDryRunPositions(Long apiKeyId) {
        List<DryRunPosition> dryRunPositions = dryRunPositionService.getPositions(apiKeyId);
        if (dryRunPositions == null || dryRunPositions.isEmpty()) {
            return new ArrayList<>();
        }

        return dryRunPositions.stream()
                .map(this::convertToPositionModel)
                .collect(Collectors.toList());
    }

    /**
     * 将 DryRunPosition 转换为 PositionModel
     *
     * @param dryRunPos 模拟持仓实体
     * @return PositionModel
     */
    private PositionModel convertToPositionModel(DryRunPosition dryRunPos) {
        PositionModel model = new PositionModel();
        model.setInstId(dryRunPos.getInstId());
        model.setInstType("SWAP"); // 模拟持仓都是永续合约
        model.setPosSide(dryRunPos.getPosSide());
        model.setPos(dryRunPos.getPos().abs());
        model.setAvailPos(dryRunPos.getPos().abs());
        model.setAvgPx(dryRunPos.getAvgPx());
        model.setLever(dryRunPos.getLever());
        model.setUpl(dryRunPos.getUnrealizedPnl() != null ? dryRunPos.getUnrealizedPnl() : BigDecimal.ZERO);
        model.setCcy("USDT");
        model.setMgnMode("isolated"); // 逐仓模式
        model.setMargin(dryRunPos.getMargin());
        // 设置时间
        if (dryRunPos.getCreatedTime() != null) {
            model.setCTime(dryRunPos.getCreatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (dryRunPos.getUpdatedTime() != null) {
            model.setUTime(dryRunPos.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return model;
    }

    /**
     * 应用查询筛选条件
     */
    private List<PositionModel> applyQueryFilters(List<PositionModel> positions, PositionQueryReq request) {
        return positions.stream()
                .filter(position -> {
                    // 持仓数量筛选
                    if (request.getMinPos() != null && position.getPos().doubleValue() < request.getMinPos()) {
                        return false;
                    }
                    if (request.getMaxPos() != null && position.getPos().doubleValue() > request.getMaxPos()) {
                        return false;
                    }
                    // 杠杆倍数筛选
                    if (request.getMinLever() != null && position.getLever().doubleValue() < request.getMinLever()) {
                        return false;
                    }
                    if (request.getMaxLever() != null && position.getLever().doubleValue() > request.getMaxLever()) {
                        return false;
                    }
                    // 只显示有持仓
                    if (request.isOnlyWithPositions() && position.getPos().doubleValue() == 0) {
                        return false;
                    }
                    // 只显示有盈亏
                    if (request.isOnlyWithPnl() && position.getUpl().doubleValue() == 0) {
                        return false;
                    }
                    // 合约类型筛选
                    if (request.getInstTypes() != null && !request.getInstTypes().isEmpty()) {
                        if (!request.getInstTypes().contains(position.getInstType())) {
                            return false;
                        }
                    }
                    // 持仓方向筛选
                    if (request.getPosSides() != null && !request.getPosSides().isEmpty()) {
                        if (!request.getPosSides().contains(position.getPosSide())) {
                            return false;
                        }
                    }
                    // 合约代码筛选
                    if (request.getInstIds() != null && !request.getInstIds().isEmpty()) {
                        if (!request.getInstIds().contains(position.getInstId())) {
                            return false;
                        }
                    }
                    // 币种筛选
                    if (request.getCcys() != null && !request.getCcys().isEmpty()) {
                        if (!request.getCcys().contains(position.getCcy())) {
                            return false;
                        }
                    }
                    // 关键词搜索
                    if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                        String keyword = request.getKeyword().toLowerCase();
                        return position.getInstId().toLowerCase().contains(keyword);
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * 应用排序
     */
    private List<PositionModel> applySorting(List<PositionModel> positions, PositionQueryReq request) {
        if (positions.isEmpty()) {
            return positions;
        }

        String sortBy = request.getSortByOrDefault();
        String sortOrder = request.getSortOrderOrDefault();
        boolean ascending = "asc".equals(sortOrder);
        return positions.stream()
                .sorted((p1, p2) -> {
                    int result = switch (sortBy) {
                        case "instId" -> p1.getInstId().compareTo(p2.getInstId());
                        case "pos" -> p1.getPos().compareTo(p2.getPos());
                        case "notionalUsd" -> p1.getNotionalUsd().compareTo(p2.getNotionalUsd());
                        case "pnl" -> p1.getUpl().compareTo(p2.getUpl());
                        case "upl" -> p1.getUpl().compareTo(p2.getUpl());
                        case "lever" -> p1.getLever().compareTo(p2.getLever());
                        case "mgnRatio" -> p1.getMgnRatio().compareTo(p2.getMgnRatio());
                        case "uTime" -> Long.compare(p1.getUTime(), p2.getUTime());
                        default -> 0;
                    };
                    return ascending ? result : -result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 应用分页
     */
    private List<PositionModel> applyPagination(List<PositionModel> positions, PositionQueryReq request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        int start = page * size;
        int end = Math.min(start + size, positions.size());
        if (start >= positions.size()) {
            return new ArrayList<>();
        }
        return positions.subList(start, end);
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

            // 判断执行模式
            ExecutionMode executionMode = executionModeResolver.resolveByApiKeyId(apiKey.getKeyId());
            log.info("统计持仓信息 - keyId: {}, executionMode: {}", apiKey.getKeyId(), executionMode);

            PositionQueryService.PositionStatisticsModel statistics;
            if (ExecutionMode.DRY_RUN.equals(executionMode)) {
                // Dry Run 模式：使用模拟持仓计算统计信息
                List<PositionModel> dryRunPositions = getDryRunPositions(apiKey.getKeyId());
                statistics = positionQueryService.calculatePositionStatistics(dryRunPositions);
                log.info("【Dry Run】模拟持仓统计 - keyId: {}, 总持仓数: {}", apiKey.getKeyId(), statistics.getTotalPositions());
            } else {
                // Live 模式：使用交易所真实持仓计算统计信息
                statistics = unifiedPositionService.getPositionStatistics(apiKey.getKeyId());
                log.info("【Live】交易所持仓统计 - keyId: {}, 总持仓数: {}", apiKey.getKeyId(), statistics.getTotalPositions());
            }

            log.debug("高级统计持仓信息成功 - 统计类型: {}, 总持仓数: {}", request.getStatisticsTypeDescription(), statistics.getTotalPositions());
            return ApiResponse.ok(statistics);
        } catch (Exception e) {
            log.error("高级统计持仓信息失败", e);
            return ApiResponse.fail("统计持仓信息失败: " + e.getMessage());
        }
    }


}