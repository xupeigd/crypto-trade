import React, {useEffect, useState} from 'react';
import {
    Button,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import {
    DeleteOutlined,
    EditOutlined,
    GlobalOutlined,
    LinkOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    PlusOutlined,
} from '@ant-design/icons';
import {AssociatedTask, CreateProxyServiceConfigRequest, ProxyServiceConfig,} from '../../types/proxy';
import {proxyService} from '../../services/proxyService';

const {Title} = Typography;
const {Option} = Select;
const {TextArea} = Input;

const ProxyList: React.FC = () => {
    const [proxies, setProxies] = useState<ProxyServiceConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingProxy, setEditingProxy] = useState<ProxyServiceConfig | null>(null);
    const [taskModalVisible, setTaskModalVisible] = useState(false);
    const [associatedTasks, setAssociatedTasks] = useState<AssociatedTask[]>([]);
    const [testingConnection, setTestingConnection] = useState<number | null>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        loadProxies();
    }, []);

    const loadProxies = async () => {
        setLoading(true);
        try {
            const data = await proxyService.getAllConfigs();

            // 为每个代理配置获取关联任务数量
            const proxiesWithTaskCount = await Promise.all(
                data.map(async (proxy) => {
                    if (proxy.proxyId) {
                        try {
                            const taskCountResponse = await proxyService.getAssociatedTaskCount(proxy.proxyId);
                            return {...proxy, associatedTaskCount: taskCountResponse?.count || 0};
                        } catch (error) {
                            console.error(`Failed to get task count for proxy ${proxy.proxyId}:`, error);
                            return {...proxy, associatedTaskCount: 0};
                        }
                    }
                    return proxy;
                })
            );

            setProxies(proxiesWithTaskCount);
        } catch (error) {
            message.error('加载代理配置列表失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditingProxy(null);
        form.resetFields();
        setModalVisible(true);
    };

    const handleEdit = (proxy: ProxyServiceConfig) => {
        setEditingProxy(proxy);
        form.setFieldsValue(proxy);
        setModalVisible(true);
    };

    const handleDelete = async (proxyId: number) => {
        try {
            await proxyService.deleteConfig(proxyId);
            message.success('删除成功');
            loadProxies();
        } catch (error: any) {
            if (error.response?.status === 409 || error.message?.includes('任务使用')) {
                message.error(error.response?.data?.message || error.message || '该代理配置被任务使用，无法删除');
            } else {
                message.error('删除失败');
            }
        }
    };

    const handleStatusChange = async (proxyId: number, status: 'active' | 'inactive') => {
        try {
            await proxyService.updateConfig(proxyId, {status});
            message.success('状态更新成功');
            loadProxies();
        } catch (error) {
            message.error('状态更新失败');
        }
    };

    const handleTestConnection = async (proxyId: number) => {
        setTestingConnection(proxyId);
        try {
            const result = await proxyService.testConnection(proxyId);
            if (result?.success) {
                message.success('代理连接测试成功');
            } else {
                message.error(`代理连接测试失败: ${result?.message || '未知错误'}`);
            }
        } catch (error) {
            message.error('代理连接测试失败');
        } finally {
            setTestingConnection(null);
        }
    };

    const handleViewTasks = async (proxyId: number) => {
        try {
            const tasks = await proxyService.getAssociatedTasks(proxyId);
            setAssociatedTasks(tasks);
            setTaskModalVisible(true);
        } catch (error) {
            message.error('获取关联任务失败');
        }
    };

    const handleSubmit = async (values: CreateProxyServiceConfigRequest) => {
        try {
            if (editingProxy?.proxyId) {
                await proxyService.updateConfig(editingProxy.proxyId, values);
                message.success('更新成功');
            } else {
                await proxyService.createConfig(values);
                message.success('创建成功');
            }
            setModalVisible(false);
            loadProxies();
        } catch (error) {
            message.error(editingProxy ? '更新失败' : '创建失败');
        }
    };

    const columns = [
        {
            title: '代理名称',
            dataIndex: 'proxyName',
            key: 'proxyName',
            width: 150,
        },
        {
            title: '类型',
            dataIndex: 'proxyType',
            key: 'proxyType',
            width: 100,
            render: (type: string) => (
                <Tag color={type === 'HTTP' ? 'blue' : 'green'}>
                    {type}
                </Tag>
            ),
        },
        {
            title: '地址:端口',
            key: 'address',
            width: 180,
            render: (_: any, record: ProxyServiceConfig) => (
                <span>{record.serverHost}:{record.serverPort}</span>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: string) => (
                <Tag color={status === 'active' ? 'green' : 'red'}>
                    {status === 'active' ? '活跃' : '禁用'}
                </Tag>
            ),
        },
        {
            title: '关联任务',
            dataIndex: 'associatedTaskCount',
            key: 'associatedTaskCount',
            width: 120,
            render: (count: number, record: ProxyServiceConfig) => {
                if (count && count > 0) {
                    return (
                        <Button
                            type="link"
                            icon={<LinkOutlined/>}
                            onClick={() => record.proxyId && handleViewTasks(record.proxyId)}
                        >
                            {count}
                        </Button>
                    );
                }
                return <span style={{color: '#999'}}>0</span>;
            },
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            width: 200,
            ellipsis: true,
        },
        {
            title: '操作',
            key: 'action',
            width: 280,
            fixed: 'right' as const,
            render: (_: any, record: ProxyServiceConfig) => (
                <Space size="small" wrap>
                    <Tooltip title="测试代理服务器连接">
                        <Button
                            type="link"
                            size="small"
                            icon={<GlobalOutlined/>}
                            loading={testingConnection === record.proxyId}
                            onClick={() => record.proxyId && handleTestConnection(record.proxyId)}
                            disabled={testingConnection === record.proxyId}
                        />
                    </Tooltip>
                    <Tooltip title={record.status === 'active' ? '禁用代理服务器' : '启用代理服务器'}>
                        <Button
                            type="link"
                            size="small"
                            icon={record.status === 'active' ? <PauseCircleOutlined/> : <PlayCircleOutlined/>}
                            onClick={() => record.proxyId && handleStatusChange(record.proxyId, record.status === 'active' ? 'inactive' : 'active')}
                            disabled={testingConnection === record.proxyId}
                        />
                    </Tooltip>
                    <Tooltip title="编辑代理服务器配置">
                        <Button
                            type="link"
                            size="small"
                            icon={<EditOutlined/>}
                            onClick={() => handleEdit(record)}
                            disabled={testingConnection === record.proxyId}
                        />
                    </Tooltip>
                    <Popconfirm
                        title="确定删除这个代理配置吗？"
                        description={record.associatedTaskCount && record.associatedTaskCount > 0
                            ? `该代理配置被 ${record.associatedTaskCount} 个任务使用，无法删除。请先解除关联。`
                            : undefined}
                        onConfirm={() => record.proxyId && handleDelete(record.proxyId)}
                        okText="确定"
                        cancelText="取消"
                        disabled={Boolean(record.associatedTaskCount && record.associatedTaskCount > 0)}
                    >
                        <Tooltip title="删除代理服务器配置">
                            <Button
                                type="link"
                                size="small"
                                danger
                                icon={<DeleteOutlined/>}
                                disabled={Boolean(record.associatedTaskCount && record.associatedTaskCount > 0)}
                            />
                        </Tooltip>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    const taskColumns = [
        {
            title: '任务名称',
            dataIndex: 'taskName',
            key: 'taskName',
            width: 180,
        },
        {
            title: '任务类型',
            dataIndex: 'taskType',
            key: 'taskType',
            width: 120,
            render: (type: string) => (
                <Tag color={type === 'data_fetch' ? 'blue' : 'purple'}>
                    {type === 'data_fetch' ? '数据获取' : '数据计算'}
                </Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 100,
            render: (status: string) => (
                <Tag color={status === 'active' ? 'green' : 'red'}>
                    {status === 'active' ? '活跃' : '禁用'}
                </Tag>
            ),
        },
        {
            title: 'Cron表达式',
            dataIndex: 'cronExpression',
            key: 'cronExpression',
            width: 160,
            render: (cron: string) => cron || '-',
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            width: 200,
            ellipsis: true,
        },
    ];

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                <Title level={2}>代理服务器配置</Title>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>
                    新建代理配置
                </Button>
            </div>

            <Table
                columns={columns}
                dataSource={proxies}
                rowKey="proxyId"
                loading={loading}
                pagination={{pageSize: 10}}
                scroll={{x: 1300, y: 400}}
                size="middle"
            />

            <Modal
                title={editingProxy ? '编辑代理配置' : '新建代理配置'}
                open={modalVisible}
                onCancel={() => setModalVisible(false)}
                footer={null}
                width={600}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleSubmit}
                >
                    <Form.Item
                        name="proxyName"
                        label="代理名称"
                        rules={[{required: true, message: '请输入代理名称'}]}
                    >
                        <Input placeholder="请输入代理名称"/>
                    </Form.Item>

                    <Form.Item
                        name="proxyType"
                        label="代理类型"
                        rules={[{required: true, message: '请选择代理类型'}]}
                    >
                        <Select placeholder="请选择代理类型">
                            <Option value="HTTP">HTTP</Option>
                            <Option value="SOCKS5">SOCKS5</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="serverHost"
                        label="服务器地址"
                        rules={[{required: true, message: '请输入服务器地址'}]}
                    >
                        <Input placeholder="请输入服务器地址，如: 127.0.0.1"/>
                    </Form.Item>

                    <Form.Item
                        name="serverPort"
                        label="服务器端口"
                        rules={[{required: true, message: '请输入服务器端口'}]}
                    >
                        <InputNumber
                            min={1}
                            max={65535}
                            placeholder="请输入服务器端口，如: 8080"
                            style={{width: '100%'}}
                        />
                    </Form.Item>

                    <Form.Item
                        name="status"
                        label="状态"
                        rules={[{required: true, message: '请选择状态'}]}
                    >
                        <Select placeholder="请选择状态">
                            <Option value="active">活跃</Option>
                            <Option value="inactive">禁用</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <TextArea rows={3} placeholder="请输入代理配置描述"/>
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                {editingProxy ? '更新' : '创建'}
                            </Button>
                            <Button onClick={() => setModalVisible(false)}>
                                取消
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                title="关联任务列表"
                open={taskModalVisible}
                onCancel={() => setTaskModalVisible(false)}
                footer={[
                    <Button key="close" onClick={() => setTaskModalVisible(false)}>
                        关闭
                    </Button>
                ]}
                width={800}
            >
                <Table
                    columns={taskColumns}
                    dataSource={associatedTasks}
                    rowKey="taskId"
                    pagination={{pageSize: 5}}
                    scroll={{x: 880}}
                    size="middle"
                    locale={{
                        emptyText: '暂无关联任务'
                    }}
                />
            </Modal>
        </div>
    );
};

export default ProxyList;