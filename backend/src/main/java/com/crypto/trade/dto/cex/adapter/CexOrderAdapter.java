package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.common.OrderSide;
import com.crypto.trade.dto.cex.common.OrderStatus;
import com.crypto.trade.dto.cex.common.OrderType;
import com.crypto.trade.dto.cex.model.CexOrder;
import com.crypto.trade.dto.cex.okx.OkxOrder;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * OKX订单到通用CEX订单的适配器
 * <p>
 * 将OKX特有的订单数据结构适配为通用的CEX订单接口,
 * 实现字段映射和类型转换。
 * </p>
 *
 * @author Page
 * @since 2025-01-21
 */
public class CexOrderAdapter {

    /**
     * 将单个OKX订单适配为通用CEX订单
     *
     * @param okxOrder OKX订单对象
     * @return 通用CEX订单
     */
    public static CexOrder adapt(OkxOrder okxOrder) {
        if (null == okxOrder) {
            return null;
        }

        return new CexOrder() {
            @Override
            public String getOrderId() {
                return okxOrder.getOrdId();
            }

            @Override
            public String getClientOrderId() {
                return okxOrder.getClOrdId();
            }

            @Override
            public String getSymbol() {
                // OKX的instId映射为通用的symbol
                return okxOrder.getInstId();
            }

            @Override
            public OrderSide getSide() {
                // OKX的"buy"/"sell"映射为枚举
                return adaptOrderSide(okxOrder.getSide());
            }

            @Override
            public OrderType getOrderType() {
                // OKX订单类型映射
                return adaptOrderType(okxOrder.getOrdType());
            }

            @Override
            public OrderStatus getStatus() {
                // OKX状态映射: live->new, partially_filled->partially_filled等
                return adaptOrderState(okxOrder.getState());
            }

            @Override
            public BigDecimal getPrice() {
                // OKX的px字段
                return okxOrder.getPx();
            }

            @Override
            public BigDecimal getQuantity() {
                // OKX的sz映射为quantity
                return okxOrder.getSz();
            }

            @Override
            public BigDecimal getFilledQuantity() {
                // OKX的accFillSz字段
                return okxOrder.getAccFillSz();
            }

            @Override
            public BigDecimal getAvgPrice() {
                // OKX的avgPx字段
                return okxOrder.getAvgPx();
            }

            @Override
            public BigDecimal getFee() {
                // OKX的fee字段
                return okxOrder.getFee();
            }

            @Override
            public String getFeeCurrency() {
                // OKX的feeCcy字段
                return okxOrder.getFeeCcy();
            }

            @Override
            public LocalDateTime getCreateTime() {
                // OKX的cTime是毫秒时间戳
                return convertTimestamp(okxOrder.getCTime());
            }

            @Override
            public Long getUpdateTime() {
                // OKX的uTime是毫秒时间戳
                return okxOrder.getUTime();
            }

            @Override
            public BigDecimal getLever() {
                // OKX的lever字段
                return okxOrder.getLever();
            }

            @Override
            public String getPosSide() {
                // OKX的posSide字段
                return okxOrder.getPosSide();
            }
        };
    }

    /**
     * 批量适配OKX订单列表
     *
     * @param okxOrders OKX订单列表
     * @return 通用CEX订单列表
     */
    public static List<CexOrder> adapt(List<OkxOrder> okxOrders) {
        if (CollectionUtils.isEmpty(okxOrders)) {
            return Collections.emptyList();
        }

        return okxOrders.stream()
                .filter(Objects::nonNull)
                .map(CexOrderAdapter::adapt)
                .collect(Collectors.toList());
    }

    /**
     * 反向适配:将通用CEX订单转换为OKX订单
     * <p>
     * 注意:由于CexOrder是接口,此方法只能转换基本字段。
     * 对于复杂场景,建议使用专门的Builder。
     * </p>
     *
     * @param cexOrder 通用CEX订单
     * @return OKX订单对象
     */
    public static OkxOrder adaptToOkx(CexOrder cexOrder) {
        if (null == cexOrder) {
            return null;
        }

        OkxOrder okxOrder = new OkxOrder();
        okxOrder.setOrdId(cexOrder.getOrderId());
        okxOrder.setClOrdId(cexOrder.getClientOrderId());
        okxOrder.setInstId(cexOrder.getSymbol());

        // 映射枚举到字符串
        if (null != cexOrder.getSide()) {
            okxOrder.setSide(cexOrder.getSide().name().toLowerCase());
        }
        if (null != cexOrder.getStatus()) {
            okxOrder.setState(mapOrderStatusToString(cexOrder.getStatus()));
        }
        if (null != cexOrder.getOrderType()) {
            okxOrder.setOrdType(cexOrder.getOrderType().name().toLowerCase());
        }

        okxOrder.setPx(cexOrder.getPrice());
        okxOrder.setSz(cexOrder.getQuantity());
        okxOrder.setAccFillSz(cexOrder.getFilledQuantity());
        okxOrder.setAvgPx(cexOrder.getAvgPrice());
        okxOrder.setFee(cexOrder.getFee());
        okxOrder.setFeeCcy(cexOrder.getFeeCurrency());

        // 持仓方向
        if (null != cexOrder.getPosSide()) {
            okxOrder.setPosSide(cexOrder.getPosSide());
        }

        // 转换时间
        if (null != cexOrder.getCreateTime()) {
            okxOrder.setCTime(cexOrder.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (null != cexOrder.getUpdateTime()) {
            okxOrder.setUTime(convertTimestamp(cexOrder.getUpdateTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }

        return okxOrder;
    }

    /**
     * 批量反向适配
     *
     * @param cexOrders 通用CEX订单列表
     * @return OKX订单列表
     */
    public static List<OkxOrder> adaptToOkx(List<CexOrder> cexOrders) {
        if (CollectionUtils.isEmpty(cexOrders)) {
            return Collections.emptyList();
        }

        return cexOrders.stream()
                .filter(Objects::nonNull)
                .map(CexOrderAdapter::adaptToOkx)
                .collect(Collectors.toList());
    }

    /**
     * 将OrderStatus枚举映射为OKX状态字符串
     */
    private static String mapOrderStatusToString(OrderStatus status) {
        if (null == status) {
            return "unknown";
        }

        return switch (status) {
            case LIVE -> "live";
            case PARTIALLY_FILLED -> "partially_filled";
            case FILLED -> "filled";
            case CANCELED -> "canceled";
            case CANCELING -> "canceling";
            default -> "unknown";
        };
    }

    /**
     * 适配订单方向(公共方法)
     */
    public static OrderSide adaptOrderSide(String side) {
        if (null == side || side.trim().isEmpty()) {
            return OrderSide.UNKNOWN;
        }

        return switch (side.toLowerCase()) {
            case "buy" -> OrderSide.BUY;
            case "sell" -> OrderSide.SELL;
            default -> OrderSide.UNKNOWN;
        };
    }

    /**
     * 适配订单类型(公共方法)
     */
    public static OrderType adaptOrderType(String ordType) {
        if (null == ordType || ordType.trim().isEmpty()) {
            return OrderType.UNKNOWN;
        }

        String normalizedType = ordType.toLowerCase();
        return switch (normalizedType) {
            case "limit" -> OrderType.LIMIT;
            case "market" -> OrderType.MARKET;
            case "conditional" -> OrderType.CONDITIONAL;
            case "post_only" -> OrderType.POST_ONLY;
            case "oco" -> OrderType.OCO;
            default -> OrderType.UNKNOWN;
        };
    }

    /**
     * 适配订单状态(公共方法)
     */
    public static OrderStatus adaptOrderState(String state) {
        if (null == state || state.trim().isEmpty()) {
            return OrderStatus.UNKNOWN;
        }

        return switch (state.toLowerCase()) {
            case "live" -> OrderStatus.LIVE;
            case "partially_filled" -> OrderStatus.PARTIALLY_FILLED;
            case "filled" -> OrderStatus.FILLED;
            case "canceled" -> OrderStatus.CANCELED;
            case "canceling" -> OrderStatus.CANCELING;
            default -> OrderStatus.UNKNOWN;
        };
    }

    /**
     * 转换时间戳(毫秒→LocalDateTime)
     */
    private static LocalDateTime convertTimestamp(Long timestamp) {
        if (null == timestamp) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        );
    }
}
