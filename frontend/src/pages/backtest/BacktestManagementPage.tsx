import React, { useEffect, useState } from 'react';
import {
    Button,
    Card,
    Col,
    DatePicker,
    Form,
    Input,
    message,
    Popconfirm,
    Row,
    Select,
    Space,
    Statistic,
    Table,
    Tag,
    Tooltip,
    Typography,
    Modal
} from 'antd';
import {
    PlayCircleOutlined,
    ReloadOutlined,
    StopOutlined,
    LineChartOutlined,
    DeleteOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { backtestService, BacktestTaskResponse } from '../../services/backtestService';
import { freqtradeConfigService, FreqtradeConfig } from '../../services/freqtradeConfigService';
import { strategyConfigService } from '../../services/strategyConfigService';
import BacktestResultModal from './BacktestResultModal';

const { Title } = Typography;
const { Option } = Select;
const { RangePicker } = DatePicker;

const BacktestManagementPage: React.FC = () => {
    const [loading, setLoading] = useState(false);
    const [starting, setStarting] = useState(false);
    const [tasks, setTasks] = useState<BacktestTaskResponse[]>([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(1);
    
    const [freqtradeConfigs, setFreqtradeConfigs] = useState<FreqtradeConfig[]>([]);
    const [strategyConfigs, setStrategyConfigs] = useState<any[]>([]);
    
    const [form] = Form.useForm();

    const [resultModalVisible, setResultModalVisible] = useState(false);
    const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);

    useEffect(() => {
        loadConfigs();
        loadTasks();
    }, []);

    useEffect(() => {
        // Poll if any task is RUNNING or PENDING
        const hasActiveTasks = (tasks || []).some(t => t.status === 'RUNNING' || t.status === 'PENDING');
        if (hasActiveTasks) {
            const timer = setInterval(() => {
                loadTasks(page, false);
            }, 10000);
            return () => clearInterval(timer);
        }
    }, [tasks, page]);

    const loadConfigs = async () => {
        try {
            const fConfigs = await freqtradeConfigService.getActiveConfigs();
            setFreqtradeConfigs(fConfigs);
            const sConfigs = await strategyConfigService.getActiveConfigs();
            setStrategyConfigs(sConfigs);
        } catch (e) {
            console.error('Failed to load configs', e);
        }
    };

    const loadTasks = async (pageNum = page, showLoading = true) => {
        if (showLoading) setLoading(true);
        try {
            const res = await backtestService.getBacktests(undefined, pageNum - 1, 10);
            setTasks(res?.content || []);
            setTotal(res?.totalElements || 0);
        } catch (e) {
            if (showLoading) message.error('加载回测任务失败');
        } finally {
            if (showLoading) setLoading(false);
        }
    };

    const handleStart = async (values: any) => {
        setStarting(true);
        try {
            const timeRange = `${values.timerange[0].format('YYYYMMDD')}-${values.timerange[1].format('YYYYMMDD')}`;
            await backtestService.createBacktest({
                taskName: values.taskName,
                freqtradeConfigId: values.freqtradeConfigId,
                strategyConfigId: values.strategyConfigId,
                timeRange,
                timeframe: values.timeframe
            });
            message.success('回测任务已启动');
            form.resetFields();
            setPage(1);
            loadTasks(1);
        } catch (e: any) {
            message.error(e.message || '启动失败');
        } finally {
            setStarting(false);
        }
    };

    const handleStop = async (taskId: number) => {
        try {
            await backtestService.stopBacktest(taskId);
            message.success('已发送停止指令');
            loadTasks();
        } catch (e) {
            message.error('停止失败');
        }
    };

    const handleDelete = async (taskId: number) => {
        try {
            await backtestService.deleteBacktest(taskId);
            message.success('删除成功');
            loadTasks();
        } catch (e) {
            message.error('删除失败');
        }
    };

    const openResult = (taskId: number, taskStatus: string) => {
        setSelectedTaskId(taskId);
        setSelectedTaskStatus(taskStatus);
        setResultModalVisible(true);
    };

    const [selectedTaskStatus, setSelectedTaskStatus] = useState<string>('');

    const getStatusTag = (status: string) => {
        switch (status) {
            case 'RUNNING': return <Tag color="blue">运行中</Tag>;
            case 'SUCCESS': return <Tag color="green">成功</Tag>;
            case 'FAILED': return <Tag color="red">失败</Tag>;
            case 'PENDING': return <Tag color="default">等待中</Tag>;
            default: return <Tag>{status}</Tag>;
        }
    };

    const columns = [
        { title: '任务ID', dataIndex: 'id', key: 'id', width: 80 },
        { title: '任务名称', dataIndex: 'taskName', key: 'taskName', width: 150, ellipsis: true },
        { title: '配置', dataIndex: 'freqtradeConfigName', key: 'freqtradeConfigName', width: 150 },
        { title: '策略', dataIndex: 'strategyName', key: 'strategyName', width: 150 },
        { title: '时间范围', dataIndex: 'timeRange', key: 'timeRange', width: 180 },
        { title: '周期', dataIndex: 'timeframe', key: 'timeframe', width: 80, render: (t: string) => <Tag>{t}</Tag> },
        { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (s: string) => getStatusTag(s) },
        {
            title: '总收益',
            key: 'profit',
            width: 120,
            render: (_: any, record: BacktestTaskResponse) => {
                if (record.status !== 'SUCCESS' || record.totalProfitAbs == null) return '-';
                const color = record.totalProfitAbs >= 0 ? '#52c41a' : '#ff4d4f';
                return (
                    <span style={{ color }}>
                        {record.totalProfitAbs} ({record.totalProfitPct}%)
                    </span>
                );
            }
        },
        {
            title: '胜率',
            dataIndex: 'winRate',
            key: 'winRate',
            width: 100,
            render: (v: number) => v != null ? `${v.toFixed(2)}%` : '-'
        },
        {
            title: '夏普率',
            dataIndex: 'sharpeRatio',
            key: 'sharpeRatio',
            width: 100,
            render: (v: number) => v != null ? v.toFixed(2) : '-'
        },
        {
            title: '操作',
            key: 'action',
            width: 100,
            render: (_: any, record: BacktestTaskResponse) => (
                <Space size="small">
                    <Tooltip title="查看报告">
                        <Button type="text" size="small" icon={<LineChartOutlined />} onClick={() => openResult(record.id, record.status)} />
                    </Tooltip>
                    {record.status === 'RUNNING' && (
                        <Popconfirm title="确定停止该回测?" onConfirm={() => handleStop(record.id)}>
                            <Tooltip title="停止">
                                <Button type="text" danger size="small" icon={<StopOutlined />} />
                            </Tooltip>
                        </Popconfirm>
                    )}
                    {(record.status === 'SUCCESS' || record.status === 'FAILED') && (
                        <Popconfirm title="确定删除该回测任务?" onConfirm={() => handleDelete(record.id)}>
                            <Tooltip title="删除">
                                <Button type="text" danger size="small" icon={<DeleteOutlined />} />
                            </Tooltip>
                        </Popconfirm>
                    )}
                </Space>
            )
        }
    ];

    const safeTasks = tasks || [];
    const runningCount = safeTasks.filter(t => t.status === 'RUNNING').length;
    const successCount = safeTasks.filter(t => t.status === 'SUCCESS').length;
    const failedCount = safeTasks.filter(t => t.status === 'FAILED').length;

    return (
        <div style={{ padding: '24px' }}>
            <Title level={3} style={{ marginBottom: 16 }}>策略回测</Title>

            <Card title="新建回测任务" style={{ marginBottom: 24 }}>
                <Form form={form} layout="vertical" onFinish={handleStart}>
                    <Row gutter={16}>
                        <Col span={6}>
                            <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
                                <Input placeholder="默认自动生成" />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="freqtradeConfigId" label="基础配置" rules={[{ required: true, message: '请选择配置' }]}>
                                <Select placeholder="选择配置">
                                    {freqtradeConfigs.map(c => <Option key={c.id} value={c.id}>{c.configName}</Option>)}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="strategyConfigId" label="策略" rules={[{ required: true, message: '请选择策略' }]}>
                                <Select placeholder="选择策略">
                                    {strategyConfigs.map(s => <Option key={s.id} value={s.id}>{s.title} ({s.strategyName})</Option>)}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="timeframe" label="K线周期" rules={[{ required: true, message: '请选择周期' }]} initialValue="5m">
                                <Select>
                                    <Option value="1m">1m</Option>
                                    <Option value="5m">5m</Option>
                                    <Option value="15m">15m</Option>
                                    <Option value="1h">1h</Option>
                                    <Option value="4h">4h</Option>
                                    <Option value="1d">1d</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name="timerange" label="时间范围" rules={[{ required: true, message: '请选择时间范围' }]}>
                                <RangePicker style={{ width: '100%' }} />
                            </Form.Item>
                        </Col>
                        <Col span={16} style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end' }}>
                            <Form.Item style={{ marginBottom: 0 }}>
                                <Space>
                                    <Tooltip title="刷新列表">
                                        <Button icon={<ReloadOutlined />} onClick={() => loadTasks()} />
                                    </Tooltip>
                                    <Tooltip title="启动回测">
                                        <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />} loading={starting} />
                                    </Tooltip>
                                </Space>
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Card>

            <Row gutter={16} style={{ marginBottom: 24 }}>
                <Col span={6}><Card bodyStyle={{ padding: 8 }}><Statistic title="总任务数" value={total} /></Card></Col>
                <Col span={6}><Card bodyStyle={{ padding: 8 }}><Statistic title="运行中" value={runningCount} valueStyle={{ color: '#1890ff' }} /></Card></Col>
                <Col span={6}><Card bodyStyle={{ padding: 8 }}><Statistic title="成功" value={successCount} valueStyle={{ color: '#52c41a' }} /></Card></Col>
                <Col span={6}><Card bodyStyle={{ padding: 8 }}><Statistic title="失败" value={failedCount} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
            </Row>

            <Card title="回测任务列表" bodyStyle={{ padding: 0, overflow: 'hidden' }}>
                <div style={{ overflowX: 'auto' }}>
                    <Table
                        columns={columns}
                        dataSource={safeTasks}
                        rowKey="id"
                        loading={loading}
                        size="small"
                        pagination={{
                            position: ['bottomRight'] as const,
                            current: page,
                            pageSize: 10,
                            total: total,
                            onChange: (p) => { setPage(p); loadTasks(p); }
                        }}
                    />
                </div>
            </Card>

            {selectedTaskId && (
                <BacktestResultModal
                    taskId={selectedTaskId}
                    taskStatus={selectedTaskStatus}
                    visible={resultModalVisible}
                    onClose={() => {
                        setResultModalVisible(false);
                        setSelectedTaskId(null);
                        setSelectedTaskStatus('');
                    }}
                />
            )}
        </div>
    );
};

export default BacktestManagementPage;
