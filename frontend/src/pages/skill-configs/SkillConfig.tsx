import React, {useEffect, useState} from 'react';
import {
    Button,
    Col,
    Form,
    Input,
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
    DeleteOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    PlusOutlined,
    ReloadOutlined,
    ThunderboltOutlined
} from '@ant-design/icons';
import type {ColumnsType} from 'antd/es/table';
import {skillConfigService, SkillConfig} from '../../services/skillConfigService';

const {Title, Text} = Typography;
const {TextArea} = Input;

// 工具类型
interface ToolInfo {
    name: string;
    description: string;
}

const SkillConfigPage: React.FC = () => {
    // 状态管理
    const [configs, setConfigs] = useState<SkillConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingConfig, setEditingConfig] = useState<SkillConfig | null>(null);
    const [form] = Form.useForm();

    // 可用工具列表
    const [availableTools, setAvailableTools] = useState<ToolInfo[]>([]);
    const [loadingOptions, setLoadingOptions] = useState(false);

    // 加载技能配置列表
    const loadConfigs = async () => {
        try {
            setLoading(true);
            const data = await skillConfigService.getAllSkillConfigs();
            setConfigs(data);
        } catch (error) {
            console.error('加载技能配置失败:', error);
            message.error('加载技能配置失败');
        } finally {
            setLoading(false);
        }
    };

    // 加载可用工具列表
    const loadOptions = async () => {
        setLoadingOptions(true);
        try {
            const tools = await skillConfigService.getAvailableTools();
            setAvailableTools(tools);
        } catch (error) {
            console.error('加载可用工具失败:', error);
            message.error('加载可用工具失败');
        } finally {
            setLoadingOptions(false);
        }
    };

    // 初始加载数据
    useEffect(() => {
        loadConfigs();
        loadOptions();
    }, []);

    // 打开新增弹窗
    const handleAdd = () => {
        setEditingConfig(null);
        form.resetFields();
        form.setFieldsValue({
            isActive: true
        });
        setModalVisible(true);
    };

    // 打开编辑弹窗
    const handleEdit = (config: SkillConfig) => {
        setEditingConfig(config);
        // 解析工具列表
        let tools: string[] = [];
        if (config.requiredTools) {
            try {
                tools = JSON.parse(config.requiredTools);
            } catch {
                tools = [];
            }
        }
        form.setFieldsValue({
            ...config,
            requiredTools: tools
        });
        setModalVisible(true);
    };

    // 保存技能配置
    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            // 将工具数组转为JSON字符串
            const submitData: SkillConfig = {
                ...values,
                requiredTools: JSON.stringify(values.requiredTools || [])
            };

            if (editingConfig && editingConfig.id) {
                // 更新
                await skillConfigService.updateSkillConfig(editingConfig.id, submitData);
                message.success('技能配置更新成功');
            } else {
                // 新增
                await skillConfigService.createSkillConfig(submitData);
                message.success('技能配置创建成功');
            }

            setModalVisible(false);
            loadConfigs();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('保存技能配置失败');
            }
        }
    };

    // 删除技能配置
    const handleDelete = async (config: SkillConfig) => {
        if (!config.id) {
            message.error('无效的技能配置');
            return;
        }

        try {
            await skillConfigService.deleteSkillConfig(config.id);
            message.success('技能配置删除成功');
            loadConfigs();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('删除技能配置失败');
            }
        }
    };

    // 切换启用状态
    const handleToggleStatus = async (config: SkillConfig) => {
        if (!config.id) {
            message.error('无效的技能配置');
            return;
        }

        try {
            const submitData: SkillConfig = {
                ...config,
                isActive: !config.isActive
            };
            await skillConfigService.updateSkillConfig(config.id, submitData);
            message.success(`技能已${config.isActive ? '禁用' : '启用'}`);
            loadConfigs();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('切换状态失败');
            }
        }
    };

    // 解析工具列表显示
    const parseToolsDisplay = (toolsJson: string): string[] => {
        if (!toolsJson) return [];
        try {
            return JSON.parse(toolsJson);
        } catch {
            return [];
        }
    };

    // 工具名称映射为中文
    const toolNameMap: Record<string, string> = {
        'k_line': 'K线数据查询',
        'position_info': '仓位查询',
        'balance_info': '账户余额查询'
    };

    // 获取工具中文名称
    const getToolChineseName = (toolName: string): string => {
        return toolNameMap[toolName] || toolName;
    };

    // 定义表格列
    const columns: ColumnsType<SkillConfig> = [
        {
            title: '名称',
            dataIndex: 'name',
            key: 'name',
            width: 150,
            render: (text) => <Text strong>{text}</Text>
        },
        {
            title: '描述',
            dataIndex: 'description',
            key: 'description',
            width: 200,
            ellipsis: true,
            render: (text) => text || '-'
        },
        {
            title: '依赖工具',
            dataIndex: 'requiredTools',
            key: 'requiredTools',
            width: 200,
            render: (tools) => {
                const toolList = parseToolsDisplay(tools);
                if (toolList.length === 0) {
                    return <Text type="secondary">无依赖</Text>;
                }
                return (
                    <Space wrap>
                        {toolList.map((tool, index) => (
                            <Tag key={index} color="blue">{getToolChineseName(tool)}</Tag>
                        ))}
                    </Space>
                );
            }
        },
        {
            title: '状态',
            dataIndex: 'isActive',
            key: 'isActive',
            width: 80,
            render: (isActive, record) => (
                <Switch
                    checked={isActive}
                    onChange={() => handleToggleStatus(record)}
                    size="small"
                />
            )
        },
        {
            title: '操作',
            key: 'action',
            width: 100,
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
                    <Popconfirm
                        title="确定要删除此技能配置吗？"
                        description="删除后数据将无法恢复。"
                        onConfirm={() => handleDelete(record)}
                        icon={<ExclamationCircleOutlined style={{color: 'red'}}/>}
                    >
                        <Tooltip title="删除">
                            <Button
                                type="text"
                                danger
                                icon={<DeleteOutlined/>}
                                size="small"
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
                        <ThunderboltOutlined style={{marginRight: '8px'}}/>
                        技能配置管理
                    </Title>
                </Col>
                <Col>
                    <Space>
                        <Button
                            icon={<ReloadOutlined/>}
                            onClick={() => {
                                loadConfigs();
                                loadOptions();
                            }}
                            loading={loading}
                        >
                            刷新
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined/>}
                            onClick={handleAdd}
                        >
                            新增技能
                        </Button>
                    </Space>
                </Col>
            </Row>

            <Table
                columns={columns}
                dataSource={configs}
                rowKey="id"
                loading={loading}
                pagination={{
                    showSizeChanger: true,
                    showQuickJumper: true,
                    showTotal: (total) => `共 ${total} 个技能配置`,
                }}
            />

            {/* 新增/编辑弹窗 */}
            <Modal
                title={editingConfig ? '编辑技能' : '新增技能'}
                open={modalVisible}
                onOk={handleSave}
                onCancel={() => setModalVisible(false)}
                width={800}
                okText="保存"
                cancelText="取消"
            >
                <Form
                    form={form}
                    layout="vertical"
                    initialValues={{
                        isActive: true
                    }}
                >
                    <Form.Item
                        name="name"
                        label="技能名称"
                        rules={[{required: true, message: '请输入技能名称'}]}
                    >
                        <Input placeholder="请输入技能名称" maxLength={100}/>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <Input placeholder="请输入描述" maxLength={500}/>
                    </Form.Item>

                    <Form.Item
                        name="skillPrompt"
                        label="Prompt模板"
                        tooltip="指导LLM如何执行这个技能的Prompt"
                        rules={[{required: true, message: '请输入Prompt模板'}]}
                    >
                        <TextArea
                            rows={8}
                            placeholder="请输入Prompt模板，用于指导LLM执行这个技能"
                            maxLength={10000}
                            showCount
                        />
                    </Form.Item>

                    <Form.Item
                        name="outputFormat"
                        label="输出格式要求"
                        tooltip="定义技能输出的格式要求"
                    >
                        <TextArea
                            rows={4}
                            placeholder="请输入输出格式要求，例如JSON Schema格式"
                            maxLength={5000}
                            showCount
                        />
                    </Form.Item>

                    <Form.Item
                        name="requiredTools"
                        label="依赖工具"
                        tooltip="选择技能执行时需要使用的工具"
                    >
                        <Select
                            mode="multiple"
                            placeholder="请选择依赖的工具"
                            loading={loadingOptions}
                            options={availableTools.map(tool => ({
                                value: tool.name,
                                label: tool.description
                            }))}
                        />
                    </Form.Item>

                    <Form.Item
                        name="executionHint"
                        label="执行流程提示"
                        tooltip="定义技能的执行步骤"
                    >
                        <TextArea
                            rows={4}
                            placeholder="请输入执行流程提示，例如：1. 先查询K线数据 2. 分析趋势 3. 给出建议"
                            maxLength={5000}
                            showCount
                        />
                    </Form.Item>

                    <Form.Item
                        name="isActive"
                        label="启用状态"
                        valuePropName="checked"
                    >
                        <Switch checkedChildren="启用" unCheckedChildren="禁用"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default SkillConfigPage;
