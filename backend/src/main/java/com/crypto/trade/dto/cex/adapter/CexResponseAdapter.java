package com.crypto.trade.dto.cex.adapter;

import com.crypto.trade.dto.cex.model.CexOrder;
import com.crypto.trade.dto.cex.okx.OkxAlgoState;
import com.crypto.trade.dto.cex.okx.OkxApiResponse;
import com.crypto.trade.dto.cex.okx.OkxOrderResponseData;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import com.crypto.trade.util.JsonUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CexResponseAdapter
 * 适配器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public class CexResponseAdapter {

    /**
     * 将OKX订单响应适配为通用订单响应
     */
    public static CexOrderResponse toCexOrderResponse(OkxOrderResponseData okxResponse) {
        if (null == okxResponse) {
            return null;
        }

        CexOrderResponse response = new CexOrderResponse();
        response.setSuccess(okxResponse.isSuccess());
        response.setTimestamp(System.currentTimeMillis());

        if (okxResponse.isFailure()) {
            response.setErrorCode(okxResponse.getErrorCode());
            response.setErrorMessage(okxResponse.getErrorMessage());
        }

        return response;
    }

    /**
     * 将OKX算法订单响应适配为通用订单响应
     * <p>
     * 用于placeOrder的响应转换
     * </p>
     */
    public static CexOrderResponse toCexOrderResponse(OkxAlgoState.OkxAlgoStateResponse okxResponse) {
        if (null == okxResponse) {
            return null;
        }

        CexOrderResponse response = new CexOrderResponse();
        response.setRawData(JsonUtils.toJsonString(okxResponse));
        response.setSuccess("0".equals(okxResponse.getCode()));
        response.setTimestamp(Long.parseLong(okxResponse.getOutTime()));

        if (!"0".equals(okxResponse.getCode())) {
            response.setErrorCode(okxResponse.getCode());
            response.setErrorMessage(okxResponse.getMsg());
        }

        if (!CollectionUtils.isEmpty(okxResponse.getData())) {
            response.setOrders(okxResponse.getData().stream()
                    .map(v -> new CexOrder() {

                        final OkxAlgoState orginal = v;

                        @Override
                        public String getOrderId() {
                            return orginal.getOrdId();
                        }

                        @Override
                        public String getClientOrderId() {
                            return orginal.getClOrdId();
                        }

                        @Override
                        public String getCode() {
                            return orginal.getSCode();
                        }

                        @Override
                        public String getMsg() {
                            return orginal.getSMsg();
                        }

                        @Override
                        public Long getUpdateTime() {
                            return Long.parseLong(orginal.getTs());
                        }
                    })
                    .collect(Collectors.toList()));
        }

        return response;
    }

    /**
     * 将OKX算法订单响应适配为通用操作响应
     * <p>
     * 用于cancelOrder的响应转换
     * </p>
     */
    public static CexOperationResponse toCexOperationResponse(OkxAlgoState.OkxAlgoStateResponse okxResponse) {
        if (null == okxResponse) {
            return null;
        }

        CexOperationResponse response = new CexOperationResponse();
        response.setSuccess("0".equals(okxResponse.getCode()));
        response.setTimestamp(System.currentTimeMillis());

        if (!"0".equals(okxResponse.getCode())) {
            response.setErrorCode(okxResponse.getCode());
            response.setErrorMessage(okxResponse.getMsg());
        }

        return response;
    }

    /**
     * 将OKX API响应适配为通用操作响应
     * <p>
     * 用于closePosition的响应转换
     * </p>
     */
    public static CexOperationResponse toCexOperationResponse(OkxApiResponse<Void> okxResponse) {
        if (null == okxResponse) {
            return null;
        }

        CexOperationResponse response = new CexOperationResponse();
        response.setSuccess("0".equals(okxResponse.getCode()));
        response.setTimestamp(System.currentTimeMillis());

        if (!"0".equals(okxResponse.getCode())) {
            response.setErrorCode(okxResponse.getCode());
            response.setErrorMessage(okxResponse.getMsg());
        }

        return response;
    }

    /**
     * 批量适配
     */
    public static List<CexOrderResponse> toCexOrderResponseList(List<OkxOrderResponseData> okxResponses) {
        if (CollectionUtils.isEmpty(okxResponses)) {
            return List.of();
        }

        return okxResponses.stream()
                .filter(Objects::nonNull)
                .map(CexResponseAdapter::toCexOrderResponse)
                .collect(Collectors.toList());
    }
}
