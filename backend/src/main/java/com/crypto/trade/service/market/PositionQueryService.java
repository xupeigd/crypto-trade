package com.crypto.trade.service.market;

import com.crypto.trade.dto.cex.adapter.CexPositionAdapter;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.model.request.PositionQueryReq;
import com.crypto.trade.service.cex.UnifiedCexApiService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * PositionQueryService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class PositionQueryService {

    @Autowired
    UnifiedCexApiService unifiedCexApiService;
    @Autowired
    UnifiedPositionService unifiedPositionService;

    /**
     * 查询所有仓位信息
     *
     * @param apiKey   API密钥
     * @param instType 合约类型，如 "SWAP" 永续合约
     * @return 仓位信息列表
     */
    public List<PositionModel> getAllPositions(ApiKey apiKey, String instType) {
        try {
            // 使用新的DTO方式查询仓位 - 现在直接使用CexPosition
            List<CexPosition> cexPositions = unifiedCexApiService.getPositions(apiKey, instType);
            List<PositionModel> positions = new ArrayList<>();
            for (CexPosition positionData : cexPositions) {
                // 只包含有持仓的仓位
                PositionModel position = convertPositionDataToMap(positionData);
                positions.add(position);
            }
            log.debug("成功查询仓位信息 - 类型: {}, 持仓数量: {}", instType, positions.size());
            return positions;
        } catch (Exception e) {
            log.error("查询仓位信息失败 - 类型: {}", instType, e);
            // 使用统一的错误处理
//            String errorMsg = unifiedCexApiService.getErrorMessage(e.getMessage());
            log.error("详细错误信息: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查询指定合约的仓位信息
     *
     * @param apiKey API密钥
     * @param instId 合约ID
     * @return 仓位信息
     */
    public PositionModel getPosition(ApiKey apiKey, String instId) {
        try {
            // 使用新的DTO方式查询仓位（这里需要查询所有仓位然后筛选）
            List<PositionModel> positionDataList = getAllPositions(apiKey, "SWAP");
            for (PositionModel positionData : positionDataList) {
                if (instId.equals(positionData.getInstId())) {
//                    PositionModel position = convertPositionDataToMap(positionData);
                    log.debug("成功查询仓位信息 - 合约: {}, 仓位: {}", instId, positionData.getPos());
                    return positionData;
                }
            }
            log.info("未找到仓位信息 - 合约: {}", instId);
            return null;
        } catch (Exception e) {
            log.error("查询仓位信息失败 - 合约: {}", instId, e);
            // 使用统一的错误处理
            log.error("详细错误信息: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算仓位统计信息
     *
     * @param positions 仓位列表
     * @return 统计信息Model
     */
    public PositionStatisticsModel calculatePositionStatistics(List<PositionModel> positions) {
        PositionStatisticsModel statistics = new PositionStatisticsModel();
        if (null == positions || positions.isEmpty()) {
            statistics.setTotalPositions(0);
            statistics.setTotalNotional(BigDecimal.ZERO);
            statistics.setTotalUnrealizedPnl(BigDecimal.ZERO);
            statistics.setTotalMargin(BigDecimal.ZERO);
            statistics.setSideStatistics(new ArrayList<>());
            statistics.setTypeStatistics(new ArrayList<>());
            return statistics;
        }
        BigDecimal totalNotional = BigDecimal.ZERO;
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;
        // 按持仓方向统计
        Map<String, Integer> sideCount = new HashMap<>();
        Map<String, BigDecimal> sideNotional = new HashMap<>();
        // 按合约类型统计
        Map<String, Integer> typeCount = new HashMap<>();
        Map<String, BigDecimal> typeNotional = new HashMap<>();
        for (PositionModel position : positions) {
            BigDecimal notionalUsd = position.getNotionalUsd();
            if (null == notionalUsd) {
                notionalUsd = BigDecimal.ZERO;
            }
            BigDecimal unrealizedPnl = position.getUpl();
            if (null == unrealizedPnl) {
                unrealizedPnl = BigDecimal.ZERO;
            }
            BigDecimal margin = position.getMargin();
            if (null == margin) {
                margin = BigDecimal.ZERO;
            }
            String posSide = position.getPosSide();
            if (null == posSide) {
                posSide = "unknown";
            }
            String instType = position.getInstType();
            if (null == instType) {
                instType = "unknown";
            }
            totalNotional = totalNotional.add(notionalUsd);
            totalUnrealizedPnl = totalUnrealizedPnl.add(unrealizedPnl);
            totalMargin = totalMargin.add(margin);
            // 持仓方向统计
            sideCount.put(posSide, sideCount.getOrDefault(posSide, 0) + 1);
            sideNotional.put(posSide, sideNotional.getOrDefault(posSide, BigDecimal.ZERO).add(notionalUsd));
            // 合约类型统计
            typeCount.put(instType, typeCount.getOrDefault(instType, 0) + 1);
            typeNotional.put(instType, typeNotional.getOrDefault(instType, BigDecimal.ZERO).add(notionalUsd));
        }
        statistics.setTotalPositions(positions.size());
        statistics.setTotalNotional(totalNotional);
        statistics.setTotalUnrealizedPnl(totalUnrealizedPnl);
        statistics.setTotalMargin(totalMargin);
        statistics.setSideStatistics(convertToStatisticsItemList(sideCount, sideNotional));
        statistics.setTypeStatistics(convertToStatisticsItemList(typeCount, typeNotional));
        return statistics;
    }

    /**
     * 转换统计信息为StatisticsItem列表
     */
    private List<PositionStatisticsModel.StatisticsItem> convertToStatisticsItemList(Map<String, Integer> countMap,
                                                                                     Map<String, BigDecimal> notionalMap) {
        List<PositionStatisticsModel.StatisticsItem> statistics = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            String key = entry.getKey();
            PositionStatisticsModel.StatisticsItem stat = PositionStatisticsModel.StatisticsItem.builder()
                    .name(key)
                    .count(entry.getValue())
                    .totalNotional(notionalMap.getOrDefault(key, BigDecimal.ZERO))
                    .totalPnl(BigDecimal.ZERO)
                    .totalMargin(BigDecimal.ZERO)
                    .build();
            statistics.add(stat);
        }
        return statistics;
    }

    /**
     * 将CexPosition转换为Map格式，保持向后兼容
     *
     * @param positionData 持仓数据DTO
     * @return Map格式的持仓信息
     */
    private PositionModel convertPositionDataToMap(CexPosition positionData) {
        return CexPositionAdapter.toPositionModel(positionData);
    }

    /**
     * 根据查询请求获取仓位信息
     *
     * @param apiKey  API密钥
     * @param request 查询请求
     * @return 符合条件的仓位信息Model列表
     */
    public List<PositionModel> queryPositions(ApiKey apiKey, PositionQueryReq request) {
        log.debug("根据查询请求获取仓位信息 - 供应商: {}, 条件数量: {}",
                request.getVendorOrDefault(), request.hasQueryConditions() ? "有" : "无");
        try {
            // 验证请求参数
            if (!request.isValid()) {
                log.warn("查询请求参数验证失败");
                return new ArrayList<>();
            }
            // 获取所有合约类型的仓位
            List<PositionModel> allPositions = new ArrayList<>();
            List<String> instTypes = request.getInstTypes();
            if (null == instTypes || instTypes.isEmpty()) {
                // 查询所有类型
                instTypes = List.of("SWAP");
//                instTypes = List.of("SWAP", "FUTURES", "OPTION");
            }
            for (String instType : instTypes) {
                List<PositionModel> positions = getAllPositions(apiKey, instType);
                allPositions.addAll(positions);
            }
            // 应用筛选条件
            List<PositionModel> filteredPositions = applyQueryFilters(allPositions, request);
            // 应用排序
            filteredPositions = applySorting(filteredPositions, request);
            // 应用分页
            filteredPositions = applyPagination(filteredPositions, request);
            log.debug("查询仓位信息成功 - 筛选前: {}, 筛选后: {}",
                    allPositions.size(), filteredPositions.size());
            return filteredPositions;
        } catch (Exception e) {
            log.error("根据查询请求获取仓位信息失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 计算仓位统计信息的Model格式
     *
     * @param positions 仓位列表
     * @return 统计信息Model
     */
    public PositionStatisticsModel calculatePositionStatisticsModel(List<PositionModel> positions) {
        return calculatePositionStatistics(positions);
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
     * ✅ 新增：从UnifiedPositionService获取缓存数据
     * 用于okx-positions页面，避免频繁调用OKX API
     *
     * @param apiKey  API密钥
     * @param request 查询请求
     * @return 符合条件的仓位信息Model列表
     */
    public List<PositionModel> getCachedPositions(ApiKey apiKey, PositionQueryReq request, boolean useCache) {
        log.debug("从缓存获取仓位信息 - keyId: {}", apiKey.getKeyId());

        try {
            // 从UnifiedPositionService获取缓存数据
            List<CexPosition> cachedPositions = unifiedPositionService.getLatestPositionData(apiKey.getKeyId(), useCache);
            if (CollectionUtils.isEmpty(cachedPositions)) {
                log.debug("缓存中无持仓数据 - keyId: {}", apiKey.getKeyId());
                return new ArrayList<>();
            }

            // 应用筛选条件
            Stream<CexPosition> stream = cachedPositions.stream();

            // 筛选合约类型
            if (null != request.getInstTypes() && !request.getInstTypes().isEmpty()) {
                stream = stream.filter(pos -> {
                    String instType = pos.getInstrumentType();
                    return instType != null && request.getInstTypes().contains(instType);
                });
            }

            // 筛选持仓方向
            if (null != request.getPosSides() && !request.getPosSides().isEmpty()) {
                stream = stream.filter(pos -> request.getPosSides().contains(pos.getSide().name()));
            }

            // 筛选合约ID
            if (null != request.getInstIds() && !request.getInstIds().isEmpty()) {
                stream = stream.filter(pos -> request.getInstIds().contains(pos.getSymbol()));
            }

            // 筛选保证金币种
            if (null != request.getCcys() && !request.getCcys().isEmpty()) {
                stream = stream.filter(pos -> {
                    String ccy = pos.getCurrency();
                    return ccy != null && request.getCcys().contains(ccy);
                });
            }

            // 搜索关键词
            if (StringUtils.hasText(request.getKeyword())) {
                stream = stream.filter(pos -> pos.getSymbol().contains(request.getKeyword()));
            }

            // 转换为PositionModel
            List<PositionModel> positions = stream.map(pos -> {
                PositionModel model = new PositionModel();
                model.setInstId(pos.getSymbol());
                model.setInstType(pos.getInstrumentType());
                model.setPosSide(pos.getSide().name());
                model.setPos(pos.getQuantity());
                model.setAvailPos(pos.getAvailableQuantity());
                model.setAvgPx(pos.getAvgPrice());
                // model.setLast(pos.getLast());  // CexPosition暂无last字段
                model.setMarkPx(pos.getMarkPrice());
                model.setUpl(pos.getUnrealizedPnl());
                // model.setUplRatio(pos.getUplRatio()); // CexPosition暂无此字段
                model.setLever(pos.getLeverage());
                model.setMargin(pos.getMargin());
                model.setNotionalUsd(pos.getNotionalValue());
                // model.setDataIngestionTime(pos.getDataIngestionTime()); // CexPosition暂无此字段
                return model;
            }).collect(Collectors.toList());

            log.debug("缓存查询完成 - keyId: {}, 结果数: {}", apiKey.getKeyId(), positions.size());
            return positions;
        } catch (Exception e) {
            log.error("从缓存获取持仓数据失败 - keyId: {}", apiKey.getKeyId(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 持仓统计信息Model
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PositionStatisticsModel {
        Integer totalPositions;
        BigDecimal totalNotional;
        BigDecimal totalUnrealizedPnl;
        BigDecimal totalMargin;
        List<StatisticsItem> sideStatistics;
        List<StatisticsItem> typeStatistics;
        List<StatisticsItem> currencyStatistics;

        /**
         * 统计项
         */
        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class StatisticsItem {
            String name;
            Integer count;
            BigDecimal totalNotional;
            BigDecimal totalPnl;
            BigDecimal totalMargin;
        }
    }

}