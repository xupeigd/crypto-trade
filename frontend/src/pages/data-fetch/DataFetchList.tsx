import React, {useEffect, useState} from 'react';
import {
    Button,
    Form,
    Input,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import {CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined, PlusOutlined,} from '@ant-design/icons';
import {CreateDataFetchConfigRequest, DataFetchConfig} from '../../types/dataFetch';
import {dataFetchService} from '../../services/dataFetchService';
import {taskService} from '../../services/taskService';
import {cexKeyService} from '../../services/cexKeyService';
import {proxyService} from '../../services/proxyService';
import {ScheduledTask} from '../../types/task';
import {CexApiKey} from '../../types/cexKey';
import {ProxyServiceConfig} from '../../types/proxy';

const {Title} = Typography;
const {Option} = Select;
const {TextArea} = Input;

const DataFetchList: React.FC = () => {
    const [configs, setConfigs] = useState<DataFetchConfig[]>([]);
    const [tasks, setTasks] = useState<ScheduledTask[]>([]);
    const [cexKeys, setCexKeys] = useState<CexApiKey[]>([]);
    const [proxyConfigs, setProxyConfigs] = useState<ProxyServiceConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingConfig, setEditingConfig] = useState<DataFetchConfig | null>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [configsData, tasksData, keysData, proxyData] = await Promise.all([
                dataFetchService.getAllConfigs(),
                taskService.getAllTasks(),
                cexKeyService.getAllKeys(),
                proxyService.getAllConfigs(),
            ]);
            setConfigs(configsData);
            setTasks(tasksData);
            setCexKeys(keysData);
            setProxyConfigs(proxyData);
        } catch (error) {
            message.error('加载数据失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditingConfig(null);
        form.resetFields();
        setModalVisible(true);
    };

    const handleEdit = (config: DataFetchConfig) => {
        setEditingConfig(config);
        form.setFieldsValue({
            ...config,
            requiresAuth: config.requiresAuth || false,
            requiresProxy: config.requiresProxy || false,
        });
        setModalVisible(true);
    };

    const handleDelete = async (configId: number) => {
        try {
            await dataFetchService.deleteConfig(configId);
            message.success('删除成功');
            loadData();
        } catch (error) {
            message.error('删除失败');
        }
    };

    const handleSubmit = async (values: CreateDataFetchConfigRequest) => {
        try {
            if (editingConfig?.configId) {
                await dataFetchService.updateConfig(editingConfig.configId, values);
                message.success('更新成功');
            } else {
                await dataFetchService.createConfig(values);
                message.success('创建成功');
            }
            setModalVisible(false);
            loadData();
        } catch (error) {
            message.error(editingConfig ? '更新失败' : '创建失败');
        }
    };

    const columns = [
        {
            title: '任务名称',
            dataIndex: 'taskId',
            key: 'taskId',
            width: 150,
            render: (taskId: number) => {
                const task = tasks.find(t => t.taskId === taskId);
                return task ? task.taskName : '未知任务';
            },
        },
        {
            title: 'Host',
            dataIndex: 'cexBaseUrl',
            key: 'cexBaseUrl',
            width: 200,
            ellipsis: true,
        },
        {
            title: 'API路径',
            dataIndex: 'apiPath',
            key: 'apiPath',
            width: 180,
        },
        {
            title: '方法',
            dataIndex: 'httpMethod',
            key: 'httpMethod',
            width: 100,
            render: (method: string) => (
                <Tag color={method === 'GET' ? 'blue' : method === 'POST' ? 'green' : 'orange'}>
                    {method}
                </Tag>
            ),
        },
        {
            title: '鉴权',
            dataIndex: 'requiresAuth',
            key: 'requiresAuth',
            width: 100,
            render: (requiresAuth: boolean) => (
                <Tag color={requiresAuth ? 'green' : 'default'}>
                    {requiresAuth ? <CheckOutlined/> : <CloseOutlined/>}
                </Tag>
            ),
        },
        {
            title: '代理',
            dataIndex: 'requiresProxy',
            key: 'requiresProxy',
            width: 150,
            render: (requiresProxy: boolean, record: DataFetchConfig) => {
                if (!requiresProxy) {
                    return (
                        <Tag color="default">
                            <CloseOutlined/>
                        </Tag>
                    );
                }

                const proxyConfig = record.proxyServiceConfig || proxyConfigs.find(proxy => proxy.proxyId === record.proxyId);
                if (proxyConfig) {
                    return (
                        <Tag color={proxyConfig.status === 'active' ? 'blue' : 'gold'}>
                            <CheckOutlined/> {proxyConfig.proxyName}{proxyConfig.status === 'active' ? '' : '（已禁用）'}
                        </Tag>
                    );
                }

                if (record.proxyId) {
                    return (
                        <Tag color="blue">
                            <CheckOutlined/> 代理ID: {record.proxyId}
                        </Tag>
                    );
                }

                return (
                    <Tag color="orange">
                        <CheckOutlined/> 代理配置缺失
                    </Tag>
                );
            },
        },
        {
            title: '数据处理类',
            dataIndex: 'dataProcessorClass',
            key: 'dataProcessorClass',
            width: 200,
            ellipsis: true,
        },
        {
            title: '目标表',
            dataIndex: 'targetDuckdbTable',
            key: 'targetDuckdbTable',
            width: 120,
        },
        {
            title: '操作',
            key: 'action',
            width: 160,
            fixed: 'right' as const,
            render: (_: any, record: DataFetchConfig) => (
                <Space size="small" wrap>
                    <Tooltip title="编辑数据获取配置">
                        <Button
                            type="link"
                            size="small"
                            icon={<EditOutlined/>}
                            onClick={() => handleEdit(record)}
                        />
                    </Tooltip>
                    <Popconfirm
                        title="确定删除这个配置吗？"
                        onConfirm={() => handleDelete(record.configId!)}
                        okText="确定"
                        cancelText="取消"
                    >
                        <Tooltip title="删除数据获取配置">
                            <Button
                                type="link"
                                size="small"
                                danger
                                icon={<DeleteOutlined/>}
                            />
                        </Tooltip>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const dataFetchTasks = tasks.filter(task => task.taskType === 'data_fetch');
    const activeCexKeys = cexKeys.filter(key => key.status === 'active');

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                <Title level={2}>数据获取配置管理</Title>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>
                    新建配置
                </Button>
            </div>

            <Table
                columns={columns}
                dataSource={configs}
                rowKey="configId"
                loading={loading}
                pagination={{pageSize: 10}}
                scroll={{x: 1400, y: 400}}
                size="middle"
            />

            <Modal
                title={editingConfig ? '编辑数据获取配置' : '新建数据获取配置'}
                open={modalVisible}
                onCancel={() => setModalVisible(false)}
                footer={null}
                width={800}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleSubmit}
                >
                    <Form.Item
                        name="taskId"
                        label="关联任务"
                        rules={[{required: true, message: '请选择关联任务'}]}
                    >
                        <Select placeholder="请选择数据获取任务">
                            {dataFetchTasks.map(task => (
                                <Option key={task.taskId} value={task.taskId!}>
                                    {task.taskName}
                                </Option>
                            ))}
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="cexBaseUrl"
                        label="CEX Base URL"
                        rules={[{required: true, message: '请输入CEX Base URL'}]}
                    >
                        <Input placeholder="例如: https://api.binance.com"/>
                    </Form.Item>

                    <Form.Item
                        name="apiPath"
                        label="API路径"
                        rules={[{required: true, message: '请输入API路径'}]}
                    >
                        <Input placeholder="例如: /api/v3/ticker/price"/>
                    </Form.Item>

                    <Form.Item
                        name="httpMethod"
                        label="HTTP方法"
                        rules={[{required: true, message: '请选择HTTP方法'}]}
                    >
                        <Select placeholder="请选择HTTP方法">
                            <Option value="GET">GET</Option>
                            <Option value="POST">POST</Option>
                            <Option value="PUT">PUT</Option>
                            <Option value="DELETE">DELETE</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="requiresAuth"
                        label="需要鉴权"
                        valuePropName="checked"
                    >
                        <Switch/>
                    </Form.Item>

                    <Form.Item
                        noStyle
                        shouldUpdate={(prevValues, currentValues) => prevValues.requiresAuth !== currentValues.requiresAuth}
                    >
                        {({getFieldValue}) =>
                            getFieldValue('requiresAuth') ? (
                                <Form.Item
                                    name="authKeyId"
                                    label="鉴权Key"
                                    rules={[{required: true, message: '请选择鉴权Key'}]}
                                >
                                    <Select placeholder="请选择API Key">
                                        {activeCexKeys.map(key => (
                                            <Option key={key.keyId} value={key.keyId!}>
                                                {key.keyName} ({key.cexName})
                                            </Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            ) : null
                        }
                    </Form.Item>

                    <Form.Item
                        name="signatureClass"
                        label="签名类"
                    >
                        <Input placeholder="例如: com.crypto.trade.impl.signature.BinanceSignatureService"/>
                    </Form.Item>

                    <Form.Item
                        name="requiresProxy"
                        label="使用代理"
                        valuePropName="checked"
                    >
                        <Switch/>
                    </Form.Item>

                    <Form.Item
                        noStyle
                        shouldUpdate={(prevValues, currentValues) => prevValues.requiresProxy !== currentValues.requiresProxy}
                    >
                        {({getFieldValue}) =>
                            getFieldValue('requiresProxy') ? (
                                <Form.Item
                                    name="proxyId"
                                    label="代理配置"
                                    rules={[{required: true, message: '请选择代理配置'}]}
                                >
                                    <Select placeholder="请选择代理配置">
                                        {proxyConfigs.filter(proxy => proxy.status === 'active').map(proxy => (
                                            <Option key={proxy.proxyId} value={proxy.proxyId!}>
                                                {proxy.proxyName} ({proxy.proxyType}://{proxy.serverHost}:{proxy.serverPort})
                                            </Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            ) : null
                        }
                    </Form.Item>

                    <Form.Item
                        name="dataProcessorClass"
                        label="数据处理类"
                        rules={[{required: true, message: '请输入数据处理类'}]}
                    >
                        <Input placeholder="例如: com.crypto.trade.impl.processor.MarketDataProcessor"/>
                    </Form.Item>

                    <Form.Item
                        name="targetDuckdbTable"
                        label="目标DuckDB表"
                        rules={[{required: true, message: '请输入目标DuckDB表名'}]}
                    >
                        <Input placeholder="例如: market_data"/>
                    </Form.Item>

                    <Form.Item
                        name="requestParams"
                        label="请求参数"
                    >
                        <TextArea rows={3} placeholder="JSON格式的请求参数"/>
                    </Form.Item>

                    <Form.Item
                        name="responseMapping"
                        label="响应映射"
                    >
                        <TextArea rows={3} placeholder="JSON格式的响应字段映射"/>
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                {editingConfig ? '更新' : '创建'}
                            </Button>
                            <Button onClick={() => setModalVisible(false)}>
                                取消
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default DataFetchList;
