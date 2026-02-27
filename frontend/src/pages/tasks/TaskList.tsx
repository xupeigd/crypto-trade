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
import {CronEditor} from '../../components/cron';
import {DeleteOutlined, EditOutlined, PauseCircleOutlined, PlayCircleOutlined, PlusOutlined,} from '@ant-design/icons';
import {CreateTaskRequest, ScheduledTask} from '../../types/task';
import {taskService} from '../../services/taskService';

const {Title} = Typography;
const {Option} = Select;
const {TextArea} = Input;

const TaskList: React.FC = () => {
    const [tasks, setTasks] = useState<ScheduledTask[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingTask, setEditingTask] = useState<ScheduledTask | null>(null);
    const [form] = Form.useForm();

    useEffect(() => {
        loadTasks();
    }, []);

    const loadTasks = async () => {
        setLoading(true);
        try {
            const data = await taskService.getAllTasks();
            setTasks(data);
        } catch (error) {
            message.error('加载任务列表失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditingTask(null);
        form.resetFields();
        setModalVisible(true);
    };

    const handleEdit = (task: ScheduledTask) => {
        setEditingTask(task);
        form.setFieldsValue(task);
        setModalVisible(true);
    };

    const handleDelete = async (taskId: number) => {
        try {
            await taskService.deleteTask(taskId);
            message.success('删除成功');
            loadTasks();
        } catch (error) {
            message.error('删除失败');
        }
    };

    const handleStatusChange = async (taskId: number, status: 'active' | 'inactive') => {
        try {
            await taskService.updateTaskStatus(taskId, status);
            message.success('状态更新成功');
            loadTasks();
        } catch (error) {
            message.error('状态更新失败');
        }
    };

    const handleExecute = async (taskId: number) => {
        try {
            await taskService.executeTask(taskId);
            message.success('任务执行已触发');
        } catch (error) {
            message.error('任务执行失败');
        }
    };

    const handleSubmit = async (values: CreateTaskRequest) => {
        try {
            if (editingTask?.taskId) {
                await taskService.updateTask(editingTask.taskId, values);
                message.success('更新成功');
            } else {
                await taskService.createTask(values);
                message.success('创建成功');
            }
            setModalVisible(false);
            loadTasks();
        } catch (error) {
            message.error(editingTask ? '更新失败' : '创建失败');
        }
    };

    const columns = [
        {
            title: '任务名称',
            dataIndex: 'taskName',
            key: 'taskName',
            width: 150,
        },
        {
            title: '任务类型',
            dataIndex: 'taskType',
            key: 'taskType',
            width: 120,
            render: (type: string) => (
                <Tag color={type === 'data_fetch' ? 'blue' : 'green'}>
                    {type === 'data_fetch' ? '数据获取' : '数据计算'}
                </Tag>
            ),
        },
        {
            title: 'Cron表达式',
            dataIndex: 'cronExpression',
            key: 'cronExpression',
            width: 160,
        },
        {
            title: '超时时间(秒)',
            dataIndex: 'timeoutSeconds',
            key: 'timeoutSeconds',
            width: 120,
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
            render: (_: any, record: ScheduledTask) => (
                <Space size="small" wrap>
                    <Tooltip title="立即执行任务">
                        <Button
                            type="link"
                            size="small"
                            icon={<PlayCircleOutlined/>}
                            onClick={() => handleExecute(record.taskId!)}
                        />
                    </Tooltip>
                    <Tooltip title={record.status === 'active' ? '禁用任务' : '启用任务'}>
                        <Button
                            type="link"
                            size="small"
                            icon={record.status === 'active' ? <PauseCircleOutlined/> : <PlayCircleOutlined/>}
                            onClick={() => handleStatusChange(record.taskId!, record.status === 'active' ? 'inactive' : 'active')}
                        />
                    </Tooltip>
                    <Tooltip title="编辑任务配置">
                        <Button
                            type="link"
                            size="small"
                            icon={<EditOutlined/>}
                            onClick={() => handleEdit(record)}
                        />
                    </Tooltip>
                    <Popconfirm
                        title="确定删除这个任务吗？"
                        onConfirm={() => handleDelete(record.taskId!)}
                        okText="确定"
                        cancelText="取消"
                    >
                        <Tooltip title="删除任务">
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

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                <Title level={2}>定时任务管理</Title>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>
                    新建任务
                </Button>
            </div>

            <Table
                columns={columns}
                dataSource={tasks}
                rowKey="taskId"
                loading={loading}
                pagination={{pageSize: 10}}
                scroll={{x: 1200, y: 400}}
                size="middle"
            />

            <Modal
                title={editingTask ? '编辑任务' : '新建任务'}
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
                        name="taskName"
                        label="任务名称"
                        rules={[{required: true, message: '请输入任务名称'}]}
                    >
                        <Input placeholder="请输入任务名称"/>
                    </Form.Item>

                    <Form.Item
                        name="taskType"
                        label="任务类型"
                        rules={[{required: true, message: '请选择任务类型'}]}
                    >
                        <Select placeholder="请选择任务类型">
                            <Option value="data_fetch">数据获取</Option>
                            <Option value="data_calculation">数据计算</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="cronExpression"
                        label="Cron表达式"
                    >
                        <CronEditor
                            placeholder="请配置定时执行规则"
                        />
                    </Form.Item>

                    <Form.Item
                        name="timeoutSeconds"
                        label="超时时间(秒)"
                        rules={[{required: true, message: '请输入超时时间'}]}
                    >
                        <InputNumber min={1} max={3600} style={{width: '100%'}}/>
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
                        <TextArea rows={3} placeholder="请输入任务描述"/>
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                {editingTask ? '更新' : '创建'}
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

export default TaskList;