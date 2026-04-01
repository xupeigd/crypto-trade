import React, {useEffect, useRef, useState, useCallback} from 'react';
import {Empty, List, Modal, Spin, Tag, Typography} from 'antd';
import {botService, TradeBalanceSnapshotResponse} from '../../services/botService';
import {useDrag} from '../../hooks/useDrag';
import {createChart, IChartApi, ISeriesApi, UTCTimestamp, LineSeries, ColorType} from 'lightweight-charts';

const {Text} = Typography;

interface EquityChartModalProps {
    visible: boolean;
    onClose: () => void;
    apiKeyId: number;
}

const EquityChartModal: React.FC<EquityChartModalProps> = ({
                                                              visible,
                                                              onClose,
                                                              apiKeyId
                                                          }) => {
    const [snapshots, setSnapshots] = useState<TradeBalanceSnapshotResponse[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [chartReady, setChartReady] = useState<boolean>(false);
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesMarginRef = useRef<ISeriesApi<'Line'> | null>(null);
    const seriesAvailableRef = useRef<ISeriesApi<'Line'> | null>(null);
    const seriesEquityRef = useRef<ISeriesApi<'Line'> | null>(null);

    const EQUITY_CHART_STORAGE_KEY = 'equity_chart_modal_position';

    const {dragState, handleMouseDown, resetPosition, modalStyle} = useDrag({
        longPressDelay: 500,
        storageKey: EQUITY_CHART_STORAGE_KEY,
        boundaryPadding: 50
    });

    const fetchSnapshots = useCallback(async () => {
        if (!visible || !apiKeyId) return;

        setLoading(true);
        setError('');

        try {
            const response = await botService.getBalanceSnapshots(apiKeyId, 50);

            if (response.success && response.data) {
                const sortedSnapshots = [...response.data].sort((a, b) => a.snapshotTime - b.snapshotTime);
                setSnapshots(sortedSnapshots);
            } else {
                setError(response.message || '获取快照数据失败');
            }
        } catch (err) {
            console.error('获取快照数据失败:', err);
            setError('获取快照数据失败');
        } finally {
            setLoading(false);
        }
    }, [visible, apiKeyId]);

    useEffect(() => {
        if (!chartContainerRef.current) return;

        const container = chartContainerRef.current;

        const chart = createChart(container, {
            width: container.clientWidth || 800,
            height: 500,
            layout: {
                background: {type: ColorType.Solid, color: '#141414'},
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
        });

        const marginSeries = chart.addSeries(LineSeries, {
            color: 'rgba(217, 217, 217, 0.5)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
            crosshairMarkerVisible: false,
        });

        const availableSeries = chart.addSeries(LineSeries, {
            color: 'rgba(250, 140, 22, 0.5)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
            crosshairMarkerVisible: false,
        });

        const equitySeries = chart.addSeries(LineSeries, {
            color: 'rgba(82, 196, 26, 0.8)',
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: true,
            crosshairMarkerVisible: true,
            crosshairMarkerRadius: 4,
        });

        chartRef.current = chart;
        seriesMarginRef.current = marginSeries;
        seriesAvailableRef.current = availableSeries;
        seriesEquityRef.current = equitySeries;
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
            seriesMarginRef.current = null;
            seriesAvailableRef.current = null;
            seriesEquityRef.current = null;
        };
    }, []);

    useEffect(() => {
        if (!visible) return;

        const timer = setTimeout(() => {
            if (!chartContainerRef.current) {
                console.log('[EquityModal] container still null after delay');
                return;
            }

            console.log('[EquityModal] creating chart, container size:', chartContainerRef.current.clientWidth, chartContainerRef.current.clientHeight);

            const container = chartContainerRef.current;

            const chart = createChart(container, {
                width: container.clientWidth || 800,
                height: 500,
                layout: {
                    background: {type: ColorType.Solid, color: '#141414'},
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
            });

            const marginSeries = chart.addSeries(LineSeries, {
                color: 'rgba(217, 217, 217, 0.5)',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
                crosshairMarkerVisible: false,
            });

            const availableSeries = chart.addSeries(LineSeries, {
                color: 'rgba(250, 140, 22, 0.5)',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: false,
                crosshairMarkerVisible: false,
            });

            const equitySeries = chart.addSeries(LineSeries, {
                color: 'rgba(82, 196, 26, 0.8)',
                lineWidth: 2,
                priceLineVisible: false,
                lastValueVisible: true,
                crosshairMarkerVisible: true,
                crosshairMarkerRadius: 4,
            });

            chartRef.current = chart;
            seriesMarginRef.current = marginSeries;
            seriesAvailableRef.current = availableSeries;
            seriesEquityRef.current = equitySeries;
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
                seriesMarginRef.current = null;
                seriesAvailableRef.current = null;
                seriesEquityRef.current = null;
                setChartReady(false);
            };
        }, 100);

        return () => clearTimeout(timer);
    }, [visible]);

    useEffect(() => {
        if (!chartReady || !seriesEquityRef.current || snapshots.length === 0) return;

        const marginData = snapshots.map(s => ({
            time: Math.floor(s.snapshotTime / 1000) as UTCTimestamp,
            value: s.usedMarginUsdt,
        }));

        const availableData = snapshots.map(s => ({
            time: Math.floor(s.snapshotTime / 1000) as UTCTimestamp,
            value: s.availableEquityUsdt + s.usedMarginUsdt,
        }));

        const equityData = snapshots.map(s => ({
            time: Math.floor(s.snapshotTime / 1000) as UTCTimestamp,
            value: s.availableEquityUsdt + s.usedMarginUsdt + s.unrealizedPnlUsdt,
        }));

        const sortedMargin = marginData.sort((a, b) => a.time - b.time);
        const sortedAvailable = availableData.sort((a, b) => a.time - b.time);
        const sortedEquity = equityData.sort((a, b) => a.time - b.time);

        const unique = (arr: typeof sortedMargin) => {
            return arr.filter((item, index, a) => index === 0 || item.time !== a[index - 1].time);
        };

        seriesMarginRef.current?.setData(unique(sortedMargin));
        seriesAvailableRef.current?.setData(unique(sortedAvailable));
        seriesEquityRef.current?.setData(unique(sortedEquity));
        chartRef.current?.timeScale().fitContent();
    }, [snapshots, chartReady]);

    useEffect(() => {
        if (visible) {
            fetchSnapshots();
        }
    }, [visible, apiKeyId, fetchSnapshots]);

    const formatTime = (timestamp: number) => {
        const date = new Date(timestamp);
        return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
    };

    const handleClose = () => {
        setSnapshots([]);
        setError('');
        resetPosition();
        onClose();
    };

    return (
        <>
        <Modal
            title={
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        cursor: dragState.isDragging ? 'grabbing' : 'grab',
                        userSelect: 'none'
                    }}
                    onMouseDown={handleMouseDown}
                >
                    <span>账户权益</span>
                    {!dragState.isDragging && (
                        <span style={{
                            fontSize: '12px',
                            color: '#999',
                            opacity: 0.7
                        }}>
                        </span>
                    )}
                </div>
            }
            open={visible}
            onCancel={handleClose}
            footer={null}
            width={1200}
            styles={{
                mask: {backgroundColor: 'rgba(0, 0, 0, 0.7)'},
                content: {
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343',
                    borderRadius: '8px',
                    ...modalStyle
                },
                body: {
                    padding: '16px',
                    backgroundColor: '#1f1f1f',
                    height: '600px',
                    cursor: dragState.isDragging ? 'grabbing' : 'default'
                }
            }}
        >
            <div style={{width: '100%', height: '100%', display: 'flex', gap: '16px'}}>
                <div style={{flex: 3, display: 'flex', flexDirection: 'column'}}>
                    <div
                        style={{
                            flex: 1,
                            backgroundColor: '#141414',
                            border: '1px solid #434343',
                            borderRadius: '4px',
                            padding: '16px',
                            position: 'relative'
                        }}
                    >
                        {loading ? (
                            <div style={{
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                height: '100%'
                            }}>
                                <Spin size="large">
                                    <div style={{marginTop: 8, color: '#999'}}>正在加载快照数据...</div>
                                </Spin>
                            </div>
                        ) : error ? (
                            <div style={{
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                height: '100%',
                                color: '#ff4d4f'
                            }}>
                                {error}
                            </div>
                        ) : snapshots.length === 0 ? (
                            <div style={{
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                height: '100%'
                            }}>
                                <Empty
                                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                                    description="暂无快照数据"
                                    style={{color: '#999'}}
                                />
                            </div>
                        ) : (
                            <div
                                ref={chartContainerRef}
                                style={{
                                    width: '100%',
                                    height: '100%',
                                    display: 'block'
                                }}
                            />
                        )}
                    </div>
                </div>

                <div style={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    backgroundColor: '#141414',
                    border: '1px solid #434343',
                    borderRadius: '4px',
                    overflow: 'hidden'
                }}>
                    <div style={{
                        padding: '12px 16px',
                        borderBottom: '1px solid #434343',
                        backgroundColor: '#1a1a1a'
                    }}>
                        <Text style={{color: '#ddd', fontWeight: 'bold'}}>最近20条快照</Text>
                    </div>
                    <div style={{flex: 1, overflow: 'auto'}}>
                        <List
                            size="small"
                            dataSource={snapshots.slice(-20).reverse()}
                            renderItem={(item) => (
                                <List.Item
                                    style={{
                                        borderBottom: '1px solid #2a2a2a',
                                        padding: '8px 12px'
                                    }}
                                >
                                    <div style={{width: '100%'}}>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            marginBottom: '4px'
                                        }}>
                                            <Text style={{color: '#999', fontSize: '11px'}}>
                                                {formatTime(item.snapshotTime)}
                                            </Text>
                                            <Tag
                                                color={item.source === 'INITIAL' ? 'blue' : 'orange'}
                                                style={{margin: 0, fontSize: '10px'}}
                                            >
                                                {item.source}
                                            </Tag>
                                        </div>
                                        <div style={{
                                            display: 'grid',
                                            gridTemplateColumns: '1fr 1fr',
                                            gap: '4px 12px'
                                        }}>
                                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                                <Text style={{color: '#999', fontSize: '11px'}}>计算权益</Text>
                                                <Text style={{
                                                    color: '#1677ff',
                                                    fontSize: '12px',
                                                    fontWeight: 'bold'
                                                }}>
                                                    {(item.availableEquityUsdt + item.usedMarginUsdt + item.unrealizedPnlUsdt).toFixed(2)}
                                                </Text>
                                            </div>
                                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                                <Text style={{color: '#999', fontSize: '11px'}}>总权益</Text>
                                                <Text style={{
                                                    color: '#1677ff',
                                                    fontSize: '12px',
                                                    fontWeight: 'bold'
                                                }}>
                                                    {item.totalEquityUsdt.toFixed(2)}
                                                </Text>
                                            </div>

                                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                                <Text style={{color: '#999', fontSize: '11px'}}>可用</Text>
                                                <Text style={{color: '#52c41a', fontSize: '11px'}}>
                                                    {item.availableEquityUsdt.toFixed(2)}
                                                </Text>
                                            </div>
                                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                                <Text style={{color: '#999', fontSize: '11px'}}>保证金</Text>
                                                <Text style={{color: '#ff4d4f', fontSize: '11px'}}>
                                                    {item.usedMarginUsdt.toFixed(2)}
                                                </Text>
                                            </div>

                                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                                <Text style={{color: '#999', fontSize: '11px'}}>未结盈亏</Text>
                                                <Text style={{
                                                    color: item.unrealizedPnlUsdt >= 0 ? '#52c41a' : '#ff4d4f',
                                                    fontSize: '11px',
                                                    fontWeight: 'bold'
                                                }}>
                                                    {item.unrealizedPnlUsdt.toFixed(2)}
                                                </Text>
                                            </div>
                                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                                <Text style={{color: '#999', fontSize: '11px'}}></Text>
                                                <Text style={{fontSize: '11px'}}></Text>
                                            </div>
                                        </div>
                                    </div>
                                </List.Item>
                            )}
                        />
                    </div>
                </div>
            </div>
        </Modal>
        </>
    );
};

export default EquityChartModal;
