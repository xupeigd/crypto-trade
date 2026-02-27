import React from 'react';
import {Card, Col, Row, Statistic} from 'antd';
import {formatEffectiveDecimal, formatPercentage} from './numberFormatter';

const TestComponent: React.FC = () => {
    const testCases = [
        {label: '圆周率截断', value: 3.1415926, expected: '3.1415'},
        {label: '长小数截断', value: 1.23456789, expected: '1.2345'},
        {label: '接近1截断', value: 0.999999, expected: '0.9999'},
        {label: '正常2位小数', value: 1.23, expected: '1.23'},
        {label: '1位小数', value: 1.2, expected: '1.2'},
        {label: '整数', value: 1, expected: '1'},
        {label: '零值', value: 0, expected: '0.00'},
        {label: '百分比测试', value: 0.123456, expected: '0.1234%', isPercentage: true},
    ];

    return (
        <Card title="数值截断功能测试" style={{margin: 20}}>
            <Row gutter={[16, 16]}>
                {testCases.map((test, index) => (
                    <Col span={6} key={index}>
                        <Card size="small">
                            <Statistic
                                title={test.label}
                                value={test.isPercentage ? formatPercentage(test.value, 2) : formatEffectiveDecimal(test.value, 2)}
                                valueStyle={{fontSize: '16px'}}
                            />
                            <div style={{fontSize: '12px', color: '#666', marginTop: 8}}>
                                期望: {test.expected}
                            </div>
                        </Card>
                    </Col>
                ))}
            </Row>
        </Card>
    );
};

export default TestComponent;