// OKX持仓数据相关类型定义

export interface OkxPosition {
    vendor: string;
    instType: 'SWAP' | 'FUTURES';
    instId: string;
    posId: string;
    posSide: 'long' | 'short' | 'net';
    pos?: number | null;
    availPos?: number | null;
    lastPx?: number | null;
    avgPx?: number | null;
    upl?: number | null;
    uplRatio?: number | null;
    lever?: number | null;
    mgnMode?: string;
    ccy?: string;
    notionalUsd?: number | null;
    margin?: number | null;
    imr?: number | null;
    mmr?: number | null;
    liqPx?: number | null;
    markPx?: number | null;
    dataIngestionTime: string;
    ts?: number | null;
    updateTime?: string | null;
}

export interface OkxPositionStatistics {
    totalPositions: number;
    totalNotional?: number | null;
    totalUnrealizedPnl?: number | null;
    totalMargin?: number | null;
    typeStatistics?: Array<{
        name: string;
        count: number;
        totalNotional: number | null;
        totalPnl?: number | null;
        totalMargin?: number | null;
    }>;
    sideStatistics?: Array<{
        name: string;
        count: number;
        totalNotional: number | null;
        totalPnl?: number | null;
        totalMargin?: number | null;
    }>;
    currencyStatistics?: Array<{
        name: string;
        count: number;
        totalNotional: number | null;
        totalPnl?: number | null;
        totalMargin?: number | null;
    }>;
    latestUpdateTime?: string | null;
}

export interface OkxPositionQueryParams {
    vendor?: string;
    startTime?: string;
    endTime?: string;
    sort?: 'asc' | 'desc';
    days?: number;
}

export interface OkxPositionHistoryRequest extends OkxPositionQueryParams {
    startTime: string;
    endTime: string;
}

export interface OkxPositionLatestRequest {
    vendor?: string;
}

export interface OkxPositionInstrumentRequest {
    instId: string;
    vendor?: string;
    days?: number;
}

export interface OkxPositionStatisticsRequest {
    vendor?: string;
}

// API响应类型
export interface OkxPositionResponse {
    data: OkxPosition[];
    success: boolean;
    message?: string;
}

export interface OkxPositionStatisticsResponse {
    data: OkxPositionStatistics;
    success: boolean;
    message?: string;
}

// 表格显示用的简化类型
export interface OkxPositionTableRow {
    instId: string;
    instType: 'SWAP' | 'FUTURES';
    posSide: 'long' | 'short' | 'net';
    pos: number;
    availPos: number;
    lastPx: number;
    avgPx: number;
    upl: number;
    uplRatio: number;
    notionalUsd: number;
    lever: number;
    margin: number;
    ccy: string;
    dataIngestionTime: string;
}

// 持仓汇总统计
export interface OkxPositionSummary {
    totalNotional: number;
    totalMargin: number;
    totalUpl: number;
    longPositions: number;
    shortPositions: number;
    swapCount: number;
    futuresCount: number;
    latestUpdateTime: string;
}

// 后端PositionModel对应的前端类型定义
export interface PositionModel {
    adl?: string;                    // 自动减仓标识：0=无，1=部分减仓，2=全部减仓等
    availPos?: number;                // 可用持仓数量
    avgPx?: number;                   // 平均开仓价格
    baseBal?: number;                 // 基础币余额（现货用）
    baseBorrowed?: number;
    baseInterest?: number;
    bePx?: number;                    // 强平价格（预估）
    bizRefId?: string;
    bizRefType?: string;
    cTime?: number;                   // 创建时间（毫秒）
    ccy?: string;                     // 保证金币种，如 USDT
    clSpotInUseAmt?: number;
    deltaBS?: number;
    deltaPA?: number;
    fee?: number;                     // 累计手续费（负数表示已支付）
    gammaBS?: number;
    gammaPA?: number;
    hedgedPos?: string;
    idxPx?: number;                   // 指数价格
    imr?: number;                     // 初始保证金
    instId?: string;                  // 合约代码，如 ETH-USDT-SWAP
    instType?: string;                // SWAP, FUTURES, OPTION 等
    interest?: number;
    last?: number;                    // 最新成交价格
    lever?: number;                   // 杠杆倍数
    liab?: number;
    liabCcy?: string;
    liqPenalty?: number;              // 强平罚金
    liqPx?: number;                   // 预估强平价
    margin?: number;                  // 保证金（逐仓）
    marginRatio?: string;            // 保证金率
    markPx?: number;                  // 标记价格
    maxSpotInUseAmt?: number;
    mgnMode?: string;                 // isolated / cross
    mgnRatio?: number;                // 保证金率
    mmr?: number;                     // 维持保证金
    nonSettleAvgPx?: number;
    notionalUsd?: number;             // 持仓名义价值（USD）
    optVal?: number;
    pendingCloseOrdLiabVal?: number;
    pnl?: number;
    realizedPnl?: number;             // 已实现盈亏
    settledPnl?: number;
    spotInUseAmt?: number;
    spotInUseCcy?: string;
    thetaBS?: number;
    thetaPA?: number;
    tradeId?: string;
    uTime?: number;                   // 持仓更新时间
    upl?: number;                     // 未实现盈亏（按标记价格计算）
    uplLastPx?: number;               // 未实现盈亏（按最新价计算）
    uplRatio?: number;                // 未实现盈亏比率（标记价）
    uplRatioLastPx?: number;          // 未实现盈亏比率（最新价）
    usdPx?: number;
    vegaBS?: number;
    vegaPA?: number;
    pos?: number;                     // 持仓数量（张）
    position?: number;                // 折算后持仓数量
    posCcy?: string;
    posId?: string;
    posSide?: string;                 // 持仓方向 long:多头 short:空头 net:净仓
    quoteBal?: number;
    quoteBorrowed?: number;
    quoteInterest?: number;
    estimatedLiquidationPx?: number;
    marginRatioPercent?: number;
    dataIngestionTime?: number;
    positionStopLossStrategies?: any[];
    // 止盈止损相关字段
    takeProfitPrice?: number; // 仓位止盈价格
    stopLossPrice?: number; // 仓位止损价格
    totalTakeProfitPrice?: number; // 全仓止盈价格
    totalStopLossPrice?: number; // 全仓止损价格
    tpTriggerPct?: number; // 止盈触发百分比
    slTriggerPct?: number; // 止损触发百分比
    tpTriggerPxType?: string; // 止盈触发类型：1=价格，2=百分比
    slTriggerPxType?: string; // 止损触发类型：1=价格，2=百分比
    tpTriggerPxMode?: string; // 止盈触发模式：1=仓位模式，2=全仓模式
    slTriggerPxMode?: string; // 止损触发模式：1=仓位模式，2=全仓模式
    // 全仓止盈止损算法订单数据
    closeOrderAlgo?: Array<{
        algoId: string;
        closeFraction: string;
        ordType: string;
        slTriggerPx?: string;
        slTriggerPxType?: string;
        tpTriggerPx?: string;
        tpTriggerPxType?: string;
    }>;
    // 资金费用相关字段
    fundingFee?: number; // 累计资金费
}
