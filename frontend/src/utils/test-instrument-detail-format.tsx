import React from 'react';
import {Card, Col, Row, Space, Tag, Typography} from 'antd';
import {formatEffectiveDecimal, truncateToDecimalPlaces} from './numberFormatter';

const {Text} = Typography;

// 模拟InstrumentDetailPanel的格式化函数
const formatFundingRate = (rate?: number): string => {
    if (typeof rate !== 'number') return '-';
    const percentValue = rate * 100;
    const truncatedValue = truncateToDecimalPlaces(percentValue, 4);
    const formattedValue = formatEffectiveDecimal(truncatedValue, 4, 0, 4);
    return `${formattedValue}%`;
};

const formatChangePercent = (change?: number | null): string => {
    if (typeof change !== 'number') return '-';
    const sign = change >= 0 ? '+' : '';
    const truncatedValue = truncateToDecimalPlaces(change, 4);
    const formattedValue = formatEffectiveDecimal(truncatedValue, 4, 0, 4);
    return `${sign}${formattedValue}%`;
};

const getChangeClass = (change?: number | null): string => {
    if (typeof change !== 'number') return 'change-neutral';
    if (change > 0) return 'change-negative';
    if (change < 0) return 'change-positive';
    return 'change-neutral';
};

const InstrumentDetailFormatTest: React.FC = () => {
    const fundingRateTestCases = [
        {label: '正常资金费率', value: 0.0001, expected: '0.01%'},
        {label: '高资金费率', value: 0.01234567, expected: '1.2345%'},
        {label: '负资金费率', value: -0.0056789, expected: '-0.5678%'},
        {label: '很小资金费率', value: 0.000001, expected: '0.0001%'},
        {label: '零资金费率', value: 0, expected: '0%'},
    ];

    const changePercentTestCases = [
        {label: '5m平均正常跌幅', value: 0.3942, expected: '+0.3942%'},
        {label: '5m平均长小数跌幅', value: 0.39425678, expected: '+0.3942%'},
        {label: '4H平均正常涨幅', value: 1.92, expected: '+1.92%'},
        {label: '4H平均长小数涨幅', value: 1.92003456, expected: '+1.92%'},
        {label: '正常跌幅', value: -2.2888, expected: '-2.2888%'},
        {label: '长小数跌幅', value: -2.28888888, expected: '-2.2888%'},
        {label: '零变化', value: 0, expected: '0%'},
        {label: '微小涨幅', value: 0.0001, expected: '+0.0001%'},
    ];

    return (
        <div style={{padding: 20}}>
            <Card title="资金费率格式化测试 (最多4位有效小数)" style={{marginBottom: 20}}>
                <Row gutter={[16, 16]}>
                    {fundingRateTestCases.map((test, index) => (
                        <Col span={8} key={index}>
                            <Card size="small">
                                <div className="compact-item">
                                    <Text type="secondary" style={{fontSize: '12px'}}>
                                        {test.label}
                                    </Text>
                                    <div style={{marginTop: 4}}>
                                        <Tag
                                            color="blue"
                                            style={{margin: 0, fontSize: '14px', padding: '4px 12px', fontWeight: 500}}
                                        >
                                            {formatFundingRate(test.value)}
                                        </Tag>
                                    </div>
                                </div>
                                <div style={{fontSize: '12px', color: '#666', marginTop: 8}}>
                                    期望: {test.expected}
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>
            </Card>

            <Card title="涨跌幅格式化测试 (最多4位有效小数)">
                <Row gutter={[16, 16]}>
                    {changePercentTestCases.map((test, index) => (
                        <Col span={8} key={index}>
                            <Card size="small">
                                <div className="compact-item">
                                    <Text type="secondary" style={{fontSize: '12px'}}>
                                        {test.label}
                                    </Text>
                                    <div style={{marginTop: 4}}>
                                        <Text
                                            className={`compact-change ${getChangeClass(test.value)}`}
                                            style={{
                                                fontSize: '14px',
                                                color: test.value > 0 ? '#ff4d4f' : test.value < 0 ? '#52c41a' : '#666'
                                            }}
                                        >
                                            {formatChangePercent(test.value)}
                                        </Text>
                                    </div>
                                </div>
                                <div style={{fontSize: '12px', color: '#666', marginTop: 8}}>
                                    期望: {test.expected}
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>
            </Card>

            <Card title="实际使用效果模拟" style={{marginTop: 20}}>
                <div className="compact-content">
                    <Space size="middle" style={{width: '100%', justifyContent: 'space-between', flexWrap: 'wrap'}}>
                        {/* 5分钟平均波动 */}
                        <div className="compact-item">
                            <Text type="secondary" style={{fontSize: '12px'}}>
                                5m平均
                            </Text>
                            <Text className={`compact-change ${getChangeClass(0.39425678)}`}
                                  style={{color: '#ff4d4f'}}>
                                {formatChangePercent(0.39425678)}
                            </Text>
                        </div>

                        {/* 4小时平均波动 */}
                        <div className="compact-item">
                            <Text type="secondary" style={{fontSize: '12px'}}>
                                4H平均
                            </Text>
                            <Text className={`compact-change ${getChangeClass(1.92003456)}`}
                                  style={{color: '#ff4d4f'}}>
                                {formatChangePercent(1.92003456)}
                            </Text>
                        </div>

                        {/* 当前资金费率 */}
                        <div className="compact-item">
                            <Text type="secondary" style={{fontSize: '12px'}}>
                                资金费率
                            </Text>
                            <Tag
                                color="blue"
                                style={{margin: 0, fontSize: '14px', padding: '4px 12px', fontWeight: 500}}
                            >
                                {formatFundingRate(0.0001)}
                            </Tag>
                        </div>
                    </Space>
                </div>
            </Card>
        </div>
    );
};

export default InstrumentDetailFormatTest;