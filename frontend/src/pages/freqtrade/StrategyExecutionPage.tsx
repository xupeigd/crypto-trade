import React, {useEffect, useState} from 'react';
import {
    Button,
    Card,
    Col,
    Form,
    message,
    Modal,
    Popconfirm,
    Radio,
    Row,
    Select,
    Space,
    Statistic,
    Table,
    Tag,
    Tooltip,
    Typography
} from 'antd';
import dayjs from 'dayjs';
import {
    DeleteOutlined,
    PlayCircleOutlined,
    ReloadOutlined,
    StopOutlined,
    MonitorOutlined
} from '@ant-design/icons';
import {useNavigate} from 'react-router-dom';
import {cexKeyService} from '../../services/cexKeyService';
import {CexKeyModel} from '../../types/cexKey';
import {
    freqtradeConfigService,
    FreqtradeConfig
} from '../../services/freqtradeConfigService';
import {strategyConfigService} from '../../services/strategyConfigService';
import {
    strategyExecutionService,
    FreqtradeInstance
} from '../../services/strategyExecutionService';

const {Title} = Typography;
const {Option} = Select;

const StrategyExecutionPage: React.FC = () => {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [starting, setStarting] = useState(false);
    const [apiKeys, setApiKeys] = useState<CexKeyModel[]>([]);
    const [freqtradeConfigs, setFreqtradeConfigs] = useState<FreqtradeConfig[]>([]);
    const [strategyConfigs, setStrategyConfigs] = useState<any[]>([]);
    const [instances, setInstances] = useState<FreqtradeInstance[]>([]);
    const [selectedApiKey, setSelectedApiKey] = useState<number | null>(null);
    const [selectedFreqtradeConfig, setSelectedFreqtradeConfig] = useState<number | null>(null);
    const [selectedStrategyConfig, setSelectedStrategyConfig] = useState<number | null>(null);
    const [isDryRun, setIsDryRun] = useState<boolean>(true);

    // 弹窗状态
    const [configModalVisible, setConfigModalVisible] = useState(false);
    const [configModalTitle, setConfigModalTitle] = useState('');
    const [configContent, setConfigContent] = useState('');
    const [apiKeyModalVisible, setApiKeyModalVisible] = useState(false);
    const [apiKeyInfo, setApiKeyInfo] = useState<CexKeyModel | null>(null);
    const [strategyModalVisible, setStrategyModalVisible] = useState(false);
    const [strategyInfo, setStrategyInfo] = useState<any>(null);

    useEffect(() => {
        loadData();
        // 每30秒刷新一次实例状态
        const interval = setInterval(() => {
            loadInstances();
        }, 30000);
        return () => clearInterval(interval);
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            await Promise.all([
                loadApiKeys(),
                loadFreqtradeConfigs(),
                loadStrategyConfigs(),
                loadInstances()
            ]);
        } finally {
            setLoading(false);
        }
    };

    const loadApiKeys = async () => {
        try {
            const keys = await cexKeyService.getAllKeys();
            // 只显示激活状态的API Key
            setApiKeys(keys.filter(k => k.status === 'active'));
        } catch (e) {
            console.error('加载API Key失败', e);
        }
    };

    const loadFreqtradeConfigs = async () => {
        try {
            const configs = await freqtradeConfigService.getActiveConfigs();
            setFreqtradeConfigs(configs);
        } catch (e) {
            console.error('加载Freqtrade配置失败', e);
        }
    };

    const loadStrategyConfigs = async () => {
        try {
            const configs = await strategyConfigService.getActiveConfigs();
            setStrategyConfigs(configs);
        } catch (e) {
            console.error('加载策略配置失败', e);
        }
    };

    const loadInstances = async () => {
        try {
            const data = await strategyExecutionService.getAllInstances();
            setInstances(data);
        } catch (e) {
            console.error('加载实例列表失败', e);
        }
    };

    const handleStart = async () => {
        if (!selectedApiKey) {
            message.warning('请选择API Key');
            return;
        }
        if (!selectedFreqtradeConfig) {
            message.warning('请选择Freqtrade配置');
            return;
        }
        if (!selectedStrategyConfig) {
            message.warning('请选择策略');
            return;
        }

        setStarting(true);
        try {
            await strategyExecutionService.startExecution(selectedApiKey, selectedFreqtradeConfig, selectedStrategyConfig, isDryRun);
            message.success('策略执行启动成功');
            await loadInstances();
        } catch (e) {
            if (e instanceof Error) {
                message.error(e.message);
            } else {
                message.error('启动失败');
            }
        } finally {
            setStarting(false);
        }
    };

    const handleStop = async (instanceId: number) => {
        try {
            await strategyExecutionService.stopExecution(instanceId);
            message.success('策略执行已停止');
            await loadInstances();
        } catch (e) {
            message.error('停止失败');
        }
    };

    const handleRestart = async (instanceId: number) => {
        try {
            await strategyExecutionService.restartExecution(instanceId);
            message.success('实例已启动');
            await loadInstances();
        } catch (e) {
            message.error('启动失败');
        }
    };

    const handleDelete = async (instanceId: number) => {
        try {
            await strategyExecutionService.deleteInstance(instanceId);
            message.success('实例已删除');
            await loadInstances();
        } catch (e) {
            message.error('删除失败');
        }
    };

    const handleMonitor = (instanceId: number) => {
        navigate(`/strategy-execution/monitor/${instanceId}`);
    };

    // 显示config.json弹窗
    const handleShowConfig = async (record: FreqtradeInstance) => {
        try {
            const content = await strategyExecutionService.getInstanceConfig(record.id);
            setConfigModalTitle(record.instanceName);
            setConfigContent(content);
            setConfigModalVisible(true);
        } catch (e) {
            message.error('获取配置失败');
        }
    };

    // 显示API Key弹窗
    const handleShowApiKey = (key: CexKeyModel) => {
        setApiKeyInfo(key);
        setApiKeyModalVisible(true);
    };

    // 显示策略详情弹窗
    const handleShowStrategy = (config: any) => {
        setStrategyInfo(config);
        setStrategyModalVisible(true);
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

    const getApiKeyInfo = (apiKeyId: number) => {
        const key = apiKeys.find(k => k.keyId === apiKeyId);
        return key;
    };

    const getStrategyInfo = (strategyConfigId: number) => {
        const config = strategyConfigs.find(c => c.id === strategyConfigId);
        return config;
    };

    // 检查实例状态（区分模拟/实盘）
    const checkInstanceStatus = (apiKeyId: number, freqtradeConfigId: number, strategyConfigId: number, isDryRun: boolean) => {
        const instance = instances.find(i =>
            i.apiKeyId === apiKeyId &&
            i.freqtradeConfigId === freqtradeConfigId &&
            i.strategyConfigId === strategyConfigId &&
            i.isDryRun === isDryRun
        );

        if (!instance) return 'none';
        if (instance.status === 'RUNNING' || instance.status === 'STARTING') return 'running';
        return 'exists';
    };

    // 判断实例状态
    const instanceStatus = selectedApiKey && selectedFreqtradeConfig && selectedStrategyConfig
        ? checkInstanceStatus(selectedApiKey, selectedFreqtradeConfig, selectedStrategyConfig, isDryRun)
        : 'none';

    const columns = [
        {
            title: 'ID',
            dataIndex: 'instanceId',
            key: 'instanceId',
            width: 100
        },
        {
            title: '名称',
            dataIndex: 'instanceName',
            key: 'instanceName',
            ellipsis: true,
            width: 180,
            render: (text: string, record: FreqtradeInstance) => (
                <a onClick={() => handleShowConfig(record)}>{text}</a>
            )
        },
        {
            title: 'API Key',
            dataIndex: 'apiKeyId',
            key: 'apiKeyId',
            width: 140,
            render: (apiKeyId: number) => {
                const key = getApiKeyInfo(apiKeyId);
                return key ? (
                    <a onClick={() => handleShowApiKey(key)}>{key.keyName}</a>
                ) : '-';
            }
        },
        {
            title: '类型',
            dataIndex: 'isDryRun',
            key: 'isDryRun',
            width: 100,
            render: (isDryRun: boolean) => isDryRun ?
                <Tag color="green">模拟</Tag> :
                <Tag color="red">实盘</Tag>
        },
        {
            title: '策略',
            key: 'strategyName',
            width: 140,
            render: (_: any, record: FreqtradeInstance) => {
                const config = getStrategyInfo(record.strategyConfigId);
                return config ? (
                    <a onClick={() => handleShowStrategy(config)}>{config.strategyName}</a>
                ) : '-';
            }
        },
        {
            title: '端口',
            dataIndex: 'apiPort',
            key: 'apiPort',
            width: 100
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: string) => getStatusTag(status)
        },
        {
            title: '盈亏比例',
            dataIndex: 'profitRatio',
            key: 'profitRatio',
            width: 120,
            render: (ratio: number) => ratio != null ? (
                <span style={{color: ratio >= 0 ? '#52c41a' : '#ff4d4f'}}>
                    {(ratio * 100).toFixed(2)}%
                </span>
            ) : '-'
        },
        {
            title: '交易次数',
            dataIndex: 'totalTrades',
            key: 'totalTrades',
            width: 100,
            render: (total: number, record: FreqtradeInstance) => (
                <span>
                    {total || 0}
                    {record.winningTrades != null && (
                        <span style={{color: '#52c41a', marginLeft: 4}}>
                            ({record.winningTrades}胜)
                        </span>
                    )}
                </span>
            )
        },
        {
            title: '启动时间',
            dataIndex: 'startedAt',
            key: 'startedAt',
            width: 180,
            render: (time: string) => time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
        },
        {
            title: '运行时长',
            key: 'runtime',
            width: 100,
            render: (_: any, record: FreqtradeInstance) => {
                if (record.status !== 'RUNNING' || !record.startedAt) return '-';
                const diff = dayjs().diff(dayjs(record.startedAt), 'millisecond');
                const days = Math.floor(diff / (1000 * 60 * 60 * 24));
                const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
                const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                let result = '';
                if (days > 0) result += `${days}d`;
                if (hours > 0) result += `${hours}h`;
                if (minutes > 0 || result === '') result += `${minutes}m`;
                return result;
            }
        },
        {
            title: '错误信息',
            dataIndex: 'lastError',
            key: 'lastError',
            ellipsis: true,
            render: (error: string) => error ? (
                <span style={{color: '#ff4d4f'}}>{error}</span>
            ) : '-'
        },
        {
            title: '操作',
            key: 'action',
            width: 160,
            render: (_: any, record: FreqtradeInstance) => (
                <Space size="small">
                    {record.status !== 'RUNNING' && record.status !== 'STARTING' && (
                        <Tooltip title="启动">
                            <Button
                                type="text"
                                icon={<PlayCircleOutlined/>}
                                onClick={() => handleRestart(record.id)}
                                size="small"
                            />
                        </Tooltip>
                    )}
                    {record.status === 'RUNNING' && (
                        <Tooltip title="停止">
                            <Button
                                type="text"
                                icon={<StopOutlined/>}
                                onClick={() => handleStop(record.id)}
                                danger
                                size="small"
                            />
                        </Tooltip>
                    )}
                    {record.status !== 'STARTING' && (
                        <Tooltip title="监控">
                            <Button
                                type="text"
                                icon={<MonitorOutlined/>}
                                onClick={() => handleMonitor(record.id)}
                                size="small"
                            />
                        </Tooltip>
                    )}
                    {record.status !== 'RUNNING' && record.status !== 'STARTING' && (
                        <Popconfirm
                            title="确定要删除此实例吗？"
                            onConfirm={() => handleDelete(record.id)}
                        >
                            <Tooltip title="删除">
                                <Button
                                    type="text"
                                    icon={<DeleteOutlined/>}
                                    danger
                                    size="small"
                                />
                            </Tooltip>
                        </Popconfirm>
                    )}
                </Space>
            )
        }
    ];

    const runningCount = instances.filter(i => i.status === 'RUNNING').length;

    return (
        <div style={{padding: '24px'}}>
            <Title level={3}>策略执行管理</Title>

            {/* 启动配置区域 */}
            <Card title="启动新策略" style={{marginBottom: 24}}>
                <Form layout="vertical">
                    <Row gutter={16}>
                        <Col span={6}>
                            <Form.Item label="API Key" rules={[{ required: true, message: '请选择API Key' }]}>
                                <Select
                                    placeholder="选择API Key"
                                    value={selectedApiKey}
                                    onChange={setSelectedApiKey}
                                    loading={loading}
                                >
                                    {apiKeys.map(key => (
                                        <Option key={key.keyId} value={key.keyId}>
                                            <Space>
                                                <span>{key.keyName}</span>
                                                <Tag color="blue">{key.cexName}</Tag>
                                                {key.isLiveTrading ? (
                                                    <Tag color="red">实盘</Tag>
                                                ) : (
                                                    <Tag color="green">模拟</Tag>
                                                )}
                                            </Space>
                                        </Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item label="Freqtrade配置" rules={[{ required: true, message: '请选择Freqtrade配置' }]}>
                                <Select
                                    placeholder="选择Freqtrade配置"
                                    value={selectedFreqtradeConfig}
                                    onChange={setSelectedFreqtradeConfig}
                                    loading={loading}
                                >
                                    {freqtradeConfigs.map(config => (
                                        <Option key={config.id} value={config.id}>
                                            {config.configName}
                                            <Tag color="blue" style={{marginLeft: 8}}>
                                                {config.startupMode === 'DOCKER' ? 'Docker' : '进程'}
                                            </Tag>
                                        </Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item label="策略配置" rules={[{ required: true, message: '请选择策略' }]}>
                                <Select
                                    placeholder="选择策略"
                                    value={selectedStrategyConfig}
                                    onChange={setSelectedStrategyConfig}
                                    loading={loading}
                                >
                                    {strategyConfigs.map(config => (
                                        <Option key={config.id} value={config.id}>
                                            {config.title}
                                            <span style={{color: '#999', marginLeft: 8}}>
                                                ({config.strategyName})
                                            </span>
                                        </Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={4}>
                            <Form.Item label="运行模式">
                                <Radio.Group
                                    value={isDryRun}
                                    onChange={(e) => setIsDryRun(e.target.value)}
                                    buttonStyle="solid"
                                >
                                    <Radio.Button value={true}>模拟</Radio.Button>
                                    <Radio.Button value={false}>实盘</Radio.Button>
                                </Radio.Group>
                            </Form.Item>
                        </Col>
                        <Col span={2} style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end' }}>
                            <Form.Item style={{ marginBottom: 0 }}>
                                <Space>
                                    <Tooltip title="刷新">
                                        <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading} />
                                    </Tooltip>
                                    <Tooltip title="启动策略">
                                        <Button
                                            type="primary"
                                            icon={<PlayCircleOutlined />}
                                            onClick={handleStart}
                                            loading={starting}
                                            disabled={!selectedApiKey || !selectedFreqtradeConfig || !selectedStrategyConfig || instanceStatus !== 'none'}
                                        >
                                            {instanceStatus === 'running' ? '运行中' : instanceStatus === 'exists' ? '已存在' : ''}
                                        </Button>
                                    </Tooltip>
                                </Space>
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Card>

            {/* 统计概览 */}
            <Row gutter={16} style={{marginBottom: 24}}>
                <Col span={6}>
                    <Card>
                        <Statistic title="总实例数" value={instances.length}/>
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="运行中"
                            value={runningCount}
                            valueStyle={{color: '#52c41a'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="已停止"
                            value={instances.filter(i => i.status === 'STOPPED').length}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="错误"
                            value={instances.filter(i => i.status === 'ERROR').length}
                            valueStyle={{color: '#ff4d4f'}}
                        />
                    </Card>
                </Col>
            </Row>

            {/* 实例列表 */}
            <Card title="运行实例列表" bodyStyle={{ padding: 0, overflow: 'hidden' }}>
                <div style={{ overflowX: 'auto' }}>
                    <Table
                        columns={columns}
                        dataSource={instances}
                        rowKey="id"
                        loading={loading}
                        pagination={{pageSize: 10}}
                        size="small"
                    />
                </div>
            </Card>

            {/* config.json 弹窗 */}
            <Modal
                title={configModalTitle}
                open={configModalVisible}
                onCancel={() => setConfigModalVisible(false)}
                footer={null}
                width={700}
            >
                <pre style={{maxHeight: 500, overflow: 'auto', background: 'rgb(17, 17, 17)', padding: 10}}>
                    {configContent}
                </pre>
            </Modal>

            {/* API Key 详情弹窗 */}
            <Modal
                title="API Key 信息"
                open={apiKeyModalVisible}
                onCancel={() => setApiKeyModalVisible(false)}
                footer={null}
                width={500}
            >
                {apiKeyInfo && (
                    <div>
                        <p><strong>名称：</strong>{apiKeyInfo.keyName}</p>
                        <p><strong>交易所：</strong>{apiKeyInfo.cexName}</p>
                        <p><strong>实盘：</strong>{apiKeyInfo.isLiveTrading ? '是' : '否'}</p>
                        <p><strong>备注：</strong>{apiKeyInfo.description || '-'}</p>
                    </div>
                )}
            </Modal>

            {/* 策略详情弹窗 */}
            <Modal
                title="策略详情"
                open={strategyModalVisible}
                onCancel={() => setStrategyModalVisible(false)}
                footer={null}
                width={700}
            >
                {strategyInfo && (
                    <div>
                        <p><strong>策略名称：</strong>{strategyInfo.strategyName}</p>
                        <p><strong>标题：</strong>{strategyInfo.title || '-'}</p>
                        <p><strong>版本：</strong>{strategyInfo.version || '-'}</p>
                        <p><strong>描述：</strong>{strategyInfo.description || '-'}</p>
                        <p><strong>策略代码：</strong></p>
                        <pre style={{maxHeight: 300, overflow: 'auto', background: 'rgb(17, 17, 17)', padding: 10}}>
                            {strategyInfo.strategyCode || '-'}
                        </pre>
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default StrategyExecutionPage;