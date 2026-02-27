import React, {useState} from 'react';
import {Card, Col, Row, Slider, Typography} from 'antd';

const {Title, Text} = Typography;

// 模拟K线图浮窗定位算法
const calculateTooltipPositionOld = (mouseX: number, mouseY: number, chartWidth: number, chartHeight: number) => {
    const tooltipWidth = 180;
    const tooltipHeight = 120;
    const offset = 15;
    const chartX = 60;
    const chartY = 20;
    const chartRightEdge = chartX + chartWidth;

    let left;
    let top = mouseY - tooltipHeight;

    // 旧的定位逻辑（可能覆盖鼠标）
    const rightEdgeIfRight = mouseX + offset + tooltipWidth;

    if (rightEdgeIfRight > chartRightEdge) {
        left = mouseX - tooltipWidth - offset; // 这里可能会覆盖鼠标
    } else {
        left = mouseX + offset;
    }

    return {left, top, mouseX, mouseY, tooltipWidth, tooltipHeight};
};

const calculateTooltipPositionNew = (mouseX: number, mouseY: number, chartWidth: number, chartHeight: number) => {
    const tooltipWidth = 180;
    const tooltipHeight = 120;
    const offset = 15;
    const chartX = 60;
    const chartY = 20;
    const chartRightEdge = chartX + chartWidth;
    const chartLeftEdge = chartX;

    let left;
    let top = mouseY - tooltipHeight - offset; // 确保有间距

    // 新的定位逻辑（确保不覆盖鼠标）
    const rightEdgeIfRight = mouseX + offset + tooltipWidth;
    const leftEdgeIfLeft = mouseX - offset - tooltipWidth;

    // 检查右侧是否有足够空间
    if (rightEdgeIfRight <= chartRightEdge) {
        left = mouseX + offset;
    } else if (leftEdgeIfLeft >= chartLeftEdge) {
        left = mouseX - tooltipWidth - offset;
    } else {
        const rightSpace = chartRightEdge - mouseX;
        const leftSpace = mouseX - chartLeftEdge;

        if (rightSpace > leftSpace) {
            left = chartRightEdge - tooltipWidth - 5;
        } else {
            left = chartLeftEdge + 5;
        }
    }

    // 确保不覆盖鼠标
    const mouseXRange = {start: mouseX - 5, end: mouseX + 5};
    const tooltipRange = {start: left, end: left + tooltipWidth};

    if (!(tooltipRange.end < mouseXRange.start || tooltipRange.start > mouseXRange.end)) {
        const rightSpace = chartRightEdge - mouseX;
        const leftSpace = mouseX - chartLeftEdge;

        if (rightSpace >= tooltipWidth + offset) {
            left = mouseX + offset;
        } else if (leftSpace >= tooltipWidth + offset) {
            left = mouseX - tooltipWidth - offset;
        } else {
            if (rightSpace > leftSpace) {
                left = Math.min(mouseX + offset, chartRightEdge - tooltipWidth - 5);
            } else {
                left = Math.max(mouseX - tooltipWidth - offset, chartLeftEdge + 5);
            }
        }
    }

    return {left, top, mouseX, mouseY, tooltipWidth, tooltipHeight};
};

const TooltipPositionTest: React.FC = () => {
    const [mouseX, setMouseX] = useState(400);
    const [mouseY, setMouseY] = useState(200);
    const chartWidth = 600;
    const chartHeight = 400;

    const oldPosition = calculateTooltipPositionOld(mouseX, mouseY, chartWidth, chartHeight);
    const newPosition = calculateTooltipPositionNew(mouseX, mouseY, chartWidth, chartHeight);

    // 检查是否会覆盖鼠标
    const checkOverlap = (position: any) => {
        const mouseXRange = {start: mouseX - 5, end: mouseX + 5};
        const tooltipRange = {start: position.left, end: position.left + position.tooltipWidth};
        return !(tooltipRange.end < mouseXRange.start || tooltipRange.start > mouseXRange.end);
    };

    const oldOverlap = checkOverlap(oldPosition);
    const newOverlap = checkOverlap(newPosition);

    return (
        <div style={{padding: 20}}>
            <Card title="K线图浮窗定位测试" style={{marginBottom: 20}}>
                <Row gutter={[16, 16]}>
                    <Col span={12}>
                        <Title level={4}>鼠标位置控制</Title>
                        <div style={{marginBottom: 20}}>
                            <Text>X坐标: {mouseX}px</Text>
                            <Slider
                                min={50}
                                max={750}
                                value={mouseX}
                                onChange={setMouseX}
                                style={{marginTop: 10}}
                            />
                        </div>
                        <div>
                            <Text>Y坐标: {mouseY}px</Text>
                            <Slider
                                min={50}
                                max={350}
                                value={mouseY}
                                onChange={setMouseY}
                                style={{marginTop: 10}}
                            />
                        </div>
                    </Col>
                    <Col span={12}>
                        <Title level={4}>测试结果</Title>
                        <div style={{marginTop: 20}}>
                            <div style={{marginBottom: 10}}>
                                <Text strong>旧算法: </Text>
                                <Text style={{color: oldOverlap ? '#ff4d4f' : '#52c41a'}}>
                                    {oldOverlap ? '❌ 会覆盖鼠标' : '✅ 不会覆盖鼠标'}
                                </Text>
                            </div>
                            <div>
                                <Text strong>新算法: </Text>
                                <Text style={{color: newOverlap ? '#ff4d4f' : '#52c41a'}}>
                                    {newOverlap ? '❌ 会覆盖鼠标' : '✅ 不会覆盖鼠标'}
                                </Text>
                            </div>
                        </div>
                    </Col>
                </Row>
            </Card>

            <Row gutter={[16, 16]}>
                <Col span={12}>
                    <Card title="旧算法效果 (可能覆盖鼠标)" size="small">
                        <div style={{
                            position: 'relative',
                            width: chartWidth,
                            height: chartHeight,
                            backgroundColor: '#f5f5f5',
                            border: '1px solid #d9d9d9'
                        }}>
                            {/* 鼠标位置 */}
                            <div
                                style={{
                                    position: 'absolute',
                                    left: mouseX - 5,
                                    top: mouseY - 5,
                                    width: 10,
                                    height: 10,
                                    backgroundColor: '#ff4d4f',
                                    borderRadius: '50%',
                                    zIndex: 1000
                                }}
                            />
                            <Text style={{
                                position: 'absolute',
                                left: mouseX + 10,
                                top: mouseY - 5,
                                fontSize: '12px'
                            }}>鼠标</Text>

                            {/* 浮窗 */}
                            <div
                                style={{
                                    position: 'absolute',
                                    left: oldPosition.left,
                                    top: oldPosition.top,
                                    width: oldPosition.tooltipWidth,
                                    height: oldPosition.tooltipHeight,
                                    backgroundColor: 'rgba(0, 0, 0, 0.9)',
                                    color: '#fff',
                                    padding: '8px',
                                    borderRadius: '6px',
                                    fontSize: '12px',
                                    border: oldOverlap ? '2px solid #ff4d4f' : '2px solid #52c41a'
                                }}
                            >
                                <div style={{fontWeight: 'bold', marginBottom: '6px'}}>K线数据</div>
                                <div>开: 67123.4567</div>
                                <div>高: 67456.7890</div>
                                <div>低: 66989.0123</div>
                                <div>收: 67234.5678</div>
                            </div>
                        </div>
                    </Card>
                </Col>

                <Col span={12}>
                    <Card title="新算法效果 (不会覆盖鼠标)" size="small">
                        <div style={{
                            position: 'relative',
                            width: chartWidth,
                            height: chartHeight,
                            backgroundColor: '#f5f5f5',
                            border: '1px solid #d9d9d9'
                        }}>
                            {/* 鼠标位置 */}
                            <div
                                style={{
                                    position: 'absolute',
                                    left: mouseX - 5,
                                    top: mouseY - 5,
                                    width: 10,
                                    height: 10,
                                    backgroundColor: '#ff4d4f',
                                    borderRadius: '50%',
                                    zIndex: 1000
                                }}
                            />
                            <Text style={{
                                position: 'absolute',
                                left: mouseX + 10,
                                top: mouseY - 5,
                                fontSize: '12px'
                            }}>鼠标</Text>

                            {/* 浮窗 */}
                            <div
                                style={{
                                    position: 'absolute',
                                    left: newPosition.left,
                                    top: newPosition.top,
                                    width: newPosition.tooltipWidth,
                                    height: newPosition.tooltipHeight,
                                    backgroundColor: 'rgba(0, 0, 0, 0.9)',
                                    color: '#fff',
                                    padding: '8px',
                                    borderRadius: '6px',
                                    fontSize: '12px',
                                    border: newOverlap ? '2px solid #ff4d4f' : '2px solid #52c41a'
                                }}
                            >
                                <div style={{fontWeight: 'bold', marginBottom: '6px'}}>K线数据</div>
                                <div>开: 67123.4567</div>
                                <div>高: 67456.789</div>
                                <div>低: 66989.0123</div>
                                <div>收: 67234.5678</div>
                            </div>
                        </div>
                    </Card>
                </Col>
            </Row>

            <Card title="优化说明" style={{marginTop: 20}}>
                <Row gutter={[16, 16]}>
                    <Col span={12}>
                        <Title level={5}>✨ 新算法改进点:</Title>
                        <ul>
                            <li>增加鼠标活动区域检测（±5px）</li>
                            <li>优先显示在右侧，减少遮挡</li>
                            <li>双重保护确保不覆盖鼠标</li>
                            <li>垂直方向增加适当间距</li>
                            <li>边界保护，防止浮窗超出图表</li>
                        </ul>
                    </Col>
                    <Col span={12}>
                        <Title level={5}>🎯 使用方法:</Title>
                        <ul>
                            <li>拖动滑块改变鼠标位置</li>
                            <li>观察新旧算法的差异</li>
                            <li>红色边框表示会覆盖鼠标</li>
                            <li>绿色边框表示安全位置</li>
                            <li>在真实K线图中测试效果</li>
                        </ul>
                    </Col>
                </Row>
            </Card>
        </div>
    );
};

export default TooltipPositionTest;

