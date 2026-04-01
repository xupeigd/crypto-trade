import React, {useEffect, useMemo, useState} from 'react';
import {Checkbox, Modal, Segmented, Spin, Typography} from 'antd';
import {CandlestickData} from '../../pages/trading/components/CandlestickChart';
import {klineBarsFromCandles} from '../charts/KLineChart';
import LightweightCandlestickChart from '../charts/LightweightCandlestickChart';
import {TechnicalIndicatorData, tradingService} from '../../services/tradingService';

const {Text} = Typography;

/**
 * 技术指标颜色常量（与LightweightCandlestickChart.tsx中实际使用的颜色保持一致）
 * EMA/RSI 不同周期按升序取对应颜色数组中的颜色
 * KDJ 不同周期取对应色系的 K 线颜色作为代表色
 */
const INDICATOR_COLORS = {
    // EMA 各周期颜色（按升序：12→index0，26→index1）
    EMA: ['#1890ff', '#00b4d8'],
    // RSI 各周期颜色（按升序：9→index0，14→index1）
    RSI: ['#9254de', '#d46b08'],
    // BOLL 三轨颜色（上轨红、中轨黄、下轨绿）
    BOLL_UPPER: '#f5222d',
    BOLL_MIDDLE: '#faad14',
    BOLL_LOWER: '#52c41a',
    // MACD DIF/DEA 颜色
    MACD_DIF: '#fa541c',
    MACD_DEA: '#722ed1',
    // KDJ 各周期 K 线颜色（作为该周期的代表色，按升序：9→index0，14→index1，21→index2）
    KDJ_K: ['#fa8c16', '#13c2c2', '#eb2f96'],
} as const;

const normalizeTimestampMs = (value: unknown): number | undefined => {
    const num = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(num) || num <= 0) return undefined;
    if (num < 1_000_000_000_000) return Math.floor(num * 1000);
    return Math.floor(num);
};

const normalizeNumber = (value: unknown): number | undefined => {
    if (value === null || value === undefined) return undefined;
    if (typeof value === 'string' && value.trim() === '') return undefined;
    const num = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(num)) return undefined;
    return num;
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

export type ActiveChartItem =
    | {
    type: 'position';
    instId: string;
    posSide: string;
    ctime: number | string;
    referencePrice?: number;
    detail: any;
}
    | {
    type: 'order';
    instId: string;
    posSide: string;
    ctime: number | string;
    referencePrice?: number;
    detail: any;
};

type TimeFrame = '1m' | '5m' | '1h' | '4h' | '1d';
type SampleSize = 30 | 60 | 90 | 120 | 150;

interface ActivePositionOrderChartModalProps {
    visible: boolean;
    onClose: () => void;
    item: ActiveChartItem | null;
    apiKeyId: number;
}

const ActivePositionOrderChartModal: React.FC<ActivePositionOrderChartModalProps> = ({
                                                                                         visible,
                                                                                         onClose,
                                                                                         item,
                                                                                         apiKeyId
                                                                                     }) => {
    const [chartData, setChartData] = useState<CandlestickData[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [currentTimeFrame, setCurrentTimeFrame] = useState<TimeFrame>('5m');
    const [manualTimeFrame, setManualTimeFrame] = useState<TimeFrame | undefined>(undefined);
    const [currentLimit, setCurrentLimit] = useState<SampleSize>(120);
    const [manualLimit, setManualLimit] = useState<SampleSize | undefined>(undefined);
    const [currentEntryCandleTime, setCurrentEntryCandleTime] = useState<number | undefined>(undefined);
    const [showTPSL, setShowTPSL] = useState<boolean>(false);
    const [showAvgPx, setShowAvgPx] = useState<boolean>(true);
    const [tpslReloadKey, setTpslReloadKey] = useState<number>(0);
    const [paneReloadKey, setPaneReloadKey] = useState<number>(0);
    const [indicators, setIndicators] = useState<Record<string, TechnicalIndicatorData> | undefined>(undefined);
    const [visibleIndicators, setVisibleIndicators] = useState<Record<string, boolean>>({
        'EMA_12': true,
        'EMA_26': true,
        'RSI_9': true,
        'RSI_14': true,
        'BOLL_20': true,
        'MACD_12': true,
        'KDJ_9': true,
        'KDJ_14': true,
        'KDJ_21': true,
    });
    // 支撑&阻力线状态
    const [showPivotPoints, setShowPivotPoints] = useState<boolean>(false);
    const [pivotPointsData, setPivotPointsData] = useState<{
        pivot: number;
        periodStartTime: number;
        supports: Array<{price: number; basedOn: string; basedTime: number}>;
        resistances: Array<{price: number; basedOn: string; basedTime: number}>;
    } | null>(null);
    const [loadingPivot, setLoadingPivot] = useState<boolean>(false);

    const bars = useMemo(() => klineBarsFromCandles(chartData), [chartData]);

    // 获取 Pivot Points 数据
    const fetchPivotPoints = async () => {
        if (!item?.instId || loadingPivot) return;
        setLoadingPivot(true);
        try {
            const data = await tradingService.getPivotPoints({
                instId: item.instId,
                timeframe: currentTimeFrame,
                limit: currentLimit,
                apiKeyId,
            });
            setPivotPointsData(data);
            setShowPivotPoints(true);
        } catch (e) {
            console.error('获取Pivot Points失败:', e);
        } finally {
            setLoadingPivot(false);
        }
    };

    const toggleIndicator = (key: string, checked: boolean) => {
        setVisibleIndicators(prev => ({
            ...prev,
            [key]: checked
        }));
    };

    // 监听副图指标变化，当所有副图指标（MACD, KDJ, RSI）都取消后，强制重新创建图表以清除空白副图
    useEffect(() => {
        const hasOscillator = Object.keys(visibleIndicators).some(key => {
            if (!visibleIndicators[key]) return false;
            const baseType = key.split('_')[0];
            return ['MACD', 'KDJ', 'RSI'].includes(baseType);
        });

        // 如果没有副图指标，递增key强制重新创建图表
        if (!hasOscillator) {
            setPaneReloadKey(k => k + 1);
        }
    }, [visibleIndicators]);

    useEffect(() => {
        if (visible && item && item.instId && apiKeyId) {
            setManualTimeFrame(undefined);
            setManualLimit(undefined);
            setCurrentLimit(120);
            fetchChartData();
        }
    }, [visible, item, apiKeyId]);

    const fetchChartData = async (overrideTimeFrame?: TimeFrame, overrideLimit?: SampleSize) => {
        if (!item) return;
        setLoading(true);
        setError('');

        try {
            const openTimeMs = normalizeTimestampMs(item.ctime);
            if (!openTimeMs) {
                setError('缺少有效的开仓/委托时间');
                setChartData([]);
                setIndicators(undefined);
                setCurrentEntryCandleTime(undefined);
                return;
            }

            const closeTime = Date.now();
            const durationMs = Math.max(0, closeTime - openTimeMs);

            const timeFrameMsMap: Record<TimeFrame, number> = {
                '1m': 60 * 1000,
                '5m': 5 * 60 * 1000,
                '1h': 60 * 60 * 1000,
                '4h': 4 * 60 * 60 * 1000,
                '1d': 24 * 60 * 60 * 1000
            };

            const hourMs = 60 * 60 * 1000;
            let timeframe: TimeFrame;
            let timeFrameMs: number;

            if (overrideTimeFrame) {
                timeframe = overrideTimeFrame;
                timeFrameMs = timeFrameMsMap[overrideTimeFrame];
            } else {
                if (durationMs < 1 * hourMs) {
                    timeframe = '1m';
                    timeFrameMs = timeFrameMsMap['1m'];
                } else if (durationMs < 5 * hourMs) {
                    timeframe = '5m';
                    timeFrameMs = timeFrameMsMap['5m'];
                } else if (durationMs < 48 * hourMs) {
                    timeframe = '1h';
                    timeFrameMs = timeFrameMsMap['1h'];
                } else {
                    timeframe = '4h';
                    timeFrameMs = timeFrameMsMap['4h'];
                }
            }

            setCurrentTimeFrame(timeframe);

            const limit = overrideLimit ?? currentLimit;
            setCurrentLimit(limit);

            const centerTime = openTimeMs + durationMs / 2;
            const alignedCenterTime = Math.floor(centerTime / timeFrameMs) * timeFrameMs;

            const indicatorWarmupCandles = 35;
            const windowCandlesBefore = Math.floor(limit / 2);
            const startMills = alignedCenterTime - windowCandlesBefore * timeFrameMs - indicatorWarmupCandles * timeFrameMs;
            const finalStartMills = Math.max(0, startMills);

            const response = await tradingService.getCompleteChartData({
                instId: item.instId,
                apiKeyId,
                timeframe,
                limit,
                indicators: ['EMA', 'RSI', 'BOLL', 'MACD', 'KDJ'],
                emaPeriods: [12, 26],
                rsiPeriods: [9, 14],
                bollParams: ['20_2'],
                macdPeriods: [12, 26, 9],
                kdjPeriods: [9, 14, 21],
                startMills: finalStartMills,
            });

            const candles = (response.candles || [])
                .slice()
                .sort((a, b) => a.timestamp - b.timestamp)
                .map((candle: any) => ({
                    timestamp: candle.timestamp,
                    open: candle.open,
                    high: candle.high,
                    low: candle.low,
                    close: candle.close,
                    volume: candle.volume ?? 0,
                    confirm: candle.confirm ?? 1,
                }));
            setChartData(candles);
            setIndicators((response as any).indicators || undefined);

            if (candles.length === 0) {
                setError('未获取到K线数据');
            } else {
                let closest = candles[0].timestamp;
                let minDiff = Math.abs(candles[0].timestamp - openTimeMs);
                for (let i = 1; i < candles.length; i++) {
                    const diff = Math.abs(candles[i].timestamp - openTimeMs);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closest = candles[i].timestamp;
                    }
                }
                setCurrentEntryCandleTime(closest);
            }
        } catch (e) {
            console.error('获取K线数据失败:', e);
            setError('获取K线数据失败');
        } finally {
            setLoading(false);
        }
    };

    const referencePrice = normalizeNumber(item?.referencePrice);
    const openTimeMs = normalizeTimestampMs(item?.ctime);
    const detail = item?.detail || {};
    const strategies = detail.positionStopLossStrategies || detail.position_stop_loss_strategies || [];
    
    // 尝试多种字段命名格式 (camelCase 和 snake_case)
    const totalTakeProfitPx = normalizeNumber(detail.totalTakeProfitPrice ?? detail.total_take_profit_price);
    const totalStopLossPx = normalizeNumber(detail.totalStopLossPrice ?? detail.total_stop_loss_price);

    const closeOrderAlgo = Array.isArray(detail.closeOrderAlgo) ? detail.closeOrderAlgo : (Array.isArray(detail.close_order_algo) ? detail.close_order_algo : []);
    const closeOrderAlgoWithTpSl = closeOrderAlgo.find((a: any) => 
        normalizeNumber(a?.tpTriggerPx ?? a?.tp_trigger_px) !== undefined || 
        normalizeNumber(a?.slTriggerPx ?? a?.sl_trigger_px) !== undefined
    );
    const algoTakeProfitPx = normalizeNumber(closeOrderAlgoWithTpSl?.tpTriggerPx ?? closeOrderAlgoWithTpSl?.tp_trigger_px);
    const algoStopLossPx = normalizeNumber(closeOrderAlgoWithTpSl?.slTriggerPx ?? closeOrderAlgoWithTpSl?.sl_trigger_px);

    const totalStrategy = strategies.find((s: any) => s?.isTotal) || strategies.find((s: any) => s?.valid) || (strategies.length > 0 ? strategies[0] : null);
    const strategyTakeProfitPx = normalizeNumber(totalStrategy?.tpTriggerPx ?? totalStrategy?.tp_trigger_px);
    const strategyStopLossPx = normalizeNumber(totalStrategy?.slTriggerPx ?? totalStrategy?.sl_trigger_px);

    const takeProfitPx = totalTakeProfitPx ?? algoTakeProfitPx ?? strategyTakeProfitPx;
    const stopLossPx = totalStopLossPx ?? algoStopLossPx ?? strategyStopLossPx;
    const hasTPSL = takeProfitPx !== undefined || stopLossPx !== undefined;

    const posSide = item?.posSide || 'long';

    const tpslReferenceLines = useMemo(() => {
        const result = {
            avgPx: referencePrice,      // 开仓均价
            cTime: openTimeMs,           // 开仓时间（毫秒）
            takeProfitPx: showTPSL ? takeProfitPx : undefined,
            stopLossPx: showTPSL ? stopLossPx : undefined,
            posSide: posSide,
            // 支撑阻力线（带时间范围）- 周期开始时间到 basedTime
            supportLines: showPivotPoints && pivotPointsData?.supports
                ? pivotPointsData.supports.map((s, index) => ({
                    price: s.price,
                    startTime: pivotPointsData.periodStartTime,  // 周期开始时间
                    endTime: s.basedTime,  // basedTime 作为结束时间
                    label: `S${index + 1}`  // 标签 S1, S2, S3
                }))
                : undefined,
            resistanceLines: showPivotPoints && pivotPointsData?.resistances
                ? pivotPointsData.resistances.map((r, index) => ({
                    price: r.price,
                    startTime: pivotPointsData.periodStartTime,  // 周期开始时间
                    endTime: r.basedTime,  // basedTime 作为结束时间
                    label: `R${index + 1}`  // 标签 R1, R2, R3
                }))
                : undefined,
        };
        console.log('[TP/SL ReferenceLines] showTPSL:', showTPSL, 'showPivotPoints:', showPivotPoints, 'result:', result);
        return result;
    }, [showTPSL, takeProfitPx, stopLossPx, posSide, referencePrice, openTimeMs, showPivotPoints, pivotPointsData]);

    useEffect(() => {
        if (!hasTPSL) {
            setShowTPSL(false);
        }
    }, [hasTPSL]);

    // 时间帧或采样率变化时，重新获取 pivot points（如果已开启）
    useEffect(() => {
        if (showPivotPoints && item?.instId && apiKeyId) {
            setPivotPointsData(null);
            fetchPivotPoints();
        }
    }, [currentTimeFrame, currentLimit]);

    return (
        <Modal
            title={
                item ? (
                    <div style={{display: 'flex', alignItems: 'center', gap: 8}}>
                        <Text style={{color: item.posSide === 'long' ? '#52c41a' : '#ff4d4f'}}>
                            {item.instId} 
                        </Text>
                        <Text style={{color: '#8c8c8c', fontSize: 12}}>
                            {currentTimeFrame} · {currentLimit}
                        </Text>
                        {item.type === 'position' && (
                            <Text style={{color: '#8c8c8c', fontSize: 12, marginLeft: 8}}>
                                开仓均价: {referencePrice !== undefined ? referencePrice : '-'} ·
                                开仓时间: {openTimeMs ? new Date(openTimeMs).toLocaleString() : '-'}
                            </Text>
                        )}
                        <Segmented
                            size="small"
                            value={manualTimeFrame ?? currentTimeFrame}
                            options={(['1m', '5m', '1h', '4h', '1d'] as TimeFrame[]).map(tf => ({
                                label: tf,
                                value: tf,
                                disabled: loading || (tf === (manualTimeFrame ?? currentTimeFrame)),
                            }))}
                            onChange={(value) => {
                                const next = value as TimeFrame;
                                if (loading) return;
                                const current = manualTimeFrame ?? currentTimeFrame;
                                if (next === current) return;
                                setManualTimeFrame(next);
                                fetchChartData(next, manualLimit ?? currentLimit);
                            }}
                            style={{marginLeft: 8}}
                        />
                        <Segmented
                            size="small"
                            value={manualLimit ?? currentLimit}
                            options={([30, 60, 90, 120, 150] as SampleSize[]).map(limit => ({
                                label: String(limit),
                                value: limit,
                                disabled: loading || (limit === (manualLimit ?? currentLimit)),
                            }))}
                            onChange={(value) => {
                                const next = value as SampleSize;
                                if (loading) return;
                                const current = manualLimit ?? currentLimit;
                                if (next === current) return;
                                setManualLimit(next);
                                setCurrentLimit(next);
                                fetchChartData(manualTimeFrame ?? currentTimeFrame, next);
                            }}
                            style={{marginLeft: 8}}
                        />
                        <Segmented
                            size="small"
                            value={showTPSL ? 'show' : 'hide'}
                            options={[
                                {label: 'TP/SL', value: 'show'},
                                {label: 'Hide', value: 'hide'},
                            ]}
                            disabled={!hasTPSL}
                            onChange={(value) => {
                                setShowTPSL(value === 'show');
                                setTpslReloadKey(k => k + 1);
                            }}
                            style={{marginLeft: 8}}
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
                            value={showPivotPoints ? 'show' : 'hide'}
                            options={[
                                {label: loadingPivot ? '计算中...' : '支撑&突破', value: 'show'},
                                {label: 'Hide', value: 'hide'},
                            ]}
                            onChange={(value) => {
                                if (value === 'show' && !pivotPointsData) {
                                    fetchPivotPoints();
                                } else {
                                    setShowPivotPoints(value === 'show');
                                }
                            }}
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
            ) : chartData.length > 0 ? (
                <div style={{padding: 16, display: 'flex', gap: 16}}>
                    <div style={{width: 1100, height: 560}}>
                        {/* key 用于在 TP/SL 切换或副图指标清空时强制重新创建图表组件，清除残留的价格线或空白副图 */}
                        <LightweightCandlestickChart
                            key={`chart-${showTPSL ? 'show' : 'hide'}-${tpslReloadKey}-${paneReloadKey}`}
                            data={bars}
                            height={560}
                            maxVisibleBars={currentLimit}
                            timeFrame={currentTimeFrame}
                            indicators={transformIndicatorsForChart(indicators)}
                            visibleIndicators={visibleIndicators}
                            referenceLines={tpslReferenceLines}
                            showAvgPx={showAvgPx}
                        />
                    </div>
                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 8,
                        padding: '12px 12px 12px 0',
                        backgroundColor: '#1f1f1f',
                        borderLeft: '1px solid #303030',
                        minWidth: 140
                    }}>
                        <Text style={{color: '#1890ff', fontWeight: 'bold', marginBottom: 4}}>技术指标</Text>
                        {/* EMA：不同周期不同颜色，与K线图中按升序分配的颜色一致 */}
                        <Checkbox checked={visibleIndicators['EMA_12']}
                                  onChange={(e) => toggleIndicator('EMA_12', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.EMA[0]}}>EMA 12</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['EMA_26']}
                                  onChange={(e) => toggleIndicator('EMA_26', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.EMA[1]}}>EMA 26</span>
                        </Checkbox>
                        {/* RSI：不同周期不同颜色 */}
                        <Checkbox checked={visibleIndicators['RSI_9']}
                                  onChange={(e) => toggleIndicator('RSI_9', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.RSI[0]}}>RSI 9</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['RSI_14']}
                                  onChange={(e) => toggleIndicator('RSI_14', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.RSI[1]}}>RSI 14</span>
                        </Checkbox>
                        {/* BOLL：以中轨颜色作为代表色 */}
                        <Checkbox checked={visibleIndicators['BOLL_20']}
                                  onChange={(e) => toggleIndicator('BOLL_20', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.BOLL_MIDDLE}}>BOLL 20,2</span>
                        </Checkbox>
                        {/* MACD：以 DIF 线颜色作为代表色 */}
                        <Checkbox checked={visibleIndicators['MACD_12']}
                                  onChange={(e) => toggleIndicator('MACD_12', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.MACD_DIF}}>MACD(12,26,9)</span>
                        </Checkbox>
                        {/* KDJ：不同周期不同颜色（K线颜色作为该周期的代表色） */}
                        <Checkbox checked={visibleIndicators['KDJ_9']}
                                  onChange={(e) => toggleIndicator('KDJ_9', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.KDJ_K[0]}}>KDJ 9</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['KDJ_14']}
                                  onChange={(e) => toggleIndicator('KDJ_14', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.KDJ_K[1]}}>KDJ 14</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['KDJ_21']}
                                  onChange={(e) => toggleIndicator('KDJ_21', e.target.checked)}>
                            <span style={{color: INDICATOR_COLORS.KDJ_K[2]}}>KDJ 21</span>
                        </Checkbox>
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

export default ActivePositionOrderChartModal;
