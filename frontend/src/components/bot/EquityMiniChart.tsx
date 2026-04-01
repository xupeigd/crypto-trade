import React, {useEffect, useRef, useState} from 'react';
import {Card, Empty, Spin, Typography} from 'antd';
import {botService, TradeBalanceSnapshotResponse} from '../../services/botService';

const {Text} = Typography;

interface EquityMiniChartProps {
    apiKeyId: number;
    onDataLoad?: () => void;
}

/**
 * 权益迷你图表组件
 * 专用于嵌入BotStatus展开区域，显示总权益的折线趋势
 */
const EquityMiniChart: React.FC<EquityMiniChartProps> = ({apiKeyId, onDataLoad}) => {
    const [snapshots, setSnapshots] = useState<TradeBalanceSnapshotResponse[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const canvasRef = useRef<HTMLCanvasElement>(null);

    // Tooltip状态
    const [tooltipData, setTooltipData] = useState<{
        index: number;
        x: number;
        y: number;
        time: string;
        equity: number;
    } | null>(null);

    /**
     * 获取权益快照数据
     */
    const fetchSnapshots = async () => {
        if (!apiKeyId) return;

        setLoading(true);
        setError('');

        try {
            const response = await botService.getBalanceSnapshots(apiKeyId, 100);

            if (response.success && response.data) {
                // 按时间正序排序(旧→新)
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
    };

    /**
     * 绘制折线图（仅显示总权益）
     */
    const drawChart = () => {
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
        const padding = {top: 20, right: 20, bottom: 30, left: 60};
        const chartWidth = width - padding.left - padding.right;
        const chartHeight = height - padding.top - padding.bottom;

        // 提取总权益数据 = 可用资金 + 已使用保证金 + 未实现盈亏
        const equityData = snapshots.map(s =>
            s.availableEquityUsdt + s.usedMarginUsdt + s.unrealizedPnlUsdt
        );

        // 计算Y轴范围
        const minValue = Math.min(...equityData) * 0.98;
        const maxValue = Math.max(...equityData) * 1.02;
        const valueRange = maxValue - minValue;

        // 绘制背景网格
        ctx.strokeStyle = '#2a2a2a';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const y = padding.top + (chartHeight / 4) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(width - padding.right, y);
            ctx.stroke();
        }

        // 绘制Y轴标签
        ctx.fillStyle = '#999';
        ctx.font = '10px Arial';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (let i = 0; i <= 4; i++) {
            const value = maxValue - (valueRange / 4) * i;
            const y = padding.top + (chartHeight / 4) * i;
            ctx.fillText(value.toFixed(0), padding.left - 8, y);
        }

        // 辅助函数
        const valueToY = (value: number) => {
            return padding.top + chartHeight - ((value - minValue) / valueRange) * chartHeight;
        };

        const indexToX = (index: number) => {
            return padding.left + (chartWidth / (equityData.length - 1)) * index;
        };

        // 绘制区域填充
        ctx.beginPath();
        ctx.fillStyle = 'rgba(82, 196, 26, 0.15)';

        // 上边缘
        equityData.forEach((value, index) => {
            const x = indexToX(index);
            const y = valueToY(value);
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });

        // 下边缘
        const lastIndex = equityData.length - 1;
        ctx.lineTo(indexToX(lastIndex), valueToY(minValue));
        ctx.lineTo(indexToX(0), valueToY(minValue));
        ctx.closePath();
        ctx.fill();

        // 绘制折线
        ctx.beginPath();
        ctx.strokeStyle = '#52c41a';
        ctx.lineWidth = 2;
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';

        equityData.forEach((value, index) => {
            const x = indexToX(index);
            const y = valueToY(value);
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });

        ctx.stroke();

        // 绘制数据点（每隔5个点显示一个，避免密集）
        ctx.fillStyle = '#52c41a';
        for (let i = 0; i < equityData.length; i += 5) {
            const x = indexToX(i);
            const y = valueToY(equityData[i]);
            ctx.beginPath();
            ctx.arc(x, y, 2, 0, Math.PI * 2);
            ctx.fill();
        }

        // 绘制X轴时间标签（只显示首尾）
        ctx.fillStyle = '#999';
        ctx.font = '10px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';

        if (snapshots.length > 0) {
            const startDate = new Date(snapshots[0].snapshotTime);
            const endDate = new Date(snapshots[snapshots.length - 1].snapshotTime);

            ctx.fillText(
                `${startDate.getMonth() + 1}/${startDate.getDate()} ${startDate.getHours()}:${String(startDate.getMinutes()).padStart(2, '0')}`,
                padding.left,
                height - padding.bottom + 8
            );

            ctx.fillText(
                `${endDate.getMonth() + 1}/${endDate.getDate()} ${endDate.getHours()}:${String(endDate.getMinutes()).padStart(2, '0')}`,
                width - padding.right,
                height - padding.bottom + 8
            );
        }

        // 绘制Tooltip
        if (tooltipData !== null) {
            const {x, y, time, equity} = tooltipData;

            // 绘制垂直指示线
            ctx.beginPath();
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
            ctx.lineWidth = 1;
            ctx.setLineDash([5, 5]);
            ctx.moveTo(x, padding.top);
            ctx.lineTo(x, height - padding.bottom);
            ctx.stroke();
            ctx.setLineDash([]);

            // 绘制高亮点
            ctx.beginPath();
            ctx.fillStyle = '#52c41a';
            ctx.arc(x, y, 6, 0, Math.PI * 2);
            ctx.fill();

            // 外圈
            ctx.beginPath();
            ctx.strokeStyle = '#52c41a';
            ctx.lineWidth = 2;
            ctx.arc(x, y, 8, 0, Math.PI * 2);
            ctx.stroke();

            // 绘制Tooltip浮窗
            const tooltipWidth = 140;
            const tooltipHeight = 50;
            const tooltipPadding = 8;
            let tooltipX = x + 15;
            let tooltipY = y - 25;

            // 确保tooltip不超出画布边界
            if (tooltipX + tooltipWidth > width) {
                tooltipX = x - tooltipWidth - 15;
            }
            if (tooltipY + tooltipHeight > height) {
                tooltipY = y - tooltipHeight - 10;
            }
            if (tooltipY < 0) {
                tooltipY = 10;
            }

            // Tooltip背景
            ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';
            ctx.beginPath();
            ctx.roundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 4);
            ctx.fill();

            // Tooltip边框
            ctx.strokeStyle = '#434343';
            ctx.lineWidth = 1;
            ctx.stroke();

            // Tooltip文字
            ctx.fillStyle = '#fff';
            ctx.font = '11px Arial';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'top';

            // 时间
            ctx.fillText(`时间: ${time}`, tooltipX + tooltipPadding, tooltipY + tooltipPadding);

            // 权益值
            ctx.fillStyle = '#52c41a';
            ctx.fillText(`权益: ${equity.toFixed(2)} ₮`, tooltipX + tooltipPadding, tooltipY + tooltipPadding + 20);
        }
    };

    // 初始加载
    useEffect(() => {
        fetchSnapshots();
    }, [apiKeyId]);

    // 数据变化时绘制图表
    useEffect(() => {
        if (!loading && snapshots.length > 0) {
            drawChart();
        }
    }, [snapshots, loading, tooltipData]);

    // 窗口大小变化时重绘
    useEffect(() => {
        const handleResize = () => {
            if (snapshots.length > 0) {
                drawChart();
            }
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, [snapshots]);

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
            {loading ? (
                <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%'}}>
                    <Spin size="large" />
                </div>
            ) : error ? (
                <Empty
                    description={<Text style={{color: '#d9d9d9'}}>{error}</Text>}
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
            ) : snapshots.length === 0 ? (
                <Empty
                    description={<Text style={{color: '#d9d9d9'}}>暂无数据</Text>}
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
            ) : (
                <canvas
                    ref={canvasRef}
                    style={{
                        width: '100%',
                        height: '100%',
                        display: 'block',
                        cursor: 'crosshair'
                    }}
                    onMouseMove={(e) => {
                        const canvas = canvasRef.current;
                        if (!canvas || snapshots.length === 0) return;

                        const rect = canvas.getBoundingClientRect();
                        const mouseX = e.clientX - rect.left;

                        const width = canvas.offsetWidth;
                        const padding = {top: 20, right: 20, bottom: 30, left: 60};
                        const chartWidth = width - padding.left - padding.right;

                        // 计算鼠标对应的数据索引
                        const x = mouseX - padding.left;
                        const index = Math.round((x / chartWidth) * (snapshots.length - 1));

                        // 确保索引在有效范围内
                        if (index >= 0 && index < snapshots.length) {
                            const equityData = snapshots.map(s =>
                                s.availableEquityUsdt + s.usedMarginUsdt + s.unrealizedPnlUsdt
                            );
                            const minValue = Math.min(...equityData) * 0.98;
                            const maxValue = Math.max(...equityData) * 1.02;
                            const valueRange = maxValue - minValue;
                            const chartHeight = canvas.offsetHeight - padding.top - padding.bottom;

                            const pointX = padding.left + (chartWidth / (snapshots.length - 1)) * index;
                            const pointY = padding.top + chartHeight - ((equityData[index] - minValue) / valueRange) * chartHeight;

                            const date = new Date(snapshots[index].snapshotTime);
                            const time = `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;

                            setTooltipData({
                                index,
                                x: pointX,
                                y: pointY,
                                time,
                                equity: equityData[index]
                            });
                        }
                    }}
                    onMouseLeave={() => {
                        setTooltipData(null);
                    }}
                />
            )}
        </Card>
    );
};

export default EquityMiniChart;
