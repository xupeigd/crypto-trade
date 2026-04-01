package com.crypto.trade.service.conversation;

import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.entity.PositionSnapshot;
import com.crypto.trade.model.OrderModel;
import com.crypto.trade.model.PositionModel;
import com.crypto.trade.service.PositionSnapshotPersistenceService;
import com.crypto.trade.service.TradingOrderService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.unified.UnifiedPositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PositionQueryExecutor
 * 仓位查询工具执行器
 *
 * @author page
 * @date 2026-03-11
 */
@Slf4j
@Component
public class PositionQueryExecutor
        implements ToolExecutor {

    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    UnifiedPositionService unifiedPositionService;
    @Autowired
    PositionSnapshotPersistenceService positionSnapshotPersistenceService;
    @Autowired
    TradingOrderService tradingOrderService;

    @Override
    public ToolExecutionResult execute(ToolParameters parameters, Long apiKeyId) {
        long startTime = System.currentTimeMillis();

        try {
            PositionInfoParameters positionParams = (PositionInfoParameters) parameters;
            // 获取type和limit
            String type = positionParams.getType();
            if (type == null) {
                type = "ALIVE";
            }
            Integer limit = positionParams.getLimit();
            if (limit == null || limit < -1) {
                limit = -1;
            } else if (limit > 30) {
                limit = 30;
            }

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

            // 根据type执行不同的查询
            List<PositionModel> positions;
            String resultTypeName;
            if ("ALIVE".equals(type)) {
                // 当前持仓 - 从UnifiedPositionService获取
                List<CexPosition> cexPositions = unifiedPositionService.getLatestPositionDataWithAutoRefresh(apiKeyId);
                positions = cexPositions.stream()
                        .map(this::convertCexPositionToModel)
                        .collect(Collectors.toList());
                resultTypeName = "当前持仓";
            } else if ("PENDING".equals(type)) {
                // 委托中 - 从TradingOrderService获取
                List<OrderModel> pendingOrders = tradingOrderService.getPendingOrders(apiKeyId, false);
                positions = pendingOrders.stream()
                        .map(this::convertOrderModelToPositionModel)
                        .collect(Collectors.toList());
                resultTypeName = "委托中";
            } else if ("HISTORY".equals(type)) {
                // 历史仓位 - 从快照服务查询
                int historyLimit = limit > 0 ? limit : 100;
                List<PositionSnapshot> snapshots = positionSnapshotPersistenceService.getPositionHistory(apiKeyId,
                        "SWAP", null, historyLimit);
                positions = snapshots.stream()
                        .map(this::convertToPositionModel)
                        .collect(Collectors.toList());
                resultTypeName = "历史仓位";
            } else {
                return ToolExecutionResult.failure("未知的type类型: " + type, getToolName());
            }

            long processingTime = System.currentTimeMillis() - startTime;

            if (positions.isEmpty()) {
                return ToolExecutionResult.success(resultTypeName + "：无数据", processingTime, getToolName());
            }

            // 应用limit限制
            int resultSize = positions.size();
            if (limit > 0 && resultSize > limit) {
                positions = positions.subList(0, limit);
            }

            // 构建返回结果 - Markdown表格格式
            StringBuilder sb = new StringBuilder();
            sb.append("### ").append(resultTypeName).append("（共").append(resultSize).append("个）\n\n");
            sb.append("| 合约 | 方向 | 仓位 | 杠杆 | 未结盈亏 | 收益率 |\n");
            sb.append("|------|------|------|------|----------|--------|\n");

            for (var pos : positions) {
                sb.append("| ").append(pos.getInstId()).append(" | ");
                sb.append("long".equals(pos.getPosSide()) ? "做多" : ("short".equals(pos.getPosSide()) ? "做空" : "-")).append(" | ");
                sb.append(pos.getPos()).append(" | ");
                sb.append(pos.getLever()).append("x | ");
                sb.append(pos.getPnl() != null ? pos.getPnl() : "-").append(" | ");
                sb.append(pos.getUplRatio() != null ? pos.getUplRatio() + "%" : "-").append(" |\n");
            }

            log.info("仓位查询成功 - type: {}, 持仓数: {}, 耗时: {}ms", type, positions.size(), processingTime);
            return ToolExecutionResult.success(sb.toString(), processingTime, getToolName());

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("仓位查询失败", e);
            return ToolExecutionResult.failure("仓位查询失败: " + e.getMessage(), processingTime, getToolName());
        }
    }

    @Override
    public String getToolName() {
        return "position_info";
    }

    @Override
    public Class<? extends ToolParameters> getParameterType() {
        return PositionInfoParameters.class;
    }

    @Override
    public boolean validateParameters(ToolParameters parameters) {
        if (!(parameters instanceof PositionInfoParameters)) {
            return false;
        }
        // position_info的instId是可选的，所以总是返回true
        return true;
    }

    /**
     * 将PositionSnapshot转换为PositionModel
     */
    private PositionModel convertToPositionModel(PositionSnapshot snapshot) {
        PositionModel model = new PositionModel();
        model.setInstId(snapshot.getInstId());
        model.setPos(snapshot.getCloseTotalPos() != null ? snapshot.getCloseTotalPos() : snapshot.getPos());
        model.setPosSide(snapshot.getPosSide());
        model.setLever(snapshot.getLever());
        model.setAvgPx(snapshot.getOpenAvgPx());
        model.setMarkPx(snapshot.getMarkPx());
        model.setUpl(BigDecimal.ZERO);
        model.setUplRatio(BigDecimal.ZERO);
        model.setRealizedPnl(snapshot.getSettledPnl());
        model.setAvailPos(BigDecimal.ZERO);
        model.setMargin(snapshot.getMargin());
        model.setFee(snapshot.getFee());
        model.setFundingFee(snapshot.getFundingFee());
        model.setMgnMode(snapshot.getMgnMode());
        model.setCcy(snapshot.getCcy());
        model.setUTime(snapshot.getUtime());
        model.setCTime(snapshot.getCtime());
        model.setOpenAvgPx(snapshot.getOpenAvgPx());
        model.setCloseAvgPx(snapshot.getCloseAvgPx());
        return model;
    }

    /**
     * 将CexPosition转换为PositionModel
     */
    private PositionModel convertCexPositionToModel(CexPosition pos) {
        PositionModel model = new PositionModel();
        model.setInstId(pos.getSymbol());
        model.setInstType(pos.getInstrumentType());
        model.setPosSide(pos.getSide() != null ? pos.getSide().name().toLowerCase() : null);
        model.setPos(pos.getQuantity());
        model.setAvailPos(pos.getAvailableQuantity());
        model.setAvgPx(pos.getAvgPrice());
        model.setMarkPx(pos.getMarkPrice());
        model.setUpl(pos.getUnrealizedPnl());
        model.setLever(pos.getLeverage());
        model.setMargin(pos.getMargin());
        model.setNotionalUsd(pos.getNotionalValue());
        return model;
    }

    /**
     * 将OrderModel转换为PositionModel（用于委托单）
     */
    private PositionModel convertOrderModelToPositionModel(OrderModel order) {
        PositionModel model = new PositionModel();
        model.setInstId(order.getInstId());
        model.setPosSide(order.getPosSide());
        model.setPos(order.getSz());
        model.setAvgPx(order.getAvgPx());
        model.setLever(order.getLever());
        return model;
    }
}
