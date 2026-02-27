import React, {useEffect, useMemo, useState} from 'react';
import {Checkbox, Modal, Segmented, Spin, Typography} from 'antd';
import {CandlestickData, COLORS} from '../../pages/trading/components/CandlestickChart';
import {klineBarsFromCandles, KLineChart} from '../charts/KLineChart';
import {TechnicalIndicatorData, tradingService} from '../../services/tradingService';

const {Text} = Typography;

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
    const [reverseOrder, setReverseOrder] = useState<boolean>(true);
    const [showTPSL, setShowTPSL] = useState<boolean>(false);
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
    const bars = useMemo(() => klineBarsFromCandles(chartData), [chartData]);

    const toggleIndicator = (key: string, checked: boolean) => {
        setVisibleIndicators(prev => ({
            ...prev,
            [key]: checked
        }));
    };

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

    useEffect(() => {
        if (!hasTPSL) {
            setShowTPSL(false);
        }
    }, [hasTPSL]);

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
                            value={reverseOrder ? 'left' : 'right'}
                            options={[
                                {label: '←', value: 'left', disabled: reverseOrder},
                                {label: '→', value: 'right', disabled: !reverseOrder},
                            ]}
                            onChange={(value) => {
                                const next = value as 'left' | 'right';
                                setReverseOrder(next === 'left');
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
                        <KLineChart
                            symbol={item?.instId}
                            period={currentTimeFrame as any}
                            data={bars}
                            reverseOrder={reverseOrder}
                            indicators={indicators}
                            visibleIndicators={visibleIndicators}
                            referenceLines={{
                                avgPx: referencePrice,
                                includeAvgPxInRange: item?.type === 'position' || hasTPSL,
                                entryLabel: item?.type === 'order' ? '委托开仓' : '开仓',
                                cTime: currentEntryCandleTime,
                                takeProfitPx: showTPSL ? takeProfitPx : undefined,
                                stopLossPx: showTPSL ? stopLossPx : undefined,
                                posSide
                            }}
                            renderTooltipExtra={({candle, referenceLines}) => {
                            if (!item || !referenceLines?.cTime || candle.timestamp !== referenceLines.cTime) return null;

                            if (item.type === 'position') {
                                const detail = item.detail || {};
                                const sz = detail.pos ?? detail.position ?? '-';
                                const avgPx = detail.avgPx ?? referencePrice ?? '-';
                                const lever = detail.lever ?? '-';
                                return (
                                    <div>
                                        <div style={{
                                            color: '#8c8c8c',
                                            fontSize: 11,
                                            fontWeight: 'bold',
                                            marginBottom: 4
                                        }}>
                                            开仓详情
                                        </div>
                                        <div style={{display: 'flex', justifyContent: 'space-between', gap: 12}}>
                                            <span style={{color: '#8c8c8c'}}>方向</span>
                                            <span
                                                style={{color: item.posSide === 'long' ? '#52c41a' : '#ff4d4f'}}>{item.posSide}</span>
                                        </div>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            gap: 12,
                                            marginTop: 2
                                        }}>
                                            <span style={{color: '#8c8c8c'}}>数量</span>
                                            <span style={{color: '#fff'}}>{String(sz)}</span>
                                        </div>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            gap: 12,
                                            marginTop: 2
                                        }}>
                                            <span style={{color: '#8c8c8c'}}>均价</span>
                                            <span style={{color: '#fff'}}>{String(avgPx)}</span>
                                        </div>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            gap: 12,
                                            marginTop: 2
                                        }}>
                                            <span style={{color: '#8c8c8c'}}>杠杆</span>
                                            <span style={{color: '#fff'}}>{String(lever)}</span>
                                        </div>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            gap: 12,
                                            marginTop: 2
                                        }}>
                                            <span style={{color: '#8c8c8c'}}>时间</span>
                                            <span
                                                style={{color: '#fff'}}>{openTimeMs ? new Date(openTimeMs).toLocaleString() : '-'}</span>
                                        </div>
                                    </div>
                                );
                            }

                            const detail = item.detail || {};
                            const side = detail.side ?? '-';
                            const sz = detail.sz ?? '-';
                            const px = detail.px ?? referencePrice ?? '-';
                            const ordType = detail.ordType ?? '-';
                            const state = detail.state ?? '-';
                            return (
                                <div>
                                    <div style={{color: '#8c8c8c', fontSize: 11, fontWeight: 'bold', marginBottom: 4}}>
                                        委托详情
                                    </div>
                                    <div style={{display: 'flex', justifyContent: 'space-between', gap: 12}}>
                                        <span style={{color: '#8c8c8c'}}>方向</span>
                                        <span
                                            style={{color: side === 'buy' ? '#52c41a' : '#ff4d4f'}}>{String(side)}</span>
                                    </div>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        gap: 12,
                                        marginTop: 2
                                    }}>
                                        <span style={{color: '#8c8c8c'}}>posSide</span>
                                        <span style={{color: '#fff'}}>{String(item.posSide)}</span>
                                    </div>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        gap: 12,
                                        marginTop: 2
                                    }}>
                                        <span style={{color: '#8c8c8c'}}>数量</span>
                                        <span style={{color: '#fff'}}>{String(sz)}</span>
                                    </div>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        gap: 12,
                                        marginTop: 2
                                    }}>
                                        <span style={{color: '#8c8c8c'}}>价格</span>
                                        <span style={{color: '#fff'}}>{String(px)}</span>
                                    </div>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        gap: 12,
                                        marginTop: 2
                                    }}>
                                        <span style={{color: '#8c8c8c'}}>类型</span>
                                        <span style={{color: '#fff'}}>{String(ordType)}</span>
                                    </div>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        gap: 12,
                                        marginTop: 2
                                    }}>
                                        <span style={{color: '#8c8c8c'}}>状态</span>
                                        <span style={{color: '#fff'}}>{String(state)}</span>
                                    </div>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        gap: 12,
                                        marginTop: 2
                                    }}>
                                        <span style={{color: '#8c8c8c'}}>时间</span>
                                        <span
                                            style={{color: '#fff'}}>{openTimeMs ? new Date(openTimeMs).toLocaleString() : '-'}</span>
                                    </div>
                                </div>
                            );
                            }}
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
                        <Checkbox checked={visibleIndicators['EMA_12']}
                                  onChange={(e) => toggleIndicator('EMA_12', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.EMA[0]}}>EMA 12</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['EMA_26']}
                                  onChange={(e) => toggleIndicator('EMA_26', e.target.checked)}>
                            <span style={{
                                color: COLORS.indicatorColors.EMA[1] || COLORS.indicatorColors.EMA[0]
                            }}>EMA 26</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['RSI_9']}
                                  onChange={(e) => toggleIndicator('RSI_9', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.RSI[0]}}>RSI 9</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['RSI_14']}
                                  onChange={(e) => toggleIndicator('RSI_14', e.target.checked)}>
                            <span style={{
                                color: COLORS.indicatorColors.RSI[1] || COLORS.indicatorColors.RSI[0]
                            }}>RSI 14</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['BOLL_20']}
                                  onChange={(e) => toggleIndicator('BOLL_20', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.BOLL[0]}}>BOLL 20,2</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['MACD_12']}
                                  onChange={(e) => toggleIndicator('MACD_12', e.target.checked)}>
                            <span style={{
                                color: COLORS.indicatorColors.BOLL[2] || COLORS.indicatorColors.EMA[0]
                            }}>MACD(12,26,9)</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['KDJ_9']}
                                  onChange={(e) => toggleIndicator('KDJ_9', e.target.checked)}>
                            <span style={{color: COLORS.indicatorColors.KDJ[0]}}>KDJ 9</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['KDJ_14']}
                                  onChange={(e) => toggleIndicator('KDJ_14', e.target.checked)}>
                            <span style={{
                                color: COLORS.indicatorColors.KDJ[1] || COLORS.indicatorColors.KDJ[0]
                            }}>KDJ 14</span>
                        </Checkbox>
                        <Checkbox checked={visibleIndicators['KDJ_21']}
                                  onChange={(e) => toggleIndicator('KDJ_21', e.target.checked)}>
                            <span style={{
                                color: COLORS.indicatorColors.KDJ[2] || COLORS.indicatorColors.KDJ[0]
                            }}>KDJ 21</span>
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
