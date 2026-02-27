import React from 'react';
import {Card, Col, Row} from 'antd';
import {formatEffectiveDecimal} from '../../utils/numberFormatter';

// 模拟K线图数据浮窗的显示效果
const CandlestickFormatTest: React.FC = () => {
    // 模拟K线数据
    const mockCandlestickData = {
        timestamp: Date.now(),
        open: 67123.456789,
        high: 67456.789012,
        low: 66989.012345,
        close: 67234.567890,
        volume: 1234.5678
    };

    // 模拟鼠标价格
    const mockCursorPrice = 67200.123456;

    const formatTime = (timestamp: number) => {
        const date = new Date(timestamp);
        const year = date.getFullYear().toString().slice(-2);
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');
        const hours = date.getHours().toString().padStart(2, '0');
        const minutes = date.getMinutes().toString().padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}`;
    };

    const priceTestCases = [
        {label: '开盘价', value: mockCandlestickData.open, color: '#52c41a'},
        {label: '最高价', value: mockCandlestickData.high, color: '#ff4d4f'},
        {label: '最低价', value: mockCandlestickData.low, color: '#ff4d4f'},
        {label: '收盘价', value: mockCandlestickData.close, color: '#52c41a'},
        {label: '鼠标价格', value: mockCursorPrice, color: '#ffd700'},
    ];

    return (
        <div style={{padding: 20}}>
            <Card title="K线图数据浮窗格式化测试 (最多4位有效小数)" style={{marginBottom: 20}}>
                <Row gutter={[16, 16]}>
                    {priceTestCases.map((test, index) => (
                        <Col span={12} key={index}>
                            <Card size="small">
                                <div style={{marginBottom: 8}}>
                                    <span style={{color: '#8c8c8c', fontSize: '14px'}}>
                                        {test.label} (原始):
                                    </span>
                                    <span style={{marginLeft: 8, fontSize: '14px'}}>
                                        {test.value}
                                    </span>
                                </div>
                                <div>
                                    <span style={{color: '#8c8c8c', fontSize: '14px'}}>
                                        {test.label} (优化后):
                                    </span>
                                    <span style={{
                                        marginLeft: 8,
                                        color: test.color,
                                        fontSize: '14px',
                                        fontWeight: 'bold'
                                    }}>
                                        {formatEffectiveDecimal(test.value, 4)}
                                    </span>
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>
            </Card>

            <Card title="K线图浮窗模拟效果" style={{marginBottom: 20}}>
                <div style={{
                    backgroundColor: 'rgba(0, 0, 0, 0.9)',
                    color: '#fff',
                    padding: '12px',
                    borderRadius: '8px',
                    fontSize: '12px',
                    width: '220px',
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    margin: '0 auto'
                }}>
                    {/* 时间信息 */}
                    <div style={{
                        fontWeight: 'bold',
                        marginBottom: '8px',
                        borderBottom: '1px solid #555',
                        paddingBottom: '6px',
                        color: '#1890ff',
                        fontSize: '12px'
                    }}>
                        {formatTime(mockCandlestickData.timestamp)}
                    </div>

                    {/* 鼠标价格 */}
                    <div style={{
                        marginTop: '8px',
                        padding: '6px 10px',
                        backgroundColor: 'rgba(255, 255, 255, 0.1)',
                        borderRadius: '4px',
                        textAlign: 'center',
                        fontWeight: 'bold',
                        color: '#ffd700',
                        fontSize: '12px',
                        marginBottom: '8px'
                    }}>
                        Cursor: {formatEffectiveDecimal(mockCursorPrice, 4)}
                    </div>

                    {/* OHLC价格信息 */}
                    <div style={{display: 'flex', justifyContent: 'space-between', gap: '12px', marginTop: '8px'}}>
                        <div>
                            <span style={{color: '#8c8c8c', fontSize: '11px'}}>开: </span>
                            <span style={{color: '#52c41a', fontSize: '12px'}}>
                                {formatEffectiveDecimal(mockCandlestickData.open, 4)}
                            </span>
                        </div>
                        <div>
                            <span style={{color: '#8c8c8c', fontSize: '11px'}}>高: </span>
                            <span style={{color: '#ff4d4f', fontSize: '12px'}}>
                                {formatEffectiveDecimal(mockCandlestickData.high, 4)}
                            </span>
                        </div>
                    </div>
                    <div style={{display: 'flex', justifyContent: 'space-between', gap: '12px', marginTop: '4px'}}>
                        <div>
                            <span style={{color: '#8c8c8c', fontSize: '11px'}}>低: </span>
                            <span style={{color: '#ff4d4f', fontSize: '12px'}}>
                                {formatEffectiveDecimal(mockCandlestickData.low, 4)}
                            </span>
                        </div>
                        <div>
                            <span style={{color: '#8c8c8c', fontSize: '11px'}}>收: </span>
                            <span style={{
                                color: mockCandlestickData.close >= mockCandlestickData.open ? '#52c41a' : '#ff4d4f',
                                fontSize: '12px'
                            }}>
                                {formatEffectiveDecimal(mockCandlestickData.close, 4)}
                            </span>
                        </div>
                    </div>
                </div>
            </Card>

            <Card title="优化效果对比">
                <Row gutter={[16, 16]}>
                    <Col span={12}>
                        <Card size="small" title="修改前 (toFixed)">
                            <div style={{backgroundColor: '#f5f5f5', padding: '10px', borderRadius: '4px'}}>
                                <div>Cursor: {mockCursorPrice.toFixed(4)}</div>
                                <div>开: {mockCandlestickData.open.toFixed(4)}</div>
                                <div>高: {mockCandlestickData.high.toFixed(4)}</div>
                                <div>低: {mockCandlestickData.low.toFixed(4)}</div>
                                <div>收: {mockCandlestickData.close.toFixed(4)}</div>
                            </div>
                        </Card>
                    </Col>
                    <Col span={12}>
                        <Card size="small" title="修改后 (有效小数+截断)">
                            <div style={{backgroundColor: '#f5f5f5', padding: '10px', borderRadius: '4px'}}>
                                <div>Cursor: {formatEffectiveDecimal(mockCursorPrice, 4)}</div>
                                <div>开: {formatEffectiveDecimal(mockCandlestickData.open, 4)}</div>
                                <div>高: {formatEffectiveDecimal(mockCandlestickData.high, 4)}</div>
                                <div>低: {formatEffectiveDecimal(mockCandlestickData.low, 4)}</div>
                                <div>收: {formatEffectiveDecimal(mockCandlestickData.close, 4)}</div>
                            </div>
                        </Card>
                    </Col>
                </Row>
            </Card>
        </div>
    );
};

export default CandlestickFormatTest;

