import React from 'react';
import {Card, Col, Row, Tag} from 'antd';
import {formatEffectiveDecimal, truncateToDecimalPlaces} from './numberFormatter';

// 模拟格式化函数
const formatPrice = (price: number): string => {
    return formatEffectiveDecimal(price, 4, 0, 4);
};

const formatChangePercent = (changePercent: number): string => {
    const sign = changePercent >= 0 ? '+' : '';
    const truncatedValue = truncateToDecimalPlaces(changePercent, 4);
    const formattedValue = formatEffectiveDecimal(truncatedValue, 4, 0, 4);
    return `${sign}${formattedValue}%`;
};

const PriceFormatTest: React.FC = () => {
    const priceTestCases = [
        {label: '正常价格', value: 913.3, expected: '913.3'},
        {label: '长小数价格', value: 913.356789, expected: '913.3567'},
        {label: '整数价格', value: 1000, expected: '1000'},
        {label: '小价格', value: 0.00123456, expected: '0.0012'},
        {label: '很小的价格', value: 0.00001, expected: '0.0001'},
        {label: 'BTC价格', value: 67123.456789, expected: '67123.4567'},
    ];

    const percentTestCases = [
        {label: '正常跌幅', value: -2.2888, expected: '-2.2888%'},
        {label: '长小数跌幅', value: -2.28888888, expected: '-2.2888%'},
        {label: '正常涨幅', value: 3.1415, expected: '+3.1415%'},
        {label: '长小数涨幅', value: 3.14159265, expected: '+3.1415%'},
        {label: '零涨幅', value: 0, expected: '0%'},
        {label: '小涨幅', value: 0.1234, expected: '+0.1234%'},
    ];

    return (
        <div style={{padding: 20}}>
            <Card title="价格格式化测试 (最多4位有效小数)" style={{marginBottom: 20}}>
                <Row gutter={[16, 16]}>
                    {priceTestCases.map((test, index) => (
                        <Col span={8} key={index}>
                            <Card size="small">
                                <div className="selected-instrument" style={{padding: '8px 0'}}>
                                    <span className="instrument-name">{test.label}</span>
                                    <span className="instrument-price" style={{fontSize: '16px', fontWeight: 'bold'}}>
                                        ${formatPrice(test.value)}
                                    </span>
                                </div>
                                <div style={{fontSize: '12px', color: '#666', marginTop: 8}}>
                                    期望: ${test.expected}
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>
            </Card>

            <Card title="涨跌幅格式化测试 (最多4位有效小数)">
                <Row gutter={[16, 16]}>
                    {percentTestCases.map((test, index) => (
                        <Col span={8} key={index}>
                            <Card size="small">
                                <div className="selected-instrument" style={{padding: '8px 0'}}>
                                    <span className="instrument-name">{test.label}</span>
                                    <Tag
                                        color="default"
                                        className="instrument-change-container"
                                    >
                                        <span className="time-label">24H </span>
                                        <span
                                            className={`change-percent ${test.value >= 0 ? 'positive' : 'negative'}`}
                                            style={{color: test.value >= 0 ? '#52c41a' : '#ff4d4f'}}
                                        >
                                            {formatChangePercent(test.value)}
                                        </span>
                                    </Tag>
                                </div>
                                <div style={{fontSize: '12px', color: '#666', marginTop: 8}}>
                                    期望: {test.expected}
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>
            </Card>
        </div>
    );
};

export default PriceFormatTest;