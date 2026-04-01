import React, {useEffect, useMemo, useState} from 'react';
import {Modal, Spin, Typography, Segmented, Checkbox} from 'antd';
import {CandlestickData, COLORS} from '../../pages/trading/components/CandlestickChart';
import {klineBarsFromCandles} from '../charts/KLineChart';
import LightweightCandlestickChart from '../charts/LightweightCandlestickChart';
import {tradingService, TechnicalIndicatorData} from '../../services/tradingService';
import type {TimeFrame} from '../../hooks/useChartState';

const {Text} = Typography;

const normalizeTimestampMs = (value: unknown): number | undefined => {
    const num = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(num) || num <= 0) return undefined;
    if (num < 1_000_000_000_000) return Math.floor(num * 1000);
    return Math.floor(num);
};

const transformIndicatorsForChart = (rawIndicators: Record<string, any> | undefined) => {
    if (!rawIndicators) return undefined;

    const result: Record<string, Array<{
        time: number;
        value?: number;
        upper?: number;
        middle?: number;
        lower?: number;
        diff?: number;
        signal?: number;
        histogram?: number;
        k?: number;
        d?: number;
        j?: number;
    }>> = {};

    Object.entries(rawIndicators).forEach(([key, indicator]) => {
        const values = indicator?.data?.values || indicator?.values;
        if (!values || !Array.isArray(values)) return;

        values.forEach((v: any) => {
            const multiPeriod = v.multiPeriodValues;
            if (!multiPeriod) return;

            Object.entries(multiPeriod).forEach(([periodKey, periodData]: [string, any]) => {
                const seriesKey = `${key}_${periodKey}`;

                if (key === 'MACD') {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        diff: periodData.diff,
                        signal: periodData.dea,
                        histogram: periodData.macd,
                    });
                } else if (key === 'BOLL') {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        upper: periodData.upper,
                        middle: periodData.middle,
                        lower: periodData.lower,
                    });
                } else if (key === 'KDJ') {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        k: periodData.k,
                        d: periodData.d,
                        j: periodData.j,
                    });
                } else {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        value: periodData,
                    });
                }
            });
        });
    });

    Object.values(result).forEach(arr => arr.sort((a, b) => a.time - b.time));
    return result;
};

interface HistoryPositionChartModalProps {
    visible: boolean;
    onClose: () => void;
    position: {
        instId: string;
        posSide: string;
        ctime: number | string;
        utime: number | string;
        openAvgPx?: number | string;
        closeAvgPx?: number | string;
    };
    apiKeyId?: number;
}

/**
 * 历史仓位K线图弹窗组件
 * 显示历史仓位的K线图，标注开仓和平仓点位
 */
const HistoryPositionChartModal: React.FC<HistoryPositionChartModalProps> = ({
                                                                                 visible,
                                                                                 onClose,
                                                                                 position,
                                                                                 apiKeyId
                                                                             }) => {
    const [chartData, setChartData] = useState<CandlestickData[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [markPrice, setMarkPrice] = useState<number | null>(null);
    const [currentTimeFrame, setCurrentTimeFrame] = useState<TimeFrame>('5m');
    const [manualTimeFrame, setManualTimeFrame] = useState<TimeFrame | null>(null);
    const [currentLimit, setCurrentLimit] = useState<number>(60);
    const [currentAlignedOpenTime, setCurrentAlignedOpenTime] = useState<number | undefined>(undefined);
    const [currentAlignedCloseTime, setCurrentAlignedCloseTime] = useState<number | undefined>(undefined);
    const [referenceLinesData, setReferenceLinesData] = useState<any>(null);
    const [reverseOrder, setReverseOrder] = useState<boolean>(true);
    const [indicators, setIndicators] = useState<Record<string, TechnicalIndicatorData> | undefined>(undefined);
    const [visibleIndicators, setVisibleIndicators] = useState<Record<string, boolean>>({
        /** EMA 12: 指数移动平均线，对近期价格更敏感，用于短期趋势分析 */
        'EMA_12': true,
        /** EMA 26: 较长周期的 EMA，用于辅助判断中短期趋势 */
        'EMA_26': true,
        /** RSI 9: 相对强弱指标，用于识别超买超卖状态 */
        'RSI_9': true,
        /** RSI 14: 标准周期的 RSI */
        'RSI_14': true,
        /** BOLL 20: 布林带，衡量价格波动及潜在支撑压力位 */
        'BOLL_20': true,
        /** MACD 12: 平滑异同移动平均线，动量指标 */
        'MACD_12': true,
        /** KDJ 9: 随机指标，周期 9 */
        'KDJ_9': true,
        /** KDJ 14: 随机指标，周期 14 */
        'KDJ_14': true,
        /** KDJ 21: 随机指标，周期 21 */
        'KDJ_21': true,
    });
    const [showAvgPx, setShowAvgPx] = useState<boolean>(true);
    const [showClosePx, setShowClosePx] = useState<boolean>(true);

    // 解析开仓价和平仓价
    const openPrice = position?.openAvgPx !== undefined && position?.openAvgPx !== null ? Number(position.openAvgPx) : undefined;
    const closePrice = position?.closeAvgPx !== undefined && position?.closeAvgPx !== null ? Number(position.closeAvgPx) : undefined;
    const bars = useMemo(() => klineBarsFromCandles(chartData), [chartData]);

    // 计算盈亏状态
    let profitLossStatus: 'profit' | 'loss' | 'break-even' = 'break-even';
    if (openPrice !== undefined && closePrice !== undefined && position) {
        if (position.posSide === 'long') {
            // 做多：平仓价>开仓价为盈利
            profitLossStatus = closePrice > openPrice ? 'profit' : (closePrice < openPrice ? 'loss' : 'break-even');
        } else if (position.posSide === 'short') {
            // 做空：平仓价<开仓价为盈利
            profitLossStatus = closePrice < openPrice ? 'profit' : (closePrice > openPrice ? 'loss' : 'break-even');
        }
    }

    // 格式化日期时间的辅助函数
    const formatDate = (timestamp: number, tf: string): string => {
        const date = new Date(timestamp);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}`;
    };

    // 切换指标可见性
    const toggleIndicator = (key: string, checked: boolean) => {
        setVisibleIndicators(prev => ({
            ...prev,
            [key]: checked
        }));
    };

    // 获取K线数据
    useEffect(() => {
        if (visible && position && position.instId) {
            setManualTimeFrame(null);
            setCurrentLimit(60);
            setReverseOrder(true);
            fetchChartData({timeframe: null, limit: 60, reverseOrder: true});
        }
    }, [visible, position, apiKeyId]);

    const fetchChartData = async (opts?: { timeframe?: TimeFrame | null; limit?: number; reverseOrder?: boolean }) => {
        setLoading(true);
        setError('');

        try {
            // 计算持仓时长（毫秒）
            const openTime = normalizeTimestampMs(position.ctime);
            const closeTime = normalizeTimestampMs(position.utime);
            if (!openTime || !closeTime) {
                setError('缺少有效的开仓/平仓时间');
                setChartData([]);
                setIndicators(undefined);
                setMarkPrice(null);
                setCurrentAlignedOpenTime(undefined);
                setCurrentAlignedCloseTime(undefined);
                return;
            }

            const holdDuration = closeTime - openTime;
            if (holdDuration <= 0) {
                setError('开仓/平仓时间不合法');
                setChartData([]);
                setIndicators(undefined);
                setMarkPrice(null);
                setCurrentAlignedOpenTime(undefined);
                setCurrentAlignedCloseTime(undefined);
                return;
            }

            const pickAutoTimeFrame = (durationMs: number): TimeFrame => {
                const hourMs = 60 * 60 * 1000;
                if (durationMs < 1 * hourMs) return '1m';
                if (durationMs < 5 * hourMs) return '5m';
                if (durationMs < 24 * hourMs) return '1h';
                return '4h';
            };

            // 计算持仓时长在各个时间帧下的K线数量
            const candles1m = holdDuration / (60 * 1000);      // 1分钟时间帧的K线数
            const candles5m = holdDuration / (5 * 60 * 1000);  // 5分钟时间帧的K线数
            const candles1h = holdDuration / (60 * 60 * 1000); // 1小时时间帧的K线数
            const candles4h = holdDuration / (4 * 60 * 60 * 1000); // 4小时时间帧的K线数

            console.log('[HistoryPositionChartModal] 持仓时长分析:', {
                holdDuration: `${holdDuration / 1000}秒`,
                candles1m: candles1m.toFixed(2),
                candles5m: candles5m.toFixed(2),
                candles1h: candles1h.toFixed(2),
                candles4h: candles4h.toFixed(2)
            });

            const limit = opts?.limit ?? currentLimit;
            const timeframe: TimeFrame = opts?.timeframe === undefined
                ? (manualTimeFrame ?? pickAutoTimeFrame(holdDuration))
                : (opts.timeframe ?? pickAutoTimeFrame(holdDuration));
            const effectiveReverseOrder = opts?.reverseOrder ?? reverseOrder;

            const timeFrameMs: number = (() => {
                if (timeframe === '1m') return 60 * 1000;
                if (timeframe === '5m') return 5 * 60 * 1000;
                if (timeframe === '1h') return 60 * 60 * 1000;
                return 4 * 60 * 60 * 1000;
            })();

            const hourMs = 60 * 60 * 1000;

            console.log('[HistoryPositionChartModal] 选定时间帧:', {
                timeframe,
                timeFrameMs: `${timeFrameMs / 1000}秒`,
                holdDuration: `${(holdDuration / hourMs).toFixed(2)}小时`
            });

            // 将开仓和平仓时间对齐到K线边界（K线开始时间）
            const openCandleStartTime = Math.floor(openTime / timeFrameMs) * timeFrameMs;
            const closeCandleStartTime = Math.floor(closeTime / timeFrameMs) * timeFrameMs;

            // 计算对齐后的持仓时长（以K线为单位）
            const alignedCandlesCount = Math.max(
                0,
                Math.round((closeCandleStartTime - openCandleStartTime) / timeFrameMs)
            );

            console.log('[HistoryPositionChartModal] 对齐时间:', {
                openTime: new Date(openTime).toISOString(),
                openTime_formatted: formatDate(openTime, timeframe),
                openCandleStartTime: new Date(openCandleStartTime).toISOString(),
                openCandleStartTime_formatted: formatDate(openCandleStartTime, timeframe),
                closeTime: new Date(closeTime).toISOString(),
                closeTime_formatted: formatDate(closeTime, timeframe),
                closeCandleStartTime: new Date(closeCandleStartTime).toISOString(),
                closeCandleStartTime_formatted: formatDate(closeCandleStartTime, timeframe),
                alignedCandlesCount
            });

            const centerIndex = Math.floor(limit / 2);

            // 2. 计算startMills，让开平仓区域处于第30根K线附近对称
            const alignedCenterTime = openCandleStartTime + Math.floor(alignedCandlesCount / 2) * timeFrameMs;
            
            // 我们希望展示 limit 个K线，且开平仓区域围绕第30根附近对称
            // startMills在后端映射为OKX API的before参数，获取比startMills更新的数据（时间戳更大）
            // 还是after参数？
            // 经查后端代码 OkxApiService.java:
            // if (startMills != null && startMills > 0) {
            //    queryParams.append("&before=").append(startMills);
            // }
            // 
            // 关键点：后端将startMills映射为了 `before` 参数！
            // OKX API `before`: 请求比此ID/时间戳更旧的数据？不对。
            // 查阅OKX API文档：
            // `after`: 请求此ID之前（更旧）的数据。
            // `before`: 请求此ID之后（更新）的数据。
            // 
            // 如果后端使用的是 `before`，那么我们需要传入一个较旧的时间点，API会返回比它更新的数据。
            // 
            // 目标：获取包含 [openTime, closeTime] 的时间段，且让 centerTime 位于中间。
            // 假设我们希望 centerTime 是第30根K线（从左往右数，即从旧到新）。
            // 那么我们需要的数据范围大约是 [centerTime - centerIndex*interval, centerTime + (limit-centerIndex)*interval]。
            // 
            // 如果使用 `before` 参数（获取更新的数据）：
            // 我们应该传入数据的"起始点"（最旧的时间点）。
            // API会返回比这个时间点更新的数据，按时间正序还是倒序？
            // OKX K线接口默认返回倒序（最新在前）。
            // 如果传 `before`，通常用于请求更新的数据（向未来翻页）。
            // 比如当前列表是 [T100, ..., T80]。
            // 传 `before=T100`，会返回 [T120, ..., T101]。
            // 
            // 所以，如果我们想要的数据中最旧的大概是 `centerTime - 30*interval`。
            // 那么我们可以把 `startMills` 设置为这个最旧的时间点。
            // 然后后端传给 `before`，请求比它更新的数据。
            // 
            // 计算：
            // startMills = alignedCenterTime - centerIndex * timeFrameMs;
            
            const indicatorWarmupCandles = Math.max(
                0,
                35,
                Math.max(...[12, 26]),
                Math.max(...[9, 14]) + 1,
                20
            );

            const windowCandlesBefore = centerIndex;
            const windowCandlesAfter = Math.max(0, limit - 1 - centerIndex);
            const windowCandlesFromLeftEdge = effectiveReverseOrder ? windowCandlesBefore : windowCandlesAfter;

            let startMills = alignedCenterTime - (indicatorWarmupCandles + windowCandlesFromLeftEdge) * timeFrameMs;
            
            // 确保startMills不为负数
            const finalStartMills = Math.max(0, startMills);
            if (!Number.isFinite(finalStartMills)) {
                setError('K线时间窗计算失败');
                setChartData([]);
                setIndicators(undefined);
                setMarkPrice(null);
                setCurrentAlignedOpenTime(undefined);
                setCurrentAlignedCloseTime(undefined);
                return;
            }

            // 重新计算开仓位置用于验证（仅用于日志）
            const candlesCount = alignedCandlesCount;
            const expectedOpenIndex = effectiveReverseOrder
                ? (centerIndex - (candlesCount / 2))
                : (centerIndex + (candlesCount / 2));
            const expectedCloseIndex = effectiveReverseOrder
                ? (centerIndex + (candlesCount / 2))
                : (centerIndex - (candlesCount / 2));

            // 开仓K线的结束时间（用于计算startMills）
            const openCandleEndTime = openCandleStartTime + timeFrameMs;

            // 计算第一根K线的开始时间（用于验证）
            // 如果使用了before=startMills，那么返回的数据应该是 [startMills+60*interval, ..., startMills+1*interval]
            // 最旧的一根K线大约是 startMills + 1*interval
            const firstCandleStartTime = finalStartMills;

            // 设置对齐后的开仓和平仓时间，用于图表显示
            console.log('[HistoryPositionChartModal] 计算对齐时间:', {
                alignedOpenTime: openCandleStartTime,
                alignedOpenTime_formatted: formatDate(openCandleStartTime, timeframe),
                alignedCloseTime: closeCandleStartTime,
                alignedCloseTime_formatted: formatDate(closeCandleStartTime, timeframe)
            });

            // 更新当前时间帧显示
            setCurrentTimeFrame(timeframe);
            setCurrentLimit(limit);

            const response = await tradingService.getCompleteChartData({
                instId: position.instId,
                apiKeyId,
                timeframe: timeframe,
                limit,
                indicators: ['EMA', 'RSI', 'BOLL', 'MACD', 'KDJ'],
                emaPeriods: [12, 26],
                rsiPeriods: [9, 14],
                bollParams: ['20_2'],
                macdPeriods: [12, 26, 9],
                kdjPeriods: [9, 14, 21],
                startMills: finalStartMills,
            });

            console.log('K线数据响应:', response);
            console.log('K线数据指标:', response.indicators);
            if (response.indicators && response.indicators.MACD) {
                console.log('MACD指标数据详情:', response.indicators.MACD);
                console.log('MACD值数组长度:', response.indicators.MACD.values ? response.indicators.MACD.values.length : 0);
                if (response.indicators.MACD.values && response.indicators.MACD.values.length > 0) {
                    console.log('第一条MACD数据:', response.indicators.MACD.values[0]);
                }
            } else {
                console.warn('响应中未包含MACD指标数据');
            }

            const candles: CandlestickData[] = (response.candles || []).map((c) => ({
                timestamp: c.timestamp,
                open: c.open,
                high: c.high,
                low: c.low,
                close: c.close,
                volume: c.volume ?? 0,
                confirm: c.confirm ?? 1,
            }));

            const wrappedIndicators: Record<string, TechnicalIndicatorData> | undefined = response.indicators
                ? (Object.fromEntries(
                    Object.entries(response.indicators).map(([key, data]) => [
                        key,
                        {success: true, message: '', data},
                    ])
                ) as Record<string, TechnicalIndicatorData>)
                : undefined;

            // 验证开仓平仓位置是否在第30根对称
            if (candles.length > 0) {
                const newestCandleTime = Math.max(...candles.map(c => c.timestamp));
                const oldestCandleTime = Math.min(...candles.map(c => c.timestamp));

                const openIndexFromOldest = Math.floor((openCandleStartTime - oldestCandleTime) / timeFrameMs);
                const closeIndexFromOldest = Math.floor((closeCandleStartTime - oldestCandleTime) / timeFrameMs);

                const openIndexFromNewest = Math.floor((newestCandleTime - openCandleStartTime) / timeFrameMs);
                const closeIndexFromNewest = Math.floor((newestCandleTime - closeCandleStartTime) / timeFrameMs);

                const openIndex = effectiveReverseOrder ? openIndexFromOldest : openIndexFromNewest;
                const closeIndex = effectiveReverseOrder ? closeIndexFromOldest : closeIndexFromNewest;

                console.log('[HistoryPositionChartModal] K线位置验证:', {
                    oldestCandleTime: new Date(oldestCandleTime).toISOString(),
                    oldestCandleTime_formatted: formatDate(oldestCandleTime, timeframe),
                    newestCandleTime: new Date(newestCandleTime).toISOString(),
                    newestCandleTime_formatted: formatDate(newestCandleTime, timeframe),
                    openTime: new Date(openTime).toISOString(),
                    openTime_formatted: formatDate(openTime, timeframe),
                    openCandleStartTime: new Date(openCandleStartTime).toISOString(),
                    openCandleStartTime_formatted: formatDate(openCandleStartTime, timeframe),
                    closeTime: new Date(closeTime).toISOString(),
                    closeTime_formatted: formatDate(closeTime, timeframe),
                    closeCandleStartTime: new Date(closeCandleStartTime).toISOString(),
                    closeCandleStartTime_formatted: formatDate(closeCandleStartTime, timeframe),
                    openIndex,
                    closeIndex,
                    openIndexFromOldest,
                    closeIndexFromOldest,
                    openIndexFromNewest,
                    closeIndexFromNewest,
                    expectedOpenIndex,
                    expectedCloseIndex,
                    candlesCount: candles.length
                });

                // 确保开仓时间比平仓时间早，如果反了就交换
                let actualOpenTime = openCandleStartTime;
                let actualCloseTime = closeCandleStartTime;
                if (openCandleStartTime > closeCandleStartTime) {
                    console.warn('[HistoryPositionChartModal] 警告：开仓时间大于平仓时间，交换两者');
                    actualOpenTime = closeCandleStartTime;
                    actualCloseTime = openCandleStartTime;
                }

                // 计算实际的中点位置
                const actualMiddle = (openIndex + closeIndex) / 2;
                console.log('[HistoryPositionChartModal] 实际中点位置:', {
                    actualMiddle: actualMiddle.toFixed(2),
                    expectedMiddle: centerIndex,
                    diff: (actualMiddle - centerIndex).toFixed(2)
                });

                // 立即更新对齐时间状态（使用修正后的时间）
                setCurrentAlignedOpenTime(actualOpenTime);
                setCurrentAlignedCloseTime(actualCloseTime);
            }

            setChartData(candles);
            setMarkPrice(response.markPrice || null);
            setIndicators(wrappedIndicators);

            if (candles.length === 0) {
                setError('未获取到K线数据');
            }
        } catch (e) {
            console.error('获取K线数据失败:', e);
            setError('获取K线数据失败');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={
                position ? (
                    <div style={{display: 'flex', alignItems: 'center', gap: 8}}>
                        <Text style={{color: position.posSide === 'long' ? '#52c41a' : '#ff4d4f'}}>
                            {position.instId} K线图
                        </Text>
                        <Text style={{color: '#8c8c8c', fontSize: 12}}>
                            {(manualTimeFrame ?? (currentTimeFrame as TimeFrame))} · {currentLimit}
                        </Text>
                        <Segmented
                            size="small"
                            value={manualTimeFrame ?? (currentTimeFrame as TimeFrame)}
                            options={(['1m', '5m', '1h', '4h', '1d'] as TimeFrame[]).map(tf => ({
                                label: tf,
                                value: tf,
                                disabled: tf === (manualTimeFrame ?? (currentTimeFrame as TimeFrame)),
                            }))}
                            onChange={(value) => {
                                const next = value as TimeFrame;
                                setManualTimeFrame(next);
                                fetchChartData({timeframe: next, limit: currentLimit});
                            }}
                            style={{marginLeft: 8}}
                        />
                        <Segmented
                            size="small"
                            value={currentLimit}
                            options={([60, 90, 120, 150] as number[]).map(l => ({
                                label: String(l),
                                value: l,
                                disabled: l === currentLimit,
                            }))}
                            onChange={(value) => {
                                const next = value as number;
                                setCurrentLimit(next);
                                fetchChartData({timeframe: manualTimeFrame ?? (currentTimeFrame as TimeFrame), limit: next});
                            }}
                        />
                        <Segmented
                            size="small"
                            value={showAvgPx ? 'show' : 'hide'}
                            options={[
                                {label: '开仓', value: 'show'},
                                {label: 'Hide', value: 'hide'},
                            ]}
                            onChange={(value) => setShowAvgPx(value === 'show')}
                            style={{marginLeft: 8}}
                        />
                        <Segmented
                            size="small"
                            value={showClosePx ? 'show' : 'hide'}
                            options={[
                                {label: '平仓', value: 'show'},
                                {label: 'Hide', value: 'hide'},
                            ]}
                            onChange={(value) => setShowClosePx(value === 'show')}
                            style={{marginLeft: 8}}
                        />
                    </div>
                ) : 'K线图'
            }
            open={visible}
            onCancel={onClose}
            footer={null}
            width={1350}
            styles={{
                body: {backgroundColor: '#1f1f1f'},
                header: {backgroundColor: '#1f1f1f', color: '#ffffff', borderBottom: '1px solid #303030'}
            }}
            style={{color: '#ffffff'}}
        >
            {loading ? (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: 600}}>
                    <Spin size="large"/>
                </div>
            ) : error ? (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: 600}}>
                    <Text style={{color: '#ff4d4f'}}>{error}</Text>
                </div>
            ) : chartData.length > 0 && position ? (
                <div style={{padding: 16, display: 'flex', gap: 16}}>
                    <div style={{width: 1100, height: 560}}>
                        <LightweightCandlestickChart
                            data={bars}
                            height={560}
                            maxVisibleBars={currentLimit}
                            timeFrame={currentTimeFrame}
                            indicators={transformIndicatorsForChart(indicators)}
                            visibleIndicators={visibleIndicators}
                            referenceLines={{
                                avgPx: openPrice,
                                closePx: closePrice,
                                cTime: currentAlignedOpenTime,
                                uTime: currentAlignedCloseTime,
                                posSide: position?.posSide,
                            }}
                            showEntryLine={false}
                            lockRightEdge={true}
                            showAvgPx={showAvgPx}
                            showClosePx={showClosePx}
                        />
                    </div>
                    <div style={{display: 'flex', flexDirection: 'column', gap: 8, padding: '12px 12px 12px 0', backgroundColor: '#1f1f1f', borderLeft: '1px solid #303030', minWidth: 140}}>
                        <Text style={{color: '#1890ff', fontWeight: 'bold', marginBottom: 4}}>技术指标</Text>
                        
                        {/* EMA 12 切换开关 */}
                        <Checkbox checked={visibleIndicators['EMA_12']} onChange={(e) => toggleIndicator('EMA_12', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.EMA[0]}}>EMA(12)</span>
                        </Checkbox>
                        
                        {/* EMA 26 切换开关 */}
                        <Checkbox checked={visibleIndicators['EMA_26']} onChange={(e) => toggleIndicator('EMA_26', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.EMA[1] || COLORS.indicatorColors.EMA[0]}}>EMA(26)</span>
                        </Checkbox>
                        
                        {/* RSI 9 切换开关 */}
                        <Checkbox checked={visibleIndicators['RSI_9']} onChange={(e) => toggleIndicator('RSI_9', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.RSI[0]}}>RSI(9)</span>
                        </Checkbox>
                        
                        {/* RSI 14 切换开关 */}
                        <Checkbox checked={visibleIndicators['RSI_14']} onChange={(e) => toggleIndicator('RSI_14', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.RSI[1] || COLORS.indicatorColors.RSI[0]}}>RSI(14)</span>
                        </Checkbox>
                        
                        {/* BOLL 20 切换开关 */}
                        <Checkbox checked={visibleIndicators['BOLL_20']} onChange={(e) => toggleIndicator('BOLL_20', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.BOLL[0]}}>BOLL(20)</span>
                        </Checkbox>
                        
                        {/* MACD 切换开关 (12, 26, 9) */}
                        <Checkbox checked={visibleIndicators['MACD_12']} onChange={(e) => toggleIndicator('MACD_12', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.BOLL[2] || COLORS.indicatorColors.EMA[0]}}>MACD(12,26,9)</span>
                        </Checkbox>

                        {/* KDJ 9 切换开关 */}
                        <Checkbox checked={visibleIndicators['KDJ_9']} onChange={(e) => toggleIndicator('KDJ_9', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.KDJ[0]}}>KDJ(9)</span>
                        </Checkbox>

                        {/* KDJ 14 切换开关 */}
                        <Checkbox checked={visibleIndicators['KDJ_14']} onChange={(e) => toggleIndicator('KDJ_14', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.KDJ[1] || COLORS.indicatorColors.KDJ[0]}}>KDJ(14)</span>
                        </Checkbox>

                        {/* KDJ 21 切换开关 */}
                        <Checkbox checked={visibleIndicators['KDJ_21']} onChange={(e) => toggleIndicator('KDJ_21', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.KDJ[2] || COLORS.indicatorColors.KDJ[0]}}>KDJ(21)</span>
                        </Checkbox>
                    </div>
                    <div style={{position: 'absolute', bottom: 16, left: 16, display: 'flex', gap: 24, fontSize: 12}}>
                        {openPrice && (
                            <Text style={{color: '#1890ff'}}>
                                开仓价: {openPrice.toFixed(2)}
                            </Text>
                        )}
                        {closePrice && (
                            <Text style={{color: '#52c41a'}}>
                                平仓价: {closePrice.toFixed(2)}
                            </Text>
                        )}
                        {position?.ctime && (
                            <Text style={{color: '#8c8c8c'}}>
                                开仓时间: {new Date(Number(position.ctime)).toLocaleString()}
                            </Text>
                        )}
                        {position?.utime && (
                            <Text style={{color: '#8c8c8c'}}>
                                平仓时间: {new Date(Number(position.utime)).toLocaleString()}
                            </Text>
                        )}
                    </div>
                </div>
            ) : (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: 600}}>
                    <Text style={{color: '#8c8c8c'}}>暂无K线数据</Text>
                </div>
            )}
        </Modal>
    );
};

export default HistoryPositionChartModal;
