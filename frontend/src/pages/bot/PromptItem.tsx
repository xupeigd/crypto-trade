import React, {useState} from 'react';
import {Badge, Button, Card, Col, Divider, message, Modal, Popover, Row, Space, Table, Tabs, Tag, Tooltip, Typography} from 'antd';
import {
    BranchesOutlined,
    ClockCircleOutlined,
    CopyOutlined,
    DatabaseOutlined,
    DownOutlined,
    ExclamationCircleOutlined,
    FileTextOutlined,
    PlayCircleOutlined,
    RedoOutlined,
    RobotOutlined,
    SwapOutlined,
    UnorderedListOutlined,
    UpOutlined
} from '@ant-design/icons';
import SegmentEditor from '../../components/bot/SegmentEditor';
import {botService, PromptHistoryResponse, ActionHistoryResponse, parsePromptToSegments, SegmentModel} from '../../services/botService';
import {tradingService, TradingOrder} from '../../services/tradingService';
import RiskControlInfoModal from '../../components/risk-control/RiskControlInfoModal';
import './PromptItem.css';

const {Text, Title} = Typography;

/**
 * 单个item的展开状态接口
 * 所有字段都是必需的，确保展开状态正确传递
 */
interface ItemExpandedState {
    promptContent: boolean; // Prompt内容是否展开
    aiResponse: boolean;   // AI模型响应是否展开
}

interface PromptItemProps {
    item: PromptHistoryResponse;
    onCallModel?: (decisionId: string) => void;
    expandedState?: ItemExpandedState; // 从父组件传入的展开状态
    onExpandedStateChange?: (key: keyof ItemExpandedState, value: boolean) => void; // 状态变化回调
    onParentIdClick?: (parentId: number) => void; // 点击parentId的回调
}

/**
 * PromptItem组件
 * 实现简化折叠结构：
 * - 卡片始终展开（移除第一层折叠）
 * - 第二层默认折叠：Prompt内容 & AI模型响应标题
 * - 支持受控的展开状态，避免数据刷新时状态丢失
 * - AI模型响应使用后端解析的responseSegments字段
 */
const PromptItem: React.FC<PromptItemProps> = ({
                                                   item,
                                                   onCallModel,
                                                   expandedState,
                                                   onExpandedStateChange,
                                                   onParentIdClick
                                               }) => {
    const [callingModel, setCallingModel] = useState(false);
    const [promptDisplayMode, setPromptDisplayMode] = useState<'segments' | 'raw'>('segments');
    const [aiResponseDisplayMode, setAiResponseDisplayMode] = useState<'segments' | 'raw'>('segments');
    const [replaying, setReplaying] = useState(false);
    // Action执行历史状态
    const [actionHistoryMap, setActionHistoryMap] = useState<Record<number, ActionHistoryResponse[]>>({});
    const [loadingHistoryMap, setLoadingHistoryMap] = useState<Record<number, boolean>>({});
    const [expandedRowKeys, setExpandedRowKeys] = useState<string[]>([]);
    const [activeTabKey, setActiveTabKey] = useState<string>('aiResponse');
    const [relatedOrders, setRelatedOrders] = useState<TradingOrder[]>([]);
    const [relatedLoading, setRelatedLoading] = useState<boolean>(false);
    // Reasoning Modal状态
    const [reasoningModalVisible, setReasoningModalVisible] = useState(false);
    const [currentReasoning, setCurrentReasoning] = useState<string>('');
    // 折叠状态 - 默认折叠
    const [isExpanded, setIsExpanded] = useState<boolean>(false);

    // 【新增】存储从promptContent解析得到的segments
    const [parsedSegments, setParsedSegments] = useState<SegmentModel[]>([]);

    // 订单详情弹窗状态
    const [orderDetailVisible, setOrderDetailVisible] = useState(false);
    const [selectedOrder, setSelectedOrder] = useState<TradingOrder | null>(null);

    // 风控信息弹窗状态
    const [riskControlInfoVisible, setRiskControlInfoVisible] = useState(false);
    const [riskControlRecordId, setRiskControlRecordId] = useState<number | null>(null);
    const [riskControlApiKeyId, setRiskControlApiKeyId] = useState<number | null>(null);

    // 格式化时间
    const formatTime = (timestamp: any) => {
        if (!timestamp) return '无时间记录';
        try {
            // 处理数组格式时间 [year, month, day, hour, minute, second]
            if (Array.isArray(timestamp)) {
                // Java月份是1-12，JS Date月份是0-11
                const date = new Date(
                    timestamp[0],
                    timestamp[1] - 1,
                    timestamp[2],
                    timestamp[3] || 0,
                    timestamp[4] || 0,
                    timestamp[5] || 0
                );
                return date.toLocaleString('zh-CN');
            }
            const date = new Date(timestamp);
            return date.toLocaleString('zh-CN');
        } catch {
            return '时间解析错误';
        }
    };

    // 【新增】解析promptContent为segments（如果item.segments不存在）
    // 使用useEffect确保在item变化时自动解析
    React.useEffect(() => {
        if (item.promptContent) {
            // 如果item.segments存在且不为空，使用它（向后兼容）
            if (item.segments && item.segments.length > 0) {
                setParsedSegments(item.segments);
            } else {
                // 否则，从promptContent动态解析
                const segments = parsePromptToSegments(item.promptContent);
                setParsedSegments(segments);
            }
        } else {
            setParsedSegments([]);
        }
    }, [item.promptContent, item.segments]);

    // 打开订单详情弹窗
    const handleOpenOrderDetail = (order: TradingOrder) => {
        setSelectedOrder(order);
        setOrderDetailVisible(true);
    };

    // 关闭订单详情弹窗
    const handleCloseOrderDetail = () => {
        setOrderDetailVisible(false);
        setSelectedOrder(null);
    };

    // 渲染订单详情弹窗
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

    // 获取风险等级颜色
    const getRiskColor = (riskLevel: string) => {
        switch (riskLevel?.toUpperCase()) {
            case 'LOW':
                return 'green';
            case 'MEDIUM':
                return 'orange';
            case 'HIGH':
                return 'red';
            default:
                return 'default';
        }
    };

    // 获取风险等级文本
    const getRiskText = (riskLevel: string) => {
        switch (riskLevel?.toUpperCase()) {
            case 'LOW':
                return '低风险';
            case 'MEDIUM':
                return '中等风险';
            case 'HIGH':
                return '高风险';
            default:
                return '未评估';
        }
    };

    /**
     * 获取状态Badge的status属性
     * @param status 状态值
     * @returns Badge的status属性
     */
    const getStatusBadgeStatus = (status: string): 'success' | 'processing' | 'error' | 'default' => {
        switch (status) {
            case 'SUCCESS':
                return 'success';
            case 'PROCESSING':
                return 'processing';
            case 'FAILED':
                return 'error';
            default:
                return 'default';
        }
    };

    /**
     * 获取状态显示文本
     * @param status 状态值
     * @returns 状态显示文本
     */
    const getStatusText = (status: string): string => {
        switch (status) {
            case 'SUCCESS':
                return '成功';
            case 'PROCESSING':
                return '处理中';
            case 'FAILED':
                return '失败';
            default:
                return '未知';
        }
    };

    /**
     * 获取调用来源Tag的颜色
     * @param source 调用来源
     * @returns Tag颜色
     */
    const getSourceColor = (source: string | null | undefined): string => {
        if (!source) return 'default';
        switch (source) {
            case 'SCHEDULED':
                return 'blue';
            case 'MANUAL':
                return 'green';
            case 'DIRECT':
                return 'orange';
            default:
                return 'default';
        }
    };

    /**
     * 获取调用来源显示文本
     * @param source 调用来源
     * @returns 显示文本
     */
    const getSourceText = (source: string | null | undefined): string => {
        if (!source) return '未知来源';
        switch (source) {
            case 'SCHEDULED':
                return 'AI';
            case 'MANUAL':
                return 'HUM';
            case 'DIRECT':
                return 'BOT';
            default:
                return '未知来源';
        }
    };

    /**
     * 获取action对应的Tag颜色
     * @param action 决策动作
     * @returns Tag颜色
     */
    const getActionColor = (action: string | null | undefined): string => {
        if (!action) return 'default';
        switch (action.toUpperCase()) {
            case 'BUY':
                return 'green';
            case 'SELL':
                return 'red';
            case 'HOLD':
                return 'orange';
            case 'QUERY':
                return 'blue';
            case 'ATTENTION':
                return 'purple';
            default:
                return 'default';
        }
    };

    /**
     * 获取instId的颜色（根据持仓方向）
     * @param instId 交易对ID
     * @param posSide 持仓方向 (long/short)
     * @returns 颜色代码
     */
    const getInstIdColor = (instId: string, posSide: string): string => {
        if (posSide === 'long') return '#52c41a';  // 绿色
        if (posSide === 'short') return '#ff4d4f'; // 红色
        return '#a0a0a0'; // 灰色（默认）
    };

    /**
     * 获取置信度的颜色
     * @param confidence 置信度值
     * @returns 颜色代码
     */
    const getConfidenceColor = (confidence: any): string => {
        if (confidence === '-' || confidence === undefined || confidence === null) {
            return '#a0a0a0';
        }
        // 后端confidence已经是0-100的范围，直接使用
        const confValue = typeof confidence === 'number' ? confidence : parseFloat(confidence);
        if (confValue < 60) return '#ff4d4f'; // 红色
        if (confValue < 80) return '#faad14'; // 橙色
        return '#52c41a'; // 绿色
    };

    /**
     * 格式化置信度显示
     * @param confidence 置信度值
     * @returns 格式化后的字符串
     */
    const formatConfidence = (confidence: any): string => {
        if (confidence === '-' || confidence === undefined || confidence === null) {
            return '-';
        }
        // 后端confidence已经是0-100的范围，直接使用
        const confValue = typeof confidence === 'number' ? confidence : parseFloat(confidence);
        return confValue.toFixed(0) + '%';
    };

    /**
     * 获取action显示文本
     * @param action 决策动作
     * @returns 显示文本
     */
    const getActionText = (action: string | null | undefined): string => {
        if (!action) return '未知';
        switch (action.toUpperCase()) {
            case 'BUY':
                return '买入';
            case 'SELL':
                return '卖出';
            case 'HOLD':
                return '持有';
            case 'QUERY':
                return '查询';
            case 'CANCEL_ORDER':
                return '取消挂单'
            case 'ATTENTION':
                return '关注';
            default:
                return action;
        }
    };

    /**
     * 获取大模型调用状态的颜色
     * @param status 状态值
     * @returns 颜色值
     */
    const getStatusColor = (status: string): string => {
        if (!status) return 'default';
        switch (status.toUpperCase()) {
            case 'SUCCESS':
                return 'success';
            case 'FAILED':
            case 'ERROR':
                return 'error';
            case 'PENDING':
                return 'default';
            case 'PROCESSING':
                return 'processing';
            default:
                return 'default';
        }
    };

    /**
     * 获取风控状态的颜色
     * @param status 风控状态（使用后端原始值：PENDING/APPROVED/REJECTED/BYPASSED）
     * @returns 颜色值
     */
    const getRiskControlStatusColor = (status: string | null | undefined): string => {
        if (!status) return 'default';
        switch (status) {
            case 'PENDING':
                return 'default';      // 灰色 - 待审核
            case 'APPROVED':
                return 'success';      // 绿色 - 通过
            case 'REJECTED':
                return 'error';        // 红色 - 驳回
            case 'BYPASSED':
                return 'warning';      // 橙色 - 绕过
            default:
                return 'default';
        }
    };

    /**
     * 获取风控状态的文本
     * @param status 风控状态（使用后端原始值：PENDING/APPROVED/REJECTED/BYPASSED）
     * @returns 状态文本
     */
    const getRiskControlStatusText = (status: string | null | undefined): string => {
        if (!status) return '';
        switch (status) {
            case 'PENDING':
                return '待审核';
            case 'APPROVED':
                return '核准';
            case 'REJECTED':
                return '驳回';
            case 'BYPASSED':
                return '自动核准';
            default:
                return '';
        }
    };

    /**
     * 获取交易动作状态的颜色
     * @param status 交易动作状态
     * @returns 颜色值
     */
    const getTradeActionStatusColor = (status: string | null | undefined): string => {
        if (!status) return 'default';
        switch (status) {
            case 'PENDING':
                return 'default';
            case 'EXECUTING':
                return 'processing';
            case 'SUCCESS':
                return 'success';
            case 'FAILED':
                return 'error';
            default:
                return 'default';
        }
    };

    /**
     * 获取交易动作状态的文本
     * @param status 交易动作状态
     * @returns 状态文本
     */
    const getTradeActionStatusText = (status: string | null | undefined): string => {
        if (!status) return '';
        switch (status) {
            case 'PENDING':
                return '待执行';
            case 'EXECUTING':
                return '执行中';
            case 'SUCCESS':
                return '执行成功';
            case 'FAILED':
                return '执行失败';
            default:
                return '';
        }
    };

    /**
     * 渲染动作详情的 Popover 内容
     * @param actions 单个动作对象或动作对象数组
     * @returns Popover 内容的 JSX 元素
     */
    const renderActionPopoverContent = (actions: any | any[]) => {
        // 支持单个action或action数组
        const actionArray = Array.isArray(actions) ? actions : [actions];

        // 获取主要动作类型（假设数组中的动作类型相同）
        const mainActionType = actionArray.length > 0 ? actionArray[0].action : '';

        // 检测是否有平仓动作（用于表格宽度设置）
        // 提前定义以避免 TDZ 错误
        const hasClose = actionArray.some(action => action.openClose === 'close');

        // 根据动作类型定义不同的列结构
        let columns: any[] = [];

        if (mainActionType === 'QUERY') {
            // QUERY动作：动作 | instId | 时间帧 | 采样数
            columns = [
                {
                    title: '动作',
                    dataIndex: 'actionDisplay',
                    key: 'actionDisplay',
                    width: 120,
                },
                {
                    title: '合约',
                    dataIndex: 'instId',
                    key: 'instId',
                    width: 200,
                    render: (instId: string, record: any) => (
                        <span style={{color: getInstIdColor(instId, record.posSide)}}>
                            {instId}
                        </span>
                    ),
                },
                {
                    title: '时间帧',
                    dataIndex: 'timeframe',
                    key: 'timeframe',
                    width: 100,
                },
                {
                    title: '采样数',
                    dataIndex: 'limit',
                    key: 'limit',
                    width: 100,
                },
            ];
        } else if (mainActionType === 'BUY' || mainActionType === 'SELL') {
            // BUY/SELL动作：动作 | instId | 方向 | [数量] | 金额 | 置信度
            columns = [
                {
                    title: '动作',
                    dataIndex: 'actionDisplay',
                    key: 'actionDisplay',
                    width: 100,  // ✅ 优化: 120 → 100
                },
                {
                    title: '合约',
                    dataIndex: 'instId',
                    key: 'instId',
                    width: 150,  // ✅ 优化: 200 → 150
                    render: (instId: string, record: any) => (
                        <span style={{color: getInstIdColor(instId, record.posSide)}}>
                            {instId}
                        </span>
                    ),
                },
                {
                    title: '方向',
                    dataIndex: 'posSide',
                    key: 'posSide',
                    width: 60,  // ✅ 优化: 80 → 60
                },
            ];

            // 平仓时添加数量列
            if (hasClose) {
                columns.push({
                    title: '数量',
                    dataIndex: 'quantity',
                    key: 'quantity',
                    width: 80,  // ✅ 优化: 100 → 80
                    render: (quantity: any) => {
                        if (quantity === '-' || quantity === undefined || quantity === null) {
                            return '-';
                        }
                        return quantity;
                    },
                });
            }

            columns.push(
                {
                    title: '金额',
                    dataIndex: 'amount',
                    key: 'amount',
                    width: 80,  // ✅ 优化: 100 → 80
                    render: (amount: any) => {
                        if (amount === '-' || amount === undefined || amount === null) {
                            return '-';
                        }
                        return `${amount} USDT`;
                    },
                },
                {
                    title: '置信度',
                    dataIndex: 'confidence',
                    key: 'confidence',
                    width: 70,  // ✅ 优化: 80 → 70
                    render: (confidence: any) => (
                        <span style={{color: getConfidenceColor(confidence)}}>
                            {formatConfidence(confidence)}
                        </span>
                    ),
                },
                {
                    title: '操作',
                    key: 'action',
                    width: 90,
                    render: (_: any, record: any) => (
                        <Space size={8}>
                            <Tooltip title="查看执行历史">
                                <UnorderedListOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleLoadActionHistory(record.id);
                                    }}
                                />
                            </Tooltip>
                            {record.reasoning && (
                                <Tooltip title={record.reasoning.length > 100 ? "查看推理过程" : record.reasoning}>
                                    <FileTextOutlined
                                        style={{cursor: 'pointer', color: '#722ed1'}}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            if (record.reasoning.length > 100) {
                                                setCurrentReasoning(record.reasoning);
                                                setReasoningModalVisible(true);
                                            }
                                        }}
                                    />
                                </Tooltip>
                            )}
                            <Tooltip title="重放此决策">
                                <RedoOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleSingleActionReplay(item.decisionId, record.id, record);
                                    }}
                                />
                            </Tooltip>
                        </Space>
                    ),
                }
            );
        } else if (mainActionType === 'HOLD') {
            // HOLD动作：动作 | instId | 方向 | 置信度
            columns = [
                {
                    title: '动作',
                    dataIndex: 'actionDisplay',
                    key: 'actionDisplay',
                    width: 120,
                },
                {
                    title: '合约',
                    dataIndex: 'instId',
                    key: 'instId',
                    width: 200,
                    render: (instId: string, record: any) => (
                        <span style={{color: getInstIdColor(instId, record.posSide)}}>
                            {instId}
                        </span>
                    ),
                },
                {
                    title: '方向',
                    dataIndex: 'posSide',
                    key: 'posSide',
                    width: 80,
                },
                {
                    title: '置信度',
                    dataIndex: 'confidence',
                    key: 'confidence',
                    width: 80,
                    render: (confidence: any) => (
                        <span style={{color: getConfidenceColor(confidence)}}>
                            {formatConfidence(confidence)}
                        </span>
                    ),
                },
                {
                    title: '操作',
                    key: 'action',
                    width: 90,
                    render: (_: any, record: any) => (
                        <Space size={8}>
                            <Tooltip title="查看执行历史">
                                <UnorderedListOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleLoadActionHistory(record.id);
                                    }}
                                />
                            </Tooltip>
                            {record.reasoning && (
                                <Tooltip title={record.reasoning.length > 100 ? "查看推理过程" : record.reasoning}>
                                    <FileTextOutlined
                                        style={{cursor: 'pointer', color: '#722ed1'}}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            if (record.reasoning.length > 100) {
                                                setCurrentReasoning(record.reasoning);
                                                setReasoningModalVisible(true);
                                            }
                                        }}
                                    />
                                </Tooltip>
                            )}
                            <Tooltip title="重放此决策">
                                <RedoOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleSingleActionReplay(item.decisionId, record.id, record);
                                    }}
                                />
                            </Tooltip>
                        </Space>
                    ),
                },
            ];
        } else if (mainActionType === 'ATTENTION') {
            // ATTENTION动作：动作 | instId | 置信度 | 操作
            columns = [
                {
                    title: '动作',
                    dataIndex: 'actionDisplay',
                    key: 'actionDisplay',
                    width: 120,
                },
                {
                    title: '合约',
                    dataIndex: 'instId',
                    key: 'instId',
                    width: 200,
                    render: (instId: string, record: any) => (
                        <span style={{color: getInstIdColor(instId, record.posSide)}}>
                            {instId}
                        </span>
                    ),
                },
                {
                    title: '置信度',
                    dataIndex: 'confidence',
                    key: 'confidence',
                    width: 80,
                    render: (confidence: any) => (
                        <span style={{color: getConfidenceColor(confidence)}}>
                            {formatConfidence(confidence)}
                        </span>
                    ),
                },
                {
                    title: '操作',
                    key: 'action',
                    width: 90,
                    render: (_: any, record: any) => (
                        <Space size={8}>
                            <Tooltip title="查看执行历史">
                                <UnorderedListOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleLoadActionHistory(record.id);
                                    }}
                                />
                            </Tooltip>
                            {record.reasoning && (
                                <Tooltip title={record.reasoning.length > 100 ? "查看推理过程" : record.reasoning}>
                                    <FileTextOutlined
                                        style={{cursor: 'pointer', color: '#722ed1'}}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            if (record.reasoning.length > 100) {
                                                setCurrentReasoning(record.reasoning);
                                                setReasoningModalVisible(true);
                                            }
                                        }}
                                    />
                                </Tooltip>
                            )}
                            <Tooltip title="重放此决策">
                                <RedoOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleSingleActionReplay(item.decisionId, record.id, record);
                                    }}
                                />
                            </Tooltip>
                        </Space>
                    ),
                },
            ];
        } else {
            // CANCEL等动作：动作 | instId | 方向 | 置信度
            columns = [
                {
                    title: '动作',
                    dataIndex: 'actionDisplay',
                    key: 'actionDisplay',
                    width: 120,
                },
                {
                    title: '合约',
                    dataIndex: 'instId',
                    key: 'instId',
                    width: 200,
                    render: (instId: string, record: any) => (
                        <span style={{color: getInstIdColor(instId, record.posSide)}}>
                            {instId}
                        </span>
                    ),
                },
                {
                    title: '方向',
                    dataIndex: 'posSide',
                    key: 'posSide',
                    width: 80,
                },
                {
                    title: '置信度',
                    dataIndex: 'confidence',
                    key: 'confidence',
                    width: 80,
                    render: (confidence: any) => (
                        <span style={{color: getConfidenceColor(confidence)}}>
                            {formatConfidence(confidence)}
                        </span>
                    ),
                },
                {
                    title: '操作',
                    key: 'action',
                    width: 90,
                    render: (_: any, record: any) => (
                        <Space size={8}>
                            <Tooltip title="查看执行历史">
                                <UnorderedListOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleLoadActionHistory(record.id);
                                    }}
                                />
                            </Tooltip>
                            {record.reasoning && (
                                <Tooltip title={record.reasoning.length > 100 ? "查看推理过程" : record.reasoning}>
                                    <FileTextOutlined
                                        style={{cursor: 'pointer', color: '#722ed1'}}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            if (record.reasoning.length > 100) {
                                                setCurrentReasoning(record.reasoning);
                                                setReasoningModalVisible(true);
                                            }
                                        }}
                                    />
                                </Tooltip>
                            )}
                            <Tooltip title="重放此决策">
                                <RedoOutlined
                                    style={{cursor: 'pointer', color: '#1890ff'}}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleSingleActionReplay(item.decisionId, record.id, record);
                                    }}
                                />
                            </Tooltip>
                        </Space>
                    ),
                },
            ];
        }

        // 准备表格数据源（支持多个action）
        const dataSource = actionArray.map((action, index) => ({
            key: String(action.id), // 使用action.id作为key,与expandedRowKeys匹配
            id: action.id, // 保留原始action的id字段，用于action重放功能
            actionDisplay: getActionText(action.action),
            instId: action.instId || '-',
            posSide: action.posSide || '-',
            amount: action.amount || '-',
            confidence: action.confidence !== undefined ? action.confidence : '-',
            timeframe: action.timeframe || '-',
            limit: action.limit !== undefined ? action.limit : '-',
            quantity: action.quantity !== undefined ? action.quantity : '-',
            openClose: action.openClose || '-',
            reasoning: action.reasoning, // 添加reasoning字段，用于reasoning图标显示
        }));

        return (
            <div style={{overflowX: 'auto', maxWidth: '100%'}}>
                {/* 表格显示动作详情 */}
                <Table
                    columns={columns}
                    dataSource={dataSource}
                    pagination={false}
                    size="small"
                    bordered={false}
                    showHeader={true}
                    tableLayout="fixed"
                    style={{
                        background: '#1f1f1f',
                        fontSize: '12px',
                        width: '100%',
                        minWidth: hasClose ? '550px' : '470px'  // ✅ 设置最小宽度
                    }}
                    className="dark-theme-action-table"
                    expandable={{
                        expandedRowRender: (record: any) => {
                            const actionHistory = actionHistoryMap[record.id];
                            const loading = loadingHistoryMap[record.id];

                            return (
                                <div style={{
                                    padding: '16px',
                                    background: '#262626',
                                    overflowX: 'auto',
                                    maxWidth: '100%'
                                }}>
                                    {loading ? (
                                        <div style={{textAlign: 'center', padding: '20px'}}>
                                            <span style={{color: '#a0a0a0'}}>加载中...</span>
                                        </div>
                                    ) : (
                                        <Table
                                            rowKey="actionId"
                                            columns={[
                                                {
                                                    title: 'Action ID',
                                                    dataIndex: 'actionId',
                                                    key: 'actionId',
                                                    width: 90,
                                                    render: (id: number) => (
                                                        <span style={{color: '#1890ff', fontFamily: 'monospace'}}>
                                                            #{id}
                                                        </span>
                                                    )
                                                },
                                                {
                                                    title: '来源',
                                                    dataIndex: 'executionSource',
                                                    key: 'executionSource',
                                                    width: 70,
                                                    render: (source: string) => {
                                                        const color = source === 'INITIAL' ? '#52c41a' : '#faad14';
                                                        return <span style={{color}}>{source}</span>;
                                                    }
                                                },
                                                {
                                                    title: '创建时间',
                                                    dataIndex: 'createdTime',
                                                    key: 'createdTime',
                                                    width: 150,
                                                    render: (timestamp: number | null) => (
                                                        <span style={{color: '#a0a0a0', fontSize: '11px'}}>
                                                            {timestamp ? formatTime(timestamp) : '-'}
                                                        </span>
                                                    )
                                                },
                                                {
                                                    title: '状态',
                                                    dataIndex: 'status',
                                                    key: 'status',
                                                    width: 70,
                                                    render: (status: string) => {
                                                        const statusConfig: Record<string, {text: string; color: string}> = {
                                                            'PENDING': {text: '待执行', color: '#faad14'},
                                                            'SUCCESS': {text: '成功', color: '#52c41a'},
                                                            'FAILED': {text: '失败', color: '#ff4d4f'}
                                                        };
                                                        const config = statusConfig[status] || {text: status, color: '#a0a0a0'};
                                                        return <span style={{color: config.color}}>{config.text}</span>;
                                                    }
                                                },
                                                {
                                                    title: '执行时间',
                                                    dataIndex: 'executedTime',
                                                    key: 'executedTime',
                                                    width: 150,
                                                    render: (time: string | null) => (
                                                        <span style={{color: '#a0a0a0', fontSize: '11px'}}>
                                                            {time || '-'}
                                                        </span>
                                                    )
                                                }
                                            ]}
                                            dataSource={actionHistory}
                                            pagination={false}
                                            size="small"
                                            bordered={false}
                                            showHeader={true}
                                            tableLayout="fixed"
                                            style={{
                                                background: '#262626',
                                                fontSize: '12px',
                                                width: '100%',
                                                minWidth: '530px'
                                            }}
                                            className="dark-theme-action-table"
                                        />
                                    )}
                                </div>
                            );
                        },
                        expandedRowKeys: expandedRowKeys
                    }}
                />
            </div>
        );
    };

    /**
     * 获取增强的action显示文本（动作 + 交易对 + 方向）
     * @param action 决策动作
     * @param instId 交易对ID (如 BTC-USDT-SWAP)
     * @param posSide 持仓方向 (long/short)
     * @returns 增强的显示文本
     */
    const getEnhancedActionText = (
        action: string | null | undefined,
        instId: string | null | undefined,
        posSide: string | null | undefined
    ): string => {
        // 直接返回 action 文本，不附加 instId 和 posSide
        return getActionText(action);
    };

    /**
     * 从responseSegments中解析action字段
     * @param responseSegments AI响应的结构化段落数据
     * @returns action值(BUY/SELL/HOLD/QUERY/ATTENTION),如果解析失败返回null
     */
    const parseActionFromSegments = (responseSegments?: Array<{
        title?: string;
        content?: string;
        category?: string;
    }>): string | null => {
        if (!responseSegments || responseSegments.length === 0) {
            return null;
        }

        // 查找title="决策JSON"的segment
        const decisionJsonSegment = responseSegments.find(seg => seg.title === "决策JSON");

        if (decisionJsonSegment && decisionJsonSegment.content) {
            try {
                const decisionData = parseDecisionJson(responseSegments);
                
                if (decisionData) {
                    if (Array.isArray(decisionData)) {
                        // 如果是数组，返回所有action的组合，用逗号分隔
                        return decisionData.map(d => d.action).filter(a => a).join(',');
                    } else if (decisionData.actions && Array.isArray(decisionData.actions)) {
                         // 如果包含actions数组
                        return decisionData.actions.map((d: any) => d.action).filter((a: any) => a).join(',');
                    } else if (decisionData.action) {
                        return decisionData.action;
                    }
                }
                
                // 降级策略：如果JSON解析失败，尝试直接正则匹配
                let content = decisionJsonSegment.content;
                // 提取"action":"VALUE"
                const actionMatch = content.match(/"action":\s*"([^"]+)"/);
                if (actionMatch && actionMatch[1]) {
                    console.log('✅ 前端成功解析action(Regex):', actionMatch[1]);
                    return actionMatch[1];
                }
            } catch (error) {
                console.warn('解析action失败:', error);
            }
        }

        return null;
    };

    /**
     * 从responseSegments中解析完整决策JSON
     */
    const parseDecisionJson = (responseSegments?: Array<{
        title?: string;
        content?: string;
        category?: string;
    }>): any => {
        if (!responseSegments || responseSegments.length === 0) {
            return null;
        }

        // 查找title="决策JSON"的segment
        const decisionJsonSegment = responseSegments.find(seg => seg.title === "决策JSON");

        if (decisionJsonSegment && decisionJsonSegment.content) {
            try {
                const content = decisionJsonSegment.content;
                // 优先尝试提取markdown代码块内容
                const jsonBlockMatch = content.match(/```json\s*([\s\S]*?)\s*```/);
                
                if (jsonBlockMatch && jsonBlockMatch[1]) {
                    return JSON.parse(jsonBlockMatch[1].trim());
                }

                // 如果没有找到代码块，尝试直接清理后解析(兼容纯JSON情况)
                const cleanedContent = content.replace(/```json\n?/g, '').replace(/```/g, '').trim();
                // 尝试查找第一个{和最后一个}
                const firstBrace = cleanedContent.indexOf('{');
                const lastBrace = cleanedContent.lastIndexOf('}');
                
                if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
                    const potentialJson = cleanedContent.substring(firstBrace, lastBrace + 1);
                    return JSON.parse(potentialJson);
                }
                
                // 最后尝试直接解析
                return JSON.parse(cleanedContent);
            } catch (error) {
                console.warn('解析决策JSON失败:', error);
            }
        }

        return null;
    };

    // 复制功能
    const handleCopy = async (text: string, fieldName: string) => {
        try {
            await navigator.clipboard.writeText(text);
            message.success(`已复制${fieldName}`);
        } catch (err) {
            message.error(`复制${fieldName}失败`);
            console.error('复制失败:', err);
        }
    };

    // 重放决策
    const handleReplay = async () => {
        // 解析决策JSON以获取完整动作列表
        const decisionData = parseDecisionJson(item.responseSegments);
        let actions: any[] = [];

        if (decisionData) {
            if (Array.isArray(decisionData)) {
                actions = decisionData;
            } else if (decisionData.actions && Array.isArray(decisionData.actions)) {
                actions = decisionData.actions;
            } else if (decisionData.action) {
                // 单个动作对象
                actions = [decisionData];
            }
        }

        // 如果没有解析出动作，使用item中的摘要信息作为兜底
        if (actions.length === 0) {
            actions.push({
                instId: item.instId,
                action: item.action,
                price: item.price,
                quantity: item.quantity
            });
        }

        // 二次确认弹窗
        Modal.confirm({
            title: <span style={{color: '#ffffff'}}>确认重放决策?</span>,
            className: 'dark-theme-modal',
            centered: true,
            width: 600,
            content: (
                <div style={{color: '#a0a0a0'}}>
                    <p style={{marginBottom: '10px'}}>您即将重放此历史决策，包含 <strong>{actions.length}</strong> 个动作:</p>

                    <div style={{
                        maxHeight: '400px',
                        overflowY: 'auto',
                        paddingRight: '4px'
                    }}>
                        {actions.map((action, index) => (
                            <div key={index} style={{
                                background: '#2a2a2a',
                                padding: '12px',
                                borderRadius: '6px',
                                marginBottom: '12px',
                                border: '1px solid #404040'
                            }}>
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    marginBottom: '8px',
                                    borderBottom: '1px solid #404040',
                                    paddingBottom: '8px'
                                }}>
                                    <span style={{color: '#fff', fontWeight: 'bold'}}>动作 #{index + 1}</span>
                                    <Tag color={getActionColor(action.action)}>{getActionText(action.action)}</Tag>
                                </div>
                                {/* 所有动作都显示合约 */}
                                {(action.instId || action.instrumentId) && (
                                    <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>合约:</strong> <span
                                        style={{color: '#fff'}}>{action.instId || action.instrumentId}</span></p>
                                )}
                                {/* 根据动作类型显示不同字段 */}
                                {action.action === 'CANCEL_ORDER' ? (
                                    <>
                                        {/* 取消挂单：只显示订单ID和持仓方向 */}
                                        {action.orderId && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>订单ID:</strong> <span
                                                style={{color: '#fff'}}>{action.orderId}</span></p>
                                        )}
                                        {action.posSide && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>持仓方向:</strong> <span
                                                style={{color: '#fff'}}>{action.posSide}</span></p>
                                        )}
                                    </>
                                ) : (
                                    <>
                                        {/* 其他动作（开仓/平仓等）：显示核心信息 */}
                                        {action.action && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>动作:</strong> <span
                                                style={{color: '#fff'}}>{action.action}</span></p>
                                        )}
                                        {action.posSide && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>持仓方向:</strong> <span
                                                style={{color: '#fff'}}>{action.posSide}</span></p>
                                        )}
                                        {action.orderType && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>订单类型:</strong> <span
                                                style={{color: '#fff'}}>{action.orderType}</span></p>
                                        )}
                                        {(action.quantity !== null && action.quantity !== undefined) && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>数量:</strong> <span
                                                style={{color: '#fff'}}>{action.quantity}</span></p>
                                        )}
                                        {(action.price !== null && action.price !== undefined) && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>价格:</strong> <span
                                                style={{color: '#fff'}}>{action.price}</span></p>
                                        )}
                                        {(action.confidence !== null && action.confidence !== undefined) && (
                                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>置信度:</strong> <span
                                                style={{color: '#fff'}}>{action.confidence}%</span></p>
                                        )}
                                    </>
                                )}
                            </div>
                        ))}
                    </div>

                    <div style={{marginTop: '12px', borderTop: '1px solid #404040', paddingTop: '12px'}}>
                        {item.modelName && (
                            <p style={{marginBottom: '4px'}}><strong style={{color: '#d9d9d9'}}>执行模型:</strong> <span
                                style={{color: '#fff'}}>{item.modelName}</span></p>
                        )}
                    </div>

                    <p style={{color: '#faad14', marginTop: 10, fontWeight: 'bold'}}>
                        ⚠️ 注意: 此操作将使用当前系统配置执行真实交易!
                    </p>
                </div>
            ),
            okText: '确认重放',
            okType: 'primary',
            cancelText: '取消',
            onOk: async () => {
                try {
                    setReplaying(true);
                    const response = await botService.replayDecision(Number(item.decisionId));

                    if (response.success) {
                        message.success('决策重放成功!');

                        // 显示执行结果详情
                        if (response.data && response.data.executionResults) {
                            Modal.info({
                                title: <span style={{color: '#ffffff'}}>重放执行结果</span>,
                                width: 600,
                                className: 'dark-theme-modal',
                                centered: true,
                                content: (
                                    <div style={{color: '#a0a0a0'}}>
                                        <p style={{marginBottom: '4px'}}><strong
                                            style={{color: '#d9d9d9'}}>原始模型:</strong> {response.data.originalModelName}
                                        </p>
                                        <p style={{marginBottom: '4px'}}><strong
                                            style={{color: '#d9d9d9'}}>动作数量:</strong> {response.data.actionCount}
                                        </p>
                                        <p style={{marginBottom: '4px'}}><strong
                                            style={{color: '#d9d9d9'}}>执行时间:</strong> {response.data.executedAt}</p>
                                        <p style={{marginBottom: '4px'}}><strong
                                            style={{color: '#d9d9d9'}}>执行结果数量:</strong> {response.data.executionResults.length}
                                        </p>
                                    </div>
                                ),
                            });
                        }
                    } else {
                        message.error('重放失败: ' + response.message);
                    }
                } catch (error) {
                    message.error('重放失败: ' + (error instanceof Error ? error.message : '未知错误'));
                } finally {
                    setReplaying(false);
                }
            },
        });
    };

    // 单个action重放
    const handleSingleActionReplay = async (recordId: string | number, actionId: string | number, action: any) => {
        Modal.confirm({
            title: <span style={{color: '#ffffff'}}>确认重放此决策?</span>,
            icon: <ExclamationCircleOutlined style={{color: '#faad14'}}/>,
            content: (
                <div style={{color: '#a0a0a0'}}>
                    <p style={{marginBottom: '8px'}}>您即将重放以下决策:</p>
                    <div style={{
                        background: '#262626',
                        padding: '12px',
                        borderRadius: '4px',
                        marginBottom: '12px'
                    }}>
                        <p style={{marginBottom: '4px'}}>
                            <strong style={{color: '#d9d9d9'}}>动作类型:</strong>{' '}
                            <span style={{color: getActionColor(action.action)}}>
                                {action.action}
                            </span>
                        </p>
                        <p style={{marginBottom: '4px'}}>
                            <strong style={{color: '#d9d9d9'}}>合约:</strong>{' '}
                            <span style={{color: getInstIdColor(action.instId, action.posSide)}}>
                                {action.instId}
                            </span>
                        </p>
                        {action.posSide && (
                            <p style={{marginBottom: '4px'}}>
                                <strong style={{color: '#d9d9d9'}}>方向:</strong> {action.posSide}
                            </p>
                        )}
                        {action.quantity && action.quantity !== '-' && (
                            <p style={{marginBottom: '4px'}}>
                                <strong style={{color: '#d9d9d9'}}>数量:</strong> {action.quantity}
                            </p>
                        )}
                        {action.amount && action.amount !== '-' && (
                            <p style={{marginBottom: '4px'}}>
                                <strong style={{color: '#d9d9d9'}}>金额:</strong> {action.amount}
                            </p>
                        )}
                        {action.confidence && (
                            <p style={{marginBottom: '4px'}}>
                                <strong style={{color: '#d9d9d9'}}>置信度:</strong>{' '}
                                <span style={{color: getConfidenceColor(action.confidence)}}>
                                    {formatConfidence(action.confidence)}
                                </span>
                            </p>
                        )}
                    </div>
                    <p style={{color: '#faad14'}}>
                        ⚠️ 注意: 此操作将执行真实交易!
                    </p>
                </div>
            ),
            okText: '确认重放',
            cancelText: '取消',
            okButtonProps: {danger: true},
            cancelButtonProps: {
                style: {
                    backgroundColor: '#1f1f1f',
                    color: '#ffffff',
                    border: '1px solid #434343'
                }
            },
            onOk: async () => {
                try {
                    const response = await botService.replaySingleAction(Number(recordId), Number(actionId));

                    if (response.success) {
                        message.success('单个决策重放成功!');

                        // 显示执行结果详情
                        if (response.data) {
                            Modal.info({
                                title: <span style={{color: '#ffffff'}}>重放执行结果</span>,
                                width: 600,
                                className: 'dark-theme-modal',
                                centered: true,
                                content: (
                                    <div style={{color: '#a0a0a0'}}>
                                        <p style={{marginBottom: '4px'}}>
                                            <strong style={{color: '#d9d9d9'}}>动作类型:</strong> {response.data.actionType}
                                        </p>
                                        <p style={{marginBottom: '4px'}}>
                                            <strong style={{color: '#d9d9d9'}}>合约:</strong> {response.data.instId}
                                        </p>
                                        <p style={{marginBottom: '4px'}}>
                                            <strong style={{color: '#d9d9d9'}}>原始模型:</strong> {response.data.originalModelName}
                                        </p>
                                        <p style={{marginBottom: '4px'}}>
                                            <strong style={{color: '#d9d9d9'}}>执行时间:</strong> {response.data.executedAt}
                                        </p>
                                        {response.data.executionResult && (
                                            <p style={{marginBottom: '4px'}}>
                                                <strong style={{color: '#d9d9d9'}}>执行状态:</strong>{' '}
                                                <span style={{
                                                    color: response.data.executionResult.success ? '#52c41a' : '#ff4d4f'
                                                }}>
                                                    {response.data.executionResult.success ? '成功' : '失败'}
                                                </span>
                                            </p>
                                        )}
                                    </div>
                                ),
                            });
                        }
                    } else {
                        message.error('重放失败: ' + response.message);
                    }
                } catch (error) {
                    message.error('重放失败: ' + (error instanceof Error ? error.message : '未知错误'));
                }
            },
        });
    };

    // 加载action执行历史
    const handleLoadActionHistory = async (actionId: number) => {
        // 如果已经展开,先收起;否则准备展开
        const key = String(actionId);
        if (expandedRowKeys.includes(key)) {
            // 收起
            setExpandedRowKeys(prev => prev.filter(k => k !== key));
            return;
        }

        // 标记加载中
        setLoadingHistoryMap(prev => ({...prev, [actionId]: true}));

        try {
            const response = await botService.getActionHistory(actionId);

            if (response.success && response.data) {
                setActionHistoryMap(prev => ({...prev, [actionId]: response.data}));
                // 加载成功后立即展开该行
                setExpandedRowKeys(prev => [...prev, key]);
            } else {
                message.error('加载执行历史失败: ' + response.message);
            }
        } catch (error) {
            console.error('加载Action执行历史失败:', error);
            message.error('加载执行历史失败: ' + (error instanceof Error ? error.message : '未知错误'));
        } finally {
            setLoadingHistoryMap(prev => ({...prev, [actionId]: false}));
        }
    };

    // 调用大模型
    const handleCallModel = async () => {
        if (callingModel) return;

        try {
            setCallingModel(true);
            if (onCallModel) {
                await onCallModel(item.decisionId);
            } else {
                message.info('调用大模型功能暂未实现');
            }
        } catch (error) {
            console.error('调用大模型失败:', error);
            message.error('调用大模型失败');
        } finally {
            setCallingModel(false);
        }
    };

    const fetchRelatedOrders = async () => {
        if (!item.decisionId) {
            setRelatedOrders([]);
            return;
        }
        setRelatedLoading(true);
        try {
            const resp = await tradingService.getOrdersByRecordId(Number(item.decisionId));
            if (resp.data && resp.data.success) {
                setRelatedOrders(resp.data.data || []);
            } else {
                setRelatedOrders([]);
            }
        } catch (e) {
            console.error('获取关联订单失败', e);
            setRelatedOrders([]);
        } finally {
            setRelatedLoading(false);
        }
    };
    return (
        <>
        <Card
            size="small"
            style={{
                backgroundColor: '#1f1f1f',
                border: '1px solid #434343',
                marginBottom: '12px',
                borderRadius: '6px'
            }}
            styles={{body: {padding: '12px'}}}
        >
            {/* 卡片头部 - 第一行：执行时间 | 模型 | 来源 | 状态 | 耗时 */}
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '8px'
            }}>
                <Space size="small" wrap style={{flex: 1}}>
                    {/* 1. 执行时间 */}
                    <Space>
                        <ClockCircleOutlined style={{color: '#1677ff'}}/>
                        <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                            {formatTime(item.createdTime)}
                        </Text>
                    </Space>

                    {/* 2. AI模型 */}
                    {item.modelName && (
                        <Space>
                            <RobotOutlined style={{color: '#722ed1'}}/>
                            <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                {item.modelName}
                            </Text>
                        </Space>
                    )}

                    {/* 3. 调用来源 */}
                    {item.callSource && (
                        <Tag color={getSourceColor(item.callSource)} style={{margin: 0}}>
                            {getSourceText(item.callSource)}
                        </Tag>
                    )}

                    {/* 4. 执行状态 */}
                    <Badge
                        status={getStatusBadgeStatus(item.status)}
                        text={getStatusText(item.status)}
                        className={item.status === 'PROCESSING' ? 'prompt-item-badge-processing' : ''}
                    />

                    {/* 5. 处理耗时 */}
                    {item.processingTimeMs && (
                        <Text style={{
                            color: item.processingTimeMs < 5000 ? '#52c41a' : '#faad14',
                            fontSize: '11px'
                        }}>
                            {item.processingTimeMs < 1000
                                ? `${item.processingTimeMs}ms`
                                : `${(item.processingTimeMs / 1000).toFixed(2)}s`}
                        </Text>
                    )}
                </Space>

                {/* 折叠/展开按钮 */}
                <Button
                    type="text"
                    size="small"
                    icon={isExpanded ? <UpOutlined /> : <DownOutlined />}
                    onClick={(e) => {
                        e.stopPropagation();
                        setIsExpanded(!isExpanded);
                    }}
                    style={{color: isExpanded ? '#1677ff' : 'rgba(255, 255, 255, 0.6)'}}
                    title={isExpanded ? "收起" : "展开"}
                />
            </div>

            {/* 卡片头部 - 第二行：会话链路按钮 | Action Tag (动作 + 交易对 + 方向) */}
            {/* 只在有action时才显示第二行 */}
            {(() => {
                // 优先解析完整的决策JSON
                const decisionData = parseDecisionJson(item.responseSegments);
                let actionObjects: any[] = [];

                if (decisionData) {
                    if (Array.isArray(decisionData)) {
                        actionObjects = decisionData;
                    } else if (decisionData.actions && Array.isArray(decisionData.actions)) {
                        actionObjects = decisionData.actions;
                    } else if (decisionData.action) {
                        actionObjects = [decisionData];
                    }
                }

                // 降级处理：如果没有解析到完整对象，使用字符串方式
                if (actionObjects.length === 0) {
                    const displayAction = item.action || parseActionFromSegments(item.responseSegments);
                    if (!displayAction) return null;

                    const actionStrings = displayAction.split(',').map(a => a.trim()).filter(a => a);
                    // 将字符串转换为简单对象，补充 item 中的字段
                    actionObjects = actionStrings.map(actionStr => ({
                        action: actionStr,
                        instId: item.instId,
                        posSide: item.posSide,
                        price: item.price,
                        quantity: item.quantity
                    }));
                }

                if (actionObjects.length === 0) return null;

                // 按action类型分组
                const actionGroups = actionObjects.reduce((groups: any, actionObj: any) => {
                    const actionType = actionObj.action;
                    if (!groups[actionType]) {
                        groups[actionType] = [];
                    }
                    groups[actionType].push(actionObj);
                    return groups;
                }, {});

                // 获取去重的action类型列表
                const uniqueActionTypes = Object.keys(actionGroups);

                // 渲染去重的action tags
                const actionTags = (
                    <Space size="small">
                        {uniqueActionTypes.map((actionType) => {
                            const actionsForType = actionGroups[actionType];
                            const count = actionsForType.length;
                            const displayText = count > 1
                                ? `${getActionText(actionType)}(${count})`
                                : getActionText(actionType);

                            return (
                                <Popover
                                    key={actionType}
                                    content={renderActionPopoverContent(actionsForType)}
                                    trigger="hover"
                                    overlayClassName="dark-theme-popover"
                                    overlayStyle={{maxWidth: '700px'}}
                                >
                                    <Tag color={getActionColor(actionType)} style={{fontSize: '13px'}}>
                                        {displayText}
                                    </Tag>
                                </Popover>
                            );
                        })}
                    </Space>
                );

                return (
                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px'}}>
                        {/* 左侧 (50%宽度) */}
                        <div style={{flex: 1, minWidth: 0}}>
                            <Space size="small" wrap>
                                {/* 会话链路按钮 */}
                                <Tooltip title="查看会话链路">
                                    <Button
                                        type="text"
                                        icon={<BranchesOutlined style={{color: '#9254de'}}/>}
                                        onClick={() => onParentIdClick?.(Number(item.decisionId))}
                                        style={{padding: '4px 8px', color: 'rgba(255, 255, 255, 0.6)'}}
                                        size="small"
                                    >
                                    </Button>
                                </Tooltip>

                                {/* 决策动作 */}
                                {actionTags}

                                {/* 开平仓类型标签 */}
                                {item.openCloses && (
                                    <Space size="small">
                                        {item.openCloses.split(',').map((oc, index) => {
                                            // 修复bug：智能匹配action对象，确保reasoning字段正确传递
                                            const direction = oc.trim(); // 'open' 或 'close'

                                            // 从actionObjects中找到匹配的action（根据posSide或action类型）
                                            const matchedAction = actionObjects.find(action => {
                                                const actionPosSide = action.posSide?.toLowerCase();
                                                const actionType = action.action?.toUpperCase();

                                                if (direction === 'open') {
                                                    // 开仓匹配long或BUY
                                                    return actionPosSide === 'long' || actionType === 'BUY';
                                                } else {
                                                    // 平仓匹配short或SELL
                                                    return actionPosSide === 'short' || actionType === 'SELL';
                                                }
                                            }) || actionObjects[0]; // 兜底：取第一个

                                            // 确保action对象包含reasoning字段
                                            const actionWithReasoning = {
                                                ...matchedAction,
                                                reasoning: matchedAction?.reasoning || item.reasoning || ''
                                            };

                                            return (
                                                <Popover
                                                    key={`openclose-${index}`}
                                                    content={renderActionPopoverContent(actionWithReasoning)}
                                                    trigger="hover"
                                                    overlayClassName="dark-theme-popover"
                                                    overlayStyle={{maxWidth: '700px'}}
                                                >
                                                    <Tag
                                                        color={direction === 'open' ? 'green' : 'red'}
                                                        style={{cursor: 'pointer', fontSize: '13px'}}
                                                    >
                                                        {direction === 'open' ? '开仓' : '平仓'}
                                                    </Tag>
                                                </Popover>
                                            );
                                        })}
                                    </Space>
                                )}
                            </Space>
                        </div>

                        {/* 右侧 (50%宽度) - 显示两个状态 */}
                        <div style={{flex: 1, textAlign: 'right', minWidth: 0}}>
                            <Space size="small">
                                {/* 风控状态 - 仅交易动作显示(BUY/SELL/CANCEL_ORDER) */}
                                {(() => {
                                    // 检查是否包含交易动作
                                    const hasTradeAction = uniqueActionTypes.some(actionType =>
                                        actionType === 'BUY' ||
                                        actionType === 'SELL' ||
                                        actionType === 'CANCEL_ORDER'
                                    );

                                    // 只有交易动作才显示风控状态
                                    if (!hasTradeAction) return null;

                                    if (!item.riskControlStatus || !getRiskControlStatusText(item.riskControlStatus)) {
                                        return null;
                                    }

                                    return (
                                        <Tag
                                            color={getRiskControlStatusColor(item.riskControlStatus)}
                                            style={{fontSize: '11px', cursor: 'pointer'}}
                                            onClick={() => {
                                                // 打开风控信息弹窗
                                                setRiskControlRecordId(Number(item.decisionId));
                                                setRiskControlApiKeyId(item.apiKeyId || null);
                                                setRiskControlInfoVisible(true);
                                            }}
                                        >
                                            {getRiskControlStatusText(item.riskControlStatus)}
                                        </Tag>
                                    );
                                })()}

                                {/* 交易动作状态 - 仅交易动作显示(BUY/SELL/CANCEL_ORDER) */}
                                {(() => {
                                    // 检查是否包含交易动作
                                    const hasTradeAction = uniqueActionTypes.some(actionType =>
                                        actionType === 'BUY' ||
                                        actionType === 'SELL' ||
                                        actionType === 'CANCEL_ORDER'
                                    );

                                    // 只有交易动作才显示交易动作状态
                                    if (!hasTradeAction) return null;

                                    if (!item.tradeActionStatus || !getTradeActionStatusText(item.tradeActionStatus)) {
                                        return null;
                                    }

                                    return (
                                        <Tag color={getTradeActionStatusColor(item.tradeActionStatus)} style={{fontSize: '11px'}}>
                                            {getTradeActionStatusText(item.tradeActionStatus)}
                                        </Tag>
                                    );
                                })()}
                            </Space>
                        </div>
                    </div>
                );
            })()}

            {/* 账户数据 - 仅当有数据时显示 */}
            {(item.inputTotalEquity !== null && item.inputTotalEquity !== undefined) && (
                <div style={{
                    border: '1px dashed #434343',
                    backgroundColor: '#262626',
                    padding: '8px 12px',
                    borderRadius: '4px',
                    width: '100%',
                    minWidth: '0',
                    boxSizing: 'border-box'
                }}>
                    <Space size="small" style={{flexWrap: 'nowrap'}}>
                        {item.inputTotalEquity !== null && item.inputTotalEquity !== undefined && (
                            <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '11px', whiteSpace: 'nowrap'}}>
                                权益: <Text style={{
                                color: '#1677ff',
                                fontWeight: 'bold',
                                whiteSpace: 'nowrap'
                            }}>{item.inputTotalEquity.toFixed(2)}</Text>
                            </Text>
                        )}

                        {item.inputUnrealizedPnl !== null && item.inputUnrealizedPnl !== undefined && (
                            <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '11px', whiteSpace: 'nowrap'}}>
                                未结盈亏: <Text style={{
                                color: item.inputUnrealizedPnl >= 0 ? '#52c41a' : '#ff4d4f',
                                fontWeight: 'bold',
                                whiteSpace: 'nowrap'
                            }}>
                                {item.inputUnrealizedPnl >= 0 ? '+' : ''}{item.inputUnrealizedPnl.toFixed(2)}
                            </Text>
                            </Text>
                        )}

                        {item.inputUsedMargin !== null && item.inputUsedMargin !== undefined && (
                            <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '11px', whiteSpace: 'nowrap'}}>
                                保证金: <Text style={{
                                color: 'rgba(255, 255, 255, 0.88)',
                                fontWeight: 'bold',
                                whiteSpace: 'nowrap'
                            }}>{item.inputUsedMargin.toFixed(2)}</Text>
                            </Text>
                        )}

                        {/*{item.inputAvailableBalance !== null && item.inputAvailableBalance !== undefined && (*/}
                        {/*    <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '11px', whiteSpace: 'nowrap'}}>*/}
                        {/*        可用: <Text style={{*/}
                        {/*        color: '#52c41a',*/}
                        {/*        fontWeight: 'bold',*/}
                        {/*        whiteSpace: 'nowrap'*/}
                        {/*    }}>{item.inputAvailableBalance.toFixed(2)}</Text>*/}
                        {/*    </Text>*/}
                        {/*)}*/}

                        {item.inputMarginRatio !== null && item.inputMarginRatio !== undefined && (
                            <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '11px', whiteSpace: 'nowrap'}}>
                                使用率: <Text style={{
                                color: '#faad14',
                                fontWeight: 'bold',
                                whiteSpace: 'nowrap'
                            }}>{item.inputMarginRatio.toFixed(2)}%</Text>
                            </Text>
                        )}
                    </Space>
                </div>
            )}

            {/* 风险等级 - 始终显示 */}
            {item.decisionRiskLevel && (
                <div style={{marginBottom: '12px'}}>
                    <Space size="middle">
                        <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '13px'}}>风险等级:
                            <Tag color={getRiskColor(item.decisionRiskLevel)} style={{marginLeft: '8px', marginRight: 0}}>
                                {getRiskText(item.decisionRiskLevel)}
                            </Tag>
                        </Text>
                        
                        {item.decisionConfidence && (
                            <Text style={{color: 'rgba(255, 255, 255, 0.65)', fontSize: '13px'}}>
                                置信度: <span style={{color: 'rgba(255, 255, 255, 0.88)', fontWeight: 'bold'}}>{(item.decisionConfidence * 100).toFixed(1)}%</span>
                            </Text>
                        )}
                    </Space>
                </div>
            )}

            {/* Tab内容区 - 仅展开时显示 */}
            {isExpanded && (
                <>
                    {/* 分隔线 */}
                    <Divider style={{margin: '12px 0', borderColor: '#434343'}}/>

                    {/* Prompt内容与AI模型响应 - Tab展示 */}
                    <Tabs
                defaultActiveKey="aiResponse"
                type="line"
                size="small"
                className="dark-theme-tabs"
                onChange={(key) => {
                    setActiveTabKey(key);
                    if (key === 'orders' && relatedOrders.length === 0 && !relatedLoading) {
                        fetchRelatedOrders();
                    }
                }}
                tabBarExtraContent={
                    <Space size="small">
                        {/* 重放决策按钮 - 仅在决策JSON存在时显示，且仅在AI响应Tab激活且状态为成功时显示 */}
                        {activeTabKey === 'aiResponse' && item.status === 'SUCCESS' && item.responseSegments && item.responseSegments.some(seg => seg.title === '决策JSON') && (
                            <Tooltip title="从历史AI响应重新执行交易决策">
                                <Button
                                    type="primary"
                                    danger
                                    size="small"
                                    icon={<RedoOutlined/>}
                                    onClick={handleReplay}
                                    loading={replaying}
                                    style={{fontSize: '11px', backgroundColor: '#1f1f1f'}}
                                >
                                </Button>
                            </Tooltip>
                        )}
                        {/*{item.status === 'PROCESSING' && (*/}
                        {/*    <Button*/}
                        {/*        type="link"*/}
                        {/*        size="small"*/}
                        {/*        icon={<PlayCircleOutlined/>}*/}
                        {/*        onClick={handleCallModel}*/}
                        {/*        loading={callingModel}*/}
                        {/*        style={{padding: '0 4px'}}*/}
                        {/*    >*/}
                        {/*        {callingModel ? '调用中...' : '调用大模型'}*/}
                        {/*    </Button>*/}
                        {/*)}*/}
                    </Space>
                }
                items={[
                    // Tab 1: Prompt内容
                    (item.promptContent || parsedSegments.length > 0) ? {
                        key: 'prompt',
                        label: (
                            <Space>
                                <DatabaseOutlined/>
                                <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                    Prompt内容
                                </Text>
                                {parsedSegments.length > 0 && (
                                    <Tag color="blue" style={{fontSize: '11px', margin: 0}}>
                                        {parsedSegments.length}
                                    </Tag>
                                )}
                            </Space>
                        ),
                        children: (
                            <div style={{marginTop: '8px'}}>
                                {/* 模式切换和复制按钮 */}
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    marginBottom: '12px'
                                }}>
                                    <Space size="small">
                                        {/* 显示模式切换 */}
                                        {parsedSegments.length > 0 && (
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<SwapOutlined/>}
                                                onClick={() => setPromptDisplayMode(
                                                    promptDisplayMode === 'segments' ? 'raw' : 'segments'
                                                )}
                                                style={{
                                                    color: 'rgba(255, 255, 255, 0.6)',
                                                    fontSize: '11px'
                                                }}
                                            >
                                                {promptDisplayMode === 'segments' ? '原始文本' : '结构化'}
                                            </Button>
                                        )}
                                    </Space>
                                    <Button
                                            type="text"
                                            size="small"
                                            icon={<CopyOutlined/>}
                                            onClick={() => {
                                                const content = promptDisplayMode === 'segments' && parsedSegments.length > 0
                                                    ? parsedSegments.map(s =>
                                                        `=== ${s.title} ===\n${s.content}`
                                                    ).join('\n\n')
                                                    : item.promptContent || (parsedSegments.length > 0 ? parsedSegments.map(s => `=== ${s.title} ===\n${s.content}`).join('\n\n') : '');
                                                handleCopy(content, 'Prompt内容');
                                            }}
                                            style={{color: 'rgba(255, 255, 255, 0.6)'}}
                                        >
                                            复制
                                        </Button>
                                </div>

                                {/* Prompt具体内容 */}
                                {promptDisplayMode === 'segments' && parsedSegments.length > 0 ? (
                                    <SegmentEditor
                                        segments={parsedSegments}
                                        readOnly={true}
                                    />
                                ) : (
                                    <div style={{
                                        backgroundColor: '#2a2a2a',
                                        padding: '12px',
                                        borderRadius: '4px',
                                        fontFamily: 'monospace',
                                        fontSize: '12px',
                                        color: 'rgba(255, 255, 255, 0.8)',
                                        whiteSpace: 'pre-wrap',
                                        overflowY: 'auto',
                                        maxHeight: '400px',
                                        border: '1px solid #404040'
                                    }}>
                                        {item.promptContent || (parsedSegments.length > 0
                                            ? parsedSegments.map(s => `=== ${s.title} ===\n${s.content}`).join('\n\n')
                                            : '暂无Prompt内容')}
                                    </div>
                                )}
                            </div>
                        )
                    } : null,
                    // Tab 2: AI模型响应
                    {
                        key: 'aiResponse',
                        label: (
                            <Space>
                                <RobotOutlined/>
                                <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                    模型响应
                                </Text>
                                {item.responseSegments && item.responseSegments.length > 0 && (
                                    <Tag color="purple" style={{fontSize: '11px', margin: 0}}>
                                        {item.responseSegments.length}
                                    </Tag>
                                )}
                            </Space>
                        ),
                        children: (
                            <div style={{marginTop: '8px'}}>
                                {/* 处理中状态 */}
                                {item.status === 'PROCESSING' && !item.responseSegments && (
                                    <div style={{
                                        padding: '20px',
                                        textAlign: 'center',
                                        color: 'rgba(255, 255, 255, 0.6)'
                                    }}>
                                        Loading...
                                    </div>
                                )}

                                {/* AI响应内容展示 */}
                                {(item.responseSegments && item.responseSegments.length > 0) || item.fullResponse ? (
                                    <>
                                        {/* 控制栏：模式切换和复制按钮 */}
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center',
                                            marginBottom: '12px'
                                        }}>
                                            <Space size="small">
                                                {/* 显示模式切换按钮 */}
                                                {item.responseSegments && item.responseSegments.length > 0 && (
                                                    <Button
                                                        type="text"
                                                        size="small"
                                                        icon={<SwapOutlined/>}
                                                        onClick={() => setAiResponseDisplayMode(
                                                            aiResponseDisplayMode === 'segments' ? 'raw' : 'segments'
                                                        )}
                                                        style={{
                                                            color: 'rgba(255, 255, 255, 0.6)',
                                                            fontSize: '11px'
                                                        }}
                                                    >
                                                        {aiResponseDisplayMode === 'segments' ? '原始文本' : '结构化'}
                                                    </Button>
                                                )}
                                            </Space>
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<CopyOutlined/>}
                                                onClick={() => {
                                                    const content = aiResponseDisplayMode === 'segments' && item.responseSegments
                                                        ? item.responseSegments.map(s =>
                                                            `=== ${s.title} ===\n${s.content}`
                                                        ).join('\n\n')
                                                        : item.fullResponse || (item.responseSegments ? item.responseSegments.map(s => `=== ${s.title} ===\n${s.content}`).join('\n\n') : '');
                                                    handleCopy(content, 'AI模型响应');
                                                }}
                                                style={{color: 'rgba(255, 255, 255, 0.6)'}}
                                            >
                                                复制
                                            </Button>
                                        </div>

                                        {/* 内容展示 */}
                                        {aiResponseDisplayMode === 'segments' && item.responseSegments && item.responseSegments.length > 0 ? (
                                            <SegmentEditor
                                                segments={item.responseSegments}
                                                readOnly={true}
                                            />
                                        ) : (
                                            <div style={{
                                                backgroundColor: '#2a2a2a',
                                                padding: '12px',
                                                borderRadius: '4px',
                                                fontFamily: 'monospace',
                                                fontSize: '12px',
                                                color: 'rgba(255, 255, 255, 0.8)',
                                                whiteSpace: 'pre-wrap',
                                                overflowY: 'auto',
                                                maxHeight: '400px',
                                                border: '1px solid #404040'
                                            }}>
                                                {item.fullResponse || (item.responseSegments && item.responseSegments.length > 0 
                                                    ? item.responseSegments.map(s => `=== ${s.title} ===\n${s.content}`).join('\n\n') 
                                                    : '暂无AI响应内容')}
                                            </div>
                                        )}
                                    </>
                                ) : (
                                    item.status !== 'PROCESSING' && (
                                        <div style={{
                                            padding: '20px',
                                            textAlign: 'center',
                                            color: 'rgba(255, 255, 255, 0.6)'
                                        }}>
                                            暂无AI响应内容
                                        </div>
                                    )
                                )}
                            </div>
                        )
                    }
                    ,
                    {
                        key: 'orders',
                        label: (
                            <Space>
                                <UnorderedListOutlined/>
                                <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                    关联订单
                                </Text>
                                {(item.relatedOrderCount || relatedOrders.length > 0) && (
                                    <Tag color="green" style={{fontSize: '11px', margin: 0}}>
                                        {item.relatedOrderCount || relatedOrders.length}
                                    </Tag>
                                )}
                            </Space>
                        ),
                        children: (
                            <div style={{marginTop: '8px'}}>
                                {relatedLoading ? (
                                    <div style={{padding: '20px', textAlign: 'center', color: 'rgba(255, 255, 255, 0.6)'}}>加载中...</div>
                                ) : (
                                    relatedOrders.length > 0 ? (
                                        <Table
                                            rowKey={(r) => String((r as any).id || (r as any).orderUuid)}
                                            columns={[
                                                { 
                                                    title: '订单ID', 
                                                    dataIndex: 'orderUuid', 
                                                    key: 'orderUuid', 
                                                    width: 150, 
                                                    ellipsis: true,
                                                    render: (text: string, record: any) => (
                                                        <span 
                                                            style={{color: '#1890ff', cursor: 'pointer'}}
                                                            onClick={() => handleOpenOrderDetail(record)}
                                                        >
                                                            {text}
                                                        </span>
                                                    )
                                                },
                                                { 
                                                    title: '合约', 
                                                    dataIndex: 'instId', 
                                                    key: 'instId', 
                                                    width: 140,
                                                    render: (text: string, record: any) => (
                                                        <span style={{color: getInstIdColor(text, record.posSide)}}>
                                                            {text}
                                                        </span>
                                                    )
                                                },
                                                { 
                                                    title: '买卖', 
                                                    dataIndex: 'side', 
                                                    key: 'side', 
                                                    width: 60,
                                                    render: (text: string) => (
                                                        <span style={{color: text === 'buy' ? '#52c41a' : '#ff4d4f'}}>
                                                            {text === 'buy' ? '买入' : '卖出'}
                                                        </span>
                                                    )
                                                },
                                                { 
                                                    title: '方向', 
                                                    dataIndex: 'posSide', 
                                                    key: 'posSide', 
                                                    width: 60,
                                                    render: (text: string) => (
                                                        <span style={{color: text === 'long' ? '#52c41a' : '#ff4d4f'}}>
                                                            {text === 'long' ? '多' : '空'}
                                                        </span>
                                                    )
                                                },
                                                { title: '张数', dataIndex: 'sz', key: 'sz', width: 80 },
                                                { title: '金额', dataIndex: 'amt', key: 'amt', width: 100 },
                                                { title: '杠杆', dataIndex: 'lever', key: 'lever', width: 60 },
                                                { 
                                                    title: '创建时间', 
                                                    dataIndex: 'createdTime', 
                                                    key: 'createdTime', 
                                                    width: 160,
                                                    render: (t: any) => <span style={{color: '#a0a0a0', fontSize: '11px'}}>{formatTime(t)}</span> 
                                                }
                                            ]}
                                            dataSource={relatedOrders}
                                            pagination={false}
                                            size="small"
                                            bordered={false}
                                            showHeader={true}
                                            tableLayout="fixed"
                                            scroll={{ x: 'max-content' }}
                                            style={{background: '#1f1f1f', fontSize: '12px', width: '100%'}}
                                            className="dark-theme-action-table"
                                        />
                                    ) : (
                                        <div style={{padding: '20px', textAlign: 'center', color: 'rgba(255, 255, 255, 0.6)'}}>暂无关联订单</div>
                                    )
                                )}
                            </div>
                        )
                    }
                ].filter(Boolean) as any}
            />
                </>
            )}
        </Card>

        {/* Reasoning内容展示Modal */}
            {renderOrderDetailModal()}
            <Modal
                title="决策推理过程"
            open={reasoningModalVisible}
            onCancel={() => setReasoningModalVisible(false)}
            footer={[
                <Button key="copy" type="primary" onClick={() => {
                    navigator.clipboard.writeText(currentReasoning);
                    message.success('已复制到剪贴板');
                }}>
                    复制
                </Button>,
                <Button key="close" onClick={() => setReasoningModalVisible(false)}>
                    关闭
                </Button>
            ]}
            width={800}
        >
            <div style={{whiteSpace: 'pre-wrap', wordBreak: 'break-word'}}>
                {currentReasoning}
            </div>
        </Modal>

        {/* 风控信息弹窗 */}
        <RiskControlInfoModal
            visible={riskControlInfoVisible}
            recordId={riskControlRecordId}
            apiKeyId={riskControlApiKeyId}
            onClose={() => {
                setRiskControlInfoVisible(false);
                setRiskControlRecordId(null);
                setRiskControlApiKeyId(null);
            }}
        />
        </>
    );
};

export default PromptItem;
