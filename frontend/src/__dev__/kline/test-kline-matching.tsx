import React, {useState} from 'react';
import {Alert, Button, Card, Col, Row, Slider, Table, Tag, Typography} from 'antd';

const {Title, Text} = Typography;

// 模拟K线数据
const mockKlineData = [
    {timestamp: 1635000000000, open: 47000, high: 47500, low: 46800, close: 47200, volume: 1000},
    {timestamp: 1635000060000, open: 47200, high: 47800, low: 47100, close: 47600, volume: 1200},
    {timestamp: 1635000120000, open: 47600, high: 48000, low: 47400, close: 47700, volume: 900},
    {timestamp: 1635000180000, open: 47700, high: 47900, low: 47500, close: 47550, volume: 800},
    {timestamp: 1635000240000, open: 47550, high: 47650, low: 47300, close: 47350, volume: 1100},
    {timestamp: 1635000300000, open: 47350, high: 47500, low: 47200, close: 47450, volume: 1300},
    {timestamp: 1635000360000, open: 47450, high: 47600, low: 47300, close: 47500, volume: 1000},
    {timestamp: 1635000420000, open: 47500, high: 47700, low: 47400, close: 47600, volume: 700},
    {timestamp: 1635000480000, open: 47600, high: 47800, low: 47500, close: 47700, volume: 600},
    {timestamp: 1635000540000, open: 47700, high: 47900, low: 47600, close: 47800, volume: 500},
    {timestamp: 1635000600000, open: 47800, high: 48000, low: 47650, close: 47750, volume: 400},
    {timestamp: 1635000660000, open: 47750, high: 47850, low: 47600, close: 47650, volume: 300},
];

// 修复后的K线索引计算算法（与实际代码保持一致）
const calculateKlineIndex = (relativeMouseX: number, chartWidth: number, dataLength: number) => {
    const barWidth = Math.max(1, Math.min(20, chartWidth / dataLength - 2));
    const barSpacing = (chartWidth - barWidth * dataLength) / (dataLength + 1);

    // 方法1：基于位置的精确计算
    let index = -1;

    if (relativeMouseX >= barSpacing) {
        const potentialIndex = Math.floor((relativeMouseX - barSpacing) / (barWidth + barSpacing));

        if (potentialIndex >= 0 && potentialIndex < dataLength) {
            const candleX = barSpacing + potentialIndex * (barWidth + barSpacing);

            if (relativeMouseX >= candleX && relativeMouseX <= candleX + barWidth) {
                index = potentialIndex;
            } else {
                const checkPrevious = potentialIndex - 1;
                const checkNext = potentialIndex + 1;

                if (checkPrevious >= 0 && checkPrevious < dataLength) {
                    const prevCandleX = barSpacing + checkPrevious * (barWidth + barSpacing);
                    if (relativeMouseX >= prevCandleX && relativeMouseX <= prevCandleX + barWidth) {
                        index = checkPrevious;
                    }
                }

                if (index === -1 && checkNext >= 0 && checkNext < dataLength) {
                    const nextCandleX = barSpacing + checkNext * (barWidth + barSpacing);
                    if (relativeMouseX >= nextCandleX && relativeMouseX <= nextCandleX + barWidth) {
                        index = checkNext;
                    }
                }
            }
        }
    }

    // 方法2：如果方法1没找到，使用距离最近的算法
    if (index === -1) {
        let minDistance = Infinity;

        for (let i = 0; i < dataLength; i++) {
            const candleX = barSpacing + i * (barWidth + barSpacing);
            const candleCenterX = candleX + barWidth / 2;
            const distance = Math.abs(relativeMouseX - candleCenterX);

            const maxAcceptableDistance = (barWidth + barSpacing) * 0.75;

            if (distance < minDistance && distance <= maxAcceptableDistance) {
                minDistance = distance;
                index = i;
            }
        }
    }

    // 方法3：边界检查
    if (index === -1) {
        if (relativeMouseX < barSpacing && dataLength > 0) {
            index = 0;
        } else if (relativeMouseX >= barSpacing + (dataLength - 1) * (barWidth + barSpacing) + barWidth) {
            index = dataLength - 1;
        }
    }

    return Math.max(0, Math.min(dataLength - 1, index));
};

const KlineMatchingTest: React.FC = () => {
    const [mouseX, setMouseX] = useState(350);
    const [chartWidth, setChartWidth] = useState(600);
    const [actualSize, setActualSize] = useState({width: 700, height: 450});
    const [testResults, setTestResults] = useState<Array<{
        index: number,
        expected: number,
        actual: number,
        match: boolean,
        data: any
    }>>([]);

    // 计算K线索引
    const klineIndex = calculateKlineIndex(mouseX - 60, chartWidth, mockKlineData.length);
    const selectedKline = mockKlineData[klineIndex];

    // 计算barWidth和barSpacing用于可视化
    const barWidth = Math.max(1, Math.min(20, chartWidth / mockKlineData.length - 2));
    const barSpacing = (chartWidth - barWidth * mockKlineData.length) / (mockKlineData.length + 1);

    // 自动化测试
    const runAutomatedTest = () => {
        const results: any[] = [];
        const testPoints = [
            60 + barSpacing, // 第1根K线
            60 + barSpacing * 2 + barWidth, // 第2根K线中心
            60 + barSpacing * 3 + barWidth * 2, // 第3根K线
            60 + chartWidth / 2, // 中间位置
            60 + chartWidth - barSpacing - barWidth, // 最后一根K线
        ];

        testPoints.forEach((testX, i) => {
            const expectedIndex = Math.min(i, mockKlineData.length - 1);
            const actualIndex = calculateKlineIndex(testX - 60, chartWidth, mockKlineData.length);
            const match = actualIndex === expectedIndex;

            results.push({
                index: i + 1,
                expected: expectedIndex,
                actual: actualIndex,
                match,
                data: mockKlineData[actualIndex]
            });
        });

        setTestResults(results);
    };

    // 渲染K线图
    const renderKlineChart = () => {
        return (
            <div style={{
                position: 'relative',
                width: actualSize.width,
                height: actualSize.height,
                backgroundColor: '#1f1f1f',
                border: '2px solid #333',
                margin: '0 auto'
            }}>
                {/* 图表区域 */}
                <div style={{
                    position: 'absolute',
                    left: 60,
                    top: 20,
                    width: chartWidth,
                    height: actualSize.height - 80,
                    backgroundColor: '#2a2a2a',
                    display: 'flex',
                    alignItems: 'center'
                }}>
                    {/* 渲染K线 */}
                    {mockKlineData.map((item, index) => {
                        const candleX = barSpacing + index * (barWidth + barSpacing);
                        const isPositive = item.close > item.open;
                        const color = isPositive ? '#52c41a' : '#ff4d4f';

                        return (
                            <div
                                key={index}
                                style={{
                                    position: 'absolute',
                                    left: candleX,
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    width: barWidth,
                                    height: 40,
                                    backgroundColor: isPositive ? 'transparent' : color,
                                    border: `2px solid ${color}`,
                                    borderRadius: 2,
                                    zIndex: index === klineIndex ? 10 : 1,
                                    boxShadow: index === klineIndex ? `0 0 10px ${color}` : 'none'
                                }}
                            />
                        );
                    })}

                    {/* 鼠标指示线 */}
                    <div
                        style={{
                            position: 'absolute',
                            left: mouseX - 60,
                            top: 0,
                            width: 2,
                            height: '100%',
                            backgroundColor: '#1890ff',
                            zIndex: 20
                        }}
                    />
                </div>

                {/* 鼠标位置 */}
                <div
                    style={{
                        position: 'absolute',
                        left: mouseX - 8,
                        top: '50%',
                        width: 16,
                        height: 16,
                        backgroundColor: '#ff4d4f',
                        borderRadius: '50%',
                        transform: 'translateY(-50%)',
                        zIndex: 30,
                        border: '2px solid #fff'
                    }}
                />
            </div>
        );
    };

    return (
        <div style={{padding: 20}}>
            <Card title="K线图鼠标悬浮数据匹配测试" style={{marginBottom: 20}}>
                <Alert
                    message="修复验证：确保鼠标悬浮的数据浮窗显示正确的K线数据"
                    description="测试鼠标位置与K线索引计算的一致性"
                    type="info"
                    style={{marginBottom: 20}}
                />

                <Row gutter={[16, 16]}>
                    <Col span={12}>
                        <Title level={4}>交互测试</Title>
                        <div style={{marginBottom: 20}}>
                            <Text>鼠标X坐标: {mouseX}px</Text>
                            <Slider
                                min={60}
                                max={60 + chartWidth}
                                value={mouseX}
                                onChange={setMouseX}
                                style={{marginTop: 10}}
                            />
                        </div>
                        <div style={{marginBottom: 20}}>
                            <Text>图表宽度: {chartWidth}px</Text>
                            <Slider
                                min={400}
                                max={800}
                                value={chartWidth}
                                onChange={setChartWidth}
                                style={{marginTop: 10}}
                            />
                        </div>
                        <Button type="primary" onClick={runAutomatedTest}>
                            运行自动化测试
                        </Button>
                    </Col>
                    <Col span={12}>
                        <Title level={4}>当前选中的K线数据</Title>
                        {selectedKline ? (
                            <div style={{backgroundColor: '#f5f5f5', padding: 15, borderRadius: 8}}>
                                <div style={{marginBottom: 10}}>
                                    <Text strong>K线索引: </Text>
                                    <Tag color="blue">{klineIndex}</Tag>
                                </div>
                                <div style={{marginBottom: 10}}>
                                    <Text strong>时间戳: </Text>
                                    <Text>{new Date(selectedKline.timestamp).toLocaleString()}</Text>
                                </div>
                                <div style={{marginBottom: 10}}>
                                    <Text strong>开/收: </Text>
                                    <Text>
                                        <span
                                            style={{color: selectedKline.open > selectedKline.close ? '#ff4d4f' : '#52c41a'}}>
                                            {selectedKline.open}
                                        </span>
                                        {' / '}
                                        <span
                                            style={{color: selectedKline.close > selectedKline.open ? '#52c41a' : '#ff4d4f'}}>
                                            {selectedKline.close}
                                        </span>
                                    </Text>
                                </div>
                                <div>
                                    <Text strong>高低: </Text>
                                    <Text>
                                        <span style={{color: '#52c41a'}}>{selectedKline.high}</span>
                                        {' / '}
                                        <span style={{color: '#ff4d4f'}}>{selectedKline.low}</span>
                                    </Text>
                                </div>
                            </div>
                        ) : (
                            <div style={{color: '#999', textAlign: 'center', padding: 20}}>
                                请将鼠标移动到图表区域
                            </div>
                        )}
                    </Col>
                </Row>
            </Card>

            <Card title="可视化测试区域" style={{marginBottom: 20}}>
                {renderKlineChart()}
            </Card>

            {testResults.length > 0 && (
                <Card title="自动化测试结果">
                    <Table
                        dataSource={testResults}
                        pagination={false}
                        size="small"
                        columns={[
                            {title: '测试点', dataIndex: 'index', key: 'index'},
                            {title: '期望索引', dataIndex: 'expected', key: 'expected'},
                            {title: '实际索引', dataIndex: 'actual', key: 'actual'},
                            {
                                title: '匹配结果',
                                dataIndex: 'match',
                                key: 'match',
                                render: (match) => (
                                    <Tag color={match ? 'success' : 'error'}>
                                        {match ? '✅ 匹配' : '❌ 不匹配'}
                                    </Tag>
                                )
                            },
                        ]}
                        summary={() => {
                            const passedCount = testResults.filter(r => r.match).length;
                            const totalCount = testResults.length;
                            const successRate = ((passedCount / totalCount) * 100).toFixed(1);
                            return (
                                <div>
                                    总计: {totalCount} | 通过: {passedCount} | 成功率: {successRate}%
                                </div>
                            );
                        }}
                    />
                </Card>
            )}
        </div>
    );
};

export default KlineMatchingTest;

