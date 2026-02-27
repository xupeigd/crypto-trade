import React, {useEffect, useRef, useState} from 'react';
import {Empty, List, Modal, Spin, Tag, Typography} from 'antd';
import {botService, TradeBalanceSnapshotResponse} from '../../services/botService';
import {useDrag} from '../../hooks/useDrag';

const {Text} = Typography;

interface EquityChartModalProps {
    visible: boolean;
    onClose: () => void;
    apiKeyId: number;
}

/**
 * 权益图表模态框组件
 * 显示账户余额快照的折线图和列表
 */
const EquityChartModal: React.FC<EquityChartModalProps> = ({
                                                              visible,
                                                              onClose,
                                                              apiKeyId
                                                          }) => {
    const [snapshots, setSnapshots] = useState<TradeBalanceSnapshotResponse[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const canvasRef = useRef<HTMLCanvasElement>(null);
    // 浮窗数据状态
    const [tooltipData, setTooltipData] = useState<{
        index: number;
        snapshot: TradeBalanceSnapshotResponse;
        x: number;
        y: number;
    } | null>(null);

    // 本地存储键名
    const EQUITY_CHART_STORAGE_KEY = 'equity_chart_modal_position';

    // 使用高性能拖拽Hook
    const {dragState, handleMouseDown, resetPosition, modalStyle} = useDrag({
        longPressDelay: 500,
        storageKey: EQUITY_CHART_STORAGE_KEY,
        boundaryPadding: 50
    });

    /**
     * 获取快照数据
     */
    const fetchSnapshots = async () => {
        if (!visible || !apiKeyId) return;

        setLoading(true);
        setError('');

        try {
            const response = await botService.getBalanceSnapshots(apiKeyId, 50);

            if (response.success && response.data) {
                // 按时间正序排序(旧→新)
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
    };

    /**
     * 绘制折线图(堆积折线图 + 鼠标悬浮指示)
     */
    const drawChart = (highlightIndex: number | null = null) => {
        const canvas = canvasRef.current;
        if (!canvas || snapshots.length === 0) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        // 设置canvas尺寸
        const width = canvas.width = canvas.offsetWidth;
        const height = canvas.height = canvas.offsetHeight;

        // 清空画布
        ctx.clearRect(0, 0, width, height);

        // 配置参数
        const padding = {top: 40, right: 20, bottom: 40, left: 80};
        const chartWidth = width - padding.left - padding.right;
        const chartHeight = height - padding.top - padding.bottom;

        // 提取数据
        const availableEquityData = snapshots.map(s => s.availableEquityUsdt);
        const usedMarginData = snapshots.map(s => s.usedMarginUsdt);
        // 计算权益 = 可用资金 + 已使用保证金 + 未实现盈亏
        const calculatedEquityData = snapshots.map(s =>
            s.availableEquityUsdt + s.usedMarginUsdt + s.unrealizedPnlUsdt
        );

        // 计算Y轴范围
        const allValues = [...calculatedEquityData, ...availableEquityData, ...usedMarginData];
        const minValue = Math.min(...allValues) * 0.95; // 留5%余量
        const maxValue = Math.max(...allValues) * 1.05; // 留5%余量
        const valueRange = maxValue - minValue;

        // 绘制背景网格
        ctx.strokeStyle = '#2a2a2a';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 5; i++) {
            const y = padding.top + (chartHeight / 5) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(width - padding.right, y);
            ctx.stroke();
        }

        // 绘制Y轴标签
        ctx.fillStyle = '#999';
        ctx.font = '11px Arial';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (let i = 0; i <= 5; i++) {
            const value = maxValue - (valueRange / 5) * i;
            const y = padding.top + (chartHeight / 5) * i;
            ctx.fillText(value.toFixed(2), padding.left - 10, y);
        }

        // 辅助函数：将数据值转换为Y坐标
        const valueToY = (value: number) => {
            return padding.top + chartHeight - ((value - minValue) / valueRange) * chartHeight;
        };

        // 辅助函数：将索引转换为X坐标
        const indexToX = (index: number) => {
            return padding.left + (chartWidth / (snapshots.length - 1)) * index;
        };

        // 堆积折线图绘制函数
        const drawStackedArea = () => {
            // 堆积数据:底层(保证金) → 中层(可用资金) → 顶层(计算权益)
            const stackedUsedMargin = usedMarginData;
            const stackedAvailable = availableEquityData;
            const stackedCalculated = calculatedEquityData;

            // 绘制三层堆积区域(从下到上:保证金 → 可用 → 计算权益)
            const layers = [
                {data: stackedUsedMargin, color: 'rgba(217, 217, 217, 0.3)', lineColor: '#d9d9d9'}, // 浅灰 - 保证金
                {data: stackedAvailable, color: 'rgba(250, 140, 22, 0.3)', lineColor: '#fa8c16'}, // 橙色 - 可用+保证金
                {data: stackedCalculated, color: 'rgba(82, 196, 26, 0.3)', lineColor: '#52c41a'} // 绿色 - 计算权益
            ];

            layers.forEach(layer => {
                // 绘制区域填充
                ctx.beginPath();
                ctx.fillStyle = layer.color;

                // 上边缘
                layer.data.forEach((value, index) => {
                    const x = indexToX(index);
                    const y = valueToY(value);
                    if (index === 0) {
                        ctx.moveTo(x, y);
                    } else {
                        ctx.lineTo(x, y);
                    }
                });

                // 下边缘(反向到X轴)
                const lastIndex = layer.data.length - 1;
                ctx.lineTo(indexToX(lastIndex), valueToY(0));
                ctx.lineTo(indexToX(0), valueToY(0));
                ctx.closePath();
                ctx.fill();

                // 绘制折线
                ctx.beginPath();
                ctx.strokeStyle = layer.lineColor;
                ctx.lineWidth = 2;
                ctx.lineJoin = 'round';
                ctx.lineCap = 'round';

                layer.data.forEach((value, index) => {
                    const x = indexToX(index);
                    const y = valueToY(value);
                    if (index === 0) {
                        ctx.moveTo(x, y);
                    } else {
                        ctx.lineTo(x, y);
                    }
                });

                ctx.stroke();

                // 绘制数据点
                ctx.fillStyle = layer.lineColor;
                layer.data.forEach((value, index) => {
                    const x = indexToX(index);
                    const y = valueToY(value);
                    ctx.beginPath();
                    ctx.arc(x, y, 3, 0, Math.PI * 2);
                    ctx.fill();
                });
            });
        };

        // 绘制堆积折线图
        drawStackedArea();

        // 如果有高亮索引,绘制垂直指示线和高亮点
        if (highlightIndex !== null && highlightIndex >= 0 && highlightIndex < snapshots.length) {
            const x = indexToX(highlightIndex);

            // 绘制垂直指示线
            ctx.beginPath();
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
            ctx.lineWidth = 1;
            ctx.setLineDash([5, 5]);
            ctx.moveTo(x, padding.top);
            ctx.lineTo(x, height - padding.bottom);
            ctx.stroke();
            ctx.setLineDash([]);

            // 绘制三个高亮点
            const highlightData = [
                {value: usedMarginData[highlightIndex], color: '#d9d9d9'},
                {value: availableEquityData[highlightIndex], color: '#fa8c16'},
                {value: calculatedEquityData[highlightIndex], color: '#52c41a'}
            ];

            highlightData.forEach(point => {
                const y = valueToY(point.value);
                ctx.beginPath();
                ctx.fillStyle = point.color;
                ctx.arc(x, y, 6, 0, Math.PI * 2); // 大一点的高亮点
                ctx.fill();

                // 外圈
                ctx.beginPath();
                ctx.strokeStyle = point.color;
                ctx.lineWidth = 2;
                ctx.arc(x, y, 8, 0, Math.PI * 2);
                ctx.stroke();
            });
        }

        // 绘制X轴时间标签(只显示首尾和中间)
        ctx.fillStyle = '#999';
        ctx.font = '10px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';

        const timeLabelIndices = [0, Math.floor(snapshots.length / 2), snapshots.length - 1];
        timeLabelIndices.forEach(index => {
            if (index >= 0 && index < snapshots.length) {
                const x = indexToX(index);
                const date = new Date(snapshots[index].snapshotTime);
                const timeStr = `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}`;
                ctx.fillText(timeStr, x, height - padding.bottom + 10);
            }
        });

        // 绘制图例
        const legendY = 15;
        const legendSpacing = 100;
        const startX = padding.left;

        const legends = [
            {color: '#52c41a', label: '计算权益'},
            {color: '#fa8c16', label: '可用资金'},
            {color: '#d9d9d9', label: '已使用保证金'}
        ];

        legends.forEach((legend, index) => {
            const x = startX + index * legendSpacing;

            // 绘制色块
            ctx.fillStyle = legend.color;
            ctx.fillRect(x, legendY - 6, 12, 12);

            // 绘制文字
            ctx.fillStyle = '#ddd';
            ctx.font = '12px Arial';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            ctx.fillText(legend.label, x + 18, legendY);
        });

        // 绘制坐标轴
        ctx.strokeStyle = '#434343';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(padding.left, padding.top);
        ctx.lineTo(padding.left, height - padding.bottom);
        ctx.lineTo(width - padding.right, height - padding.bottom);
        ctx.stroke();
    };

    /**
     * 鼠标移动事件处理
     */
    const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
        const canvas = canvasRef.current;
        if (!canvas || snapshots.length === 0) return;

        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        // 配置参数(需要与drawChart保持一致)
        const padding = {top: 40, right: 20, bottom: 40, left: 80};
        const chartWidth = canvas.offsetWidth - padding.left - padding.right;

        // 检查是否在图表区域内
        if (x < padding.left || x > canvas.offsetWidth - padding.right) {
            setTooltipData(null);
            drawChart(null);
            return;
        }

        // 计算最近的数据点索引
        const xInChart = x - padding.left;
        const index = Math.round((xInChart / chartWidth) * (snapshots.length - 1));

        if (index >= 0 && index < snapshots.length) {
            // 重绘Canvas,显示高亮
            drawChart(index);

            // 更新浮窗数据
            setTooltipData({
                index,
                snapshot: snapshots[index],
                x: e.clientX,
                y: e.clientY
            });
        }
    };

    /**
     * 鼠标离开事件处理
     */
    const handleMouseLeave = () => {
        setTooltipData(null);
        // 重绘Canvas,移除高亮
        drawChart(null);
    };

    /**
     * Modal关闭处理
     */
    const handleClose = () => {
        setSnapshots([]);
        setError('');
        resetPosition();
        onClose();
    };

    /**
     * 监听visible变化,获取数据
     */
    useEffect(() => {
        if (visible) {
            fetchSnapshots();
        }
    }, [visible, apiKeyId]);

    /**
     * 数据变化后绘制图表
     */
    useEffect(() => {
        if (snapshots.length > 0 && visible) {
            // 延迟绘制,确保DOM已渲染
            setTimeout(() => {
                drawChart();
            }, 100);
        }
    }, [snapshots, visible]);

    /**
     * 格式化时间
     */
    const formatTime = (timestamp: number) => {
        const date = new Date(timestamp);
        return `${date.getMonth() + 1}/${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
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
                {/* 左栏：折线图 (75%) */}
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
                            <canvas
                                ref={canvasRef}
                                onMouseMove={handleMouseMove}
                                onMouseLeave={handleMouseLeave}
                                style={{
                                    width: '100%',
                                    height: '100%',
                                    display: 'block'
                                }}
                            />
                        )}
                    </div>
                </div>

                {/* 右栏：列表 (25%) */}
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
                                            {/* 第一行: 计算权益 | 总权益 */}
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

                                            {/* 第二行: 可用 | 保证金 */}
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

                                            {/* 第三行: 未结盈亏 | (空) */}
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

        {/* 浮窗组件 */}
        {tooltipData && (
            <div
                style={{
                    position: 'fixed',
                    left: tooltipData.x + 15,
                    top: tooltipData.y + 15,
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343',
                    borderRadius: '6px',
                    padding: '12px 16px',
                    zIndex: 9999,
                    pointerEvents: 'none',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.5)',
                    minWidth: '200px'
                }}
            >
                <div style={{marginBottom: '8px'}}>
                    <div style={{fontSize: '12px', color: '#999', marginBottom: '4px'}}>
                        时间
                    </div>
                    <div style={{fontSize: '13px', color: '#ddd', fontWeight: 'bold'}}>
                        {formatTime(tooltipData.snapshot.snapshotTime)}
                    </div>
                </div>

                <div style={{display: 'flex', flexDirection: 'column', gap: '4px'}}>
                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                        <span style={{fontSize: '12px', color: '#999'}}>计算权益</span>
                        <span style={{fontSize: '13px', fontWeight: 'bold', color: '#1677ff'}}>
                            {(tooltipData.snapshot.availableEquityUsdt + tooltipData.snapshot.usedMarginUsdt + tooltipData.snapshot.unrealizedPnlUsdt).toFixed(2)}
                        </span>
                    </div>

                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                        <span style={{fontSize: '12px', color: '#999'}}>可用资金</span>
                        <span style={{fontSize: '13px', fontWeight: 'bold', color: '#52c41a'}}>
                            {tooltipData.snapshot.availableEquityUsdt.toFixed(2)}
                        </span>
                    </div>

                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                        <span style={{fontSize: '12px', color: '#999'}}>保证金</span>
                        <span style={{fontSize: '13px', fontWeight: 'bold', color: '#ff4d4f'}}>
                            {tooltipData.snapshot.usedMarginUsdt.toFixed(2)}
                        </span>
                    </div>

                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                        <span style={{fontSize: '12px', color: '#999'}}>未结盈亏</span>
                        <span style={{
                            fontSize: '13px',
                            fontWeight: 'bold',
                            color: tooltipData.snapshot.unrealizedPnlUsdt >= 0 ? '#52c41a' : '#ff4d4f'
                        }}>
                            {tooltipData.snapshot.unrealizedPnlUsdt.toFixed(2)}
                        </span>
                    </div>
                </div>
            </div>
        )}
        </>
    );
};

export default EquityChartModal;
