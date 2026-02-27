import React, {useEffect, useState} from 'react';
import {Alert, Card, Col, List, Row, Spin, Statistic, Tag, Typography} from 'antd';
import {ApiOutlined, CheckCircleOutlined, ClockCircleOutlined, KeyOutlined} from '@ant-design/icons';
import {dashboardService, DashboardStatistics} from '../services/dashboardService';
import {executionService} from '../services/executionService';
import {Execution} from '../types/execution';
import dayjs from 'dayjs';

const {Title} = Typography;

const Dashboard: React.FC = () => {
    const [statistics, setStatistics] = useState<DashboardStatistics | null>(null);
    const [recentExecutions, setRecentExecutions] = useState<Execution[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setLoading(true);
            setError(null);

            // 分别调用API，即使一个失败也不影响另一个
            try {
                const statsData = await dashboardService.getStatistics();
                setStatistics(statsData);
            } catch (statsError) {
                console.error('加载统计数据失败', statsError);
                // 继续加载执行记录，不设置错误状态
            }

            try {
                const executionsData = await executionService.getRecentExecutionsSince(24);
                setRecentExecutions(executionsData.slice(0, 10)); // 只显示最近10条
            } catch (execError) {
                console.error('加载执行记录失败', execError);
                // 如果统计数据也加载失败，才设置错误状态
                if (!statistics) {
                    setError('部分数据加载失败，请检查后端服务是否正常运行');
                }
            }
        } catch (err) {
            console.error('加载数据失败', err);
            setError('加载数据失败，请检查后端服务是否正常运行');
        } finally {
            setLoading(false);
        }
    };

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

    if (loading) {
        return (
            <div style={{textAlign: 'center', padding: '50px'}}>
                <Spin size="large"/>
                <div style={{marginTop: 16}}>加载中...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div>
                <Alert
                    message="加载失败"
                    description={error}
                    type="error"
                    showIcon
                    style={{marginBottom: 16}}
                    action={
                        <button
                            style={{background: 'none', border: 'none', color: '#1890ff', cursor: 'pointer'}}
                            onClick={loadData}
                        >
                            重试
                        </button>
                    }
                />
            </div>
        );
    }

    return (
        <div>
            <Title level={2}>系统概览</Title>

            <Row gutter={16} style={{marginBottom: 24}}>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="活跃任务"
                            value={statistics?.activeTasks || 0}
                            prefix={<ClockCircleOutlined/>}
                            valueStyle={{color: '#3f8600'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="API Key"
                            value={statistics?.totalApiKeys || 0}
                            prefix={<KeyOutlined/>}
                            valueStyle={{color: '#1890ff'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="数据获取配置"
                            value={statistics?.dataFetchConfigs || 0}
                            prefix={<ApiOutlined/>}
                            valueStyle={{color: '#722ed1'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="今日成功执行"
                            value={statistics?.todaySuccessExecutions || 0}
                            prefix={<CheckCircleOutlined/>}
                            valueStyle={{color: '#52c41a'}}
                        />
                    </Card>
                </Col>
            </Row>

            <Row gutter={16}>
                <Col span={12}>
                    <Card title="最近执行记录" variant="borderless">
                        {recentExecutions.length > 0 ? (
                            <List
                                dataSource={recentExecutions}
                                renderItem={(execution) => (
                                    <List.Item key={execution.executionId}>
                                        <List.Item.Meta
                                            title={`任务 ${execution.taskId}`}
                                            description={
                                                <div>
                                                    <div>
                                                        <Tag color={getStatusColor(execution.executionStatus)}>
                                                            {getStatusText(execution.executionStatus)}
                                                        </Tag>
                                                        <span style={{marginLeft: 8}}>
                              {getTriggerTypeText(execution.triggerType)}
                            </span>
                                                    </div>
                                                    <div style={{fontSize: '12px', color: '#999', marginTop: 4}}>
                                                        {dayjs(execution.triggerTime).format('MM-DD HH:mm:ss')}
                                                    </div>
                                                </div>
                                            }
                                        />
                                    </List.Item>
                                )}
                                size="small"
                            />
                        ) : (
                            <p>暂无执行记录</p>
                        )}
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="系统状态" variant="borderless">
                        <p>系统运行正常</p>
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default Dashboard;