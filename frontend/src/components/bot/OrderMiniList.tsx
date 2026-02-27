import React, {useEffect, useState} from 'react';
import {Card, Empty, List, Spin, Tag, Tooltip, Typography, Modal, Row, Col, Button} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';
import {TradingOrder, tradingService} from '../../services/tradingService';

const {Text, Title} = Typography;

interface OrderMiniListProps {
    apiKeyId: number;
    onDataLoad?: () => void;
}

/**
 * 历史订单迷你列表组件
 * 显示最近的历史订单
 */
const OrderMiniList: React.FC<OrderMiniListProps> = ({apiKeyId, onDataLoad}) => {
    const [orders, setOrders] = useState<TradingOrder[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    // 分页相关状态
    const [currentPage, setCurrentPage] = useState<number>(0);
    const [hasMore, setHasMore] = useState<boolean>(true);
    const [loadingMore, setLoadingMore] = useState<boolean>(false);
    // 订单详情弹窗状态
    const [orderDetailVisible, setOrderDetailVisible] = useState(false);
    const [selectedOrder, setSelectedOrder] = useState<TradingOrder | null>(null);

    /**
     * 获取历史订单数据
     * @param page 页码,默认0
     */
    const fetchOrders = async (page: number = 0) => {
        if (!apiKeyId) return;

        // 区分首次加载和加载更多
        if (page === 0) {
            setLoading(true);
            setError('');
        } else {
            setLoadingMore(true);
        }

        try {
            const response = await tradingService.getHistoryOrders(apiKeyId, 7, page, 20);

            if (response.data && response.data.success && response.data.data) {
                const {data: newOrders, total} = response.data.data;

                if (page === 0) {
                    // 首次加载,替换数据
                    setOrders(newOrders);
                    setCurrentPage(0);
                } else {
                    // 加载更多,追加数据
                    setOrders(prevOrders => [...prevOrders, ...newOrders]);
                }

                // 判断是否还有更多数据
                setHasMore((page + 1) * 20 < total);
                setCurrentPage(page);
                onDataLoad?.();
            } else {
                if (page === 0) {
                    setError(response.data?.message || '获取订单数据失败');
                }
            }
        } catch (err) {
            console.error('获取订单数据失败:', err);
            if (page === 0) {
                setError('获取订单数据失败');
            }
        } finally {
            if (page === 0) {
                setLoading(false);
            } else {
                setLoadingMore(false);
            }
        }
    };

    useEffect(() => {
        fetchOrders();
    }, [apiKeyId]);

    /**
     * 格式化时间
     */
    const formatTime = (timestamp: string) => {
        try {
            const date = new Date(timestamp);
            return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
        } catch (e) {
            return '-';
        }
    };

    /**
     * 获取订单状态颜色
     */
    const getStatusColor = (status: string) => {
        switch (status) {
            case 'success':
                return 'green';
            case 'failed':
            case 'canceled':
                return 'red';
            case 'pending':
            case 'submitted':
                return 'orange';
            default:
                return 'default';
        }
    };

    /**
     * 获取订单状态文本
     */
    const getStatusText = (status: string) => {
        switch (status) {
            case 'success':
                return '已成交';
            case 'failed':
                return '失败';
            case 'canceled':
                return '已撤销';
            case 'pending':
                return '待处理';
            case 'submitted':
                return '已提交';
            case 'canceling':
                return '取消中';
            default:
                return status;
        }
    };

    /**
     * 获取合约名称颜色
     */
    const getInstIdColor = (posSide?: string) => {
        if (posSide === 'long') return '#52c41a';
        if (posSide === 'short') return '#ff4d4f';
        return '#d9d9d9';
    };

    /**
     * 获取订单来源的显示文本
     * @param source 订单来源（web/ai/bot/api/sync）
     * @returns 显示文本
     */
    const getOrderSourceText = (source: string | null | undefined): string => {
        if (!source) return '未知';
        switch (source) {
            case 'web':
                return 'HUM';
            case 'ai':
                return 'AI';
            case 'bot':
                return 'BOT';
            case 'api':
                return 'API';
            case 'sync':
                return 'SYNC';
            default:
                return source;
        }
    };

    /**
     * 获取订单来源的Tag颜色
     * @param source 订单来源
     * @returns Tag颜色
     */
    const getOrderSourceColor = (source: string | null | undefined): string => {
        if (!source) return 'default';
        switch (source) {
            case 'web':
                return 'blue';      // 网页手动下单
            case 'ai':
                return 'purple';    // AI自动交易
            case 'bot':
                return 'green';     // 机器人策略
            case 'api':
                return 'orange';    // API接口调用
            case 'sync':
                return 'default';   // 数据同步
            default:
                return 'default';
        }
    };

    /**
     * 获取交易类型文本（开多/开空/平多/平空/撤销）
     * @param order 订单对象
     * @returns 交易类型文本
     */
    const getTradeTypeText = (order: TradingOrder): string => {
        // 先判断撤销状态
        if ('canceled' === order.orderStatus || 'canceling' === order.orderStatus) {
            return '撤销';
        }

        // 判断失败状态
        if ('failed' === order.orderStatus) {
            return '失败';
        }

        // 只有成功订单才判断开平仓
        if ('success' !== order.orderStatus) {
            return '未知';
        }

        // 根据 posSide + side 组合判断开平仓
        if ('long' === order.posSide) {
            if ('buy' === order.side) {
                return '开多';
            }
            if ('sell' === order.side) {
                return '平多';
            }
        }

        if ('short' === order.posSide) {
            if ('sell' === order.side) {
                return '开空';
            }
            if ('buy' === order.side) {
                return '平空';
            }
        }

        return '未知';
    };

    /**
     * 加载更多订单
     */
    const loadMore = () => {
        if (!loadingMore && hasMore) {
            fetchOrders(currentPage + 1);
        }
    };

    /**
     * 滚动事件监听
     */
    const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const container = e.currentTarget;
        const scrollTop = container.scrollTop;
        const scrollHeight = container.scrollHeight;
        const clientHeight = container.clientHeight;

        // 距离底部100px时触发加载
        const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;

        if (isNearBottom && hasMore && !loadingMore && !loading) {
            loadMore();
        }
    };

    /**
     * 打开订单详情弹窗
     */
    const handleOpenOrderDetail = (order: TradingOrder) => {
        setSelectedOrder(order);
        setOrderDetailVisible(true);
    };

    /**
     * 关闭订单详情弹窗
     */
    const handleCloseOrderDetail = () => {
        setOrderDetailVisible(false);
        setSelectedOrder(null);
    };

    /**
     * 渲染订单详情弹窗
     */
    const renderOrderDetailModal = () => {
        if (!selectedOrder) return null;

        const {cexOrder} = selectedOrder;

        const DetailItem = ({label, value, copyable = false}: { label: string, value: React.ReactNode, copyable?: boolean }) => (
            <div style={{marginBottom: '12px', display: 'flex', flexDirection: 'column'}}>
                <Text style={{color: '#8c8c8c', fontSize: '12px', marginBottom: '4px'}}>{label}</Text>
                <Text
                    style={{color: '#d9d9d9', fontSize: '14px'}}
                    copyable={copyable ? {text: String(value)} : false}
                >
                    {value}
                </Text>
            </div>
        );

        return (
            <Modal
                title={<span style={{color: '#fff'}}>订单详情</span>}
                open={orderDetailVisible}
                onCancel={handleCloseOrderDetail}
                footer={null}
                width={600}
                className="dark-theme-modal"
                styles={{body: {padding: '24px'}}}
            >
                <div style={{display: 'flex', flexDirection: 'column', gap: '24px'}}>
                    {/* 基础信息 */}
                    <div>
                        <Title level={5} style={{color: '#fff', marginBottom: '16px', borderLeft: '3px solid #1890ff', paddingLeft: '8px'}}>基础信息</Title>
                        <Row gutter={[16, 16]}>
                            <Col span={12}>
                                <DetailItem label="系统订单ID" value={selectedOrder.orderUuid} copyable />
                            </Col>
                            <Col span={12}>
                                <DetailItem label="合约代码" value={selectedOrder.instId} copyable />
                            </Col>
                            <Col span={12}>
                                <DetailItem label="方向" value={
                                    <span style={{color: selectedOrder.side === 'buy' ? '#52c41a' : '#ff4d4f'}}>
                                        {selectedOrder.side === 'buy' ? '买入' : '卖出'}
                                        {selectedOrder.posSide ? ` (${selectedOrder.posSide})` : ''}
                                    </span>
                                } />
                            </Col>
                            <Col span={12}>
                                <DetailItem label="类型" value={selectedOrder.orderType} />
                            </Col>
                            <Col span={12}>
                                <DetailItem label="状态" value={
                                    <Tag color={selectedOrder.orderStatus === 'success' ? 'success' : selectedOrder.orderStatus === 'failed' ? 'error' : 'processing'}>
                                        {selectedOrder.orderStatus}
                                    </Tag>
                                } />
                            </Col>
                            <Col span={12}>
                                <DetailItem label="创建时间" value={new Date(selectedOrder.createdTime).toLocaleString()} />
                            </Col>
                            {selectedOrder.errorMsg && (
                                <Col span={24}>
                                    <DetailItem label="错误信息" value={<span style={{color: '#ff4d4f'}}>{selectedOrder.errorMsg}</span>} />
                                </Col>
                            )}
                        </Row>
                    </div>

                    {/* CEX订单信息 */}
                    {cexOrder && (
                        <div>
                            <Title level={5} style={{color: '#fff', marginBottom: '16px', borderLeft: '3px solid #faad14', paddingLeft: '8px'}}>交易所订单详情</Title>
                            <Row gutter={[16, 16]}>
                                <Col span={12}>
                                    <DetailItem label="交易所订单ID" value={cexOrder.orderId} copyable />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="交易所状态" value={cexOrder.orderState} />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="委托价格" value={cexOrder.px} />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="成交均价" value={cexOrder.avgPx || '-'} />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="委托数量" value={cexOrder.sz} />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="成交数量" value={cexOrder.filledSz} />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="手续费" value={`${cexOrder.fee} ${cexOrder.feeCcy || ''}`} />
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="更新时间" value={cexOrder.uTime ? new Date(Number(cexOrder.uTime)).toLocaleString() : '-'} />
                                </Col>
                            </Row>
                        </div>
                    )}
                </div>
            </Modal>
        );
    };

    return (
        <>
            {renderOrderDetailModal()}
            <Card
                title={<Text style={{color: '#d9d9d9', fontSize: 14}}>历史订单</Text>}
                extra={
                    <Tooltip title="刷新订单列表">
                        <Button
                            type="text"
                            icon={<ReloadOutlined />}
                            onClick={() => fetchOrders(0)}
                            loading={loading}
                            style={{color: '#d9d9d9'}}
                            size="small"
                        />
                    </Tooltip>
                }
                size="small"
                style={{
                    height: 300,
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343'
                }}
                styles={{
                    body: {
                        padding: '6px',
                        height: 240,
                        overflow: 'auto'
                    }
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
            ) : orders.length === 0 ? (
                <Empty
                    description={<Text style={{color: '#d9d9d9'}}>暂无订单</Text>}
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
            ) : (
                <div
                    style={{
                        height: '100%',
                        overflowY: 'auto'
                    }}
                    onScroll={handleScroll}
                >
                    <List
                        dataSource={orders}
                        renderItem={(order) => (
                            <List.Item
                                key={order.id}
                                style={{
                                    padding: '8px 0',
                                    borderBottom: '1px solid #2a2a2a',
                                    cursor: 'pointer'
                                }}
                                onClick={() => handleOpenOrderDetail(order)}
                            >
                                <div style={{width: '100%'}}>
                                    {/* 第一行: 订单时间、交易类型、合约 */}
                                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4}}>
                                        {/* 左侧: 订单时间 + 交易类型Tag */}
                                        <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                            <Text style={{color: '#999', fontSize: 11}}>
                                                {formatTime(order.createdTime)}
                                            </Text>

                                            {/* 交易类型 Tag (开多/开空/平多/平空/撤销) */}
                                            <Tag
                                                color="default"
                                                style={{
                                                    margin: 0,
                                                    fontSize: 10,
                                                    padding: '0 4px',
                                                    lineHeight: '16px'
                                                }}
                                            >
                                                {getTradeTypeText(order)}
                                            </Tag>
                                        </div>

                                        {/* 右侧: 合约代码(绿多红空) */}
                                        <Text
                                            style={{
                                                color: getInstIdColor(order.posSide),
                                                fontSize: 12,
                                                fontWeight: 'bold'
                                            }}
                                            ellipsis={{tooltip: order.instId}}
                                        >
                                            {order.instId}
                                        </Text>
                                    </div>

                                    {/* 第二行: 订单ID + 状态 */}
                                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4}}>
                                        {/* 左侧: 订单ID */}
                                        <Text
                                            style={{color: '#666', fontSize: 10}}
                                            ellipsis={{tooltip: order.cexOrder?.orderId || '无CEX订单ID'}}
                                        >
                                            {order.cexOrder?.orderId || '-'}
                                        </Text>

                                        {/* 右侧: 订单状态 */}
                                        <Tooltip
                                            title={(order.orderStatus === 'failed' || order.errorMsg) ? (order.errorMsg || '执行失败') : null}
                                        >
                                            <Tag
                                                color={getStatusColor(order.orderStatus)}
                                                style={{
                                                    margin: 0,
                                                    fontSize: 11,
                                                    padding: '0 4px',
                                                    lineHeight: '18px'
                                                }}
                                            >
                                                {getStatusText(order.orderStatus)}
                                            </Tag>
                                        </Tooltip>
                                    </div>

                                    {/* 第三行: 数量、来源 */}
                                    <div style={{display: 'flex', alignItems: 'center'}}>
                                        {/* 左侧: 数量 - 只有数量>0时才显示 */}
                                        {order.sz > 0 && (
                                            <Text style={{color: '#d9d9d9', fontSize: 12}}>
                                                数量: {Number(order.sz).toLocaleString('zh-CN', {
                                                    minimumFractionDigits: 0,
                                                    maximumFractionDigits: 4
                                                })}
                                            </Text>
                                        )}

                                        {/* 右侧: 来源Tag - marginLeft: auto实现右对齐 */}
                                        {order.source && (
                                            <Tag
                                                color={getOrderSourceColor(order.source)}
                                                style={{
                                                    margin: 0,
                                                    fontSize: 10,
                                                    padding: '0 4px',
                                                    lineHeight: '16px',
                                                    marginLeft: 'auto'  // 关键:自动推到右侧
                                                }}
                                            >
                                                {getOrderSourceText(order.source)}
                                            </Tag>
                                        )}
                                    </div>
                                </div>
                            </List.Item>
                        )}
                        style={{
                            fontSize: 12
                        }}
                    />
                    {/* 加载更多提示 */}
                    {loadingMore && (
                        <div style={{
                            textAlign: 'center',
                            padding: '12px',
                            color: '#999'
                        }}>
                            <Spin size="small" />
                            <span style={{marginLeft: 8}}>加载中...</span>
                        </div>
                    )}
                    {/* 没有更多提示 */}
                    {!hasMore && orders.length > 0 && (
                        <div style={{
                            textAlign: 'center',
                            padding: '12px',
                            color: '#666',
                            fontSize: 11
                        }}>
                            没有更多了
                        </div>
                    )}
                </div>
            )}
        </Card>
        </>
    );
};

export default OrderMiniList;
