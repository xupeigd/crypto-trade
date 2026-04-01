/**
 * AI交易风控配置相关类型定义
 */

// 风控模式枚举
export enum RiskMode {
    AUTO = 'AUTO',
    MANUAL = 'MANUAL'
}

// 交易风格枚举
export enum TradingStyle {
    C1_CONSERVATIVE = 'C1_CONSERVATIVE',
    C2_CAUTIOUS = 'C2_CAUTIOUS',
    C3_MODERATE = 'C3_MODERATE',
    C4_ACTIVE = 'C4_ACTIVE',
    C5_AGGRESSIVE = 'C5_AGGRESSIVE'
}

// 执行模式枚举
export enum ExecutionMode {
    LIVE = 'LIVE',
    DRY_RUN = 'DRY_RUN'
}

// 执行模式映射
export const ExecutionModeMap = {
    [ExecutionMode.LIVE]: '实盘模式',
    [ExecutionMode.DRY_RUN]: '模拟模式'
};

// 执行模式颜色映射
export const ExecutionModeColorMap = {
    [ExecutionMode.LIVE]: '#52c41a', // 绿色 - 表示实盘
    [ExecutionMode.DRY_RUN]: '#fa8c16' // 橙色 - 表示模拟
};

// 执行模式图标映射
export const ExecutionModeIconMap = {
    [ExecutionMode.LIVE]: '🔴', // 实盘
    [ExecutionMode.DRY_RUN]: '🟠' // 模拟
};

// 风控模式映射
export const RiskModeMap = {
    [RiskMode.AUTO]: '自动模式',
    [RiskMode.MANUAL]: '手动模式'
};

// 风控模式颜色映射
export const RiskModeColorMap = {
    [RiskMode.AUTO]: '#52c41a', // 绿色 - 表示自动运行
    [RiskMode.MANUAL]: '#fa8c16' // 橙色 - 表示手动控制
};

// 风控模式图标映射
export const RiskModeIconMap = {
    [RiskMode.AUTO]: '🤖', // 机器人图标
    [RiskMode.MANUAL]: '👤' // 人物图标
};

// 交易风格映射
export const TradingStyleMap = {
    [TradingStyle.C1_CONSERVATIVE]: '保守型',
    [TradingStyle.C2_CAUTIOUS]: '谨慎型',
    [TradingStyle.C3_MODERATE]: '稳健型',
    [TradingStyle.C4_ACTIVE]: '积极型',
    [TradingStyle.C5_AGGRESSIVE]: '激进型'
};

// 交易风格颜色映射
export const TradingStyleColorMap = {
    [TradingStyle.C1_CONSERVATIVE]: '#1677ff', // 蓝色 - 表示保守稳健
    [TradingStyle.C2_CAUTIOUS]: '#13c2c2',    // 青色 - 表示谨慎
    [TradingStyle.C3_MODERATE]: '#52c41a',    // 绿色 - 表示稳健
    [TradingStyle.C4_ACTIVE]: '#fa8c16',      // 橙色 - 表示积极
    [TradingStyle.C5_AGGRESSIVE]: '#ff4d4f'   // 红色 - 表示激进
};

// 交易风格图标映射
export const TradingStyleIconMap = {
    [TradingStyle.C1_CONSERVATIVE]: '🛡️', // 盾牌图标 - 表示保护稳健
    [TradingStyle.C2_CAUTIOUS]: '⚖️',     // 天平图标 - 表示谨慎平衡
    [TradingStyle.C3_MODERATE]: '📊',     // 图表图标 - 表示稳健
    [TradingStyle.C4_ACTIVE]: '⚡',       // 闪电图标 - 表示积极
    [TradingStyle.C5_AGGRESSIVE]: '🚀'    // 火箭图标 - 表示激进进取
};

// 交易风格描述映射
export const TradingStyleDescriptionMap = {
    [TradingStyle.C1_CONSERVATIVE]: '优先考虑资本保护，只在确定性极高时交易，严格控制止损，最大亏损3%，目标盈利5%',
    [TradingStyle.C2_CAUTIOUS]: '注重风险控制，选择性参与高确定性机会，最大亏损5%，目标盈利10%',
    [TradingStyle.C3_MODERATE]: '平衡风险与收益，寻找稳健的交易机会，最大亏损8%，目标盈利15%',
    [TradingStyle.C4_ACTIVE]: '追求更高收益，愿意承担适度风险，对市场波动反应更灵敏，最大亏损12%，目标盈利20%',
    [TradingStyle.C5_AGGRESSIVE]: '追求高收益机会，承担较高风险以获取更大回报，最大亏损18%，目标盈利30%'
};

// 风控模式历史记录接口
export interface RiskModeHistory {
    historyId: number;
    oldMode: RiskMode;
    newMode: RiskMode;
    changeReason?: string;
    operatorInfo?: string;
    createdTime: string | number | number[];
}

// 交易风格历史记录接口
export interface TradingStyleHistory {
    historyId: number;
    oldStyle: TradingStyle;
    newStyle: TradingStyle;
    changeReason?: string;
    operatorInfo?: string;
    createdTime: string | number | number[];
}

// 风控模式配置信息接口
export interface RiskControlInfo {
    currentMode: RiskMode;
    defaultMode: RiskMode;
    runtimeMode?: RiskMode;
    isAutoMode: boolean;
    isManualMode: boolean;
    lastChange?: RiskModeHistory;
    totalChangesToday: number;

    // 交易风格相关字段
    currentTradingStyle: TradingStyle;
    defaultTradingStyle: TradingStyle;
    runtimeTradingStyle?: TradingStyle;
    isC1ConservativeStyle: boolean;
    isC2CautiousStyle: boolean;
    isC3ModerateStyle: boolean;
    isC4ActiveStyle: boolean;
    isC5AggressiveStyle: boolean;
    lastTradingStyleChange?: TradingStyleHistory;
    totalTradingStyleChangesToday: number;

    // 执行模式相关字段
    executionMode?: ExecutionMode;
    defaultExecutionMode: ExecutionMode;
}

// 当前模式响应接口
export interface CurrentModeResponse {
    currentMode: RiskMode;
    description: string;
    isAutoMode: boolean;
    isManualMode: boolean;
}

// 设置风控模式请求接口
export interface SetRiskModeRequest {
    mode: RiskMode;
    changeReason: string;
}

// 重置为默认模式请求接口
export interface ResetRequest {
    changeReason?: string;
}

// 设置风控模式响应接口
export interface SetRiskModeResponse {
    success: boolean;
    message: string;
    history?: RiskModeHistory;
    currentMode: RiskMode;
}

// 当前交易风格响应接口
export interface CurrentTradingStyleResponse {
    currentTradingStyle: TradingStyle;
    description: string;
    isC1ConservativeStyle: boolean;
    isC2CautiousStyle: boolean;
    isC3ModerateStyle: boolean;
    isC4ActiveStyle: boolean;
    isC5AggressiveStyle: boolean;
}

// 设置交易风格请求接口
export interface SetTradingStyleRequest {
    style: TradingStyle;
    changeReason: string;
}

// 设置交易风格响应接口
export interface SetTradingStyleResponse {
    success: boolean;
    message: string;
    history?: TradingStyleHistory;
    currentTradingStyle: TradingStyle;
}

// 设置执行模式请求接口
export interface SetExecutionModeRequest {
    mode: ExecutionMode;
    reason: string;
}

// 设置执行模式响应接口
export interface SetExecutionModeResponse {
    success: boolean;
    message: string;
}

// 今日统计信息接口
export interface TodayStatsResponse {
    date: string;
    todayChangeCount: number;
    lastChange?: RiskModeHistory;
    currentMode: RiskMode;
}

// API响应基础接口
export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    message?: string;
}

// 分页请求参数接口
export interface PaginationParams {
    page?: number;
    pageSize?: number;
    limit?: number;
}

// 时间范围查询参数接口
export interface DateRangeParams {
    startTime: string;
    endTime: string;
}

// 历史记录查询参数接口
export interface HistoryQueryParams extends PaginationParams {
    startTime?: string;
    endTime?: string;
}

// 模式变更统计响应接口
export interface ChangeStatsResponse {
    startTime: string;
    endTime: string;
    changeCount: number;
}