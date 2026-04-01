import React, {useEffect, useState} from 'react';
import {Alert, Button, Card, Col, Row, Spin, Statistic, Typography} from 'antd';
import {useNavigate} from 'react-router-dom';
import {dashboardService, DashboardStatistics} from '../../services/dashboardService';
import {
    ApiOutlined,
    ArrowRightOutlined,
    ClockCircleOutlined,
    DatabaseOutlined,
    GlobalOutlined,
    HistoryOutlined,
    KeyOutlined,
    LineChartOutlined,
    SettingOutlined
} from '@ant-design/icons';

const {Title, Text} = Typography;

interface SystemModule {
    key: string;
    title: string;
    description: string;
    icon: React.ReactNode;
    color: string;
    path: string;
    count?: number;
    countLabel?: string;
}

const SystemManagement: React.FC = () => {
    const navigate = useNavigate();
    const [statistics, setStatistics] = useState<DashboardStatistics | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadStatistics();
    }, []);

    const loadStatistics = async () => {
        try {
            setLoading(true);
            setError(null);
            const statsData = await dashboardService.getStatistics();
            setStatistics(statsData);
        } catch (err) {
            console.error('加载统计数据失败', err);
            setError('加载统计数据失败，请检查后端服务是否正常运行');
        } finally {
            setLoading(false);
        }
    };

    const systemModules: SystemModule[] = [
        {
            key: 'cex-keys',
            title: 'CEX API Key 管理',
            description: '管理交易所API密钥，支持数据库存储和环境变量两种方式',
            icon: <KeyOutlined/>,
            color: '#1890ff',
            path: '/system/cex-keys',
            count: statistics?.totalApiKeys,
            countLabel: '个API Key'
        },
        {
            key: 'data-fetch',
            title: '数据获取配置',
            description: '配置数据获取API、鉴权方式、代理设置和数据处理器',
            icon: <ApiOutlined/>,
            color: '#52c41a',
            path: '/system/data-fetch',
            count: statistics?.dataFetchConfigs,
            countLabel: '个配置'
        },
        {
            key: 'proxy',
            title: '代理服务器配置',
            description: '配置HTTP和SOCKS5代理服务器，用于数据获取的网络请求',
            icon: <GlobalOutlined/>,
            color: '#722ed1',
            path: '/system/proxy',
            count: 2,
            countLabel: '个代理'
        },
        {
            key: 'cex-proxy-bindings',
            title: '交易所代理绑定',
            description: '按交易所绑定代理服务器，CEX请求自动走绑定代理',
            icon: <GlobalOutlined/>,
            color: '#531dab',
            path: '/system/cex-proxy-bindings'
        },
        {
            key: 'freqtrade-config',
            title: 'Freqtrade配置',
            description: '配置Freqtrade启动模式、策略目录、API等',
            icon: <SettingOutlined/>,
            color: '#eb2f96',
            path: '/system/freqtrade-config'
        },
        {
            key: 'tasks',
            title: '定时任务管理',
            description: '创建和管理定时任务，支持Cron表达式和多种触发方式',
            icon: <ClockCircleOutlined/>,
            color: '#fa8c16',
            path: '/system/tasks',
            count: statistics?.activeTasks,
            countLabel: '个活跃任务'
        },
        {
            key: 'executions',
            title: '执行记录',
            description: '查看任务执行历史记录，包括执行状态、时间和结果',
            icon: <HistoryOutlined/>,
            color: '#13c2c2',
            path: '/system/executions',
            count: statistics?.todaySuccessExecutions,
            countLabel: `个今日成功执行 (成功率: ${statistics?.todaySuccessRate?.toFixed(2) || '0.00'}%)`
        }
    ];

    const handleModuleClick = (module: SystemModule) => {
        navigate(module.path);
    };

    const SystemModuleCard: React.FC<{ module: SystemModule }> = ({module}) => (
        <Card
            hoverable
            style={{
                height: '100%',
                cursor: 'pointer',
                transition: 'all 0.3s ease'
            }}
            styles={{
                body: {padding: '24px'}
            }}
            onClick={() => handleModuleClick(module)}
        >
            <div style={{marginBottom: '16px'}}>
                <div style={{
                    fontSize: '32px',
                    color: module.color,
                    marginBottom: '12px',
                    display: 'inline-block'
                }}>
                    {module.icon}
                </div>
                <Title level={4} style={{margin: 0, color: '#262626'}}>
                    {module.title}
                </Title>
            </div>

            <Text type="secondary" style={{fontSize: '14px', lineHeight: '1.5'}}>
                {module.description}
            </Text>

            {module.count !== undefined && (
                <div style={{marginTop: '16px'}}>
                    <Statistic
                        value={module.count}
                        suffix={module.countLabel}
                        valueStyle={{fontSize: '20px', color: module.color}}
                    />
                </div>
            )}

            <div style={{marginTop: '16px', textAlign: 'right'}}>
                <Button type="text" icon={<ArrowRightOutlined/>} style={{color: module.color}}>
                    进入管理
                </Button>
            </div>
        </Card>
    );

    if (loading) {
        return (
            <div style={{padding: '24px', textAlign: 'center', marginTop: '100px'}}>
                <Spin size="large"/>
                <div style={{marginTop: 16}}>加载中...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div style={{padding: '24px'}}>
                <Alert
                    message="加载失败"
                    description={error}
                    type="error"
                    showIcon
                    style={{marginBottom: 16}}
                    action={
                        <Button size="small" onClick={loadStatistics}>
                            重试
                        </Button>
                    }
                />
            </div>
        );
    }

    return (
        <div style={{padding: '24px'}}>
            {/* 页面标题 */}
            <div style={{marginBottom: '32px', textAlign: 'center'}}>
                <Title level={2} style={{margin: 0}}>
                    <SettingOutlined style={{marginRight: '8px'}}/>
                    Dashboard
                </Title>
                <Text type="secondary" style={{fontSize: '16px'}}>
                    系统概览与统计信息
                </Text>
            </div>

            {/* 系统概览统计 */}
            <Card style={{marginBottom: '32px'}} title="系统概览">
                <Row gutter={16}>
                    <Col span={4}>
                        <Statistic
                            title="API Keys"
                            value={statistics?.totalApiKeys || 0}
                            prefix={<KeyOutlined/>}
                            valueStyle={{color: '#1890ff'}}
                        />
                    </Col>
                    <Col span={4}>
                        <Statistic
                            title="数据获取配置"
                            value={statistics?.dataFetchConfigs || 0}
                            prefix={<ApiOutlined/>}
                            valueStyle={{color: '#52c41a'}}
                        />
                    </Col>
                    <Col span={4}>
                        <Statistic
                            title="活跃任务"
                            value={statistics?.activeTasks || 0}
                            prefix={<ClockCircleOutlined/>}
                            valueStyle={{color: '#fa8c16'}}
                        />
                    </Col>
                    <Col span={4}>
                        <Statistic
                            title="今日成功执行"
                            value={statistics?.todaySuccessExecutions || 0}
                            prefix={<DatabaseOutlined/>}
                            valueStyle={{color: '#13c2c2'}}
                        />
                    </Col>
                    <Col span={4}>
                        <Statistic
                            title="今日执行成功率"
                            value={statistics?.todaySuccessRate || 0}
                            precision={2}
                            suffix="%"
                            prefix={<LineChartOutlined/>}
                            valueStyle={{color: '#722ed1'}}
                        />
                    </Col>
                    <Col span={4}>
                        <Statistic
                            title="今日总执行"
                            value={statistics?.todayTotalExecutions || 0}
                            prefix={<HistoryOutlined/>}
                            valueStyle={{color: '#eb2f96'}}
                        />
                    </Col>
                </Row>
            </Card>

            {/* 系统模块列表 */}
            <Row gutter={[16, 16]}>
                {systemModules.map(module => (
                    <Col xs={24} sm={12} md={8} lg={6} key={module.key}>
                        <SystemModuleCard module={module}/>
                    </Col>
                ))}
            </Row>

        </div>
    );
};

export default SystemManagement;
