import {useCallback, useEffect, useState} from 'react';

// 定义技术指标类型
export type TechnicalIndicator = 'EMA' | 'SMA' | 'WMA' | 'RSI' | 'BOLL' | 'MACD' | 'KDJ' | 'CCI' | 'ATR' | 'OBV' | 'ADX';

// 定义时间帧类型
export type TimeFrame = '1m' | '5m' | '1h' | '4h' | '1d';

// 定义K线采样类型
export type KlineLimit = 10 | 30 | 60 | 90 | 120 | 150 | 180 | 240;

// 定义指标配置接口
export interface IndicatorConfig {
    period: number; // 保持向后兼容
    stdDev?: number; // 仅BOLL使用（单个标准差）
    fastPeriod?: number; // MACD使用
    slowPeriod?: number; // MACD使用
    signalPeriod?: number; // MACD使用
    // 新增多周期支持
    periods?: number[]; // 多周期配置数组
    periodColors?: string[]; // 每个周期对应的颜色
    visiblePeriods?: number[]; // 可见周期数组
    stdDevs?: number[]; // BOLL专用：每个周期对应的标准差数组
}

// 定义K线图状态接口
export interface ChartState {
    timeFrame: TimeFrame;
    klineLimit: KlineLimit;
    selectedIndicators: TechnicalIndicator[];
    indicatorConfigs: Record<TechnicalIndicator, IndicatorConfig>;
}

// 默认状态配置
const DEFAULT_CHART_STATE: ChartState = {
    timeFrame: '5m', // 默认5分钟
    klineLimit: 240, // 默认240条K线
    selectedIndicators: [],
    indicatorConfigs: {
        EMA: {period: 20},
        SMA: {period: 20},
        WMA: {period: 20},
        RSI: {period: 14},
        BOLL: {period: 20, stdDev: 2},
        MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9}, // MACD标准参数
        KDJ: {period: 9},
        CCI: {period: 20},
        ATR: {period: 14},
        OBV: {period: 20},
        ADX: {period: 14}
    }
};

// localStorage key
const CHART_STATE_STORAGE_KEY = 'chart-state';

// 验证时间帧 - 移到函数外部避免初始化顺序问题
const isValidTimeFrame = (value: any): value is TimeFrame => {
    return ['1m', '5m', '1h', '4h', '1d'].includes(value);
};

// 验证技术指标 - 移到函数外部避免初始化顺序问题
const isValidIndicator = (value: any): value is TechnicalIndicator => {
    return ['EMA', 'SMA', 'WMA', 'RSI', 'BOLL', 'MACD', 'KDJ', 'CCI', 'ATR', 'OBV', 'ADX'].includes(value);
};

// 验证K线采样 - 移到函数外部避免初始化顺序问题
const isValidKlineLimit = (value: any): value is KlineLimit => {
    return [10, 30, 60, 90, 120, 150, 180, 240].includes(value);
};

// 深度比较函数，用于检测配置变化
const deepEqual = (obj1: any, obj2: any): boolean => {
    if (obj1 === obj2) return true;

    if (obj1 == null || obj2 == null) return obj1 === obj2;

    if (typeof obj1 !== typeof obj2) return false;

    if (typeof obj1 !== 'object') return obj1 === obj2;

    if (Array.isArray(obj1) !== Array.isArray(obj2)) return false;

    const keys1 = Object.keys(obj1);
    const keys2 = Object.keys(obj2);

    if (keys1.length !== keys2.length) return false;

    for (const key of keys1) {
        if (!keys2.includes(key)) return false;
        if (!deepEqual(obj1[key], obj2[key])) return false;
    }

    return true;
};

/**
 * K线图状态管理Hook
 * 提供状态持久化和统一的状态管理
 */
export const useChartState = (instId?: string) => {
    // 从localStorage读取状态
    const loadStateFromStorage = useCallback((): ChartState => {
        try {
            if (!instId) return DEFAULT_CHART_STATE;

            const stored = localStorage.getItem(`${CHART_STATE_STORAGE_KEY}-${instId}`);
            if (!stored) return DEFAULT_CHART_STATE;

            const parsedState = JSON.parse(stored);

            // 验证状态结构
            return {
                timeFrame: isValidTimeFrame(parsedState.timeFrame) ? parsedState.timeFrame : DEFAULT_CHART_STATE.timeFrame,
                klineLimit: isValidKlineLimit(parsedState.klineLimit) ? parsedState.klineLimit : DEFAULT_CHART_STATE.klineLimit,
                selectedIndicators: Array.isArray(parsedState.selectedIndicators)
                    ? parsedState.selectedIndicators.filter(isValidIndicator)
                    : DEFAULT_CHART_STATE.selectedIndicators,
                indicatorConfigs: {
                    ...DEFAULT_CHART_STATE.indicatorConfigs,
                    ...(parsedState.indicatorConfigs || {})
                }
            };
        } catch (error) {
            console.warn('Failed to load chart state from localStorage:', error);
            return DEFAULT_CHART_STATE;
        }
    }, [instId]);

    // 保存状态到localStorage
    const saveStateToStorage = useCallback((state: ChartState) => {
        try {
            if (!instId) return;

            localStorage.setItem(`${CHART_STATE_STORAGE_KEY}-${instId}`, JSON.stringify(state));
        } catch (error) {
            console.warn('Failed to save chart state to localStorage:', error);
        }
    }, [instId]);

    // 初始化状态
    const [chartState, setChartState] = useState<ChartState>(loadStateFromStorage);

    // 更新时间帧
    const updateTimeFrame = useCallback((newTimeFrame: TimeFrame) => {
        setChartState(prev => {
            const newState = {...prev, timeFrame: newTimeFrame};
            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 更新K线采样
    const updateKlineLimit = useCallback((newKlineLimit: KlineLimit) => {
        setChartState(prev => {
            const newState = {...prev, klineLimit: newKlineLimit};
            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 添加技术指标
    const addIndicator = useCallback((indicator: TechnicalIndicator, config?: Partial<IndicatorConfig>) => {
        setChartState(prev => {
            if (prev.selectedIndicators.includes(indicator)) {
                return prev; // 已存在，不重复添加
            }

            const newState = {
                ...prev,
                selectedIndicators: [...prev.selectedIndicators, indicator],
                indicatorConfigs: {
                    ...prev.indicatorConfigs,
                    [indicator]: {...prev.indicatorConfigs[indicator], ...config}
                }
            };

            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 移除技术指标
    const removeIndicator = useCallback((indicator: TechnicalIndicator) => {
        setChartState(prev => {
            const newState = {
                ...prev,
                selectedIndicators: prev.selectedIndicators.filter(i => i !== indicator)
            };

            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 更新指标配置
    const updateIndicatorConfig = useCallback((indicator: TechnicalIndicator, config: Partial<IndicatorConfig>) => {
        console.log(`[useChartState] 更新指标配置 ${indicator}:`, config);

        setChartState(prev => {
            const currentConfig = prev.indicatorConfigs[indicator];
            let newConfig = {...currentConfig, ...config};

            // 技术指标周期上限验证：最大不能超过60
            const MAX_PERIOD = 60;
            if (newConfig.periods) {
                // 过滤超过60的周期
                const validPeriods = newConfig.periods.filter(p => p <= MAX_PERIOD);
                const filteredPeriods = newConfig.periods.filter(p => p > MAX_PERIOD);

                if (filteredPeriods.length > 0) {
                    console.warn(`[useChartState] 指标 ${indicator} 过滤超过${MAX_PERIOD}的周期:`, filteredPeriods);
                }

                newConfig.periods = validPeriods;
                // 同时过滤对应的颜色和可见性配置
                if (newConfig.periodColors) {
                    newConfig.periodColors = newConfig.periodColors.slice(0, validPeriods.length);
                }
                if (newConfig.visiblePeriods) {
                    newConfig.visiblePeriods = newConfig.visiblePeriods.filter(p => p <= MAX_PERIOD);
                }
            }

            // 确保配置包含多周期格式（向后兼容处理）
            if (!newConfig.periods && newConfig.period) {
                if (indicator === 'RSI') {
                    newConfig.periods = [newConfig.period];
                    newConfig.periodColors = ['#1890ff'];
                } else if (indicator === 'BOLL') {
                    newConfig.periods = [newConfig.period];
                    newConfig.periodColors = ['#52c41a'];
                } else if (['EMA', 'SMA', 'WMA'].includes(indicator)) {
                    newConfig.periods = [newConfig.period];
                    const colorMap: Record<string, string> = {
                        'EMA': '#1890ff',
                        'SMA': '#ff9800',
                        'WMA': '#722ed1'
                    };
                    newConfig.periodColors = [colorMap[indicator] || '#1890ff'];
                }
            }

            // 使用深度比较来检测变化 - 更可靠
            const hasChanges = !deepEqual(currentConfig, newConfig);

            console.log(`[useChartState] 配置变化检测 ${indicator}:`, {
                hasChanges,
                当前配置: currentConfig,
                新配置: newConfig,
                配置类型: {
                    当前periods: Array.isArray(currentConfig.periods),
                    新periods: Array.isArray(newConfig.periods),
                    当前periods长度: currentConfig.periods?.length,
                    新periods长度: newConfig.periods?.length
                }
            });

            if (!hasChanges) {
                console.log(`[useChartState] 指标 ${indicator} 配置无实际变化，跳过更新`);
                return prev;
            }

            // 检查指标是否还有有效配置（周期或单周期）
            const hasValidConfig = (() => {
                // 有多周期配置
                if (newConfig.periods && newConfig.periods.length > 0) {
                    return true;
                }
                // 向后兼容：有单周期配置（RSI除外，RSI强制多周期）
                // 对于EMA、SMA、WMA，period必须是大于0的数字
                if (newConfig.period && indicator !== 'RSI' && newConfig.period > 0) {
                    return true;
                }
                return false;
            })();

            console.log(`[useChartState] 指标 ${indicator} 有效配置检查:`, {hasValidConfig, newConfig});

            // 构建新状态
            let newSelectedIndicators = prev.selectedIndicators;
            if (!hasValidConfig && prev.selectedIndicators.includes(indicator)) {
                // 如果没有有效配置，从选中列表中移除该指标
                newSelectedIndicators = prev.selectedIndicators.filter(i => i !== indicator);
                console.log(`[useChartState] 指标 ${indicator} 无有效配置，从选中列表移除`);
            }

            const newState = {
                ...prev,
                selectedIndicators: newSelectedIndicators,
                indicatorConfigs: {
                    ...prev.indicatorConfigs,
                    [indicator]: newConfig
                }
            };

            console.log(`[useChartState] 指标 ${indicator} 配置已更新，触发重渲染`);

            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 切换指标选中状态
    const toggleIndicator = useCallback((indicator: TechnicalIndicator, config?: Partial<IndicatorConfig>) => {
        setChartState(prev => {
            const isSelected = prev.selectedIndicators.includes(indicator);

            let newSelectedIndicators: TechnicalIndicator[];
            if (isSelected) {
                newSelectedIndicators = prev.selectedIndicators.filter(i => i !== indicator);
            } else {
                newSelectedIndicators = [...prev.selectedIndicators, indicator];
            }

            // 确保使用传入的配置，不强制覆盖
            const newConfig = {...prev.indicatorConfigs[indicator], ...config};

            // 如果没有periods但有period，创建向后兼容的配置
            if (indicator === 'RSI' && !newConfig.periods && newConfig.period) {
                newConfig.periods = [newConfig.period];
                newConfig.periodColors = ['#1890ff'];
            } else if (indicator === 'BOLL' && !newConfig.periods && newConfig.period) {
                // 为BOLL提供默认的多周期配置
                newConfig.periods = [newConfig.period];
                newConfig.periodColors = ['#52c41a'];
            } else if (['EMA', 'SMA', 'WMA'].includes(indicator) && !newConfig.periods && newConfig.period) {
                // 为移动平均线指标提供默认的多周期配置
                newConfig.periods = [newConfig.period];
                const colorMap: Record<string, string> = {
                    'EMA': '#1890ff',
                    'SMA': '#ff9800',
                    'WMA': '#722ed1'
                };
                newConfig.periodColors = [colorMap[indicator] || '#1890ff'];
            }

            const newState = {
                ...prev,
                selectedIndicators: newSelectedIndicators,
                indicatorConfigs: {
                    ...prev.indicatorConfigs,
                    [indicator]: newConfig
                }
            };

            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 重置所有状态
    const resetState = useCallback(() => {
        const newState = DEFAULT_CHART_STATE;
        setChartState(newState);
        saveStateToStorage(newState);
    }, [saveStateToStorage]);

    // 清除所有技术指标
    const clearAllIndicators = useCallback(() => {
        console.log('[useChartState] 清除所有技术指标');
        setChartState(prev => {
            // 完全清空指标配置，而不保留默认配置
            const emptyIndicatorConfigs: Record<TechnicalIndicator, IndicatorConfig> = {
                EMA: {period: 20},
                SMA: {period: 20},
                WMA: {period: 20},
                RSI: {period: 14},
                BOLL: {period: 20, stdDev: 2},
                MACD: {period: 12, fastPeriod: 12, slowPeriod: 26, signalPeriod: 9},
                KDJ: {period: 9},
                CCI: {period: 20},
                ATR: {period: 14},
                OBV: {period: 20},
                ADX: {period: 14}
            };

            const newState = {
                timeFrame: prev.timeFrame, // 保持当前时间帧
                klineLimit: prev.klineLimit, // 保持当前K线采样设置
                selectedIndicators: [], // 清空所有选中的指标
                indicatorConfigs: emptyIndicatorConfigs // 使用空配置，清空所有periods等
            };

            console.log('[useChartState] 清除后的状态:', newState);
            saveStateToStorage(newState);
            return newState;
        });
    }, [saveStateToStorage]);

    // 监听instId变化，重新加载状态
    useEffect(() => {
        const newState = loadStateFromStorage();
        setChartState(newState);
    }, [loadStateFromStorage]);

    return {
        // 状态
        chartState,
        timeFrame: chartState.timeFrame,
        klineLimit: chartState.klineLimit,
        selectedIndicators: chartState.selectedIndicators,
        indicatorConfigs: chartState.indicatorConfigs,

        // 操作方法
        updateTimeFrame,
        updateKlineLimit,
        addIndicator,
        removeIndicator,
        updateIndicatorConfig,
        toggleIndicator,
        resetState,
        clearAllIndicators
    };
};