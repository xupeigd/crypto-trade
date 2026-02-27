import React from 'react';
import {Button, Card, Space, Table, Tag, Typography} from 'antd';
import {EyeOutlined, ReloadOutlined} from '@ant-design/icons';
import type {OkxPosition, OkxPositionSummary} from '../../../types/okxPosition';
import {OkxPositionService} from '../../../services/okxPositionService';
import dayjs from 'dayjs';

const {Title, Text} = Typography;

interface PositionOverviewProps {
    positions: OkxPosition[];
    summary: OkxPositionSummary | null;
    loading?: boolean;
    onRefresh?: () => void;
    onViewAll?: () => void;
}

const PositionOverview: React.FC<PositionOverviewProps> = ({
                                                               positions,
                                                               summary,
                                                               loading = false,
                                                               onRefresh,
                                                               onViewAll
                                                           }) => {
    // 只显示前5个持仓
    const displayPositions = positions.slice(0, 5);

    const columns = [
        {
            title: '合约',
            dataIndex: 'instId',
            key: 'instId',
            width: 120,
            render: (text: string, record: OkxPosition) => (
                <div>
                    <Text strong style={{fontSize: '12px'}}>{text}</Text>
                    <br/>
                    <Tag color={record.instType === 'SWAP' ? 'blue' : 'green'}>
                        {OkxPositionService.formatInstrumentType(record.instType)}
                    </Tag>
                </div>
            ),
        },
        {
            title: '方向',
            dataIndex: 'posSide',
            key: 'posSide',
            width: 60,
            render: (text: string) => {
                const color = text === 'long' ? 'green' : text === 'short' ? 'red' : 'default';
                return (
                    <Tag color={color}>
                        {OkxPositionService.formatPositionSide(text)}
                    </Tag>
                );
            },
        },
        {
            title: '持仓量',
            dataIndex: 'pos',
            key: 'pos',
            width: 80,
            render: (value: number | null) => (
                <Text style={{fontSize: '12px'}}>
                    {value !== null ? OkxPositionService.formatNumber(value, 4) : '0'}
                </Text>
            ),
        },
        {
            title: '标记价',
            dataIndex: 'markPx',
            key: 'markPx',
            width: 80,
            render: (value: number | null) => (
                <Text style={{fontSize: '12px'}}>
                    ${value !== null ? OkxPositionService.formatNumber(value, 4) : '0.0000'}
                </Text>
            ),
        },
        {
            title: '盈亏',
            dataIndex: 'upl',
            key: 'upl',
            width: 80,
            render: (value: number | null) => {
                if (value === null) return <Text style={{fontSize: '12px'}}>0.00</Text>;
                const color = value > 0 ? 'green' : value < 0 ? 'red' : 'default';
                return (
                    <Text style={{color, fontSize: '12px'}}>
                        {value > 0 ? '+' : ''}
                        {OkxPositionService.formatNumber(value, 2)}
                    </Text>
                );
            },
        },
        {
            title: '盈亏率',
            dataIndex: 'uplRatio',
            key: 'uplRatio',
            width: 70,
            render: (value: number | null) => {
                if (value === null) return <Text style={{fontSize: '12px'}}>0.00%</Text>;
                const color = value > 0 ? 'green' : value < 0 ? 'red' : 'default';
                return (
                    <Text style={{color, fontSize: '12px'}}>
                        {OkxPositionService.formatPercentage(value)}
                    </Text>
                );
            },
        },
    ];

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                <Title level={4}>持仓概览</Title>
                <Space>
                    <Button
                        icon={<ReloadOutlined/>}
                        size="small"
                        onClick={onRefresh}
                        loading={loading}
                    >
                        刷新
                    </Button>
                    <Button
                        icon={<EyeOutlined/>}
                        size="small"
                        onClick={onViewAll}
                    >
                        查看全部
                    </Button>
                </Space>
            </div>

            {displayPositions.length > 0 ? (
                <Card>
                    <Table
                        columns={columns}
                        dataSource={displayPositions}
                        rowKey={(record) => `${record.instId}-${record.posSide}`}
                        loading={loading}
                        pagination={false}
                        size="small"
                        scroll={{x: 400}}
                        footer={() =>
                            summary && (
                                <div style={{textAlign: 'right'}}>
                                    <Text style={{fontSize: '12px'}}>
                                        总名义价值: ${OkxPositionService.formatNumber(summary.totalNotional, 2)} |
                                        总保证金: ${OkxPositionService.formatNumber(summary.totalMargin, 2)} |
                                        总盈亏:
                                        <Text style={{color: summary.totalUpl > 0 ? '#52c41a' : '#ff4d4f'}}>
                                            {summary.totalUpl > 0 ? '+' : ''}
                                            {OkxPositionService.formatNumber(summary.totalUpl, 2)}
                                        </Text>
                                    </Text>
                                </div>
                            )
                        }
                    />
                </Card>
            ) : (
                <Card>
                    <div style={{textAlign: 'center', padding: '20px', color: '#999'}}>
                        暂无持仓数据
                    </div>
                </Card>
            )}

            {displayPositions.length > 0 && (
                <div style={{textAlign: 'center', marginTop: 8}}>
                    <Text type="secondary" style={{fontSize: '12px'}}>
                        显示前5个持仓，共{positions.length}个 |
                        最后更新: {positions[0]?.dataIngestionTime ?
                        dayjs(positions[0].dataIngestionTime).format('HH:mm:ss') : '-'
                    }
                    </Text>
                </div>
            )}
        </div>
    );
};

export default PositionOverview;