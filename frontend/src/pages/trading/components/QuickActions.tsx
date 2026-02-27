import React from 'react';
import {Card, Col, Row, Typography} from 'antd';
import {
    ApiOutlined,
    ClockCircleOutlined,
    HistoryOutlined,
    KeyOutlined,
    LineChartOutlined,
    ReloadOutlined
} from '@ant-design/icons';

const {Title, Text} = Typography;

interface QuickActionsProps {
    onRefreshData?: () => void;
    onViewPositions?: () => void;
    onViewExecutions?: () => void;
    onManageApiKeys?: () => void;
    onManageDataFetch?: () => void;
    onManageTasks?: () => void;
}

const QuickActions: React.FC<QuickActionsProps> = ({
                                                       onRefreshData,
                                                       onViewPositions,
                                                       onViewExecutions,
                                                       onManageApiKeys,
                                                       onManageDataFetch,
                                                       onManageTasks
                                                   }) => {
    const tradingActions = [
        {
            title: '查看持仓',
            description: '查看详细的持仓信息',
            icon: <LineChartOutlined/>,
            color: '#1890ff',
            onClick: onViewPositions
        },
        {
            title: '执行记录',
            description: '查看交易执行历史',
            icon: <HistoryOutlined/>,
            color: '#52c41a',
            onClick: onViewExecutions
        },
        {
            title: '刷新数据',
            description: '获取最新交易数据',
            icon: <ReloadOutlined/>,
            color: '#722ed1',
            onClick: onRefreshData
        }
    ];

    const systemActions = [
        {
            title: 'API Key管理',
            description: '管理交易所API密钥',
            icon: <KeyOutlined/>,
            color: '#fa8c16',
            onClick: onManageApiKeys
        },
        {
            title: '数据配置',
            description: '配置数据获取规则',
            icon: <ApiOutlined/>,
            color: '#13c2c2',
            onClick: onManageDataFetch
        },
        {
            title: '任务管理',
            description: '管理定时任务',
            icon: <ClockCircleOutlined/>,
            color: '#f5222d',
            onClick: onManageTasks
        }
    ];

    const ActionCard: React.FC<{
        title: string;
        description: string;
        icon: React.ReactNode;
        color: string;
        onClick?: () => void;
    }> = ({title, description, icon, color, onClick}) => (
        <Card
            hoverable
            style={{
                textAlign: 'center',
                cursor: onClick ? 'pointer' : 'default',
                height: '100%'
            }}
            styles={{
                body: {padding: '16px'}
            }}
            onClick={onClick}
        >
            <div style={{color, fontSize: '24px', marginBottom: '8px'}}>
                {icon}
            </div>
            <Title level={5} style={{margin: '8px 0', color}}>
                {title}
            </Title>
            <Text type="secondary" style={{fontSize: '12px'}}>
                {description}
            </Text>
        </Card>
    );

    return (
        <div>
            <Title level={4}>快捷操作</Title>

            <Row gutter={16} style={{marginBottom: 16}}>
                <Col span={24}>
                    <Title level={5}>交易相关</Title>
                </Col>
                {tradingActions.map((action, index) => (
                    <Col span={8} key={`trading-${index}`} style={{marginBottom: 16}}>
                        <ActionCard {...action} />
                    </Col>
                ))}
            </Row>

            <Row gutter={16}>
                <Col span={24}>
                    <Title level={5}>系统管理</Title>
                </Col>
                {systemActions.map((action, index) => (
                    <Col span={8} key={`system-${index}`} style={{marginBottom: 16}}>
                        <ActionCard {...action} />
                    </Col>
                ))}
            </Row>
        </div>
    );
};

export default QuickActions;