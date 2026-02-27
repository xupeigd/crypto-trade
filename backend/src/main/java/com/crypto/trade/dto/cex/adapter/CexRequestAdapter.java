package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.okx.*;
import com.crypto.trade.dto.cex.request.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通用请求到OKX请求的适配器
 * <p>
 * 提供通用CEX请求类型到OKX特定请求类型的转换。
 * 支持普通订单和算法订单的适配。
 * </p>
 *
 * @author Page
 * @since 2025-01-21
 */
public class CexRequestAdapter {

    /**
     * 将通用下单请求转换为OKX下单请求
     * <p>
     * 字段映射规则:
     * <ul>
     * <li>symbol → instId</li>
     * <li>tradeMode → tdMode</li>
     * <li>currency → ccy</li>
     * <li>clientOrderId → clOrdId</li>
     * <li>side → side</li>
     * <li>orderType → ordType</li>
     * <li>quantity → sz</li>
     * <li>positionSide → posSide</li>
     * <li>price → px</li>
     * <li>leverage → lever</li>
     * <li>takeProfitPrice → attachAlgoOrds[].tpTriggerPx (止盈触发价格)</li>
     * <li>stopLossPrice → attachAlgoOrds[].slTriggerPx (止损触发价格)</li>
     * </ul>
     * </p>
     *
     * @param request 通用CEX下单请求
     * @return OKX下单请求
     */
    public static PlaceOrderReq toOkxPlaceOrderRequest(CexPlaceOrderRequest request) {
        if (null == request) {
            return null;
        }

        // 构建基础订单请求
        PlaceOrderReq.PlaceOrderReqBuilder builder = PlaceOrderReq.builder()
                .instId(request.getSymbol())
                .tdMode(request.getTradeMode())
                .ccy(request.getCurrency())
                .clOrdId(request.getClientOrderId())
                .side(request.getSide())
                .ordType(request.getOrderType())
                .sz(request.getQuantity())
                .posSide(request.getPositionSide())
                .px(request.getPrice())
                .lever(request.getLeverage());

        // 处理止盈止损参数（通过attachAlgoOrds字段）
        if (request.getTakeProfitPrice() != null || request.getStopLossPrice() != null) {
            PlaceOrderReq.AlgoOrder algoOrder = PlaceOrderReq.AlgoOrder.builder()
                    .instId(request.getSymbol())
                    .side(request.getSide())
                    .posSide(request.getPositionSide())
                    .tdMode(request.getTradeMode())
                    .closeFraction("1")  // 全部平仓
                    .ordType("conditional")  // 条件单
                    .cxlOnClosePos(true)  // 持仓平仓后自动取消
                    .reduceOnly(true)  // 只减仓
                    .ccy(request.getCurrency())
                    .build();

            // 设置止盈参数（使用市价单）
            if (request.getTakeProfitPrice() != null) {
                algoOrder.setTpOrdPx("-1");  // 市价单
                algoOrder.setTpTriggerPx(request.getTakeProfitPrice().toString());
            }

            // 设置止损参数（使用市价单）
            if (request.getStopLossPrice() != null) {
                algoOrder.setSlOrdPx("-1");  // 市价单
                algoOrder.setSlTriggerPx(request.getStopLossPrice().toString());
            }

            // 将算法订单附加到主订单
            builder.attachAlgoOrds(List.of(algoOrder));
        }

        return builder.build();
    }

    /**
     * 将通用撤单请求转换为OKX撤单请求
     */
    public static OkxOrderReq toOkxCancelOrderRequest(CexCancelOrderRequest request) {
        if (null == request) {
            return null;
        }

        OkxOrderReq okxRequest = new OkxOrderReq();
        okxRequest.setInstId(request.getSymbol());
        okxRequest.setOrdId(request.getOrderId());
        // OkxOrderReq没有clOrdId字段,忽略clientOrderId
        return okxRequest;
    }

    /**
     * 将通用平仓请求转换为OKX平仓请求
     */
    public static OkxClosePositionRequest toOkxClosePositionRequest(CexClosePositionRequest request) {
        if (null == request) {
            return null;
        }
        OkxClosePositionRequest okxRequest = new OkxClosePositionRequest();
        okxRequest.setInstId(request.getSymbol());
        okxRequest.setPosSide(request.getPositionSide());
        okxRequest.setTdMode(request.getTdMode());
        okxRequest.setMgnMode(request.getMgnMode());

        // ClosePositionRequest使用sz字段,添加异常处理
        try {
            okxRequest.setSz(new BigDecimal(request.getQuantity()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("平仓数量格式错误: " + request.getQuantity(), e);
        }
        return okxRequest;
    }

    // ==================== 算法订单相关适配方法 ====================

    /**
     * 将通用算法订单创建请求转换为OKX算法订单请求
     * <p>
     * 字段映射规则:
     * <ul>
     * <li>symbol → instId</li>
     * <li>tradeMode → tdMode</li>
     * <li>currency → ccy</li>
     * <li>positionSide → posSide</li>
     * <li>orderType → ordType</li>
     * <li>quantity → sz</li>
     * <li>clientAlgoOrderId → algoClOrdId</li>
     * <li>closeFraction → closeFraction</li>
     * <li>takeProfitTriggerPrice → tpTriggerPx</li>
     * <li>takeProfitOrderPrice → tpOrdPx</li>
     * <li>stopLossTriggerPrice → slTriggerPx</li>
     * <li>stopLossOrderPrice → slOrdPx</li>
     * </ul>
     * </p>
     *
     * @param request 通用CEX算法订单创建请求
     * @return OKX算法订单请求
     */
    public static OkxAlgoRequest toOkxAlgoOrderRequest(CexAlgoOrderRequest request) {
        if (null == request) {
            return null;
        }

        return OkxAlgoRequest.builder()
                // 基础字段
                .instId(request.getSymbol())
                .tdMode(request.getTradeMode())
                .ccy(request.getCurrency())
                .side(request.getSide())
                .posSide(request.getPositionSide())
                .ordType(request.getOrderType())
                .sz(request.getQuantity())
                .tag(request.getTag())
                .tgtCcy(request.getTargetCurrency())
                .algoClOrdId(request.getClientAlgoOrderId())
                .closeFraction(request.getCloseFraction())
                .tradeQuoteCcy(request.getTradeQuoteCurrency())
                .cxlOnClosePos(request.getCancelOnClosePosition())
                .reduceOnly(request.getReduceOnly())
                // 止盈相关
                .tpTriggerPx(request.getTakeProfitTriggerPrice())
                .tpOrdPx(request.getTakeProfitOrderPrice())
                .tpTriggerPxType(request.getTakeProfitTriggerPriceType())
                // 止损相关
                .slTriggerPx(request.getStopLossTriggerPrice())
                .slOrdPx(request.getStopLossOrderPrice())
                .slTriggerPxType(request.getStopLossTriggerPriceType())
                .build();
    }

    /**
     * 将通用算法订单修改请求转换为OKX算法订单修改请求
     * <p>
     * 字段映射规则:
     * <ul>
     * <li>algoId → algoId</li>
     * <li>clientAlgoOrderId → algoClOrdId</li>
     * <li>cancelOnFail → cxlOnFail</li>
     * <li>requestId → reqId</li>
     * <li>newQuantity → newSz</li>
     * <li>newTakeProfitTriggerPrice → newTpTriggerPx</li>
     * <li>newTakeProfitOrderPrice → newTpOrdPx</li>
     * <li>newStopLossTriggerPrice → newSlTriggerPx</li>
     * <li>newStopLossOrderPrice → newSlOrdPx</li>
     * <li>newTakeProfitTriggerPriceType → newTpTriggerPxType</li>
     * <li>newStopLossTriggerPriceType → newSlTriggerPxType</li>
     * <li>newOrderType → newOrdType</li>
     * </ul>
     * </p>
     *
     * @param request 通用CEX算法订单修改请求
     * @return OKX算法订单修改请求
     */
    public static OkxAmendAlgoRequest toOkxAmendAlgoOrderRequest(CexAmendAlgoOrderRequest request) {
        if (null == request) {
            return null;
        }

        return OkxAmendAlgoRequest.builder()
                .instId(request.getSymbol())
                .algoId(request.getAlgoId())
                .algoClOrdId(request.getClientAlgoOrderId())
                .cxlOnFail(request.getCancelOnFail())
                .reqId(request.getRequestId())
                .newSz(request.getNewQuantity())
                .newTpTriggerPx(request.getNewTakeProfitTriggerPrice())
                .newTpOrdPx(request.getNewTakeProfitOrderPrice())
                .newSlTriggerPx(request.getNewStopLossTriggerPrice())
                .newSlOrdPx(request.getNewStopLossOrderPrice())
                .newTpTriggerPxType(request.getNewTakeProfitTriggerPriceType())
                .newSlTriggerPxType(request.getNewStopLossTriggerPriceType())
                .newOrdType(request.getNewOrderType())
                .build();
    }

    /**
     * 将通用算法订单取消请求转换为OKX算法订单取消请求
     * <p>
     * 字段映射规则:
     * <ul>
     * <li>algoId → algoId</li>
     * <li>symbol → instId</li>
     * <li>clientAlgoOrderId → 忽略(OKX不支持)</li>
     * </ul>
     * </p>
     *
     * @param request 通用CEX算法订单取消请求
     * @return OKX算法订单取消请求
     */
    public static OkxAlgoCancelRequest toOkxCancelAlgoOrderRequest(CexCancelAlgoOrderRequest request) {
        if (null == request) {
            return null;
        }

        return OkxAlgoCancelRequest.builder()
                .algoId(request.getAlgoId())
                .instId(request.getSymbol())
                .build();
    }
}
