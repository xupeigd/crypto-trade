import React, {useEffect, useState, useRef} from 'react';
import {
    Button,
    Card,
    Col,
    Input,
    message,
    Row,
    Space,
    Statistic,
    Table,
    Tabs,
    Tag,
    Tooltip,
    Typography
} from 'antd';
import {
    ArrowUpOutlined,
    ArrowDownOutlined,
    ReloadOutlined,
    SearchOutlined,
    RollbackOutlined
} from '@ant-design/icons';
import {useParams, useNavigate} from 'react-router-dom';
import {
    strategyExecutionService,
    FreqtradeInstance,
    InstanceDetail,
    TradeInfo,
    ProfitSummary
} from '../../services/strategyExecutionService';

const {Title, Text} = Typography;
const {TextArea} = Input;

const InstanceMonitorPage: React.FC = () => {
    const {instanceId} = useParams<{ instanceId: string }>();
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [instanceDetail, setInstanceDetail] = useState<InstanceDetail | null>(null);
    const [profit, setProfit] = useState<ProfitSummary | null>(null);
    const [positions, setPositions] = useState<TradeInfo[]>([]);
    const [trades, setTrades] = useState<TradeInfo[]>([]);
    const [logs, setLogs] = useState<string>('');
    const [logKeyword, setLogKeyword] = useState<string>('');
    const logRef = useRef<HTMLPreElement>(null);

    useEffect(() => {
        if (instanceId) {
            loadAllData();
            // 每15秒刷新数据
            const interval = setInterval(() => {
                refreshData();
            }, 15000);
            return () => clearInterval(interval);
        }
    }, [instanceId]);

    useEffect(() => {
        // 自动滚动到日志底部
        if (logRef.current) {
            logRef.current.scrollTop = logRef.current.scrollHeight;
        }
    }, [logs]);

    const loadAllData = async () => {
        setLoading(true);
        try {
            await Promise.all([
                loadInstanceDetail(),
                loadProfit(),
                loadPositions(),
                loadTrades(),
                loadLogs()
            ]);
        } finally {
            setLoading(false);
        }
    };

    const refreshData = async () => {
        try {
            await Promise.all([
                loadProfit(),
                loadPositions()
            ]);
        } catch (e) {
            console.error('刷新数据失败', e);
        }
    };

    const loadInstanceDetail = async () => {
        if (!instanceId) return;
        try {
            const detail = await strategyExecutionService.getInstanceDetail(Number(instanceId));
            setInstanceDetail(detail);
        } catch (e) {
            message.error('加载实例详情失败');
        }
    };

    const loadProfit = async () => {
        if (!instanceId) return;
        try {
            const data = await strategyExecutionService.getProfit(Number(instanceId));
            setProfit(data);
        } catch (e) {
            console.error('加载盈亏统计失败', e);
        }
    };

    const loadPositions = async () => {
        if (!instanceId) return;
        try {
            const data = await strategyExecutionService.getPositions(Number(instanceId));
            setPositions(data);
        } catch (e) {
            console.error('加载持仓失败', e);
        }
    };

    const loadTrades = async () => {
        if (!instanceId) return;
        try {
            const data = await strategyExecutionService.getTrades(Number(instanceId), 50);
            setTrades(data);
        } catch (e) {
            console.error('加载交易记录失败', e);
        }
    };

    const loadLogs = async () => {
        if (!instanceId) return;
        try {
            const data = await strategyExecutionService.getInstanceLogs(Number(instanceId), 500);
            setLogs(data);
        } catch (e) {
            console.error('加载日志失败', e);
        }
    };

    const handleSearchLogs = async () => {
        if (!instanceId || !logKeyword) return;
        try {
            const matches = await strategyExecutionService.searchLogs(Number(instanceId), logKeyword);
            setLogs(matches.join('\n'));
        } catch (e) {
            message.error('搜索日志失败');
        }
    };

    const getStatusTag = (status: string) => {
        const statusConfig: Record<string, { color: string; text: string }> = {
            STARTING: {color: 'processing', text: '启动中'},
            RUNNING: {color: 'success', text: '运行中'},
            STOPPED: {color: 'default', text: '已停止'},
            ERROR: {color: 'error', text: '错误'}
        };
        const config = statusConfig[status] || {color: 'default', text: status};
        return <Tag color={config.color}>{config.text}</Tag>;
    };

    const positionColumns = [
        {
            title: '交易对',
            dataIndex: 'pair',
            key: 'pair',
            width: 120
        },
        {
            title: '数量',
            dataIndex: 'amount',
            key: 'amount',
            width: 100,
            render: (v: number) => v.toFixed(4)
        },
        {
            title: '开仓价',
            dataIndex: 'openRate',
            key: 'openRate',
            width: 100,
            render: (v: number) => v.toFixed(4)
        },
        {
            title: '当前价',
            dataIndex: 'currentRate',
            key: 'currentRate',
            width: 100,
            render: (v: number) => v.toFixed(4)
        },
        {
            title: '盈亏',
            dataIndex: 'profit',
            key: 'profit',
            width: 100,
            render: (v: number) => (
                <span style={{color: v >= 0 ? '#52c41a' : '#ff4d4f'}}>
                    {v.toFixed(4)}
                </span>
            )
        },
        {
            title: '盈亏%',
            dataIndex: 'profitPct',
            key: 'profitPct',
            width: 100,
            render: (v: number) => (
                <span style={{color: v >= 0 ? '#52c41a' : '#ff4d4f'}}>
                    {v.toFixed(2)}%
                </span>
            )
        },
        {
            title: '开仓时间',
            dataIndex: 'openDate',
            key: 'openDate',
            width: 180,
            render: (v: string) => v ? new Date(v).toLocaleString() : '-'
        }
    ];

    const tradeColumns = [
        ...positionColumns,
        {
            title: '状态',
            dataIndex: 'isOpen',
            key: 'isOpen',
            width: 80,
            render: (v: boolean) => (
                <Tag color={v ? 'blue' : 'default'}>
                    {v ? '持仓中' : '已平仓'}
                </Tag>
            )
        }
    ];

    if (!instanceDetail) {
        return <div style={{padding: 24}}>加载中...</div>;
    }

    const {instance, config, strategy} = instanceDetail;

    return (
        <div style={{padding: '24px'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: 16}}>
                <div>
                    <Title level={3} style={{marginBottom: 8}}>
                        实例监控
                    </Title>
                    <Text type="secondary">{instance.instanceName}</Text>
                </div>
                <Space>
                    <Tooltip title="返回列表">
                        <Button icon={<RollbackOutlined/>} onClick={() => navigate('/system/strategy-execution')}/>
                    </Tooltip>
                    <Tooltip title="刷新">
                        <Button
                            type="primary"
                            icon={<ReloadOutlined/>}
                            onClick={loadAllData}
                            loading={loading}
                        />
                    </Tooltip>
                </Space>
            </div>

            {/* 实例信息 */}
            <Card size="small" style={{marginBottom: 16}}>
                <Row gutter={24}>
                    <Col span={4}>
                        <Text type="secondary">状态: </Text>
                        {getStatusTag(instance.status)}
                    </Col>
                    <Col span={4}>
                        <Text type="secondary">API端口: </Text>
                        <Text strong>{instance.apiPort}</Text>
                    </Col>
                    <Col span={4}>
                        <Text type="secondary">策略: </Text>
                        <Text strong>{strategy?.title || '-'}</Text>
                    </Col>
                    <Col span={4}>
                        <Text type="secondary">配置: </Text>
                        <Text strong>{config?.configName || '-'}</Text>
                    </Col>
                    <Col span={4}>
                        <Text type="secondary">容器ID: </Text>
                        <Text code>{instance.containerId?.substring(0, 12) || '-'}</Text>
                    </Col>
                    <Col span={4}>
                        <Text type="secondary">启动时间: </Text>
                        <Text>{instance.startedAt ? new Date(instance.startedAt).toLocaleString() : '-'}</Text>
                    </Col>
                </Row>
            </Card>

            {/* 盈亏统计 */}
            <Row gutter={16} style={{marginBottom: 16}}>
                <Col span={6}>
                    <Card style={{minHeight: 80, display: 'flex', alignItems: 'center'}} bodyStyle={{padding: 8}}>
                        <Statistic
                            style={{flex: 1}}
                            title="总盈亏"
                            value={profit?.profitAll || 0}
                            precision={4}
                            valueStyle={{color: (profit?.profitAll || 0) >= 0 ? '#52c41a' : '#ff4d4f'}}
                            prefix={(profit?.profitAll || 0) >= 0 ? <ArrowUpOutlined/> : <ArrowDownOutlined/>}
                            suffix="₮"
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card style={{minHeight: 80, display: 'flex', alignItems: 'center'}} bodyStyle={{padding: 8}}>
                        <Statistic
                            style={{flex: 1}}
                            title="盈亏比例"
                            value={(profit?.profitRatio || 0) * 100}
                            precision={2}
                            valueStyle={{color: (profit?.profitRatio || 0) >= 0 ? '#52c41a' : '#ff4d4f'}}
                            prefix={(profit?.profitRatio || 0) >= 0 ? <ArrowUpOutlined/> : <ArrowDownOutlined/>}
                            suffix="%"
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card style={{minHeight: 80, display: 'flex', alignItems: 'center'}} bodyStyle={{padding: 8}}>
                        <Statistic
                            style={{flex: 1}}
                            title="总交易次数"
                            value={profit?.tradeCount || 0}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card style={{minHeight: 80, display: 'flex', alignItems: 'center'}} bodyStyle={{padding: 8}}>
                        <Statistic
                            style={{flex: 1}}
                            title="胜率"
                            value={profit?.tradeCount ? ((profit.winningTrades || 0) / profit.tradeCount * 100) : 0}
                            precision={2}
                            suffix="%"
                        />
                    </Card>
                </Col>
            </Row>

            {/* 详细信息Tab */}
            <Tabs
                defaultActiveKey="positions"
                items={[
                    {
                        key: 'positions',
                        label: '当前持仓',
                        children: (
                            <Card>
                                <Table
                                    columns={positionColumns}
                                    dataSource={positions}
                                    rowKey="tradeId"
                                    loading={loading}
                                    pagination={false}
                                    size="small"
                                />
                            </Card>
                        )
                    },
                    {
                        key: 'trades',
                        label: '交易历史',
                        children: (
                            <Card>
                                <Table
                                    columns={tradeColumns}
                                    dataSource={trades}
                                    rowKey="tradeId"
                                    loading={loading}
                                    pagination={{pageSize: 20}}
                                    size="small"
                                />
                            </Card>
                        )
                    },
                    {
                        key: 'logs',
                        label: '运行日志',
                        children: (
                            <Card>
                                <div style={{marginBottom: 16}}>
                                    <Input.Search
                                        placeholder="搜索日志关键字"
                                        value={logKeyword}
                                        onChange={e => setLogKeyword(e.target.value)}
                                        onSearch={handleSearchLogs}
                                        enterButton={<SearchOutlined/>}
                                        style={{width: 300, marginRight: 16}}
                                    />
                                    <Button onClick={loadLogs}>显示全部</Button>
                                </div>
                                <div
                                    style={{
                                        backgroundColor: '#1e1e1e',
                                        padding: 12,
                                        borderRadius: 4,
                                        maxHeight: 500,
                                        overflow: 'auto'
                                    }}
                                >
                                    <pre
                                        ref={logRef}
                                        style={{
                                            margin: 0,
                                            color: '#d4d4d4',
                                            fontSize: 12,
                                            fontFamily: 'Consolas, Monaco, monospace',
                                            whiteSpace: 'pre-wrap',
                                            wordBreak: 'break-all'
                                        }}
                                    >
                                        {logs || '暂无日志'}
                                    </pre>
                                </div>
                            </Card>
                        )
                    }
                ]}
            />
        </div>
    );
};

export default InstanceMonitorPage;
