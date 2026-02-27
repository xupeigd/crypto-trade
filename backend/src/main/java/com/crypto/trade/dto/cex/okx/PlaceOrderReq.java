package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.request.OrderRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PlaceOrderReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderReq {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String instId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String tdMode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String ccy;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String clOrdId; // 客户自定义订单ID,使用TradingOrder.orderUuid

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String side;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String ordType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String sz;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String posSide;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String px;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String lever;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<AlgoOrder> attachAlgoOrds;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String closeFraction;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean reduceOnly;

    public static PlaceOrderReq from(OrderRequest req) {

        PlaceOrderReqBuilder builder = PlaceOrderReq.builder();
        builder.instId(req.instId);
        builder.tdMode(req.tdMode);
        builder.ccy(req.ccy);
        builder.side(req.side);
        builder.ordType(req.orderType);
        builder.sz(req.sz.toString());
        if (null != req.posSide) {
            builder.posSide(req.posSide);
        }
        if (null != req.px) {
            builder.px(req.px.toString());
        }
        if (null != req.lever) {
            builder.lever(req.lever.toString());
        }
        if (null != req.takeProfitPrice || null != req.stopLossPrice) {
            AlgoOrder.AlgoOrderBuilder algoOrderBuilder = AlgoOrder.builder();
            algoOrderBuilder.instId(req.instId);
            algoOrderBuilder.side(req.side);
            algoOrderBuilder.posSide(req.posSide);
            algoOrderBuilder.tdMode(req.tdMode);
            algoOrderBuilder.closeFraction("1");
            algoOrderBuilder.ordType("conditional");
            // 添加其他必需参数
            algoOrderBuilder.cxlOnClosePos(true);
            algoOrderBuilder.reduceOnly(true);
            algoOrderBuilder.ccy("USDT");
            if (null != req.takeProfitPrice) {
                algoOrderBuilder.tpOrdPx("-1");
                algoOrderBuilder.tpTriggerPx(String.valueOf(req.takeProfitPrice));
            }
            if (null != req.stopLossPrice) {
                algoOrderBuilder.slOrdPx("-1");
                algoOrderBuilder.slTriggerPx(String.valueOf(req.stopLossPrice));
            }
            builder.attachAlgoOrds(List.of(algoOrderBuilder.build()));
        }
        return builder.build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static final class AlgoOrder {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String instId;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String side;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String posSide;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String tdMode;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String closeFraction;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String ordType;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean cxlOnClosePos;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean reduceOnly;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String ccy;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String tpOrdPx;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String tpTriggerPx;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String slOrdPx;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String slTriggerPx;

    }

}
