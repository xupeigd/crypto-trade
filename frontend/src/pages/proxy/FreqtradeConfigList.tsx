import React, {useEffect, useState} from 'react';
import {Button, Form, Input, InputNumber, message, Modal, Popconfirm, Select, Space, Table, Tag, Typography} from 'antd';
import {DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons';
import {freqtradeConfigService} from '../../services/freqtradeConfigService';

const {Title} = Typography;
const {Option} = Select;

const STARTUP_MODE_OPTIONS = [
    { value: 'PROCESS', label: '进程模式' },
    { value: 'DOCKER', label: 'Docker模式' }
];

interface FreqtradeConfig {
    id?: number;
    configName: string;
    startupMode: string;
    processPath?: string;
    dockerImage?: string;
    userDataDir?: string;
    apiHost?: string;
    apiPort?: number;
    portRangeMin?: number;
    portRangeMax?: number;
    isActive?: boolean;
    description?: string;
}

const FreqtradeConfigList: React.FC = () => {
    const [data, setData] = useState<FreqtradeConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editing, setEditing] = useState<FreqtradeConfig | null>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const configs = await freqtradeConfigService.getAllConfigs();
            setData(configs);
        } catch (e) {
            message.error('加载Freqtrade配置失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({
            startupMode: 'DOCKER',
            apiHost: '127.0.0.1',
            portRangeMin: 8081,
            portRangeMax: 8999,
            isActive: true
        });
        setModalVisible(true);
    };

    const handleEdit = (row: FreqtradeConfig) => {
        setEditing(row);
        form.setFieldsValue({
            configName: row.configName,
            startupMode: row.startupMode,
            processPath: row.processPath,
            dockerImage: row.dockerImage,
            userDataDir: row.userDataDir,
            apiHost: row.apiHost,
            portRangeMin: row.portRangeMin,
            portRangeMax: row.portRangeMax,
            isActive: row.isActive,
            description: row.description
        });
        setModalVisible(true);
    };

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            if (editing?.id) {
                await freqtradeConfigService.updateConfig(editing.id, values);
                message.success('更新成功');
            } else {
                await freqtradeConfigService.createConfig(values);
                message.success('创建成功');
            }
            setModalVisible(false);
            await loadData();
        } catch (e) {
            if (e instanceof Error) {
                message.error(e.message);
            } else {
                message.error('保存失败');
            }
        }
    };

    const handleDelete = async (id?: number) => {
        if (!id) {
            return;
        }
        try {
            await freqtradeConfigService.deleteConfig(id);
            message.success('删除成功');
            await loadData();
        } catch (e) {
            message.error('删除失败');
        }
    };

    const handleToggleStatus = async (row: FreqtradeConfig) => {
        try {
            await freqtradeConfigService.toggleStatus(row.id!);
            message.success(`已${row.isActive ? '禁用' : '启用'}`);
            await loadData();
        } catch (e) {
            message.error('操作失败');
        }
    };

    const columns = [
        {
            title: '配置名称',
            dataIndex: 'configName',
            key: 'configName',
            width: 150
        },
        {
            title: '启动模式',
            dataIndex: 'startupMode',
            key: 'startupMode',
            width: 100,
            render: (mode: string) => (
                <Tag color={mode === 'DOCKER' ? 'blue' : 'green'}>
                    {mode === 'DOCKER' ? 'Docker' : '进程'}
                </Tag>
            )
        },
        {
            title: '用户数据目录',
            dataIndex: 'userDataDir',
            key: 'userDataDir',
            ellipsis: true
        },
        {
            title: '状态',
            dataIndex: 'isActive',
            key: 'isActive',
            width: 80,
            render: (active: boolean) => (
                <Tag color={active ? 'success' : 'default'}>
                    {active ? '启用' : '禁用'}
                </Tag>
            )
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            ellipsis: true
        },
        {
            title: '操作',
            key: 'action',
            width: 150,
            render: (_: any, record: FreqtradeConfig) => (
                <Space size="small">
                    <Button
                        type="text"
                        icon={<EditOutlined />}
                        onClick={() => handleEdit(record)}
                        size="small"
                    />
                    <Popconfirm
                        title="确定要删除此配置吗？"
                        onConfirm={() => handleDelete(record.id)}
                        icon={<DeleteOutlined style={{color: 'red'}} />}
                    >
                        <Button
                            type="text"
                            icon={<DeleteOutlined />}
                            danger
                            size="small"
                        />
                    </Popconfirm>
                </Space>
            )
        }
    ];

    return (
        <div style={{padding: '24px'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: '16px'}}>
                <Title level={3}>Freqtrade配置</Title>
                <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
                    新建配置
                </Button>
            </div>

            <Table
                size="small"
                columns={columns}
                dataSource={data}
                rowKey="id"
                loading={loading}
                pagination={{pageSize: 10}}
            />

            <Modal
                title={editing ? '编辑Freqtrade配置' : '新建Freqtrade配置'}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                width={600}
                okText="保存"
                cancelText="取消"
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="configName"
                        label="配置名称"
                        rules={[{required: true, message: '请输入配置名称'}]}
                    >
                        <Input placeholder="例如: 主交易配置" />
                    </Form.Item>

                    <Form.Item
                        name="startupMode"
                        label="启动模式"
                        rules={[{required: true, message: '请选择启动模式'}]}
                    >
                        <Select placeholder="请选择启动模式">
                            {STARTUP_MODE_OPTIONS.map(option => (
                                <Option key={option.value} value={option.value}>
                                    {option.label}
                                </Option>
                            ))}
                        </Select>
                    </Form.Item>

                    <Form.Item
                        noStyle
                        shouldUpdate={(prev, curr) => prev.startupMode !== curr.startupMode}
                    >
                        {() => (
                            <>
                                {form.getFieldValue('startupMode') === 'PROCESS' && (
                                    <Form.Item
                                        name="processPath"
                                        label="进程启动脚本路径"
                                        rules={[{required: true, message: '请输入进程启动脚本路径'}]}
                                    >
                                        <Input placeholder="例如: /opt/freqtrade/freqtrade.sh" />
                                    </Form.Item>
                                )}

                                {form.getFieldValue('startupMode') === 'DOCKER' && (
                                    <Form.Item
                                        name="dockerImage"
                                        label="Docker镜像"
                                        rules={[{required: true, message: '请输入Docker镜像'}]}
                                    >
                                        <Input placeholder="例如: freqtradeorg/freqtrade:stable" />
                                    </Form.Item>
                                )}
                            </>
                        )}
                    </Form.Item>

                    <Form.Item
                        name="userDataDir"
                        label="用户数据目录"
                        rules={[{required: true, message: '请输入用户数据目录'}]}
                        extra="包含配置文件(config.json)、策略文件(strategies/)、数据库(freqtrade.db)等"
                    >
                        <Input placeholder="例如: /opt/freqtrade/user_data" />
                    </Form.Item>

                    <Form.Item
                        name="apiHost"
                        label="API地址"
                    >
                        <Input placeholder="127.0.0.1" />
                    </Form.Item>

                    <Form.Item label="端口范围" required>
                        <Space>
                            <Form.Item
                                name="portRangeMin"
                                noStyle
                                rules={[{required: true, message: '请输入最小端口'}]}
                            >
                                <InputNumber min={1024} max={65535} placeholder="8081" style={{width: 120}} />
                            </Form.Item>
                            <span>至</span>
                            <Form.Item
                                name="portRangeMax"
                                noStyle
                                rules={[{required: true, message: '请输入最大端口'}]}
                            >
                                <InputNumber min={1024} max={65535} placeholder="8999" style={{width: 120}} />
                            </Form.Item>
                        </Space>
                    </Form.Item>

                    <Form.Item
                        name="isActive"
                        label="启用状态"
                    >
                        <Select>
                            <Option value={true}>启用</Option>
                            <Option value={false}>禁用</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <Input.TextArea rows={2} placeholder="配置描述信息" />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default FreqtradeConfigList;