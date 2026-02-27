package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.common.MarginMode;
import com.crypto.trade.dto.cex.common.PositionSide;
import com.crypto.trade.dto.cex.model.CexAlgoOrder;
import com.crypto.trade.dto.cex.model.CexPosition;
import com.crypto.trade.dto.cex.okx.OkxPosition;
import com.crypto.trade.model.PositionModel;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexPositionAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexPositionAdapter {

    public static CexPosition adapt(OkxPosition okxPosition) {
        if (null == okxPosition) {
            return null;
        }

        return new CexPosition() {

            final OkxPosition orginal = okxPosition;

            @Override
            public String getSymbol() {
                return orginal.getInstId();
            }

            @Override
            public PositionSide getSide() {
                return adaptPositionSide(orginal.getPosSide());
            }

            @Override
            public BigDecimal getQuantity() {
                return orginal.getPos();
            }

            @Override
            public BigDecimal getAvailableQuantity() {
                return orginal.getAvailPos();
            }

            @Override
            public BigDecimal getAvgPrice() {
                return orginal.getAvgPx();
            }

            @Override
            public BigDecimal getMarkPrice() {
                return orginal.getMarkPx();
            }

            @Override
            public BigDecimal getUnrealizedPnl() {
                return orginal.getUpl();
            }

            @Override
            public BigDecimal getUnrealizedPnlRatio() {
                return orginal.getUplRatio();
            }

            @Override
            public BigDecimal getRealizedPnl() {
                return orginal.getRealizedPnl();
            }

            @Override
            public BigDecimal getLeverage() {
                return orginal.getLever();
            }

            @Override
            public MarginMode getMarginMode() {
                return adaptMarginMode(orginal.getMgnMode());
            }

            @Override
            public BigDecimal getMargin() {
                return orginal.getMargin();
            }

            @Override
            public BigDecimal getLiquidationPrice() {
                return orginal.getLiqPx();
            }

            @Override
            public BigDecimal getNotionalValue() {
                return orginal.getNotionalUsd();
            }

            @Override
            public Long getCreateTime() {
                return orginal.getCTime();
            }

            @Override
            public String getInstrumentType() {
                return orginal.getInstType();
            }

            @Override
            public String getCurrency() {
                return orginal.getCcy();
            }

            @Override
            public BigDecimal getMarginRatio() {
                return orginal.getMgnRatio();
            }

            @Override
            public String getPosId() {
                return orginal.getPosId();
            }

            @Override
            public BigDecimal getFundingFee() {
                return orginal.getFundingFee();
            }

            @Override
            public BigDecimal getFee() {
                return orginal.getFee();
            }

            @Override
            public List<CexAlgoOrder> getCloseOrderAlgo() {
                return CollectionUtils.isEmpty(orginal.getCloseOrderAlgo()) ? List.of()
                        : orginal.getCloseOrderAlgo().stream()
                        .map(CexAlgoOrderAdapter::adapt)
                        .collect(Collectors.toList());
            }

            @Override
            public String getMgnMode() {
                return orginal.getMgnMode();
            }

            @Override
            public String getType() {
                return orginal.getType();
            }

            @Override
            public BigDecimal getLast() {
                return orginal.getLast();
            }

            @Override
            public BigDecimal getImr() {
                return orginal.getImr();
            }

            @Override
            public BigDecimal getMmr() {
                return orginal.getMmr();
            }

            @Override
            public Long getUTime() {
                return orginal.getUTime();
            }

            @Override
            public BigDecimal getUplLastPx() {
                return orginal.getUplLastPx();
            }

            @Override
            public Long getDataIngestionTime() {
                return orginal.getDataIngestionTime();
            }

            @Override
            public BigDecimal getOpenMaxPos() {
                return orginal.getOpenMaxPos();
            }

            @Override
            public BigDecimal getCloseTotalPos() {
                return orginal.getCloseTotalPos();
            }

            @Override
            public BigDecimal getSettledPnl() {
                return orginal.getSettledPnl();
            }

            @Override
            public BigDecimal getPnlRatio() {
                return orginal.getPnlRatio();
            }

            @Override
            public BigDecimal getOpenAvgPx() {
                return orginal.getOpenAvgPx();
            }

            @Override
            public BigDecimal getCloseAvgPx() {
                return orginal.getCloseAvgPx();
            }
        };
    }

    public static List<CexPosition> adapt(List<OkxPosition> okxPositions) {
        if (CollectionUtils.isEmpty(okxPositions)) {
            return Collections.emptyList();
        }

        return okxPositions.stream()
                .filter(Objects::nonNull)
                .map(CexPositionAdapter::adapt)
                .collect(Collectors.toList());
    }

    private static PositionSide adaptPositionSide(String posSide) {
        if (null == posSide || posSide.trim().isEmpty()) {
            return PositionSide.UNKNOWN;
        }

        return switch (posSide.toLowerCase()) {
            case "long" -> PositionSide.LONG;
            case "short" -> PositionSide.SHORT;
            case "net" -> PositionSide.NET;
            default -> PositionSide.UNKNOWN;
        };
    }

    private static MarginMode adaptMarginMode(String mgnMode) {
        if (null == mgnMode || mgnMode.trim().isEmpty()) {
            return MarginMode.UNKNOWN;
        }

        return switch (mgnMode.toLowerCase()) {
            case "isolated" -> MarginMode.ISOLATED;
            case "cross" -> MarginMode.CROSS;
            default -> MarginMode.UNKNOWN;
        };
    }

    /**
     * 将CexPosition转换为PositionModel
     * <p>
     * 统一的转换方法,用于将抽象的CexPosition对象转换为前端使用的PositionModel。
     * 手动复制所有字段,避免使用JsonUtils.transform()导致的Jackson反序列化抽象类错误。
     * </p>
     *
     * @param cexPosition CEX通用持仓对象
     * @return PositionModel
     */
    public static PositionModel toPositionModel(CexPosition cexPosition) {
        if (null == cexPosition) {
            return null;
        }

        PositionModel model = new PositionModel();

        // 基础字段映射
        if (null != cexPosition.getSymbol()) {
            model.setInstId(cexPosition.getSymbol());
        }
        if (null != cexPosition.getSide()) {
            model.setPosSide(cexPosition.getSide().getCode());
        }
        if (null != cexPosition.getInstrumentType()) {
            model.setInstType(cexPosition.getInstrumentType());
        }

        // 数量和价格字段
        if (null != cexPosition.getAvgPrice()) {
            model.setAvgPx(cexPosition.getAvgPrice());
        }
        if (null != cexPosition.getMarkPrice()) {
            model.setMarkPx(cexPosition.getMarkPrice());
        }
        if (null != cexPosition.getAvailableQuantity()) {
            model.setAvailPos(cexPosition.getAvailableQuantity());
        }
        if (null != cexPosition.getQuantity()) {
            model.setPos(cexPosition.getQuantity());
        }

        // 杠杆和保证金字段
        if (null != cexPosition.getLeverage()) {
            model.setLever(cexPosition.getLeverage());
        }
        if (null != cexPosition.getMargin()) {
            model.setMargin(cexPosition.getMargin());
        }
        if (null != cexPosition.getMarginRatio()) {
            model.setMgnRatio(cexPosition.getMarginRatio());
        }
        if (null != cexPosition.getMarginMode()) {
            model.setMgnMode(cexPosition.getMarginMode().name());
        }

        // 盈亏字段
        if (null != cexPosition.getUnrealizedPnl()) {
            model.setUpl(cexPosition.getUnrealizedPnl());
        }
        if (null != cexPosition.getUnrealizedPnlRatio()) {
            model.setUplRatio(cexPosition.getUnrealizedPnlRatio());
        }
        if (null != cexPosition.getRealizedPnl()) {
            model.setRealizedPnl(cexPosition.getRealizedPnl());
        }

        // 其他字段
        if (null != cexPosition.getLiquidationPrice()) {
            model.setLiqPx(cexPosition.getLiquidationPrice());
        }
        if (null != cexPosition.getNotionalValue()) {
            model.setNotionalUsd(cexPosition.getNotionalValue());
        }
        if (null != cexPosition.getCreateTime()) {
            model.setCTime(cexPosition.getCreateTime());
        }
        if (null != cexPosition.getCurrency()) {
            model.setCcy(cexPosition.getCurrency());
        }
        if (null != cexPosition.getPosId()) {
            model.setPosId(cexPosition.getPosId());
        }
        if (null != cexPosition.getFundingFee()) {
            model.setFundingFee(cexPosition.getFundingFee());
        }
        if (null != cexPosition.getFee()) {
            model.setFee(cexPosition.getFee());
        }

        // 算法订单字段(重要!)
        // CexPositionAdapter.adapt()已经正确适配了closeOrderAlgo字段
        // 这里直接使用适配后的结果
        List<CexAlgoOrder> closeOrderAlgo = cexPosition.getCloseOrderAlgo();
        if (!CollectionUtils.isEmpty(closeOrderAlgo)) {
            model.setCloseOrderAlgo(closeOrderAlgo);
        } else {
            model.setCloseOrderAlgo(List.of());
        }

        return model;
    }

    /**
     * 批量转换CexPosition列表为PositionModel列表
     * <p>
     * 统一的批量转换方法,避免在多处重复编写转换逻辑。
     * </p>
     *
     * @param cexPositions CEX持仓对象列表
     * @return PositionModel列表
     */
    public static List<PositionModel> toPositionModelList(List<CexPosition> cexPositions) {
        if (CollectionUtils.isEmpty(cexPositions)) {
            return Collections.emptyList();
        }

        return cexPositions.stream()
                .filter(Objects::nonNull)
                .map(CexPositionAdapter::toPositionModel)
                .collect(Collectors.toList());
    }
}
