import React, {useEffect, useState} from 'react';
import {
    Button,
    Col,
    Form,
    Input,
    message,
    Modal,
    Popconfirm,
    Popover,
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
    MessageOutlined,
    PlusOutlined,
    ReloadOutlined,
    RobotOutlined
} from '@ant-design/icons';
import type {ColumnsType} from 'antd/es/table';
import {agentConfigService} from '../../services/agentConfigService';
import {skillConfigService, SkillConfig} from '../../services/skillConfigService';

const {Title, Text} = Typography;
const {TextArea} = Input;

// 智能体配置类型
interface AgentConfig {
    id?: number;
    name: string;
    systemPrompt?: string;
    tools?: string;
    skills?: string;
    modelConfigId?: number;
    description?: string;
    executionMode?: string; // 执行模式: LIVE/DRY_RUN
    isActive?: boolean;
    createdAt?: string;
    updatedAt?: string;
}

// Agent关系类型
interface AgentRelation {
    id?: number;
    agentId: number;
    subAgentId: number;
    relationType: string;
    priority?: number;
    delegationPrompt?: string;
    isActive?: boolean;
}

// 工具类型
interface ToolInfo {
    name: string;
    description: string;
}

// 模型类型
interface ModelInfo {
    configId: number;
    modelId: string;
    displayName: string;
    provider: string;
    modelType?: string;
}

const AgentConfig: React.FC = () => {
    // 状态管理
    const [configs, setConfigs] = useState<AgentConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingConfig, setEditingConfig] = useState<AgentConfig | null>(null);
    const [form] = Form.useForm();

    // 可用工具列表
    const [availableTools, setAvailableTools] = useState<ToolInfo[]>([]);
    // 可用模型列表
    const [availableModels, setAvailableModels] = useState<ModelInfo[]>([]);
    // 可用技能列表
    const [availableSkills, setAvailableSkills] = useState<SkillConfig[]>([]);
    const [loadingOptions, setLoadingOptions] = useState(false);

    // 加载智能体配置列表
    const loadConfigs = async () => {
        try {
            setLoading(true);
            const data = await agentConfigService.getAllAgentConfigs();
            setConfigs(data);
        } catch (error) {
            console.error('加载智能体配置失败:', error);
            message.error('加载智能体配置失败');
        } finally {
            setLoading(false);
        }
    };

    // 加载可用选项（工具、模型、技能列表）
    const loadOptions = async () => {
        setLoadingOptions(true);
        try {
            const [tools, models, skills] = await Promise.all([
                agentConfigService.getAvailableTools(),
                agentConfigService.getAvailableModels(),
                skillConfigService.getActiveSkillConfigs()
            ]);
            setAvailableTools(tools);
            setAvailableModels(models);
            setAvailableSkills(skills);
        } catch (error) {
            console.error('加载可用选项失败:', error);
            message.error('加载可用选项失败');
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
    const handleEdit = async (config: AgentConfig) => {
        setEditingConfig(config);
        // 解析工具列表
        let tools: string[] = [];
        if (config.tools) {
            try {
                tools = JSON.parse(config.tools);
            } catch {
                tools = [];
            }
        }
        // 解析技能列表
        let skills: number[] = [];
        if (config.skills) {
            try {
                skills = JSON.parse(config.skills);
            } catch {
                skills = [];
            }
        }

        // 加载子Agent关系
        let subAgents: number[] = [];
        let relationType = '';
        if (config.id) {
            try {
                const relations = await agentConfigService.getSubAgents(config.id);
                subAgents = relations.map((r: AgentRelation) => r.subAgentId);
                if (relations.length > 0) {
                    relationType = relations[0].relationType;
                }
            } catch (error) {
                console.error('加载子Agent关系失败:', error);
            }
        }

        form.setFieldsValue({
            ...config,
            tools,
            skills,
            subAgents,
            relationType
        });
        setModalVisible(true);
    };

    // 保存智能体配置
    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            // 将工具数组和技能数组转为JSON字符串
            const submitData = {
                ...values,
                tools: JSON.stringify(values.tools || []),
                skills: JSON.stringify(values.skills || [])
            };

            let savedAgentId: number | undefined;

            if (editingConfig && editingConfig.id) {
                // 更新
                await agentConfigService.updateAgentConfig(editingConfig.id, submitData);
                savedAgentId = editingConfig.id;
                message.success('智能体配置更新成功');
            } else {
                // 新增
                const newConfig = await agentConfigService.createAgentConfig(submitData);
                savedAgentId = newConfig.id;
                message.success('智能体配置创建成功');
            }

            // 保存子Agent关系
            if (savedAgentId && values.subAgents && values.subAgents.length > 0) {
                // 先删除旧关系
                const existingRelations = await agentConfigService.getSubAgents(savedAgentId);
                for (const relation of existingRelations) {
                    await agentConfigService.deleteAgentRelation(relation.id);
                }
                // 创建新关系
                for (let i = 0; i < values.subAgents.length; i++) {
                    await agentConfigService.saveAgentRelation({
                        agentId: savedAgentId,
                        subAgentId: values.subAgents[i],
                        relationType: values.relationType || 'MASTER_SLAVE',
                        priority: i,
                        isActive: true
                    });
                }
            }

            setModalVisible(false);
            loadConfigs();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('保存智能体配置失败');
            }
        }
    };

    // 删除智能体配置
    const handleDelete = async (config: AgentConfig) => {
        if (!config.id) {
            message.error('无效的智能体配置');
            return;
        }

        try {
            await agentConfigService.deleteAgentConfig(config.id);
            message.success('智能体配置删除成功');
            loadConfigs();
        } catch (error) {
            if (error instanceof Error) {
                message.error(error.message);
            } else {
                message.error('删除智能体配置失败');
            }
        }
    };

    // 打开AI Chat（临时会话，使用智能体预设）
    const handleChat = (config: AgentConfig) => {
        // 派发事件，传递智能体配置
        const event = new CustomEvent('agent-chat-start', {
            detail: {
                agentConfig: config
            }
        });
        window.dispatchEvent(event);
    };

    // 切换启用状态
    const handleToggleStatus = async (config: AgentConfig) => {
        if (!config.id) {
            message.error('无效的智能体配置');
            return;
        }

        try {
            const submitData = {
                ...config,
                isActive: !config.isActive
            };
            await agentConfigService.updateAgentConfig(config.id, submitData);
            message.success(`智能体已${config.isActive ? '禁用' : '启用'}`);
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

    // 解析技能ID列表显示
    const parseSkillsDisplay = (skillsJson: string): number[] => {
        if (!skillsJson) return [];
        try {
            return JSON.parse(skillsJson);
        } catch {
            return [];
        }
    };

    // 获取模型显示名称
    const getModelDisplayName = (modelConfigId: number): string => {
        const model = availableModels.find(m => m.configId === modelConfigId);
        const typeLabel = model?.modelType === 'LOCAL' ? '本地' : '远程';
        return model ? `${model.displayName} (${typeLabel})` : `ID: ${modelConfigId}`;
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
    const columns: ColumnsType<AgentConfig> = [
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
            title: '模型',
            dataIndex: 'modelConfigId',
            key: 'modelConfigId',
            width: 200,
            render: (modelConfigId) => {
                if (!modelConfigId) return <Text type="secondary">未绑定</Text>;
                const model = availableModels.find(m => m.configId === modelConfigId);
                if (model) {
                    const typeLabel = model.modelType === 'LOCAL' ? '本地' : '远程';
                    return (
                        <Tag color="blue">
                            {model.displayName} ({typeLabel})
                        </Tag>
                    );
                }
                return <Tag>ID: {modelConfigId}</Tag>;
            }
        },
        {
            title: '工具',
            dataIndex: 'tools',
            key: 'tools',
            width: 200,
            render: (tools) => {
                const toolList = parseToolsDisplay(tools);
                if (toolList.length === 0) {
                    return <Text type="secondary">未绑定</Text>;
                }
                return (
                    <Popover
                        content={
                            <div style={{maxHeight: '200px', overflowY: 'auto'}}>
                                <Space wrap>
                                    {toolList.map((tool, index) => (
                                        <Tag key={index} color="green">{getToolChineseName(tool)}</Tag>
                                    ))}
                                </Space>
                            </div>
                        }
                        trigger="hover"
                    >
                        <div style={{
                            maxWidth: '180px',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            cursor: 'pointer'
                        }}>
                            {toolList.length > 0 && (
                                <Tag color="green">{getToolChineseName(toolList[0])}</Tag>
                            )}
                            {toolList.length > 1 && (
                                <Tag color="blue">+{toolList.length - 1}</Tag>
                            )}
                        </div>
                    </Popover>
                );
            }
        },
        {
            title: '技能',
            dataIndex: 'skills',
            key: 'skills',
            width: 150,
            render: (skills) => {
                const skillIds = parseSkillsDisplay(skills);
                if (skillIds.length === 0) {
                    return <Text type="secondary">未绑定</Text>;
                }
                return (
                    <Space wrap>
                        {skillIds.map((skillId, index) => {
                            const skill = availableSkills.find(s => s.id === skillId);
                            return (
                                <Tag key={index} color="purple">
                                    {skill ? skill.name : `ID:${skillId}`}
                                </Tag>
                            );
                        })}
                    </Space>
                );
            }
        },
        {
            title: '执行模式',
            dataIndex: 'executionMode',
            key: 'executionMode',
            width: 100,
            render: (mode) => {
                if (!mode) {
                    return <Text type="secondary">继承上级</Text>;
                }
                return mode === 'DRY_RUN' 
                    ? <Tag color="orange">模拟模式</Tag>
                    : <Tag color="green">实盘模式</Tag>;
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
            width: 150,
            render: (_, record) => (
                <Space size="small">
                    <Tooltip title="聊天">
                        <Button
                            type="text"
                            icon={<MessageOutlined/>}
                            onClick={() => handleChat(record)}
                            size="small"
                        />
                    </Tooltip>
                    <Tooltip title="编辑">
                        <Button
                            type="text"
                            icon={<EditOutlined/>}
                            onClick={() => handleEdit(record)}
                            size="small"
                        />
                    </Tooltip>
                    <Popconfirm
                        title="确定要删除此智能体配置吗？"
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
                        <RobotOutlined style={{marginRight: '8px'}}/>
                        智能体配置管理
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
                            新增智能体
                        </Button>
                    </Space>
                </Col>
            </Row>

            <Table
                columns={columns}
                dataSource={configs}
                rowKey="id"
                loading={loading}
                scroll={{ x: 'max-content' }}
                pagination={{
                    showSizeChanger: true,
                    showQuickJumper: true,
                    showTotal: (total) => `共 ${total} 个智能体配置`,
                }}
            />

            {/* 新增/编辑弹窗 */}
            <Modal
                title={editingConfig ? '编辑智能体' : '新增智能体'}
                open={modalVisible}
                onOk={handleSave}
                onCancel={() => setModalVisible(false)}
                width={700}
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
                        label="智能体名称"
                        rules={[{required: true, message: '请输入智能体名称'}]}
                    >
                        <Input placeholder="请输入智能体名称" maxLength={100}/>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <Input placeholder="请输入描述" maxLength={500}/>
                    </Form.Item>

                    <Form.Item
                        name="systemPrompt"
                        label="系统预设Prompt"
                        tooltip="设置智能体的系统提示词，用于定义智能体的行为和角色"
                    >
                        <TextArea
                            rows={6}
                            placeholder="请输入系统预设Prompt"
                            maxLength={5000}
                            showCount
                        />
                    </Form.Item>

                    <Form.Item
                        name="modelConfigId"
                        label="模型"
                        tooltip="从大模型配置中选择绑定的模型"
                        rules={[{required: true, message: '请选择模型'}]}
                    >
                        <Select
                            placeholder="请选择模型"
                            loading={loadingOptions}
                            options={availableModels.map(model => ({
                                value: model.configId,
                                label: `${model.displayName} (${model.modelType === 'LOCAL' ? '本地' : '远程'})`
                            }))}
                        />
                    </Form.Item>

                    <Form.Item
                        name="tools"
                        label="工具"
                        tooltip="选择智能体可以使用的工具"
                    >
                        <Select
                            mode="multiple"
                            placeholder="请选择绑定的工具"
                            loading={loadingOptions}
                            options={availableTools.map(tool => ({
                                value: tool.name,
                                label: tool.description
                            }))}
                        />
                    </Form.Item>

                    <Form.Item
                        name="skills"
                        label="技能"
                        tooltip="选择智能体可以使用的技能"
                    >
                        <Select
                            mode="multiple"
                            placeholder="请选择绑定的技能"
                            loading={loadingOptions}
                            options={availableSkills.map(skill => ({
                                value: skill.id,
                                label: skill.name
                            }))}
                        />
                    </Form.Item>

                    <Form.Item
                        name="subAgents"
                        label="子Agent"
                        tooltip="选择该Agent可以调用的子Agent（多智能体协作）"
                    >
                        <Select
                            mode="multiple"
                            placeholder="请选择子Agent"
                            disabled={!editingConfig?.id}
                            options={configs
                                .filter(c => c.id !== editingConfig?.id)
                                .map(agent => ({
                                    value: agent.id,
                                    label: agent.name
                                }))}
                        />
                    </Form.Item>

                    <Form.Item
                        name="relationType"
                        label="协作模式"
                        tooltip="多Agent协作模式：主从（主Agent协调）、协作（平等协作）、层级（顺序调用）"
                    >
                        <Select
                            placeholder="单Agent模式"
                            allowClear
                            options={[
                                { value: 'MASTER_SLAVE', label: '主从模式' },
                                { value: 'COLLABORATIVE', label: '协作模式' },
                                { value: 'HIERARCHICAL', label: '层级模式' }
                            ]}
                        />
                    </Form.Item>

                    <Form.Item
                        name="executionMode"
                        label="执行模式"
                        tooltip="设置交易执行模式，优先级：智能体 > AI交易 > 全局配置"
                    >
                        <Select
                            placeholder="使用上级配置"
                            allowClear
                            options={[
                                { value: 'LIVE', label: '实盘模式 - 真实下单' },
                                { value: 'DRY_RUN', label: '模拟模式 - 不提交订单' }
                            ]}
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

export default AgentConfig;
