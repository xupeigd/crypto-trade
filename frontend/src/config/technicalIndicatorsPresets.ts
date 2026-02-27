import {IndicatorConfig, TechnicalIndicator} from '../hooks/useChartState';

/**
 * 技术指标预设组合配置
 * 定义常用的交易策略和技术指标组合
 */

export interface IndicatorPreset {
    id: string;
    name: string;
    description: string;
    category: 'short-term' | 'medium-term' | 'long-term' | 'trading-system' | 'trend-following';
    indicators: TechnicalIndicator[];
    configs: Record<TechnicalIndicator, IndicatorConfig>;
    icon?: string;
    color?: string;
}

/**
 * 技术指标预设配置集合
 * 涵盖短期、中期、长期交易策略以及专业交易系统
 */
export const TECHNICAL_INDICATOR_PRESETS: IndicatorPreset[] = [
    // ===== 短期策略 =====
    {
        id: 'short-term-momentum',
        name: '短期动量策略',
        description: '适用于短期交易，捕捉短期价格动量',
        category: 'short-term',
        indicators: ['EMA', 'RSI'],
        configs: {
            EMA: {period: 5},
            SMA: {period: 10},
            WMA: {period: 8},
            RSI: {period: 14},
            BOLL: {period: 20, stdDev: 2},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '🚀',
        color: '#ff4d4f'
    },
    {
        id: 'short-term-breakout',
        name: '短期突破策略',
        description: '识别短期价格突破点',
        category: 'short-term',
        indicators: ['EMA', 'SMA', 'BOLL'],
        configs: {
            EMA: {period: 10},
            SMA: {period: 20},
            WMA: {period: 15},
            RSI: {period: 9},
            BOLL: {period: 15, stdDev: 1.5},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '⚡',
        color: '#fa8c16'
    },

    // ===== 中期策略 =====
    {
        id: 'medium-term-balanced',
        name: '中期均衡策略',
        description: '平衡的中期交易策略',
        category: 'medium-term',
        indicators: ['EMA', 'SMA', 'RSI', 'BOLL'],
        configs: {
            EMA: {period: 20},
            SMA: {period: 30},
            WMA: {period: 25},
            RSI: {period: 14},
            BOLL: {period: 20, stdDev: 2},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '⚖️',
        color: '#1890ff'
    },
    {
        id: 'medium-term-trend',
        name: '中期趋势策略',
        description: '中期趋势跟踪策略',
        category: 'medium-term',
        indicators: ['EMA', 'SMA', 'RSI'],
        configs: {
            EMA: {period: 30},
            SMA: {period: 50},
            WMA: {period: 40},
            RSI: {period: 21},
            BOLL: {period: 25, stdDev: 2.2},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '📈',
        color: '#52c41a'
    },

    // ===== 长期策略 =====
    {
        id: 'long-term-investor',
        name: '长期投资者策略',
        description: '适合长期投资者的稳健策略',
        category: 'long-term',
        indicators: ['EMA', 'SMA', 'BOLL'],
        configs: {
            EMA: {period: 60},
            SMA: {period: 60},
            WMA: {period: 60},
            RSI: {period: 30},
            BOLL: {period: 50, stdDev: 2.5},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '🏛️',
        color: '#722ed1'
    },
    {
        id: 'long-term-position',
        name: '长期持仓策略',
        description: '长期持仓和技术分析结合',
        category: 'long-term',
        indicators: ['EMA', 'SMA', 'RSI'],
        configs: {
            EMA: {period: 60},
            SMA: {period: 60},
            WMA: {period: 60},
            RSI: {period: 50},
            BOLL: {period: 60, stdDev: 3},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '💼',
        color: '#13c2c2'
    },

    // ===== 专业交易系统 =====
    {
        id: 'golden-cross',
        name: '黄金交叉系统',
        description: '经典EMA交叉交易系统',
        category: 'trading-system',
        indicators: ['EMA', 'RSI'],
        configs: {
            EMA: {period: 50},
            SMA: {period: 60},
            WMA: {period: 60},
            RSI: {period: 14},
            BOLL: {period: 20, stdDev: 2},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '✨',
        color: '#fadb14'
    },
    {
        id: 'multi-timeframe',
        name: '多时间框架分析',
        description: '结合多个时间框架的综合分析',
        category: 'trading-system',
        indicators: ['EMA', 'SMA', 'WMA', 'RSI', 'BOLL'],
        configs: {
            EMA: {period: 20},
            SMA: {period: 50},
            WMA: {period: 30},
            RSI: {period: 14},
            BOLL: {period: 20, stdDev: 2},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '🔄',
        color: '#cf1322'
    },

    // ===== 趋势跟踪 =====
    {
        id: 'trend-following-ema',
        name: 'EMA趋势跟踪',
        description: '基于EMA的趋势跟踪策略',
        category: 'trend-following',
        indicators: ['EMA', 'SMA'],
        configs: {
            EMA: {period: 20},
            SMA: {period: 50},
            WMA: {period: 30},
            RSI: {period: 21},
            BOLL: {period: 30, stdDev: 2.2},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '📊',
        color: '#1890ff'
    },
    {
        id: 'strong-trend',
        name: '强趋势识别',
        description: '识别和跟踪强趋势',
        category: 'trend-following',
        indicators: ['EMA', 'WMA', 'RSI'],
        configs: {
            EMA: {period: 30},
            SMA: {period: 60},
            WMA: {period: 45},
            RSI: {period: 28},
            BOLL: {period: 40, stdDev: 2.5},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '🎯',
        color: '#52c41a'
    },

    // ===== 特殊策略 =====
    {
        id: 'scalping-quick',
        name: '快速剥头皮',
        description: '超短线快速交易策略',
        category: 'short-term',
        indicators: ['EMA', 'RSI'],
        configs: {
            EMA: {period: 3},
            SMA: {period: 5},
            WMA: {period: 4},
            RSI: {period: 7},
            BOLL: {period: 10, stdDev: 1},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '⚡',
        color: '#ff4d4f'
    },
    {
        id: 'mean-reversion',
        name: '均值回归策略',
        description: '基于价格均值回归的交易策略',
        category: 'medium-term',
        indicators: ['SMA', 'BOLL', 'RSI'],
        configs: {
            EMA: {period: 20},
            SMA: {period: 20},
            WMA: {period: 18},
            RSI: {period: 21},
            BOLL: {period: 20, stdDev: 1.8},
            MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}
        },
        icon: '🔄',
        color: '#fa8c16'
    }
];

/**
 * 按类别分组的预设配置
 */
export const PRESETS_BY_CATEGORY = {
    'short-term': TECHNICAL_INDICATOR_PRESETS.filter(preset => preset.category === 'short-term'),
    'medium-term': TECHNICAL_INDICATOR_PRESETS.filter(preset => preset.category === 'medium-term'),
    'long-term': TECHNICAL_INDICATOR_PRESETS.filter(preset => preset.category === 'long-term'),
    'trading-system': TECHNICAL_INDICATOR_PRESETS.filter(preset => preset.category === 'trading-system'),
    'trend-following': TECHNICAL_INDICATOR_PRESETS.filter(preset => preset.category === 'trend-following')
};

/**
 * 类别显示配置
 */
export const CATEGORY_CONFIG = {
    'short-term': {
        name: '短期策略',
        description: '适用于短期交易和剥头皮',
        icon: '🚀',
        color: '#ff4d4f'
    },
    'medium-term': {
        name: '中期策略',
        description: '适用于波段交易和中期分析',
        icon: '📈',
        color: '#1890ff'
    },
    'long-term': {
        name: '长期策略',
        description: '适用于长期投资和持仓',
        icon: '💼',
        color: '#52c41a'
    },
    'trading-system': {
        name: '交易系统',
        description: '专业级交易分析系统',
        icon: '⚙️',
        color: '#722ed1'
    },
    'trend-following': {
        name: '趋势跟踪',
        description: '趋势识别和跟踪策略',
        icon: '🎯',
        color: '#fa8c16'
    }
};

/**
 * 根据预设ID获取预设配置
 */
export const getPresetById = (id: string): IndicatorPreset | undefined => {
    return TECHNICAL_INDICATOR_PRESETS.find(preset => preset.id === id);
};

/**
 * 获取推荐预设配置
 */
export const getRecommendedPresets = (): IndicatorPreset[] => {
    return [
        'medium-term-balanced',
        'golden-cross',
        'trend-following-ema'
    ].map(id => getPresetById(id)).filter(Boolean) as IndicatorPreset[];
};

/**
 * 多周期配置预设
 * 用于快速设置多周期EMA/SMA/WMA显示
 */
export const MULTI_PERIOD_PRESETS = {
    // EMA多周期组合
    emaShort: [5, 10, 20],           // 短期EMA组合
    emaMedium: [20, 30, 50],         // 中期EMA组合
    emaLong: [30, 50, 60],           // 长期EMA组合
    emaComplete: [5, 10, 20, 30, 50, 60], // 完整EMA组合

    // SMA多周期组合
    smaShort: [5, 10, 20],           // 短期SMA组合
    smaMedium: [20, 30, 50],         // 中期SMA组合
    smaLong: [30, 50, 60],           // 长期SMA组合
    smaComplete: [5, 10, 20, 30, 50, 60], // 完整SMA组合

    // WMA多周期组合
    wmaShort: [5, 10, 15],           // 短期WMA组合
    wmaMedium: [15, 30, 45],         // 中期WMA组合
    wmaLong: [30, 45, 60],           // 长期WMA组合
    wmaComplete: [5, 10, 15, 30, 45, 60], // 完整WMA组合

    // RSI多周期组合
    rsiShort: [7, 14, 21],           // 短期RSI组合
    rsiMedium: [14, 21, 28],         // 中期RSI组合
    rsiLong: [21, 30, 50],           // 长期RSI组合
    rsiComplete: [7, 14, 21, 28, 35, 50] // 完整RSI组合
};

export default TECHNICAL_INDICATOR_PRESETS;