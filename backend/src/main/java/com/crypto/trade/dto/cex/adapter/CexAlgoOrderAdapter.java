package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexAlgoOrder;
import com.crypto.trade.dto.cex.okx.OkxAlgoOrder;
import com.crypto.trade.dto.cex.okx.OkxAlgoState;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * CexAlgoOrderAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexAlgoOrderAdapter {

    public static CexAlgoOrder adapt(OkxAlgoOrder okxAlgoOrder) {
        if (null == okxAlgoOrder) {
            return null;
        }

        return new CexAlgoOrder() {

            @JsonIgnore
            final OkxAlgoOrder orginal = okxAlgoOrder;

            @Override
            public String getAlgoId() {
                return orginal.getAlgoId();
            }

            @Override
            public String getOrderId() {
                return orginal.getAlgoId();
            }

            @Override
            public String getSymbol() {
                return orginal.getInstId();
            }

            @Override
            public com.crypto.trade.dto.cex.common.OrderSide getSide() {
                return CexOrderAdapter.adaptOrderSide(orginal.getSide());
            }

            @Override
            public com.crypto.trade.dto.cex.common.OrderType getOrderType() {
                return CexOrderAdapter.adaptOrderType(orginal.getOrdType());
            }

            @Override
            public com.crypto.trade.dto.cex.common.OrderStatus getStatus() {
                return CexOrderAdapter.adaptOrderState(orginal.getState());
            }

            @Override
            public BigDecimal getTriggerPrice() {
                return orginal.getTriggerPx();
            }

            @Override
            public BigDecimal getTpTriggerPx() {
                return orginal.getTpTriggerPx();
            }

            @Override
            public BigDecimal getSlTriggerPx() {
                return orginal.getSlTriggerPx();
            }

            @Override
            public BigDecimal getOrderPrice() {
                return orginal.getOrdPx();
            }

            @Override
            public BigDecimal getQuantity() {
                return orginal.getSz();
            }

            @Override
            public LocalDateTime getCreateTime() {
                Long cTime = orginal.getCTime();
                if (null == cTime) {
                    return null;
                }
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(cTime), ZoneId.systemDefault());
            }

            @Override
            public String getPosSide() {
                return orginal.getPosSide();
            }

        };
    }

    public static List<CexAlgoOrder> adapt(List<OkxAlgoOrder> okxAlgoOrders) {
        if (CollectionUtils.isEmpty(okxAlgoOrders)) {
            return Collections.emptyList();
        }
        return okxAlgoOrders.stream()
                .filter(Objects::nonNull)
                .map(CexAlgoOrderAdapter::adapt)
                .collect(Collectors.toList());
    }

    /**
     * 将OKX算法订单状态响应转换为通用CEX算法订单操作响应
     * <p>
     * 字段映射规则:
     * <ul>
     * <li>algoId → algoId</li>
     * <li>algoClOrdId → clientAlgoOrderId</li>
     * <li>ordId → orderId</li>
     * <li>sCode → errorCode</li>
     * <li>sMsg → errorMessage</li>
     * <li>成功判断: sCode == "0"</li>
     * </ul>
     * </p>
     *
     * @param okxResponse OKX算法订单状态响应
     * @return 通用CEX算法订单操作响应
     */
    public static CexAlgoOrderOperationResponse toOperationResponse(OkxAlgoState.OkxAlgoStateResponse okxResponse) {
        if (null == okxResponse) {
            return CexAlgoOrderOperationResponse.builder()
                    .success(false)
                    .errorCode("NULL_RESPONSE")
                    .errorMessage("OKX响应为空")
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        // 提取第一个数据项(算法订单操作响应通常只返回一条数据)
        OkxAlgoState algoState = null;
        if (!CollectionUtils.isEmpty(okxResponse.getData())) {
            algoState = okxResponse.getData().get(0);
        }

        // 判断是否成功 (sCode == "0" 表示成功)
        boolean success = "0".equals(okxResponse.getCode());

        return CexAlgoOrderOperationResponse.builder()
                .algoId(null != algoState ? algoState.getAlgoId() : null)
                .clientAlgoOrderId(null != algoState ? algoState.getAlgoClOrdId() : null)
                .orderId(null != algoState ? algoState.getOrdId() : null)
                .success(success)
                .errorCode(okxResponse.getCode())
                .errorMessage(okxResponse.getMsg())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
