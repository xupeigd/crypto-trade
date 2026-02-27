import React, {useEffect, useState} from 'react';
import {Button, Col, Input, message, Modal, Row, Tag} from 'antd';
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    ExclamationCircleOutlined
} from '@ant-design/icons';
import {OrderApprovalModalProps} from '../../types/riskControl';
import {RealtimePrice, tradingService} from '../../services/tradingService';
import {riskControlService} from '../../services/riskControlService';
import {usePageTimer} from '../../hooks/usePageTimer';
import './OrderApprovalModal.css';

const {TextArea} = Input;

const OrderApprovalModal: React.FC<OrderApprovalModalProps> = ({
                                                                   order,
                                                                   visible,
                                                                   apiKeyId,
                                                                   onClose,
                                                                   onApprove,
                                                                   onReject
                                                               }) => {
    const [rejectReason, setRejectReason] = useState('');
    const [rejectModalVisible, setRejectModalVisible] = useState(false);
    const [loading, setLoading] = useState(false);

    // 实时价格相关状态
    const [realtimePrice, setRealtimePrice] = useState<RealtimePrice | null>(null);
    const [priceLoading, setPriceLoading] = useState(false);

    // 审核通过
    const handleApprove = async () => {
        if (!order) return;

        setLoading(true);
        try {
            await onApprove(order.orderId);
            setTimeout(() => {
                setLoading(false);
                onClose();
                setRejectReason('');
            }, 500);
        } catch (error) {
            setLoading(false);
        }
    };

    // 审核驳回
    const handleReject = async () => {
        if (!order) return;

        if (!rejectReason.trim()) {
            message.warning('请输入驳回原因');
            return;
        }

        setLoading(true);
        try {
            await onReject(order.orderId, rejectReason);
            setTimeout(() => {
                setLoading(false);
                onClose();
                setRejectReason('');
                setRejectModalVisible(false);
            }, 500);
        } catch (error) {
            setLoading(false);
        }
    };

    // 打开驳回弹窗
    const handleRejectClick = () => {
        setRejectModalVisible(true);
    };

    // 关闭驳回弹窗
    const handleRejectModalCancel = () => {
        setRejectModalVisible(false);
        setRejectReason('');
    };

    // 获取实时价格
    const fetchRealtimePrice = async (symbol: string) => {
        try {
            setPriceLoading(true);
            console.log('正在获取实时价格:', symbol); // 添加调试日志
            const response = await tradingService.getRealtimePrice(symbol, apiKeyId || 0);
            console.log('实时价格原始响应:', response); // 添加调试日志

            // 处理新的 ApiResponse<RealtimePrice> 结构
            if (response.success && response.data) {
                const priceData = response.data;
                console.log('处理后的价格数据:', priceData); // 添加调试日志
                setRealtimePrice(priceData);
            } else {
                console.warn('实时价格数据格式不正确:', response);
                setRealtimePrice(null);
            }
        } catch (error) {
            console.error('获取实时价格失败:', error);
            setRealtimePrice(null);
        } finally {
            setPriceLoading(false);
        }
    };

    // 格式化止盈止损详情
    const formatStopLossDetail = (price: string | undefined, currentPrice: number | undefined, side: string, type: 'profit' | 'loss') => {
        if (!price || !currentPrice || !order) return `${price} (--)`;

        const targetPrice = parseFloat(price);
        const percentage = ((targetPrice - currentPrice) / currentPrice * 100) * (side === 'BUY' ? 1 : -1);
        const amount = parseFloat(order.quantity || '0');
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

    // 格式化价格显示 - 去除末尾多余的零，保持最多6位有效小数
    const formatPrice = (price: number) => {
        return parseFloat(price.toFixed(6)).toString();
    };

    // 使用定时器实现5秒更新实时价格
    const {isActive} = usePageTimer(
        async () => {
            if (order?.symbol && visible) {
                await fetchRealtimePrice(order.symbol);
            }
        },
        {
            interval: 5000,
            enabled: visible && !!order?.symbol,
            persistKey: 'risk-modal-realtime-price'
        }
    );

    // 弹窗打开时立即获取一次价格
    useEffect(() => {
        if (visible && order?.symbol) {
            fetchRealtimePrice(order.symbol);
        } else {
            // 弹窗关闭时清空价格数据
            setRealtimePrice(null);
        }
    }, [visible, order?.symbol]);

    // 获取状态图标
    const getStatusIcon = (auditStatus: string) => {
        switch (auditStatus) {
            case 'PENDING':
                return <ClockCircleOutlined style={{color: '#1890ff'}}/>;
            case 'APPROVED':
                return <CheckCircleOutlined style={{color: '#52c41a'}}/>;
            case 'REJECTED':
                return <CloseCircleOutlined style={{color: '#ff4d4f'}}/>;
            default:
                return <ExclamationCircleOutlined style={{color: '#faad14'}}/>;
        }
    };

    if (!order) return null;

    return (
        <>
            <Modal
                title={
                    <div className="modal-title">
                        {getStatusIcon(order.auditStatus || '')}
                        <span>订单风控审核</span>
                    </div>
                }
                open={visible}
                onCancel={onClose}
                footer={[
                    <Button
                        key="reject"
                        danger
                        onClick={handleRejectClick}
                        disabled={loading || order.auditStatus !== 'PENDING'}
                    >
                        驳回
                    </Button>,
                    <Button
                        key="approve"
                        type="primary"
                        loading={loading}
                        onClick={handleApprove}
                        disabled={order.auditStatus !== 'PENDING'}
                    >
                        通过
                    </Button>,
                ]}
                width={600}
                className="order-approval-modal"
            >
                <div>
                    <div
                        style={{
                            background: 'rgb(42, 42, 42)',
                            padding: '16px',
                            borderRadius: '8px',
                            marginBottom: '16px',
                            border: '1px solid rgb(64, 64, 64)'
                        }}
                    >
                        {/* 新增：实时价格显示 */}
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>实时价格：</strong></span>
                            </Col>
                            <Col>
                                <span
                                    style={{color: priceLoading ? '#faad14' : '#52c41a'}}
                                    className={`realtime-price ${priceLoading ? 'realtime-price-updating' : ''}`}
                                >
                                    {priceLoading ? '加载中...' : realtimePrice ? formatPrice(realtimePrice.lastPrice) : '--'}
                                </span>
                            </Col>
                        </Row>

                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>订单来源：</strong></span>
                            </Col>
                            <Col>
                                <Tag color={order.orderSource === 'ai' ? 'purple' : 'blue'}>
                                    {order.orderSource === 'ai' ? 'AI下单' : '用户下单'}
                                </Tag>
                            </Col>
                        </Row>

                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>交易对：</strong></span>
                            </Col>
                            <Col>
                                <span style={{color: 'rgb(255, 255, 255)'}}>{order.symbol}</span>
                            </Col>
                        </Row>

                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>订单类型：</strong></span>
                            </Col>
                            <Col>
                                <Tag color={order.orderType === 'LIMIT' ? 'blue' : 'green'}>
                                    {order.orderType ? riskControlService.getOrderTypeText(order.orderType) : '-'}
                                </Tag>
                            </Col>
                        </Row>

                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>方向：</strong></span>
                            </Col>
                            <Col>
                                <Tag color={order.side === 'BUY' ? 'green' : 'red'}>
                                    {order.side ? riskControlService.getOrderSideText(order.side) : '-'}
                                </Tag>
                            </Col>
                        </Row>

                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>数量：</strong></span>
                            </Col>
                            <Col>
                <span style={{color: 'rgb(255, 255, 255)'}}>
                  {riskControlService.formatQuantity(order.quantity)}
                </span>
                            </Col>
                        </Row>

                        {order.price && (
                            <Row justify="space-between" style={{marginBottom: '8px'}}>
                                <Col>
                                    <span style={{color: 'rgb(160, 160, 160)'}}><strong>价格：</strong></span>
                                </Col>
                                <Col>
                  <span style={{color: 'rgb(255, 255, 255)'}}>
                    {riskControlService.formatPrice(order.price)}
                  </span>
                                </Col>
                            </Row>
                        )}

                        {/* 拆分：止盈 */}
                        {order.takeProfitPrice && (
                            <Row justify="space-between" style={{marginBottom: '8px'}}>
                                <Col>
                                    <span style={{color: 'rgb(160, 160, 160)'}}><strong>止盈：</strong></span>
                                </Col>
                                <Col>
                                    <span style={{color: 'rgb(255, 255, 255)'}}>
                                        {formatStopLossDetail(order.takeProfitPrice, realtimePrice?.lastPrice, order.side || '', 'profit')}
                                    </span>
                                </Col>
                            </Row>
                        )}

                        {/* 拆分：止损 */}
                        {order.stopLossPrice && (
                            <Row justify="space-between" style={{marginBottom: '8px'}}>
                                <Col>
                                    <span style={{color: 'rgb(160, 160, 160)'}}><strong>止损：</strong></span>
                                </Col>
                                <Col>
                                    <span style={{color: 'rgb(255, 255, 255)'}}>
                                        {formatStopLossDetail(order.stopLossPrice, realtimePrice?.lastPrice, order.side || '', 'loss')}
                                    </span>
                                </Col>
                            </Row>
                        )}

                        {/* 暂时隐藏风控等级显示
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>风控等级：</strong></span>
                            </Col>
                            <Col>
                                <Tag color={riskControlService.getRiskLevelColor(order.riskLevel)}>
                                    {riskControlService.getRiskLevelText(order.riskLevel)}
                                </Tag>
                            </Col>
                        </Row>
                        */}

                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col>
                                <span style={{color: 'rgb(160, 160, 160)'}}><strong>创建时间：</strong></span>
                            </Col>
                            <Col>
                <span style={{color: 'rgb(255, 255, 255)'}}>
                  {riskControlService.formatDateTime(order.createTime)}
                </span>
                            </Col>
                        </Row>

                        {order.auditTime && (
                            <Row justify="space-between" style={{marginBottom: '8px'}}>
                                <Col>
                                    <span style={{color: 'rgb(160, 160, 160)'}}><strong>审核时间：</strong></span>
                                </Col>
                                <Col>
                  <span style={{color: 'rgb(255, 255, 255)'}}>
                    {riskControlService.formatDateTime(order.auditTime)}
                  </span>
                                </Col>
                            </Row>
                        )}

                        {/* 预估总占用资金 - 新增区块 */}
                        {order.estimatedTotalCapital && (
                            <Row gutter={[16, 16]} style={{marginTop: '16px', marginBottom: '8px'}}>
                                <Col span={24}>
                                    <div style={{
                                        background: 'rgb(42, 42, 42)',
                                        padding: '16px',
                                        borderRadius: '8px',
                                        border: '1px solid rgb(64, 64, 64)'
                                    }}>
                                        <Row justify="space-between" align="middle">
                                            <Col>
                                                <span style={{color: 'rgb(160, 160, 160)', fontSize: '14px'}}>
                                                    <strong>预估总占用资金:</strong>
                                                </span>
                                            </Col>
                                            <Col>
                                                <span style={{
                                                    color: '#ff4d4f',
                                                    fontWeight: 'bold',
                                                    fontSize: '18px',
                                                    letterSpacing: '0.5px'
                                                }}>
                                                    {parseFloat(order.estimatedTotalCapital).toFixed(2)}
                                                </span>
                                            </Col>
                                        </Row>
                                        <div style={{
                                            marginTop: '8px',
                                            fontSize: '12px',
                                            color: 'rgb(128, 128, 128)'
                                        }}>
                                            包含保证金 + 开仓手续费 + 平仓手续费
                                        </div>
                                    </div>
                                </Col>
                            </Row>
                        )}

                        {order.auditor && (
                            <Row justify="space-between" style={{marginBottom: '8px'}}>
                                <Col>
                                    <span style={{color: 'rgb(160, 160, 160)'}}><strong>审核人：</strong></span>
                                </Col>
                                <Col>
                                    <span style={{color: 'rgb(255, 255, 255)'}}>{order.auditor}</span>
                                </Col>
                            </Row>
                        )}

                        {order.rejectionReason && (
                            <Row justify="space-between" style={{marginBottom: '0px'}}>
                                <Col>
                                    <span style={{color: 'rgb(160, 160, 160)'}}><strong>驳回原因：</strong></span>
                                </Col>
                                <Col>
                                    <span style={{color: 'rgb(255, 255, 255)'}}>{order.rejectionReason}</span>
                                </Col>
                            </Row>
                        )}
                    </div>
                </div>

                {order.auditStatus === 'PENDING' && (
                    <div className="approval-actions">
                        <div className="action-tips">
                            <ExclamationCircleOutlined style={{color: '#faad14', marginRight: '8px'}}/>
                            <span>请仔细核对订单信息后进行审核操作</span>
                        </div>
                    </div>
                )}
            </Modal>

            {/* 驳回原因弹窗 */}
            <Modal
                title="驳回订单"
                open={rejectModalVisible}
                onOk={handleReject}
                onCancel={handleRejectModalCancel}
                okText="确认驳回"
                cancelText="取消"
                confirmLoading={loading}
                className="reject-modal"
                width={400}
            >
                <div className="reject-form">
                    <div className="reject-tip">
                        <ExclamationCircleOutlined style={{color: '#faad14', marginRight: '8px'}}/>
                        <span>请输入驳回原因，以便后续分析和改进</span>
                    </div>
                    <TextArea
                        placeholder="请输入驳回原因..."
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        rows={4}
                        maxLength={200}
                        showCount
                        className="reject-textarea"
                    />
                </div>
            </Modal>
        </>
    );
};

export default OrderApprovalModal;