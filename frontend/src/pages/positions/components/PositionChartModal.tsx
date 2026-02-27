import React, {useEffect, useMemo, useState} from 'react';
import {Modal, Space, Spin, message} from 'antd';
import {LineChartOutlined} from '@ant-design/icons';
import {klineBarsFromCandles, KLineChart} from '../../../components/charts/KLineChart';
import {tradingService} from '../../../services/tradingService';
import {OkxPosition} from '../../../types/okxPosition';

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
    const bars = useMemo(() => klineBarsFromCandles(chartData.candles || []), [chartData.candles]);

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
                                // 后端返回的是倒序(最新→最旧),我们需要反转成升序(最旧→最新)
                                // 然后再按timestamp排序,确保数据完全按时间升序排列
                                const reversedValues = [...indicatorData.values].reverse()
                                    .sort((a, b) => a.timestamp - b.timestamp);

                                formattedIndicators[indicator] = {
                                    success: true,
                                    message: '',
                                    data: {
                                        ...indicatorData,
                                        values: reversedValues  // 使用反转并排序后的数组
                                    }
                                };
                            }
                        });
                    }

                    // BOLL数据转换完成日志
                    if (formattedIndicators['BOLL']) {
                        console.log('[PositionChartModal] BOLL数据转换完成:', {
                            success: formattedIndicators['BOLL'].success,
                            valuesLength: formattedIndicators['BOLL'].data?.values?.length,
                            firstValue: formattedIndicators['BOLL'].data?.values?.[0],
                            lastValue: formattedIndicators['BOLL'].data?.values?.[formattedIndicators['BOLL'].data?.values?.length - 1],
                            hasMultiPeriod: !!formattedIndicators['BOLL'].data?.values?.[0]?.multiPeriodValues,
                            multiPeriodKeys: formattedIndicators['BOLL'].data?.values?.[0]?.multiPeriodValues ?
                                Object.keys(formattedIndicators['BOLL'].data.values[0].multiPeriodValues) : []
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

                    // 按时间戳正序排序(从旧到新),确保K线图正确展示(最旧的在左侧,最新的在右侧)
                    candlestickData.sort((a, b) => a.timestamp - b.timestamp);

                    console.log('[PositionChartModal] K线数据转换完成:');
                    console.log('  - 转换后K线数量:', candlestickData.length);
                    console.log('  - 第一根K线:', candlestickData[0]);
                    console.log('  - 最后一根K线:', candlestickData[candlestickData.length - 1]);
                    console.log('[PositionChartModal] 技术指标数据转换完成:');
                    console.log('  - 指标数量:', Object.keys(formattedIndicators).length);
                    console.log('  - 指标列表:', Object.keys(formattedIndicators));
                    Object.keys(formattedIndicators).forEach(key => {
                        const ind = formattedIndicators[key];
                        console.log(`  - ${key}:`, {
                            success: ind.success,
                            valuesLength: ind.data?.values?.length || 0
                        });
                    });

                    setChartData({
                        candles: candlestickData,
                        indicators: formattedIndicators,
                        markPrice: response.markPrice || null
                    });
                    console.log('[PositionChartModal] chartData状态已更新');
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
                            <KLineChart
                                symbol={position.instId}
                                period="1h"
                                data={bars}
                                loading={loading}
                                markPrice={currentMarkPrice || chartData.markPrice}
                                indicators={chartData.indicators}
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
