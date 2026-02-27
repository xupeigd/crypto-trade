package com.crypto.trade.service.cex;

import com.crypto.trade.dto.cex.model.*;
import com.crypto.trade.dto.cex.request.*;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.crypto.trade.dto.cex.response.CexAlgoOrderResponse;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import com.crypto.trade.entity.ApiKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UnifiedCexApiService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class UnifiedCexApiService {

    @Autowired
    private CexApiFactory factory;

    // ==================== 账户相关 ====================

    /**
     * 获取账户余额(通用CEX接口)
     */
    public List<CexAccountBalance> getAccountBalance(ApiKey apiKey, String ccy) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getAccountBalance(apiKey, ccy);
    }

    /**
     * 获取持仓信息(通用CEX接口)
     */
    public List<CexPosition> getPositions(ApiKey apiKey, String instType) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getPositions(apiKey, instType);
    }

    /**
     * 获取历史持仓(通用CEX接口)
     */
    public List<CexPosition> getPositionsHistory(ApiKey apiKey, String instType, String instId,
                                                 String after, String before, Integer limit) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getPositionsHistory(apiKey, instType, instId, after, before, limit);
    }

    // ==================== 订单交易相关 ====================

    /**
     * 下单(通用CEX接口)
     */
    public CexOrderResponse placeOrder(ApiKey apiKey, CexPlaceOrderRequest request) throws Exception {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.placeOrder(apiKey, request);
    }

    /**
     * 撤单(通用CEX接口)
     */
    public CexOperationResponse cancelOrder(ApiKey apiKey, CexCancelOrderRequest request) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.cancelOrder(apiKey, request);
    }

    /**
     * 平仓(通用CEX接口)
     */
    public CexOperationResponse closePosition(ApiKey apiKey, CexClosePositionRequest request) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.closePosition(apiKey, request);
    }

    /**
     * 获取历史订单(通用CEX接口)
     */
    public List<CexOrder> getHistoryOrders(ApiKey apiKey, String instType, String instId, String state) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getHistoryOrders(apiKey, instType, instId, state);
    }

    /**
     * 获取待成交订单(通用CEX接口)
     */
    public List<CexOrder> getPendingOrders(ApiKey apiKey, String instType, String instId) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getPendingOrders(apiKey, instType, instId);
    }

    // ==================== 算法订单相关 (通用CEX接口) ====================

    /**
     * 创建算法订单(通用CEX接口)
     */
    public CexAlgoOrderOperationResponse setAlgoOrder(ApiKey apiKey, CexAlgoOrderRequest request) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.setAlgoOrder(apiKey, request);
    }

    /**
     * 修改算法订单(通用CEX接口)
     */
    public CexAlgoOrderOperationResponse amendAlgoOrder(ApiKey apiKey, CexAmendAlgoOrderRequest request) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.amendAlgoOrder(apiKey, request);
    }

    /**
     * 取消算法订单(通用CEX接口)
     */
    public CexAlgoOrderOperationResponse cancelAlgoOrder(ApiKey apiKey, CexCancelAlgoOrderRequest request) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.cancelAlgoOrder(apiKey, request);
    }

    /**
     * 获取算法订单列表(通用CEX接口)
     */
    public CexAlgoOrderResponse getAlgoOrders(ApiKey apiKey, String instType, String instId,
                                              String algoId, String clientAlgoOrderId) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getAlgoOrders(apiKey, instType, instId, algoId, clientAlgoOrderId);
    }

    // ==================== 市场数据相关 (CEX通用方法) ====================

    /**
     * 获取合约信息(CEX通用方法)
     */
    public List<CexInstrument> getInstruments(ApiKey apiKey, String instType, String instId) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getInstruments(apiKey, instType, instId);
    }

    /**
     * 获取标记价格(CEX通用方法)
     */
    public List<CexMarkPrice> getMarkPrice(ApiKey apiKey, String instId) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getMarkPrice(apiKey, instId);
    }

    /**
     * 获取市场行情(CEX通用方法)
     */
    public List<CexMarketTicker> getMarketTickers(ApiKey apiKey, String instType, String instId) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getMarketTickers(apiKey, instType, instId);
    }

    /**
     * 获取资金费率(CEX通用方法)
     */
    public List<CexFundingRate> getFundingRate(ApiKey apiKey, String instId) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getFundingRate(apiKey, instId);
    }

    /**
     * 获取K线数据(CEX通用方法)
     */
    public List<CexMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit) {
        return getMarketCandles(apiKey, instId, period, limit, null);
    }

    /**
     * 获取K线数据(CEX通用方法，支持指定开始时间)
     */
    public List<CexMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit, Long startMills) {
        CexApiService service = factory.getCexApiService(apiKey);
        return service.getMarketCandles(apiKey, instId, period, limit, startMills);
    }


    // ==================== 通用工具方法 ====================

}
