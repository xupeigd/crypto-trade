package com.crypto.trade.service.cex;

import com.crypto.trade.dto.cex.model.*;
import com.crypto.trade.dto.cex.request.*;
import com.crypto.trade.dto.cex.response.CexAlgoOrderOperationResponse;
import com.crypto.trade.dto.cex.response.CexAlgoOrderResponse;
import com.crypto.trade.dto.cex.response.CexOperationResponse;
import com.crypto.trade.dto.cex.response.CexOrderResponse;
import com.crypto.trade.entity.ApiKey;

import java.util.List;

/**
 * CEX API统一接口
 * <p>
 * 定义所有中心化交易所(CEX)的通用API接口,支持多交易所实现。
 * 所有方法返回通用CEX Model,不暴露特定交易所类型。
 * </p>
 * <p>
 * 当前支持的交易所:
 * - OKX (已实现)
 * - Binance (预留)
 * - Bybit (预留)
 * </p>
 * <p>
 * <b>架构说明:</b>
 * <ul>
 * <li>此接口为100%纯通用CEX接口,不包含任何交易所特定类型</li>
 * <li>算法订单现已纳入通用接口,支持多交易所实现</li>
 * <li>实现类直接实现此接口,内部完成适配转换</li>
 * </ul>
 * </p>
 *
 * @author Page
 * @since 2025-01-21
 */
public interface CexApiService {

    // ==================== 账户相关 ====================

    /**
     * 获取账户余额
     */
    List<CexAccountBalance> getAccountBalance(ApiKey apiKey, String ccy);

    /**
     * 获取持仓信息
     */
    List<CexPosition> getPositions(ApiKey apiKey, String instType);

    /**
     * 获取历史持仓
     */
    List<CexPosition> getPositionsHistory(ApiKey apiKey, String instType, String instId,
                                          String after, String before, Integer limit);

    // ==================== 订单交易相关 ====================

    /**
     * 下单
     */
    CexOrderResponse placeOrder(ApiKey apiKey, CexPlaceOrderRequest request) throws Exception;

    /**
     * 撤单
     */
    CexOperationResponse cancelOrder(ApiKey apiKey, CexCancelOrderRequest request);

    /**
     * 平仓
     */
    CexOperationResponse closePosition(ApiKey apiKey, CexClosePositionRequest request);

    /**
     * 获取历史订单(通用CEX接口)
     */
    List<CexOrder> getHistoryOrders(ApiKey apiKey, String instType, String instId, String state);

    /**
     * 获取待成交订单(通用CEX接口)
     */
    List<CexOrder> getPendingOrders(ApiKey apiKey, String instType, String instId);

    // ==================== 市场数据相关 (通用CEX接口) ====================

    /**
     * 获取合约信息(通用CEX接口)
     */
    List<CexInstrument> getInstruments(ApiKey apiKey, String instType, String instId);

    /**
     * 获取标记价格(通用CEX接口)
     */
    List<CexMarkPrice> getMarkPrice(ApiKey apiKey, String instId);

    /**
     * 获取市场行情(Ticker)(通用CEX接口)
     */
    List<CexMarketTicker> getMarketTickers(ApiKey apiKey, String instType, String instId);

    /**
     * 获取资金费率(通用CEX接口)
     */
    List<CexFundingRate> getFundingRate(ApiKey apiKey, String instId);

    /**
     * 获取K线数据(通用CEX接口)
     */
    List<CexMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit);

    /**
     * 获取K线数据(通用CEX接口，支持指定开始时间)
     *
     * @param apiKey     API密钥信息
     * @param instId     合约ID
     * @param period     时间周期(如 "5m", "1H", "1D")
     * @param limit      数据条数限制
     * @param startMills 开始时间戳(毫秒)，可选
     * @return K线数据列表
     */
    List<CexMarketCandle> getMarketCandles(ApiKey apiKey, String instId, String period, Integer limit, Long startMills);

    // ==================== 算法订单相关 (通用CEX接口) ====================

    /**
     * 创建算法订单(通用CEX接口)
     * <p>
     * 支持止盈止损、OCO订单等算法订单类型。
     * 不同交易所的算法订单机制可能不同,实现类需完成适配。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 算法订单创建请求
     * @return 算法订单操作响应
     */
    CexAlgoOrderOperationResponse setAlgoOrder(ApiKey apiKey, CexAlgoOrderRequest request);

    /**
     * 修改算法订单(通用CEX接口)
     * <p>
     * 修改已创建的算法订单参数,如触发价格、委托价格等。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 算法订单修改请求
     * @return 算法订单操作响应
     */
    CexAlgoOrderOperationResponse amendAlgoOrder(ApiKey apiKey, CexAmendAlgoOrderRequest request);

    /**
     * 取消算法订单(通用CEX接口)
     * <p>
     * 取消已创建的算法订单。
     * </p>
     *
     * @param apiKey  API密钥信息
     * @param request 算法订单取消请求
     * @return 算法订单操作响应
     */
    CexAlgoOrderOperationResponse cancelAlgoOrder(ApiKey apiKey, CexCancelAlgoOrderRequest request);

    /**
     * 获取算法订单列表(通用CEX接口)
     * <p>
     * 查询已创建的算法订单信息,包括止盈止损、条件单等。
     * </p>
     *
     * @param apiKey            API密钥信息
     * @param instType          产品类型 (SPOT/SWAP/FUTURES/OPTION)
     * @param instId            产品ID (如 BTC-USDT-SWAP)
     * @param algoId            算法订单ID (可选)
     * @param clientAlgoOrderId 客户端自定义算法订单ID (可选)
     * @return 算法订单响应
     */
    CexAlgoOrderResponse getAlgoOrders(ApiKey apiKey, String instType, String instId,
                                       String algoId, String clientAlgoOrderId);

}
