import React from 'react';
import {Card, Col, Row, Statistic, Typography} from 'antd';
import {DollarOutlined, FallOutlined, LineChartOutlined, RiseOutlined, WalletOutlined} from '@ant-design/icons';
import type {OkxPositionStatistics} from '../../../types/okxPosition';
import {formatEffectiveDecimal, formatPercentage} from '../../../utils/numberFormatter';

const {Title} = Typography;

interface TradingStatsProps {
    statistics: OkxPositionStatistics | null;
    totalUnrealizedPnl: number;
    totalMargin: number;
    todayExecutions: number;
}

const TradingStats: React.FC<TradingStatsProps> = ({
                                                       statistics,
                                                       totalUnrealizedPnl,
                                                       totalMargin,
                                                       todayExecutions
                                                   }) => {
    const totalNotional = (statistics?.typeStatistics || []).reduce((sum, item) => sum + (item.totalNotional || 0), 0) || 0;
    const longPositions = (statistics?.sideStatistics || []).find(item => item.name === 'long')?.count || 0;
    const shortPositions = (statistics?.sideStatistics || []).find(item => item.name === 'short')?.count || 0;

    return (
        <div>
            <Title level={4}>交易概览</Title>
            <Row gutter={16}>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="总持仓数"
                            value={statistics?.totalPositions || 0}
                            prefix={<LineChartOutlined/>}
                            valueStyle={{color: '#1890ff'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="总名义价值"
                            value={totalNotional}
                            prefix={<DollarOutlined/>}
                            formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                            valueStyle={{color: '#52c41a'}}
                            suffix="USD"
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="未结盈亏"
                            value={totalUnrealizedPnl}
                            prefix={<RiseOutlined/>}
                            formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                            valueStyle={{color: totalUnrealizedPnl >= 0 ? '#52c41a' : '#ff4d4f'}}
                            suffix="USD"
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="今日执行"
                            value={todayExecutions}
                            prefix={<WalletOutlined/>}
                            valueStyle={{color: '#722ed1'}}
                        />
                    </Card>
                </Col>
            </Row>

            <Row gutter={16} style={{marginTop: 16}}>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="多头持仓"
                            value={longPositions}
                            prefix={<RiseOutlined/>}
                            valueStyle={{color: '#52c41a'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="空头持仓"
                            value={shortPositions}
                            prefix={<FallOutlined/>}
                            valueStyle={{color: '#ff4d4f'}}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="保证金"
                            value={totalMargin}
                            prefix={<WalletOutlined/>}
                            formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                            valueStyle={{color: '#1890ff'}}
                            suffix="USD"
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="盈亏率"
                            value={totalMargin > 0 ? (totalUnrealizedPnl / totalMargin * 100) : 0}
                            prefix={<RiseOutlined/>}
                            formatter={(value) => formatPercentage(value as number, 2)}
                            valueStyle={{color: totalUnrealizedPnl >= 0 ? '#52c41a' : '#ff4d4f'}}
                            suffix="%"
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default TradingStats;