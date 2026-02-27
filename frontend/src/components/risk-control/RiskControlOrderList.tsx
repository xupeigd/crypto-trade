import React, {useEffect, useState} from 'react';
import {Badge, Button, Empty, List, message, Spin, Tag, Tooltip} from 'antd';
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    EyeOutlined,
    SafetyOutlined
} from '@ant-design/icons';
import {RiskControlOrder, RiskControlOrderListProps} from '../../types/riskControl';
import {CurrentModeResponse} from '../../pages/ai-trading/types';
import {riskControlService, RiskControlService} from '../../services/riskControlService';
import {usePageTimer} from '../../hooks/usePageTimer';
import OrderApprovalModal from './OrderApprovalModal';
import './RiskControlOrderList.css';

const RiskControlOrderList: React.FC<RiskControlOrderListProps> = ({apiKeyId, onOrderUpdate}) => {
    const [orders, setOrders] = useState<RiskControlOrder[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedOrder, setSelectedOrder] = useState<RiskControlOrder | null>(null);
    const [modalVisible, setModalVisible] = useState(false);

    // 风控模式相关状态
    const [currentMode, setCurrentMode] = useState<string | null>(null);
    const [modeLoading, setModeLoading] = useState(true);
    const [isModeInitialized, setIsModeInitialized] = useState(false);

    // 获取当前风控模式
    const fetchCurrentMode = async () => {
        try {
            const modeData: CurrentModeResponse = await riskControlService.getCurrentRiskMode();

            if (modeData && modeData.currentMode) {
                const mode = modeData.currentMode;

                // 验证模式值是否有效
                if (mode === 'AUTO' || mode === 'MANUAL') {
                    setCurrentMode(mode);
                    setIsModeInitialized(true);
                } else {
                    console.warn('无效的风控模式值:', mode);
                    // 清除缓存，下次重新获取
                    RiskControlService.clearRiskModeCache();
                    setIsModeInitialized(true); // 即使失败也标记为已初始化，避免一直显示加载状态
                }
            } else {
                console.warn('风控模式响应格式不正确:', modeData);
                // 清除缓存，下次重新获取
                RiskControlService.clearRiskModeCache();
                setIsModeInitialized(true); // 即使失败也标记为已初始化
            }
        } catch (error) {
            console.error('获取风控模式失败:', error);
            // 清除缓存，下次重新获取
            RiskControlService.clearRiskModeCache();
            setIsModeInitialized(true); // 即使失败也标记为已初始化
        } finally {
            setModeLoading(false);
        }
    };

    // 获取待审核订单列表
    const fetchPendingOrders = async () => {
        try {
            setLoading(true);
            const response = await riskControlService.getPendingOrders();
            if (response.success && response.data) {
                // 数据有效性验证：过滤掉无效的订单数据
                // 注意：移除了 auditStatus 的强制要求，因为后端可能返回 null 值
                const validOrders = response.data.filter(order => {
                    return order != null &&
                        order.orderId != null &&
                        order.symbol != null;
                });
                console.log('风控订单API响应数据:', response.data);
                console.log('过滤后的有效订单:', validOrders);
                setOrders(validOrders);
            } else {
                message.error(response.message || '获取待审核订单失败');
                setOrders([]); // 确保设置为空数组而不是保留旧数据
            }
        } catch (error: any) {
            console.error('获取待审核订单失败:', error);
            message.error('获取待审核订单失败');
            setOrders([]); // 确保设置为空数组而不是保留旧数据
        } finally {
            setLoading(false);
        }
    };

    // 审核通过
    const handleApprove = async (orderId: number) => {
        try {
            const response = await riskControlService.approveOrder(orderId);
            if (response.success) {
                message.success('审核通过成功');
                fetchPendingOrders();
                onOrderUpdate();
            } else {
                showDetailedError('审核通过失败', response);
            }
        } catch (error: any) {
            console.error('审核通过失败:', error);
            showDetailedError('审核通过失败', {message: '网络连接异常', errorDetails: error});
        }
    };

    // 显示详细错误信息
    const showDetailedError = (title: string, errorResponse: any) => {
        let errorMessage = errorResponse.message || title;
        let suggestion = '';

        // 如果有详细错误信息，使用更详细的提示
        if (errorResponse.errorDetails) {
            const details = errorResponse.errorDetails;
            errorMessage = details.errorMessage || errorMessage;
            suggestion = details.suggestion || '';

            // 创建详细的错误提示内容
            const errorContent = (
                <div>
                    <div style={{marginBottom: '8px', fontWeight: 'bold'}}>
                        {details.errorTypeDescription || title}
                    </div>
                    <div style={{marginBottom: '8px'}}>
                        错误详情: {errorMessage}
                    </div>
                    {details.orderId && (
                        <div style={{marginBottom: '8px', fontSize: '12px', color: '#666'}}>
                            订单ID: {details.orderId}
                        </div>
                    )}
                    {suggestion && (
                        <div style={{marginBottom: '8px', color: '#1890ff'}}>
                            建议: {suggestion}
                        </div>
                    )}
                </div>
            );

            message.error({
                content: errorContent,
                duration: 6, // 延长显示时间
                style: {maxWidth: '500px'}
            });
        } else {
            // 普通错误提示
            message.error(errorMessage, 5);
        }
    };

    // 审核驳回
    const handleReject = async (orderId: number, reason: string) => {
        try {
            const response = await riskControlService.rejectOrder(orderId, reason);
            if (response.success) {
                message.success('订单已驳回');
                fetchPendingOrders();
                onOrderUpdate();
            } else {
                message.error(response.message || '驳回失败');
            }
        } catch (error: any) {
            console.error('驳回失败:', error);
            message.error('驳回失败');
        }
    };

    // 查看订单详情
    const handleViewDetail = (order: RiskControlOrder) => {
        setSelectedOrder(order);
        setModalVisible(true);
    };

    // 关闭弹窗
    const handleCloseModal = () => {
        setModalVisible(false);
        setSelectedOrder(null);
    };

    // 获取状态图标
    const getStatusIcon = (auditStatus: string | null) => {
        // 如果 auditStatus 为 null，默认视为 PENDING 状态
        const status = auditStatus || 'PENDING';

        switch (status) {
            case 'PENDING':
                return <ClockCircleOutlined style={{color: '#1890ff'}}/>;
            case 'APPROVED':
                return <CheckCircleOutlined style={{color: '#52c41a'}}/>;
            case 'REJECTED':
                return <CloseCircleOutlined style={{color: '#ff4d4f'}}/>;
            default:
                return <ClockCircleOutlined style={{color: '#1890ff'}}/>; // 默认也使用待审核图标
        }
    };

    // 调试函数：打印订单数据用于颜色分析
    const debugOrderColor = (order: RiskControlOrder) => {
        console.log('订单颜色调试数据:', {
            orderId: order.orderId,
            symbol: order.symbol,
            side: order.side,
            posSide: order.posSide,
            orderType: order.orderType,
            calculatedColor: getTradeDirectionColor(order.side, order.posSide, order.symbol)
        });
    };

    // 获取订单类型标签颜色
    const getOrderTypeColor = (orderType: string) => {
        switch (orderType) {
            case 'LIMIT':
                return 'blue';
            case 'MARKET':
                return 'green';
            default:
                return 'default';
        }
    };

    // 获取订单方向标签颜色
    const getOrderSideColor = (side: string) => {
        switch (side) {
            case 'BUY':
                return 'red';
            case 'SELL':
                return 'green';
            default:
                return 'default';
        }
    };

    // 等待时间格式化函数
    const formatWaitingTime = (timeStr?: string): {
        text: string;
        color: string;
        backgroundColor: string;
        timeStatus: string
    } => {
        if (!timeStr) return {
            text: '-',
            color: '#fff',
            backgroundColor: 'rgba(255, 255, 255, 0.02)',
            timeStatus: 'default'
        };
        try {
            const date = new Date(timeStr);
            const now = new Date();
            const diffMs = now.getTime() - date.getTime();
            const diffSeconds = Math.floor(diffMs / 1000);
            const diffMinutes = Math.floor(diffSeconds / 60);
            const diffHours = Math.floor(diffMinutes / 60);
            const diffDays = Math.floor(diffHours / 24);

            let timeText = '';
            let color = '#fff';
            let backgroundColor = 'rgba(255, 255, 255, 0.02)';
            let timeStatus = 'default';

            if (diffDays > 0) {
                timeText = `${diffDays}d${diffHours % 24}h${diffMinutes % 60}m${diffSeconds % 60}s`;
            } else if (diffHours > 0) {
                timeText = `${diffHours}h${diffMinutes % 60}m${diffSeconds % 60}s`;
            } else if (diffMinutes > 0) {
                timeText = `${diffMinutes}m${diffSeconds % 60}s`;
            } else {
                timeText = `${diffSeconds}s`;
            }

            // 根据等待时间设置颜色和背景色
            if (diffMinutes <= 10) {
                color = '#52c41a'; // 绿色
                backgroundColor = 'rgba(82, 196, 26, 0.1)'; // 绿色10%透明度
                timeStatus = 'low';
            } else if (diffMinutes <= 30) {
                color = '#faad14'; // 橙色
                backgroundColor = 'rgba(250, 173, 20, 0.1)'; // 橙色10%透明度
                timeStatus = 'medium';
            } else {
                color = '#ff4d4f'; // 红色
                backgroundColor = 'rgba(255, 77, 79, 0.1)'; // 红色10%透明度
                timeStatus = 'high';
            }

            return {text: timeText, color, backgroundColor, timeStatus};
        } catch {
            return {text: timeStr, color: '#fff', backgroundColor: 'rgba(255, 255, 255, 0.02)', timeStatus: 'default'};
        }
    };

    // 获取时间状态对应的CSS类名
    const getTimeStatusClass = (timeStr?: string): string => {
        const {timeStatus} = formatWaitingTime(timeStr);
        return `time-${timeStatus}`;
    };

    // 带颜色控制的时间格式化函数 - 数字部分使用状态颜色，单位部分固定白色
    const formatWaitingTimeWithColor = (timeStr?: string): React.ReactNode => {
        if (!timeStr) return <span style={{color: '#fff'}}> - </span>;

        try {
            const date = new Date(timeStr);
            const now = new Date();
            const diffMs = now.getTime() - date.getTime();
            const diffSeconds = Math.floor(diffMs / 1000);
            const diffMinutes = Math.floor(diffSeconds / 60);
            const diffHours = Math.floor(diffMinutes / 60);

            let color = '#fff';

            // 确定状态颜色（复用现有逻辑）
            if (diffMinutes <= 10) {
                color = '#52c41a'; // 绿色
            } else if (diffMinutes <= 30) {
                color = '#faad14'; // 橙色
            } else {
                color = '#ff4d4f'; // 红色
            }

            // 构建时间文本并拆分为数字+单位
            if (diffHours > 0) {
                return (
                    <>
                        <span style={{color}}>{diffHours}</span>
                        <span style={{color: '#fff'}}>h </span>
                        <span style={{color}}>{diffMinutes % 60}</span>
                        <span style={{color: '#fff'}}>m </span>
                        <span style={{color}}>{diffSeconds % 60}</span>
                        <span style={{color: '#fff'}}>s</span>
                    </>
                );
            } else if (diffMinutes > 0) {
                return (
                    <>
                        <span style={{color}}>{diffMinutes}</span>
                        <span style={{color: '#fff'}}>m </span>
                        <span style={{color}}>{diffSeconds % 60}</span>
                        <span style={{color: '#fff'}}>s</span>
                    </>
                );
            } else {
                return (
                    <>
                        <span style={{color}}>{diffSeconds}</span>
                        <span style={{color: '#fff'}}>s</span>
                    </>
                );
            }
        } catch {
            return <span style={{color: '#fff'}}>{timeStr}</span>;
        }
    };

    // 格式化保证金和杠杆显示
    const formatMarginAndLever = (order: RiskControlOrder | null): string => {
        if (!order) return '保证金';

        // 优先使用原始成本金额，如果没有则计算
        let amount = '0.00';
        try {
            if (order.originalAmount && order.originalAmount != null) {
                amount = parseFloat(order.originalAmount.toString()).toFixed(2);
            } else if (order.quantity && order.price && order.quantity != null && order.price != null) {
                // 兼容旧数据：根据数量和价格计算
                const calculatedAmount = parseFloat(order.quantity.toString()) * parseFloat(order.price.toString());
                if (!isNaN(calculatedAmount)) {
                    amount = calculatedAmount.toFixed(2);
                }
            }
        } catch (error) {
            console.warn('计算保证金金额失败:', error);
            amount = '0.00';
        }

        // 获取杠杆倍数，默认为1
        const lever = (order.lever && order.lever != null) ? order.lever.toString() : '1';

        return `${amount}(${lever}x)`;
    };

    // 获取交易方向颜色（买多/买空）
    const getTradeDirectionColor = (side: string | null, posSide?: string, symbol?: string): string => {
        // 数据验证 - 如果side为null，尝试从其他字段推断
        if (!side) {
            console.log('side为空，尝试从其他字段推断 - posSide:', posSide, 'symbol:', symbol);

            // 尝试从posSide推断
            if (posSide) {
                if (posSide === 'long') {
                    console.log('从posSide推断为多头，返回绿色');
                    return '#52c41a'; // 绿色
                } else if (posSide === 'short') {
                    console.log('从posSide推断为空头，返回红色');
                    return '#ff4d4f'; // 红色
                }
            }

            // 如果也无法推断，使用默认颜色（橙色表示需要人工判断）
            console.log('无法推断方向，返回橙色');
            return '#faad14'; // 橙色
        }

        // 多头情况（多）= 绿色：
        // BUY + long（做多开仓）
        // SELL + long（卖平多）
        if (side === 'BUY' || (side === 'SELL' && posSide === 'long')) {
            console.log('多头方向，返回绿色 - side:', side, 'posSide:', posSide);
            return '#52c41a'; // 绿色
        }

        // 空头情况（空）= 红色：
        // SELL + short（做空开仓）
        // BUY + short（买空平仓）
        if (side === 'SELL' || (side === 'BUY' && posSide === 'short')) {
            console.log('空头方向，返回红色 - side:', side, 'posSide:', posSide);
            return '#ff4d4f'; // 红色
        }

        console.log('未知方向，返回橙色 - side:', side, 'posSide:', posSide);
        return '#faad14'; // 默认橙色（表示需要人工判断）
    };

    // 使用统一的页面定时器，根据风控模式控制刷新
    const {isActive} = usePageTimer(
        async () => {
            await fetchPendingOrders();
            onOrderUpdate();
        },
        {
            interval: 5000, // 5秒刷新一次
            autoStart: true,
            maxRetries: 3,
            enabled: currentMode === 'MANUAL' && !modeLoading && isModeInitialized, // 手动模式且模式加载完成时启用
            persistKey: 'risk-control-order-timer'
        }
    );

    // 初始化风控模式和定期检查
    useEffect(() => {
        // 初始加载风控模式
        fetchCurrentMode();

        // 定期检查风控模式变化（每30秒检查一次）
        const modeCheckInterval = setInterval(fetchCurrentMode, 30000);

        return () => {
            clearInterval(modeCheckInterval);
        };
    }, []);

    return (
        <div className="risk-control-order-list">
            <div className="risk-control-header">
                <div className="header-left">
                    <h4>风控审核</h4>
                    {orders.length > 0 && (
                        <Badge count={orders.length} overflowCount={99}>
                            <SafetyOutlined style={{fontSize: '16px'}}/>
                        </Badge>
                    )}
                    {orders.length === 0 && (
                        <SafetyOutlined style={{fontSize: '16px'}}/>
                    )}
                </div>
            </div>

            <Spin spinning={loading}>
                {orders.length === 0 ? (
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="暂无待审核订单"
                        style={{marginTop: '20px'}}
                    />
                ) : (
                    <List
                        size="small"
                        dataSource={orders.filter(order => order != null)}
                        renderItem={(order, index) => {
                            // 安全检查，确保order存在
                            if (!order) return null;

                            // 调试订单颜色
                            debugOrderColor(order);

                            return (
                                <List.Item
                                    key={order.orderId || `order-${index}`}
                                    className={`risk-order-item ${getTimeStatusClass(order.createTime)}`}
                                    actions={[
                                        <Tooltip key="view-detail" title="查看详情">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<EyeOutlined/>}
                                                onClick={() => handleViewDetail(order)}
                                            />
                                        </Tooltip>
                                    ]}
                                >
                                    <List.Item.Meta
                                        title={
                                            <div className="order-title simplified">
                                                <span
                                                    className="symbol"
                                                    style={{
                                                        color: getTradeDirectionColor(order.side, order.posSide, order.symbol)
                                                    }}
                                                >
                                                    {order.symbol || 'Unknown'}
                                                </span>
                                                <Tag color={order.orderSource === 'ai' ? 'purple' : 'blue'}>
                                                    {order.orderSource === 'ai' ? 'AI' : '用户'}
                                                </Tag>
                                                <Tag color="blue">
                                                    {formatMarginAndLever(order)}
                                                </Tag>
                                            </div>
                                        }
                                        description={
                                            <div className="order-description simplified">
                                                <Tooltip title={riskControlService.formatDateTime(order.createTime)}>
                                                    <div className="time-with-icon">
                                                        {getStatusIcon(order.auditStatus)}
                                                        <span className="create-time">
                            {formatWaitingTimeWithColor(order.createTime)}
                          </span>
                                                    </div>
                                                </Tooltip>
                                            </div>
                                        }
                                    />
                                </List.Item>
                            );
                        }}
                    />
                )}
            </Spin>

            <OrderApprovalModal
                order={selectedOrder}
                visible={modalVisible}
                apiKeyId={apiKeyId}
                onClose={handleCloseModal}
                onApprove={handleApprove}
                onReject={handleReject}
            />


            {/* 自动模式遮罩层 */}
            {currentMode === 'AUTO' && !modeLoading && isModeInitialized && (
                <div className="manual-mode-overlay">
                    <div className="overlay-content">
                        <div className="overlay-icon">🤖</div>
                        <div className="overlay-text">自动模式</div>
                        {/*<div className="overlay-description">风控审核已暂停</div>*/}
                    </div>
                </div>
            )}
        </div>
    );
};

// Debug info for P0 bug - currentMode should show "AUTO" or "MANUAL" - Fixed API response parsing
export default RiskControlOrderList;