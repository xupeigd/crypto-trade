import React, {useEffect, useMemo, useRef, useState} from 'react';
import {Badge, Col, Row, Space, Tag, Tooltip, Typography} from 'antd';
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    DownOutlined,
    ExclamationCircleOutlined,
    FundOutlined,
    ThunderboltOutlined,
    UnorderedListOutlined,
    UpOutlined,
    WalletOutlined
} from '@ant-design/icons';
import EquityChartModal from '../../components/bot/EquityChartModal';
import EquityMiniChart from '../../components/bot/EquityMiniChart';
import PositionMiniList from '../../components/bot/PositionMiniList';
import OrderMiniList from '../../components/bot/OrderMiniList';
import {tradingService} from '../../services/tradingService';

const {Text} = Typography;

interface BotStatusProps {
    status: {
        callCount: number;
        lastCallTime: number | null;
        modelName: string;
        apiKeyDescription: string;
        riskControlMode: string;
        tradingStyle: string;
        automaticTradeEnabled: boolean;
        systemStatus: string;
        lastProcessingTime: number;
        apiKeyId?: number;
    };
    showEquityModal?: boolean;
    onOpenEquityModal?: () => void;
    onCloseEquityModal?: () => void;
}

const BotStatus: React.FC<BotStatusProps> = ({status, showEquityModal, onOpenEquityModal, onCloseEquityModal}) => {
    // localStorage key
    const BOT_STATUS_EXPANDED_KEY = 'bot_status_expanded';

    // 展开/折叠状态（从localStorage初始化）
    const [isExpanded, setIsExpanded] = useState(() => {
        if (typeof window !== 'undefined') {
            const saved = localStorage.getItem(BOT_STATUS_EXPANDED_KEY);
            return saved === 'true';
        }
        return false;
    });

    // 数据版本号:用于触发子组件数据更新,但不强制重新挂载
    const [dataVersion, setDataVersion] = useState(0);

    const positionMiniListAnchorRef = useRef<HTMLDivElement | null>(null);
    const [positionsCount, setPositionsCount] = useState<number>(0);
    const [ordersCount, setOrdersCount] = useState<number>(0);
    const [targetTab, setTargetTab] = useState<'positions' | 'orders' | 'history' | undefined>(undefined);

    const apiKeyId = status.apiKeyId;

    const refreshCounts = useMemo(() => {
        return async () => {
            if (!apiKeyId) return;

            const [positionsRes, ordersRes] = await Promise.allSettled([
                tradingService.getLivePositions(apiKeyId),
                tradingService.getPendingOrders(apiKeyId),
            ]);

            if (positionsRes.status === 'fulfilled' && Array.isArray(positionsRes.value.data)) {
                setPositionsCount(positionsRes.value.data.length);
            }
            if (ordersRes.status === 'fulfilled' && Array.isArray(ordersRes.value.data)) {
                setOrdersCount(ordersRes.value.data.length);
            }
        };
    }, [apiKeyId]);

    useEffect(() => {
        if (!apiKeyId) return;
        refreshCounts();

        const timer = setInterval(() => {
            if (document.hidden) return;
            refreshCounts();
        }, 10000);

        return () => clearInterval(timer);
    }, [apiKeyId, refreshCounts]);

    // 格式化时间
    const formatTime = (timestamp: number | null) => {
        if (!timestamp) return '从未调用';
        try {
            const date = new Date(timestamp);
            return date.toLocaleString('zh-CN');
        } catch {
            return '时间解析错误';
        }
    };

    // 格式化处理时间
    const formatProcessingTime = (timeMs: number) => {
        if (!timeMs) return '0ms';
        return timeMs < 1000 ? `${timeMs}ms` : `${(timeMs / 1000).toFixed(2)}s`;
    };

    // 交易风格配置 - 返回Ant Design预定义颜色名称
    const getTradingStyleColor = (style: string) => {
        const colorMap: Record<string, string> = {
            '保守型': 'green',
            '谨慎型': 'lime',
            '稳健型': 'gold',
            '积极型': 'orange',
            '激进型': 'red'
        };
        return colorMap[style] || 'default';
    };

    // 展开/折叠切换（保存到localStorage）
    const toggleExpanded = () => {
        setIsExpanded(prev => {
            const newValue = !prev;
            // 保存到localStorage
            if (typeof window !== 'undefined') {
                localStorage.setItem(BOT_STATUS_EXPANDED_KEY, String(newValue));
            }
            return newValue;
        });
    };

    const openExpandedAndFocus = (nextTab: 'positions' | 'orders') => {
        setTargetTab(nextTab);
        setIsExpanded(true);
        if (typeof window !== 'undefined') {
            localStorage.setItem(BOT_STATUS_EXPANDED_KEY, 'true');
        }
        requestAnimationFrame(() => {
            positionMiniListAnchorRef.current?.scrollIntoView({behavior: 'smooth', block: 'start'});
            setTargetTab(undefined);
        });
    };

    // 30秒自动刷新逻辑
    // 优化:静默更新数据版本号,避免强制重新挂载导致闪烁
    useEffect(() => {
        if (!isExpanded || !status.apiKeyId) return;

        // 立即刷新一次
        setDataVersion(prev => prev + 1);

        // 设置30秒定时器
        const timer = setInterval(() => {
            // 递增数据版本号,触发子组件静默更新数据
            setDataVersion(prev => prev + 1);
        }, 30000);

        return () => clearInterval(timer);
    }, [isExpanded, status.apiKeyId]);

    return (
        <div style={{
            width: '100%'
        }}>
            {/* 状态栏信息 */}
            <div style={{
                width: '100%',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: '16px'
            }}>
                {/* 左侧 - 调用信息 */}
                <Space size="large">
                    <Space>
                        <ThunderboltOutlined style={{color: '#1677ff'}}/>
                        <Text style={{color: 'rgba(255, 255, 255, 0.88)', whiteSpace: 'nowrap'}}>
                            No. <Text strong style={{color: '#1677ff'}}>{status.callCount}</Text>
                        </Text>
                    </Space>

                    <Space>
                        <ClockCircleOutlined style={{color: '#52c41a'}}/>
                        <Text style={{color: 'rgba(255, 255, 255, 0.88)', whiteSpace: 'nowrap'}}>
                            上次: <Text style={{color: '#52c41a'}}>{formatTime(status.lastCallTime)}</Text>
                        </Text>
                    </Space>
                </Space>

                {/* 右侧 - 状态信息 */}
                <Space size="middle">
                    {/* API Key */}
                    <Tag
                        color="blue"
                        style={{margin: 0, fontSize: 12}}
                    >
                        {status.apiKeyDescription}
                    </Tag>

                    {/* 风控模式 */}
                    <Tag
                        color="orange"
                        style={{margin: 0, fontSize: 12}}
                    >
                        {status.riskControlMode}
                    </Tag>

                    {/* 交易风格 */}
                    <Tag
                        color={getTradingStyleColor(status.tradingStyle)}
                        style={{margin: 0, fontSize: 12}}
                    >
                        {status.tradingStyle}
                    </Tag>

                    {/* 自动作业状态 */}
                    <Tag
                        color={status.automaticTradeEnabled ? "rgba(82, 196, 26, 0.3)" : "rgba(0, 0, 0, 0.25)"}
                        icon={status.automaticTradeEnabled ? <CheckCircleOutlined style={{color: '#52c41a'}}/> : <ExclamationCircleOutlined style={{color: '#d9d9d9'}}/>}
                        style={{
                            margin: 0,
                            fontSize: 12,
                            color: status.automaticTradeEnabled ? '#52c41a' : '#d9d9d9',
                            border: status.automaticTradeEnabled ? '1px solid #52c41a' : '1px solid #434343'
                        }}
                    >
                        {status.automaticTradeEnabled ? "自动" : "自动"}
                    </Tag>

                    {/* 权益/持仓/委托按钮 */}
                    {true && (
                        <>
                            <Space size={12}>
                                <Badge count={positionsCount} showZero={false} size="small" offset={[6, -2]} color="#fa8c16">
                                    <Tooltip title="当前持仓">
                                        <FundOutlined
                                            style={{color: '#1677ff', cursor: 'pointer', fontSize: 14}}
                                            onClick={() => openExpandedAndFocus('positions')}
                                        />
                                    </Tooltip>
                                </Badge>
                                <Badge count={ordersCount} showZero={false} size="small" offset={[6, -2]} color="#fa8c16">
                                    <Tooltip title="当前委托">
                                        <UnorderedListOutlined
                                            style={{color: '#1677ff', cursor: 'pointer', fontSize: 14}}
                                            onClick={() => openExpandedAndFocus('orders')}
                                        />
                                    </Tooltip>
                                </Badge>
                                <Tooltip title="查看权益详情">
                                    <WalletOutlined
                                        style={{
                                            color: '#1677ff',
                                            cursor: 'pointer',
                                            fontSize: '14px'
                                        }}
                                        onClick={onOpenEquityModal}
                                    />
                                </Tooltip>
                            </Space>

                            <Text style={{
                                color: status.lastProcessingTime < 5000 ? '#52c41a' : '#faad14',
                                fontSize: '12px'
                            }}>
                                {formatProcessingTime(status.lastProcessingTime)}
                            </Text>
                        </>
                    )}

                    {/* 展开/折叠按钮 */}
                    <Tooltip title={isExpanded ? "收起详情" : "展开详情"}>
                        <div
                            onClick={toggleExpanded}
                            style={{
                                color: isExpanded ? '#1677ff' : '#d9d9d9',
                                cursor: 'pointer',
                                fontSize: '16px',
                                display: 'flex',
                                alignItems: 'center'
                            }}
                        >
                            {isExpanded ? <UpOutlined /> : <DownOutlined />}
                        </div>
                    </Tooltip>
                </Space>
            </div>

            {/* 展开区域 - 三栏布局 5:3:2 (共24格) */}
            {isExpanded && status.apiKeyId && (
                <div style={{marginTop: '16px'}}>
                    <Row gutter={16}>
                        {/* 第一栏 - 权益K线图 (5/10 ≈ 12/24) */}
                        <Col span={9}>
                            <EquityMiniChart
                                key={`equity-${dataVersion}`}
                                apiKeyId={status.apiKeyId}
                            />
                        </Col>

                        {/* 第二栏 - 持仓列表 (3/10 ≈ 7/24) */}
                        <Col span={9}>
                            <div ref={positionMiniListAnchorRef}>
                                <PositionMiniList
                                    apiKeyId={status.apiKeyId}
                                    activeTabKey={targetTab}
                                />
                            </div>
                        </Col>

                        {/* 第三栏 - 历史订单 (2/10 ≈ 5/24) */}
                        <Col span={6}>
                            <OrderMiniList
                                key={`order-${dataVersion}`}
                                apiKeyId={status.apiKeyId}
                            />
                        </Col>
                    </Row>
                </div>
            )}

            {/* 权益图表Modal */}
            {showEquityModal && onOpenEquityModal && onCloseEquityModal && (
                <EquityChartModal
                    visible={showEquityModal}
                    onClose={onCloseEquityModal}
                    apiKeyId={status.apiKeyId || 0}
                />
            )}
        </div>
    );
};

export default BotStatus;
