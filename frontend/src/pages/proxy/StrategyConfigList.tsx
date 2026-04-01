import React, {useEffect, useState} from 'react';
import {Button, Form, Input, message, Modal, Popconfirm, Space, Switch, Table, Tag, Typography} from 'antd';
import {DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons';
import {strategyConfigService} from '../../services/strategyConfigService';

const {Title} = Typography;

interface StrategyConfig {
    id?: number;
    title: string;
    strategyName: string;
    version?: string;
    prompt?: string;
    strategyCode?: string;
    description?: string;
    isActive?: boolean;
}

const StrategyConfigList: React.FC = () => {
    const [data, setData] = useState<StrategyConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editing, setEditing] = useState<StrategyConfig | null>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const configs = await strategyConfigService.getAllConfigs();
            setData(configs);
        } catch (e) {
            message.error('加载策略配置失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditing(null);
        form.resetFields();
        form.setFieldsValue({
            isActive: true
        });
        setModalVisible(true);
    };

    const handleEdit = (row: StrategyConfig) => {
        setEditing(row);
        form.setFieldsValue({
            title: row.title,
            strategyName: row.strategyName,
            version: row.version,
            prompt: row.prompt,
            strategyCode: row.strategyCode,
            description: row.description,
            isActive: row.isActive
        });
        setModalVisible(true);
    };

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            if (editing?.id) {
                await strategyConfigService.updateConfig(editing.id, values);
                message.success('更新成功');
            } else {
                await strategyConfigService.createConfig(values);
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
            await strategyConfigService.deleteConfig(id);
            message.success('删除成功');
            await loadData();
        } catch (e) {
            message.error('删除失败');
        }
    };

    const handleToggleStatus = async (row: StrategyConfig) => {
        try {
            await strategyConfigService.toggleStatus(row.id!);
            message.success(`已${row.isActive ? '禁用' : '启用'}`);
            await loadData();
        } catch (e) {
            message.error('操作失败');
        }
    };

    const columns = [
        {
            title: '标题',
            dataIndex: 'title',
            key: 'title',
            width: 150
        },
        {
            title: '名称',
            dataIndex: 'strategyName',
            key: 'strategyName',
            width: 120
        },
        {
            title: '版本',
            dataIndex: 'version',
            key: 'version',
            width: 80
        },
        {
            title: '状态',
            dataIndex: 'isActive',
            key: 'isActive',
            width: 80,
            render: (active: boolean, record: StrategyConfig) => (
                <Switch
                    checked={active}
                    onChange={() => handleToggleStatus(record)}
                    checkedChildren="启用"
                    unCheckedChildren="禁用"
                />
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
            width: 120,
            render: (_: any, record: StrategyConfig) => (
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
                <Title level={3}>策略配置</Title>
                <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
                    新建策略
                </Button>
            </div>

            <Table
                columns={columns}
                dataSource={data}
                rowKey="id"
                loading={loading}
                pagination={{pageSize: 10}}
            />

            <Modal
                title={editing ? '编辑策略配置' : '新建策略配置'}
                open={modalVisible}
                onOk={handleSubmit}
                onCancel={() => setModalVisible(false)}
                width={700}
                okText="保存"
                cancelText="取消"
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="title"
                        label="标题"
                        rules={[{required: true, message: '请输入标题'}]}
                    >
                        <Input placeholder="例如: 趋势跟踪策略" />
                    </Form.Item>

                    <Form.Item
                        name="strategyName"
                        label="策略名称"
                        rules={[{required: true, message: '请输入策略名称'}]}
                    >
                        <Input placeholder="例如: TrendFollowing" />
                    </Form.Item>

                    <Form.Item
                        name="version"
                        label="版本"
                    >
                        <Input placeholder="例如: v1.0.0" />
                    </Form.Item>

                    <Form.Item
                        name="prompt"
                        label="Prompt提示词"
                    >
                        <Input.TextArea rows={4} placeholder="策略的Prompt提示词" />
                    </Form.Item>

                    <Form.Item
                        name="strategyCode"
                        label="策略代码"
                    >
                        <Input.TextArea rows={10} placeholder="策略代码内容" />
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <Input.TextArea rows={2} placeholder="策略描述信息" />
                    </Form.Item>

                    <Form.Item
                        name="isActive"
                        label="启用状态"
                        valuePropName="checked"
                    >
                        <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default StrategyConfigList;
