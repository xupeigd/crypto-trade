import React, {useEffect, useState} from 'react';
import {
    App,
    Button,
    Col,
    Divider,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Row,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Tooltip,
    Typography
} from 'antd';
import {
    ApiOutlined,
    CheckCircleOutlined,
    ClusterOutlined,
    DeleteOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    LockOutlined,
    PlusOutlined,
    ReloadOutlined,
    SettingOutlined,
    StarFilled,
    StarOutlined
} from '@ant-design/icons';
import type {ColumnsType} from 'antd/es/table';
import {AIInfoModel, ApiFormat, ApiFormatLabels, ModelType, ModelTypeLabels} from '../../types/aiInfoModel';
import {aiModelConfigService} from '../../services/aiModelConfigService';

const {Title, Text} = Typography;
const {TextArea} = Input;

const AiModelConfig: React.FC = () => {
    const app = App.useApp();

    // 状态管理
    const [models, setModels] = useState<AIInfoModel[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingModel, setEditingModel] = useState<AIInfoModel | null>(null);
    const [form] = Form.useForm();
    const [selectedModelType, setSelectedModelType] = useState<ModelType>(ModelType.LOCAL);
    const [testingConnection, setTestingConnection] = useState(false);

    // 加载模型列表
    const loadModels = async () => {
        try {
            setLoading(true);
            const data = await aiModelConfigService.getAllModels();
            setModels(data);
        } catch (error) {
            console.error('加载模型配置失败:', error);
            message.error('加载模型配置失败');
        } finally {
            setLoading(false);
        }
    };

    // 初始加载数据
    useEffect(() => {
        loadModels();
    }, []);

    // 打开新增模型弹窗
    const handleAdd = () => {
        setEditingModel(null);
        setSelectedModelType(ModelType.LOCAL);
        form.resetFields();
        form.setFieldsValue({
            isActive: true,
            defaultModel: false,
            modelType: ModelType.LOCAL,
            maxTokens: 8192,
            costPerToken: 0.000001,
            timeoutSeconds: 30,
            retryCount: 3,
            maxConcurrent: 5
        });
        setModalVisible(true);
    };

    // 打开编辑模型弹窗
    const handleEdit = (model: AIInfoModel) => {
        setEditingModel(model);
        const modelType = (model.modelType as ModelType) || ModelType.LOCAL;
        setSelectedModelType(modelType);
        form.setFieldsValue(model);
        setModalVisible(true);
    };

    // 测试模型连接
    const handleTestConnection = async (model: AIInfoModel) => {
        if (!model.configId) {
            message.error('无效的模型配置');
            return;
        }

        setTestingConnection(true);
        try {
            const response = await aiModelConfigService.testModelConnection(model.configId);
            if (response.success) {
                message.success('模型连接测试成功');
            } else {
                message.error('模型连接测试失败: ' + response.message);
            }
        } catch (error) {
            message.error('测试连接时发生错误');
        } finally {
            setTestingConnection(false);
        }
    };

    // 模型类型改变处理
    const handleModelTypeChange = (value: string) => {
        setSelectedModelType(value as ModelType);
        // 切换模型类型时，重置相关字段的默认值
        if (value === ModelType.LOCAL) {
            form.setFieldsValue({
                apiFormat: ApiFormat.OLLAMA,
                timeoutSeconds: 30,
                retryCount: 3,
                maxConcurrent: 5
            });
        } else {
            form.setFieldsValue({
                apiFormat: ApiFormat.OPENAI,
                timeoutSeconds: 60,
                retryCount: 3,
                maxConcurrent: 3
            });
        }
    };

    // 保存模型
    const handleSave = async () => {
        try {
            const values = await form.validateFields();

            if (editingModel && editingModel.configId) {
                // 更新
                await aiModelConfigService.updateModel(editingModel.configId, values);
                message.success('模型配置更新成功');
            } else {
                // 新增
                await aiModelConfigService.createModel(values);
                message.success('模型配置创建成功');
            }

            setModalVisible(false);
            loadModels();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('保存模型配置失败');
            }
        }
    };

    // 删除模型
    const handleDelete = async (model: AIInfoModel) => {
        if (!model.configId) {
            message.error('无效的模型配置');
            return;
        }

        try {
            await aiModelConfigService.deleteModel(model.configId);
            message.success('模型配置删除成功');
            loadModels();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('删除模型配置失败');
            }
        }
    };

    // 切换默认模型
    const handleSetDefault = async (model: AIInfoModel) => {
        if (!model.configId) {
            message.error('无效的模型配置');
            return;
        }

        try {
            await aiModelConfigService.setDefaultModel(model.configId);
            message.success('默认模型设置成功');
            loadModels();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('设置默认模型失败');
            }
        }
    };

    // 切换模型状态
    const handleToggleStatus = async (model: AIInfoModel) => {
        if (!model.configId) {
            message.error('无效的模型配置');
            return;
        }

        try {
            await aiModelConfigService.toggleModelStatus(model.configId);
            message.success(`模型已${model.isActive ? '禁用' : '启用'}`);
            loadModels();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('切换模型状态失败');
            }
        }
    };

    // 定义表格列
    const columns: ColumnsType<AIInfoModel> = [
        {
            title: '模型ID',
            dataIndex: 'modelId',
            key: 'modelId',
            width: 150,
            render: (text) => <Text code>{text}</Text>
        },
        {
            title: '显示名称',
            dataIndex: 'displayName',
            key: 'displayName',
            width: 120,
        },
        {
            title: '提供商',
            dataIndex: 'provider',
            key: 'provider',
            width: 80,
            render: (text) => <Tag color="blue">{text}</Tag>
        },
        {
            title: '模型类型',
            dataIndex: 'modelType',
            key: 'modelType',
            width: 100,
            render: (text) => (
                <Tag color={text === ModelType.REMOTE ? 'orange' : 'green'}>
                    {ModelTypeLabels[text as ModelType] || text}
                </Tag>
            )
        },
        {
            title: 'API格式',
            dataIndex: 'apiFormat',
            key: 'apiFormat',
            width: 80,
            render: (text) => text ? <Tag color="cyan">{ApiFormatLabels[text as ApiFormat] || text}</Tag> : '-'
        },
        {
            title: '参数规模',
            dataIndex: 'parameterSize',
            key: 'parameterSize',
            width: 80,
            render: (text) => text ? <Tag color="green">{text}B</Tag> : '-'
        },
        {
            title: '状态',
            dataIndex: 'isActive',
            key: 'isActive',
            width: 80,
            render: (isActive, record) => (
                <Space>
                    <Switch
                        checked={isActive}
                        onChange={() => handleToggleStatus(record)}
                        disabled={record.defaultModel}
                        size="small"
                    />
                    {record.defaultModel && (
                        <Tooltip title="默认模型">
                            <StarFilled style={{color: '#faad14'}}/>
                        </Tooltip>
                    )}
                </Space>
            )
        },
        {
            title: '操作',
            key: 'action',
            width: 200,
            render: (_, record) => (
                <Space size="small">
                    <Tooltip title="编辑">
                        <Button
                            type="text"
                            icon={<EditOutlined/>}
                            onClick={() => handleEdit(record)}
                            size="small"
                        />
                    </Tooltip>

                    <Tooltip title="测试连接">
                        <Button
                            type="text"
                            icon={<CheckCircleOutlined/>}
                            onClick={() => handleTestConnection(record)}
                            loading={testingConnection}
                            size="small"
                        />
                    </Tooltip>

                    {!record.defaultModel && (
                        <Tooltip title="设为默认">
                            <Button
                                type="text"
                                icon={<StarOutlined/>}
                                onClick={() => handleSetDefault(record)}
                                size="small"
                            />
                        </Tooltip>
                    )}

                    <Popconfirm
                        title="确定要删除此模型配置吗？"
                        description="删除后模型将被禁用，不会实际删除数据。"
                        onConfirm={() => handleDelete(record)}
                        icon={<ExclamationCircleOutlined style={{color: 'red'}}/>}
                        disabled={record.defaultModel}
                    >
                        <Tooltip title={record.defaultModel ? "不能删除默认模型" : "删除"}>
                            <Button
                                type="text"
                                danger
                                icon={<DeleteOutlined/>}
                                size="small"
                                disabled={record.defaultModel}
                            />
                        </Tooltip>
                    </Popconfirm>
                </Space>
            )
        }
    ];

    return (
        <div style={{padding: '24px'}}>
            <Row justify="space-between" align="middle" style={{marginBottom: '16px'}}>
                <Col>
                    <Title level={4} style={{margin: 0}}>
                        <ClusterOutlined style={{marginRight: '8px'}}/>
                        AI模型配置管理
                    </Title>
                </Col>
                <Col>
                    <Space>
                        <Button
                            icon={<ReloadOutlined/>}
                            onClick={loadModels}
                            loading={loading}
                        >
                            刷新
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined/>}
                            onClick={handleAdd}
                        >
                            新增模型
                        </Button>
                    </Space>
                </Col>
            </Row>

            <Table
                columns={columns}
                dataSource={models}
                rowKey="configId"
                loading={loading}
                pagination={{
                    showSizeChanger: true,
                    showQuickJumper: true,
                    showTotal: (total) => `共 ${total} 个模型配置`,
                }}
                expandable={{
                    expandedRowRender: (record) => (
                        <div style={{margin: 0}}>
                            <Row gutter={[16, 8]}>
                                <Col span={24}>
                                    <Text strong>描述：{record.description || '暂无描述'}</Text>
                                </Col>
                                <Col span={8}>
                                    <Text>API URL：</Text>
                                    <br/>
                                    <Text code>{record.apiUrl || '未配置'}</Text>
                                </Col>
                                <Col span={8}>
                                    <Text>API 密钥：</Text>
                                    <br/>
                                    <Text
                                        code>{record.apiKey ? '••••••••••••••••••••••••••••••••' : '未配置'}</Text>
                                </Col>
                                <Col span={8}>
                                    <Text>超时时间：{record.timeoutSeconds || '-'}秒</Text>
                                    <br/>
                                    <Text>重试次数：{record.retryCount || '-'}次</Text>
                                    <br/>
                                    <Text>最大并发：{record.maxConcurrent || '-'}</Text>
                                </Col>
                            </Row>
                        </div>
                    ),
                    rowExpandable: (record) => !!(record.description || record.apiUrl || record.apiKey),
                }}
            />

            {/* 新增/编辑模型弹窗 */}
            <Modal
                title={editingModel ? '编辑模型配置' : '新增模型配置'}
                open={modalVisible}
                onOk={handleSave}
                onCancel={() => setModalVisible(false)}
                width={600}
                okText="保存"
                cancelText="取消"
            >
                <Form
                    form={form}
                    layout="vertical"
                    initialValues={{
                        isActive: true,
                        defaultModel: false,
                        modelType: ModelType.LOCAL,
                        parameterSize: 7,
                        maxTokens: 8192,
                        costPerToken: 0.000001,
                        timeoutSeconds: 30,
                        retryCount: 3,
                        maxConcurrent: 5,
                        apiFormat: ApiFormat.OLLAMA
                    }}
                >
                    {/* 基础信息 */}
                    <Form.Item
                        name="modelType"
                        label="模型类型"
                        rules={[{required: true, message: '请选择模型类型'}]}
                    >
                        <Select
                            placeholder="请选择模型类型"
                            onChange={handleModelTypeChange}
                            options={[
                                {label: ModelTypeLabels[ModelType.LOCAL], value: ModelType.LOCAL},
                                {label: ModelTypeLabels[ModelType.REMOTE], value: ModelType.REMOTE}
                            ]}
                        />
                    </Form.Item>

                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="modelId"
                                label="模型ID"
                                rules={[
                                    {required: true, message: '请输入模型ID'},
                                    {pattern: /^[a-zA-Z0-9:-]+$/, message: '模型ID只能包含字母、数字、冒号和连字符'}
                                ]}
                            >
                                <Input
                                    placeholder={selectedModelType === ModelType.LOCAL ? "例如: qwen2.5:4b" : "例如: gpt-3.5-turbo"}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="displayName"
                                label="显示名称"
                                rules={[{required: true, message: '请输入显示名称'}]}
                            >
                                <Input placeholder="例如: Qwen2.5 4B"/>
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="provider"
                                label="提供商"
                                rules={[{required: true, message: '请输入提供商'}]}
                            >
                                <Input placeholder="例如: qwen"/>
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="parameterSize"
                                label="参数规模(B)"
                            >
                                <InputNumber
                                    min={0}
                                    max={1000}
                                    placeholder="例如: 4"
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Form.Item
                        name="description"
                        label="模型描述"
                    >
                        <TextArea
                            rows={2}
                            placeholder="请输入模型描述"
                        />
                    </Form.Item>

                    {/* API配置 */}
                    <Divider orientation="left">
                        <span><ApiOutlined/> API配置</span>
                    </Divider>

                    <Form.Item
                        name="apiUrl"
                        label={selectedModelType === ModelType.LOCAL ? "服务地址" : "API地址"}
                        rules={[
                            {
                                required: true,
                                message: selectedModelType === ModelType.LOCAL ? '请输入服务地址' : '请输入API地址'
                            }
                        ]}
                    >
                        <Input
                            placeholder={
                                selectedModelType === ModelType.LOCAL
                                    ? "例如: http://localhost:11434"
                                    : "例如: https://api.openai.com"
                            }
                        />
                    </Form.Item>

                    {selectedModelType === ModelType.REMOTE && (
                        <Row gutter={16}>
                            <Col span={12}>
                                <Form.Item
                                    name="apiKey"
                                    label="API密钥"
                                    rules={[{required: true, message: '请输入API密钥'}]}
                                >
                                    <Input.Password
                                        placeholder="请输入API密钥"
                                        suffix={<LockOutlined/>}
                                    />
                                </Form.Item>
                            </Col>
                            <Col span={12}>
                                <Form.Item
                                    name="apiFormat"
                                    label="API格式"
                                    rules={[{required: true, message: '请选择API格式'}]}
                                >
                                    <Select
                                        placeholder="请选择API格式"
                                        options={[
                                            {label: ApiFormatLabels[ApiFormat.OPENAI], value: ApiFormat.OPENAI},
                                            {label: ApiFormatLabels[ApiFormat.CLAUDE], value: ApiFormat.CLAUDE},
                                            {label: ApiFormatLabels[ApiFormat.CUSTOM], value: ApiFormat.CUSTOM}
                                        ]}
                                    />
                                </Form.Item>
                            </Col>
                        </Row>
                    )}

                    {/* 性能配置 */}
                    <Divider orientation="left">
                        <span><SettingOutlined/> 性能配置</span>
                    </Divider>

                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="maxTokens"
                                label="最大Token数"
                            >
                                <InputNumber
                                    min={1}
                                    max={1000000}
                                    placeholder="例如: 8192"
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="costPerToken"
                                label="成本/Token($)"
                            >
                                <InputNumber
                                    min={0}
                                    max={1}
                                    step={0.000001}
                                    precision={6}
                                    placeholder="例如: 0.000001"
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="timeoutSeconds"
                                label="超时时间(秒)"
                            >
                                <InputNumber
                                    min={5}
                                    max={300}
                                    placeholder="例如: 60"
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="retryCount"
                                label="重试次数"
                            >
                                <InputNumber
                                    min={0}
                                    max={10}
                                    placeholder="例如: 3"
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="maxConcurrent"
                                label="最大并发"
                            >
                                <InputNumber
                                    min={1}
                                    max={100}
                                    placeholder="例如: 5"
                                    style={{width: '100%'}}
                                />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="isActive"
                                label="启用状态"
                                valuePropName="checked"
                            >
                                <Switch checkedChildren="启用" unCheckedChildren="禁用"/>
                            </Form.Item>
                        </Col>
                    </Row>

                    {/* 额外请求参数配置 */}
                    {(selectedModelType === ModelType.REMOTE) && (
                        <>
                            <Divider orientation="left" style={{marginTop: '16px'}}>
                                <span><ApiOutlined/> 额外请求参数</span>
                            </Divider>
                            <Form.Item
                                name="extraBody"
                                label="附加请求参数 (JSON格式)"
                                tooltip={{
                                    title: '可以添加自定义的API请求参数,格式为JSON。这些参数会覆盖默认参数。',
                                    placement: 'top'
                                }}
                                rules={[
                                    {
                                        validator: async (_, value) => {
                                            if (!value || value.trim() === '') {
                                                return Promise.resolve();
                                            }
                                            try {
                                                JSON.parse(value);
                                                return Promise.resolve();
                                            } catch (e) {
                                                return Promise.reject(new Error('JSON格式不正确,请检查语法'));
                                            }
                                        }
                                    }
                                ]}
                            >
                                <Input.TextArea
                                    rows={6}
                                    placeholder={`示例:\n{\n  "temperature": 0.8,\n  "top_p": 0.9,\n  "thinking": {\n    "budget_tokens": 10000\n  }\n}`}
                                    style={{fontFamily: 'monospace', fontSize: '13px'}}
                                />
                            </Form.Item>
                            <div style={{marginTop: '-16px', marginBottom: '16px', paddingLeft: '2px'}}>
                                <Text type="secondary" style={{fontSize: '12px'}}>
                                    💡 提示: 此处填写的参数会合并到API请求中,可以覆盖默认参数(如temperature、max_tokens等)
                                </Text>
                            </div>
                        </>
                    )}

                    <Form.Item
                        name="defaultModel"
                        label="默认模型"
                        valuePropName="checked"
                    >
                        <Switch checkedChildren="是" unCheckedChildren="否"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default AiModelConfig;