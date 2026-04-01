import React, {useEffect, useMemo, useState} from 'react';
import {Modal, Space, Spin, message} from 'antd';
import {LineChartOutlined} from '@ant-design/icons';
import {KLineBar, LightweightCandlestickChart} from '../../../components/charts/LightweightCandlestickChart';
import {tradingService, IndicatorDataPoint} from '../../../services/tradingService';
import {OkxPosition} from '../../../types/okxPosition';

/**
 * 将 CandlestickData[] 转换为 KLineBar[] 格式
 */
const candlestickDataToKLineBars = (candles: any[]): KLineBar[] => {
    return candles
        .filter(c => c && Number.isFinite(c.timestamp))
        .map(c => ({
            time: c.timestamp,
            open: c.open,
            high: c.high,
            low: c.low,
            close: c.close,
            volume: c.volume || 0,
            confirmed: c.confirm === 1
        }))
        .sort((a, b) => a.time - b.time);
};

/**
 * 将后端指标数据格式转换为 LightweightCandlestickChart 所需的扁平化格式
 */
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

        // 特殊处理 BOLL：检查是否有直接的 upperBand/middleBand/lowerBand 字段
        if (key === 'BOLL') {
            const hasDirectBollFields = values.some((v: any) =>
                v.upperBand !== undefined || v.middleBand !== undefined || v.lowerBand !== undefined
            );

            if (hasDirectBollFields) {
                const period = indicator?.data?.period || indicator?.period || 20;
                const seriesKey = `BOLL_boll_${period}`;
                result[seriesKey] = values.map((v: any) => ({
                    time: v.timestamp,
                    upper: v.upperBand ?? undefined,
                    middle: v.middleBand ?? undefined,
                    lower: v.lowerBand ?? undefined,
                }));
                return;
            }
        }

        values.forEach((v: IndicatorDataPoint) => {
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

    // 按时间排序
    Object.values(result).forEach(arr => arr.sort((a, b) => a.time - b.time));
    return result;
};

/**
 * 仓位K线图弹窗组件
 *
 * 功能:
 * - 展示指定合约的K线图
 * - 显示持仓参考线(开仓价、止盈价、止损价)
 * - 顶部显示仓位关键信息
 * - 默认技术指标: EMA5/20/30, RSI5/15/30, BOLL12
 */
interface PositionChartModalProps {
    visible: boolean;
    position: OkxPosition | any | null;  // 支持多种仓位类型
    onClose: () => void;
    apiKeyId: number;
}

interface ChartData {
    candles: any[];
    indicators: any;
    markPrice: number | null;
}

const PositionChartModal: React.FC<PositionChartModalProps> = ({visible, position, onClose, apiKeyId}) => {
    const [loading, setLoading] = useState(false);
    const [chartData, setChartData] = useState<ChartData>({
        candles: [],
        indicators: {},
        markPrice: null
    });
    const [currentMarkPrice, setCurrentMarkPrice] = useState<number | null>(null); // 实时标记价格
    const bars = useMemo(() => candlestickDataToKLineBars(chartData.candles || []), [chartData.candles]);

    // 转换指标数据为新格式
    const transformedIndicators = useMemo(() => {
        return transformIndicatorsForChart(chartData.indicators);
    }, [chartData.indicators]);

    // 计算强平价格(简化版)
    const calculateLiquidationPrice = (pos: OkxPosition): number => {
        if (!pos.avgPx || !pos.lever) return 0;

        const avgPrice = Number(pos.avgPx);
        const leverage = Number(pos.lever);
        const posSide = pos.posSide;

        // 简化的强平价格计算公式
        let liquidationPrice: number;
        if ('long' === posSide) {
            liquidationPrice = avgPrice * (1 - 0.9 / leverage);
        } else {
            liquidationPrice = avgPrice * (1 + 0.9 / leverage);
        }

        return liquidationPrice;
    };

    // 获取K线数据
    useEffect(() => {
        if (!visible || !position) {
            return;
        }

        const fetchChartData = async () => {
            setLoading(true);
            try {
                console.log('[PositionChartModal] ==================== 开始获取K线数据 ====================');
                console.log('[PositionChartModal] 合约:', position.instId);
                console.log('[PositionChartModal] 请求参数:', {
                    instId: position.instId,
                    timeframe: '1h',
                    limit: 120,
                    indicators: ['EMA', 'RSI', 'BOLL', 'KDJ'],
                    emaPeriods: [5, 20, 30],
                    rsiPeriods: [5, 15, 30],
                    bollParams: ['20_2.0'],
                    kdjPeriods: [9, 14, 21]
                });

                // 调用完整的图表数据接口
                const response = await tradingService.getCompleteChartData({
                    instId: position.instId,
                    apiKeyId: apiKeyId,
                    timeframe: '1h',
                    limit: 120,
                    indicators: ['EMA', 'RSI', 'BOLL', 'KDJ'],
                    emaPeriods: [5, 20, 30],
                    rsiPeriods: [5, 15, 30],
                    bollParams: ['20_2.0'],
                    kdjPeriods: [9, 14, 21]
                });

                console.log('[PositionChartModal] API原始响应:', response);
                console.log('[PositionChartModal] response.candles存在?', !!response?.candles);
                console.log('[PositionChartModal] response.candles长度:', response?.candles?.length || 0);

                if (response && response.candles) {
                    // 转换indicators数据格式为TechnicalIndicatorData
                    const formattedIndicators: {[key: string]: any} = {};
                    if (response.indicators) {
                        Object.keys(response.indicators).forEach(indicator => {
                            const indicatorData = response.indicators![indicator];
                            if (indicatorData && indicatorData.values) {
                                // 反转values数组,使其与K线数据的升序保持一致
                                const reversedValues = [...indicatorData.values].reverse()
                                    .sort((a, b) => a.timestamp - b.timestamp);

                                formattedIndicators[indicator] = {
                                    success: true,
                                    message: '',
                                    data: {
                                        ...indicatorData,
                                        values: reversedValues
                                    }
                                };
                            }
                        });
                    }

                    // 转换K线数据格式,确保confirm字段正确传递
                    const candlestickData = response.candles.map((candle: any) => ({
                        timestamp: candle.timestamp,
                        open: candle.open,
                        high: candle.high,
                        low: candle.low,
                        close: candle.close,
                        volume: Number(candle.volume || candle.vol || candle.volumeCcy || candle.volCcyQuote || 0),
                        confirm: candle.confirm === undefined || candle.confirm === null
                            ? 1
                            : (Number.isFinite(Number(candle.confirm)) ? Number(candle.confirm) : 1),
                    }));

                    // 按时间戳正序排序
                    candlestickData.sort((a, b) => a.timestamp - b.timestamp);

                    console.log('[PositionChartModal] K线数据转换完成:');
                    console.log('  - 转换后K线数量:', candlestickData.length);
                    console.log('  - 第一根K线:', candlestickData[0]);
                    console.log('  - 最后一根K线:', candlestickData[candlestickData.length - 1]);
                    console.log('[PositionChartModal] 技术指标数据转换完成:');
                    console.log('  - 指标数量:', Object.keys(formattedIndicators).length);
                    console.log('  - 指标列表:', Object.keys(formattedIndicators));

                    setChartData({
                        candles: candlestickData,
                        indicators: formattedIndicators,
                        markPrice: response.markPrice || null
                    });
                    console.log('[PositionChartModal] ==================== K线数据获取完成 ====================');
                } else {
                    message.error('K线数据获取失败');
                }
            } catch (error) {
                console.error('[PositionChartModal] 获取K线数据失败:', error);
                console.error('[PositionChartModal] 错误详情:', (error as Error).message);
                message.error('获取K线数据失败: ' + (error as Error).message);
            } finally {
                console.log('[PositionChartModal] setLoading(false) - loading状态结束');
                setLoading(false);
            }
        };

        fetchChartData();
    }, [visible, position]);

    // 实时更新标记价格 (每3秒更新一次)
    useEffect(() => {
        if (!visible || !position) {
            return;
        }

        const updateMarkPrice = async () => {
            try {
                console.log('[PositionChartModal] 更新标记价格 - instId:', position.instId);
                const response = await tradingService.getMarkPrice(position.instId, apiKeyId);

                if (response?.success && response?.markPrice) {
                    const newMarkPrice = Number(response.markPrice);
                    console.log('[PositionChartModal] 标记价格已更新:', {
                        instId: position.instId,
                        markPrice: newMarkPrice,
                        timestamp: new Date().toISOString()
                    });
                    setCurrentMarkPrice(newMarkPrice);
                } else {
                    console.warn('[PositionChartModal] 标记价格响应格式异常:', response);
                }
            } catch (error) {
                console.error('[PositionChartModal] 获取标记价格失败:', error);
            }
        };

        // 立即执行一次
        updateMarkPrice();

        // 设置定时器，每3秒更新一次
        const intervalId = setInterval(updateMarkPrice, 3000);

        // 清理函数：弹窗关闭时清除定时器
        return () => {
            console.log('[PositionChartModal] 清除标记价格更新定时器');
            clearInterval(intervalId);
        };
    }, [visible, position]);

    if (!position) {
        return null;
    }

    // 计算显示数据
    const liquidationPrice = calculateLiquidationPrice(position);
    const upl = position.upl || 0;
    const uplRatio = position.uplRatio || 0;

    // 提取止盈止损价格(从positionStopLossStrategies中)
    let takeProfitPrice: number | undefined;
    let stopLossPrice: number | undefined;

    const strategies = (position as any).positionStopLossStrategies;
    if (strategies && strategies.length > 0) {
        // 查找全仓止盈止损，如果没有isTotal字段，则使用第一个有效策略
        const totalStrategy = strategies.find((s: any) => s.isTotal) || strategies.find((s: any) => s.valid);
        if (totalStrategy) {
            if ('undefined' !== typeof totalStrategy.tpTriggerPx) {
                takeProfitPrice = Number(totalStrategy.tpTriggerPx);
            }
            if ('undefined' !== typeof totalStrategy.slTriggerPx) {
                stopLossPrice = Number(totalStrategy.slTriggerPx);
            }
        }
    }

    return (
        <Modal
            title={
                <Space style={{fontSize: '16px', fontWeight: 'bold'}}>
                    <LineChartOutlined/>
                    <span>{position.instId}</span>
                    <span style={{
                        fontSize: '12px',
                        padding: '2px 8px',
                        borderRadius: '4px',
                        backgroundColor: 'long' === position.posSide ? 'rgba(82, 196, 26, 0.2)' : 'rgba(255, 77, 79, 0.2)',
                        color: 'long' === position.posSide ? '#52c41a' : '#ff4d4f'
                    }}>
                        {'long' === position.posSide ? '做多' : '做空'}
                    </span>
                </Space>
            }
            open={visible}
            onCancel={onClose}
            footer={null}
            width={1200}
            style={{top: 20}}
            destroyOnHidden
        >
            <div style={{height: '800px'}}>
                {loading ? (
                    <div style={{
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        alignItems: 'center',
                        gap: '12px'
                    }}>
                        <Spin size="large" />
                        <span style={{color: '#999'}}>加载K线数据...</span>
                    </div>
                ) : (
                    <>
                        {/* 仓位信息卡片 */}
                        <div style={{
                            padding: '12px',
                            background: '#2a2a2a',
                            borderRadius: '4px',
                            marginBottom: '12px',
                            display: 'grid',
                            gridTemplateColumns: 'repeat(4, 1fr)',
                            gap: '12px'
                        }}>
                            <div>
                                <div style={{fontSize: '11px', color: '#999', marginBottom: '4px'}}>开仓价</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#1890ff'}}>
                                    {position.avgPx ? Number(position.avgPx).toFixed(2) : '-'}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '11px', color: '#999', marginBottom: '4px'}}>强平价</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#ff7875'}}>
                                    {liquidationPrice > 0 ? liquidationPrice.toFixed(2) : '-'}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '11px', color: '#999', marginBottom: '4px'}}>未结盈亏</div>
                                <div style={{
                                    fontSize: '14px',
                                    fontWeight: 'bold',
                                    color: upl >= 0 ? '#52c41a' : '#ff4d4f'
                                }}>
                                    {upl >= 0 ? '+' : ''}{upl.toFixed(2)}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '11px', color: '#999', marginBottom: '4px'}}>盈亏率</div>
                                <div style={{
                                    fontSize: '14px',
                                    fontWeight: 'bold',
                                    color: uplRatio >= 0 ? '#52c41a' : '#ff4d4f'
                                }}>
                                    {uplRatio >= 0 ? '+' : ''}{uplRatio.toFixed(2)}%
                                </div>
                            </div>
                        </div>

                        {/* K线图 */}
                        <div style={{height: 'calc(100% - 80px)'}}>
                            <LightweightCandlestickChart
                                data={bars}
                                height={700}
                                timeFrame="1h"
                                loading={loading}
                                markPrice={currentMarkPrice || chartData.markPrice}
                                indicators={transformedIndicators}
                                referenceLines={{
                                    avgPx: position.avgPx ? Number(position.avgPx) : undefined,
                                    liquidationPx: liquidationPrice > 0 ? liquidationPrice : undefined,
                                    takeProfitPx: takeProfitPrice,
                                    stopLossPx: stopLossPrice
                                }}
                            />
                        </div>
                    </>
                )}
            </div>
        </Modal>
    );
};

export default PositionChartModal;