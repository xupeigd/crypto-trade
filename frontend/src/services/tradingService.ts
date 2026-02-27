import axios from 'axios';
import {PositionModel} from '../types/okxPosition';
import {ApiResponse} from '../types/common';

// API基础配置
const API_BASE_URL = '';

// 请求去重管理器
class RequestDeduplicator {
    private pendingRequests = new Map<string, Promise<any>>();
    private cache = new Map<string, { data: any; timestamp: number }>();
    private readonly cacheDuration = 3000; // 3秒缓存

    // 去重请求执行器
    async execute<T>(config: any, requestFn: () => Promise<T>): Promise<T> {
        const key = this.generateKey(config);

        // 检查缓存（仅对GET请求）
        if (config.method?.toUpperCase() === 'GET' || !config.method) {
            const cachedData = this.checkCache(key);
            if (cachedData) {
                return cachedData;
            }
        }

        // 检查是否有相同请求正在进行
        if (this.pendingRequests.has(key)) {
            console.log('⏸️ 请求去重，等待相同请求完成:', key);
            return this.pendingRequests.get(key) as Promise<T>;
        }

        // 执行新请求
        console.log('🔄 发起新的API请求:', key);
        const promise = requestFn()
            .then((response) => {
                // 缓存GET请求的响应
                if (config.method?.toUpperCase() === 'GET' || !config.method) {
                    this.setCache(key, response);
                }
                console.log('✅ API请求成功:', key);
                return response;
            })
            .catch((error) => {
                console.error('❌ API请求失败:', key, error);
                throw error;
            })
            .finally(() => {
                // 清除进行中的请求记录
                this.pendingRequests.delete(key);
            });

        this.pendingRequests.set(key, promise);
        return promise;
    }

    // 清除所有缓存
    clearCache(): void {
        this.cache.clear();
    }

    // 清除过期缓存
    cleanExpiredCache(): void {
        const now = Date.now();
        for (const [key, value] of this.cache.entries()) {
            if (now - value.timestamp >= this.cacheDuration) {
                this.cache.delete(key);
            }
        }
    }

    // 生成请求唯一键
    private generateKey(config: any): string {
        const {method, url, params, data} = config;
        return `${method?.toUpperCase() || 'GET'}-${url}-${JSON.stringify(params || {})}-${JSON.stringify(data || {})}`;
    }

    // 检查缓存
    private checkCache(key: string): any | null {
        const cached = this.cache.get(key);
        if (cached && Date.now() - cached.timestamp < this.cacheDuration) {
            console.log('💰 使用API缓存:', key);
            return cached.data;
        }
        if (cached) {
            this.cache.delete(key); // 清除过期缓存
        }
        return null;
    }

    // 设置缓存
    private setCache(key: string, data: any): void {
        this.cache.set(key, {data, timestamp: Date.now()});
    }
}

// 创建全局请求去重实例
const requestDeduplicator = new RequestDeduplicator();

// 定期清理过期缓存
setInterval(() => {
    requestDeduplicator.cleanExpiredCache();
}, 30000); // 每30秒清理一次

// 创建axios实例
const tradingApi = axios.create({
    baseURL: API_BASE_URL,
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 请求拦截器
tradingApi.interceptors.request.use(
    (config) => {
        // console.log(`API请求: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
    },
    (error) => {
        console.error('API请求错误:', error);
        return Promise.reject(error);
    }
);

// 响应拦截器
tradingApi.interceptors.response.use(
    (response) => {
        // console.log(`API响应: ${response.status} ${response.config.url}`);
        return response;
    },
    (error) => {
        console.error('API响应错误:', error);
        return Promise.reject(error);
    }
);

// 类型定义
export interface ApiKey {
    keyId: number;
    keyName: string;
    accessKey: string;
    vendor: string;
    status: string;
    isLiveTrading: boolean;
    createdTime: string;
}

// 技术指标数据接口
export interface TechnicalIndicatorData {
    success: boolean;
    message: string;
    data: IndicatorData;
}

export interface IndicatorData {
    metricName: string;
    period: number;
    standardDeviation?: number; // 仅BOLL使用
    values: IndicatorDataPoint[];
    multiPeriodValues?: IndicatorDataPointMultiPeriod[]; // 多周期数据
    createdAt: string;
}

export interface IndicatorDataPoint {
    timestamp: number;
    value?: number;        // EMA、RSI使用
    upperBand?: number;    // BOLL上轨
    middleBand?: number;   // BOLL中轨
    lowerBand?: number;    // BOLL下轨
    // 多周期支持
    multiPeriodValues?: {
        [key: string]: number;  // 例如: { ema_5: 123.45, ema_20: 122.80 }
    };

    // 索引签名支持动态属性
    [key: string]: any;
}

export interface IndicatorDataPointMultiPeriod {
    timestamp: number;
    values?: {
        period: number;
        value?: number;
    }[];
}

// 完整图表数据接口 - 匹配后端CompleteChartDataModel
export interface CompleteChartData {
    instId?: string;
    timeframe: string;
    limit: number;
    candles: MarkPriceCandle[];
    timestamp: number;
    markPrice?: number; // 当前标记价格
    indicators?: {
        [key: string]: IndicatorData;
    };
}


// 合约基础信息接口 - 匹配后端InstrumentBasicInfoModel
export interface InstrumentBasicInfo {
    instId: string;           // 合约ID
    displayName: string;      // 显示名称
    baseAsset: string;        // 基础资产
    quoteAsset: string;       // 计价资产
    category: string;         // 合约类别（现货/永续合约）
    sortOrder: number;        // 排序
}

export interface Instrument {
    instId: string;
    lastPrice: number;
    bidPrice: number;
    askPrice: number;
    open24h: number;
    high24h: number;
    low24h: number;
    volume24h: number;
    volCcy24h: number;      // 24小时交易额(币数量)
    volumeUsdt24h: number;  // 24小时USDT交易额
    changePercent: number;
    // 资金费率和标记价格相关字段
    fundingRate?: number;           // 当前资金费率
    nextFundingRate?: number;       // 下次资金费率
    nextFundingTime?: string;       // 下次资金费率时间戳
    markPrice?: number;             // 标记价格
    markPriceTimestamp?: string;    // 标记价格时间戳
}

/**
 * CEX订单详情接口
 * <p>
 * 包含所有CEX交易所特定的订单信息
 * </p>
 */
export interface CexOrderDetails {
    orderId: string;             // CEX订单ID（交易所返回的订单ID）
    exchange: string;            // 交易所（okx/binance/bybit）
    tdMode: string;              // 交易模式（isolated/cross）
    ccy: string;                 // 保证金币种
    sz: number;                  // 委托数量
    ctVal?: number;              // 合约面值（合约张数转为基础货币数量的乘数）
    ctMult?: number;             // 合约乘数
    px: number;                  // 委托价格
    orderState: string;          // CEX订单状态（live/partially_filled/filled/canceled/failed）
    avgPx: number | null;        // 成交均价
    filledSz: number;            // 已成交数量
    filledAmt: number;           // 已成交金额
    fillRatio: number;           // 成交比例（%）
    fee: number;                 // 手续费
    feeCcy: string | null;       // 手续费币种
    cTime: string | null;        // CEX订单创建时间（时间戳）
    uTime: string | null;        // CEX订单最后更新时间（时间戳）
}

/**
 * 交易订单接口
 * <p>
 * 重构说明：
 * - orderId改为orderUuid（系统订单UUID）
 * - 移除了CEX特定字段（vendor, ccy, tdMode, sz, px等）
 * - orderResult改为orderStatus
 * - 添加了cexOrder嵌套对象（包含CEX订单详情）
 * </p>
 */
export interface TradingOrder {
    id: number;
    orderUuid: string;           // 系统订单UUID（唯一标识）
    apiKeyId: number;
    instId: string;
    side: string;
    orderType: string;
    lever: number;
    posSide?: string;            // 持仓方向：long/short
    amt: number;                 // 委托金额（USDT）
    sz: number;                  // 委托数量（合约张数）
    source: string;
    orderStatus: string;         // 系统订单状态（pending/submitted/success/failed/canceling/canceled）
    errorMsg: string | null;
    createdTime: string;         // 创建时间（时间戳）
    updatedTime: string;         // 更新时间（时间戳）
    submittedTime: string | null; // 提交到CEX的时间（时间戳）
    completedTime: string | null; // 完成时间（时间戳）
    execTime: string | null;     // CEX订单执行时间（时间戳）
    cexOrder: CexOrderDetails | null;  // CEX订单详情（嵌套对象）
    takeProfitPrice?: number;
    stopLossPrice?: number;
    takeProfitPct?: number;
    stopLossPct?: number;
}

export interface OrderRequest {
    apiKeyId: number;
    instId: string;
    side: 'buy' | 'sell';
    orderType: 'market' | 'limit';
    amount: number;
    lever: number;
    sz?: number;                // 委托数量(合约张数)
    px?: number;                // 限价单价格
    takeProfitPrice?: number;   // 止盈价格
    stopLossPrice?: number;     // 止损价格
    posSide?: 'long' | 'short'; // 持仓方向：long=开多，short=开空
    source?: 'web' | 'ai' | 'bot' | 'api'; // 订单来源: web=用户手动, ai=AI自动, bot=机器人, api=接口调用
}

export interface TradingResult {
    success: boolean;
    orderId: string | null;
    message: string;
    data?: any; // 用于存储额外的响应数据，如 isManualRiskMode
    clOrdId?: string;      // 客户端订单ID
    algoId?: string;       // 算法订单ID
    sCode?: string;        // 状态码
    sMsg?: string;         // 状态消息
    isManualRiskMode?: boolean; // 是否为手动风控模式
}

// 止盈止损结果接口
export interface StopLossResult {
    success: boolean;
    message: string;
    data?: any;
    algoId?: string;       // 算法订单ID
    orderId?: string;      // 订单ID
    sCode?: string;        // 状态码
    sMsg?: string;         // 状态消息
    tpTriggerPx?: string;  // 止盈触发价格
    slTriggerPx?: string;  // 止损触发价格
}

// 平仓结果接口
export interface ClosePositionResult {
    success: boolean;
    message: string;
    data?: any;
    orderId?: string;      // 订单ID
    clOrdId?: string;      // 客户端订单ID
    sCode?: string;        // 状态码
    sMsg?: string;         // 状态消息
    instId?: string;       // 合约品种
    posSide?: string;      // 持仓方向 (long/short)
    sz?: number;           // 平仓数量
    closeType?: string;    // 平仓类型 (market/limit)
    avgPx?: string;        // 平均成交价格
    filledSz?: string;     // 成交数量
    filledAmt?: string;    // 成交金额
    fee?: string;          // 手续费
    feeCcy?: string;       // 手续费币种
}

// 全仓止盈止损策略接口
export interface TotalStopLossStrategy {
    algoId?: string;       // 算法订单ID
    apiKeyId?: number;     // API密钥ID
    instId?: string;       // 合约品种
    posSide?: string;      // 持仓方向 (long/short/net)
    side?: string;         // 订单方向 (buy/sell)
    sz?: string;           // 订单数量
    tpTriggerPx?: string;  // 止盈触发价格
    slTriggerPx?: string;  // 止损触发价格
    tpTriggerPxType?: string; // 止盈触发价格类型
    slTriggerPxType?: string; // 止损触发价格类型
    state?: string;        // 策略状态
    cTime?: Date;          // 创建时间
    uTime?: Date;          // 更新时间
    strategyType?: string; // 策略类型
    triggerCondition?: string; // 触发条件
}

export interface FundingRateResponse {
    success: boolean;
    fundingRate?: string;
    nextFundingRate?: string;
    fundingTime?: string;
    nextFundingTime?: string;
    error?: string;
}

// 实时价格接口
export interface RealtimePrice {
    instId: string;        // 合约ID
    lastPrice: number;     // 最新价格
    changePercent24H: number; // 24小时涨跌幅
    changePercent4H?: number;  // 4小时涨跌幅
    changePercent1H?: number;  // 1小时涨跌幅
    changePercent?: number; // 兼容旧字段
    updateTime: string;    // 更新时间
}

// 合约概况数据接口（不包含success字段）
export interface InstrumentOverviewData {
    instId: string;
    fundingRate?: number;
    nextFundingRate?: number;
    fundingTime?: string;
    nextFundingTime?: string;
    volatilityData?: {
        [period: string]: {
            averageVolatility?: number;
            minPrice?: number;
            maxPrice?: number;
            dataPoints?: number;
        };
    };
    message?: string;
}

export interface MarkPriceResponse {
    success: boolean;
    markPrice?: string;
    timestamp?: string;
    error?: string;
}

export interface MarkPriceCandle {
    timestamp: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume?: number;
    volumeCcy?: number;      // 交易量，数值为交易货币的数量
    volCcyQuote?: number;   // 交易量，以计价货币为单位
    confirm?: number;       // K线状态：0代表未完结，1代表已完结
}

// 当前委托订单接口
/**
 * 订单模型（与后端OrderModel保持一致）
 */
export interface OrderModel {
    ordId: string;           // 订单ID
    clOrdId: string;         // 客户自定义订单ID
    instId: string;          // 产品ID
    instType: string;        // 产品类型
    ccy: string;             // 保证金币种
    ordType: string;         // 订单类型
    side: string;            // 订单方向
    posSide: string;         // 持仓方向
    tdMode: string;          // 交易模式
    sz: string;              // 委托数量
    px: string;              // 委托价格
    accFillSz: string;       // 累计成交数量
    fillPx: string;          // 成交均价
    avgPx: string;           // 成均价
    lever: string;           // 杠杆倍数
    tpTriggerPx: string;     // 止盈触发价
    tpTriggerPxType: string; // 止盈触发类型
    tpOrdPx: string;         // 止盈委托价
    slTriggerPx: string;     // 止损触发价
    slTriggerPxType: string; // 止损触发类型
    slOrdPx: string;         // 止损委托价
    fee: string;             // 计算手续费
    feeCcy: string;          // 手续费币种
    state: string;           // 订单状态
    ctime: number;    // 订单创建时间
    uTime: number;    // 订单更新时间
    apiKeyId: number;        // API Key ID
    vendor: string;          // 交易所
}

/**
 * 兼容性接口（向后兼容）
 */
export type PendingOrder = OrderModel;

// 简单缓存机制
const cache = new Map<string, { data: any; timestamp: number }>();
const CACHE_DURATION = 30000; // 30秒缓存

// 资金费率和标记价格的API调用函数（带缓存）
const fetchWithCache = async <T>(key: string, fetcher: () => Promise<T>): Promise<T> => {
    const cached = cache.get(key);
    if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
        return cached.data;
    }

    const data = await fetcher();
    cache.set(key, {data, timestamp: Date.now()});
    return data;
};

// 创建带有去重机制的API方法
const createDeduplicatedApiMethod = <T extends any[], R>(
    apiCall: (...args: T) => Promise<R>,
    getRequestConfig: (...args: T) => { method: string; url: string; params?: any; data?: any }
) => {
    return (...args: T): Promise<R> => {
        const config = getRequestConfig(...args);
        return requestDeduplicator.execute(config, () => apiCall(...args));
    };
};

// API服务函数
export const tradingService = {
    // 获取活跃的API Key列表
    getActiveApiKeys: createDeduplicatedApiMethod(
        () => tradingApi.get<ApiResponse<ApiKey[]>>('/trading/api-keys'),
        () => ({method: 'GET', url: '/trading/api-keys'})
    ),


    // 下单
    placeOrder: (orderRequest: OrderRequest) => {
        return tradingApi.post<ApiResponse<TradingResult>>('/trading/order', orderRequest);
    },

    // 撤单
    cancelOrder: (orderId: string, apiKeyId: number, instId?: string) => {
        const requestBody = instId ? {instId} : null;
        return tradingApi.post<ApiResponse<TradingResult>>(`/trading/${apiKeyId}/cancel/${orderId}`, requestBody);
    },

    // 获取实时仓位数据
    getLivePositions: async (apiKeyId: number): Promise<ApiResponse<PositionModel[]>> => {
        const response = await tradingApi.get<ApiResponse<PositionModel[]>>(`/trading/positions/${apiKeyId}/live`);
        return response.data;
    },

    // 获取活跃订单列表
    getActiveOrders: (apiKeyId?: number) => {
        return tradingApi.get<ApiResponse<TradingOrder[]>>('/trading/orders/active', {
            params: apiKeyId ? {apiKeyId} : {}
        });
    },

    // 获取当前委托订单列表
    getPendingOrders: async (apiKeyId: number): Promise<ApiResponse<OrderModel[]>> => {
        const response = await tradingApi.get<ApiResponse<OrderModel[]>>(`/trading/orders/${apiKeyId}/pending`);
        return response.data;
    },

    // 获取历史订单列表(支持分页)
    getHistoryOrders: (apiKeyId: number, days: number = 7, page: number = 0, size: number = 20) => {
        return tradingApi.get<ApiResponse<{
            data: TradingOrder[];
            total: number;
            page: number;
            size: number;
        }>>(`/trading/orders/${apiKeyId}/history`, {
            params: {days, page, size}
        });
    },

    // 获取历史仓位数据
    getPositionsHistory: async (apiKeyId: number, params?: {
        instType?: string;
        instId?: string;
        after?: string;
        before?: string;
        limit?: number;
    }): Promise<ApiResponse<any[]>> => {
        const response = await tradingApi.get<ApiResponse<any[]>>(`/trading/positions/${apiKeyId}/history`, {
            params: params || {}
        });
        return response.data;
    },

    // 根据RecordId获取关联订单
    getOrdersByRecordId: (recordId: number) => {
        return tradingApi.get<ApiResponse<TradingOrder[]>>(`/trading/orders/by-record/${recordId}`);
    },

    // 获取合约资金费率（带缓存）
    getFundingRate: (instId: string, apiKeyId: number) => {
        return fetchWithCache(
            `funding-rate-${instId}-${apiKeyId}`,
            () => tradingApi.get<FundingRateResponse>(`/trading/funding-rate/${instId}?apiKeyId=${apiKeyId}`)
                .then(response => response.data)
        );
    },

    // 获取合约标记价格（带缓存）
    getMarkPrice: (instId: string, apiKeyId: number) => {
        return fetchWithCache(
            `mark-price-${instId}-${apiKeyId}`,
            () => tradingApi.get<MarkPriceResponse>(`/trading/mark-price/${instId}?apiKeyId=${apiKeyId}`)
                .then(response => response.data)
        );
    },

    // 获取标记价格K线数据（带缓存）
    getMarkPriceCandles: (instId: string, apiKeyId: number, period: string = '1H', limit: number = 100) => {
        return fetchWithCache(
            `mark-price-candles-${instId}-${apiKeyId}-${period}-${limit}`,
            () => tradingApi.get<MarkPriceCandle[]>(`/trading/mark-price-candles/${instId}`, {
                params: {period, limit, apiKeyId}
            }).then(response => response.data)
        );
    },

    // 获取1小时K线数据用于计算波动幅度
    getOneHourVolatility: (instId: string, apiKeyId: number) => {
        return fetchWithCache(
            `one-hour-volatility-${instId}-${apiKeyId}`,
            () => tradingApi.get<MarkPriceCandle[]>(`/trading/mark-price-candles/${instId}`, {
                params: {period: '1H', limit: 1, apiKeyId}  // 获取最近1小时的1个K线数据
            }).then(response => response.data)
        );
    },

    // 获取合约详细信息（包含资金费率、标记价格和波动幅度）
    getInstrumentDetail: async (instId: string, apiKeyId: number) => {
        try {
            // 获取基础数据
            const [fundingRate, markPrice, fiveMinCandles, fourHourCandles] = await Promise.all([
                tradingService.getFundingRate(instId, apiKeyId),
                tradingService.getMarkPrice(instId, apiKeyId),
                tradingService.getMarkPriceCandles(instId, apiKeyId, '5m', 288),  // 5分钟，获取288个数据点（24小时）
                tradingService.getMarkPriceCandles(instId, apiKeyId, '4H', 60)  // 4小时，获取60个数据点（10天）
            ]);

            // 计算平均波动幅度的函数
            const calculateAverageChange = (candles: MarkPriceCandle[]): number | null => {
                if (!candles || candles.length === 0) {
                    console.warn('K线数据为空:', candles);
                    return null;
                }

                try {
                    // 计算每个K线的涨跌幅: (最高价 - 最低价) / 开盘价 * 100
                    const changes = candles.map(candle => {
                        const open = candle.open;
                        const high = candle.high;
                        const low = candle.low;

                        if (open === 0) {
                            console.warn('K线开盘价为0:', candle);
                            return null;
                        }

                        if (isNaN(high) || isNaN(low)) {
                            console.warn('K线最高价或最低价无效:', candle);
                            return null;
                        }

                        // 计算涨跌幅: (最高价 - 最低价) / 开盘价 * 100
                        return ((high - low) / open) * 100;
                    }).filter(change => change !== null) as number[];

                    if (changes.length === 0) {
                        return null;
                    }

                    // 过滤掉涨跌幅为0的数据
                    let filteredChanges = changes.filter(change => change !== 0);

                    // 如果有足够的数据，剔除最大值和最小值
                    if (filteredChanges.length > 2) {
                        const sortedChanges = [...filteredChanges].sort((a, b) => a - b);
                        const minChange = sortedChanges[0];
                        const maxChange = sortedChanges[sortedChanges.length - 1];

                        filteredChanges = filteredChanges.filter(change =>
                            change !== minChange && change !== maxChange
                        );

                        console.log(`剔除极值: 最小值${minChange.toFixed(2)}%, 最大值${maxChange.toFixed(2)}%`);
                    }

                    if (filteredChanges.length === 0) {
                        console.warn('过滤后无有效数据');
                        return null;
                    }

                    // 计算平均值
                    const avgChange = filteredChanges.reduce((sum, change) => sum + change, 0) / filteredChanges.length;

                    console.log(`波动计算: 原始数据点数: ${changes.length}, 过滤后数据点数: ${filteredChanges.length}, 涨跌幅数组: [${filteredChanges.map(c => c.toFixed(2)).join(', ')}%], 平均涨跌幅: ${avgChange.toFixed(2)}%`);
                    return avgChange;
                } catch (error) {
                    console.error('计算平均波动幅度失败:', error);
                    return null;
                }
            };

            // 打印调试信息
            console.log('获取到的5分钟K线数据数量:', fiveMinCandles?.length);
            console.log('获取到的4小时K线数据数量:', fourHourCandles?.length);

            // 计算波动幅度
            const fiveMinAvgChange = calculateAverageChange(fiveMinCandles);
            const fourHourAvgChange = calculateAverageChange(fourHourCandles);

            console.log('计算结果 - 5分钟平均波动:', fiveMinAvgChange, '4小时平均波动:', fourHourAvgChange);

            return {
                fundingRate: fundingRate.success ? parseFloat(fundingRate.fundingRate || '0') : undefined,
                nextFundingRate: fundingRate.success ? parseFloat(fundingRate.nextFundingRate || '0') : undefined,
                fundingTime: fundingRate.success ? fundingRate.fundingTime : undefined,
                nextFundingTime: fundingRate.success ? fundingRate.nextFundingTime : undefined,
                markPrice: markPrice.success ? parseFloat(markPrice.markPrice || '0') : undefined,
                currentPrice: markPrice.success ? parseFloat(markPrice.markPrice || '0') : undefined, // 添加当前价格字段
                markPriceTimestamp: markPrice.success ? markPrice.timestamp : undefined,
                fiveMinAvgChange,
                fourHourAvgChange,
                hasError: !fundingRate.success || !markPrice.success,
                error: fundingRate.error || markPrice.error
            };
        } catch (error) {
            console.error('获取合约详细信息失败:', error);
            return {
                fundingRate: undefined,
                nextFundingRate: undefined,
                fundingTime: undefined,
                nextFundingTime: undefined,
                markPrice: undefined,
                currentPrice: undefined,
                markPriceTimestamp: undefined,
                fiveMinAvgChange: undefined,
                fourHourAvgChange: undefined,
                hasError: true,
                error: error instanceof Error ? error.message : '获取详细信息失败'
            };
        }
    },

    // 获取TopN交易额合约（不包含价格信息，仅用于下拉选择）
    getTopNInstruments: (count: number, apiKeyId: number) => {
        // 构造查询参数，count和apiKeyId都是必传
        const params = {count, apiKeyId};
        return tradingApi.get<ApiResponse<InstrumentBasicInfo[]>>('/trading/instruments/topn', {params})
            .then(response => {
                // 检查API响应结构
                if (response.data && response.data.success && response.data.data) {
                    return response.data.data;
                } else {
                    throw new Error(response.data?.message || '获取TopN合约失败');
                }
            });
    },

    // 获取合约概况信息（资金费率和多周期波动率）
    getInstrumentOverview: (instId: string, apiKeyId: number, periods?: string[]): Promise<InstrumentOverviewData> => {
        const params = new URLSearchParams();
        params.append('apiKeyId', apiKeyId.toString());
        if (periods && periods.length > 0) {
            periods.forEach(period => {
                params.append('periods', period);
            });
        }

        const url = `/trading/instruments/${instId}/overview?${params.toString()}`;

        return fetchWithCache<InstrumentOverviewData>(
            `instrument-overview-${instId}-${apiKeyId}-${periods?.join(',') || 'default'}`,
            async () => {
                const response = await tradingApi.get<ApiResponse<InstrumentOverviewData>>(url);

                // 适配新的后端结构：ApiResponse<InstrumentOverviewData>
                if (response.data && response.data.success && response.data.data) {
                    return response.data.data;
                }
                // 如果后端还没有完全迁移到ApiResponse结构，保持兼容性
                if (response.data && 'instId' in response.data && response.data.instId) {
                    return response.data as InstrumentOverviewData;
                }
                throw new Error('获取合约概况失败：无效的响应数据');
            }
        );
    },

    // 获取选中合约的实时价格和涨跌幅
    getRealtimePrice: createDeduplicatedApiMethod(
        (instId: string, apiKeyId: number) => tradingApi.get<ApiResponse<RealtimePrice>>(`/trading/instruments/${instId}/realtime-price?apiKeyId=${apiKeyId}`)
            .then(response => response.data),
        (instId: string, apiKeyId: number) => ({
            method: 'GET',
            url: `/trading/instruments/${instId}/realtime-price?apiKeyId=${apiKeyId}`
        })
    ),

    // 设置止盈止损条件订单
    setStopLossOrder: (stopLossConfig: {
        apiKeyId: number;
        instId: string;
        side: 'buy' | 'sell';
        posSide: 'long' | 'short';
        sz: number;
        tpTriggerPx?: string;
        tpTriggerPxType?: '1' | '2';
        tpOrderType?: 'limit' | 'market';
        tpTriggerPxMode?: '1' | '2';
        slTriggerPx?: string;
        slTriggerPxType?: '1' | '2';
        slOrderType?: 'limit' | 'market';
        slTriggerPxMode?: '1' | '2';
    }) => {
        return tradingApi.post<ApiResponse<StopLossResult>>('/trading/stop-loss', stopLossConfig);
    },

    // 市价平仓
    closePosition: (closeConfig: {
        apiKeyId: number;
        instId: string;
        posSide: 'long' | 'short';
        sz: number;
        mgnMode?: string;
    }) => {
        return tradingApi.post<ApiResponse<ClosePositionResult>>('/trading/close-position', closeConfig);
    },

    // 查询全仓止盈止损策略
    // getTotalStopLossStrategies: (params: {
    //     apiKeyId: number;
    //     instId: string;
    // }) => {
    //     return tradingApi.get<ApiResponse<TotalStopLossStrategy[]>>('/trading/total-stop-loss-strategies', {params});
    // },

    // 设置全仓止盈止损
    setTotalStopLoss: (config: {
        apiKeyId: number;
        instId: string;
        posSide: 'long' | 'short';
        side: 'buy' | 'sell';
        tpTriggerPx?: string;
        slTriggerPx?: string;
        tpTriggerPxType?: '1' | '2';
        slTriggerPxType?: '1' | '2';
        tpTriggerPxMode?: string;
        slTriggerPxMode?: string;
        sz: string;
        algoId?: string;
        mgnMode?: string;
    }) => {
        return tradingApi.post<ApiResponse<StopLossResult>>('/trading/total-stop-loss', config);
    },

    // 修改全仓止盈止损
    amendTotalStopLoss: (config: {
        apiKeyId: number;
        algoId: string;
        instId: string;  // 新增必需参数
        tpTriggerPx?: string;
        slTriggerPx?: string;
        tpTriggerPxType?: '1' | '2';
        slTriggerPxType?: '1' | '2';
        mgnMode?: string;
    }) => {
        return tradingApi.post<TradingResult>('/trading/total-stop-loss/amend', config);
    },

    // 删除全仓止盈止损
    cancelTotalStopLoss: (config: {
        apiKeyId: number;
        algoId: string;
        mgnMode?: string;
    }) => {
        return tradingApi.post<ApiResponse<TradingResult>>('/trading/total-stop-loss/cancel', config);
    },

    // 撤销止盈止损算法订单
    cancelStopLossAlgos: (config: {
        apiKeyId: number;
        instId: string;
        algoId: string;
    }) => {
        return tradingApi.post<ApiResponse<TradingResult>>('/trading/cancel-algos', config);
    },

    // 技术指标计算相关接口
    // 计算技术指标
    calculateTechnicalIndicator: (params: {
        instId: string;
        apiKeyId: number;
        timeframe: string;
        metricName: string;
        limit?: number;
        period?: number;
        stdDev?: number;
    }) => {
        const queryParams = new URLSearchParams();
        queryParams.append('apiKeyId', params.apiKeyId.toString());
        queryParams.append('timeframe', params.timeframe);

        if (params.limit) queryParams.append('limit', params.limit.toString());
        if (params.period) queryParams.append('period', params.period.toString());
        if (params.stdDev) queryParams.append('stdDev', params.stdDev.toString());

        // 添加缓存机制
        const cacheKey = `technical-indicator-${params.metricName}-${params.instId}-${params.apiKeyId}-${params.timeframe}-${params.limit || 100}-${params.period || 'default'}-${params.stdDev || 'default'}`;

        return fetchWithCache(
            cacheKey,
            () => tradingApi.get<ApiResponse<TechnicalIndicatorData>>(
                `/trading/technical-indicators/${params.metricName}/${params.instId}?${queryParams.toString()}`
            )
        );
    },

    // 获取完整图表数据（包含K线数据和技术指标）
    getCompleteChartData: (params: {
        instId: string;
        apiKeyId?: number;
        timeframe: string;
        limit?: number;
        indicators?: string[];
        // 单周期参数（向后兼容）
        emaPeriod?: number;
        rsiPeriod?: number;
        bollPeriod?: number;
        wmaPeriod?: number;
        // 多周期参数（支持多周期同时显示）
        emaPeriods?: number[];
        smaPeriods?: number[];
        wmaPeriods?: number[];
        rsiPeriods?: number[];
        // BOLL参数：格式为 "周期_标准差"，如 ["20_2.0", "30_1.5"]
        bollParams?: string[];
        // MACD参数：格式为 [fast, slow, signal]，如 [12, 26, 9]
        macdPeriods?: number[];
        // KDJ参数：周期列表，如 [9, 14, 21]
        kdjPeriods?: number[];
        cciPeriods?: number[];
        atrPeriods?: number[];
        obvPeriods?: number[];
        adxPeriods?: number[];
        // K线数据开始时间戳（毫秒），可选
        startMills?: number;
    }) => {
        const queryParams = new URLSearchParams();
        if (params.apiKeyId !== undefined && params.apiKeyId !== null) {
            queryParams.append('apiKeyId', params.apiKeyId.toString());
        }
        queryParams.append('timeframe', params.timeframe);

        if (params.limit) queryParams.append('limit', params.limit.toString());
        if (params.indicators && params.indicators.length > 0) {
            params.indicators.forEach(indicator => {
                queryParams.append('indicators', indicator);
            });
        }
        // 单周期参数（向后兼容）
        if (params.emaPeriod) queryParams.append('emaPeriod', params.emaPeriod.toString());
        if (params.rsiPeriod) queryParams.append('rsiPeriod', params.rsiPeriod.toString());
        if (params.bollPeriod) queryParams.append('bollPeriod', params.bollPeriod.toString());
        if (params.wmaPeriod) queryParams.append('wmaPeriod', params.wmaPeriod.toString());

        // 多周期参数
        if (params.emaPeriods && params.emaPeriods.length > 0) {
            params.emaPeriods.forEach(period => {
                queryParams.append('emaPeriods', period.toString());
            });
        }
        if (params.smaPeriods && params.smaPeriods.length > 0) {
            params.smaPeriods.forEach(period => {
                queryParams.append('smaPeriods', period.toString());
            });
        }
        if (params.wmaPeriods && params.wmaPeriods.length > 0) {
            params.wmaPeriods.forEach(period => {
                queryParams.append('wmaPeriods', period.toString());
            });
        }
        if (params.rsiPeriods && params.rsiPeriods.length > 0) {
            params.rsiPeriods.forEach(period => {
                queryParams.append('rsiPeriods', period.toString());
            });
        }
        // BOLL参数：格式为 "周期_标准差"，如 ["20_2.0", "30_1.5"]
        if (params.bollParams && params.bollParams.length > 0) {
            params.bollParams.forEach(param => {
                queryParams.append('bollParams', param);
            });
        }
        
        // MACD参数：格式为 "fast,slow,signal"，如 "12,26,9"
        // 后端可能期望的是macdPeriods数组，或者特定的格式。
        // 根据getCompleteChartData的参数定义，macdPeriods是number[]
        // 但通常MACD需要三个参数，如果是一个数组，应该怎么传？
        // 假设后端接受 macdPeriods=12&macdPeriods=26&macdPeriods=9
        if (params.macdPeriods && params.macdPeriods.length > 0) {
            params.macdPeriods.forEach(period => {
                queryParams.append('macdPeriods', period.toString());
            });
        }

        if (params.kdjPeriods && params.kdjPeriods.length > 0) {
            params.kdjPeriods.forEach(period => {
                queryParams.append('kdjPeriods', period.toString());
            });
        }

        if (params.cciPeriods && params.cciPeriods.length > 0) {
            params.cciPeriods.forEach(period => {
                queryParams.append('cciPeriods', period.toString());
            });
        }

        if (params.atrPeriods && params.atrPeriods.length > 0) {
            params.atrPeriods.forEach(period => {
                queryParams.append('atrPeriods', period.toString());
            });
        }

        if (params.obvPeriods && params.obvPeriods.length > 0) {
            params.obvPeriods.forEach(period => {
                queryParams.append('obvPeriods', period.toString());
            });
        }

        if (params.adxPeriods && params.adxPeriods.length > 0) {
            params.adxPeriods.forEach(period => {
                queryParams.append('adxPeriods', period.toString());
            });
        }
        const startMillsKey = params.startMills === undefined || params.startMills === null
            ? 'none'
            : String(params.startMills);

        // K线数据开始时间戳
        if (params.startMills !== undefined && params.startMills !== null && params.startMills > 0) {
            queryParams.append('startMills', params.startMills.toString());
        }

        // 创建包含所有关键参数的缓存键，确保不同的多周期配置和apiKey不会共享缓存
        const apiKeyIdKey = params.apiKeyId === undefined || params.apiKeyId === null ? 'none' : String(params.apiKeyId);
        const cacheKey = `complete-chart-data-${params.instId}-${apiKeyIdKey}-${params.timeframe}-${params.limit || 240}-${params.indicators?.join(',') || 'none'}-ema-${params.emaPeriods?.join(',') || 'none'}-sma-${params.smaPeriods?.join(',') || 'none'}-wma-${params.wmaPeriods?.join(',') || 'none'}-rsi-${params.rsiPeriods?.join(',') || 'none'}-boll-${params.bollParams?.join(',') || 'none'}-macd-${params.macdPeriods?.join(',') || 'none'}-kdj-${params.kdjPeriods?.join(',') || 'none'}-cci-${params.cciPeriods?.join(',') || 'none'}-atr-${params.atrPeriods?.join(',') || 'none'}-obv-${params.obvPeriods?.join(',') || 'none'}-adx-${params.adxPeriods?.join(',') || 'none'}-ema-${params.emaPeriod || 'none'}-rsi-${params.rsiPeriod || 'none'}-boll-${params.bollPeriod || 'none'}-wma-${params.wmaPeriod || 'none'}-start-${startMillsKey}`;

        console.log('[tradingService] 图表数据请求参数:', {
            instId: params.instId,
            apiKeyId: params.apiKeyId,
            timeframe: params.timeframe,
            indicators: params.indicators,
            startMills: params.startMills,
            queryParams: queryParams.toString(),
            cacheKey: cacheKey.substring(0, 100) + '...'
        });

        const requestUrl = `/trading/complete-chart-data/${params.instId}?${queryParams.toString()}`;
        console.log('[tradingService] 完整请求URL:', requestUrl);

        return fetchWithCache(
            cacheKey,
            () => tradingApi.get<ApiResponse<CompleteChartData>>(requestUrl).then(response => {
                console.log('[tradingService] API原始响应:', response.data);
                // 检查API响应结构
                if (response.data && response.data.success && response.data.data) {
                    const data = response.data.data;
                    console.log('[tradingService] 返回的数据包含指标:', {
                        hasIndicators: !!data.indicators,
                        indicatorKeys: data.indicators ? Object.keys(data.indicators) : []
                    });
                    return response.data.data;
                } else {
                    throw new Error(response.data?.message || '获取图表数据失败');
                }
            })
        );
    },

    // 获取支持的指标类型
    getSupportedMetrics: () => {
        return tradingApi.get<ApiResponse<string[]>>('/trading/technical-indicators/metrics');
    }
};

// 独立导出函数，方便直接使用
export const getActiveApiKeys = () => tradingService.getActiveApiKeys();
export const getTopNInstruments = (count: number, apiKeyId: number) => tradingService.getTopNInstruments(count, apiKeyId);
export const getRealtimePrice = (instId: string, apiKeyId: number): Promise<ApiResponse<RealtimePrice>> => tradingService.getRealtimePrice(instId, apiKeyId);
export const getPendingOrders = (apiKeyId: number): Promise<ApiResponse<OrderModel[]>> => tradingService.getPendingOrders(apiKeyId);

export default tradingService;
