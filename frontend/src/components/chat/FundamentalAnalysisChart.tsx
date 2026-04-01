import React, { useEffect, useState, useCallback } from 'react';
import { Spin, message } from 'antd';
import { useMemo } from 'react';
import LightweightCandlestickChart, { KLineBar } from '../charts/LightweightCandlestickChart';
import tradingService from '../../services/tradingService';

/**
 * FundamentalAnalysis JSON 数据结构
 */
export interface FundamentalAnalysisData {
    instId: string;
    type: 'FundamentalAnalysis';
    timeframe: string;
    overall_trend?: string;
    key_patterns?: Array<{
        pattern_name: string;
        direction: string;
        strength: string;
        description: string;
        candles_involved?: string;
    }>;
    supports?: Array<{
        price: number;
        type: string;
        strength: string;
        reason: string;
        candles_start_time?: number;  // 13位毫秒时间戳，水平线起始时间
        candles_end_time?: number;    // 13位毫秒时间戳，水平线结束时间
    }>;
    resistances?: Array<{
        price: number;
        type: string;
        strength: string;
        reason: string;
        breakout_target?: number;
        candles_start_time?: number;  // 13位毫秒时间戳，水平线起始时间
        candles_end_time?: number;    // 13位毫秒时间戳，水平线结束时间
    }>;
    summary?: string;
    risk_level?: string;
}

interface FundamentalAnalysisChartProps {
    data: FundamentalAnalysisData;
    height?: number;
    onBack?: () => void;
}

const FundamentalAnalysisChart: React.FC<FundamentalAnalysisChartProps> = ({
    data,
    height = 400,
    onBack
}) => {
    const [loading, setLoading] = useState(true);
    const [klineData, setKlineData] = useState<KLineBar[]>([]);
    const [error, setError] = useState<string | null>(null);

    // 提取支撑线和阻力线价格（使用 useMemo 缓存避免无限循环）
    // 不传时间范围，让线横贯整个可视范围
    const supportLines = useMemo(() =>
        data.supports?.map((s, index) => ({
            price: s.price,
            label: `S${index + 1}`  // 添加 S 前缀标签
        })) || [],
        [data.supports]
    );
    const resistanceLines = useMemo(() =>
        data.resistances?.map((r, index) => ({
            price: r.price,
            label: `R${index + 1}`  // 添加 R 前缀标签
        })) || [],
        [data.resistances]
    );

    // 缓存 referenceLines 对象，避免每次渲染创建新引用
    const referenceLines = useMemo(() => ({
        supportLines,
        resistanceLines
    }), [supportLines, resistanceLines]);

    // 获取K线数据
    const fetchKlineData = useCallback(async () => {
        if (!data.instId || !data.timeframe) {
            setError('缺少 instId 或 timeframe 参数');
            setLoading(false);
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const result = await tradingService.getCompleteChartData({
                instId: data.instId,
                timeframe: data.timeframe,
                limit: 100,  // 获取最近100根K线
            });

            if (result && result.candles && result.candles.length > 0) {
                // 转换数据格式并按时间升序排序
                const bars: KLineBar[] = result.candles
                    .map((candle: any) => ({
                        time: candle.timestamp,
                        open: candle.open,
                        high: candle.high,
                        low: candle.low,
                        close: candle.close,
                        volume: candle.volume || 0,
                        confirmed: candle.confirm === 1
                    }))
                    .sort((a, b) => a.time - b.time);
                setKlineData(bars);
            } else {
                setError('未获取到K线数据');
            }
        } catch (err: any) {
            console.error('获取K线数据失败:', err);
            setError(err.message || '获取K线数据失败');
        } finally {
            setLoading(false);
        }
    }, [data.instId, data.timeframe]);

    useEffect(() => {
        fetchKlineData();
    }, [fetchKlineData]);

    if (loading) {
        return (
            <div style={{
                height,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#1f1f1f',
                borderRadius: '6px'
            }}>
                <Spin />
            </div>
        );
    }

    if (error) {
        return (
            <div style={{
                height,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#1f1f1f',
                borderRadius: '6px',
                color: '#ff4d4f'
            }}>
                <div>{error}</div>
                <a onClick={fetchKlineData} style={{ marginTop: 8, cursor: 'pointer' }}>
                    重试
                </a>
            </div>
        );
    }

    return (
        <div style={{
            width: '100%',
            position: 'relative'
        }}>
            {/* 顶部信息栏 */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '8px 12px',
                backgroundColor: '#2a2a2a',
                borderRadius: '6px 6px 0 0',
                borderBottom: '1px solid #3a3a3a'
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span style={{ color: '#fff', fontWeight: 500 }}>{data.instId}</span>
                    <span style={{ color: '#999', fontSize: 12 }}>{data.timeframe}</span>
                    {data.overall_trend && (
                        <span style={{ color: '#999', fontSize: 12 }}>({data.overall_trend})</span>
                    )}
                </div>
                {data.risk_level && (
                    <span style={{
                        color: data.risk_level === '高' ? '#ff4d4f' :
                               data.risk_level === '低' ? '#52c41a' : '#faad14',
                        fontSize: 12
                    }}>
                        风险: {data.risk_level}
                    </span>
                )}
            </div>

            {/* K线图 */}
            <div style={{
                position: 'relative',
                backgroundColor: '#1f1f1f',
                borderRadius: '0 0 6px 6px',
                overflow: 'hidden'
            }}>
                {/* 悬浮的原始数据按钮 */}
                {onBack && (
                    <a
                        onClick={onBack}
                        style={{
                            position: 'absolute',
                            top: 8,
                            left: 8,
                            zIndex: 10,
                            color: '#1890ff',
                            backgroundColor: 'rgba(0, 0, 0, 0.6)',
                            padding: '4px 8px',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: 12,
                            textDecoration: 'none'
                        }}
                    >
                        原始数据
                    </a>
                )}
                <LightweightCandlestickChart
                    data={klineData}
                    height={height}
                    timeFrame={data.timeframe}
                    maxVisibleBars={klineData.length || 100}
                    referenceLines={referenceLines}
                    lockRightEdge={true}
                />
            </div>

            {/* 图例说明 */}
            <div style={{
                display: 'flex',
                gap: 16,
                padding: '8px 12px',
                backgroundColor: '#2a2a2a',
                borderRadius: '0 0 6px 6px',
                fontSize: 12
            }}>
                <span style={{ color: '#1890ff' }}>━━ 支撑线 ({supportLines.length})</span>
                <span style={{ color: '#fa8c16' }}>━━ 阻力线 ({resistanceLines.length})</span>
            </div>

            {/* 总结信息 */}
            {data.summary && (
                <div style={{
                    padding: '8px 12px',
                    backgroundColor: '#2a2a2a',
                    marginTop: 4,
                    borderRadius: '6px',
                    fontSize: 12,
                    color: '#999'
                }}>
                    {data.summary}
                </div>
            )}
        </div>
    );
};

export default FundamentalAnalysisChart;