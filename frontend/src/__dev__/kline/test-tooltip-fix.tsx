import React, {useState} from 'react';
import {Alert, Button, Card, Col, Row, Slider, Typography} from 'antd';

const {Title, Text} = Typography;

// 修复后的浮窗定位算法（与实际代码保持一致）
const calculateTooltipPositionFixed = (mouseX: number, mouseY: number, actualSize: any) => {
    // 容错处理：确保输入参数有效
    const safeMouseX = Math.max(0, Math.min(mouseX, actualSize.width || 0));
    const safeMouseY = Math.max(0, Math.min(mouseY, actualSize.height || 0));

    const tooltipWidth = 180;
    const tooltipHeight = 120;
    const offset = 15;
    const chartX = 60;
    const chartY = 20;
    const currentWidth = actualSize.width || 0;
    const currentHeight = actualSize.height || 0;
    const chartWidth = Math.max(0, currentWidth - 80);
    const chartHeight = Math.max(0, currentHeight - 80);
    const chartRightEdge = chartX + chartWidth;
    const chartLeftEdge = chartX;

    // 容错处理：确保图表尺寸有效
    if (chartWidth <= 0 || chartHeight <= 0) {
        return {left: safeMouseX + offset, top: safeMouseY - tooltipHeight - offset};
    }

    let left;
    let top;

    // 计算鼠标活动区域
    const mouseXRange = {
        start: safeMouseX - 8,
        end: safeMouseX + 8
    };

    // 计算可用空间
    const rightAvailableSpace = Math.max(0, chartRightEdge - (safeMouseX + offset));
    const leftAvailableSpace = Math.max(0, (safeMouseX - offset) - chartLeftEdge);

    let candidatePositions = [];

    // 候选位置1：右侧（完全安全）
    if (rightAvailableSpace >= tooltipWidth + 5) {
        const rightPos = safeMouseX + offset;
        const tooltipRange = {start: rightPos, end: rightPos + tooltipWidth};
        if (tooltipRange.end < mouseXRange.start || tooltipRange.start > mouseXRange.end) {
            candidatePositions.push({
                position: rightPos,
                priority: 1,
                side: 'right'
            });
        }
    }

    // 候选位置2：左侧（完全安全）
    if (leftAvailableSpace >= tooltipWidth + 5) {
        const leftPos = safeMouseX - tooltipWidth - offset;
        const tooltipRange = {start: leftPos, end: leftPos + tooltipWidth};
        if (tooltipRange.end < mouseXRange.start || tooltipRange.start > mouseXRange.end) {
            candidatePositions.push({
                position: leftPos,
                priority: 2,
                side: 'left'
            });
        }
    }

    // 选择最佳位置
    if (candidatePositions.length > 0) {
        candidatePositions.sort((a, b) => a.priority - b.priority);
        left = candidatePositions[0].position;
    } else {
        if (rightAvailableSpace > leftAvailableSpace) {
            left = Math.min(safeMouseX + offset, chartRightEdge - tooltipWidth - 5);
        } else {
            left = Math.max(safeMouseX - tooltipWidth - offset, chartLeftEdge + 5);
        }
    }

    // 垂直方向定位
    const topSpace = Math.max(0, safeMouseY - chartY - offset);
    const bottomSpace = Math.max(0, (chartY + chartHeight) - safeMouseY - offset);

    if (topSpace >= tooltipHeight + 5) {
        top = safeMouseY - tooltipHeight - offset;
    } else if (bottomSpace >= tooltipHeight + 5) {
        top = safeMouseY + offset;
    } else {
        if (topSpace > bottomSpace) {
            top = Math.max(chartY + 5, chartY + chartHeight - tooltipHeight - 5);
        } else {
            top = Math.min(chartY + chartHeight - tooltipHeight - 5, safeMouseY + offset);
        }
    }

    // 最终边界保护
    const finalLeft = Math.max(chartLeftEdge + 5, Math.min(left, chartRightEdge - tooltipWidth - 5));
    const finalTop = Math.max(chartY + 5, Math.min(top, chartY + chartHeight - tooltipHeight - 5));

    return {left: finalLeft, top: finalTop};
};

const TooltipFixTest: React.FC = () => {
    const [mouseX, setMouseX] = useState(400);
    const [mouseY, setMouseY] = useState(200);
    const [actualSize, setActualSize] = useState({width: 700, height: 450});
    const [testResult, setTestResult] = useState<string>('');
    const chartWidth = actualSize.width - 80;
    const chartHeight = actualSize.height - 80;

    // 测试各种场景
    const testScenarios = [
        {name: '左侧边缘', x: 80, y: 200},
        {name: '右侧边缘', x: 650, y: 200},
        {name: '顶部边缘', x: 350, y: 50},
        {name: '底部边缘', x: 350, y: 400},
        {name: '左上角', x: 80, y: 50},
        {name: '右上角', x: 650, y: 50},
        {name: '左下角', x: 80, y: 400},
        {name: '右下角', x: 650, y: 400},
        {name: '中心位置', x: 350, y: 225},
    ];

    const position = calculateTooltipPositionFixed(mouseX, mouseY, actualSize);

    // 检查是否覆盖鼠标
    const checkOverlap = () => {
        const mouseXRange = {start: mouseX - 8, end: mouseX + 8};
        const tooltipRange = {start: position.left, end: position.left + 180};
        const overlap = !(tooltipRange.end < mouseXRange.start || tooltipRange.start > mouseXRange.end);
        return overlap;
    };

    // 测试所有场景
    const runAllTests = () => {
        let passedTests = 0;
        let totalTests = testScenarios.length;

        testScenarios.forEach(scenario => {
            const pos = calculateTooltipPositionFixed(scenario.x, scenario.y, actualSize);
            const mouseXRange = {start: scenario.x - 8, end: scenario.x + 8};
            const tooltipRange = {start: pos.left, end: pos.left + 180};
            const overlap = !(tooltipRange.end < mouseXRange.start || tooltipRange.start > mouseXRange.end);

            // 检查是否在边界内
            const inBounds = (
                pos.left >= 60 &&
                pos.left + 180 <= actualSize.width - 20 &&
                pos.top >= 20 &&
                pos.top + 120 <= actualSize.height - 60
            );

            if (!overlap && inBounds) {
                passedTests++;
            }
        });

        const successRate = ((passedTests / totalTests) * 100).toFixed(1);
        setTestResult(`测试完成: ${passedTests}/${totalTests} 通过 (${successRate}%)`);
    };

    const overlap = checkOverlap();

    return (
        <div style={{padding: 20}}>
            <Card title="K线图浮窗定位Bug修复验证" style={{marginBottom: 20}}>
                <Alert
                    message="修复完成验证"
                    description="测试新的浮窗定位算法是否解决了覆盖鼠标和边界溢出问题"
                    type="success"
                    style={{marginBottom: 20}}
                />

                <Row gutter={[16, 16]}>
                    <Col span={12}>
                        <Title level={4}>交互测试</Title>
                        <div style={{marginBottom: 20}}>
                            <Text>X坐标: {mouseX}px</Text>
                            <Slider
                                min={0}
                                max={actualSize.width}
                                value={mouseX}
                                onChange={setMouseX}
                                style={{marginTop: 10}}
                            />
                        </div>
                        <div style={{marginBottom: 20}}>
                            <Text>Y坐标: {mouseY}px</Text>
                            <Slider
                                min={0}
                                max={actualSize.height}
                                value={mouseY}
                                onChange={setMouseY}
                                style={{marginTop: 10}}
                            />
                        </div>
                        <div>
                            <Text>图表尺寸: {actualSize.width} × {actualSize.height}</Text>
                        </div>
                    </Col>
                    <Col span={12}>
                        <Title level={4}>测试结果</Title>
                        <div style={{marginTop: 20}}>
                            <div style={{marginBottom: 10}}>
                                <Text strong>鼠标覆盖检测: </Text>
                                <Text style={{color: overlap ? '#ff4d4f' : '#52c41a'}}>
                                    {overlap ? '❌ 覆盖鼠标' : '✅ 不覆盖鼠标'}
                                </Text>
                            </div>
                            <div style={{marginBottom: 10}}>
                                <Text strong>浮窗位置: </Text>
                                <Text> ({Math.round(position.left)}, {Math.round(position.top)})</Text>
                            </div>
                            <div style={{marginBottom: 20}}>
                                <Button type="primary" onClick={runAllTests}>
                                    运行自动化测试
                                </Button>
                            </div>
                            {testResult && (
                                <Alert
                                    message={testResult}
                                    type={testResult.includes('100%') ? 'success' : 'warning'}
                                />
                            )}
                        </div>
                    </Col>
                </Row>
            </Card>

            <Card title="可视化测试区域">
                <div style={{
                    position: 'relative',
                    width: actualSize.width,
                    height: actualSize.height,
                    backgroundColor: '#f5f5f5',
                    border: '2px solid #1890ff',
                    margin: '0 auto'
                }}>
                    {/* 图表区域 */}
                    <div style={{
                        position: 'absolute',
                        left: 60,
                        top: 20,
                        width: chartWidth,
                        height: chartHeight,
                        backgroundColor: '#e6f7ff',
                        border: '1px dashed #1890ff'
                    }}>
                        <Text style={{
                            position: 'absolute',
                            top: '50%',
                            left: '50%',
                            transform: 'translate(-50%, -50%)',
                            color: '#666'
                        }}>
                            K线图区域 ({chartWidth} × {chartHeight})
                        </Text>
                    </div>

                    {/* 鼠标位置 */}
                    <div
                        style={{
                            position: 'absolute',
                            left: mouseX - 8,
                            top: mouseY - 8,
                            width: 16,
                            height: 16,
                            backgroundColor: '#ff4d4f',
                            borderRadius: '50%',
                            zIndex: 1000,
                            border: '2px solid #fff'
                        }}
                    />

                    {/* 浮窗 */}
                    <div
                        style={{
                            position: 'absolute',
                            left: position.left,
                            top: position.top,
                            width: 180,
                            height: 120,
                            backgroundColor: 'rgba(0, 0, 0, 0.9)',
                            color: '#fff',
                            padding: '8px',
                            borderRadius: '6px',
                            fontSize: '12px',
                            border: overlap ? '2px solid #ff4d4f' : '2px solid #52c41a',
                            zIndex: 999
                        }}
                    >
                        <div style={{fontWeight: 'bold', marginBottom: '6px'}}>
                            K线数据
                        </div>
                        <div>开: 67123.4567</div>
                        <div>高: 67456.789</div>
                        <div>低: 66989.0123</div>
                        <div>收: 67234.5678</div>
                    </div>

                    {/* 坐标标注 */}
                    <Text style={{
                        position: 'absolute',
                        left: mouseX + 12,
                        top: mouseY - 5,
                        fontSize: '12px',
                        color: '#ff4d4f',
                        fontWeight: 'bold',
                        zIndex: 1001
                    }}>
                        鼠标 ({mouseX}, {mouseY})
                    </Text>
                </div>
            </Card>

            <Card title="预设场景快速测试" style={{marginTop: 20}}>
                <Row gutter={[16, 16]}>
                    {testScenarios.map((scenario, index) => (
                        <Col span={6} key={index}>
                            <Button
                                onClick={() => {
                                    setMouseX(scenario.x);
                                    setMouseY(scenario.y);
                                }}
                                style={{width: '100%'}}
                            >
                                {scenario.name}
                            </Button>
                        </Col>
                    ))}
                </Row>
            </Card>
        </div>
    );
};

export default TooltipFixTest;

