import React, {useEffect, useState} from 'react';
import {DatePicker, Select, Space, Table, Tag, Typography,} from 'antd';
import {Execution} from '../../types/execution';
import {executionService} from '../../services/executionService';
import {taskService} from '../../services/taskService';
import {ScheduledTask} from '../../types/task';
import {usePageTimer} from '../../hooks/usePageTimer';
import RefreshIndicator from '../../components/RefreshIndicator';
import AutoRefreshToggle from '../../components/AutoRefreshToggle';
import dayjs from 'dayjs';

const {Title} = Typography;
const {Option} = Select;
const {RangePicker} = DatePicker;

const ExecutionList: React.FC = () => {
    const [executions, setExecutions] = useState<Execution[]>([]);
    const [tasks, setTasks] = useState<ScheduledTask[]>([]);
    const [selectedTask, setSelectedTask] = useState<number | undefined>();
    const [selectedStatus, setSelectedStatus] = useState<string | undefined>();
    const [selectedTriggerType, setSelectedTriggerType] = useState<string | undefined>();
    const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
    const [isManualLoading, setIsManualLoading] = useState(false); // 手动刷新loading状态

    // 数据加载函数
    const loadExecutionData = async () => {
        console.log('执行记录页面开始加载数据...');
        try {
            const [executionsData, tasksData] = await Promise.all([
                executionService.getAllExecutions(),
                taskService.getAllTasks(),
            ]);
            setExecutions(executionsData);
            setTasks(tasksData);
            console.log('执行记录数据更新完成');
        } catch (error) {
            console.error('获取执行记录数据失败:', error);
        }
    };

    // 页面初始化时立即加载数据
    useEffect(() => {
        console.log('执行记录页面组件挂载，立即加载数据');
        loadExecutionData();
    }, []); // 只在组件挂载时执行一次

    // 手动刷新函数
    const handleManualRefresh = async () => {
        if (isManualLoading) return;

        console.log('执行手动刷新，更新执行记录数据');
        setIsManualLoading(true);

        try {
            await loadExecutionData();
            console.log('手动刷新完成，执行记录数据已更新');
        } catch (error) {
            console.error('手动刷新失败:', error);
        } finally {
            setIsManualLoading(false);
        }
    };

    // 使用页面级定时器Hook，每5秒刷新一次
    const {
        isActive: autoRefreshEnabled,
        isPaused,
        retryCount,
        start: startTimer,
        stop: stopTimer,
        restart: refresh
    } = usePageTimer(
        loadExecutionData,
        {
            interval: 5000, // 5秒
            persistKey: 'executions-timer', // 页面独立的存储key
            maxRetries: 5,
            autoStart: false // 由手动控制，避免重复初始化
        }
    );

    // 模拟刷新状态，用于RefreshIndicator组件
    const getRefreshStatus = () => {
        if (isPaused) return 'paused';
        if (autoRefreshEnabled) return 'active';
        return 'idle';
    };

    // 移除初始化useEffect，因为usePageTimer Hook已经包含了首次数据加载逻辑

    // 删除旧的loadData函数，现在使用usePageTimer Hook

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'success':
                return 'green';
            case 'failed':
                return 'red';
            case 'running':
                return 'blue';
            case 'pending':
                return 'orange';
            case 'timeout':
                return 'volcano';
            case 'skipped':
                return 'default';
            default:
                return 'default';
        }
    };

    const getStatusText = (status: string) => {
        switch (status) {
            case 'success':
                return '成功';
            case 'failed':
                return '失败';
            case 'running':
                return '运行中';
            case 'pending':
                return '待执行';
            case 'timeout':
                return '超时';
            case 'skipped':
                return '跳过';
            default:
                return status;
        }
    };

    const getTriggerTypeText = (type: string) => {
        switch (type) {
            case 'cron':
                return '定时触发';
            case 'parent':
                return '父任务触发';
            case 'manual':
                return '手动触发';
            default:
                return type;
        }
    };

    const filteredExecutions = executions
        .filter(execution => {
            if (selectedTask && execution.taskId !== selectedTask) return false;
            if (selectedStatus && execution.executionStatus !== selectedStatus) return false;
            if (selectedTriggerType && execution.triggerType !== selectedTriggerType) return false;
            if (dateRange) {
                const executionTime = dayjs(execution.createdTime);
                const startTime = dateRange[0].startOf('day');
                const endTime = dateRange[1].endOf('day');
                if (executionTime.isBefore(startTime) || executionTime.isAfter(endTime)) return false;
            }
            return true;
        })
        .sort((a, b) => dayjs(b.triggerTime).valueOf() - dayjs(a.triggerTime).valueOf());

    const columns = [
        {
            title: '任务名称',
            dataIndex: 'taskId',
            key: 'taskId',
            render: (taskId: number) => {
                const task = tasks.find(t => t.taskId === taskId);
                return task ? task.taskName : `任务 ${taskId}`;
            },
        },
        {
            title: '触发类型',
            dataIndex: 'triggerType',
            key: 'triggerType',
            render: (type: string) => getTriggerTypeText(type),
        },
        {
            title: '父任务',
            dataIndex: 'parentTaskName',
            key: 'parentTaskName',
        },
        {
            title: '状态',
            dataIndex: 'executionStatus',
            key: 'executionStatus',
            render: (status: string) => (
                <Tag color={getStatusColor(status)}>
                    {getStatusText(status)}
                </Tag>
            ),
        },
        {
            title: '触发时间',
            dataIndex: 'triggerTime',
            key: 'triggerTime',
            render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            title: '执行时间',
            dataIndex: 'actualExecuteTime',
            key: 'actualExecuteTime',
            render: (time: string) => time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: '完成时间',
            dataIndex: 'finishTime',
            key: 'finishTime',
            render: (time: string) => time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-',
        },
        {
            title: '错误信息',
            dataIndex: 'errorMessage',
            key: 'errorMessage',
            ellipsis: true,
            render: (error: string) => error || '-',
        },
    ];

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                <Title level={2}>执行记录</Title>
                <Space>
                    <RefreshIndicator
                        status={getRefreshStatus()}
                        retryCount={retryCount}
                        maxRetries={5}
                    />
                    <AutoRefreshToggle
                        enabled={autoRefreshEnabled}
                        onChange={(enabled) => enabled ? startTimer() : stopTimer()}
                        onManualRefresh={handleManualRefresh}
                        loading={isManualLoading}
                    />
                </Space>
            </div>

            <div style={{marginBottom: 16}}>
                <Space>
                    <Select
                        placeholder="选择任务"
                        style={{width: 200}}
                        allowClear
                        onChange={setSelectedTask}
                    >
                        {tasks.map(task => (
                            <Option key={task.taskId} value={task.taskId!}>
                                {task.taskName}
                            </Option>
                        ))}
                    </Select>

                    <Select
                        placeholder="选择状态"
                        style={{width: 120}}
                        allowClear
                        onChange={setSelectedStatus}
                    >
                        <Option value="success">成功</Option>
                        <Option value="failed">失败</Option>
                        <Option value="running">运行中</Option>
                        <Option value="pending">待执行</Option>
                        <Option value="timeout">超时</Option>
                        <Option value="skipped">跳过</Option>
                    </Select>

                    <Select
                        placeholder="触发类型"
                        style={{width: 120}}
                        allowClear
                        onChange={setSelectedTriggerType}
                    >
                        <Option value="cron">定时触发</Option>
                        <Option value="parent">父任务触发</Option>
                        <Option value="manual">手动触发</Option>
                    </Select>

                    <RangePicker
                        placeholder={['开始日期', '结束日期']}
                        onChange={(dates) => setDateRange(dates as [dayjs.Dayjs, dayjs.Dayjs])}
                    />
                </Space>
            </div>

            <Table
                columns={columns}
                dataSource={filteredExecutions}
                rowKey="executionId"
                loading={isManualLoading}
                pagination={{pageSize: 20}}
                scroll={{x: 1000}}
            />
        </div>
    );
};

export default ExecutionList;