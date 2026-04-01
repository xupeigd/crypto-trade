import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Empty} from 'antd';
import {formatEffectiveDecimal} from '../../../utils/numberFormatter';
import {getIndicatorValues} from '../../../utils/indicatorValues';
import {TechnicalIndicatorData} from '../../../services/tradingService';
import {IndicatorConfig, TechnicalIndicator, TimeFrame} from '../../../hooks/useChartState';

// K线数据接口
export interface CandlestickData {
    timestamp: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
    confirm: number; // 0=未完结，1=已完结
}

interface CandlestickChartProps {
    data: CandlestickData[];
    width: number;
    height: number;
    loading?: boolean;
    markPrice?: number | null;
    timeFrame?: TimeFrame;
    reverseOrder?: boolean; // 是否反转数据顺序（用于历史仓位K线图切换）
    indicators?: {
        EMA?: TechnicalIndicatorData;
        SMA?: TechnicalIndicatorData;
        WMA?: TechnicalIndicatorData;
        BOLL?: TechnicalIndicatorData;
        RSI?: TechnicalIndicatorData;
        MACD?: TechnicalIndicatorData;
        KDJ?: TechnicalIndicatorData;
        CCI?: TechnicalIndicatorData;
        ATR?: TechnicalIndicatorData;
        OBV?: TechnicalIndicatorData;
        ADX?: TechnicalIndicatorData;
    };
    markPriceColor?: string; // 当前K线标识线的颜色
    indicatorConfigs?: Record<TechnicalIndicator, IndicatorConfig>; // 指标配置
    visibleIndicators?: Record<string, boolean>; // 外部控制的指标可见性
    onUpdateIndicatorConfig?: (indicator: TechnicalIndicator, config: Partial<IndicatorConfig>) => void; // 更新指标配置
    referenceLines?: {
        avgPx?: number;           // 开仓价参考线
        includeAvgPxInRange?: boolean;
        entryLabel?: string;
        closePx?: number;         // 平仓价参考线（历史仓位等场景使用）
        takeProfitPx?: number;    // 止盈价参考线
        stopLossPx?: number;      // 止损价参考线
        liquidationPx?: number;   // 强平价参考线
        cTime?: number;           // 开仓时间（毫秒时间戳）
        uTime?: number;           // 平仓时间（毫秒时间戳）
        profitLossStatus?: 'profit' | 'loss' | 'break-even'; // 盈亏状态
        posSide?: string;         // 持仓方向 long:多头 short:空头
    }; // 持仓参考线
    renderTooltipExtra?: (args: {
        candle: CandlestickData;
        dataIndex: number;
        timeFrame: string;
        referenceLines?: CandlestickChartProps['referenceLines'];
    }) => React.ReactNode;
    isDebugMode?: boolean; // 调试模式
}

// 主题颜色配置
export const COLORS = {
    background: '#1f1f1f',      // 深色背景
    grid: '#434343',           // 网格线
    text: '#999999',           // 文字颜色
    positive: '#52c41a',       // 阳线颜色（涨）
    negative: '#ff4d4f',       // 阴线颜色（跌）
    border: '#303030',         // 边框颜色
    // 技术指标颜色
    ema: '#1890ff',            // EMA线条颜色（蓝色）
    sma: '#ff9800',            // SMA线条颜色（橙色）
    wma: '#722ed1',            // WMA线条颜色（紫色）
    bollUpper: '#ff4d4f',      // 布林带上轨（红色）
    bollMiddle: '#999999',     // 布林带中轨（灰色）
    bollLower: '#52c41a',      // 布林带下轨（绿色）
    rsi: '#9254de',            // RSI线条颜色（紫色）
    rsiOverbought: '#ff4d4f',  // RSI超买线（红色）
    rsiOversold: '#52c41a',    // RSI超卖线（绿色）
    // 多周期指标颜色
    ma5: '#ff6b6b',            // 5期MA（红色）
    ma10: '#4ecdc4',           // 10期MA（青色）
    ma20: '#45b7d1',           // 20期MA（蓝色）
    ma30: '#96ceb4',           // 30期MA（绿色）
    ma60: '#ffeaa7',           // 60期MA（黄色）
    ma120: '#dfe6e9',          // 120期MA（浅灰色）
    ma200: '#74b9ff',          // 200期MA（浅蓝色）
    // 指标类型颜色映射
    indicatorColors: {
        EMA: ['#1890ff', '#00d2d3', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#13c2c2'],
        SMA: ['#eb2f96', '#fa541c', '#fadb14', '#52c41a', '#1890ff', '#722ed1', '#13c2c2'],
        WMA: ['#a0d911', '#faad14', '#fa8c16', '#f5222d', '#cf1322', '#722ed1', '#2f54eb'],
        RSI: ['#9254de', '#13c2c2', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#1890ff'],
        BOLL: ['#ff4d4f', '#52c41a', '#1890ff', '#fa8c16', '#722ed1', '#faad14', '#13c2c2'],
        KDJ: ['#13c2c2', '#9254de', '#fa8c16', '#52c41a', '#f5222d', '#1890ff', '#722ed1'],
        ADX: ['#fa8c16', '#52c41a', '#1890ff', '#9254de', '#f5222d', '#722ed1', '#13c2c2']
    }
};

const shouldDebug = () => {
    return import.meta.env.DEV && typeof window !== 'undefined' && (window as any).__DEBUG_KLINE__ === true;
};

const debugLog = (...args: any[]) => {
    if (shouldDebug()) {
        // console.log(...args);
    }
};

const debugWarn = (...args: any[]) => {
    if (shouldDebug()) {
        // console.warn(...args);
    }
};

// 将hex颜色转换为rgba格式
const hexToRgba = (hex: string, opacity: number): string => {
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${opacity})`;
};

// 图表布局配置常量
const CHART_LAYOUT = {
    mainChartRatio: 0.45,       // 主图高度比例 (45%)
    volumeChartRatio: 0.12,     // 成交量图高度比例 (12%)
    rsiChartRatio: 0.28,        // RSI副图高度比例 (28%)
    macdChartRatio: 0.15,       // MACD副图高度比例 (15%)
    topMargin: 30,             // 顶部边距（为时间轴预留）
    bottomMargin: 30,          // 底部边距（为X轴预留）
    leftMargin: 50,            // 左边距（用于价格标签）
    rightMargin: 50,           // 右边距（默认对称，如有参考线会动态增加）
    xAxisHeight: 25,           // X轴标签区域高度
    separatorMargin: 5,        // 图表之间的分隔间距
    gridLines: 10,             // 网格线数量
};

const CandlestickChart: React.FC<CandlestickChartProps> = ({
                                                               data,
                                                               width,
                                                               height,
                                                               loading = false,
                                                               markPrice,
                                                               timeFrame,
                                                               indicators,
                                                               markPriceColor,
                                                               indicatorConfigs,
                                                               visibleIndicators: propVisibleIndicators,
                                                               onUpdateIndicatorConfig,
                                                               referenceLines,
                                                               reverseOrder = false,
                                                               renderTooltipExtra
                                                           }) => {
    const getIndicatorByName = useCallback((name: string): any => {
        if (!indicators) return undefined;
        const direct = (indicators as any)[name];
        if (direct) return direct;
        const upper = (indicators as any)[name.toUpperCase()];
        if (upper) return upper;
        const lower = (indicators as any)[name.toLowerCase()];
        if (lower) return lower;
        return undefined;
    }, [indicators]);

    // 组件级函数：验证指标数据的有效性（兼容新旧两种数据格式）
    const isValidIndicator = useCallback((indicator: any): boolean => {
        if (!indicator) return false;

        // 新格式（后端直接返回）：{metricName: "EMA", values: [...]}
        if (indicator.metricName && indicator.values) {
            return Array.isArray(indicator.values) && indicator.values.length > 0;
        }

        // MACD特殊格式（新格式）：{metricName: "MACD", macdValues: [...]}
        if (indicator.metricName && indicator.macdValues) {
            return Array.isArray(indicator.macdValues) && indicator.macdValues.length > 0;
        }

        // MACD multiPeriodValues格式（新格式）：{metricName: "MACD", values: [{multiPeriodValues: {...}}]}
        if (indicator.metricName && indicator.values && indicator.values[0]?.multiPeriodValues) {
            const hasMacdKey = Object.keys(indicator.values[0].multiPeriodValues || {}).some(
                key => key.toLowerCase().startsWith('macd_')
            );
            return hasMacdKey;
        }

        // 旧格式（带包装层）：{success: true, data: {metricName: "EMA", values: [...]}}
        if (indicator.success === true && indicator.data && indicator.data.values) {
            return Array.isArray(indicator.data.values) && indicator.data.values.length > 0;
        }

        // 旧格式-MACD特殊处理：{success: true, data: {metricName: "MACD", macdValues: [...]}}
        if (indicator.success === true && indicator.data && indicator.data.macdValues) {
            return Array.isArray(indicator.data.macdValues) && indicator.data.macdValues.length > 0;
        }

        return false;
    }, []);

    // 时间格式化工具函数 - 根据时间帧返回不同格式
    const formatTimeLabel = useCallback((timestamp: number, currentTimeFrame?: TimeFrame): string => {
        const date = new Date(timestamp);
        const year = date.getFullYear().toString().slice(-2);
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');
        const hours = date.getHours().toString().padStart(2, '0');
        const minutes = date.getMinutes().toString().padStart(2, '0');

        switch (currentTimeFrame) {
            case '1d':
                return `${year}/${month}/${day}`;
            case '4h':
            case '1h':
                return `${month}/${day} ${hours}`;
            case '5m':
            case '1m':
            default:
                return `${month}/${day} ${hours}:${minutes}`;
        }
    }, []);

    const canvasRef = useRef<HTMLCanvasElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const [hoveredPoint, setHoveredPoint] = useState<{
        x: number,
        y: number,
        data?: CandlestickData,
        yPrice?: number,
        dataIndex?: number
    } | null>(null);
    const [internalVisibleIndicators, setInternalVisibleIndicators] = useState<Record<string, boolean>>({});
    const visibleIndicators = propVisibleIndicators || internalVisibleIndicators;
    const displayData = useMemo(() => {
        if (!data || data.length === 0) return [];
        return reverseOrder
            ? [...data].sort((a, b) => a.timestamp - b.timestamp)
            : [...data].sort((a, b) => b.timestamp - a.timestamp);
    }, [data, reverseOrder]);

    // 初始化指标可见性并同步配置
    useEffect(() => {
        if (propVisibleIndicators) return;

        if (indicators) {
            // 检测是否所有指标都被清除（selectedIndicators为空）
            const hasAnyActiveIndicator = Object.keys(indicators).some(key => {
                const indicator = indicators[key as keyof typeof indicators];
                return isValidIndicator(indicator);
            });

            // 如果没有任何活跃的指标，清空visibleIndicators状态
            if (!hasAnyActiveIndicator) {
                if (Object.keys(visibleIndicators).length > 0) {
                    debugLog('[CandlestickChart] 检测到所有指标已清除，清空图例状态');
                    setInternalVisibleIndicators({});
                }
                return;
            }

            const nextVisibility: Record<string, boolean> = {};
            let hasChanges = false;

            // 检查并更新可见性
            const updateVisibility = (type: TechnicalIndicator, periods: number[]) => {
                const config = indicatorConfigs?.[type];
                const configVisiblePeriods = config?.visiblePeriods;

                periods.forEach(p => {
                    const key = `${type}_${p}`;
                    let isVisible = true;

                    if (configVisiblePeriods) {
                        // 如果有明确的可见性配置，则以此为准
                        isVisible = configVisiblePeriods.includes(p);
                    } else {
                        // 如果没有配置，且当前未设置，则默认为可见
                        // 如果已有设置，保持原状（除非是初始化）
                        isVisible = visibleIndicators[key] !== undefined ? visibleIndicators[key] : true;
                    }

                    if (visibleIndicators[key] !== isVisible) {
                        hasChanges = true;
                    }
                    nextVisibility[key] = isVisible;
                });
            };

            // 处理EMA
            if (isValidIndicator(indicators.EMA)) {
                const values = getIndicatorValues(indicators.EMA);
                const sample = Array.isArray(values)
                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;

                if (sample?.multiPeriodValues) {
                    const periods = Object.keys(sample.multiPeriodValues || {})
                        .filter(key => key.startsWith('ema_'))
                        .map(key => parseInt(key.replace('ema_', '')))
                        .filter(n => !Number.isNaN(n))
                        .sort((a, b) => a - b);
                    if (periods.length > 0) updateVisibility('EMA', periods);
                } else {
                    const period = (indicators.EMA as any)?.data?.period ?? (indicators.EMA as any)?.period;
                    if (period) {
                        const key = `EMA_${period}`;
                        if (visibleIndicators[key] === undefined) {
                            nextVisibility[key] = true;
                            hasChanges = true;
                        } else {
                            nextVisibility[key] = visibleIndicators[key];
                        }
                    }
                }
            }

            // 处理SMA
            if (isValidIndicator(indicators.SMA)) {
                const values = getIndicatorValues(indicators.SMA);
                const sample = Array.isArray(values)
                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;
                if (sample?.multiPeriodValues) {
                    const periods = Object.keys(sample.multiPeriodValues || {})
                        .filter(key => key.startsWith('sma_'))
                        .map(key => parseInt(key.replace('sma_', '')))
                        .filter(n => !Number.isNaN(n))
                        .sort((a, b) => a - b);
                    if (periods.length > 0) updateVisibility('SMA', periods);
                } else {
                    const period = (indicators.SMA as any)?.data?.period ?? (indicators.SMA as any)?.period;
                    if (period) {
                        const key = `SMA_${period}`;
                        if (visibleIndicators[key] === undefined) {
                            nextVisibility[key] = true;
                            hasChanges = true;
                        } else {
                            nextVisibility[key] = visibleIndicators[key];
                        }
                    }
                }
            }

            // 处理WMA
            if (isValidIndicator(indicators.WMA)) {
                const values = getIndicatorValues(indicators.WMA);
                const sample = Array.isArray(values)
                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;
                if (sample?.multiPeriodValues) {
                    const periods = Object.keys(sample.multiPeriodValues || {})
                        .filter(key => key.startsWith('wma_'))
                        .map(key => parseInt(key.replace('wma_', '')))
                        .filter(n => !Number.isNaN(n))
                        .sort((a, b) => a - b);
                    if (periods.length > 0) updateVisibility('WMA', periods);
                } else {
                    const period = (indicators.WMA as any)?.data?.period ?? (indicators.WMA as any)?.period;
                    if (period) {
                        const key = `WMA_${period}`;
                        if (visibleIndicators[key] === undefined) {
                            nextVisibility[key] = true;
                            hasChanges = true;
                        } else {
                            nextVisibility[key] = visibleIndicators[key];
                        }
                    }
                }
            }

            // 处理RSI
            if (isValidIndicator(indicators.RSI)) {
                const values = getIndicatorValues(indicators.RSI);
                const sample = Array.isArray(values)
                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;
                if (sample?.multiPeriodValues) {
                    const periods = Object.keys(sample.multiPeriodValues || {})
                        .filter(key => key.startsWith('rsi_'))
                        .map(key => parseInt(key.replace('rsi_', '')))
                        .filter(n => !Number.isNaN(n))
                        .sort((a, b) => a - b);
                    if (periods.length > 0) updateVisibility('RSI', periods);
                } else {
                    const period = (indicators.RSI as any)?.data?.period ?? (indicators.RSI as any)?.period;
                    if (period) {
                        const key = `RSI_${period}`;
                        if (visibleIndicators[key] === undefined) {
                            nextVisibility[key] = true;
                            hasChanges = true;
                        } else {
                            nextVisibility[key] = visibleIndicators[key];
                        }
                    }
                }
            }

            // 处理BOLL
            if (isValidIndicator(indicators.BOLL)) {
                const values = getIndicatorValues(indicators.BOLL);
                const sample = Array.isArray(values)
                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;
                if (sample?.multiPeriodValues) {
                    const periods = Object.keys(sample.multiPeriodValues || {})
                        .filter(key => key.startsWith('boll_'))
                        .map(key => parseInt(key.replace('boll_', '')))
                        .filter(n => !Number.isNaN(n))
                        .sort((a, b) => a - b);
                    if (periods.length > 0) updateVisibility('BOLL', periods);
                } else {
                    const period = (indicators.BOLL as any)?.data?.period ?? (indicators.BOLL as any)?.period;
                    if (period) {
                        const key = `BOLL_${period}`;
                        if (visibleIndicators[key] === undefined) {
                            nextVisibility[key] = true;
                            hasChanges = true;
                        } else {
                            nextVisibility[key] = visibleIndicators[key];
                        }
                    }
                }
            }

            // 处理其他指标
            ['KDJ', 'CCI', 'ATR', 'OBV', 'ADX'].forEach(typeStr => {
                const type = typeStr as TechnicalIndicator;
                const indicator = getIndicatorByName(typeStr);
                if (!isValidIndicator(indicator)) return;

                const values = getIndicatorValues(indicator);
                const sample = Array.isArray(values)
                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;

                const prefix = type.toLowerCase() + '_';
                if (sample?.multiPeriodValues) {
                    const periods = Object.keys(sample.multiPeriodValues || {})
                        .filter(key => key.startsWith(prefix))
                        .map(key => parseInt(key.replace(prefix, '')))
                        .filter(n => !Number.isNaN(n))
                        .sort((a, b) => a - b);
                    if (periods.length > 0) updateVisibility(type, periods);
                } else {
                    const period = (indicator as any)?.data?.period ?? (indicator as any)?.period;
                    if (period) {
                        const key = `${type}_${period}`;
                        if (visibleIndicators[key] === undefined) {
                            nextVisibility[key] = true;
                            hasChanges = true;
                        } else {
                            nextVisibility[key] = visibleIndicators[key];
                        }
                    }
                }
            });

            if (hasChanges) {
                setInternalVisibleIndicators(prev => ({...prev, ...nextVisibility}));
            }
        }
    }, [indicators, indicatorConfigs]);

    /**
     * Canvas实际渲染尺寸
     * 优先使用props传入的尺寸作为初始值,确保Modal等场景下首次渲染就有有效尺寸
     * ResizeObserver会监听并更新这个值以适应容器变化
     */
    const [actualSize, setActualSize] = useState(() => ({
        width: width > 0 ? width : 800,  // 确保有默认宽度
        height: height > 0 ? height : 600  // 确保有默认高度
    }));

    /**
     * 获取布林带在指定时间戳位置的值（使用时间戳匹配）
     *
     * @param indicator 布林带指标数据
     * @param timestamp K线时间戳
     * @returns 返回匹配的布林带值（上轨、中轨、下轨）
     */
    const getBollingerBands = (indicator: TechnicalIndicatorData | undefined, timestamp: number): {
        upper?: number,
        middle?: number,
        lower?: number
    } => {
        const values = getIndicatorValues(indicator);
        if (!values || values.length === 0) {
            return {};
        }

        // 使用时间戳查找对应的布林带数据点
        const point = values.find((item: any) => item.timestamp === timestamp);

        // 如果找不到匹配的时间戳，返回空对象
        if (!point) {
            return {};
        }

        return {
            upper: point?.upperBand,
            middle: point?.middleBand,
            lower: point?.lowerBand
        };
    };

    /**
     * 获取多周期指标的值（使用时间戳匹配）
     *
     * @param indicator 技术指标数据
     * @param timestamp K线时间戳
     * @returns 返回匹配的指标值数组，按周期升序排列
     */
    const getMultiPeriodIndicatorValues = (indicator: TechnicalIndicatorData | undefined, timestamp: number): {
        period: number;
        value: number | undefined;
    }[] => {
        const values = getIndicatorValues(indicator);
        if (!values || values.length === 0) {
            debugLog('⚠️ [getMultiPeriodIndicatorValues] 没有找到values数据');
            return [];
        }

        debugLog('🔍 [getMultiPeriodIndicatorValues] 查找timestamp:', timestamp, '数据总数:', values.length, '第一个timestamp:', values[0]?.timestamp, '最后一个timestamp:', values[values.length - 1]?.timestamp);

        // 使用时间戳查找对应的技术指标数据点
        const point = values.find((item: any) => item.timestamp === timestamp);

        // 如果找不到匹配的时间戳，返回空数组
        if (!point) {
            debugLog('⚠️ [getMultiPeriodIndicatorValues] 未找到匹配timestamp的数据点');
            return [];
        }

        debugLog('✅ [getMultiPeriodIndicatorValues] 找到匹配数据点, multiPeriodValues:', point.multiPeriodValues);

        // 新的多周期数据格式：multiPeriodValues对象包含不同周期的值
        if (point.multiPeriodValues) {
            const result: { period: number; value: number | undefined }[] = [];

            // 遍历multiPeriodValues对象，提取周期和值
            Object.keys(point.multiPeriodValues).forEach(key => {
                // key格式为 "ema_20", "sma_10" 等
                const match = key.match(/^(ema|sma|wma|rsi|macd|kdj|cci|atr|obv|adx)_(\d+)$/i);
                if (match) {
                    const period = parseInt(match[2]);
                    const value = point.multiPeriodValues![key];
                    if (typeof value === 'number' && Number.isFinite(value)) {
                        result.push({period, value});
                    }
                }
            });

            return result.sort((a, b) => a.period - b.period); // 按周期排序
        }

        // 兼容旧的单周期格式
        if (point.value !== undefined && indicator) {
            return [{period: indicator.data.period || 20, value: point.value}];
        }

        return [];
    };

    const getMultiPeriodKdjValues = (indicator: TechnicalIndicatorData | undefined, timestamp: number): {
        period: number;
        k?: number;
        d?: number;
        j?: number;
    }[] => {
        const values = getIndicatorValues(indicator);
        if (!values || values.length === 0) {
            return [];
        }

        const normalizeTs = (value: unknown): number | undefined => {
            const num = typeof value === 'number' ? value : Number(value);
            if (!Number.isFinite(num) || num <= 0) return undefined;
            return num < 1_000_000_000_000 ? Math.floor(num * 1000) : Math.floor(num);
        };

        const findPointByTimestamp = (ts: number): any | undefined => {
            const normalized = normalizeTs(ts);
            if (normalized === undefined) return undefined;
            const exact = values.find((item: any) => normalizeTs(item.timestamp) === normalized);
            if (exact) return exact;
            return values.find((item: any) => {
                const itemTs = normalizeTs(item.timestamp);
                if (itemTs === undefined) return false;
                return Math.abs(itemTs - normalized) <= 1000;
            });
        };

        const point = findPointByTimestamp(timestamp);
        if (!point) {
            return [];
        }

        const getPeriodFallback = (): number => {
            const period = indicator?.data?.period;
            if (Number.isFinite(Number(period))) return Number(period);
            const periods = (indicator?.data as any)?.periods;
            if (Array.isArray(periods) && periods.length > 0 && Number.isFinite(Number(periods[0]))) {
                return Number(periods[0]);
            }
            return 9;
        };

        if (!point?.multiPeriodValues) {
            const rawK = point.k ?? point.K;
            const rawD = point.d ?? point.D;
            const rawJ = point.j ?? point.J;
            const k = rawK === null || rawK === undefined ? undefined : Number(rawK);
            const d = rawD === null || rawD === undefined ? undefined : Number(rawD);
            const j = rawJ === null || rawJ === undefined ? undefined : Number(rawJ);
            if (!Number.isFinite(k) && !Number.isFinite(d) && !Number.isFinite(j)) {
                return [];
            }
            const period = getPeriodFallback();
            return [{
                period,
                k: Number.isFinite(k) ? k : undefined,
                d: Number.isFinite(d) ? d : undefined,
                j: Number.isFinite(j) ? j : undefined
            }];
        }

        const result: { period: number; k?: number; d?: number; j?: number }[] = [];
        Object.keys(point.multiPeriodValues).forEach(key => {
            const match = key.match(/^kdj_(\d+)/i);
            if (!match) return;

            const period = parseInt(match[1]);
            const raw = point.multiPeriodValues[key];
            const rawType = typeof raw;
            const k = rawType === 'number' || rawType === 'string'
                ? Number(raw)
                : (raw?.k === null || raw?.k === undefined
                    ? (raw?.K === null || raw?.K === undefined ? undefined : Number(raw.K))
                    : Number(raw.k));
            const d = rawType === 'number' || rawType === 'string'
                ? undefined
                : (raw?.d === null || raw?.d === undefined
                    ? (raw?.D === null || raw?.D === undefined ? undefined : Number(raw.D))
                    : Number(raw.d));
            const j = rawType === 'number' || rawType === 'string'
                ? undefined
                : (raw?.j === null || raw?.j === undefined
                    ? (raw?.J === null || raw?.J === undefined ? undefined : Number(raw.J))
                    : Number(raw.j));

            const item: { period: number; k?: number; d?: number; j?: number } = {period};
            if (Number.isFinite(k)) item.k = k;
            if (Number.isFinite(d)) item.d = d;
            if (Number.isFinite(j)) item.j = j;
            result.push(item);
        });

        return result.sort((a, b) => a.period - b.period);
    };

    /**
     * 获取多周期BOLL指标的值（使用时间戳匹配）
     *
     * @param indicator 技术指标数据
     * @param timestamp K线时间戳
     * @param indicatorConfigs 指标配置（用于获取stdev）
     * @returns 返回匹配的BOLL值数组，按周期升序排列
     */
    const getMultiPeriodBollingerBands = (
        indicator: TechnicalIndicatorData | undefined,
        timestamp: number,
        indicatorConfigs?: Record<TechnicalIndicator, IndicatorConfig>
    ): {
        period: number;
        stdev: number;
        upper?: number;
        middle?: number;
        lower?: number;
    }[] => {
        const values = getIndicatorValues(indicator);
        if (!values || values.length === 0) {
            return [];
        }

        // 使用时间戳查找对应的BOLL数据点
        const point = values.find((item: any) => item.timestamp === timestamp);

        // 如果找不到匹配的时间戳，返回空数组
        if (!point) {
            return [];
        }

        // 新的多周期数据格式：multiPeriodValues对象包含不同周期的BOLL值
        if (point.multiPeriodValues) {
            const result: { period: number; stdev: number; upper?: number; middle?: number; lower?: number }[] = [];

            // 遍历multiPeriodValues对象，提取BOLL周期数据
            Object.keys(point.multiPeriodValues).forEach(key => {
                // key格式为 "boll_20" / "boll_20,2" / "boll_20-2"
                const match = key.match(/^boll_(\d+)/i);
                if (match) {
                    const period = parseInt(match[1]);
                    const bollData = point.multiPeriodValues![key] as any;

                    // 从配置中获取该周期对应的标准差
                    let stdev = 2.0; // 默认标准差
                    if (indicatorConfigs?.BOLL?.periods && indicatorConfigs?.BOLL?.stdDevs) {
                        const periodIndex = indicatorConfigs.BOLL.periods.indexOf(period);
                        if (periodIndex !== -1 && indicatorConfigs.BOLL.stdDevs[periodIndex] !== undefined) {
                            stdev = indicatorConfigs.BOLL.stdDevs[periodIndex];
                        }
                    }

                    result.push({
                        period,
                        stdev,
                        upper: bollData?.upperBand ?? bollData?.upper,
                        middle: bollData?.middleBand ?? bollData?.middle,
                        lower: bollData?.lowerBand ?? bollData?.lower
                    });
                }
            });

            return result.sort((a, b) => a.period - b.period); // 按周期排序
        }

        // 兼容旧的单周期格式
        const upper = (point as any).upperBand ?? (point as any).upper;
        const middle = (point as any).middleBand ?? (point as any).middle;
        const lower = (point as any).lowerBand ?? (point as any).lower;
        if ((upper !== undefined || middle !== undefined || lower !== undefined) && indicator) {
            const period = indicator.data.period || 20;
            const stdev = indicator.data.standardDeviation || 2.0;
            return [{
                period,
                stdev,
                upper,
                middle,
                lower
            }];
        }

        return [];
    };

    // 计算价格范围（包含技术指标、标记价格和参考线）
    const calculatePriceRange = () => {
        if (data.length === 0) return {min: 0, max: 100};

        let min = Number.MAX_VALUE;
        let max = Number.MIN_VALUE;

        // 1. K线价格范围
        data.forEach(item => {
            min = Math.min(min, item.low);
            max = Math.max(max, item.high);
        });

        // 2. 标记价格
        if (markPrice) {
            min = Math.min(min, markPrice);
            max = Math.max(max, markPrice);
        }

        // 3. 参考线 (持仓均价、止盈止损,不包含强平价)
        if (referenceLines) {
            if (referenceLines.avgPx && referenceLines.includeAvgPxInRange !== false) {
                min = Math.min(min, referenceLines.avgPx);
                max = Math.max(max, referenceLines.avgPx);
            }
            if (referenceLines.closePx) {
                min = Math.min(min, referenceLines.closePx);
                max = Math.max(max, referenceLines.closePx);
            }
            // 强平价不参与价格范围计算,避免Y轴范围过大导致K线图显示空白
            // if (referenceLines.liquidationPx) {
            //     min = Math.min(min, referenceLines.liquidationPx);
            //     max = Math.max(max, referenceLines.liquidationPx);
            // }
            if (referenceLines.takeProfitPx) {
                min = Math.min(min, referenceLines.takeProfitPx);
                max = Math.max(max, referenceLines.takeProfitPx);
            }
            if (referenceLines.stopLossPx) {
                min = Math.min(min, referenceLines.stopLossPx);
                max = Math.max(max, referenceLines.stopLossPx);
            }
        }

        // 4. 技术指标
        if (indicators) {
            const indicatorRanges: number[] = [];

            // 辅助函数：检查指标是否可见
            const isIndicatorVisible = (type: string, period: number): boolean => {
                const key = `${type}_${period}`;
                // 默认可见，只有明确为false时才不可见
                return visibleIndicators[key] !== false;
            };

            // 辅助函数：处理多周期值
            const processMultiPeriod = (point: any, type: string, prefix: string) => {
                if (point.multiPeriodValues) {
                    Object.keys(point.multiPeriodValues as Record<string, any>).forEach((key: string) => {
                        if (key.startsWith(prefix)) {
                            const period = parseInt(key.split('_')[1]);
                            if (isIndicatorVisible(type, period)) {
                                const val = point.multiPeriodValues[key];
                                // 如果是对象（如BOLL），提取属性
                                if (typeof val === 'object' && val !== null) {
                                    // 兼容 upper/upperBand
                                    const upper = val.upper !== undefined ? val.upper : val.upperBand;
                                    if (upper !== undefined && upper !== null) {
                                        const u = Number(upper);
                                        if (!isNaN(u)) indicatorRanges.push(u);
                                    }
                                    // 兼容 lower/lowerBand
                                    const lower = val.lower !== undefined ? val.lower : val.lowerBand;
                                    if (lower !== undefined && lower !== null) {
                                        const l = Number(lower);
                                        if (!isNaN(l)) indicatorRanges.push(l);
                                    }
                                    // 兼容 middle/middleBand (通常在中轨在上下轨之间，但也可能影响范围)
                                    const middle = val.middle !== undefined ? val.middle : val.middleBand;
                                    if (middle !== undefined && middle !== null) {
                                        const m = Number(middle);
                                        if (!isNaN(m)) indicatorRanges.push(m);
                                    }
                                    // 兼容 value
                                    if (val.value !== undefined && val.value !== null) {
                                        const v = Number(val.value);
                                        if (!isNaN(v)) indicatorRanges.push(v);
                                    }
                                } else if (val !== undefined && val !== null) {
                                    const v = Number(val);
                                    if (!isNaN(v)) indicatorRanges.push(v);
                                }
                            }
                        }
                    });
                }
            };

            // 检查 EMA, SMA, WMA
            ['EMA', 'SMA', 'WMA'].forEach(type => {
                const indicator = indicators[type as keyof typeof indicators];
                const values = getIndicatorValues(indicator);
                if (values) {
                    values.forEach((point: any) => {
                        // 多周期
                        processMultiPeriod(point, type, `${type.toLowerCase()}_`);

                        // 单周期兼容
                        if (point.value !== undefined && point.value !== null && indicator) {
                            const period = indicator.data.period;
                            if (isIndicatorVisible(type, period)) {
                                const v = Number(point.value);
                                if (!isNaN(v)) indicatorRanges.push(v);
                            }
                        }

                        // 旧格式兼容 (sma_20: 123)
                        Object.keys(point as Record<string, any>).forEach((key: string) => {
                            if (key.startsWith(`${type.toLowerCase()}_`)) {
                                const val = point[key];
                                if (val !== undefined && val !== null) {
                                    const v = Number(val);
                                    if (!isNaN(v)) {
                                        // 确保 key 包含数字部分再解析
                                        const parts = key.split('_');
                                        if (parts.length > 1 && !isNaN(parseInt(parts[1]))) {
                                            const period = parseInt(parts[1]);
                                            if (isIndicatorVisible(type, period)) {
                                                indicatorRanges.push(v);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    });
                }
            });

            // 检查 BOLL
            const bollValuesForRange = getIndicatorValues(indicators.BOLL);
            if (bollValuesForRange) {
                bollValuesForRange.forEach((point: any) => {
                    // 多周期 BOLL
                    processMultiPeriod(point, 'BOLL', 'boll_');

                    // 单周期 BOLL
                    if (point.upperBand !== undefined || point.lowerBand !== undefined) {
                        const periodRaw = indicators.BOLL!.data!.period as any;
                        const period = typeof periodRaw === 'string' ? parseInt(periodRaw.split(',')[0]) : periodRaw;

                        if (isIndicatorVisible('BOLL', period)) {
                            if (point.upperBand !== undefined && point.upperBand !== null) {
                                const u = Number(point.upperBand);
                                if (!isNaN(u)) indicatorRanges.push(u);
                            }
                            if (point.lowerBand !== undefined && point.lowerBand !== null) {
                                const l = Number(point.lowerBand);
                                if (!isNaN(l)) indicatorRanges.push(l);
                            }
                        }
                    }
                });
            }

            if (indicatorRanges.length > 0) {
                const indicatorMin = Math.min(...indicatorRanges);
                const indicatorMax = Math.max(...indicatorRanges);

                min = Math.min(min, indicatorMin);
                max = Math.max(max, indicatorMax);
            }
        }

        // 确保参考线（止盈止损）在视图范围内
        if (referenceLines) {
            const refPrices: number[] = [];
            if (referenceLines.takeProfitPx && !isNaN(referenceLines.takeProfitPx)) {
                refPrices.push(referenceLines.takeProfitPx);
            }
            if (referenceLines.stopLossPx && !isNaN(referenceLines.stopLossPx)) {
                refPrices.push(referenceLines.stopLossPx);
            }
            if (referenceLines.avgPx && !isNaN(referenceLines.avgPx)) {
                refPrices.push(referenceLines.avgPx);
            }

            if (refPrices.length > 0) {
                const refMin = Math.min(...refPrices);
                const refMax = Math.max(...refPrices);
                // 只有当参考线价格有效时才扩展范围
                if (refMin > 0) min = Math.min(min, refMin);
                if (refMax > 0) max = Math.max(max, refMax);
            }
        }

        // 添加5%的边距
        const padding = (max - min) * 0.05;
        return {
            min: min - padding,
            max: max + padding
        };
    };

    // 绘制成交量直方图
    const drawVolumeHistogram = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        volumeY: number,
        chartWidth: number,
        volumeHeight: number,
        visibleData: CandlestickData[]
    ) => {
        if (!visibleData || visibleData.length === 0) return;

        // 计算最大成交量
        let maxVolume = 0;
        visibleData.forEach(item => {
            maxVolume = Math.max(maxVolume, item.volume);
        });

        // // console.log('[CandlestickChart] drawVolumeHistogram', { maxVolume, volumeY, volumeHeight, dataCount: visibleData.length });

        // 始终绘制标题，即使没有交易量数据
        ctx.fillStyle = COLORS.text;
        ctx.font = '10px Arial';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'top';
        const lastVolume = visibleData[visibleData.length - 1].volume;
        const volText = maxVolume === 0 ? 'Vol: 0 (No Data)' : `Vol: ${formatEffectiveDecimal(lastVolume, 2)}`;
        ctx.fillText(volText, chartX + 5, volumeY + 5);

        // 绘制分割线（上方）
        ctx.strokeStyle = COLORS.grid;
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(chartX, volumeY);
        ctx.lineTo(chartX + chartWidth, volumeY);
        ctx.stroke();

        if (maxVolume === 0) return;

        const barWidth = Math.max(1, Math.min(20, chartWidth / visibleData.length - 2));
        const barSpacing = (chartWidth - barWidth * visibleData.length) / (visibleData.length + 1);

        visibleData.forEach((item, index) => {
            const x = chartX + barSpacing + index * (barWidth + barSpacing);
            // 归一化高度，保留顶部一点空间用于标题
            const normalizedHeight = (item.volume / maxVolume) * (volumeHeight - 15);
            const y = volumeY + volumeHeight - normalizedHeight;

            const isPositive = item.close > item.open;
            // 使用半透明颜色，避免遮挡
            const color = isPositive ? 'rgba(82, 196, 26, 0.5)' : 'rgba(255, 77, 79, 0.5)';

            ctx.fillStyle = color;
            // 只有当volume > 0时才绘制
            if (item.volume > 0) {
                ctx.fillRect(x, y, barWidth, normalizedHeight);
            }
        });
    };

    // 绘制技术指标
    const drawIndicators = (
        ctx: CanvasRenderingContext2D,
        chartY: number,
        mainChartHeight: number,
        rsiChartHeight: number,
        chartX: number,
        chartWidth: number,
        rsiY: number,
        timestampToX: Map<number, number>
    ) => {
        if (!indicators || !data || data.length === 0) return;

        // 重新计算主图和副图区域（因为这是独立的作用域）
        const rsiIndicator = getIndicatorByName('RSI');
        const adxIndicator = getIndicatorByName('ADX');
        const kdjIndicator = getIndicatorByName('KDJ');
        const cciIndicator = getIndicatorByName('CCI');
        const atrIndicator = getIndicatorByName('ATR');
        const obvIndicator = getIndicatorByName('OBV');
        const emaIndicator = getIndicatorByName('EMA');
        const bollIndicator = getIndicatorByName('BOLL');
        const smaIndicator = getIndicatorByName('SMA');
        const wmaIndicator = getIndicatorByName('WMA');

        const hasRSI = isValidIndicator(rsiIndicator);
        const hasADX = isValidIndicator(adxIndicator);
        const hasKDJ = isValidIndicator(kdjIndicator);
        const hasCCI = isValidIndicator(cciIndicator);
        const hasATR = isValidIndicator(atrIndicator);
        const hasOBV = isValidIndicator(obvIndicator);
        const hasEMA = isValidIndicator(emaIndicator);
        const hasBOLL = isValidIndicator(bollIndicator);
        const hasSMA = isValidIndicator(smaIndicator);
        const hasWMA = isValidIndicator(wmaIndicator);

        // 如果没有任何有效指标，直接返回
        if (!hasRSI && !hasADX && !hasKDJ && !hasCCI && !hasATR && !hasOBV && !hasEMA && !hasBOLL && !hasSMA && !hasWMA) {
            debugLog('没有有效的指标数据需要绘制');
            return;
        }

        // 计算价格范围（用于主图指标）
        const priceRange = calculatePriceRange();

        // 统一的坐标转换函数 - 移除额外的padding，与K线和网格线保持一致
        const priceToY = (price: number, mainChartY: number, mainChartHeight: number) => {
            const normalizedHeight = (price - priceRange.min) / (priceRange.max - priceRange.min);
            return mainChartY + mainChartHeight * (1 - normalizedHeight);
        };

        // 辅助函数：绘制两端虚线的线段
        const drawDashedEndsLine = (points: { x: number, y: number }[]) => {
            if (points.length < 2) return;

            // 绘制第一段（虚线）
            ctx.beginPath();
            ctx.setLineDash([3, 3]);
            ctx.moveTo(points[0].x, points[0].y);
            ctx.lineTo(points[1].x, points[1].y);
            ctx.stroke();

            // 绘制中间段（实线）
            if (points.length > 3) {
                ctx.beginPath();
                ctx.setLineDash([]);
                ctx.moveTo(points[1].x, points[1].y);
                for (let i = 2; i < points.length - 1; i++) {
                    ctx.lineTo(points[i].x, points[i].y);
                }
                ctx.stroke();
            }

            // 绘制最后一段（虚线）
            if (points.length > 2) {
                ctx.beginPath();
                ctx.setLineDash([3, 3]);
                ctx.moveTo(points[points.length - 2].x, points[points.length - 2].y);
                ctx.lineTo(points[points.length - 1].x, points[points.length - 1].y);
                ctx.stroke();
            }

            ctx.setLineDash([]); // 重置为实线
        };

        // 绘制多周期技术指标（EMA、SMA、WMA）
        const drawMultiPeriodIndicator = (
            indicatorType: 'EMA' | 'SMA' | 'WMA' | 'RSI' | 'ADX' | 'KDJ',
            indicatorData: any,
            defaultColor: string,
            isRSI: boolean = false,
            rsiY?: number,
            rsiChartHeight?: number
        ) => {
            const values = getIndicatorValues(indicatorData);
            if (!values) return;

            // 检查是否为多周期数据
            const isMultiPeriod = values.length > 0 && values[0].multiPeriodValues;
            // 从multiPeriodValues中提取周期
            let periods: number[] = [];
            if (isMultiPeriod && values[0].multiPeriodValues) {
                const prefixMap: { [key: string]: string } = {
                    EMA: 'ema_', SMA: 'sma_', WMA: 'wma_', RSI: 'rsi_', ADX: 'adx_'
                };
                const prefix = prefixMap[indicatorType] || indicatorType.toLowerCase() + '_';
                periods = Object.keys(values[0].multiPeriodValues)
                    .filter(key => key.startsWith(prefix))
                    .map(key => parseInt(key.replace(prefix, '')))
                    .sort((a, b) => a - b);
            }

            if (shouldDebug() && indicatorType === 'KDJ') {
                const first = values[0];
                debugLog('[CandlestickChart][KDJ] values length:', values.length);
                debugLog('[CandlestickChart][KDJ] first timestamp:', first?.timestamp);
                debugLog('[CandlestickChart][KDJ] first multiPeriodValues keys:', first?.multiPeriodValues ? Object.keys(first.multiPeriodValues) : []);
                debugLog('[CandlestickChart][KDJ] extracted periods:', periods);
            }

            const getPoint = (val: number, timestamp: number) => {
                const x = timestampToX.get(timestamp);
                if (x === undefined) return null;

                let y;
                if (isRSI && rsiY && rsiChartHeight) {
                    y = rsiY + (rsiChartHeight - 40) * (1 - val / 100) + 20;
                } else {
                    y = priceToY(val, chartY, mainChartHeight);
                }
                return {x, y};
            };

            // 按时间戳排序
            const sortedValues = [...values].sort((a: any, b: any) => a.timestamp - b.timestamp);

            if (isMultiPeriod && periods.length > 0) {
                // 多周期模式：绘制多条不同颜色的指标线
                const colorArray = (COLORS.indicatorColors as any)[indicatorType] || [defaultColor, '#00d2d3', '#52c41a'];

                periods.forEach((period: number, periodIndex: number) => {
                    const visibilityKey = `${indicatorType}_${period}`;
                    if (visibleIndicators[visibilityKey] === false) return; // 如果不可见，跳过绘制

                    const key = indicatorType.toLowerCase() + '_' + period;
                    const lineColor = colorArray[periodIndex % colorArray.length];

                    ctx.strokeStyle = lineColor;
                    ctx.lineWidth = periodIndex === 0 ? 2 : 1.5; // 第一条线稍粗一些

                    const points: { x: number, y: number }[] = [];
                    sortedValues.forEach((point: any) => {
                        const raw = point?.multiPeriodValues?.[key] ?? point?.[key];
                        const num = raw === null || raw === undefined ? Number.NaN : Number(raw);
                        if (Number.isFinite(num)) {
                            const p = getPoint(num, point.timestamp);
                            if (p) points.push(p);
                        }
                    });

                    if (shouldDebug() && indicatorType === 'KDJ') {
                        debugLog('[CandlestickChart][KDJ] period', period, 'points', points.length, 'visible', visibleIndicators[visibilityKey]);
                    }
                    drawDashedEndsLine(points);
                });
            } else {
                // 单周期模式：使用原有的绘制逻辑
                const period = indicatorData.data.period || 20;
                const visibilityKey = `${indicatorType}_${period}`;
                if (visibleIndicators[visibilityKey] === false) return; // 如果不可见，跳过绘制

                ctx.strokeStyle = defaultColor;
                ctx.lineWidth = 2;

                const points: { x: number, y: number }[] = [];
                sortedValues.forEach((point: any) => {
                    const num = point?.value === null || point?.value === undefined ? Number.NaN : Number(point.value);
                    if (Number.isFinite(num)) {
                        const p = getPoint(num, point.timestamp);
                        if (p) points.push(p);
                    }
                });

                drawDashedEndsLine(points);
            }
        };

        const drawDynamicMultiPeriodIndicator = (
            indicatorType: 'CCI' | 'ATR' | 'OBV',
            indicatorData: any,
            defaultColor: string,
            subY: number,
            subHeight: number,
            drawZeroLine: boolean
        ) => {
            const values = getIndicatorValues(indicatorData);
            if (!values) return;
            if (subHeight <= 0) return;

            const sample = values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0);
            if (!sample?.multiPeriodValues) return;

            const prefix = indicatorType.toLowerCase() + '_';
            const periods = Object.keys(sample.multiPeriodValues)
                .filter(key => key.startsWith(prefix))
                .map(key => parseInt(key.replace(prefix, '')))
                .filter(n => !Number.isNaN(n))
                .sort((a, b) => a - b);
            if (periods.length === 0) return;

            const sortedValues = [...values].sort((a: any, b: any) => a.timestamp - b.timestamp);

            let min = Number.POSITIVE_INFINITY;
            let max = Number.NEGATIVE_INFINITY;
            periods.forEach(period => {
                const visibilityKey = `${indicatorType}_${period}`;
                if (visibleIndicators[visibilityKey] === false) return;
                const key = prefix + period;
                sortedValues.forEach((point: any) => {
                    const raw = point?.multiPeriodValues?.[key];
                    const num = raw === null || raw === undefined ? Number.NaN : Number(raw);
                    if (Number.isFinite(num)) {
                        min = Math.min(min, num);
                        max = Math.max(max, num);
                    }
                });
            });

            if (!Number.isFinite(min) || !Number.isFinite(max)) return;
            if (min === max) {
                max = min + 1;
            }

            const valToY = (v: number) => {
                const usable = Math.max(0, subHeight - 20);
                const t = (v - min) / (max - min);
                return subY + usable * (1 - t) + 10;
            };

            if (drawZeroLine && min <= 0 && max >= 0) {
                const y0 = valToY(0);
                ctx.strokeStyle = COLORS.grid;
                ctx.lineWidth = 1;
                ctx.setLineDash([3, 3]);
                ctx.beginPath();
                ctx.moveTo(chartX, y0);
                ctx.lineTo(chartX + chartWidth, y0);
                ctx.stroke();
                ctx.setLineDash([]);
            }

            const colorArray = (COLORS.indicatorColors as any)[indicatorType] || [defaultColor, '#00d2d3', '#52c41a'];
            periods.forEach((period, periodIndex) => {
                const visibilityKey = `${indicatorType}_${period}`;
                if (visibleIndicators[visibilityKey] === false) return;

                const key = prefix + period;
                const lineColor = colorArray[periodIndex % colorArray.length];
                ctx.strokeStyle = lineColor;
                ctx.lineWidth = periodIndex === 0 ? 2 : 1.5;

                const points: { x: number, y: number }[] = [];
                sortedValues.forEach((point: any) => {
                    const raw = point?.multiPeriodValues?.[key];
                    const num = raw === null || raw === undefined ? Number.NaN : Number(raw);
                    if (Number.isFinite(num)) {
                        const x = timestampToX.get(point.timestamp);
                        if (x !== undefined) {
                            points.push({x, y: valToY(num)});
                        }
                    }
                });

                drawDashedEndsLine(points);
            });
        };

        const drawMultiPeriodKdjIndicator = (
            indicatorData: any,
            subY: number,
            subHeight: number
        ) => {
            const values = getIndicatorValues(indicatorData);
            if (!values) return;
            if (subHeight <= 0) return;

            const sample = values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0);
            if (!sample?.multiPeriodValues) return;

            const periods = Object.keys(sample.multiPeriodValues)
                .filter(key => /^kdj_\d+$/i.test(key))
                .map(key => parseInt(key.replace(/kdj_/i, '')))
                .filter(n => !Number.isNaN(n))
                .sort((a, b) => a - b);
            if (periods.length === 0) return;

            if (shouldDebug()) {
                debugLog('[CandlestickChart][KDJ] extracted periods:', periods);
            }

            const valToY = (v: number) => subY + (subHeight - 40) * (1 - v / 100) + 20;
            const sortedValues = [...values].sort((a: any, b: any) => a.timestamp - b.timestamp);
            const colorArray = (COLORS.indicatorColors as any).KDJ || ['#13c2c2', '#9254de', '#fa8c16', '#52c41a', '#f5222d', '#1890ff', '#722ed1'];

            periods.forEach((period, periodIndex) => {
                const visibilityKey = `KDJ_${period}`;
                if (visibleIndicators[visibilityKey] === false) return;

                const key = `kdj_${period}`;
                const baseColor = colorArray[periodIndex % colorArray.length];

                const pointsK: { x: number, y: number, k: number, d: number }[] = [];
                const pointsD: { x: number, y: number }[] = [];
                const pointsJ: { x: number, y: number }[] = [];

                sortedValues.forEach((point: any) => {
                    const x = timestampToX.get(point.timestamp);
                    if (x === undefined) return;
                    const raw = point?.multiPeriodValues?.[key];
                    const rawType = typeof raw;
                    const k = rawType === 'number' || rawType === 'string'
                        ? Number(raw)
                        : (raw?.k === null || raw?.k === undefined ? Number.NaN : Number(raw.k));
                    const d = rawType === 'number' || rawType === 'string'
                        ? Number.NaN
                        : (raw?.d === null || raw?.d === undefined ? Number.NaN : Number(raw.d));
                    const j = rawType === 'number' || rawType === 'string'
                        ? Number.NaN
                        : (raw?.j === null || raw?.j === undefined ? Number.NaN : Number(raw.j));

                    if (Number.isFinite(k) && Number.isFinite(d)) {
                        pointsK.push({x, y: valToY(k), k, d});
                        pointsD.push({x, y: valToY(d)});
                    } else if (Number.isFinite(k)) {
                        pointsK.push({x, y: valToY(k), k, d: Number.NaN});
                    }

                    if (Number.isFinite(j)) {
                        pointsJ.push({x, y: valToY(j)});
                    }
                });

                if (shouldDebug()) {
                    debugLog('[CandlestickChart][KDJ] period', period, 'pointsK', pointsK.length, 'pointsD', pointsD.length, 'pointsJ', pointsJ.length);
                }

                if (pointsK.length >= 2) {
                    ctx.strokeStyle = baseColor;
                    ctx.lineWidth = 2;
                    drawDashedEndsLine(pointsK.map(p => ({x: p.x, y: p.y})));
                }

                if (pointsD.length >= 2) {
                    ctx.strokeStyle = hexToRgba(baseColor, 0.7);
                    ctx.lineWidth = 1.5;
                    ctx.setLineDash([4, 3]);
                    drawDashedEndsLine(pointsD);
                    ctx.setLineDash([]);
                }

                if (pointsJ.length >= 2) {
                    ctx.strokeStyle = hexToRgba(baseColor, 0.5);
                    ctx.lineWidth = 1.2;
                    ctx.setLineDash([2, 4]);
                    drawDashedEndsLine(pointsJ);
                    ctx.setLineDash([]);
                }

                ctx.fillStyle = baseColor;
                for (let i = 1; i < pointsK.length; i++) {
                    const prev = pointsK[i - 1];
                    const curr = pointsK[i];
                    if (!Number.isFinite(prev.d) || !Number.isFinite(curr.d)) continue;

                    const goldenCross = prev.k < prev.d && curr.k >= curr.d;
                    const deathCross = prev.k > prev.d && curr.k <= curr.d;
                    if (!goldenCross && !deathCross) continue;

                    ctx.beginPath();
                    ctx.fillStyle = goldenCross ? COLORS.positive : COLORS.negative;
                    ctx.arc(curr.x, curr.y, 3, 0, Math.PI * 2);
                    ctx.fill();
                }
            });
        };

        // 绘制EMA（支持多周期）
        if (hasEMA) {
            drawMultiPeriodIndicator('EMA', indicators.EMA, COLORS.ema);
        }

        // 绘制BOLL（支持多周期）
        if (hasBOLL) {
            debugLog('[CandlestickChart] ========== 开始绘制BOLL指标 ==========');
            const bollValues = getIndicatorValues(indicators.BOLL);

            if (!bollValues || bollValues.length === 0) {
                debugLog('[CandlestickChart] BOLL数据为空，跳过绘制');
                return;
            }

            debugLog('[CandlestickChart] BOLL基础数据:', {
                success: isValidIndicator(indicators.BOLL),
                valuesLength: bollValues.length,
                firstValue: bollValues[0],
                lastValue: bollValues[bollValues.length - 1]
            });

            // 检查是否为多周期数据
            const isMultiPeriod = bollValues[0].multiPeriodValues;

            debugLog('[CandlestickChart] BOLL多周期检测:', {
                isMultiPeriod,
                hasMultiPeriodValues: !!bollValues[0]?.multiPeriodValues,
                multiPeriodKeys: isMultiPeriod ? Object.keys(bollValues[0].multiPeriodValues || {}) : []
            });

            if (isMultiPeriod) {
                // 多周期模式：提取所有周期键名（包含完整格式，如 boll_20,2）
                const periodKeys = Object.keys(bollValues[0].multiPeriodValues || {})
                    .filter(key => key.startsWith('boll_'))
                    .sort();

                debugLog('[CandlestickChart] 检测到的BOLL周期键:', periodKeys);

                // 为每个周期绘制独立的BOLL带
                periodKeys.forEach((periodKey, periodIndex) => {
                    // 从periodKey中提取周期数字（例如从"boll_20,2"提取20）
                    const periodMatch = periodKey.match(/boll_(\d+)/);
                    const period = periodMatch ? parseInt(periodMatch[1]) : parseInt(periodKey.replace('boll_', ''));

                    const visibilityKey = `BOLL_${period}`;
                    if (visibleIndicators[visibilityKey] === false) return; // 如果不可见，跳过绘制

                    // 获取该周期的颜色
                    const colorArray = COLORS.indicatorColors.BOLL || [COLORS.bollUpper];
                    const lineColor = colorArray[periodIndex % colorArray.length];

                    // 从multiPeriodValues中提取该周期的数据
                    const periodBollData = bollValues.map((point: any) => {
                        const bollData = point.multiPeriodValues?.[periodKey];
                        return {
                            timestamp: point.timestamp,
                            upperBand: bollData?.upper,      // 字段映射：upper -> upperBand
                            middleBand: bollData?.middle,    // 字段映射：middle -> middleBand
                            lowerBand: bollData?.lower       // 字段映射：lower -> lowerBand
                        };
                    }).filter((v: any) => v.upperBand !== undefined);

                    debugLog(`[CandlestickChart] 周期${period}(${periodKey})的BOLL数据:`, {
                        原始数据量: bollValues.length,
                        过滤后数据量: periodBollData.length,
                        第一条: periodBollData[0],
                        最后一条: periodBollData[periodBollData.length - 1]
                    });

                    const sortedBollValues = [...periodBollData].sort((a, b) => a.timestamp - b.timestamp);

                    // 绘制该周期的BOLL带（使用对应周期颜色，20%透明度）
                    const opacity = 0.2; // 多周期BOLL使用20%透明度
                    ctx.fillStyle = hexToRgba(lineColor, opacity); // 使用对应周期颜色
                    ctx.beginPath();

                    // 绘制上轨路径 (正向)
                    let hasStarted = false;
                    sortedBollValues.forEach((point) => {
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined) return;

                        const y = priceToY(point.upperBand!, chartY, mainChartHeight);
                        if (!hasStarted) {
                            ctx.moveTo(x, y);
                            hasStarted = true;
                        } else {
                            ctx.lineTo(x, y);
                        }
                    });

                    // 绘制下轨路径 (反向)
                    for (let i = sortedBollValues.length - 1; i >= 0; i--) {
                        const point = sortedBollValues[i];
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined) continue;

                        const y = priceToY(point.lowerBand!, chartY, mainChartHeight);
                        if (hasStarted) ctx.lineTo(x, y);
                    }

                    ctx.closePath();
                    ctx.fill();

                    // 绘制上轨
                    ctx.strokeStyle = lineColor;
                    ctx.lineWidth = 1;
                    const upperPoints: { x: number, y: number }[] = [];
                    sortedBollValues.forEach((point) => {
                        if (point.upperBand !== null && point.upperBand !== undefined) {
                            const x = timestampToX.get(point.timestamp);
                            if (x !== undefined) {
                                const y = priceToY(point.upperBand, chartY, mainChartHeight);
                                upperPoints.push({x, y});
                            }
                        }
                    });
                    drawDashedEndsLine(upperPoints);

                    // 绘制中轨
                    const middlePoints: { x: number, y: number }[] = [];
                    sortedBollValues.forEach((point) => {
                        if (point.middleBand !== null && point.middleBand !== undefined) {
                            const x = timestampToX.get(point.timestamp);
                            if (x !== undefined) {
                                const y = priceToY(point.middleBand, chartY, mainChartHeight);
                                middlePoints.push({x, y});
                            }
                        }
                    });
                    drawDashedEndsLine(middlePoints);

                    // 绘制下轨
                    const lowerPoints: { x: number, y: number }[] = [];
                    sortedBollValues.forEach((point) => {
                        if (point.lowerBand !== null && point.lowerBand !== undefined) {
                            const x = timestampToX.get(point.timestamp);
                            if (x !== undefined) {
                                const y = priceToY(point.lowerBand, chartY, mainChartHeight);
                                lowerPoints.push({x, y});
                            }
                        }
                    });
                    drawDashedEndsLine(lowerPoints);
                });
            } else {
                // 单周期模式（向后兼容）
                // 尝试从旧格式获取period，新格式可能没有
                const period = (indicators.BOLL as any)?.data?.period || 20; // 默认20
                const visibilityKey = `BOLL_${period}`;

                if (visibleIndicators[visibilityKey] !== false) {
                    const sortedBollValues = [...bollValues].sort((a, b) => a.timestamp - b.timestamp);

                    // 绘制UB与LB之间的背景色 (30%透明度)
                    ctx.fillStyle = 'rgba(153, 153, 153, 0.3)';
                    ctx.beginPath();

                    // 绘制上轨路径 (正向)
                    let hasStarted = false;
                    sortedBollValues.forEach((point) => {
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined) return;

                        const y = priceToY(point.upperBand!, chartY, mainChartHeight);
                        if (!hasStarted) {
                            ctx.moveTo(x, y);
                            hasStarted = true;
                        } else {
                            ctx.lineTo(x, y);
                        }
                    });

                    // 绘制下轨路径 (反向)
                    for (let i = sortedBollValues.length - 1; i >= 0; i--) {
                        const point = sortedBollValues[i];
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined) continue;

                        const y = priceToY(point.lowerBand!, chartY, mainChartHeight);
                        if (hasStarted) ctx.lineTo(x, y);
                    }

                    ctx.closePath();
                    ctx.fill();

                    // 绘制上轨
                    ctx.strokeStyle = COLORS.bollUpper;
                    ctx.lineWidth = 1;
                    const upperPoints: { x: number, y: number }[] = [];
                    sortedBollValues.forEach((point) => {
                        if (point.upperBand !== null && point.upperBand !== undefined) {
                            const x = timestampToX.get(point.timestamp);
                            if (x !== undefined) {
                                const y = priceToY(point.upperBand, chartY, mainChartHeight);
                                upperPoints.push({x, y});
                            }
                        }
                    });
                    drawDashedEndsLine(upperPoints);

                    // 绘制中轨
                    ctx.strokeStyle = COLORS.bollMiddle;
                    ctx.lineWidth = 1;
                    ctx.setLineDash([5, 5]);
                    ctx.beginPath();
                    let hasMovedMiddle = false;
                    sortedBollValues.forEach((point) => {
                        if (point.middleBand !== null && point.middleBand !== undefined) {
                            const x = timestampToX.get(point.timestamp);
                            if (x !== undefined) {
                                const y = priceToY(point.middleBand, chartY, mainChartHeight);
                                if (!hasMovedMiddle) {
                                    ctx.moveTo(x, y);
                                    hasMovedMiddle = true;
                                } else {
                                    ctx.lineTo(x, y);
                                }
                            }
                        }
                    });
                    ctx.stroke();
                    ctx.setLineDash([]);

                    // 绘制下轨
                    ctx.strokeStyle = COLORS.bollLower;
                    ctx.lineWidth = 1;
                    const lowerPoints: { x: number, y: number }[] = [];
                    sortedBollValues.forEach((point) => {
                        if (point.lowerBand !== null && point.lowerBand !== undefined) {
                            const x = timestampToX.get(point.timestamp);
                            if (x !== undefined) {
                                const y = priceToY(point.lowerBand, chartY, mainChartHeight);
                                lowerPoints.push({x, y});
                            }
                        }
                    });
                    drawDashedEndsLine(lowerPoints);
                }
            }
        }

        // 绘制SMA（支持多周期）
        if (hasSMA) {
            drawMultiPeriodIndicator('SMA', indicators.SMA, COLORS.sma);
        }

        // 绘制WMA（支持多周期）
        if (hasWMA) {
            drawMultiPeriodIndicator('WMA', indicators.WMA, COLORS.wma);
        }

        if ((hasRSI || hasADX || hasKDJ || hasCCI || hasATR || hasOBV) && rsiChartHeight > 0) {
            // RSI副图的Y坐标已作为参数传入 (rsiY)

            // 布局修复完成 - RSI副图不再重叠主图

            // 在RSI副图上方添加增强分隔线
            ctx.strokeStyle = COLORS.grid;
            ctx.lineWidth = 2;  // 增加线条粗细，更明显
            ctx.setLineDash([5, 3]);  // 虚线样式，更好地区分
            ctx.beginPath();
            const separatorX = chartX;  // 使用正确的chartX
            const separatorWidth = chartWidth;  // 使用正确的chartWidth
            ctx.moveTo(separatorX, rsiY);
            ctx.lineTo(separatorX + separatorWidth, rsiY);
            ctx.stroke();
            ctx.setLineDash([]);  // 重置虚线样式

            // 绘制RSI背景
            ctx.fillStyle = 'rgba(31, 31, 31, 0.5)';
            ctx.fillRect(chartX, rsiY, chartWidth, rsiChartHeight);

            if (hasRSI || hasKDJ) {
                ctx.strokeStyle = COLORS.rsiOverbought;
                ctx.lineWidth = 1;
                ctx.setLineDash([3, 3]);
                ctx.beginPath();
                ctx.moveTo(chartX, rsiY + (rsiChartHeight - 40) * (1 - 70 / 100) + 20);
                ctx.lineTo(chartX + chartWidth, rsiY + (rsiChartHeight - 40) * (1 - 70 / 100) + 20);
                ctx.stroke();

                ctx.strokeStyle = COLORS.rsiOversold;
                ctx.beginPath();
                ctx.moveTo(chartX, rsiY + (rsiChartHeight - 40) * (1 - 30 / 100) + 20);
                ctx.lineTo(chartX + chartWidth, rsiY + (rsiChartHeight - 40) * (1 - 30 / 100) + 20);
                ctx.stroke();
                ctx.setLineDash([]);
            }

            // 绘制RSI线（使用多周期渲染函数）
            if (hasRSI) {
                drawMultiPeriodIndicator('RSI', rsiIndicator, COLORS.rsi, true, rsiY, rsiChartHeight);
            }
            if (hasADX) {
                drawMultiPeriodIndicator('ADX', adxIndicator, '#fa8c16', true, rsiY, rsiChartHeight);
            }
            if (hasKDJ) {
                drawMultiPeriodKdjIndicator(kdjIndicator, rsiY, rsiChartHeight);
            }
            if (hasCCI) {
                drawDynamicMultiPeriodIndicator('CCI', cciIndicator, '#fadb14', rsiY, rsiChartHeight, true);
            }
            if (hasATR) {
                drawDynamicMultiPeriodIndicator('ATR', atrIndicator, '#722ed1', rsiY, rsiChartHeight, false);
            }
            if (hasOBV) {
                drawDynamicMultiPeriodIndicator('OBV', obvIndicator, '#1890ff', rsiY, rsiChartHeight, true);
            }
        }
    };

    // 绘制K线图
    const drawChart = () => {
        const canvas = canvasRef.current;
        if (!canvas) {
            return;
        }

        const ctx = canvas.getContext('2d');
        if (!ctx) {
            return;
        }

        const currentWidth = actualSize.width;
        const currentHeight = actualSize.height;

        // 清空画布
        ctx.clearRect(0, 0, currentWidth, currentHeight);

        // 设置高DPI支持
        const dpr = window.devicePixelRatio || 1;
        canvas.width = currentWidth * dpr;
        canvas.height = currentHeight * dpr;
        ctx.scale(dpr, dpr);

        // 绘制背景
        ctx.fillStyle = COLORS.background;
        ctx.fillRect(0, 0, currentWidth, currentHeight);

        if (data.length === 0) {
            debugWarn('[CandlestickChart] ✗ K线数据为空,显示"暂无数据"');
            ctx.fillStyle = COLORS.text;
            ctx.font = '14px Arial';
            ctx.textAlign = 'center';
            ctx.fillText('暂无数据', currentWidth / 2, currentHeight / 2);
            return;
        }

        debugLog('[CandlestickChart] ✓ 开始绘制K线图...');

        // 使用统一的边距计算，确保与鼠标交互逻辑一致
        const margins = getChartMargins();
        const adjustedWidth = margins.adjustedWidth;
        const chartWidth = margins.chartWidth;
        const chartX = margins.left;
        const chartY = margins.top;
        const chartHeight = margins.chartHeight;

        const priceRange = calculatePriceRange();

        debugLog('[CandlestickChart] 技术指标验证:');
        debugLog('  - indicators存在?', !!indicators);
        if (indicators) {
            Object.keys(indicators).forEach(key => {
                const ind = (indicators as any)[key];
                debugLog(`  - ${key}:`, {
                    exists: !!ind,
                    metricName: ind?.metricName,
                    hasValues: ind?.values ? Array.isArray(ind.values) : false,
                    valuesLength: ind?.values?.length || 0,
                    success: ind?.success,
                    hasData: !!ind?.data,
                    dataValuesLength: ind?.data?.values?.length || 0,
                    isValid: isValidIndicator(ind)
                });
            });
        }

        const rsiIndicator = getIndicatorByName('RSI');
        const adxIndicator = getIndicatorByName('ADX');
        const kdjIndicator = getIndicatorByName('KDJ');
        const cciIndicator = getIndicatorByName('CCI');
        const atrIndicator = getIndicatorByName('ATR');
        const obvIndicator = getIndicatorByName('OBV');

        if (shouldDebug()) {
            debugLog('[CandlestickChart] indicators keys:', indicators ? Object.keys(indicators) : []);
            debugLog('[CandlestickChart] lookup KDJ:', {
                hasKDJ: !!kdjIndicator,
                metricName: kdjIndicator?.metricName,
                success: kdjIndicator?.success,
                hasValues: Array.isArray(kdjIndicator?.values),
                hasDataValues: Array.isArray(kdjIndicator?.data?.values)
            });
        }

        // 计算主图和副图区域
        const hasRSI = isValidIndicator(rsiIndicator);
        const hasADX = isValidIndicator(adxIndicator);
        const hasKDJ = isValidIndicator(kdjIndicator);
        const hasCCI = isValidIndicator(cciIndicator);
        const hasATR = isValidIndicator(atrIndicator);
        const hasOBV = isValidIndicator(obvIndicator);
        const hasRSIChart = hasRSI || hasADX || hasKDJ || hasCCI || hasATR || hasOBV;
        const hasMACD = isValidIndicator(getIndicatorByName('MACD'));
        const macdVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('MACD_'));
        const showMACD = macdVisibilityKeys.length === 0 ? true : macdVisibilityKeys.some(key => !!visibleIndicators[key]);
        debugLog('[CandlestickChart] hasRSI:', hasRSI, 'hasADX:', hasADX, 'hasMACD:', hasMACD);

        // 重新定义可用绘制区域
        const availableHeight = currentHeight - CHART_LAYOUT.topMargin - CHART_LAYOUT.bottomMargin;

        // 计算分隔符占用的高度
        const separatorCount = (hasRSIChart ? 1 : 0) + (hasMACD ? 1 : 0) + 1;
        const totalSeparatorHeight = separatorCount * CHART_LAYOUT.separatorMargin;
        const heightForCharts = Math.max(0, availableHeight - totalSeparatorHeight);

        // 分配高度
        let mainChartHeight = heightForCharts * CHART_LAYOUT.mainChartRatio;
        const volumeChartHeight = heightForCharts * CHART_LAYOUT.volumeChartRatio;
        let rsiChartHeight = hasRSIChart ? heightForCharts * CHART_LAYOUT.rsiChartRatio : 0;
        let macdChartHeight = hasMACD ? heightForCharts * CHART_LAYOUT.macdChartRatio : 0;

        // 如果没有RSI，将RSI的空间分配给主图
        if (!hasRSIChart) {
            mainChartHeight += heightForCharts * CHART_LAYOUT.rsiChartRatio;
        }
        // 如果没有MACD，将MACD的空间分配给主图
        if (!hasMACD) {
            mainChartHeight += heightForCharts * CHART_LAYOUT.macdChartRatio;
        }

        // 计算各图表的Y坐标
        const volumeY = chartY + mainChartHeight + CHART_LAYOUT.separatorMargin;
        const rsiY = volumeY + volumeChartHeight + CHART_LAYOUT.separatorMargin;
        const macdY = rsiY + rsiChartHeight + CHART_LAYOUT.separatorMargin;

        // 布局优化完成 - 调试信息已移除
        const barWidth = Math.max(1, Math.min(20, chartWidth / displayData.length - 2));
        const barSpacing = (chartWidth - barWidth * displayData.length) / (displayData.length + 1);

        // 绘制网格线和价格标签（使用displayData确保X轴标签与K线顺序一致）
        drawGridAndLabels(ctx, chartX, chartY, chartWidth, chartHeight, priceRange, mainChartHeight, displayData);

        // 创建时间戳到X坐标的映射，用于对齐技术指标
        const timestampToX = new Map<number, number>();
        displayData.forEach((item, index) => {
            const x = chartX + barSpacing + index * (barWidth + barSpacing) + barWidth / 2;
            timestampToX.set(item.timestamp, x);
        });

        // 绘制K线
        displayData.forEach((item, index) => {
            const x = chartX + barSpacing + index * (barWidth + barSpacing);
            // 修复：使用mainChartHeight而不是chartHeight，确保K线只在主图区域绘制
            const yHigh = chartY + mainChartHeight * (1 - (item.high - priceRange.min) / (priceRange.max - priceRange.min));
            const yLow = chartY + mainChartHeight * (1 - (item.low - priceRange.min) / (priceRange.max - priceRange.min));
            const yOpen = chartY + mainChartHeight * (1 - (item.open - priceRange.min) / (priceRange.max - priceRange.min));
            const yClose = chartY + mainChartHeight * (1 - (item.close - priceRange.min) / (priceRange.max - priceRange.min));

            // 确定颜色
            const isPositive = item.close > item.open;
            const color = isPositive ? COLORS.positive : COLORS.negative;

            // 判断是否为未结束的K线（confirm=0）
            const isUnfinishedKline = item.confirm === 0;

            // 设置线条样式
            ctx.strokeStyle = color;
            if (isUnfinishedKline) {
                // 未结束的K线使用虚线
                ctx.setLineDash([2, 2]); // 虚线样式
                ctx.lineWidth = 1;
            } else {
                // 已结束的K线使用实线
                ctx.setLineDash([]); // 实线样式
                ctx.lineWidth = 1;
            }

            // 绘制上影线
            ctx.beginPath();
            ctx.moveTo(x + barWidth / 2, Math.min(yHigh, yLow));
            ctx.lineTo(x + barWidth / 2, Math.max(yHigh, yLow));
            ctx.stroke();

            // 绘制下影线
            ctx.beginPath();
            ctx.moveTo(x + barWidth / 2, Math.max(yHigh, yLow));
            ctx.lineTo(x + barWidth / 2, Math.max(yOpen, yClose));
            ctx.stroke();

            // 绘制实体
            const bodyTop = Math.min(yOpen, yClose);
            const bodyHeight = Math.abs(yClose - yOpen);

            if (isUnfinishedKline) {
                // 未结束的K线：空心虚线矩形
                ctx.strokeStyle = color;
                ctx.lineWidth = 1;
                ctx.setLineDash([2, 2]); // 确保虚线样式
                ctx.strokeRect(x, bodyTop, barWidth, Math.max(1, bodyHeight));
            } else {
                // 已结束的K线
                ctx.setLineDash([]); // 实线样式
                if (isPositive) {
                    // 阳线：空心
                    ctx.strokeStyle = color;
                    ctx.lineWidth = 1;
                    ctx.strokeRect(x, bodyTop, barWidth, Math.max(1, bodyHeight));
                } else {
                    // 阴线：实心
                    ctx.fillStyle = color;
                    ctx.fillRect(x, bodyTop, barWidth, Math.max(1, bodyHeight));
                }
            }

            // 重置线条样式为实线，避免影响后续绘制
            ctx.setLineDash([]);
        });

        // 增强当前K线视觉效果（最新的一根K线）
        if (displayData.length > 0) {
            // 根据排序方式确定最新K线的索引
            // reverseOrder=true (新在右): 最新K线在末尾
            // reverseOrder=false (新在左): 最新K线在开头
            const latestIndex = reverseOrder ? displayData.length - 1 : 0;
            const currentKline = displayData[latestIndex];
            const x = chartX + barSpacing + latestIndex * (barWidth + barSpacing);

            // 重新计算Y坐标
            const yOpen = chartY + mainChartHeight * (1 - (currentKline.open - priceRange.min) / (priceRange.max - priceRange.min));
            const yClose = chartY + mainChartHeight * (1 - (currentKline.close - priceRange.min) / (priceRange.max - priceRange.min));

            const isUnfinishedKline = currentKline.confirm === 0;
            const latestCandleTimestamp = data.length > 0 ? data[data.length - 1].timestamp : 0;
            const shouldDrawCurrentCandleEffect = latestCandleTimestamp >= Date.now();

            // 添加呼吸灯效果边框
            if (shouldDrawCurrentCandleEffect) {
                const pulseOpacity = 0.6 + Math.sin(Date.now() / 300) * 0.4; // 呼吸灯效果
                const bodyTop = Math.min(yOpen, yClose);
                const bodyHeight = Math.abs(yClose - yOpen);

                // 绘制特殊边框（增强视觉识别）
                ctx.save();
                ctx.strokeStyle = `rgba(255, 100, 100, ${pulseOpacity})`; // 红色呼吸灯效果
                ctx.lineWidth = 2; // 加粗边框
                ctx.setLineDash([3, 2]); // 特殊虚线样式
                ctx.strokeRect(x - 1, bodyTop - 1, barWidth + 2, bodyHeight + 2); // 稍微放大的边框
                ctx.restore();

                // 如果是未结束的K线，添加更强的视觉效果
                if (isUnfinishedKline) {
                    ctx.save();

                    // 添加闪烁效果
                    const flashOpacity = 0.3 + Math.abs(Math.sin(Date.now() / 200)) * 0.7;
                    ctx.fillStyle = `rgba(255, 200, 100, ${flashOpacity})`;
                    ctx.fillRect(x - 2, bodyTop - 2, barWidth + 4, bodyHeight + 4);

                    // 添加"当前"标识
                    ctx.fillStyle = '#FFD700'; // 金色
                    ctx.font = 'bold 10px Arial';
                    ctx.textAlign = 'center';
                    ctx.fillText('当前', x + barWidth / 2, bodyTop - 8);

                    ctx.restore();
                }
            }
        }

        // 绘制成交量直方图
        drawVolumeHistogram(ctx, chartX, volumeY, chartWidth, volumeChartHeight, displayData);

        // 绘制技术指标
        drawIndicators(ctx, chartY, mainChartHeight, rsiChartHeight, chartX, chartWidth, rsiY, timestampToX);


        // 绘制当前标记价格线
        if (markPrice !== null && markPrice !== undefined) {
            drawMarkPriceLine(ctx, chartX, chartY, chartWidth, mainChartHeight, priceRange, markPriceColor);
        }

        // 辅助函数：找到最接近的K线时间戳
        const findClosestTimestamp = (data: CandlestickData[], targetTime: number): number | null => {
            if (data.length === 0) return null;

            let closest = data[0].timestamp;
            let minDiff = Math.abs(data[0].timestamp - targetTime);

            for (let i = 1; i < data.length; i++) {
                const diff = Math.abs(data[i].timestamp - targetTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = data[i].timestamp;
                }
            }

            return closest;
        };

        // 绘制持仓期间背景高亮
        const drawPositionPeriod = (
            ctx: CanvasRenderingContext2D,
            chartX: number,
            chartY: number,
            chartWidth: number,
            totalHeight: number,
            data: CandlestickData[],
            timestampToX: Map<number, number>,
            priceRange: { min: number, max: number },
            barWidth: number
        ) => {
            if (!referenceLines?.cTime || !referenceLines?.uTime) {
                return;
            }

            if (!referenceLines?.avgPx || !referenceLines?.closePx) {
                return;
            }

            const startTimestamp = findClosestTimestamp(data, referenceLines.cTime);
            const endTimestamp = findClosestTimestamp(data, referenceLines.uTime);

            // 如果找不到匹配的时间戳，输出警告
            if (!startTimestamp || !endTimestamp) {
                return;
            }
            const startX = timestampToX.get(startTimestamp);
            const endX = timestampToX.get(endTimestamp);

            if (startX === undefined || endX === undefined) {
                debugLog('[drawPositionPeriod] 无法获取X坐标，跳过绘制');
                return;
            }

            // 调整绘制区域，覆盖K线宽度
            // startX/endX是K线中心点，需要向左/右扩展半个barWidth
            // 注意：K线图显示顺序是新数据在左侧，旧数据在右侧
            // 所以开仓时间早（旧）在右侧，平仓时间晚（新）在左侧
            // 因此startX（开仓）可能大于endX（平仓），需要处理这种情况
            const actualLeftX = Math.min(startX, endX);
            const actualRightX = Math.max(startX, endX);
            const rectX = actualLeftX - barWidth / 2;
            const rectWidth = (actualRightX - actualLeftX) + barWidth;
            const rectEndX = rectX + rectWidth;

            // 计算开仓价和平仓价的Y坐标
            const openPrice = referenceLines.avgPx;
            const closePrice = referenceLines.closePx;

            const openY = chartY + totalHeight * (1 - (openPrice - priceRange.min) / (priceRange.max - priceRange.min));
            const closeY = chartY + totalHeight * (1 - (closePrice - priceRange.min) / (priceRange.max - priceRange.min));

            // 确定背景的上下边界
            const topY = Math.min(openY, closeY);
            const bottomY = Math.max(openY, closeY);
            const bgHeight = bottomY - topY;

            // 根据盈亏状态设置背景色
            let bgColor = 'rgba(24, 144, 255, 0.08)'; // 默认蓝色
            if (referenceLines.profitLossStatus === 'profit') {
                bgColor = 'rgba(82, 196, 26, 0.1)'; // 盈利：70%透明绿色
            } else if (referenceLines.profitLossStatus === 'loss') {
                bgColor = 'rgba(255, 77, 79, 0.1)'; // 亏损：70%透明红色
            }

            // 绘制半透明背景（只在开仓价和平仓价之间）
            ctx.fillStyle = bgColor;
            ctx.fillRect(rectX, topY, rectWidth, bgHeight);

            // 绘制开仓价水平虚线（从开仓时间到平仓时间）
            ctx.strokeStyle = '#1890ff';
            ctx.lineWidth = 1;
            ctx.setLineDash([5, 3]);
            ctx.beginPath();
            ctx.moveTo(rectX, openY);
            ctx.lineTo(rectEndX, openY);
            ctx.stroke();

            // 绘制开仓价标签
            const openPriceLabel = openPrice.toFixed(2);
            ctx.fillStyle = '#1890ff';
            ctx.font = 'bold 10px Arial';
            ctx.textAlign = 'right';
            ctx.textBaseline = 'bottom';
            ctx.fillText(openPriceLabel, rectX - 5, openY - 2);

            // 绘制平仓价水平虚线（从开仓时间到平仓时间）
            ctx.strokeStyle = '#52c41a';
            ctx.lineWidth = 1;
            ctx.setLineDash([5, 3]);
            ctx.beginPath();
            ctx.moveTo(rectX, closeY);
            ctx.lineTo(rectEndX, closeY);
            ctx.stroke();

            // 绘制平仓价标签
            const closePriceLabel = closePrice.toFixed(2);
            ctx.fillStyle = '#52c41a';
            ctx.font = 'bold 10px Arial';
            ctx.textAlign = 'right';
            ctx.textBaseline = 'top';
            ctx.fillText(closePriceLabel, rectX - 5, closeY + 2);

            // 绘制左边界（开仓时间）
            ctx.strokeStyle = '#1890ff';
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);
            ctx.beginPath();
            ctx.moveTo(startX, topY);
            ctx.lineTo(startX, bottomY);
            ctx.stroke();                // 绘制左边界标签（开仓）
            const openLabelWidth = 40;
            const openLabelHeight = 20;
            const openLabelX = startX - openLabelWidth / 2;
            const openLabelY = topY - openLabelHeight - 5;

            ctx.fillStyle = 'rgba(24, 144, 255, 0.8)';
            ctx.fillRect(openLabelX, openLabelY, openLabelWidth, openLabelHeight);

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 11px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(referenceLines?.entryLabel ?? '开仓', startX, openLabelY + openLabelHeight / 2);

            // 绘制右边界（平仓时间）
            ctx.strokeStyle = '#52c41a';
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);
            ctx.beginPath();
            ctx.moveTo(endX, topY);
            ctx.lineTo(endX, bottomY);
            ctx.stroke();

            // 绘制右边界标签（平仓）
            const closeLabelWidth = 40;
            const closeLabelHeight = 20;
            const closeLabelX = endX - closeLabelWidth / 2;
            const closeLabelY = topY - closeLabelHeight - 5;

            ctx.fillStyle = 'rgba(82, 196, 26, 0.8)';
            ctx.fillRect(closeLabelX, closeLabelY, closeLabelWidth, closeLabelHeight);

            ctx.fillStyle = '#ffffff';
            ctx.font = 'bold 11px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('平仓', endX, closeLabelY + closeLabelHeight / 2);

            ctx.setLineDash([]);
        };
        // 绘制持仓期间背景高亮
        drawPositionPeriod(ctx, chartX, chartY, chartWidth, mainChartHeight, displayData, timestampToX, priceRange, barWidth);

        // 绘制MACD（副图）
        if (hasMACD && showMACD && macdChartHeight > 0) {
            // 绘制MACD背景
            ctx.fillStyle = 'rgba(31, 31, 31, 0.5)';
            ctx.fillRect(chartX, macdY, chartWidth, macdChartHeight);

            // 绘制分隔线
            ctx.strokeStyle = COLORS.grid;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(chartX, macdY);
            ctx.lineTo(chartX + chartWidth, macdY);
            ctx.stroke();

            // 获取MACD数据
            const macdValues = getIndicatorValues(indicators?.MACD);
            if (macdValues && macdValues.length > 0) {
                const sortedMacdValues = [...macdValues].sort((a: any, b: any) => a.timestamp - b.timestamp);

                // 查找MACD数据的最小最大值
                let macdMin = Infinity, macdMax = -Infinity;
                sortedMacdValues.forEach((point: any) => {
                    if (point.diff !== undefined) {
                        macdMin = Math.min(macdMin, point.diff);
                        macdMax = Math.max(macdMax, point.diff);
                    }
                    if (point.dea !== undefined) {
                        macdMin = Math.min(macdMin, point.dea);
                        macdMax = Math.max(macdMax, point.dea);
                    }
                    if (point.macd !== undefined) {
                        macdMin = Math.min(macdMin, point.macd);
                        macdMax = Math.max(macdMax, point.macd);
                    }
                });

                if (macdMin !== Infinity && macdMax !== -Infinity) {
                    const macdRange = macdMax - macdMin;
                    const padding = macdRange * 0.1;
                    macdMin -= padding;
                    macdMax += padding;
                    const macdScale = macdChartHeight / (macdMax - macdMin);
                    const macdZeroY = macdY + (macdMax / (macdMax - macdMin)) * macdChartHeight;

                    // 绘制零线
                    ctx.strokeStyle = '#666';
                    ctx.lineWidth = 1;
                    ctx.setLineDash([3, 3]);
                    ctx.beginPath();
                    ctx.moveTo(chartX, macdZeroY);
                    ctx.lineTo(chartX + chartWidth, macdZeroY);
                    ctx.stroke();
                    ctx.setLineDash([]);

                    // 绘制MACD柱状图（红绿柱）
                    sortedMacdValues.forEach((point: any) => {
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined || point.macd === undefined) return;

                        const barHeight = Math.abs(point.macd) * macdScale;
                        const barY = point.macd >= 0 ? macdZeroY - barHeight : macdZeroY;

                        ctx.fillStyle = point.macd >= 0 ? '#ff4d4f' : '#52c41a';
                        ctx.fillRect(x - 2, barY, 4, barHeight);
                    });

                    // 绘制DIF线（快线，橙色）
                    ctx.strokeStyle = '#fa541c';
                    ctx.lineWidth = 1.5;
                    ctx.beginPath();
                    let hasMoved = false;
                    sortedMacdValues.forEach((point: any) => {
                        if (point.diff === undefined) return;
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined) return;
                        const y = macdY + macdChartHeight - (point.diff - macdMin) * macdScale;
                        if (!hasMoved) {
                            ctx.moveTo(x, y);
                            hasMoved = true;
                        } else {
                            ctx.lineTo(x, y);
                        }
                    });
                    ctx.stroke();

                    // 绘制DEA线（慢线，紫色）
                    ctx.strokeStyle = '#9254de';
                    ctx.lineWidth = 1.5;
                    ctx.beginPath();
                    hasMoved = false;
                    sortedMacdValues.forEach((point: any) => {
                        if (point.dea === undefined) return;
                        const x = timestampToX.get(point.timestamp);
                        if (x === undefined) return;
                        const y = macdY + macdChartHeight - (point.dea - macdMin) * macdScale;
                        if (!hasMoved) {
                            ctx.moveTo(x, y);
                            hasMoved = true;
                        } else {
                            ctx.lineTo(x, y);
                        }
                    });
                    ctx.stroke();
                }
            }

            // 绘制MACD标签
            ctx.fillStyle = '#999';
            ctx.font = '10px sans-serif';
            ctx.fillText('MACD(12,26,9)', chartX + 5, macdY + 12);
        }

        drawEntryTimeMarker(ctx, chartX, chartY, chartWidth, mainChartHeight, displayData, timestampToX, barWidth);
        drawEntryAvgPriceSegment(ctx, chartX, chartY, chartWidth, mainChartHeight, priceRange, displayData, timestampToX);

        // 绘制止盈止损参考线
        drawPositionReferenceLines(ctx, chartX, chartY, chartWidth, mainChartHeight, priceRange);

        // 绘制鼠标Y轴位置对应的价格水平线（鼠标悬浮时）
        if (hoveredPoint?.yPrice !== undefined) {
            drawHighPriceLine(ctx, chartX, chartY, chartWidth, mainChartHeight, priceRange, hoveredPoint.yPrice);
        }

        // 绘制鼠标X轴位置对应的垂直线（十字线功能）
        if (hoveredPoint?.x !== undefined && hoveredPoint?.dataIndex !== undefined) {
            const totalChartHeight = actualSize.height - CHART_LAYOUT.topMargin - CHART_LAYOUT.bottomMargin;
            const relativeMouseX = hoveredPoint.x - chartX;
            drawCrosshairVerticalLine(
                ctx,
                relativeMouseX,
                chartX,
                chartY,
                chartWidth,
                totalChartHeight,
                hoveredPoint.dataIndex,
                displayData
            );
        }
    };

    // 绘制标记价格线
    const drawMarkPriceLine = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        chartY: number,
        chartWidth: number,
        chartHeight: number,
        priceRange: { min: number, max: number },
        lineColor?: string
    ) => {
        // 如果没有指定颜色，使用默认橙红色
        const actualLineColor = lineColor || '#ff6b35';

        // 计算标记价格的Y坐标 - 使用mainChartHeight确保只在主图区域显示
        const y = chartY + chartHeight * (1 - ((markPrice || 0) - priceRange.min) / (priceRange.max - priceRange.min));

        // 如果标记价格超出显示范围，调整到最近的位置
        let displayY = y;
        let displayPrice = markPrice || 0;

        if (y < chartY) {
            displayY = chartY + 10;
            displayPrice = priceRange.max;
        } else if (y > chartY + chartHeight) {  // 这里chartHeight实际是mainChartHeight
            displayY = chartY + chartHeight - 10;
            displayPrice = priceRange.min;
        }

        // 绘制水平虚线
        ctx.strokeStyle = actualLineColor;
        ctx.lineWidth = 1;
        ctx.setLineDash([5, 3]); // 虚线样式
        ctx.beginPath();
        ctx.moveTo(chartX, displayY);
        ctx.lineTo(chartX + chartWidth, displayY);
        ctx.stroke();

        // 绘制Y轴标记价格标签 - 移至左侧
        const priceStr = formatEffectiveDecimal(displayPrice, 4);
        ctx.font = 'bold 12px Arial';
        const textWidth = ctx.measureText(priceStr).width;
        const padding = 6;
        const boxWidth = textWidth + padding * 2;
        const boxX = chartX - 8 - textWidth - padding;

        const bgColor = actualLineColor.replace(')', ', 0.1)').replace('rgb', 'rgba').replace('#', 'rgba(' + parseInt(actualLineColor.slice(1, 3), 16) + ',' + parseInt(actualLineColor.slice(3, 5), 16) + ',' + parseInt(actualLineColor.slice(5, 7), 16) + ', 0.1)');
        ctx.fillStyle = bgColor;
        ctx.fillRect(boxX, displayY - 12, boxWidth, 24);

        ctx.strokeStyle = actualLineColor;
        ctx.lineWidth = 1;
        ctx.setLineDash([]); // 实线
        ctx.strokeRect(boxX, displayY - 12, boxWidth, 24);

        // 显示价格 - 字体大小调整为12px
        ctx.textBaseline = 'middle';
        ctx.fillStyle = actualLineColor;
        ctx.textAlign = 'right';
        ctx.fillText(priceStr, chartX - 8, displayY);

        // 重置textBaseline
        ctx.textBaseline = 'alphabetic';

        // 重置线条样式
        ctx.setLineDash([]);
    };

    // 绘制持仓参考线
    const drawPositionReferenceLines = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        chartY: number,
        chartWidth: number,
        chartHeight: number,
        priceRange: { min: number, max: number }
    ) => {
        if (!referenceLines) return;

        // 防止除以0导致NaN
        const priceSpan = priceRange.max - priceRange.min;
        if (priceSpan <= 0) return;

        const isLong = referenceLines.posSide === 'long';
        const topY = chartY;
        const bottomY = chartY + chartHeight;

        // 绘制止盈/止损区域背景（分别处理，可以只显示一个）
        const tpValid = referenceLines.takeProfitPx !== undefined && referenceLines.takeProfitPx !== null;
        const slValid = referenceLines.stopLossPx !== undefined && referenceLines.stopLossPx !== null;

        // 计算Y坐标
        const getPriceY = (price: number) => {
            return chartY + chartHeight * (1 - (price - priceRange.min) / priceSpan);
        };

        // 做多：止盈在上，止损在下
        if (isLong) {
            // 止盈区域：上缘到止盈线
            if (tpValid) {
                const tpY = getPriceY(referenceLines.takeProfitPx!);
                const tpInRange = tpY >= chartY && tpY <= chartY + chartHeight;
                if (tpInRange) {
                    ctx.fillStyle = 'rgba(82, 196, 26, 0.15)';
                    ctx.fillRect(chartX, topY, chartWidth, Math.max(0, tpY - topY));
                }
            }
            // 止损区域：止损线到下缘
            if (slValid) {
                const slY = getPriceY(referenceLines.stopLossPx!);
                const slInRange = slY >= chartY && slY <= chartY + chartHeight;
                if (slInRange) {
                    ctx.fillStyle = 'rgba(255, 77, 79, 0.15)';
                    ctx.fillRect(chartX, slY, chartWidth, Math.max(0, bottomY - slY));
                }
            }
        } else {
            // 做空：止盈在下，止损在上
            // 止盈区域：止盈线到下缘
            if (tpValid) {
                const tpY = getPriceY(referenceLines.takeProfitPx!);
                const tpInRange = tpY >= chartY && tpY <= chartY + chartHeight;
                if (tpInRange) {
                    ctx.fillStyle = 'rgba(82, 196, 26, 0.15)';
                    ctx.fillRect(chartX, tpY, chartWidth, Math.max(0, bottomY - tpY));
                }
            }
            // 止损区域：上缘到止损线
            if (slValid) {
                const slY = getPriceY(referenceLines.stopLossPx!);
                const slInRange = slY >= chartY && slY <= chartY + chartHeight;
                if (slInRange) {
                    ctx.fillStyle = 'rgba(255, 77, 79, 0.15)';
                    ctx.fillRect(chartX, topY, chartWidth, Math.max(0, slY - topY));
                }
            }
        }

        // 参考线配置
        const linesConfig = [
            // {
            //     price: referenceLines.avgPx,
            //     label: '开仓',
            //     color: '#1890ff' // 蓝色
            // },
            // {
            //     price: referenceLines.liquidationPx,
            //     label: '强平',
            //     color: '#fa8c16' // 橙色
            // },
            {
                price: referenceLines.takeProfitPx,
                label: '止盈',
                kind: 'tp',
                color: '#52c41a' // 绿色
            },
            {
                price: referenceLines.stopLossPx,
                label: '止损',
                kind: 'sl',
                color: '#ff4d4f' // 红色
            }
        ];

        // 绘制每条参考线
        linesConfig.forEach(config => {
            if (config.price === undefined || config.price === null || isNaN(Number(config.price))) return;

            const price = Number(config.price);
            const y = chartY + chartHeight * (1 - (price - priceRange.min) / priceSpan);

            // 如果价格超出显示范围，不绘制
            if (y < chartY || y > chartY + chartHeight) return;

            // 绘制水平虚线
            ctx.save(); // 保存状态
            ctx.strokeStyle = config.color;
            ctx.lineWidth = 2; // 加粗一点
            ctx.setLineDash([5, 3]); // 虚线样式
            ctx.beginPath();
            ctx.moveTo(chartX, y);
            ctx.lineTo(chartX + chartWidth, y);
            ctx.stroke();
            ctx.restore(); // 恢复状态

            // 绘制右侧标签
            const priceStr = formatEffectiveDecimal(price, 4);
            const line1Text = `${config.label} ${priceStr}`;
            let line2Text = '';

            // 计算百分比（相对于开仓价）
            if (referenceLines.avgPx && referenceLines.avgPx > 0) {
                const percentage = ((price - referenceLines.avgPx) / referenceLines.avgPx) * 100;
                const value = Math.abs(percentage).toFixed(2);
                if (config.kind === 'tp') {
                    line2Text = `+${value}%`;
                } else if (config.kind === 'sl') {
                    line2Text = `-${value}%`;
                } else {
                    const sign = percentage >= 0 ? '+' : '';
                    line2Text = `${sign}${percentage.toFixed(2)}%`;
                }
            }

            ctx.font = 'bold 11px Arial';
            const line1Width = ctx.measureText(line1Text).width;
            const line2Width = line2Text ? ctx.measureText(line2Text).width : 0;
            const textWidth = Math.max(line1Width, line2Width);

            const padding = 6;
            const lineHeight = 14; // 行高
            const boxWidth = textWidth + padding * 2;
            const boxHeight = line2Text ? (lineHeight * 2 + padding * 2) : 24; // 如果有两行，增加高度
            const boxX = chartX + chartWidth + 8;
            const boxY = line2Text ? (y - boxHeight / 2) : (y - 12); // 垂直居中

            // 绘制标签边框(保留边框,不绘制背景色)
            ctx.strokeStyle = config.color;
            ctx.lineWidth = 1;
            ctx.setLineDash([]); // 实线边框
            ctx.strokeRect(boxX, boxY, boxWidth, boxHeight);

            // 绘制标签文字
            ctx.fillStyle = config.color;
            ctx.textAlign = 'left';

            if (line2Text) {
                // 第一行：价格
                ctx.textBaseline = 'top';
                ctx.fillText(line1Text, boxX + padding, boxY + padding);

                // 第二行：百分比
                ctx.fillText(line2Text, boxX + padding, boxY + padding + lineHeight);
            } else {
                // 单行模式
                ctx.textBaseline = 'middle';
                ctx.fillText(line1Text, boxX + padding, y);
            }

            // 重置textBaseline
            ctx.textBaseline = 'alphabetic';
        });
    };

    const drawEntryTimeMarker = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        chartY: number,
        chartWidth: number,
        chartHeight: number,
        data: CandlestickData[],
        timestampToX: Map<number, number>,
        barWidth: number
    ) => {
        if (referenceLines?.uTime !== undefined && referenceLines?.uTime !== null) return;
        if (!referenceLines?.cTime) return;
        if (data.length === 0) return;

        const findClosestTimestamp = (targetTime: number): number | null => {
            let closest = data[0].timestamp;
            let minDiff = Math.abs(data[0].timestamp - targetTime);
            for (let i = 1; i < data.length; i++) {
                const diff = Math.abs(data[i].timestamp - targetTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = data[i].timestamp;
                }
            }
            return closest;
        };

        const entryTimestamp = findClosestTimestamp(referenceLines.cTime);
        if (!entryTimestamp) return;

        const entryX = timestampToX.get(entryTimestamp);
        if (entryX === undefined) return;

        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.moveTo(entryX, chartY);
        ctx.lineTo(entryX, chartY + chartHeight);
        ctx.stroke();
        ctx.setLineDash([]);

        const label = referenceLines?.entryLabel ?? '开仓';
        ctx.font = 'bold 11px Arial';
        const paddingX = 6;
        const paddingY = 4;
        const textWidth = ctx.measureText(label).width;
        const boxWidth = textWidth + paddingX * 2;
        const boxHeight = 18;
        const boxX = Math.min(Math.max(entryX - boxWidth / 2, chartX), chartX + chartWidth - boxWidth);
        const boxY = chartY + 6;

        ctx.fillStyle = 'rgba(24, 144, 255, 0.8)';
        ctx.fillRect(boxX, boxY, boxWidth, boxHeight);

        ctx.fillStyle = '#ffffff';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(label, boxX + boxWidth / 2, boxY + boxHeight / 2 + 0.5);
        ctx.textAlign = 'start';
        ctx.textBaseline = 'alphabetic';

        const tickY = chartY + chartHeight;
        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(entryX, tickY);
        ctx.lineTo(entryX, tickY + Math.max(6, barWidth * 0.3));
        ctx.stroke();
    };

    const drawEntryAvgPriceSegment = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        chartY: number,
        chartWidth: number,
        chartHeight: number,
        priceRange: { min: number, max: number },
        data: CandlestickData[],
        timestampToX: Map<number, number>
    ) => {
        if (referenceLines?.uTime !== undefined && referenceLines?.uTime !== null) return;
        if (!referenceLines) return;
        if (referenceLines.avgPx === undefined || referenceLines.avgPx === null) return;
        if (referenceLines.cTime === undefined || referenceLines.cTime === null) return;
        if (data.length === 0) return;

        const findClosestTimestamp = (targetTime: number): number | null => {
            let closest = data[0].timestamp;
            let minDiff = Math.abs(data[0].timestamp - targetTime);
            for (let i = 1; i < data.length; i++) {
                const diff = Math.abs(data[i].timestamp - targetTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = data[i].timestamp;
                }
            }
            return closest;
        };

        const entryTimestamp = findClosestTimestamp(referenceLines.cTime);
        if (!entryTimestamp) return;

        const entryX = timestampToX.get(entryTimestamp);
        if (entryX === undefined) return;

        let minTimestamp = Infinity;
        let maxTimestamp = -Infinity;
        for (const candle of data) {
            if (candle.timestamp < minTimestamp) minTimestamp = candle.timestamp;
            if (candle.timestamp > maxTimestamp) maxTimestamp = candle.timestamp;
        }
        if (!Number.isFinite(minTimestamp) || !Number.isFinite(maxTimestamp)) return;

        const oldestX = timestampToX.get(minTimestamp);
        const newestX = timestampToX.get(maxTimestamp);
        if (oldestX === undefined || newestX === undefined) return;

        const y = chartY + chartHeight * (1 - (Number(referenceLines.avgPx) - priceRange.min) / (priceRange.max - priceRange.min));
        if (y < chartY || y > chartY + chartHeight) return;

        const startX = entryX;
        // 如果数据反序（最新的在右侧），endX就是最新的点；如果是正序（最新的在左侧），endX需要是左边界（或者最新的点）
        // 实际上，为了满足"向更新的时间延长"：
        // 1. 如果是最新的在右侧（reverseOrder=true），线从 entryX 画到 newestX
        // 2. 如果是最新的在左侧（reverseOrder=false），线从 entryX 画到 chartX (或者 newestX，即最左侧)
        // 这里的 newestX 是时间最大的点。
        // 如果 reverseOrder=true (升序，旧->新)，最右侧是新数据。entryX -> 右侧边界。
        // 如果 reverseOrder=false (降序，新->旧)，最左侧是新数据。entryX -> 左侧边界。

        // 假设 newestX 总是代表"最新时间"的X坐标
        const endX = newestX;

        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.setLineDash([5, 3]);
        ctx.beginPath();
        ctx.moveTo(startX, y);
        ctx.lineTo(endX, y);
        ctx.stroke();
        ctx.setLineDash([]);

        // 绘制右侧标签（移动到K线图右侧区域）
        const priceStr = formatEffectiveDecimal(referenceLines.avgPx, 4);
        const text = `${referenceLines?.entryLabel ?? '开仓'} ${priceStr}`;

        ctx.font = 'bold 11px Arial';
        const labelPadding = 6;
        const textWidth = ctx.measureText(text).width;
        const boxWidth = textWidth + labelPadding * 2;
        const boxHeight = 24; // 与止盈止损标签高度一致

        // 计算标签位置：位于图表右侧边缘外部
        const boxX = chartX + chartWidth + 8;
        const boxY = y - 12; // 垂直居中

        // 绘制标签边框(保留边框,不绘制背景色)
        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.setLineDash([]); // 实线边框
        ctx.strokeRect(boxX, boxY, boxWidth, boxHeight);

        // 绘制标签文字
        ctx.textBaseline = 'middle';
        ctx.fillStyle = '#1890ff';
        ctx.textAlign = 'left';
        ctx.fillText(text, boxX + labelPadding, y);

        // 重置textBaseline
        ctx.textBaseline = 'alphabetic';
    };

    // 绘制最高价格线
    const drawHighPriceLine = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        chartY: number,
        chartWidth: number,
        chartHeight: number,
        priceRange: { min: number, max: number },
        highPrice: number
    ) => {
        // 计算最高价格的Y坐标
        const y = chartY + chartHeight * (1 - (highPrice - priceRange.min) / (priceRange.max - priceRange.min));

        // 如果最高价格超出显示范围，调整到最近的位置
        let displayY = y;
        let displayPrice = highPrice;

        if (y < chartY) {
            displayY = chartY + 10;
            displayPrice = priceRange.max;
        } else if (y > chartY + chartHeight) {
            displayY = chartY + chartHeight - 10;
            displayPrice = priceRange.min;
        }

        // 绘制水平虚线
        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.setLineDash([8, 4]); // 虚线样式
        ctx.beginPath();
        ctx.moveTo(chartX, displayY);
        ctx.lineTo(chartX + chartWidth, displayY);
        ctx.stroke();

        // 绘制Y轴最高价格标签
        ctx.fillStyle = 'rgba(24, 144, 255, 0.1)';
        ctx.fillRect(chartX - 60, displayY - 12, 50, 24);

        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.setLineDash([]); // 实线
        ctx.strokeRect(chartX - 60, displayY - 12, 50, 24);

        ctx.fillStyle = '#1890ff';
        ctx.font = 'bold 10px Arial';
        ctx.textAlign = 'right';
        ctx.fillText(displayPrice.toFixed(2), chartX - 15, displayY + 4);

        // 添加"最高"标识
        ctx.font = '9px Arial';
        ctx.fillStyle = '#1890ff';
        ctx.textAlign = 'left';
        ctx.fillText('最高', chartX - 8, displayY + 4);

        // 重置线条样式
        ctx.setLineDash([]);
    };

    // 绘制十字线垂直线
    const drawCrosshairVerticalLine = (
        ctx: CanvasRenderingContext2D,
        mouseX: number,
        chartX: number,
        chartY: number,
        chartWidth: number,
        totalHeight: number,
        dataIndex: number,
        data: CandlestickData[]
    ) => {
        // 计算垂直线X坐标，限制在图表区域内
        const crosshairX = Math.max(chartX, Math.min(chartX + mouseX, chartX + chartWidth));

        // 绘制垂直虚线（从顶部到底部）
        ctx.strokeStyle = '#1890ff';
        ctx.lineWidth = 1;
        ctx.setLineDash([8, 4]);
        ctx.beginPath();
        ctx.moveTo(crosshairX, chartY);
        ctx.lineTo(crosshairX, chartY + totalHeight);
        ctx.stroke();

        // 绘制底部时间标签
        if (dataIndex >= 0 && dataIndex < data.length) {
            const timestamp = data[dataIndex].timestamp;
            const timeStr = formatTimeLabel(timestamp, timeFrame);

            // 时间标签背景
            const labelWidth = 70;
            const labelHeight = 20;
            const labelX = crosshairX - labelWidth / 2;
            const labelY = chartY + totalHeight - labelHeight - 5;

            // 绘制背景
            ctx.fillStyle = 'rgba(24, 144, 255, 0.8)';
            ctx.fillRect(labelX, labelY, labelWidth, labelHeight);

            // 绘制时间文字
            ctx.fillStyle = '#ffffff';
            ctx.font = '11px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(timeStr, crosshairX, labelY + labelHeight / 2);
        }

        // 重置线条样式
        ctx.setLineDash([]);
    };

    // 计算悬浮窗实际宽度
    const calculateActualTooltipWidth = useCallback((dataIndex: number | undefined, indicators: any): number => {
        let baseWidth = 192; // 基础宽度增加20% (160 * 1.2)
        let additionalWidth = 0;

        // 技术指标宽度计算
        if (dataIndex !== undefined && indicators) {
            // 辅助函数：计算指标数量
            const countIndicators = (indicator: any): number => {
                const values = getIndicatorValues(indicator);
                if (!values) return 0;
                const isMultiPeriod = values.length > 0 && values[0].multiPeriodValues;
                if (isMultiPeriod && values[0].multiPeriodValues) {
                    return Object.keys(values[0].multiPeriodValues).length;
                }
                return 1;
            };

            const indicatorCount = {
                EMA: countIndicators(indicators.EMA),
                SMA: countIndicators(indicators.SMA),
                WMA: countIndicators(indicators.WMA),
                BOLL: countIndicators(indicators.BOLL), // 动态计算BOLL周期数量
                RSI: isValidIndicator(indicators.RSI) ? 1 : 0
            };

            // 每个指标大约增加20-30px宽度
            const totalIndicators = Object.values(indicatorCount).reduce((sum, count) => sum + count, 0);
            additionalWidth = Math.min(totalIndicators * 25, 60); // 最多增加60px
        }

        if (renderTooltipExtra) {
            additionalWidth += 60;
        }

        const actualWidth = baseWidth + additionalWidth;
        return Math.min(Math.max(actualWidth, 216), 320);
    }, [renderTooltipExtra]);

    // 增强的鼠标遮挡检测
    const isMouseOverlap = useCallback((tooltipLeft: number, tooltipWidth: number, mouseX: number): boolean => {
        const mouseRange = 12; // 扩展鼠标检测范围到12px
        const mouseLeft = mouseX - mouseRange;
        const mouseRight = mouseX + mouseRange;
        const tooltipRight = tooltipLeft + tooltipWidth;

        // 检测是否有重叠
        return !(tooltipRight < mouseLeft || tooltipLeft > mouseRight);
    }, []);

    // 智能左侧位置计算
    const getOptimalLeftPosition = useCallback((
        mouseX: number,
        chartLeftEdge: number,
        tooltipWidth: number,
        offset: number
    ): number => {
        const preferredLeft = mouseX - tooltipWidth - offset;

        // 确保不超出左边界，并添加额外安全边距
        const minLeft = chartLeftEdge + 5;
        const extraMargin = 8; // 额外的安全边距

        if (preferredLeft >= minLeft) {
            // 理想情况：有足够空间
            return preferredLeft - extraMargin;
        } else {
            // 空间不足：紧贴左边界，但确保不遮挡鼠标
            const boundaryPosition = minLeft;

            // 检查是否会遮挡鼠标，如果会则尝试右侧
            if (isMouseOverlap(boundaryPosition, tooltipWidth, mouseX)) {
                // 返回特殊值表示左侧不可行
                return -1;
            }

            return boundaryPosition;
        }
    }, [isMouseOverlap]);

    // 计算浮窗最佳位置
    const calculateTooltipPosition = (mouseX: number, mouseY: number) => {
        // 容错处理：确保输入参数有效
        const safeMouseX = Math.max(0, Math.min(mouseX, actualSize.width || 0));
        const safeMouseY = Math.max(0, Math.min(mouseY, actualSize.height || 0));

        // 使用动态宽度计算
        const tooltipWidth = calculateActualTooltipWidth(hoveredPoint?.dataIndex, indicators);
        const tooltipHeight = renderTooltipExtra ? 170 : 120;
        const offset = 15; // 鼠标与浮窗的间距
        const chartX = 60;
        const chartY = 20;
        const currentWidth = actualSize.width || 0;
        const currentHeight = actualSize.height || 0;
        const chartWidth = Math.max(0, currentWidth - 80);
        const chartHeight = Math.max(0, currentHeight - 80);
        const chartRightEdge = chartX + chartWidth;
        const chartLeftEdge = chartX;

        // 容错处理：确保图表尺寸有效
        if (chartWidth <= 0 || chartHeight <= 0) {
            return {left: safeMouseX + offset, top: safeMouseY - tooltipHeight - offset};
        }

        let left;
        let top;

        // 使用增强的鼠标遮挡检测，不再需要mouseXRange
        // 旧的检测范围已整合到isMouseOverlap函数中

        // 计算可用空间，确保不会出现负值
        const rightAvailableSpace = Math.max(0, chartRightEdge - (safeMouseX + offset));
        const leftAvailableSpace = Math.max(0, (safeMouseX - offset) - chartLeftEdge);

        // 智能判断鼠标位置：超过图表宽度2/3时优先考虑左侧
        const chartCenterX = chartLeftEdge + chartWidth / 2;
        const mouseIsOnRightSide = safeMouseX > chartLeftEdge + chartWidth * 0.66;
        const mouseIsOnLeftSide = safeMouseX < chartLeftEdge + chartWidth * 0.33;

        // 水平方向定位策略 - 使用增强的检测逻辑
        let candidatePositions = [];

        // 根据鼠标位置动态调整优先级
        const rightPriority = mouseIsOnRightSide ? 3 : 1;
        const leftPriority = mouseIsOnRightSide ? 1 : (mouseIsOnLeftSide ? 3 : 2);

        // 候选位置1：优先侧（根据鼠标位置动态调整）
        if (mouseIsOnRightSide) {
            // 鼠标在右侧时，优先考虑左侧
            const optimalLeftPos = getOptimalLeftPosition(safeMouseX, chartLeftEdge, tooltipWidth, offset);
            if (optimalLeftPos !== -1 && leftAvailableSpace >= tooltipWidth) {
                if (!isMouseOverlap(optimalLeftPos, tooltipWidth, safeMouseX)) {
                    candidatePositions.push({
                        position: optimalLeftPos,
                        priority: leftPriority,
                        side: 'left'
                    });
                }
            }

            // 候选位置2：次选右侧（空间充足时）
            if (rightAvailableSpace >= tooltipWidth) {
                const rightPos = safeMouseX + offset;
                if (!isMouseOverlap(rightPos, tooltipWidth, safeMouseX)) {
                    candidatePositions.push({
                        position: rightPos,
                        priority: rightPriority,
                        side: 'right'
                    });
                }
            }
        } else {
            // 鼠标在左侧或中间时，保持原有逻辑：优先右侧
            if (rightAvailableSpace >= tooltipWidth) {
                const rightPos = safeMouseX + offset;
                if (!isMouseOverlap(rightPos, tooltipWidth, safeMouseX)) {
                    candidatePositions.push({
                        position: rightPos,
                        priority: rightPriority,
                        side: 'right'
                    });
                }
            }

            // 候选位置2：左侧（使用智能计算）
            const optimalLeftPos = getOptimalLeftPosition(safeMouseX, chartLeftEdge, tooltipWidth, offset);
            if (optimalLeftPos !== -1 && leftAvailableSpace >= tooltipWidth) {
                if (!isMouseOverlap(optimalLeftPos, tooltipWidth, safeMouseX)) {
                    candidatePositions.push({
                        position: optimalLeftPos,
                        priority: leftPriority,
                        side: 'left'
                    });
                }
            }
        }

        // 候选位置3：紧贴右边界（边界情况，严格不重叠）
        const rightBoundaryPos = Math.max(chartLeftEdge + 5, chartRightEdge - tooltipWidth - 5);
        if (rightBoundaryPos > chartLeftEdge) {
            if (!isMouseOverlap(rightBoundaryPos, tooltipWidth, safeMouseX)) {
                candidatePositions.push({
                    position: rightBoundaryPos,
                    priority: 3,
                    side: 'right-boundary'
                });
            }
        }

        // 候选位置4：紧贴左边界（边界情况，严格不重叠）
        const leftBoundaryPos = chartLeftEdge + 5;
        if (leftBoundaryPos + tooltipWidth < chartRightEdge) {
            if (!isMouseOverlap(leftBoundaryPos, tooltipWidth, safeMouseX)) {
                candidatePositions.push({
                    position: leftBoundaryPos,
                    priority: 4,
                    side: 'left-boundary'
                });
            }
        }

        // 选择最佳位置
        if (candidatePositions.length > 0) {
            candidatePositions.sort((a, b) => a.priority - b.priority);
            left = candidatePositions[0].position;
        } else {
            // 增强的回退机制：优先确保不遮挡鼠标
            // 如果左侧空间足够但会遮挡鼠标，优先选择右侧
            const leftPosition = safeMouseX - tooltipWidth - offset;
            const rightPosition = safeMouseX + offset;

            if (!isMouseOverlap(leftPosition, tooltipWidth, safeMouseX) &&
                leftPosition >= chartLeftEdge + 5) {
                // 左侧安全且不遮挡鼠标
                left = leftPosition;
            } else if (!isMouseOverlap(rightPosition, tooltipWidth, safeMouseX) &&
                rightPosition + tooltipWidth <= chartRightEdge - 5) {
                // 右侧安全且不遮挡鼠标
                left = rightPosition;
            } else {
                // 都会遮挡：选择遮挡最少的一侧
                const leftOverlapIntensity = isMouseOverlap(leftPosition, tooltipWidth, safeMouseX) ?
                    Math.max(0, (leftPosition + tooltipWidth) - (safeMouseX - 12)) : 0;
                const rightOverlapIntensity = isMouseOverlap(rightPosition, tooltipWidth, safeMouseX) ?
                    Math.max(0, (safeMouseX + 12) - rightPosition) : 0;

                if (leftOverlapIntensity < rightOverlapIntensity) {
                    left = Math.max(leftPosition, chartLeftEdge + 5);
                } else {
                    left = Math.min(rightPosition, chartRightEdge - tooltipWidth - 5);
                }
            }
        }

        // 垂直方向定位（增强容错）
        const topSpace = Math.max(0, safeMouseY - chartY - offset);
        const bottomSpace = Math.max(0, (chartY + chartHeight) - safeMouseY - offset);

        if (topSpace >= tooltipHeight + 5) {
            // 上方空间充足
            top = safeMouseY - tooltipHeight - offset;
        } else if (bottomSpace >= tooltipHeight + 5) {
            // 下方空间充足
            top = safeMouseY + offset;
        } else {
            // 垂直方向空间都不足，选择空间更大的一侧
            if (topSpace > bottomSpace) {
                top = Math.max(chartY + 5, chartY + chartHeight - tooltipHeight - 5);
            } else {
                top = Math.min(chartY + chartHeight - tooltipHeight - 5, safeMouseY + offset);
            }
        }

        // 最终边界保护（双重保护）
        const finalLeft = Math.max(chartLeftEdge + 5, Math.min(left, chartRightEdge - tooltipWidth - 5));
        const finalTop = Math.max(chartY + 5, Math.min(top, chartY + chartHeight - tooltipHeight - 5));

        // 验证结果的有效性
        const isValidPosition = (
            finalLeft >= chartLeftEdge &&
            finalLeft + tooltipWidth <= chartRightEdge &&
            finalTop >= chartY &&
            finalTop + tooltipHeight <= chartY + chartHeight
        );

        if (!isValidPosition) {
            // 极端情况的最后容错
            return {
                left: chartLeftEdge + 10,
                top: chartY + 10
            };
        }

        return {left: finalLeft, top: finalTop};
    };

    // 绘制网格线和价格标签
    const drawGridAndLabels = (
        ctx: CanvasRenderingContext2D,
        chartX: number,
        chartY: number,
        chartWidth: number,
        chartHeight: number,
        priceRange: { min: number, max: number },
        mainChartHeight: number,
        sortedData: CandlestickData[]  // 使用sortedData确保与K线顺序一致
    ) => {
        ctx.strokeStyle = COLORS.grid;
        ctx.lineWidth = 1;
        ctx.fillStyle = COLORS.text;
        ctx.font = '12px Arial';
        ctx.textAlign = 'right';

        // 绘制水平网格线和价格标签（只在主图区域内绘制）
        for (let i = 0; i <= CHART_LAYOUT.gridLines; i++) {
            const y = chartY + (mainChartHeight * i) / CHART_LAYOUT.gridLines;  // 使用配置的网格线数量
            const price = priceRange.max - ((priceRange.max - priceRange.min) * i) / CHART_LAYOUT.gridLines;

            // 网格线
            ctx.beginPath();
            ctx.moveTo(chartX, y);
            ctx.lineTo(chartX + chartWidth, y);
            ctx.stroke();

            // 价格标签
            ctx.fillText(price.toFixed(2), chartX - 5, y + 4);  // 减少左边距偏移，确保完整显示
        }

        // 绘制垂直网格线和时间标签
        const verticalLines = CHART_LAYOUT.gridLines;
        ctx.textAlign = 'center';

        for (let i = 0; i <= verticalLines; i++) {
            const x = chartX + (chartWidth * i) / verticalLines;

            // 垂直网格线 - 贯穿所有图表
            ctx.beginPath();
            ctx.moveTo(x, chartY);
            ctx.lineTo(x, chartY + chartHeight);
            ctx.stroke();

            // 时间标签 - 根据数据点索引显示对应时间（使用sortedData确保与K线顺序一致）
            const dataIndex = Math.floor((sortedData.length - 1) * i / verticalLines);
            if (dataIndex >= 0 && dataIndex < sortedData.length) {
                const timestamp = sortedData[dataIndex].timestamp;
                const date = new Date(timestamp);

                // 格式化为 yy-MM-dd hh:mm
                const year = date.getFullYear().toString().slice(-2);
                const month = (date.getMonth() + 1).toString().padStart(2, '0');
                const day = date.getDate().toString().padStart(2, '0');
                const hours = date.getHours().toString().padStart(2, '0');
                const minutes = date.getMinutes().toString().padStart(2, '0');

                const timeLabel = formatTimeLabel(timestamp, timeFrame);

                // 修复：计算对应K线柱的中心位置，让时间标签对齐到K线柱中心（使用sortedData）
                const barWidth = Math.max(1, Math.min(20, chartWidth / sortedData.length - 2));
                const barSpacing = (chartWidth - barWidth * sortedData.length) / (sortedData.length + 1);

                // 计算对应K线柱的中心位置
                const candleX = chartX + barSpacing + dataIndex * (barWidth + barSpacing);
                const candleCenterX = candleX + barWidth / 2;

                // 优化后的X轴标签位置计算
                const xAxisY = chartY + chartHeight + CHART_LAYOUT.separatorMargin + CHART_LAYOUT.xAxisHeight / 2;

                ctx.fillStyle = COLORS.text;
                ctx.font = '10px Arial';
                ctx.textAlign = 'center'; // 居中对齐时间标签

                // // console.log(`时间标签对齐: ...`); // 移除日志

                ctx.fillText(timeLabel, candleCenterX, xAxisY);
                ctx.textAlign = 'start'; // 恢复默认对齐方式
            }
        }
    };

    // 统一的边距计算函数 - 与绘制逻辑保持一致
    const getChartMargins = useCallback(() => {
        // 使用实际宽度，确保与CSS定位一致
        const adjustedWidth = Math.max(actualSize.width, 100);

        const dynamicLeftMargin = Math.max(CHART_LAYOUT.leftMargin, adjustedWidth * 0.08);

        // 如果有参考线，增加右侧边距以显示标签
        const hasReferenceLines = referenceLines && (
            referenceLines.takeProfitPx !== undefined ||
            referenceLines.stopLossPx !== undefined ||
            referenceLines.avgPx !== undefined ||
            referenceLines.liquidationPx !== undefined
        );

        // 增加对指标图例的考虑 - 如果显示内部图例，预留空间避免遮挡K线
        const hasLegend = !propVisibleIndicators;
        const legendWidth = hasLegend ? 140 : 0; // 这里的宽度应与下方div的maxWidth一致

        const baseRightMargin = hasReferenceLines ? 120 : CHART_LAYOUT.rightMargin;
        // 确保右侧边距足够容纳图例 (图例右侧留白10 + 图例宽度140 + 额外间距10 = 160)
        const finalBaseRightMargin = hasLegend ? Math.max(baseRightMargin, 160) : baseRightMargin;

        const rightRatio = hasReferenceLines ? 0.03 : 0.08;

        const dynamicRightMargin = Math.max(finalBaseRightMargin, adjustedWidth * rightRatio);

        return {
            left: dynamicLeftMargin,
            right: dynamicRightMargin,
            top: CHART_LAYOUT.topMargin,
            bottom: CHART_LAYOUT.bottomMargin,
            chartWidth: adjustedWidth - dynamicLeftMargin - dynamicRightMargin,
            chartHeight: actualSize.height - CHART_LAYOUT.topMargin - CHART_LAYOUT.bottomMargin,
            adjustedWidth: adjustedWidth,
            legendWidth: legendWidth // 导出图例宽度
        };
    }, [actualSize, referenceLines, propVisibleIndicators]);

    // 处理鼠标移动
    const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
        const canvas = canvasRef.current;
        if (!canvas || data.length === 0) {
            setHoveredPoint(null);
            return;
        }

        // 容错处理：确保鼠标事件数据有效
        if (!e.clientX || !e.clientY || isNaN(e.clientX) || isNaN(e.clientY)) {
            setHoveredPoint(null);
            return;
        }

        // 获取canvas的CSS显示尺寸和位置
        const rect = canvas.getBoundingClientRect();
        if (!rect || rect.width <= 0 || rect.height <= 0) {
            setHoveredPoint(null);
            return;
        }

        // 容错处理：确保actualSize有效
        if (!actualSize || actualSize.width <= 0 || actualSize.height <= 0) {
            setHoveredPoint(null);
            return;
        }

        // 使用统一的边距计算 - 与绘制逻辑完全一致
        const margins = getChartMargins();
        const chartX = margins.left;
        const chartY = margins.top;
        const chartWidth = margins.chartWidth;
        const chartHeight = margins.chartHeight;

        // 容错处理：确保图表区域有效
        if (chartWidth <= 0 || chartHeight <= 0) {
            setHoveredPoint(null);
            return;
        }

        // 计算相对于图表的鼠标CSS坐标
        const relativeMouseX = e.clientX - rect.left - chartX;
        const relativeMouseY = e.clientY - rect.top - chartY;

        // 检查鼠标是否在图表区域内（增加容错范围）
        const tolerance = 2; // 允许2像素的容错范围
        if (relativeMouseX < -tolerance || relativeMouseX > chartWidth + tolerance ||
            relativeMouseY < -tolerance || relativeMouseY > chartHeight + tolerance) {
            setHoveredPoint(null);
            return;
        }

        // 边界情况处理：限制鼠标坐标在有效范围内
        const clampedRelativeX = Math.max(0, Math.min(relativeMouseX, chartWidth));
        const clampedRelativeY = Math.max(0, Math.min(relativeMouseY, chartHeight));

        // 使用与K线绘制相同的计算方式，但基于CSS像素坐标
        const barWidth = Math.max(1, Math.min(20, chartWidth / displayData.length - 2));
        const barSpacing = (chartWidth - barWidth * displayData.length) / (displayData.length + 1);

        // 容错处理：确保barWidth和barSpacing有效
        if (isNaN(barWidth) || isNaN(barSpacing) || barWidth <= 0) {
            setHoveredPoint(null);
            return;
        }

        // 计算鼠标指向的K线索引（优化算法）
        let index = -1;

        // 方法1：基于位置的精确计算
        if (clampedRelativeX >= barSpacing) {
            // 计算可能属于哪个K线
            const potentialIndex = Math.floor((clampedRelativeX - barSpacing) / (barWidth + barSpacing));

            // 检查这个索引是否在有效范围内
            if (potentialIndex >= 0 && potentialIndex < displayData.length) {
                const candleX = barSpacing + potentialIndex * (barWidth + barSpacing);

                // 检查鼠标是否在这个K线的水平范围内
                if (clampedRelativeX >= candleX && clampedRelativeX <= candleX + barWidth) {
                    index = potentialIndex;
                } else {
                    // 如果不在范围内，检查相邻的K线
                    const checkPrevious = potentialIndex - 1;
                    const checkNext = potentialIndex + 1;

                    // 检查前一个K线
                    if (checkPrevious >= 0 && checkPrevious < displayData.length) {
                        const prevCandleX = barSpacing + checkPrevious * (barWidth + barSpacing);
                        if (clampedRelativeX >= prevCandleX && clampedRelativeX <= prevCandleX + barWidth) {
                            index = checkPrevious;
                        }
                    }

                    // 检查后一个K线
                    if (index === -1 && checkNext >= 0 && checkNext < displayData.length) {
                        const nextCandleX = barSpacing + checkNext * (barWidth + barSpacing);
                        if (clampedRelativeX >= nextCandleX && clampedRelativeX <= nextCandleX + barWidth) {
                            index = checkNext;
                        }
                    }
                }
            }
        }

        // 方法2：如果方法1没找到，使用距离最近的算法
        if (index === -1) {
            let minDistance = Infinity;

            for (let i = 0; i < displayData.length; i++) {
                const candleX = barSpacing + i * (barWidth + barSpacing);
                const candleCenterX = candleX + barWidth / 2;
                const distance = Math.abs(clampedRelativeX - candleCenterX);

                // 只考虑在合理距离范围内的K线
                const maxAcceptableDistance = (barWidth + barSpacing) * 0.75;

                if (distance < minDistance && distance <= maxAcceptableDistance) {
                    minDistance = distance;
                    index = i;
                }
            }
        }

        // 方法3：如果仍然没找到，使用边界检查
        if (index === -1) {
            // 检查是否在第一个K线的左边
            if (clampedRelativeX < barSpacing && displayData.length > 0) {
                index = 0;
            }
            // 检查是否在最后一个K线的右边
            else if (clampedRelativeX >= barSpacing + (displayData.length - 1) * (barWidth + barSpacing) + barWidth) {
                index = displayData.length - 1;
            }
        }

        // 最终边界保护
        if (index >= 0 && index < displayData.length) {
            // 索引有效，保持不变
        } else {
            // 索引无效，设置为默认值
            index = Math.max(0, Math.min(displayData.length - 1, Math.floor(clampedRelativeX / Math.max(1, chartWidth / displayData.length))));
        }

        // 计算鼠标Y轴位置对应的价格（基于CSS像素坐标）
        const priceRange = calculatePriceRange();
        let yPrice = null;

        // 计算主图高度用于价格计算
        const availableHeight = actualSize.height - CHART_LAYOUT.topMargin - CHART_LAYOUT.bottomMargin;

        // 复制drawChart中的高度计算逻辑以保持一致
        const hasRSI = isValidIndicator(indicators?.RSI);
        const hasMACD = isValidIndicator(indicators?.MACD);
        const separatorCount = (hasRSI ? 1 : 0) + (hasMACD ? 1 : 0) + 1;
        const totalSeparatorHeight = separatorCount * CHART_LAYOUT.separatorMargin;
        const heightForCharts = Math.max(0, availableHeight - totalSeparatorHeight);

        let mainChartHeightForPrice = heightForCharts * CHART_LAYOUT.mainChartRatio;
        if (!hasRSI) {
            mainChartHeightForPrice += heightForCharts * CHART_LAYOUT.rsiChartRatio;
        }
        if (!hasMACD) {
            mainChartHeightForPrice += heightForCharts * CHART_LAYOUT.macdChartRatio;
        }

        if (priceRange && !isNaN(priceRange.max) && !isNaN(priceRange.min) && priceRange.max !== priceRange.min) {
            // 价格范围有效，计算价格 - 只在主图区域内计算
            // 如果鼠标在主图区域外，不显示价格
            if (clampedRelativeY <= mainChartHeightForPrice) {
                yPrice = priceRange.max - (clampedRelativeY / mainChartHeightForPrice) * (priceRange.max - priceRange.min);

                // 确保计算的价格是有效数字
                if (isNaN(yPrice) || !isFinite(yPrice)) {
                    yPrice = null;
                }
            }
        }

        // 计算相对于容器的CSS坐标，用于浮窗定位
        const containerMouseX = e.clientX - rect.left;
        const containerMouseY = e.clientY - rect.top;

        // 最终容错处理：确保所有数据有效
        if (index >= 0 && index < displayData.length && displayData[index]) {
            // 验证数据项的有效性
            const dataItem = displayData[index];
            if (dataItem && typeof dataItem === 'object') {
                // 确保数据项有必要的属性
                const isValidData = (
                    typeof dataItem.open === 'number' &&
                    typeof dataItem.high === 'number' &&
                    typeof dataItem.low === 'number' &&
                    typeof dataItem.close === 'number' &&
                    typeof dataItem.volume === 'number' &&
                    typeof dataItem.timestamp === 'number' &&
                    !isNaN(dataItem.open) && !isNaN(dataItem.high) && !isNaN(dataItem.low) && !isNaN(dataItem.close) &&
                    !isNaN(dataItem.volume) && !isNaN(dataItem.timestamp) &&
                    isFinite(dataItem.open) && isFinite(dataItem.high) && isFinite(dataItem.low) && isFinite(dataItem.close) &&
                    isFinite(dataItem.volume) && isFinite(dataItem.timestamp)
                );

                if (isValidData) {
                    setHoveredPoint({
                        x: Math.max(0, containerMouseX),
                        y: Math.max(0, containerMouseY),
                        data: dataItem,
                        yPrice: yPrice !== null ? yPrice : undefined,
                        dataIndex: index
                    });
                    return;
                }
            }
        }

        // 如果任何验证失败，清除hover状态
        setHoveredPoint(null);
    };

    const handleMouseLeave = () => {
        setHoveredPoint(null);
    };

    // 监听容器尺寸变化
    useEffect(() => {
        let resizeObserver: ResizeObserver | null = null;
        let initTimer: ReturnType<typeof setTimeout> | null = null;
        let retryCount = 0;
        const maxRetries = 10;

        const updateCanvasSize = () => {
            if (containerRef.current) {
                const {clientWidth, clientHeight} = containerRef.current;
                // 确保容器有有效尺寸,且尺寸变化超过阈值(避免频繁更新)
                if (clientWidth > 0 && clientHeight > 0) {
                    const newSize = {width: clientWidth, height: clientHeight};

                    // 只有当尺寸真正改变时才更新,避免不必要的重绘
                    setActualSize(prev => {
                        // 允许5px的误差,避免微小变化导致频繁重绘
                        const widthChanged = Math.abs(prev.width - newSize.width) > 5;
                        const heightChanged = Math.abs(prev.height - newSize.height) > 5;

                        if (widthChanged || heightChanged) {
                            return newSize;
                        }
                        return prev;
                    });

                    return true;
                }
            }
            return false;
        };

        const initializeWithRetry = () => {
            if (updateCanvasSize()) {
                // 成功获取尺寸，停止重试
                return;
            }

            retryCount++;
            if (retryCount < maxRetries) {
                // 重试，逐渐增加延迟
                initTimer = setTimeout(initializeWithRetry, 100 * retryCount);
            }
        };

        // 延迟初始化，确保DOM完全渲染
        initTimer = setTimeout(initializeWithRetry, 300);

        // 防抖处理窗口大小变化
        let resizeTimeout: ReturnType<typeof setTimeout>;
        const handleResize = () => {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(() => {
                updateCanvasSize();
            }, 100);
        };

        window.addEventListener('resize', handleResize);

        // 使用ResizeObserver监听容器尺寸变化，添加防抖
        if (typeof ResizeObserver !== 'undefined') {
            resizeObserver = new ResizeObserver((entries) => {
                // 防抖处理
                clearTimeout(resizeTimeout);
                resizeTimeout = setTimeout(() => {
                    for (const entry of entries) {
                        const {width, height} = entry.contentRect;
                        // 只处理有效尺寸,避免用0覆盖有效值
                        if (width > 0 && height > 0) {
                            setActualSize(prev => {
                                const newSize = {width, height};
                                // 允许5px的误差,避免微小变化导致频繁重绘
                                const widthChanged = Math.abs(prev.width - newSize.width) > 5;
                                const heightChanged = Math.abs(prev.height - newSize.height) > 5;

                                if (widthChanged || heightChanged) {
                                    return newSize;
                                }
                                return prev;
                            });
                        }
                    }
                }, 50);
            });

            if (containerRef.current) {
                resizeObserver.observe(containerRef.current);
            }
        }

        return () => {
            if (initTimer) {
                clearTimeout(initTimer);
            }
            if (resizeTimeout) {
                clearTimeout(resizeTimeout);
            }
            window.removeEventListener('resize', handleResize);
            if (resizeObserver && containerRef.current) {
                resizeObserver.unobserve(containerRef.current);
            }
        };
    }, []);

    // 监听referenceLines变化，触发重绘
    useEffect(() => {
        if (referenceLines) {
            drawChart();
        }
    }, [referenceLines]);

    useEffect(() => {
        // 确保只有在有效尺寸时才进行绘制
        if (actualSize.width > 0 && actualSize.height > 0) {
            drawChart();
        }
    }, [data, actualSize.width, actualSize.height, hoveredPoint, markPrice, visibleIndicators, indicators, reverseOrder]);

    if (loading) {
        return <div style={{width, height, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
            <span>加载中...</span>
        </div>;
    }

    if (!data || data.length === 0) {
        return <div style={{width, height, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无K线数据"/>
        </div>;
    }

    return (
        <div ref={containerRef} style={{position: 'relative', width: '100%', height: '100%'}}>
            {/* 添加全局样式 */}
            <style>{`
                @keyframes pulse {
                    0% {
                        opacity: 1;
                        transform: scale(1);
                    }
                    50% {
                        opacity: 0.8;
                        transform: scale(1.02);
                    }
                    100% {
                        opacity: 1;
                        transform: scale(1);
                    }
                }
            `}</style>

            {/* 绘制技术指标图例（移至右侧） */}
            {!propVisibleIndicators && (
                <div style={{
                    position: 'absolute',
                    top: 25,
                    right: 10,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '2px',
                    pointerEvents: 'auto', // 允许点击
                    zIndex: 10, // 确保在canvas之上
                    backgroundColor: 'rgba(31, 31, 31, 0.85)', // 深色背景，略微透明
                    backdropFilter: 'blur(4px)', // 添加模糊效果，增强可读性
                    border: `1px solid ${COLORS.grid}`, // 灰色边框
                    padding: '4px',
                    borderRadius: '4px',
                    maxHeight: '200px', // 限制最大高度
                    overflowY: 'auto', // 内容过多时允许滚动
                    maxWidth: '140px', // 这里的宽度应与getChartMargins中的legendWidth一致
                    boxSizing: 'border-box', // 包含padding和border在宽度计算内
                }}>
                    {Object.keys(visibleIndicators).map(key => {
                        const [type, period] = key.split('_');
                        // 查找颜色
                        let color = '#fff';
                        if (['EMA', 'SMA', 'WMA', 'RSI'].includes(type)) {
                            const colors = (COLORS.indicatorColors as any)[type];
                            // 需要找到这是第几个周期
                            let index = 0;
                            const indicator = getIndicatorByName(type);
                            if (isValidIndicator(indicator)) {
                                const values = getIndicatorValues(indicator);
                                const sample = Array.isArray(values)
                                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                                    : undefined;
                                if (sample?.multiPeriodValues) {
                                    // 提取所有周期并排序
                                    const prefixMap: { [key: string]: string } = {
                                        EMA: 'ema_', SMA: 'sma_', WMA: 'wma_', RSI: 'rsi_'
                                    };
                                    const prefix = prefixMap[type] || type.toLowerCase() + '_';
                                    const periods = Object.keys(sample.multiPeriodValues)
                                        .filter(k => k.startsWith(prefix))
                                        .map(k => parseInt(k.replace(prefix, '')))
                                        .sort((a, b) => a - b);
                                    index = periods.indexOf(parseInt(period));
                                }
                            }
                            color = colors[index % colors.length];
                        } else if (type === 'BOLL') {
                            // BOLL多周期：根据周期索引选择对应颜色
                            const colors = (COLORS.indicatorColors as any).BOLL || [COLORS.bollUpper];
                            const indicator = getIndicatorByName('BOLL');
                            let index = 0;
                            const values = getIndicatorValues(indicator);
                            if (values && values[0]?.multiPeriodValues) {
                                // 必须与Canvas绘制逻辑保持完全一致的排序方式
                                const periodKeys = Object.keys(values[0].multiPeriodValues)
                                    .filter(k => k.startsWith('boll_'))
                                    .sort(); // Canvas使用默认的字典序排序

                                // 找到当前period对应的key的索引
                                const targetPeriod = parseInt(period);
                                index = periodKeys.findIndex(pk => {
                                    const match = pk.match(/boll_(\d+)/);
                                    const p = match ? parseInt(match[1]) : parseInt(pk.replace('boll_', ''));
                                    return p === targetPeriod;
                                });

                                // 如果没找到（不应该发生），默认为0
                                if (index === -1) index = 0;
                            }
                            color = colors[index % colors.length];
                        } else {
                            const colors = (COLORS.indicatorColors as any)[type] || ['#fff'];
                            let index = 0;
                            const indicator = getIndicatorByName(type);
                            if (isValidIndicator(indicator)) {
                                const values = getIndicatorValues(indicator);
                                const sample = Array.isArray(values)
                                    ? values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                                    : undefined;
                                if (sample?.multiPeriodValues) {
                                    const prefix = type.toLowerCase() + '_';
                                    const periods = Object.keys(sample.multiPeriodValues)
                                        .filter(k => k.startsWith(prefix))
                                        .map(k => parseInt(k.replace(prefix, '')))
                                        .sort((a, b) => a - b);
                                    index = periods.indexOf(parseInt(period));
                                }
                            }
                            color = colors[index % colors.length] || colors[0];
                        }

                        return (
                            <div key={key} style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px',
                                fontSize: '10px',
                                lineHeight: '12px', // 紧凑行高
                                height: '12px', // 固定高度
                                color: color,
                                cursor: 'pointer'
                            }} onClick={() => {
                                const newVisible = !visibleIndicators[key];
                                // 更新本地状态
                                setInternalVisibleIndicators(prev => ({
                                    ...prev,
                                    [key]: newVisible
                                }));

                                // 同步到全局配置
                                if (onUpdateIndicatorConfig && indicatorConfigs) {
                                    const config = indicatorConfigs[type as TechnicalIndicator];
                                    // 只有多周期指标才需要同步visiblePeriods
                                    if (config && (config.periods || ['EMA', 'SMA', 'WMA', 'RSI', 'BOLL'].includes(type))) {
                                        const p = parseInt(period);
                                        let newVisiblePeriods: number[] = [];

                                        if (config.visiblePeriods) {
                                            newVisiblePeriods = [...config.visiblePeriods];
                                        } else if (config.periods) {
                                            // 如果没有visiblePeriods但有periods，默认全选
                                            newVisiblePeriods = [...config.periods];
                                        } else if (config.period) {
                                            // 单周期兼容
                                            newVisiblePeriods = [config.period];
                                        }

                                        if (newVisible) {
                                            if (!newVisiblePeriods.includes(p)) newVisiblePeriods.push(p);
                                        } else {
                                            newVisiblePeriods = newVisiblePeriods.filter(v => v !== p);
                                        }

                                        onUpdateIndicatorConfig(type as TechnicalIndicator, {
                                            visiblePeriods: newVisiblePeriods
                                        });
                                    }
                                }
                            }}>
                                {/* 特殊处理BOLL指标的显示格式 */}
                                {type === 'BOLL' ? (
                                    <span>BOLL({period},{
                                        // 从配置中获取标准差值
                                        (() => {
                                            const config = indicatorConfigs?.['BOLL'];
                                            const periods = config?.periods || [];
                                            const stdDevs = config?.stdDevs || [];
                                            const periodNum = parseInt(period);
                                            const index = periods.indexOf(periodNum);
                                            const stdDev = index >= 0 && stdDevs[index] !== undefined
                                                ? stdDevs[index]
                                                : 2.0; // 默认标准差
                                            return stdDev.toFixed(1);
                                        })()
                                    })</span>
                                ) : (
                                    <span>{type}({period})</span>
                                )}
                                <input
                                    type="checkbox"
                                    checked={visibleIndicators[key]}
                                    readOnly
                                    style={{
                                        margin: 0,
                                        width: '10px',
                                        height: '10px',
                                        cursor: 'pointer',
                                        accentColor: color // 使用指标颜色作为勾选框颜色
                                    }}
                                />
                            </div>
                        );
                    })}
                </div>
            )}

            <canvas
                ref={canvasRef}
                style={{
                    width: '100%',
                    height: '100%',
                    cursor: 'crosshair',
                    backgroundColor: COLORS.background,
                    display: 'block'
                }}
                onMouseMove={handleMouseMove}
                onMouseLeave={handleMouseLeave}
            />

            {/* 数据提示框 */}
            {hoveredPoint?.data && (() => {
                const position = calculateTooltipPosition(hoveredPoint.x, hoveredPoint.y);
                return (
                    <div
                        style={{
                            position: 'absolute',
                            left: position.left,
                            top: position.top,
                            backgroundColor: 'rgba(0, 0, 0, 0.9)',
                            color: '#fff',
                            padding: '8px',
                            borderRadius: '6px',
                            fontSize: '12px',
                            zIndex: 1000,
                            pointerEvents: 'none',
                            minWidth: '180px',
                            maxWidth: renderTooltipExtra ? '320px' : '220px',
                            boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
                            border: '1px solid rgba(255, 255, 255, 0.1)'
                        }}
                    >
                        {/* 第一行：时间信息 */}
                        <div style={{
                            fontWeight: 'bold',
                            marginBottom: '6px',
                            borderBottom: '1px solid #555',
                            paddingBottom: '4px',
                            color: hoveredPoint.data.volume === 0 ? '#ff9800' : '#1890ff',
                            animation: hoveredPoint.data.volume === 0 ? 'pulse 2s infinite' : 'none',
                            textShadow: hoveredPoint.data.volume === 0 ? '0 0 8px rgba(255, 152, 0, 0.6)' : 'none'
                        }}>
                            {(() => {
                                const date = new Date(hoveredPoint.data.timestamp);
                                const year = date.getFullYear().toString().slice(-2);
                                const month = (date.getMonth() + 1).toString().padStart(2, '0');
                                const day = date.getDate().toString().padStart(2, '0');
                                const hours = date.getHours().toString().padStart(2, '0');
                                const minutes = date.getMinutes().toString().padStart(2, '0');
                                const timeStr = formatTimeLabel(hoveredPoint.data.timestamp, timeFrame);
                                // 判断是否是最后一根K线且K线时间未过期
                                const latestCandleTimestamp = displayData.length > 0
                                    ? (reverseOrder ? displayData[displayData.length - 1].timestamp : displayData[0].timestamp)
                                    : 0;
                                const isLastCandle = hoveredPoint.data.timestamp === latestCandleTimestamp &&
                                    latestCandleTimestamp >= Date.now();
                                return (hoveredPoint.data.confirm === 0 || isLastCandle) ? `${timeStr} (进行中)` : timeStr;
                            })()}
                        </div>


                        {/* OHLC价格信息 */}
                        <div style={{display: 'flex', justifyContent: 'space-between', gap: '12px', marginTop: '6px'}}>
                            <div>
                                <span style={{color: '#8c8c8c'}}>开: </span>
                                <span
                                    style={{color: '#52c41a'}}>{formatEffectiveDecimal(hoveredPoint.data.open, 4)}</span>
                            </div>
                            <div>
                                <span style={{color: '#8c8c8c'}}>高: </span>
                                <span
                                    style={{color: '#ff4d4f'}}>{formatEffectiveDecimal(hoveredPoint.data.high, 4)}</span>
                            </div>
                        </div>
                        <div style={{display: 'flex', justifyContent: 'space-between', gap: '12px', marginTop: '2px'}}>
                            <div>
                                <span style={{color: '#8c8c8c'}}>低: </span>
                                <span
                                    style={{color: '#ff4d4f'}}>{formatEffectiveDecimal(hoveredPoint.data.low, 4)}</span>
                            </div>
                            <div>
                                <span style={{color: '#8c8c8c'}}>收: </span>
                                <span
                                    style={{color: hoveredPoint.data.close >= hoveredPoint.data.open ? '#52c41a' : '#ff4d4f'}}>
                                {formatEffectiveDecimal(hoveredPoint.data.close, 4)}
                            </span>
                            </div>
                        </div>
                        <div style={{display: 'flex', justifyContent: 'space-between', gap: '12px', marginTop: '2px'}}>
                            <div>
                                <span style={{color: '#8c8c8c'}}>量: </span>
                                <span
                                    style={{color: hoveredPoint.data.close >= hoveredPoint.data.open ? '#52c41a' : '#ff4d4f'}}>
                                    {formatEffectiveDecimal(hoveredPoint.data.volume, 4)}
                                </span>
                            </div>
                            <div>
                                <span style={{color: '#8c8c8c'}}>幅: </span>
                                <span
                                    style={{color: hoveredPoint.data.close >= hoveredPoint.data.open ? '#52c41a' : '#ff4d4f'}}>
                                    {((hoveredPoint.data.high - hoveredPoint.data.low) / hoveredPoint.data.low * 100).toFixed(2)}%
                                </span>
                            </div>
                        </div>

                        {/* 技术指标信息 */}
                        {hoveredPoint.dataIndex !== undefined && indicators && (
                            <div style={{
                                marginTop: '8px',
                                paddingTop: '6px',
                                borderTop: '1px solid #555'
                            }}>
                                {/* EMA指标 */}
                                {(() => {
                                    if (!isValidIndicator(indicators.EMA)) return null;
                                    const emaValues = getMultiPeriodIndicatorValues(indicators.EMA, hoveredPoint.data.timestamp);
                                    const colors = COLORS.indicatorColors.EMA;
                                    const visibleValues = emaValues.filter(item => {
                                        const key = `EMA_${item.period}`;
                                        return visibleIndicators[key] === true;
                                    });
                                    if (visibleValues.length === 0) return null;
                                    return (
                                        <div style={{marginBottom: '4px'}}>
                                            <div style={{color: '#8c8c8c', fontSize: '11px', fontWeight: 'bold', marginBottom: '2px'}}>
                                                EMA:
                                            </div>
                                            <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                {visibleValues.map((item) => {
                                                    const originalIndex = emaValues.findIndex(v => v.period === item.period);
                                                    return (
                                                        <span key={item.period} style={{color: colors[originalIndex % colors.length], fontSize: '10px'}}>
                                                            EMA({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    );
                                })()}
                                {(() => {
                                    if (!isValidIndicator(indicators.SMA)) return null;
                                    const smaValues = getMultiPeriodIndicatorValues(indicators.SMA, hoveredPoint.data.timestamp);
                                    const colors = COLORS.indicatorColors.SMA;
                                    const visibleValues = smaValues.filter(item => {
                                        const key = `SMA_${item.period}`;
                                        return visibleIndicators[key] === true;
                                    });
                                    if (visibleValues.length === 0) return null;
                                    return (
                                        <div style={{marginBottom: '4px'}}>
                                            <div style={{color: '#8c8c8c', fontSize: '11px', fontWeight: 'bold', marginBottom: '2px'}}>
                                                SMA:
                                            </div>
                                            <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                {visibleValues.map((item) => {
                                                    const originalIndex = smaValues.findIndex(v => v.period === item.period);
                                                    return (
                                                        <span key={item.period} style={{color: colors[originalIndex % colors.length], fontSize: '10px'}}>
                                                            SMA({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    );
                                })()}

                                {/* WMA指标 */}
                                {(() => {
                                    if (!isValidIndicator(indicators.WMA)) return null;
                                    const wmaValues = getMultiPeriodIndicatorValues(indicators.WMA, hoveredPoint.data.timestamp);
                                    const colors = COLORS.indicatorColors.WMA;
                                    const visibleValues = wmaValues.filter(item => {
                                        const key = `WMA_${item.period}`;
                                        return visibleIndicators[key] === true;
                                    });
                                    if (visibleValues.length === 0) return null;
                                    return (
                                        <div style={{marginBottom: '4px'}}>
                                            <div style={{color: '#8c8c8c', fontSize: '11px', fontWeight: 'bold', marginBottom: '2px'}}>
                                                WMA:
                                            </div>
                                            <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                {visibleValues.map((item) => {
                                                    const originalIndex = wmaValues.findIndex(v => v.period === item.period);
                                                    return (
                                                        <span key={item.period} style={{color: colors[originalIndex % colors.length], fontSize: '10px'}}>
                                                            WMA({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    );
                                })()}{/* 布林带指标 */}
                                {(() => {
                                    if (!isValidIndicator(indicators.BOLL)) return null;
                                    const bollValues = getMultiPeriodBollingerBands(indicators.BOLL, hoveredPoint.data.timestamp, indicatorConfigs);
                                    const colors = COLORS.indicatorColors.BOLL;

                                    const visibleValues = bollValues.filter(item => {
                                        const key = `BOLL_${item.period}`;
                                        return visibleIndicators[key] !== false &&
                                            (item.upper !== undefined || item.middle !== undefined || item.lower !== undefined);
                                    });
                                    if (visibleValues.length === 0) return null;

                                    return (
                                        <div style={{marginBottom: '4px'}}>
                                            <div style={{color: '#8c8c8c', fontSize: '11px', fontWeight: 'bold', marginBottom: '2px'}}>
                                                BOLL:
                                            </div>
                                            <div>
                                                {visibleValues.map((item) => {
                                                    const originalIndex = bollValues.findIndex(v => v.period === item.period);
                                                    const bollColor = colors[originalIndex % colors.length];

                                                    return (
                                                        <div key={item.period} style={{display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '2px'}}>
                                                            <span style={{color: bollColor, fontSize: '10px', fontWeight: 'bold'}}>
                                                                BOLL({item.period}):
                                                            </span>
                                                            <span style={{color: bollColor, fontSize: '10px'}}>
                                                                UPPER: {item.upper !== undefined ? formatEffectiveDecimal(item.upper, 4) : '--'}
                                                            </span>
                                                            <span style={{color: bollColor, fontSize: '10px'}}>
                                                                MID: {item.middle !== undefined ? formatEffectiveDecimal(item.middle, 4) : '--'}
                                                            </span>
                                                            <span style={{color: bollColor, fontSize: '10px'}}>
                                                                LOWER: {item.lower !== undefined ? formatEffectiveDecimal(item.lower, 4) : '--'}
                                                            </span>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    );
                                })()}{/* RSI指标 */}
                                {(() => {
                                    if (!isValidIndicator(indicators.RSI)) return null;
                                    const rsiValues = getMultiPeriodIndicatorValues(indicators.RSI, hoveredPoint.data.timestamp);
                                    const colors = COLORS.indicatorColors.RSI;

                                    const visibleValues = rsiValues.filter(item => {
                                        const key = `RSI_${item.period}`;
                                        return visibleIndicators[key] === true;
                                    });
                                    if (visibleValues.length === 0) return null;

                                    return (
                                        <div style={{marginBottom: '4px'}}>
                                            <div style={{color: '#8c8c8c', fontSize: '11px', fontWeight: 'bold', marginBottom: '2px'}}>
                                                RSI:
                                            </div>
                                            <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                {visibleValues.map((item) => {
                                                    const originalIndex = rsiValues.findIndex(v => v.period === item.period);
                                                    const rsiColor = colors[originalIndex % colors.length];
                                                    return (
                                                        <span key={item.period} style={{color: rsiColor, fontSize: '10px'}}>
                                                            RSI({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    );
                                })()}

                                {/* 通用指标渲染 (MACD, KDJ, CCI, ATR, OBV, ADX) */}
                                {['MACD', 'KDJ', 'CCI', 'ATR', 'OBV', 'ADX'].map(type => {
                                    const indicator = getIndicatorByName(type);
                                    if (!isValidIndicator(indicator)) return null;

                                    /**
                                     * 处理 MACD 的特殊可见性逻辑
                                     *
                                     * 逻辑说明：
                                     * 1. MACD 通常是全局开启或关闭。
                                     * 2. 如果未配置特定可见性，默认显示。
                                     */
                                    if (type === 'MACD') {
                                        const macdVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('MACD_'));
                                        const showMACD = macdVisibilityKeys.length === 0 ? true : macdVisibilityKeys.some(key => !!visibleIndicators[key]);
                                        if (!showMACD) return null;

                                        const macdValues = getIndicatorValues(indicator);
                                        const point = Array.isArray(macdValues)
                                            ? macdValues.find((p: any) => p?.timestamp === hoveredPoint.data?.timestamp)
                                            : undefined;

                                        if (!point) return null;

                                        return (
                                            <div key={type} style={{marginBottom: '4px'}}>
                                                <div style={{
                                                    color: '#8c8c8c',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold',
                                                    marginBottom: '2px'
                                                }}>
                                                    MACD:
                                                </div>
                                                <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                    <span style={{color: '#ff4d4f', fontSize: '10px'}}>
                                                        DIF: {point.diff !== undefined ? formatEffectiveDecimal(point.diff, 4) : '--'}
                                                    </span>
                                                    <span style={{color: '#52c41a', fontSize: '10px'}}>
                                                        DEA: {point.dea !== undefined ? formatEffectiveDecimal(point.dea, 4) : '--'}
                                                    </span>
                                                    <span style={{color: '#fa8c16', fontSize: '10px'}}>
                                                        BAR: {point.macd !== undefined ? formatEffectiveDecimal(point.macd, 4) : '--'}
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    }

                                    if (type === 'OBV') {
                                        const obvVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('OBV_'));
                                        const showOBV = obvVisibilityKeys.length === 0 ? true : obvVisibilityKeys.some(key => !!visibleIndicators[key]);
                                        if (!showOBV) return null;

                                        const obvValues = getMultiPeriodIndicatorValues(indicator, hoveredPoint.data?.timestamp || 0);
                                        
                                        if (!obvValues || obvValues.length === 0) return null;

                                        return (
                                            <div key={type} style={{marginBottom: '4px'}}>
                                                <div style={{
                                                    color: '#8c8c8c',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold',
                                                    marginBottom: '2px'
                                                }}>
                                                    OBV:
                                                </div>
                                                <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                    {obvValues.map((item, index) => (
                                                        <span key={item.period} style={{color: '#ffeb3b', fontSize: '10px'}}>
                                                            OBV({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    ))}
                                                </div>
                                            </div>
                                        );
                                    }

                                    if (type === 'KDJ') {
                                        const kdjVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('KDJ_'));
                                        const showKDJ = kdjVisibilityKeys.length === 0 ? true : kdjVisibilityKeys.some(key => !!visibleIndicators[key]);
                                        if (!showKDJ) return null;

                                        const kdjValues = getMultiPeriodKdjValues(indicator, hoveredPoint.data?.timestamp || 0);
                                        const colors = (COLORS.indicatorColors as any).KDJ || ['#13c2c2', '#9254de', '#fa8c16', '#52c41a', '#f5222d', '#1890ff', '#722ed1'];
                                        
                                        if (!kdjValues || kdjValues.length === 0) return null;

                                        return (
                                            <div key={type} style={{marginBottom: '4px'}}>
                                                <div style={{
                                                    color: '#8c8c8c',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold',
                                                    marginBottom: '2px'
                                                }}>
                                                    KDJ:
                                                </div>
                                                <div style={{display: 'flex', flexDirection: 'column', gap: '2px'}}>
                                                    {kdjValues.map(item => {
                                                        const originalIndex = kdjValues.findIndex(v => v.period === item.period);
                                                        const color = colors[originalIndex % colors.length];
                                                        return (
                                                            <div key={item.period} style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                                <span style={{color, fontSize: '10px', fontWeight: 'bold'}}>KDJ({item.period})</span>
                                                                <span style={{color, fontSize: '10px'}}>K: {item.k !== undefined ? formatEffectiveDecimal(item.k, 4) : '--'}</span>
                                                                <span style={{color, fontSize: '10px'}}>D: {item.d !== undefined ? formatEffectiveDecimal(item.d, 4) : '--'}</span>
                                                                <span style={{color, fontSize: '10px'}}>J: {item.j !== undefined ? formatEffectiveDecimal(item.j, 4) : '--'}</span>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        );
                                    }

                                    if (type === 'CCI') {
                                        const cciVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('CCI_'));
                                        const showCCI = cciVisibilityKeys.length === 0 ? true : cciVisibilityKeys.some(key => !!visibleIndicators[key]);
                                        if (!showCCI) return null;

                                        const cciValues = getMultiPeriodIndicatorValues(indicator, hoveredPoint.data?.timestamp || 0);
                                        const colors = (COLORS.indicatorColors as any).CCI || ['#ffeb3b', '#ffc107', '#ff9800'];
                                        
                                        if (!cciValues || cciValues.length === 0) return null;

                                        return (
                                            <div key={type} style={{marginBottom: '4px'}}>
                                                <div style={{
                                                    color: '#8c8c8c',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold',
                                                    marginBottom: '2px'
                                                }}>
                                                    CCI:
                                                </div>
                                                <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                    {cciValues.map((item) => (
                                                        <span key={item.period} style={{color: colors[0], fontSize: '10px'}}>
                                                            CCI({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    ))}
                                                </div>
                                            </div>
                                        );
                                    }

                                    if (type === 'ATR') {
                                        const atrVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('ATR_'));
                                        const showATR = atrVisibilityKeys.length === 0 ? true : atrVisibilityKeys.some(key => !!visibleIndicators[key]);
                                        if (!showATR) return null;

                                        const atrValues = getMultiPeriodIndicatorValues(indicator, hoveredPoint.data?.timestamp || 0);
                                        const colors = (COLORS.indicatorColors as any).ATR || ['#ffeb3b', '#ffc107', '#ff9800'];
                                        
                                        if (!atrValues || atrValues.length === 0) return null;

                                        return (
                                            <div key={type} style={{marginBottom: '4px'}}>
                                                <div style={{
                                                    color: '#8c8c8c',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold',
                                                    marginBottom: '2px'
                                                }}>
                                                    ATR:
                                                </div>
                                                <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                    {atrValues.map((item) => (
                                                        <span key={item.period} style={{color: colors[0], fontSize: '10px'}}>
                                                            ATR({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    ))}
                                                </div>
                                            </div>
                                        );
                                    }

                                    if (type === 'ADX') {
                                        const adxVisibilityKeys = Object.keys(visibleIndicators).filter(key => key.startsWith('ADX_'));
                                        const showADX = adxVisibilityKeys.length === 0 ? true : adxVisibilityKeys.some(key => !!visibleIndicators[key]);
                                        if (!showADX) return null;

                                        const adxValues = getMultiPeriodIndicatorValues(indicator, hoveredPoint.data?.timestamp || 0);
                                        const colors = (COLORS.indicatorColors as any).ADX || ['#ffeb3b', '#ffc107', '#ff9800'];
                                        
                                        if (!adxValues || adxValues.length === 0) return null;

                                        return (
                                            <div key={type} style={{marginBottom: '4px'}}>
                                                <div style={{
                                                    color: '#8c8c8c',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold',
                                                    marginBottom: '2px'
                                                }}>
                                                    ADX:
                                                </div>
                                                <div style={{display: 'flex', flexWrap: 'wrap', gap: '8px'}}>
                                                    {adxValues.map((item) => (
                                                        <span key={item.period} style={{color: colors[0], fontSize: '10px'}}>
                                                            ADX({item.period}): {item.value !== undefined ? formatEffectiveDecimal(item.value, 4) : '--'}
                                                        </span>
                                                    ))}
                                                </div>
                                            </div>
                                        );
                                    }

                                    {/**
                                     * 处理其他指标的渲染
                                     *
                                     * 逻辑说明：
                                     * 1. 包含 KDJ 的多周期渲染。
                                     * 2. 包含 CCI, ATR 等常规指标。
                                     * 3. 如果没有任何可见子项，则隐藏整个指标容器。
                                     */
                                    }
                                })}
                            </div>
                        )}

                        {(() => {
                            if (!renderTooltipExtra || hoveredPoint.dataIndex === undefined) return null;
                            const extra = renderTooltipExtra({
                                candle: hoveredPoint.data,
                                dataIndex: hoveredPoint.dataIndex,
                                timeFrame: timeFrame ?? '1m',
                                referenceLines
                            });
                            if (!extra) return null;
                            return (
                                <div style={{
                                    marginTop: '8px',
                                    paddingTop: '6px',
                                    borderTop: '1px solid #555'
                                }}>
                                    {extra}
                                </div>
                            );
                        })()}
                    </div>
                );
            })()}
        </div>
    );
};

export default CandlestickChart;
