import React, { useEffect, useState } from 'react';
import { Modal, Tabs, Spin, Descriptions, Table, Typography, Tooltip } from 'antd';
import { backtestService, BacktestResultResponse } from '../../services/backtestService';
import './BacktestResultModal.css';

const { Text } = Typography;

interface BacktestResultModalProps {
    taskId: number;
    taskStatus?: string;
    visible: boolean;
    onClose: () => void;
}

// 资金曲线图表组件
const ProfitChart: React.FC<{ data: any[] }> = ({ data }) => {
    const canvasRef = React.useRef<HTMLCanvasElement>(null);
    const containerRef = React.useRef<HTMLDivElement>(null);
    const [tooltip, setTooltip] = React.useState<{ x: number; y: number; date: string; profit: number } | null>(null);

    React.useEffect(() => {
        const canvas = canvasRef.current;
        const container = containerRef.current;
        if (!canvas || !container || !data || data.length === 0) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        // 使用 requestAnimationFrame 确保 canvas 已正确布局
        const draw = () => {
            // 使用容器的实际宽度
            const containerWidth = container.offsetWidth;
            const width = canvas.width = containerWidth * 2;
            const height = canvas.height = 400;
            ctx.scale(2, 2);

            const padding = { top: 20, right: 20, bottom: 40, left: 60 };
            const chartWidth = containerWidth - padding.left - padding.right;
            const chartHeight = 400 - padding.top - padding.bottom;

            // 解析数据并计算累计收益
            const points: { date: string; profit: number; cumProfit: number }[] = [];
            let cumProfit = 0;
            for (const item of data) {
                const date = item.date || item[0];
                const profit = parseFloat(item.profit || item[1] || 0);
                cumProfit += profit;
                points.push({ date, profit, cumProfit });
            }

            if (points.length === 0) return;

            // 计算Y轴范围
            const profits = points.map(p => p.cumProfit);
            const minProfit = Math.min(0, ...profits);
            const maxProfit = Math.max(0, ...profits);
            const profitRange = maxProfit - minProfit || 1;

            // 坐标转换
            const xScale = (i: number) => padding.left + (i / (points.length - 1 || 1) * chartWidth);
            const yScale = (v: number) => padding.top + chartHeight - ((v - minProfit) / profitRange * chartHeight);

            // 绘制
            ctx.clearRect(0, 0, canvas.offsetWidth, canvas.offsetHeight);

            // 绘制零线
            const zeroY = yScale(0);
            ctx.strokeStyle = '#d9d9d9';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(padding.left, zeroY);
            ctx.lineTo(padding.left + chartWidth, zeroY);
            ctx.stroke();

            // 绘制收益曲线
            ctx.beginPath();
            ctx.strokeStyle = '#52c41a';
            ctx.lineWidth = 2;
            points.forEach((p, i) => {
                const x = xScale(i);
                const y = yScale(p.cumProfit);
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            });
            ctx.stroke();

            // 填充区域
            const gradient = ctx.createLinearGradient(0, padding.top, 0, padding.top + chartHeight);
            gradient.addColorStop(0, 'rgba(82, 196, 26, 0.3)');
            gradient.addColorStop(1, 'rgba(255, 77, 79, 0.3)');
            ctx.lineTo(xScale(points.length - 1), zeroY);
            ctx.lineTo(padding.left, zeroY);
            ctx.closePath();
            ctx.fillStyle = gradient;
            ctx.fill();

            // 绘制数据点
            points.forEach((p, i) => {
                const x = xScale(i);
                const y = yScale(p.cumProfit);
                ctx.beginPath();
                ctx.arc(x, y, 3, 0, Math.PI * 2);
                ctx.fillStyle = p.cumProfit >= 0 ? '#52c41a' : '#ff4d4f';
                ctx.fill();
            });

            // X轴标签
            ctx.fillStyle = '#666';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'center';
            const showCount = Math.min(6, points.length);
            for (let i = 0; i < showCount; i++) {
                const idx = Math.floor(i / (showCount - 1) * (points.length - 1));
                const p = points[idx];
                const x = xScale(idx);
                const dateStr = p.date.substring(2, 10);
                ctx.fillText(dateStr, x, canvas.offsetHeight - 8);
            }

            // Y轴标签
            ctx.textAlign = 'right';
            const yLabels = [minProfit, (minProfit + maxProfit) / 2, maxProfit];
            yLabels.forEach(v => {
                const y = yScale(v);
                ctx.fillText(v.toFixed(2), padding.left - 5, y + 4);
            });

            // 鼠标事件
            const handleMouseMove = (e: MouseEvent) => {
                const rect = canvas.getBoundingClientRect();
                const x = e.clientX - rect.left;
                const relX = x - padding.left;
                if (relX < 0 || relX > chartWidth) {
                    setTooltip(null);
                    return;
                }
                const idx = Math.round(relX / chartWidth * (points.length - 1));
                const p = points[Math.min(idx, points.length - 1)];
                setTooltip({ x: e.clientX - rect.left, y: 0, date: p.date, profit: p.cumProfit });
            };
            canvas.onmousemove = handleMouseMove;
            canvas.onmouseleave = () => setTooltip(null);
        };

        requestAnimationFrame(draw);
    }, [data]);

    return (
        <div ref={containerRef} style={{ position: 'relative', height: 400, overflow: 'hidden' }}>
            <canvas ref={canvasRef} style={{ width: '100%', height: 400, display: 'block', cursor: 'crosshair' }} />
            {tooltip && (
                <div style={{
                    position: 'absolute',
                    left: tooltip.x + 10,
                    top: 10,
                    background: 'rgba(0,0,0,0.8)',
                    color: '#fff',
                    padding: '4px 8px',
                    borderRadius: 4,
                    fontSize: 12,
                    pointerEvents: 'none'
                }}>
                    <div>{tooltip.date}</div>
                    <div style={{ color: tooltip.profit >= 0 ? '#52c41a' : '#ff4d4f' }}>
                        累计收益: {tooltip.profit.toFixed(2)}
                    </div>
                </div>
            )}
        </div>
    );
};

const BacktestResultModal: React.FC<BacktestResultModalProps> = ({ taskId, taskStatus, visible, onClose }) => {
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState<BacktestResultResponse | null>(null);
    const isRunning = taskStatus === 'RUNNING';

    // 运行中显示蒙层
    const overlayStyle: React.CSSProperties = isRunning ? {
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(255, 255, 255, 0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10
    } : {};

    // 格式化数字，最多4位小数，超过显示...
    const formatNumber = (val: number): string => {
        if (val === null || val === undefined) return '-';
        const str = val.toFixed(8);
        const dotIndex = str.indexOf('.');
        if (dotIndex === -1) return str;
        const intPart = str.substring(0, dotIndex);
        const decPart = str.substring(dotIndex + 1);
        if (decPart.replace(/0+$/, '').length <= 4) return val.toFixed(4);
        return intPart + '.' + decPart.substring(0, 4) + '...';
    };

    useEffect(() => {
        if (visible && taskId) {
            loadResult();
        } else {
            setResult(null);
        }
    }, [visible, taskId]);

    const loadResult = async () => {
        setLoading(true);
        try {
            const data = await backtestService.getBacktestResult(taskId);
            setResult(data);
        } catch (e) {
            console.error('Failed to load result', e);
        } finally {
            setLoading(false);
        }
    };

    // 计算持仓时长
    const calculateDuration = (openDate: string, closeDate: string) => {
        if (!openDate || !closeDate) return '-';
        const open = new Date(openDate).getTime();
        const close = new Date(closeDate).getTime();
        const diffMs = close - open;
        const diffMins = Math.floor(diffMs / (1000 * 60));
        const days = Math.floor(diffMins / (60 * 24));
        const hours = Math.floor((diffMins % (60 * 24)) / 60);
        const mins = diffMins % 60;
        let result = '';
        if (days > 0) result += `${days}d`;
        if (hours > 0) result += `${hours}h`;
        if (mins > 0 || result === '') result += `${mins}m`;
        return result;
    };

    const tradeColumns = [
        {
            title: '交易对',
            dataIndex: 'pair',
            key: 'pair',
            render: (pair: string, record: any) => {
                const isShort = record.type === '做空';
                const color = isShort ? '#ff4d4f' : '#52c41a';
                return <span style={{ color }}>{pair}</span>;
            }
        },
        {
            title: '开仓时间',
            dataIndex: 'open_date',
            key: 'open_date',
            render: (val: string) => val ? val.replace('+00:00', '').replace('T', ' ').substring(2, 19) : '-'
        },
        {
            title: '持仓时长',
            key: 'duration',
            render: (_: any, record: any) => calculateDuration(record.open_date, record.close_date)
        },
        {
            title: '入场价',
            dataIndex: 'open_rate',
            key: 'open_rate',
            render: (val: number) => (
                <Tooltip title={val}>{formatNumber(val)}</Tooltip>
            )
        },
        {
            title: '出场价',
            dataIndex: 'close_rate',
            key: 'close_rate',
            render: (val: number) => (
                <Tooltip title={val}>{formatNumber(val)}</Tooltip>
            )
        },
        {
            title: '收益',
            dataIndex: 'profit_abs',
            key: 'profit_abs',
            render: (val: number) => (
                <Tooltip title={val}>
                    <span style={{ color: val >= 0 ? '#52c41a' : '#ff4d4f' }}>{formatNumber(val)}</span>
                </Tooltip>
            )
        },
        { 
            title: '收益率', 
            dataIndex: 'profit_ratio', 
            key: 'profit_ratio',
            render: (val: number) => (
                <span style={{ color: val >= 0 ? '#52c41a' : '#ff4d4f' }}>{(val * 100).toFixed(2)}%</span>
            )
        },
    ];

    return (
        <Modal
            title={
                <span className={isRunning ? 'pulse-animation' : ''}>
                    {result?.strategyName || '回测报告'}({taskId})
                </span>
            }
            open={visible}
            onCancel={onClose}
            footer={null}
            width={1000}
            destroyOnHidden
        >
            <Spin spinning={loading}>
                {result && (
                    <>
                        <Descriptions bordered size="small" column={4} style={{ marginBottom: 16 }}>
                            <Descriptions.Item label="Freqtrade配置">{result.freqtradeConfigName || '-'}</Descriptions.Item>
                            <Descriptions.Item label="策略名称">{result.strategyName || '-'}</Descriptions.Item>
                            <Descriptions.Item label="时间范围">{result.timeRange || '-'}</Descriptions.Item>
                            <Descriptions.Item label="时间帧">{result.timeframe || '-'}</Descriptions.Item>
                        </Descriptions>

                        <Descriptions bordered size="small" column={4} style={{ marginBottom: 24 }}>
                            <Descriptions.Item label="总收益 (绝对值)">
                                <Text type={result.totalProfitAbs >= 0 ? 'success' : 'danger'}>
                                    {result.totalProfitAbs}
                                </Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="总收益 (%)">
                                <Text type={result.totalProfitPct >= 0 ? 'success' : 'danger'}>
                                    {result.totalProfitPct}%
                                </Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="胜率">
                                {result.winRate?.toFixed(2) || '0.00'}%
                            </Descriptions.Item>
                            <Descriptions.Item label="总交易数">
                                {result.totalTrades}
                            </Descriptions.Item>
                            <Descriptions.Item label="最大回撤 (绝对值)">
                                <Text type="danger">{result.maxDrawdownAbs}</Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="最大回撤 (%)">
                                <Text type="danger">{result.maxDrawdownPct}%</Text>
                            </Descriptions.Item>
                            <Descriptions.Item label="夏普率">
                                <Text type={(result.sharpeRatio ?? 0) >= 0 ? 'success' : 'danger'}>
                                    {result.sharpeRatio ?? '0'}
                                </Text>
                            </Descriptions.Item>
                        </Descriptions>

                        <Tabs defaultActiveKey="trades" items={[
                            {
                                key: 'trades',
                                label: '交易记录',
                                children: (
                                    <div style={{ position: 'relative' }}>
                                        {isRunning && <div style={overlayStyle}><span>运行中...</span></div>}
                                        <Table
                                            columns={tradeColumns}
                                            dataSource={result.trades || []}
                                            rowKey={(record: any) => record.id || Math.random().toString()}
                                            size="small"
                                            scroll={{ y: 400 }}
                                            pagination={false}
                                        />
                                    </div>
                                )
                            },
                            {
                                key: 'chart',
                                label: '资金曲线',
                                children: (
                                    <div style={{ position: 'relative', height: 400 }}>
                                        {isRunning && <div style={overlayStyle}><span>运行中...</span></div>}
                                        {result.dailyProfit && result.dailyProfit.length > 0 ? (
                                            <ProfitChart data={result.dailyProfit} />
                                        ) : (
                                            <div style={{ height: 400, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f5f5f5', color: '#999' }}>
                                                暂无数据
                                            </div>
                                        )}
                                    </div>
                                )
                            },
                            {
                                key: 'logs',
                                label: '原始日志',
                                children: (
                                    <pre style={{
                                        background: '#141414',
                                        color: '#fff',
                                        padding: 16,
                                        borderRadius: 8,
                                        maxHeight: 400,
                                        overflow: 'auto'
                                    }}>
                                        {result.logs || '暂无日志'}
                                    </pre>
                                )
                            }
                        ]} />
                    </>
                )}
            </Spin>
        </Modal>
    );
};

export default BacktestResultModal;
