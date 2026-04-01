import React, {useEffect, useState} from 'react';
import {Button, Col, Empty, message, Modal, Row, Spin, Tag} from 'antd';
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    ExclamationCircleOutlined,
    InfoCircleOutlined
} from '@ant-design/icons';
import {RiskControlOrder, AuditStatus, RiskLevel} from '../../types/riskControl';
import {RealtimePrice, tradingService} from '../../services/tradingService';
import {riskControlService} from '../../services/riskControlService';
import './RiskControlInfoModal.css';

/**
 * 风控信息弹窗组件
 * 只读展示风控订单详情，支持多订单列表展示
 */
interface RiskControlInfoModalProps {
    visible: boolean;
    recordId: number | null;
    apiKeyId: number | null;
    onClose: () => void;
}

const RiskControlInfoModal: React.FC<RiskControlInfoModalProps> = ({
    visible,
    recordId,
    apiKeyId,
    onClose
}) => {
    // 状态管理
    const [orders, setOrders] = useState<RiskControlOrder[]>([]);
    const [loading, setLoading] = useState(false);
    const [realtimePrices, setRealtimePrices] = useState<Map<string, RealtimePrice>>(new Map());
    const [priceLoading, setPriceLoading] = useState(false);

    // 加载风控订单数据
    useEffect(() => {
        if (visible && recordId) {
            fetchOrders();
        } else {
            // 关闭时清空数据
            setOrders([]);
            setRealtimePrices(new Map());
        }
    }, [visible, recordId]);

    // 加载实时价格（每5秒刷新）
    useEffect(() => {
        if (!visible || orders.length === 0 || !apiKeyId) return;

        // 立即加载一次
        fetchRealtimePrices();

        // 设置定时刷新
        const timer = setInterval(() => {
            fetchRealtimePrices();
        }, 5000);

        return () => clearInterval(timer);
    }, [visible, orders, apiKeyId]);

    // 加载风控订单
    const fetchOrders = async () => {
        if (!recordId) return;

        setLoading(true);
        try {
            const response = await riskControlService.getOrdersByRecordId(Number(recordId));

            if (response.success && response.data) {
                setOrders(response.data);
                console.log('加载风控订单成功:', response.data.length, '条');
            } else {
                message.error('加载风控订单失败');
                setOrders([]);
            }
        } catch (error) {
            console.error('加载风控订单异常:', error);
            message.error('加载风控订单异常');
            setOrders([]);
        } finally {
            setLoading(false);
        }
    };

    // 加载实时价格
    const fetchRealtimePrices = async () => {
        if (!apiKeyId || orders.length === 0) return;

        try {
            setPriceLoading(true);

            // 提取所有唯一的交易对
            const symbols = [...new Set(orders.map(o => o.symbol))];

            // 并发获取所有交易对的实时价格
            const priceMap = new Map<string, RealtimePrice>();

            await Promise.all(symbols.map(async symbol => {
                try {
                    const response = await tradingService.getRealtimePrice(symbol, apiKeyId);
                    if (response.success && response.data) {
                        priceMap.set(symbol, response.data);
                    }
                } catch (error) {
                    console.warn(`获取${symbol}实时价格失败:`, error);
                }
            }));

            setRealtimePrices(priceMap);
        } catch (error) {
            console.error('加载实时价格失败:', error);
        } finally {
            setPriceLoading(false);
        }
    };

    // 格式化止盈止损详情
    const formatStopLossDetail = (
        price: string | undefined,
        currentPrice: number | undefined,
        side: string,
        quantity: string,
        type: 'profit' | 'loss'
    ) => {
        if (!price || !currentPrice) return `${price} (--)`;

        const targetPrice = parseFloat(price);
        const percentage = ((targetPrice - currentPrice) / currentPrice * 100) * (side === 'BUY' ? 1 : -1);
        const amount = parseFloat(quantity || '0');
        const expectedValue = Math.abs(targetPrice - currentPrice) * amount;

        const sign = percentage >= 0 ? '+' : '';
        const percentageColor = percentage >= 0 ? '#52c41a' : '#ff4d4f';
        const percentageText = `${sign}${percentage.toFixed(2)}%`;
        const expectedText = type === 'profit' ? `预期盈利: ${expectedValue.toFixed(2)}` : `预期亏损: ${expectedValue.toFixed(2)}`;

        return (
            <span className="stop-loss-detail">
                {price}
                {' ('}
                <span style={{color: percentageColor}} className="percentage-value">
                    {percentageText}
                </span>
                {' | '}
                <span className="expected-value">{expectedText}</span>
                {')'}
            </span>
        );
    };

    // 格式化价格显示
    const formatPrice = (price: number) => {
        return parseFloat(price.toFixed(6)).toString();
    };

    // 获取审核状态图标
    const getStatusIcon = (status: AuditStatus) => {
        switch (status) {
            case AuditStatus.PENDING:
                return <ClockCircleOutlined style={{color: '#faad14'}} />;
            case AuditStatus.APPROVED:
                return <CheckCircleOutlined style={{color: '#52c41a'}} />;
            case AuditStatus.REJECTED:
                return <CloseCircleOutlined style={{color: '#ff4d4f'}} />;
            default:
                return <ExclamationCircleOutlined style={{color: '#1890ff'}} />;
        }
    };

    // 渲染单个订单卡片
    const renderOrderCard = (order: RiskControlOrder, index: number) => {
        const realtimePrice = realtimePrices.get(order.symbol);
        const currentPrice = realtimePrice?.lastPrice || parseFloat(order.price || '0');

        return (
            <div key={order.orderId} className="order-detail-card">
                {/* 卡片头部 */}
                <div className="card-header">
                    <Tag color={order.orderSource === 'ai' ? 'purple' : 'blue'}>
                        {order.orderSource === 'ai' ? 'AI下单' : '用户下单'}
                    </Tag>
                    <span className="order-id">订单 #{index + 1}</span>
                    <Tag color={order.auditStatus ? riskControlService.getAuditStatusColor(order.auditStatus) : 'default'}>
                        {order.auditStatus && getStatusIcon(order.auditStatus)}
                        {order.auditStatus ? riskControlService.getAuditStatusText(order.auditStatus) : '-'}
                    </Tag>
                    <Tag color={order.riskLevel ? riskControlService.getRiskLevelColor(order.riskLevel) : 'default'}>
                        {order.riskLevel ? riskControlService.getRiskLevelText(order.riskLevel) : '-'}
                    </Tag>
                </div>

                {/* 卡片主体 */}
                <div className="card-body">
                    {/* 实时价格 */}
                    <Row className="detail-row">
                        <Col span={8} className="label">实时价格：</Col>
                        <Col span={16} className="value">
                            {realtimePrice ? (
                                <span className={priceLoading ? 'realtime-price-updating' : ''}>
                                    {formatPrice(realtimePrice.lastPrice)}
                                    {priceLoading && <span className="loading-dot">...</span>}
                                </span>
                            ) : (
                                <span style={{color: 'rgba(255,255,255,0.45)'}}>加载中...</span>
                            )}
                        </Col>
                    </Row>

                    {/* 基本信息 */}
                    <div className="info-section">
                        <div className="section-title">订单信息</div>
                        <Row className="detail-row">
                            <Col span={8} className="label">交易对：</Col>
                            <Col span={16} className="value">{order.symbol}</Col>
                        </Row>
                        <Row className="detail-row">
                            <Col span={8} className="label">订单类型：</Col>
                            <Col span={16} className="value">
                                {order.orderType ? riskControlService.getOrderTypeText(order.orderType) : '-'}
                            </Col>
                        </Row>
                        <Row className="detail-row">
                            <Col span={8} className="label">订单方向：</Col>
                            <Col span={16} className="value">
                                {order.side ? riskControlService.getOrderSideText(order.side) : '-'}
                            </Col>
                        </Row>
                        <Row className="detail-row">
                            <Col span={8} className="label">订单数量：</Col>
                            <Col span={16} className="value">
                                {riskControlService.formatQuantity(order.quantity)}
                            </Col>
                        </Row>
                        <Row className="detail-row">
                            <Col span={8} className="label">订单价格：</Col>
                            <Col span={16} className="value">
                                {riskControlService.formatPrice(order.price)}
                            </Col>
                        </Row>
                        {order.lever && (
                            <Row className="detail-row">
                                <Col span={8} className="label">杠杆倍数：</Col>
                                <Col span={16} className="value">{order.lever}x</Col>
                            </Row>
                        )}
                    </div>

                    {/* 止盈止损 */}
                    {(order.takeProfitPrice || order.stopLossPrice) && (
                        <div className="info-section">
                            <div className="section-title">止盈止损</div>
                            {order.takeProfitPrice && (
                                <Row className="detail-row">
                                    <Col span={8} className="label">止盈价格：</Col>
                                    <Col span={16} className="value">
                                        {formatStopLossDetail(
                                            order.takeProfitPrice,
                                            currentPrice,
                                            order.side || '',
                                            order.quantity,
                                            'profit'
                                        )}
                                    </Col>
                                </Row>
                            )}
                            {order.stopLossPrice && (
                                <Row className="detail-row">
                                    <Col span={8} className="label">止损价格：</Col>
                                    <Col span={16} className="value">
                                        {formatStopLossDetail(
                                            order.stopLossPrice,
                                            currentPrice,
                                            order.side || '',
                                            order.quantity,
                                            'loss'
                                        )}
                                    </Col>
                                </Row>
                            )}
                        </div>
                    )}

                    {/* 预估资金 */}
                    {order.estimatedTotalCapital && (
                        <div className="capital-estimation">
                            <InfoCircleOutlined style={{marginRight: 8}} />
                            <span>预估总占用资金：{parseFloat(order.estimatedTotalCapital).toFixed(2)} ₮</span>
                        </div>
                    )}

                    {/* 审核信息 */}
                    <div className="info-section">
                        <div className="section-title">审核信息</div>
                        <Row className="detail-row">
                            <Col span={8} className="label">创建时间：</Col>
                            <Col span={16} className="value">
                                {riskControlService.formatDateTime(order.createTime)}
                            </Col>
                        </Row>
                        {order.auditTime && (
                            <Row className="detail-row">
                                <Col span={8} className="label">审核时间：</Col>
                                <Col span={16} className="value">
                                    {riskControlService.formatDateTime(order.auditTime)}
                                </Col>
                            </Row>
                        )}
                        {order.auditor && (
                            <Row className="detail-row">
                                <Col span={8} className="label">审核人：</Col>
                                <Col span={16} className="value">{order.auditor}</Col>
                            </Row>
                        )}
                        {order.rejectionReason && (
                            <Row className="detail-row">
                                <Col span={8} className="label">驳回原因：</Col>
                                <Col span={16} className="value" style={{color: '#ff4d4f'}}>
                                    {order.rejectionReason}
                                </Col>
                            </Row>
                        )}
                    </div>
                </div>
            </div>
        );
    };

    return (
        <Modal
            title="风控订单详情"
            open={visible}
            onCancel={onClose}
            footer={[
                <Button key="close" onClick={onClose}>
                    关闭
                </Button>
            ]}
            width={800}
            className="risk-control-info-modal"
        >
            {loading ? (
                <div style={{textAlign: 'center', padding: '40px 0'}}>
                    <Spin tip="加载中..." />
                </div>
            ) : orders.length === 0 ? (
                <Empty
                    description="暂无风控订单数据"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    style={{padding: '40px 0'}}
                />
            ) : (
                <div className="order-list">
                    {orders.map((order, index) => renderOrderCard(order, index))}
                </div>
            )}
        </Modal>
    );
};

export default RiskControlInfoModal;
