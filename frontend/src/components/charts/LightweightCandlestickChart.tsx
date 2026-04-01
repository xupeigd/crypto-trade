import React, {useEffect, useRef, useState, useCallback} from 'react';
import {createChart, IChartApi, ISeriesApi, CandlestickSeries, HistogramSeries, LineSeries, UTCTimestamp, ColorType} from 'lightweight-charts';

// 时间戳格式检测阈值：10000000000 对应 2286-11-20，大于此值为毫秒级
const TIMESTAMP_MS_THRESHOLD = 10000000000;

/**
 * 各指标类型的多周期颜色数组（按周期升序排列时依次分配）
 * 颜色之间需有足够区分度，且不与其他指标类型的颜色冲突
 */
const MULTI_PERIOD_COLORS: Record<string, string[]> = {
    // EMA：蓝色系 → 深蓝、天蓝
    EMA: ['#1890ff', '#00b4d8', '#0077b6', '#48cae4'],
    // SMA：玫红/橙色系
    SMA: ['#eb2f96', '#fa541c', '#fadb14', '#a8071a'],
    // RSI：紫色系 → 深紫、中紫
    RSI: ['#9254de', '#d46b08', '#389e0d', '#0958d9'],
    // WMA：绿色系
    WMA: ['#52c41a', '#73d13d', '#95de64', '#b7eb8f'],
    // CCI：青色系
    CCI: ['#13c2c2', '#36cfc9', '#5cdbd3', '#87e8de'],
    // ATR：橙色系
    ATR: ['#fa8c16', '#ffa940', '#ffc06d', '#ffd591'],
    // OBV：蓝色系（与EMA区分）
    OBV: ['#2f54eb', '#597ef7', '#85a5ff', '#adc6ff'],
    // ADX：红色系
    ADX: ['#f5222d', '#ff4d4f', '#ff7875', '#ffa39e'],
};

/**
 * KDJ 多周期颜色组：每个周期有独立色系，K/D/J 三线在同一色系内有明暗区分
 */
const KDJ_COLOR_SETS = [
    // period 0：橙色系（K实线、D亮橙、J深橙）
    {K: '#fa8c16', D: '#ffc53d', J: '#ad4e00'},
    // period 1：青色系（K实线、D亮青、J深青）
    {K: '#13c2c2', D: '#5cdbd3', J: '#006d75'},
    // period 2：洋红色系（K实线、D粉红、J深红）
    {K: '#eb2f96', D: '#ff85c2', J: '#9e1068'},
] as const;

/**
 * 将时间戳转换为秒级 UTCTimestamp
 * 自动检测输入是秒级（10位）还是毫秒级（13位）
 */
const toUTCTimestamp = (time: number): UTCTimestamp => {
    if (time >= TIMESTAMP_MS_THRESHOLD) {
        // 毫秒级，需要除以 1000
        return Math.floor(time / 1000) as UTCTimestamp;
    }
    // 秒级，直接使用
    return Math.floor(time) as UTCTimestamp;
};

export type KLineBar = {
    time: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
    confirmed?: boolean;
};

export interface TechnicalIndicatorData {
    time: number;
    value?: number;
    upper?: number;
    middle?: number;
    lower?: number;
    signal?: number;
    histogram?: number;
    diff?: number;
    k?: number;
    d?: number;
    j?: number;
}

interface LightweightCandlestickChartProps {
    data: KLineBar[];
    width?: number;
    height?: number;
    markPrice?: number | null;
    markPriceColor?: string;
    maxVisibleBars?: number;
    timeFrame?: string;
    loading?: boolean;
    indicators?: {
        EMA?: TechnicalIndicatorData[];
        SMA?: TechnicalIndicatorData[];
        WMA?: TechnicalIndicatorData[];
        RSI?: TechnicalIndicatorData[];
        MACD?: TechnicalIndicatorData[];
        BOLL?: TechnicalIndicatorData[];
        KDJ?: TechnicalIndicatorData[];
        CCI?: TechnicalIndicatorData[];
        ATR?: TechnicalIndicatorData[];
        OBV?: TechnicalIndicatorData[];
        ADX?: TechnicalIndicatorData[];
    };
    visibleIndicators?: Record<string, boolean>;
    referenceLines?: {
        avgPx?: number;
        closePx?: number;
        takeProfitPx?: number;
        stopLossPx?: number;
        liquidationPx?: number;
        cTime?: number;       // 开仓时间（毫秒时间戳）
        uTime?: number;       // 平仓时间（毫秒时间戳）
        posSide?: string; // 持仓方向：long=做多，short=做空
        // K线形态可视化支撑/阻力线（支持时间范围）
        supportLines?: Array<{ price: number; startTime?: number; endTime?: number; label?: string }>;    // 支撑线（蓝色）
        resistanceLines?: Array<{ price: number; startTime?: number; endTime?: number; label?: string }>; // 阻力线（橙色）
    };
    onTooltip?: (payload: {time: number; open: number; high: number; low: number; close: number; volume: number}) => void;
    // 调试用：强制清除所有价格线
    debugForceClearPriceLines?: boolean;
    // 是否显示开仓价水平线（历史持仓时设为false，只显示标签）
    showEntryLine?: boolean;
    // 是否锁定右边缘，禁止向右滑动（历史持仓时设为true）
    lockRightEdge?: boolean;
    // 是否显示开仓价标签（Y轴标签）
    showAvgPx?: boolean;
    // 是否显示平仓价标签（Y轴标签）
    showClosePx?: boolean;
}

const LightweightCandlestickChart: React.FC<LightweightCandlestickChartProps> = ({
    data,
    width,
    height,
    markPrice,
    markPriceColor,
    maxVisibleBars,
    timeFrame,
    indicators,
    visibleIndicators = {},
    referenceLines,
    loading = false,
    onTooltip,
    debugForceClearPriceLines = false,
    showEntryLine = true,
    lockRightEdge = true,
    showAvgPx = true,
    showClosePx = true,
}) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const candlestickSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
    const volumeSeriesRef = useRef<ISeriesApi<'Histogram'> | null>(null);
    const indicatorSeriesRef = useRef<Record<string, any>>({});
    const [chartReady, setChartReady] = useState(false);
    const [containerSize, setContainerSize] = useState({width: 0, height: 0});

    const priceLineRefs = useRef<any[]>([]);
    const markPriceLineRef = useRef<any>(null);
    const entryLineSeriesRef = useRef<ISeriesApi<'Line'> | null>(null);
    const supportResistanceLineRefs = useRef<ISeriesApi<'Line'>[]>([]);  // 支撑阻力线 Series 引用
    const supportResistancePriceLineRefs = useRef<any[]>([]);  // 支撑阻力线 PriceLine 标签引用

    // 指标数据查找表：时间戳(秒) → { indicatorKey → 数据点 }
    const indicatorDataLookupRef = useRef<Record<number, Record<string, any>>>({});
    // 指标颜色索引：indicatorKey → 同类型中的排序索引
    const indicatorColorIndexRef = useRef<Record<string, number>>({});
    // Pane 索引映射：indicatorType → paneIndex（持久化分配）
    const paneIndexMapRef = useRef<Record<string, number>>({});

    // Tooltip 状态
    const [tooltipData, setTooltipData] = useState<{
        time: number;
        open: number;
        high: number;
        low: number;
        close: number;
        volume: number;
    } | null>(null);
    const [tooltipPosition, setTooltipPosition] = useState<{ x: number; y: number } | null>(null);
    // 当前十字线所在时刻的指标值
    const [tooltipIndicators, setTooltipIndicators] = useState<Record<string, any> | null>(null);

    // 优先使用传入的 height prop，否则使用容器实际高度（从 ResizeObserver 获取），最后默认 500
    const chartHeight = height || containerSize.height || 500;

    // 防抖标志：避免 initChart 被快速连续调用
    const initChartInProgressRef = useRef(false);
    // 图表更新标志：防止 removeSeries/addSeries 操作期间触发 subscribeCrosshairMove 回调导致无限循环
    const isUpdatingRef = useRef(false);
    const overlayCanvasRef = useRef<HTMLCanvasElement>(null);

    /**
     * 绘制止盈止损区域背景（只在 K 线主图区域，不包含下方成交量副图和技术指标副图）
     * 做多时：止盈区域（顶部到止盈价）绿色，止损区域（止损价到底部）红色
     * 做空时：止损区域（顶部到止损价）红色，止盈区域（止盈价到底部）绿色
     * 同时绘制开仓/平仓围闭区域
     */
    const drawTPSLBackground = useCallback(() => {
        const canvas = overlayCanvasRef.current;
        const chart = chartRef.current;
        const series = candlestickSeriesRef.current;
        
        if (!canvas || !chart || !series || !referenceLines) return;
        
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        
        // 将价格转换为数字类型
        const takeProfitPxRaw = referenceLines.takeProfitPx;
        const stopLossPxRaw = referenceLines.stopLossPx;
        const posSide = referenceLines.posSide || 'long'; // 默认为做多
        
        const takeProfitPx = typeof takeProfitPxRaw === 'string' ? parseFloat(takeProfitPxRaw) : (takeProfitPxRaw ?? undefined);
        const stopLossPx = typeof stopLossPxRaw === 'string' ? parseFloat(stopLossPxRaw) : (stopLossPxRaw ?? undefined);
        
        // 获取开仓/平仓价格和时间
        const avgPx = referenceLines.avgPx;
        const closePx = referenceLines.closePx;
        const cTime = referenceLines.cTime;
        const uTime = referenceLines.uTime;
        
        // 没有止盈止损配置且没有开仓/平仓配置时清空画布
        if (takeProfitPx === undefined && stopLossPx === undefined && 
            (avgPx === undefined || closePx === undefined || cTime === undefined || uTime === undefined)) {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            return;
        }
        
        // 获取 DPR 缩放因子（必须先获取，因为在 priceToY 中使用）
        const dpr = window.devicePixelRatio || 1;

        // 获取价格坐标转换函数
        // 注意：priceToCoordinate 返回逻辑像素，需要乘以 DPR 才能在 canvas 上正确绘制
        const priceToY = (price: number): number | null => {
            const coord = series.priceToCoordinate(price);
            if (coord === null) return null;
            return coord * dpr;  // 转换为 DPR 缩放后的实际像素
        };

        // 获取时间坐标转换函数
        // 注意：图表数据使用秒级时间戳，所以 timeToCoordinate 也需要秒级时间戳
        const timeToX = (timeMs: number): number | null => {
            // 将毫秒时间戳转换为秒级时间戳
            const timeSec = Math.floor(timeMs / 1000);
            const coord = chart.timeScale().timeToCoordinate(timeSec as any);
            if (coord === null) return null;
            return coord * dpr;
        };

        // 在K线数据中找到最接近目标时间的bar时间（用于时间帧对齐）
        const findClosestBarTime = (targetTime: number): number | null => {
            if (!data || data.length === 0) return null;
            // targetTime 是毫秒时间戳，data[i].time 也是毫秒时间戳
            let closest = data[0].time;
            let minDiff = Math.abs(data[0].time - targetTime);
            for (let i = 1; i < data.length; i++) {
                const diff = Math.abs(data[i].time - targetTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = data[i].time;
                }
            }
            return closest;
        };

        // 使用 canvas 的实际像素尺寸（已乘以 DPR）
        const canvasWidth = canvas.width;  // 已经是 DPR 缩放后的值
        const canvasHeight = canvas.height;  // 已经是 DPR 缩放后的值

        // 获取主图 pane 的实际高度（因为可能有多个副图，每个 pane 高度不同）
        // chart.panes()[0] 是主图（K线 pane），使用 getHeight() 获取其实际像素高度
        const panes = chart.panes();
        const mainPaneHeight = panes.length > 0 ? panes[0].getHeight() * dpr : canvasHeight;
        
        // 清空画布
        ctx.clearRect(0, 0, canvasWidth, canvasHeight);
        
        const isLong = posSide === 'long';
        
        // 绘制止盈止损区域
        if (isLong) {
            // 做多：止盈在上，止损在下
            if (takeProfitPx !== undefined) {
                const tpY = priceToY(takeProfitPx);
                if (tpY !== null && tpY > 0) {
                    ctx.fillStyle = 'rgba(82, 196, 26, 0.15)';
                    ctx.fillRect(0, 0, canvasWidth, tpY);
                }
            }
            if (stopLossPx !== undefined) {
                const slY = priceToY(stopLossPx);
                if (slY !== null) {
                    ctx.fillStyle = 'rgba(255, 77, 79, 0.15)';
                    ctx.fillRect(0, slY, canvasWidth, mainPaneHeight - slY);
                }
            }
        } else {
            // 做空：止损在上，止盈在下
            if (stopLossPx !== undefined) {
                const slY = priceToY(stopLossPx);
                if (slY !== null && slY > 0) {
                    ctx.fillStyle = 'rgba(255, 77, 79, 0.15)';
                    ctx.fillRect(0, 0, canvasWidth, slY);
                }
            }
            if (takeProfitPx !== undefined) {
                const tpY = priceToY(takeProfitPx);
                if (tpY !== null) {
                    ctx.fillStyle = 'rgba(82, 196, 26, 0.15)';
                    ctx.fillRect(0, tpY, canvasWidth, mainPaneHeight - tpY);
                }
            }
        }
        
        // 绘制开仓/平仓围闭区域（历史仓位）
        if (avgPx !== undefined && closePx !== undefined && cTime !== undefined && uTime !== undefined) {
            const openY = priceToY(avgPx);
            const closeY = priceToY(closePx);
            
            // 在K线数据中找到最接近开仓/平仓时间的bar时间
            const openBarTime = findClosestBarTime(cTime);
            const closeBarTime = findClosestBarTime(uTime);
            
            // 将bar时间转换为X坐标
            const openX = openBarTime !== null ? timeToX(openBarTime) : null;
            const closeX = closeBarTime !== null ? timeToX(closeBarTime) : null;
            
            if (openY !== null && closeY !== null && openX !== null && closeX !== null) {
                // 计算矩形位置和尺寸
                const rectX = Math.min(openX, closeX);
                const rectY = Math.min(openY, closeY);
                const rectWidth = Math.abs(closeX - openX);
                const rectHeight = Math.abs(closeY - openY);
                
                // 判断盈利或亏损
                const isProfit = isLong ? closePx > avgPx : closePx < avgPx;
                
                // 设置颜色（盈利绿色，亏损红色）
                ctx.fillStyle = isProfit ? 'rgba(82, 196, 26, 0.15)' : 'rgba(255, 77, 79, 0.15)';
                
                // 绘制矩形区域
                ctx.fillRect(rectX, rectY, rectWidth, rectHeight);
                
                // 绘制边框
                ctx.strokeStyle = isProfit ? 'rgba(82, 196, 26, 0.5)' : 'rgba(255, 77, 79, 0.5)';
                ctx.lineWidth = 1 * dpr;
                ctx.strokeRect(rectX, rectY, rectWidth, rectHeight);
            }
        }
    }, [referenceLines, data]);

    // 监听图表变化并重绘止盈止损背景
    useEffect(() => {
        if (!chartReady || !referenceLines) return;

        // 增加延迟确保 lightweight-charts 完成 panes 更新后再获取高度
        const timer = setTimeout(() => {
            drawTPSLBackground();
        }, 50);

        return () => clearTimeout(timer);
    }, [chartReady, referenceLines, indicators, visibleIndicators, drawTPSLBackground]);

    // 监听图表可见范围变化（滑动K线时重新绘制开平仓区域）
    useEffect(() => {
        if (!chartReady || !chartRef.current) return;

        const timeScale = chartRef.current.timeScale();
        const handler = () => {
            drawTPSLBackground();
        };

        timeScale.subscribeVisibleTimeRangeChange(handler);

        return () => {
            timeScale.unsubscribeVisibleTimeRangeChange(handler);
        };
    }, [chartReady, drawTPSLBackground]);

    useEffect(() => {
        if (!containerRef.current) return;
        const el = containerRef.current;
        const ro = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (entry) {
                const {width, height} = entry.contentRect;
                if (width > 0 && height > 0) {
                    setContainerSize({width: Math.floor(width), height: Math.floor(height)});
                }
            }
        });
        ro.observe(el);
        const rect = el.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
            setContainerSize({width: Math.floor(rect.width), height: Math.floor(rect.height)});
        }
        return () => ro.disconnect();
    }, []);

    // 当 indicators 变化时，重建查找表和颜色索引表
    useEffect(() => {
        // 重建颜色索引：同类型按周期升序排列，分配索引
        const colorIndexByKey: Record<string, number> = {};
        if (indicators) {
            const keysByBaseType: Record<string, string[]> = {};
            Object.keys(indicators).forEach(k => {
                const bt = k.split('_')[0];
                if (!keysByBaseType[bt]) keysByBaseType[bt] = [];
                keysByBaseType[bt].push(k);
            });
            Object.values(keysByBaseType).forEach(keys => {
                keys.sort((a, b) => {
                    const pa = parseInt(a.replace(/^[A-Z]+_[a-z]+_/i, ''));
                    const pb = parseInt(b.replace(/^[A-Z]+_[a-z]+_/i, ''));
                    return (isNaN(pa) ? 0 : pa) - (isNaN(pb) ? 0 : pb);
                }).forEach((k, idx) => { colorIndexByKey[k] = idx; });
            });
        }
        indicatorColorIndexRef.current = colorIndexByKey;

        // 重建时间查找表：时间戳(秒) → { key → 数据点 }
        const lookup: Record<number, Record<string, any>> = {};
        if (indicators) {
            Object.entries(indicators).forEach(([key, values]) => {
                if (!values || !Array.isArray(values)) return;
                (values as any[]).forEach(v => {
                    const ts = toUTCTimestamp(v.time);
                    if (!lookup[ts]) lookup[ts] = {};
                    lookup[ts][key] = v;
                });
            });
        }
        indicatorDataLookupRef.current = lookup;
    }, [indicators]);

    const initChart = useCallback(() => {
        // 防抖：如果已经在初始化中，跳过
        if (initChartInProgressRef.current) return;

        if (!containerRef.current) return;

        const container = containerRef.current;
        // 直接从 DOM 获取实际宽度，不再依赖 containerSize state
        const chartWidth = width || container.clientWidth;
        if (chartWidth === 0) return;

        // 设置防抖标志
        initChartInProgressRef.current = true;

        // 关键修复：如果已有图表，先移除它（避免创建多个实例）
        if (chartRef.current) {
            try {
                chartRef.current.remove();
            } catch (e) {
                console.debug('chart.remove error:', e);
            }
            chartRef.current = null;
            candlestickSeriesRef.current = null;
            volumeSeriesRef.current = null;
            indicatorSeriesRef.current = {};
            paneIndexMapRef.current = {};  // 重置 pane 索引映射，避免残留导致空白副图
        }

        // 计算最小K线间距，限制可视范围内的K线数量上限
        // minBarSpacing = 图表宽度 / 最大可视K线数量
        let minBarSpacing = 0.5; // 默认值
        if (maxVisibleBars && maxVisibleBars > 0) {
            minBarSpacing = chartWidth / maxVisibleBars;
        }

        const chart = createChart(container, {
            width: chartWidth,
            height: chartHeight,
            layout: {
                background: {type: ColorType.Solid, color: '#1f1f1f'},
                textColor: '#999999',
                attributionLogo: false,
            },
            grid: {
                vertLines: {color: '#2a2a2a'},
                horzLines: {color: '#2a2a2a'},
            },
            crosshair: {
                mode: 1,
            },
            timeScale: {
                timeVisible: true,
                secondsVisible: false,
                borderVisible: false,
                barSpacing: minBarSpacing,
                minBarSpacing: minBarSpacing,
                fixRightEdge: lockRightEdge,
            },
            rightPriceScale: {
                borderVisible: false,
            },
            localization: {
                timeFormatter: (time: number) => {
                    const date = new Date(time * 1000);
                    const year = String(date.getFullYear()).slice(-2);
                    const month = String(date.getMonth() + 1).padStart(2, '0');
                    const day = String(date.getDate()).padStart(2, '0');
                    const hours = String(date.getHours()).padStart(2, '0');
                    const minutes = String(date.getMinutes()).padStart(2, '0');
                    
                    // 根据时间帧格式化
                    if (timeFrame === '1m' || timeFrame === '5m') {
                        return `${year}-${month}-${day} ${hours}:${minutes}`;
                    } else if (timeFrame === '1h' || timeFrame === '4h') {
                        return `${year}-${month}-${day} ${hours}`;
                    } else {
                        return `${year}-${month}-${day}`;
                    }
                },
            },
            handleScroll: true,
            handleScale: true,
        });

        const candlestickSeries = chart.addSeries(CandlestickSeries, {
            upColor: '#52c41a',
            downColor: '#ff4d4f',
            borderUpColor: '#52c41a',
            borderDownColor: '#ff4d4f',
            wickUpColor: '#52c41a',
            wickDownColor: '#ff4d4f',
            priceLineVisible: false, // 隐藏最后价格线（避免显示红色/绿色水平线）
            lastValueVisible: false, // 隐藏最后价格标签
        });

        const volumeSeries = chart.addSeries(HistogramSeries, {
            priceFormat: {type: 'volume'},
            priceScaleId: '',
            // 移除 paneIndex 参数，让成交量与K线融合到同一 pane（主图）
        });

        volumeSeries.priceScale().applyOptions({
            scaleMargins: {
                top: 0.8,   // K线占上方80%，成交量显示在下方20%
                bottom: 0,
            },
        });

        chartRef.current = chart;
        candlestickSeriesRef.current = candlestickSeries;
        volumeSeriesRef.current = volumeSeries;

        chart.subscribeCrosshairMove((param) => {
                // 防护：如果正在更新图表（removeSeries/addSeries 操作期间），跳过回调
                // 防止无限循环：removeSeries 触发回调 -> setState -> 重新渲染 -> useEffect -> removeSeries
                if (isUpdatingRef.current) return;
                
                if (param.time && param.point) {
                    const timeSeconds = param.time as number;
                    const candleData = param.seriesData.get(candlestickSeries);
                    const volumeData = param.seriesData.get(volumeSeries);
                    if (candleData && 'open' in candleData) {
                        const barData = candleData as { open: number; high: number; low: number; close: number };
                        setTooltipData({
                            time: timeSeconds * 1000,
                            open: barData.open,
                            high: barData.high,
                            low: barData.low,
                            close: barData.close,
                            volume: (volumeData && 'value' in volumeData ? volumeData.value : 0),
                        });
                        setTooltipPosition({ x: param.point.x, y: param.point.y });
                        // 查出该时刻的指标数据
                        setTooltipIndicators(indicatorDataLookupRef.current[timeSeconds] || null);
                        if (onTooltip) {
                            onTooltip({
                                time: timeSeconds * 1000,
                                open: barData.open,
                                high: barData.high,
                                low: barData.low,
                                close: barData.close,
                                volume: 0
                            });
                        }
                    } else {
                        setTooltipData(null);
                        setTooltipPosition(null);
                        setTooltipIndicators(null);
                    }
                } else {
                    // 鼠标离开图表时清空 tooltip
                    setTooltipData(null);
                    setTooltipPosition(null);
                    setTooltipIndicators(null);
                }
            });

        const handleResize = () => {
            if (container && chartRef.current && chartReady) {
                chartRef.current.applyOptions({
                    width: container.clientWidth,
                });
            }
        };

        const resizeObserver = new ResizeObserver(handleResize);
        resizeObserver.observe(container);

        // 初始化完成后清除防抖标志
        initChartInProgressRef.current = false;
        setChartReady(true);

        return () => {
            resizeObserver.disconnect();
            chart.remove();
            chartRef.current = null;
            candlestickSeriesRef.current = null;
            volumeSeriesRef.current = null;
            indicatorSeriesRef.current = {};
        };
    }, [chartHeight, width, timeFrame]);

    useEffect(() => {
        initChart();
    }, [initChart]);

    // 当 containerSize 从无效变为有效时，确保图表被初始化
    useEffect(() => {
        if (containerSize.width > 0 && containerSize.height > 0 && !chartReady && !initChartInProgressRef.current) {
            initChart();
        }
    }, [containerSize, chartReady, initChart]);

    // 当采样率变化时，重置视图使可视范围展示对应数量的K线
    useEffect(() => {
        if (!chartReady || !chartRef.current) return;
        
        const chartWidth = width || containerSize.width;
        if (chartWidth === 0) return;
        
        if (maxVisibleBars && maxVisibleBars > 0) {
            // barSpacing = 每根K线占用的像素宽度
            const barSpacing = chartWidth / maxVisibleBars;
            
            chartRef.current.timeScale().applyOptions({
                barSpacing: barSpacing,
            });
            
            // 滚动到最新位置
            chartRef.current.timeScale().scrollToRealTime();
        }
    }, [maxVisibleBars, chartReady, width, containerSize]);

    useEffect(() => {
        if (!chartReady || !candlestickSeriesRef.current || !volumeSeriesRef.current || data.length === 0) return;

        const sortedData = [...data].sort((a, b) => a.time - b.time);

        const candleData = sortedData.map(d => ({
            time: toUTCTimestamp(d.time),
            open: d.open,
            high: d.high,
            low: d.low,
            close: d.close,
        }));

        const volumeData = sortedData.map(d => ({
            time: toUTCTimestamp(d.time),
            value: d.volume || 0,
            color: d.close >= d.open ? 'rgba(82, 196, 26, 0.5)' : 'rgba(255, 77, 79, 0.5)',
        }));

        candlestickSeriesRef.current.setData(candleData);
        volumeSeriesRef.current.setData(volumeData);
        // 滚动逻辑移到 indicators useEffect 中统一处理
    }, [data, chartReady]);

    useEffect(() => {
        if (!chartReady) return;

        // 设置更新标志，防止 subscribeCrosshairMove 回调触发 setState 导致无限循环
        isUpdatingRef.current = true;

        // 只清理 series，让 lightweight-charts 自动管理 pane
        Object.keys(indicatorSeriesRef.current).forEach(k => {
            const series = indicatorSeriesRef.current[k];
            // 只有当 series 存在且图表存在时才尝试移除
            if (series != null && chartRef.current) {
                try {
                    chartRef.current.removeSeries(series);
                } catch (e) {
                    // 忽略移除失败的错误（series 可能已经失效）
                    console.debug('removeSeries error:', e);
                }
            }
        });
        indicatorSeriesRef.current = {};

        if (!indicators || !candlestickSeriesRef.current) return;

        // 使用持久化的 paneIndexMapRef 分配 paneIndex
        // 从 visibleIndicators 提取需要显示的 oscillator 类型
        // 同时验证 indicators 中是否有对应的实际数据，避免创建空白副图
        const visibleOscillatorTypes = new Set<string>();
        Object.keys(visibleIndicators).forEach(key => {
            if (visibleIndicators[key]) {
                const baseType = key.split('_')[0];
                if (['MACD', 'KDJ', 'RSI', 'CCI', 'OBV', 'ADX'].includes(baseType)) {
                    // 检查 indicators 中是否有该类型的数据
                    const hasData = Object.keys(indicators || {}).some(indicatorKey => {
                        const indicatorBaseType = indicatorKey.split('_')[0];
                        return indicatorBaseType === baseType;
                    });
                    if (hasData) {
                        visibleOscillatorTypes.add(baseType);
                    }
                }
            }
        });

        // 清理已取消的指标 - 先收集需要移除的pane索引
        const panesToRemove: number[] = [];
        Object.keys(paneIndexMapRef.current).forEach(type => {
            if (!visibleOscillatorTypes.has(type)) {
                const paneIndex = paneIndexMapRef.current[type];
                if (paneIndex !== undefined && paneIndex > 0) {
                    panesToRemove.push(paneIndex);
                }
                delete paneIndexMapRef.current[type];
            }
        });

        // 移除空的pane（从后往前移除，避免索引变化影响）
        if (chartRef.current && panesToRemove.length > 0) {
            // 去重并倒序排序
            const uniquePanes = [...new Set(panesToRemove)].sort((a, b) => b - a);
            uniquePanes.forEach(paneIndex => {
                try {
                    // 不能移除主图(paneIndex=0)
                    if (paneIndex > 0 && paneIndex < chartRef.current!.panes().length) {
                        chartRef.current!.removePane(paneIndex);
                    }
                } catch (e) {
                    console.debug('removePane error:', e);
                }
            });
            
            // 重要：移除pane后，需要清除所有振荡指标的series引用
            // 因为这些series可能已经失效（被一起移除了）
            Object.keys(indicatorSeriesRef.current).forEach(key => {
                const baseType = key.split('_')[0];
                if (['MACD', 'KDJ', 'RSI', 'CCI', 'OBV', 'ADX'].includes(baseType)) {
                    delete indicatorSeriesRef.current[key];
                }
            });
        }

        // 重新压缩pane索引（因为removePane后索引会重新排列）
        const remainingTypes = Object.keys(paneIndexMapRef.current);
        const newPaneIndexMap: Record<string, number> = {};
        remainingTypes.forEach((type, index) => {
            newPaneIndexMap[type] = index + 1;  // 副图从1开始
        });
        paneIndexMapRef.current = newPaneIndexMap;

        // 为新指标分配 paneIndex
        visibleOscillatorTypes.forEach(type => {
            if (paneIndexMapRef.current[type] === undefined) {
                paneIndexMapRef.current[type] = Object.keys(paneIndexMapRef.current).length + 1;
            }
        });

        // 确保需要的 pane 存在
        if (chartRef.current) {
            const currentPaneCount = chartRef.current.panes().length;
            const neededPaneCount = Math.max(...Object.values(paneIndexMapRef.current), 0) + 1;
            for (let i = currentPaneCount; i < neededPaneCount; i++) {
                chartRef.current.addPane();
            }
        }

        // 预处理：按指标类型分组，对同类型的 key 按周期数字升序排列，确定各 key 的颜色索引
        const colorIndexByKey: Record<string, number> = {};
        const keysByBaseType: Record<string, string[]> = {};
        Object.keys(indicators).forEach(k => {
            const bt = k.split('_')[0];
            if (!keysByBaseType[bt]) keysByBaseType[bt] = [];
            keysByBaseType[bt].push(k);
        });
        Object.values(keysByBaseType).forEach(keys => {
            // 从 key（如 EMA_ema_12）中解析周期数字，按升序排列后赋予颜色索引
            keys.sort((a, b) => {
                const pa = parseInt(a.replace(/^[A-Z]+_[a-z]+_/i, ''));
                const pb = parseInt(b.replace(/^[A-Z]+_[a-z]+_/i, ''));
                return (isNaN(pa) ? 0 : pa) - (isNaN(pb) ? 0 : pb);
            }).forEach((k, idx) => {
                colorIndexByKey[k] = idx;
            });
        });

        Object.entries(indicators).forEach(([key, values]) => {
            if (!values || !Array.isArray(values) || values.length === 0) return;

            const baseType = key.split('_')[0];
            const periodWithPrefix = key.substring(baseType.length + 1);
            const period = periodWithPrefix.replace(/^(ema|sma|wma|rsi|boll|macd|kdj|cci|atr|obv|adx)_/i, '').replace(/,/g, '_');
            const visibleKey = `${baseType}_${period}`;
            const isVisible = visibleIndicators[visibleKey] !== undefined
                ? visibleIndicators[visibleKey]
                : Object.keys(visibleIndicators).some(k => k.startsWith(baseType) && visibleIndicators[k]);
            if (!isVisible) return;

            const isOscillator = ['MACD', 'KDJ', 'RSI', 'CCI', 'OBV', 'ADX'].includes(baseType);
            let paneIndex = 0; // default main pane

            if (isOscillator) {
                paneIndex = paneIndexMapRef.current[baseType] ?? 0;
            }

            if (baseType === 'MACD') {
                // MACD三线：DIF橙红、DEA深紫、柱状图绿/红
                if (!indicatorSeriesRef.current[`${key}_DIF`]) {
                    indicatorSeriesRef.current[`${key}_DIF`] = chartRef.current?.addSeries(LineSeries, {
                        color: '#fa541c', lineWidth: 2, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_DIF`]?.setData(
                    values.filter(v => v.diff !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.diff as number }))
                );

                if (!indicatorSeriesRef.current[`${key}_DEA`]) {
                    indicatorSeriesRef.current[`${key}_DEA`] = chartRef.current?.addSeries(LineSeries, {
                        color: '#722ed1', lineWidth: 2, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_DEA`]?.setData(
                    values.filter(v => v.signal !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.signal as number }))
                );

                if (!indicatorSeriesRef.current[key]) {
                    indicatorSeriesRef.current[key] = chartRef.current?.addSeries(HistogramSeries, { priceFormat: { type: 'volume' } }, paneIndex) || null;
                }
                indicatorSeriesRef.current[key]?.setData(
                    values.filter(v => v.histogram !== undefined).map(v => ({
                        time: toUTCTimestamp(v.time),
                        value: v.histogram as number,
                        color: (v.histogram as number) >= 0 ? 'rgba(82, 196, 26, 0.3)' : 'rgba(255, 77, 79, 0.3)',
                    }))
                );
                return;
            }

            if (baseType === 'BOLL') {
                // BOLL三轨使用不同颜色：上轨红、中轨黄、下轨绿
                if (!indicatorSeriesRef.current[`${key}_upper`]) {
                    indicatorSeriesRef.current[`${key}_upper`] = chartRef.current?.addSeries(LineSeries, {
                        color: '#f5222d', lineWidth: 1, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_upper`]?.setData(
                    values.filter(v => v.upper !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.upper as number }))
                );

                if (!indicatorSeriesRef.current[`${key}_middle`]) {
                    indicatorSeriesRef.current[`${key}_middle`] = chartRef.current?.addSeries(LineSeries, {
                        color: '#faad14', lineWidth: 1, lineStyle: 2, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_middle`]?.setData(
                    values.filter(v => v.middle !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.middle as number }))
                );

                if (!indicatorSeriesRef.current[`${key}_lower`]) {
                    indicatorSeriesRef.current[`${key}_lower`] = chartRef.current?.addSeries(LineSeries, {
                        color: '#ff00ff', lineWidth: 1, priceLineVisible: false, lastValueVisible: false, // 改成洋红色用于调试
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_lower`]?.setData(
                    values.filter(v => v.lower !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.lower as number }))
                );
                return;
            }

            if (baseType === 'KDJ') {
                // 每个 KDJ 周期使用独立色系，K/D/J 在同色系内明暗区分
                const kdjColorSet = KDJ_COLOR_SETS[colorIndexByKey[key] % KDJ_COLOR_SETS.length] || KDJ_COLOR_SETS[0];
                if (!indicatorSeriesRef.current[`${key}_K`]) {
                    indicatorSeriesRef.current[`${key}_K`] = chartRef.current?.addSeries(LineSeries, {
                        color: kdjColorSet.K, lineWidth: 2, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_K`]?.setData(
                    values.filter(v => v.k !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.k as number }))
                );

                if (!indicatorSeriesRef.current[`${key}_D`]) {
                    indicatorSeriesRef.current[`${key}_D`] = chartRef.current?.addSeries(LineSeries, {
                        color: kdjColorSet.D, lineWidth: 2, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_D`]?.setData(
                    values.filter(v => v.d !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.d as number }))
                );

                if (!indicatorSeriesRef.current[`${key}_J`]) {
                    indicatorSeriesRef.current[`${key}_J`] = chartRef.current?.addSeries(LineSeries, {
                        color: kdjColorSet.J, lineWidth: 2, priceLineVisible: false, lastValueVisible: false,
                    }, paneIndex) || null;
                }
                indicatorSeriesRef.current[`${key}_J`]?.setData(
                    values.filter(v => v.j !== undefined).map(v => ({ time: toUTCTimestamp(v.time), value: v.j as number }))
                );
                return;
            }

            // EMA/SMA/RSI/WMA/ATR：按周期索引从颜色数组中选色，不同周期颜色不同
            const colorIdx = colorIndexByKey[key] ?? 0;
            const multiColors = MULTI_PERIOD_COLORS[baseType];
            const lineColor = multiColors
                ? multiColors[colorIdx % multiColors.length]
                : (['#1890ff', '#00d2d3', '#52c41a', '#fa8c16'][colorIdx % 4]);

            if (!indicatorSeriesRef.current[key]) {
                const series = chartRef.current?.addSeries(LineSeries, {
                    color: lineColor,
                    lineWidth: 2,
                    priceLineVisible: false,
                    lastValueVisible: false, // 改为 false 禁用最后值标签线
                }, paneIndex);

                if (series) {
                    indicatorSeriesRef.current[key] = series;
                }
            }

            const series = indicatorSeriesRef.current[key];
            if (series) {
                series.setData(
                    values.filter(v => v.value !== undefined).map(v => ({
                        time: toUTCTimestamp(v.time),
                        value: v.value as number,
                    }))
                );
            }
        });

        // 滚动到最新K线
        chartRef.current?.timeScale().scrollToRealTime();

        // 重置更新标志
        requestAnimationFrame(() => {
            isUpdatingRef.current = false;
        });
    }, [indicators, visibleIndicators, chartReady, data]);

    useEffect(() => {
        if (!chartReady || !candlestickSeriesRef.current) return;

        // 设置更新标志，防止 subscribeCrosshairMove 回调触发 setState 导致无限循环
        isUpdatingRef.current = true;

        const series = candlestickSeriesRef.current;

        // 如果强制清除，则尝试移除所有可能的价格线
        if (debugForceClearPriceLines) {
            const allLines = [
                ...priceLineRefs.current,
                markPriceLineRef.current ? [markPriceLineRef.current] : [],
            ].flat();
            allLines.forEach((line) => {
                try {
                    series.removePriceLine(line);
                } catch (e) {
                    // 忽略移除失败的错误
                }
            });
            priceLineRefs.current = [];
            markPriceLineRef.current = null;
        } else {
            // 正常清理逻辑
            const allLines = [
                ...priceLineRefs.current,
                markPriceLineRef.current ? [markPriceLineRef.current] : [],
            ].flat();

            allLines.forEach((line) => {
                try {
                    series.removePriceLine(line);
                } catch (e) {
                    // 忽略移除失败的错误
                }
            });
            priceLineRefs.current = [];
            markPriceLineRef.current = null;
        }
        // 清理开仓价水平线（有时间范围限制的 LineSeries）
        if (entryLineSeriesRef.current) {
            try {
                chartRef.current?.removeSeries(entryLineSeriesRef.current);
            } catch (e) {
                // 忽略移除失败的错误
            }
            entryLineSeriesRef.current = null;
        }
        // 清理支撑阻力线
        supportResistanceLineRefs.current.forEach(lineSeries => {
            try {
                chartRef.current?.removeSeries(lineSeries);
            } catch (e) {
                // 忽略移除失败的错误
            }
        });
        supportResistanceLineRefs.current = [];
        
        // 清理支撑阻力线价格标签
        supportResistancePriceLineRefs.current.forEach(line => {
            try {
                series.removePriceLine(line);
            } catch (e) {
                // 忽略移除失败的错误
            }
        });
        supportResistancePriceLineRefs.current = [];

        const entries = Object.entries(referenceLines || {}).filter(([, v]) => v !== undefined);

        const priceSeen = new Set<number>();
        const linesToCreate: {price: number; color: string; key: string}[] = [];

        entries.forEach(([key, value]) => {
            if (value === undefined || value === null) return;
            // avgPx 和 closePx 需要特殊处理（时间范围限制），跳过此处
            if (key === 'avgPx' || key === 'cTime' || key === 'uTime') return;
            
            // 当 showEntryLine=false 时，跳过 closePx 的水平线（历史持仓只显示标签）
            if (key === 'closePx' && !showEntryLine) return;

            // 将价格转换为数字类型
            const numericPrice = typeof value === 'string' ? parseFloat(value) : Number(value);
            if (!Number.isFinite(numericPrice)) return;
            if (priceSeen.has(numericPrice)) return;

            const colors: Record<string, string> = {
                avgPx: '#1890ff',        // 蓝色
                closePx: '#fa8c16',      // 橙色
                takeProfitPx: '#52c41a', // 绿色（止盈）
                stopLossPx: '#ff4d4f',   // 红色（止损）
                liquidationPx: '#722ed1',// 紫色（强平价）
            };

            priceSeen.add(numericPrice);
            linesToCreate.push({
                price: numericPrice,
                color: colors[key] || '#fa8c16',
                key
            });
        });

        // 处理支撑线和阻力线（使用 LineSeries 绘制，对齐x轴）
        // 获取图表最后一个 bar 的时间
        const lastBarTime = data.length > 0 ? data[data.length - 1].time : 0;
        const firstBarTime = data.length > 0 ? data[0].time : 0;

        // 调试日志
        console.log('[支撑/阻力线] K线数据时间范围:', {
            firstBarTime: firstBarTime,
            firstBarDate: new Date(firstBarTime).toISOString(),
            lastBarTime: lastBarTime,
            lastBarDate: new Date(lastBarTime).toISOString(),
            barsCount: data.length
        });

        // 在 K 线数据中找到最接近目标时间的 bar 的时间戳
        // 注意：K线数据的 time 字段和 targetTime 都是13位毫秒时间戳
        const findClosestBarTime = (targetTime: number): number => {
            if (data.length === 0) return targetTime;
            let closest = data[0].time;
            let minDiff = Math.abs(data[0].time - targetTime);
            for (let i = 1; i < data.length; i++) {
                const diff = Math.abs(data[i].time - targetTime);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = data[i].time;
                }
            }
            return closest;
        };

        // 绘制支撑线（蓝色）
        const supportLines = referenceLines?.supportLines;
        if (supportLines && Array.isArray(supportLines) && chartRef.current && data.length > 0) {
            console.log('[支撑线] 待绘制数量:', supportLines.length);
            supportLines.forEach((line, index) => {
                const lineData = typeof line === 'object' ? line : { price: line, startTime: undefined, endTime: undefined };
                const numericPrice = typeof lineData.price === 'string' ? parseFloat(lineData.price) : Number(lineData.price);
                if (!Number.isFinite(numericPrice)) return;

                // 检查时间范围是否在K线数据内，如果不在则跳过
                if (lineData.startTime && lineData.startTime > lastBarTime) {
                    console.log(`[支撑线 ${index}] 跳过: startTime 晚于 K线数据 (${new Date(lineData.startTime).toISOString()} > ${new Date(lastBarTime).toISOString()})`);
                    return;
                }
                if (lineData.endTime && lineData.endTime < firstBarTime) {
                    console.log(`[支撑线 ${index}] 跳过: endTime 早于 K线数据 (${new Date(lineData.endTime).toISOString()} < ${new Date(firstBarTime).toISOString()})`);
                    return;
                }

                let startTime = lineData.startTime ? findClosestBarTime(lineData.startTime) : firstBarTime;
                // 使用 endTime 或默认到 lastBarTime
                let endTime = lineData.endTime ? findClosestBarTime(lineData.endTime) : lastBarTime;

                // 确保 startTime <= endTime
                if (startTime > endTime) {
                    [startTime, endTime] = [endTime, startTime];
                }

                // 调试日志
                console.log(`[支撑线 ${index}]`, {
                    price: numericPrice,
                    startTime: lineData.startTime,
                    startTimeDate: lineData.startTime ? new Date(lineData.startTime).toISOString() : 'undefined',
                    matchedStartTime: startTime,
                    matchedStartDate: new Date(startTime).toISOString(),
                    endTime: lineData.endTime,
                    endTimeDate: lineData.endTime ? new Date(lineData.endTime).toISOString() : 'undefined',
                    matchedEndTime: endTime,
                    matchedEndDate: new Date(endTime).toISOString()
                });

                const supportLineSeries = chartRef.current!.addSeries(LineSeries, {

                                    color: '#1890ff',  // 支撑线蓝色

                                    lineWidth: 1,

                                    lineStyle: 2,  // 虚线

                                    priceLineVisible: false,  // 不显示价格线标签（避免重复）

                                    lastValueVisible: false,  // 不显示末端标签（避免重复）

                                });

                if (startTime === endTime) {
                    supportLineSeries.setData([
                        { time: toUTCTimestamp(startTime), value: numericPrice },
                    ]);
                } else {
                    supportLineSeries.setData([
                        { time: toUTCTimestamp(startTime), value: numericPrice },
                        { time: toUTCTimestamp(endTime), value: numericPrice },
                    ]);
                }

                supportResistanceLineRefs.current.push(supportLineSeries);
                
                // 创建 Y 轴标签显示 S1/S2/S3
                if (lineData.label && candlestickSeriesRef.current) {
                    const labelPriceLine = candlestickSeriesRef.current.createPriceLine({
                        price: numericPrice,
                        color: '#1890ff',
                        lineVisible: false,
                        lineStyle: 2,
                        axisLabelVisible: true,
                        title: lineData.label,  // S1, S2, S3
                    });
                    if (labelPriceLine) {
                        supportResistancePriceLineRefs.current.push(labelPriceLine);
                    }
                }
            });
        }

        // 绘制阻力线（橙色）
        const resistanceLines = referenceLines?.resistanceLines;
        if (resistanceLines && Array.isArray(resistanceLines) && chartRef.current && data.length > 0) {
            console.log('[阻力线] 待绘制数量:', resistanceLines.length);
            resistanceLines.forEach((line, index) => {
                const lineData = typeof line === 'object' ? line : { price: line, startTime: undefined, endTime: undefined };
                const numericPrice = typeof lineData.price === 'string' ? parseFloat(lineData.price) : Number(lineData.price);
                if (!Number.isFinite(numericPrice)) return;

                // 检查时间范围是否在K线数据内，如果不在则跳过
                if (lineData.startTime && lineData.startTime > lastBarTime) {
                    console.log(`[阻力线 ${index}] 跳过: startTime 晚于 K线数据 (${new Date(lineData.startTime).toISOString()} > ${new Date(lastBarTime).toISOString()})`);
                    return;
                }
                if (lineData.endTime && lineData.endTime < firstBarTime) {
                    console.log(`[阻力线 ${index}] 跳过: endTime 早于 K线数据 (${new Date(lineData.endTime).toISOString()} < ${new Date(firstBarTime).toISOString()})`);
                    return;
                }

                let startTime = lineData.startTime ? findClosestBarTime(lineData.startTime) : firstBarTime;
                // 使用 endTime 或默认到 lastBarTime
                let endTime = lineData.endTime ? findClosestBarTime(lineData.endTime) : lastBarTime;

                // 确保 startTime <= endTime
                if (startTime > endTime) {
                    [startTime, endTime] = [endTime, startTime];
                }

                // 调试日志
                console.log(`[阻力线 ${index}]`, {
                    price: numericPrice,
                    startTime: lineData.startTime,
                    startTimeDate: lineData.startTime ? new Date(lineData.startTime).toISOString() : 'undefined',
                    matchedStartTime: startTime,
                    matchedStartDate: new Date(startTime).toISOString(),
                    endTime: lineData.endTime,
                    endTimeDate: lineData.endTime ? new Date(lineData.endTime).toISOString() : 'undefined',
                    matchedEndTime: endTime,
                    matchedEndDate: new Date(endTime).toISOString()
                });

                const resistanceLineSeries = chartRef.current!.addSeries(LineSeries, {
                    color: '#fa8c16',  // 阻力线橙色
                    lineWidth: 1,
                    lineStyle: 2,  // 虚线
                    priceLineVisible: false,  // 不显示价格线标签（避免重复）
                    lastValueVisible: false,  // 不显示末端标签（避免重复）
                });

                if (startTime === endTime) {
                    resistanceLineSeries.setData([
                        { time: toUTCTimestamp(startTime), value: numericPrice },
                    ]);
                } else {
                    resistanceLineSeries.setData([
                        { time: toUTCTimestamp(startTime), value: numericPrice },
                        { time: toUTCTimestamp(endTime), value: numericPrice },
                    ]);
                }

                supportResistanceLineRefs.current.push(resistanceLineSeries);
                
                // 创建 Y 轴标签显示 R1/R2/R3
                if (lineData.label && candlestickSeriesRef.current) {
                    const labelPriceLine = candlestickSeriesRef.current.createPriceLine({
                        price: numericPrice,
                        color: '#fa8c16',
                        lineVisible: false,
                        lineStyle: 2,
                        axisLabelVisible: true,
                        title: lineData.label,  // R1, R2, R3
                    });
                    if (labelPriceLine) {
                        supportResistancePriceLineRefs.current.push(labelPriceLine);
                    }
                }
            });
        }

        const markPriceValue = markPrice;
        if (markPriceValue !== undefined && markPriceValue !== null && !priceSeen.has(markPriceValue)) {
            const numericMarkPrice = typeof markPriceValue === 'string' ? parseFloat(markPriceValue) : Number(markPriceValue);
            if (Number.isFinite(numericMarkPrice)) {
                linesToCreate.push({
                    price: numericMarkPrice,
                    color: markPriceColor || '#fa8c16',
                    key: 'markPrice'
                });
            }
                }
        
                // 创建有时间范围限制的开仓价水平线（从开仓时间开始，不是横贯全图）
        // 注意：此逻辑必须在 "if (linesToCreate.length === 0) return;" 之前执行
        const avgPx = referenceLines?.avgPx;
        const closePx = referenceLines?.closePx;
        const cTime = referenceLines?.cTime;

        if (avgPx !== undefined && cTime !== undefined && chartRef.current && data.length > 0) {
            // 获取图表最后一个 bar 的时间
            const lastBarTime = data[data.length - 1].time;

            // 在 K 线数据中找到最接近 cTime 的 bar 的时间戳
            // 因为 K 线数据的时间戳是对齐到时间帧的，cTime 需要找到对应的 bar
            const findClosestBarTime = (targetTime: number): number => {
                let closest = data[0].time;
                let minDiff = Math.abs(data[0].time - targetTime);
                for (let i = 1; i < data.length; i++) {
                    const diff = Math.abs(data[i].time - targetTime);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closest = data[i].time;
                    }
                }
                return closest;
            };

            const startTime = findClosestBarTime(cTime);

            // 只有当 showEntryLine 为 true 且 showAvgPx 为 true 时才创建 LineSeries 绘制水平线
            if (showEntryLine && showAvgPx) {
                // 创建新的 LineSeries
                const entryLineSeries = chartRef.current.addSeries(LineSeries, {
                    color: '#1890ff',
                    lineWidth: 1,
                    lineStyle: 2,
                    priceLineVisible: false,
                    lastValueVisible: false,
                });

                // 添加数据点：开仓时间到最后一个 bar 时间
                // 注意：如果 startTime 和 lastBarTime 相同，只能设置一个点，否则会报重复时间戳错误
                if (startTime === lastBarTime) {
                    entryLineSeries.setData([
                        { time: toUTCTimestamp(startTime), value: avgPx },
                    ]);
                } else {
                    entryLineSeries.setData([
                        { time: toUTCTimestamp(startTime), value: avgPx },
                        { time: toUTCTimestamp(lastBarTime), value: avgPx },
                    ]);
                }

                entryLineSeriesRef.current = entryLineSeries;
            }
        }

        // 重要：先清空 refs，避免重复添加同一条线
        priceLineRefs.current = [];
        markPriceLineRef.current = null;

        // 创建开仓价格的Y轴标签（在清空后创建，避免被清除）
        // showAvgPx=true 时才显示Y轴标签
        if (avgPx !== undefined && showAvgPx) {
            const entryPriceLine = series.createPriceLine({
                price: avgPx,
                color: '#1890ff',
                lineVisible: false,
                lineStyle: 2,
                axisLabelVisible: true,
                title: '开',
            });
            if (entryPriceLine) {
                priceLineRefs.current.push(entryPriceLine);
            }
        }

        // 创建平仓价格的Y轴标签（历史持仓时showEntryLine=false，只显示标签不显示线）
        // showClosePx=true 时才显示Y轴标签
        if (!showEntryLine && closePx !== undefined && showClosePx) {
            const closePriceLine = series.createPriceLine({
                price: closePx,
                color: '#fa8c16',
                lineVisible: false,
                lineStyle: 2,
                axisLabelVisible: true,
                title: '平',
            });
            if (closePriceLine) {
                priceLineRefs.current.push(closePriceLine);
            }
        }

        if (linesToCreate.length === 0) return;

        linesToCreate.sort((a, b) => a.price - b.price);

        linesToCreate.forEach(({price, color, key}) => {
            const line = series.createPriceLine({
                price,
                color,
                lineWidth: 1,
                lineStyle: 2,
                axisLabelVisible: true,
            });
            if (line) {
                if (key === 'markPrice') {
                    markPriceLineRef.current = line;
                } else {
                    priceLineRefs.current.push(line);
                }
            }
        });

        // 统一计算价格范围，确保所有元素可见（K线 + 技术指标 + 价格线）
        if (chartRef.current && data.length > 0) {
            const allPrices: number[] = [];

            // 1. K线数据的最高/最低价
            data.forEach(d => {
                allPrices.push(d.high, d.low);
            });

            // 2. 技术指标的价格范围（BOLL的upper/lower，EMA等）
            if (indicators) {
                Object.entries(indicators).forEach(([key, values]) => {
                    if (!values || !Array.isArray(values)) return;
                    const baseType = key.split('_')[0];
                    const periodWithPrefix = key.substring(baseType.length + 1);
                    const period = periodWithPrefix.replace(/^(ema|rsi|boll|macd|kdj)_/i, '').replace(/,/g, '_');
                    const visibleKey = `${baseType}_${period}`;
                    const isVisible = visibleIndicators[visibleKey] !== undefined
                        ? visibleIndicators[visibleKey]
                        : Object.keys(visibleIndicators).some(k => k.startsWith(baseType) && visibleIndicators[k]);
                    if (!isVisible) return;

                    // 只收集主图指标的价格（BOLL, EMA, SMA, WMA, ATR等），不收集副图指标（MACD, KDJ, RSI, CCI, OBV, ADX）
                    const isOscillator = ['MACD', 'KDJ', 'RSI', 'CCI', 'OBV', 'ADX'].includes(baseType);
                    if (isOscillator) return;

                    values.forEach((v: any) => {
                        if (v.upper !== undefined) allPrices.push(v.upper);
                        if (v.middle !== undefined) allPrices.push(v.middle);
                        if (v.lower !== undefined) allPrices.push(v.lower);
                        if (v.value !== undefined) allPrices.push(v.value);
                    });
                });
            }

            // 3. 价格线的价格
            linesToCreate.forEach(({price}) => {
                allPrices.push(price);
            });

            // 计算最终范围
            const minPrice = Math.min(...allPrices);
            const maxPrice = Math.max(...allPrices);
            const priceRange = maxPrice - minPrice;
            const margin = priceRange * 0.05; // 5% 边距

            const priceScale = chartRef.current.priceScale('right');
            // 使用正确的 API 设置价格范围
            priceScale.setAutoScale(false);
            priceScale.setVisibleRange({ from: minPrice - margin, to: maxPrice + margin });
        }

        // 使用 requestAnimationFrame 延迟重置标志，确保所有图表操作完成
        requestAnimationFrame(() => {
            isUpdatingRef.current = false;
        });

        return () => {
            isUpdatingRef.current = false;
        };
    }, [referenceLines, markPrice, markPriceColor, chartReady, data, indicators, visibleIndicators, showEntryLine, showAvgPx, showClosePx]);

    // 格式化数字
    const formatNumber = (num: number, decimals: number = 2): string => {
        if (Number.isFinite(num)) {
            return num.toFixed(decimals);
        }
        return '-';
    };

    /**
     * 渲染 tooltip 中的指标行
     * 仅渲染已勾选的指标，颜色与图线保持一致
     */
    const renderIndicatorRows = (): React.ReactNode => {
        if (!tooltipIndicators) return null;

        const colorIndexMap = indicatorColorIndexRef.current;
        const rows: React.ReactNode[] = [];

        Object.entries(visibleIndicators).forEach(([visKey, isVisible]) => {
            if (!isVisible) return;

            // visKey 格式：'EMA_12'、'RSI_14'、'BOLL_20'、'MACD_12'、'KDJ_9'
            const underscoreIdx = visKey.indexOf('_');
            if (underscoreIdx < 0) return;
            const baseType = visKey.substring(0, underscoreIdx); // 'EMA'
            const period = visKey.substring(underscoreIdx + 1);  // '12'

            // 在 tooltipIndicators 中查找匹配的 key（格式如 EMA_ema_12）
            const matchingKey = Object.keys(tooltipIndicators).find(k => {
                if (k.split('_')[0] !== baseType) return false;
                // 去掉 "TYPE_prefix_" 后的剩余部分为周期
                const stripped = k.replace(new RegExp(`^${baseType}_[a-z]+_`, 'i'), '');
                return stripped === period || stripped.startsWith(period + '_') || stripped.startsWith(period + ',');
            });
            if (!matchingKey) return;

            const pt = tooltipIndicators[matchingKey];
            if (!pt) return;

            const colorIdx = colorIndexMap[matchingKey] ?? 0;

            if (baseType === 'EMA' || baseType === 'SMA' || baseType === 'WMA' || baseType === 'ATR') {
                // 单值指标，颜色按周期索引
                const colors = MULTI_PERIOD_COLORS[baseType];
                const color = colors ? colors[colorIdx % colors.length] : '#999';
                if (pt.value === undefined) return;
                rows.push(
                    <div key={visKey} style={{display: 'flex', justifyContent: 'space-between', gap: 8}}>
                        <span style={{color}}>{baseType}{period}:</span>
                        <span style={{color}}>{formatNumber(pt.value, 4)}</span>
                    </div>
                );
            } else if (baseType === 'RSI' || baseType === 'CCI' || baseType === 'OBV') {
                // 振荡类单值指标
                const colors = MULTI_PERIOD_COLORS[baseType];
                const color = colors ? colors[colorIdx % colors.length] : '#999';
                if (pt.value === undefined) return;
                rows.push(
                    <div key={visKey} style={{display: 'flex', justifyContent: 'space-between', gap: 8}}>
                        <span style={{color}}>{baseType}{period}:</span>
                        <span style={{color}}>{formatNumber(pt.value, 2)}</span>
                    </div>
                );
            } else if (baseType === 'ADX') {
                // ADX 指标
                const colors = MULTI_PERIOD_COLORS.ADX;
                const color = colors ? colors[colorIdx % colors.length] : '#f5222d';
                if (pt.value === undefined) return;
                rows.push(
                    <div key={visKey} style={{display: 'flex', justifyContent: 'space-between', gap: 8}}>
                        <span style={{color}}>ADX{period}:</span>
                        <span style={{color}}>{formatNumber(pt.value, 2)}</span>
                    </div>
                );
            } else if (baseType === 'BOLL') {
                // 三轨颜色固定：上红、中黄、下绿
                if (pt.upper === undefined && pt.middle === undefined && pt.lower === undefined) return;
                rows.push(
                    <div key={visKey} style={{fontSize: 11}}>
                        <span style={{color: '#999'}}>BOLL{period}: </span>
                        <span style={{color: '#f5222d'}}>{formatNumber(pt.upper ?? NaN, 4)}</span>
                        <span style={{color: '#555'}}>/</span>
                        <span style={{color: '#faad14'}}>{formatNumber(pt.middle ?? NaN, 4)}</span>
                        <span style={{color: '#555'}}>/</span>
                        <span style={{color: '#52c41a'}}>{formatNumber(pt.lower ?? NaN, 4)}</span>
                    </div>
                );
            } else if (baseType === 'MACD') {
                // DIF橙红、DEA深紫
                rows.push(
                    <div key={visKey} style={{fontSize: 11}}>
                        <span style={{color: '#999'}}>MACD: </span>
                        <span style={{color: '#fa541c'}}>D:{formatNumber(pt.diff ?? NaN, 4)}</span>
                        <span style={{color: '#555'}}> </span>
                        <span style={{color: '#722ed1'}}>S:{formatNumber(pt.signal ?? NaN, 4)}</span>
                    </div>
                );
            } else if (baseType === 'KDJ') {
                // 每个周期独立色系
                const kdjSet = KDJ_COLOR_SETS[colorIdx % KDJ_COLOR_SETS.length];
                rows.push(
                    <div key={visKey} style={{fontSize: 11}}>
                        <span style={{color: '#999'}}>KDJ{period}: </span>
                        <span style={{color: kdjSet.K}}>K:{formatNumber(pt.k ?? NaN, 1)}</span>
                        <span style={{color: '#555'}}> </span>
                        <span style={{color: kdjSet.D}}>D:{formatNumber(pt.d ?? NaN, 1)}</span>
                        <span style={{color: '#555'}}> </span>
                        <span style={{color: kdjSet.J}}>J:{formatNumber(pt.j ?? NaN, 1)}</span>
                    </div>
                );
            }
        });

        if (rows.length === 0) return null;
        return (
            <>
                <div style={{borderTop: '1px solid #444', marginTop: 4, paddingTop: 4}}/>
                {rows}
            </>
        );
    };

    // 格式化时间
    const formatTime = (timestamp: number): string => {
        const date = new Date(timestamp);
        const year = String(date.getFullYear()).slice(-2);
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        
        if (timeFrame === '1m' || timeFrame === '5m') {
            return `${year}-${month}-${day} ${hours}:${minutes}`;
        } else if (timeFrame === '1h' || timeFrame === '4h') {
            return `${year}-${month}-${day} ${hours}:00`;
        } else {
            return `${year}-${month}-${day}`;
        }
    };

    // 当没有传入 height prop 时，使用 100% 自适应父容器
    const containerHeight = height ? chartHeight : '100%';

    return (
        <div style={{ position: 'relative', width: '100%', height: containerHeight }}>
            <div
                ref={containerRef}
                style={{
                    width: '100%',
                    height: containerHeight,
                    display: loading ? 'none' : 'block'
                }}
            />
            {/* 止盈止损区域背景覆盖层 */}
            <canvas
                ref={overlayCanvasRef}
                width={(width || containerSize.width) * (window.devicePixelRatio || 1)}
                height={chartHeight * (window.devicePixelRatio || 1)}
                style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: '100%',
                    pointerEvents: 'none',
                    zIndex: 1
                }}
            />
            {tooltipData && tooltipPosition && (
                <div
                    style={{
                        position: 'absolute',
                        left: Math.min(tooltipPosition.x + 15, (width || containerSize.width) - 160),
                        top: Math.max(tooltipPosition.y - 100, 10),
                        backgroundColor: 'rgba(30, 30, 30, 0.95)',
                        border: '1px solid #444',
                        borderRadius: 4,
                        padding: '8px 12px',
                        fontSize: 12,
                        color: '#fff',
                        pointerEvents: 'none',
                        zIndex: 1000,
                        minWidth: 140,
                    }}
                >
                    <div style={{ color: '#999', marginBottom: 4 }}>{formatTime(tooltipData.time)}</div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
                        <span style={{ color: '#999' }}>开:</span>
                        <span>{formatNumber(tooltipData.open)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
                        <span style={{ color: '#999' }}>高:</span>
                        <span style={{ color: '#52c41a' }}>{formatNumber(tooltipData.high)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
                        <span style={{ color: '#999' }}>低:</span>
                        <span style={{ color: '#ff4d4f' }}>{formatNumber(tooltipData.low)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
                        <span style={{ color: '#999' }}>收:</span>
                        <span style={{ color: tooltipData.close >= tooltipData.open ? '#52c41a' : '#ff4d4f' }}>
                            {formatNumber(tooltipData.close)}
                        </span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, marginTop: 4, borderTop: '1px solid #444', paddingTop: 4 }}>
                        <span style={{ color: '#999' }}>量:</span>
                        <span>{tooltipData.volume > 0 ? formatNumber(tooltipData.volume, 0) : '-'}</span>
                    </div>
                    {/* 已勾选的技术指标数值 */}
                    {renderIndicatorRows()}
                </div>
            )}
        </div>
    );
};

export default LightweightCandlestickChart;
