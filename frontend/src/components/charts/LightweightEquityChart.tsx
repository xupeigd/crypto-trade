import React, {useEffect, useRef, useState, useCallback} from 'react';
import {Card, Empty, Spin, Typography} from 'antd';
import {botService, TradeBalanceSnapshotResponse} from '../../services/botService';
import {createChart, IChartApi, ISeriesApi, UTCTimestamp, AreaSeries, ColorType} from 'lightweight-charts';

const {Text} = Typography;

interface LightweightEquityChartProps {
    apiKeyId: number;
    onDataLoad?: () => void;
}

const LightweightEquityChart: React.FC<LightweightEquityChartProps> = ({apiKeyId, onDataLoad}) => {
    const [snapshots, setSnapshots] = useState<TradeBalanceSnapshotResponse[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [chartReady, setChartReady] = useState<boolean>(false);
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Area'> | null>(null);

    const fetchSnapshots = useCallback(async () => {
        if (!apiKeyId) return;

        setLoading(true);
        setError('');

        try {
            const response = await botService.getBalanceSnapshots(apiKeyId, 100);

            if (response.success && response.data) {
                const sortedSnapshots = [...response.data].sort((a, b) => a.snapshotTime - b.snapshotTime);
                setSnapshots(sortedSnapshots);
                onDataLoad?.();
            } else {
                setError(response.message || '获取权益数据失败');
            }
        } catch (err) {
            console.error('获取权益数据失败:', err);
            setError('获取权益数据失败');
        } finally {
            setLoading(false);
        }
    }, [apiKeyId, onDataLoad]);

    useEffect(() => {
        const container = chartContainerRef.current;
        if (!container) return;

        const chart = createChart(container, {
            width: Math.max(container.clientWidth, 300),
            height: 216,
            layout: {
                background: {type: ColorType.Solid, color: '#1f1f1f'},
                textColor: '#999999',
                attributionLogo: true,
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
            },
            rightPriceScale: {
                borderVisible: false,
            },
            handleScroll: false,
            handleScale: false,
        });

        const areaSeries = chart.addSeries(AreaSeries, {
            lineColor: '#52c41a',
            lineWidth: 2,
            topColor: 'rgba(82, 196, 26, 0.3)',
            bottomColor: 'rgba(82, 196, 26, 0.05)',
            priceLineVisible: false,
            lastValueVisible: true,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 4,
        });

        chartRef.current = chart;
        seriesRef.current = areaSeries;
        setChartReady(true);

        const handleResize = () => {
            if (container && chartRef.current) {
                chartRef.current.applyOptions({
                    width: container.clientWidth,
                });
            }
        };

        const resizeObserver = new ResizeObserver(handleResize);
        resizeObserver.observe(container);

        return () => {
            resizeObserver.disconnect();
            chart.remove();
            chartRef.current = null;
            seriesRef.current = null;
        };
    }, []);

    useEffect(() => {
        if (!chartReady || !seriesRef.current || snapshots.length === 0) return;

        const equityData = snapshots.map(s => ({
            time: Math.floor(s.snapshotTime / 1000) as UTCTimestamp,
            value: s.availableEquityUsdt + s.usedMarginUsdt + s.unrealizedPnlUsdt,
        }));

        const sortedData = equityData.sort((a, b) => a.time - b.time);

        const uniqueData = sortedData.filter((item, index, arr) => {
            if (index === 0) return true;
            return item.time !== arr[index - 1].time;
        });

        seriesRef.current.setData(uniqueData);
        chartRef.current?.timeScale().fitContent();
    }, [snapshots, chartReady]);

    useEffect(() => {
        fetchSnapshots();
    }, [fetchSnapshots]);

    return (
        <Card
            title={<Text style={{color: '#d9d9d9', fontSize: 14}}>权益走势</Text>}
            size="small"
            style={{
                height: 300,
                backgroundColor: '#1f1f1f',
                border: '1px solid #434343'
            }}
            styles={{
                body: {padding: '12px', height: 240, overflow: 'hidden'}
            }}
        >
            {(loading || error || snapshots.length === 0) && (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%'}}>
                    {loading ? <Spin size="large"/> : (
                        <Empty
                            description={<Text style={{color: '#d9d9d9'}}>{error || '暂无数据'}</Text>}
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                    )}
                </div>
            )}
            <div 
                ref={chartContainerRef} 
                style={{
                    width: '100%', 
                    height: 216, 
                    display: loading || error || snapshots.length === 0 ? 'none' : 'block', 
                    cursor: 'crosshair'
                }} 
            />
        </Card>
    );
};

export default LightweightEquityChart;
