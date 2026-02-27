import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Button, Card, Checkbox, Col, Divider, InputNumber, Row, Space, Tag, Typography, message} from 'antd';
import {CheckOutlined, CloseOutlined, DeleteOutlined, PlusOutlined} from '@ant-design/icons';
import {IndicatorConfig, TechnicalIndicator} from '../../../hooks/useChartState';
import PortalContainer from '../../../components/common/PortalContainer';

const {Text} = Typography;

interface IndicatorDropdownPanelProps {
    indicator: TechnicalIndicator;
    indicatorConfig: IndicatorConfig;
    isVisible: boolean;
    onClose: () => void;
    onUpdateConfig: (indicator: TechnicalIndicator, config: Partial<IndicatorConfig>) => void;
    onActivateIndicator?: (indicator: TechnicalIndicator) => void; // 新增：激活指标函数
    selectedIndicators?: TechnicalIndicator[]; // 新增：当前已选中的指标列表
    triggerElement?: HTMLElement | null; // 触发器元素，用于位置计算
    className?: string;
    style?: React.CSSProperties;
}

interface PeriodItem {
    period: number;
    color: string;
    isActive: boolean;
    stdDev?: number; // BOLL专用：标准差值
}

/**
 * 技术指标向下弹出配置面板
 * 支持多周期配置、颜色管理和周期操作
 */
const IndicatorDropdownPanel: React.FC<IndicatorDropdownPanelProps> = ({
                                                                           indicator,
                                                                           indicatorConfig,
                                                                           isVisible,
                                                                           onClose,
                                                                           onUpdateConfig,
                                                                           onActivateIndicator,
                                                                           selectedIndicators = [],
                                                                           triggerElement,
                                                                           className,
                                                                           style
                                                                       }) => {
    const [newPeriod, setNewPeriod] = useState<number>(20);
    const [showNewPeriodInput, setShowNewPeriodInput] = useState<boolean>(false);
    const [position, setPosition] = useState<{ top: number; left: number }>({top: 0, left: 0});
    const panelRef = useRef<HTMLDivElement>(null);

    // 指标特定颜色映射 - 与CandlestickChart保持一致
    const indicatorColorMap: Record<string, string[]> = {
        EMA: ['#1890ff', '#00d2d3', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#13c2c2'],
        SMA: ['#eb2f96', '#fa541c', '#fadb14', '#52c41a', '#1890ff', '#722ed1', '#13c2c2'],
        WMA: ['#a0d911', '#faad14', '#fa8c16', '#f5222d', '#cf1322', '#722ed1', '#2f54eb'],
        RSI: ['#9254de', '#13c2c2', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#1890ff'],
        BOLL: ['#ff4d4f', '#52c41a', '#1890ff', '#fa8c16', '#722ed1', '#faad14', '#13c2c2']
    };

    // 获取当前指标的颜色序列，如果未定义则使用默认序列
    const defaultColorSequence = [
        '#1890ff', '#00d2d3', '#52c41a', '#fa8c16', '#f5222d',
        '#722ed1', '#13c2c2', '#eb2f96', '#fa541c', '#fadb14'
    ];
    
    const colorSequence = indicatorColorMap[indicator] || defaultColorSequence;

    // 计算弹窗位置
    const calculatePosition = useCallback(() => {
        if (!triggerElement || !isVisible) return;

        const triggerRect = triggerElement.getBoundingClientRect();
        const panelWidth = 280; // 预估的面板宽度
        const panelHeight = 400; // 预估的面板高度
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        let left = triggerRect.left;
        let top = triggerRect.bottom + 4; // 在触发器下方4px

        // 水平边界检查 - 确保面板不超出视窗
        if (left + panelWidth > viewportWidth) {
            left = viewportWidth - panelWidth - 8; // 右边留8px边距
        }
        if (left < 8) {
            left = 8; // 左边留8px边距
        }

        // 垂直边界检查
        if (top + panelHeight > viewportHeight) {
            // 如果下方空间不足，显示在触发器上方
            top = triggerRect.top - panelHeight - 4;
            // 如果上方空间也不足，则显示在视窗顶部
            if (top < 8) {
                top = 8;
            }
        }

        setPosition({left, top});
    }, [triggerElement, isVisible]);

    // 监听窗口大小变化和滚动
    useEffect(() => {
        if (isVisible) {
            calculatePosition();

            // 监听窗口大小变化
            const handleResize = () => calculatePosition();
            window.addEventListener('resize', handleResize);

            // 监听滚动事件
            const handleScroll = () => calculatePosition();
            window.addEventListener('scroll', handleScroll, true);

            return () => {
                window.removeEventListener('resize', handleResize);
                window.removeEventListener('scroll', handleScroll, true);
            };
        }
    }, [isVisible, calculatePosition]);

    // 点击外部关闭
    useEffect(() => {
        if (isVisible) {
            const handleClickOutside = (event: MouseEvent) => {
                if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
                    // 检查点击是否在触发器元素外
                    if (!triggerElement || !triggerElement.contains(event.target as Node)) {
                        onClose();
                    }
                }
            };

            document.addEventListener('mousedown', handleClickOutside);
            return () => {
                document.removeEventListener('mousedown', handleClickOutside);
            };
        }
    }, [isVisible, onClose, triggerElement]);

    // 指标特定配置
    const indicatorConfigs = {
        EMA: {
            name: 'EMA',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            icon: '📈',
            description: '指数移动平均线'
        },
        SMA: {
            name: 'SMA',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            icon: '📊',
            description: '简单移动平均线'
        },
        WMA: {
            name: 'WMA',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            icon: '📉',
            description: '加权移动平均线'
        },
        BOLL: {
            name: 'BOLL',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            icon: '🎯',
            description: '布林带'
        },
        RSI: {
            name: 'RSI',
            defaultPeriod: 14,
            minPeriod: 1,
            maxPeriod: 60,
            icon: '⚡',
            description: '相对强弱指数'
        },
        MACD: {
            name: 'MACD',
            defaultPeriod: 12,
            minPeriod: 1,
            maxPeriod: 60,
            icon: '🔄',
            description: 'MACD指标'
        }
    };

    const config = indicatorConfigs[indicator];

    // 获取当前周期的列表
    const getCurrentPeriods = (): PeriodItem[] => {
        // 添加详细调试信息
        console.log(`[IndicatorDropdownPanel] ${indicator} getCurrentPeriods 调用:`, {
            indicatorConfig,
            有periods: !!indicatorConfig.periods,
            periods长度: indicatorConfig.periods?.length,
            periods详情: indicatorConfig.periods,
            hasPeriodColors: !!indicatorConfig.periodColors,
            periodColors详情: indicatorConfig.periodColors,
            hasVisiblePeriods: !!indicatorConfig.visiblePeriods,
            visiblePeriods详情: indicatorConfig.visiblePeriods,
            指标是否选中: selectedIndicators?.includes(indicator),
            selectedIndicators
        });

        // 直接返回现有的多周期配置，不创建临时配置
        if (!indicatorConfig.periods || indicatorConfig.periods.length === 0) {
            console.log(`[IndicatorDropdownPanel] ${indicator} 没有多周期配置，返回空数组`);
            return [];
        }

        // 检查当前指标是否被选中
        const isIndicatorSelected = selectedIndicators?.includes(indicator) || false;

        // 计算排序后的索引映射，用于颜色分配
        // 必须与CandlestickChart中的逻辑保持一致
        const getSortedIndex = (currentPeriod: number, allPeriods: number[]) => {
            if (indicator === 'BOLL') {
                // BOLL使用字符串字典序排序 (boll_100 < boll_20)
                const sorted = [...allPeriods].sort((a, b) => {
                    return `boll_${a}`.localeCompare(`boll_${b}`);
                });
                return sorted.indexOf(currentPeriod);
            } else {
                // 其他指标使用数值排序 (20 < 100)
                const sorted = [...allPeriods].sort((a, b) => a - b);
                return sorted.indexOf(currentPeriod);
            }
        };

        const result = indicatorConfig.periods.map((period, index) => {
            // 确定激活状态：
            // 1. 如果有visiblePeriods配置，则根据是否包含在其中判断
            // 2. 如果没有visiblePeriods配置，则默认激活（或者根据指标是否选中）
            let isActive = true;
            if (indicatorConfig.visiblePeriods) {
                isActive = indicatorConfig.visiblePeriods.includes(period);
            } else {
                // 如果没有显式配置可见性，默认跟随指标选中状态
                isActive = isIndicatorSelected;
            }

            // 使用排序后的索引来确定颜色，确保与K线图一致
            const sortedIndex = getSortedIndex(period, indicatorConfig.periods!);
            const color = colorSequence[sortedIndex % colorSequence.length];

            return {
                period,
                color,
                isActive
            };
        });

        console.log(`[IndicatorDropdownPanel] ${indicator} 生成的周期列表:`, result);

        return result;
    };

    const [periods, setPeriods] = useState<PeriodItem[]>(getCurrentPeriods());

    // 状态同步：确保本地状态与父组件状态保持一致
    useEffect(() => {
        const expectedPeriods = getCurrentPeriods();

        // 比较周期数组、颜色数组和激活状态，检测实际变化
        const hasChanged =
            periods.length !== expectedPeriods.length ||
            periods.some((p, index) =>
                p.period !== expectedPeriods[index]?.period ||
                p.color !== expectedPeriods[index]?.color ||
                p.isActive !== expectedPeriods[index]?.isActive
            );

        if (hasChanged) {
            console.log(`[IndicatorDropdownPanel] ${indicator} 配置已更新，同步本地状态`, {
                旧周期: periods,
                新周期: expectedPeriods
            });
            setPeriods(expectedPeriods);
        }
    }, [indicatorConfig.periods, indicatorConfig.periodColors, indicatorConfig.visiblePeriods]);

    // 添加新周期
    const handleAddPeriod = () => {
        // 周期上限验证：最大不能超过60
        const MAX_PERIOD = 60;
        if (newPeriod < config.minPeriod || newPeriod > MAX_PERIOD) {
            message.warning(`技术指标周期必须在 ${config.minPeriod} 到 ${MAX_PERIOD} 之间`);
            return;
        }

        // 检查是否已存在
        if (periods.some(p => p.period === newPeriod)) {
            message.warning('该周期已存在');
            return;
        }

        const newPeriodItem: PeriodItem = {
            period: newPeriod,
            color: colorSequence[periods.length % colorSequence.length],
            isActive: true,
            stdDev: indicator === 'BOLL' ? 2.0 : undefined // BOLL默认标准差2.0
        };

        const updatedPeriods = [...periods, newPeriodItem];
        setPeriods(updatedPeriods);

        // 构建新的配置对象
        const newConfig = {
            periods: updatedPeriods.map(p => p.period),
            periodColors: updatedPeriods.map(p => p.color),
            visiblePeriods: updatedPeriods.filter(p => p.isActive).map(p => p.period),
            stdDevs: updatedPeriods.map(p => p.stdDev !== undefined ? p.stdDev : 2.0) // BOLL专用
        };

        // 立即更新配置并强制触发重新渲染
        console.log(`[IndicatorDropdownPanel] 添加周期 ${indicator}:`, newConfig);

        // 检查指标是否已激活，如果未激活则先激活
        const isSelected = selectedIndicators.includes(indicator);
        if (!isSelected && onActivateIndicator) {
            // 先激活指标
            console.log(`[IndicatorDropdownPanel] 添加第一个周期，激活指标 ${indicator}`);
            try {
                onActivateIndicator(indicator);
            } catch (error) {
                console.error(`[IndicatorDropdownPanel] 激活指标失败 ${indicator}:`, error);
            }
        }

        // 触发配置更新
        try {
            onUpdateConfig(indicator, newConfig);
            console.log(`[IndicatorDropdownPanel] 配置更新已触发 ${indicator}:`, newConfig);
        } catch (error) {
            console.error(`[IndicatorDropdownPanel] 配置更新失败 ${indicator}:`, error);
        }

        setNewPeriod(config.defaultPeriod);
        setShowNewPeriodInput(false);
    };

    // 删除周期
    const handleDeletePeriod = (periodToDelete: number) => {
        const updatedPeriods = periods.filter(p => p.period !== periodToDelete);
        setPeriods(updatedPeriods);

        if (updatedPeriods.length > 0) {
            const newConfig = {
                periods: updatedPeriods.map(p => p.period),
                periodColors: updatedPeriods.map(p => p.color),
                visiblePeriods: updatedPeriods.filter(p => p.isActive).map(p => p.period),
                stdDevs: updatedPeriods.map(p => p.stdDev !== undefined ? p.stdDev : 2.0) // BOLL专用
            };
            console.log(`[IndicatorDropdownPanel] 删除周期 ${indicator}:`, newConfig);

            // 强制触发配置更新
            try {
                onUpdateConfig(indicator, newConfig);

                // 添加延迟确保状态更新完成
                setTimeout(() => {
                    console.log(`[IndicatorDropdownPanel] 删除周期状态更新完成 ${indicator}`);
                }, 10);
            } catch (error) {
                console.error(`[IndicatorDropdownPanel] 删除周期失败 ${indicator}:`, error);
            }
        } else {
            console.log(`[IndicatorDropdownPanel] 删除周期 ${indicator}: 没有剩余周期，完全清理配置`);

            // 所有周期都被删除时，清理所有配置
            const cleanConfig = {
                periods: [],
                periodColors: [],
                visiblePeriods: [],
                period: undefined,
                stdDev: undefined,
                fastPeriod: undefined,
                slowPeriod: undefined,
                signalPeriod: undefined
            };
            console.log(`[IndicatorDropdownPanel] 完全清理配置 ${indicator}:`, cleanConfig);

            try {
                onUpdateConfig(indicator, cleanConfig);
            } catch (error) {
                console.error(`[IndicatorDropdownPanel] 清空周期失败 ${indicator}:`, error);
            }
        }
    };

    // 切换周期激活状态
    const handleTogglePeriod = (period: number, isActive: boolean) => {
        const updatedPeriods = periods.map(p =>
            p.period === period ? {...p, isActive} : p
        );
        setPeriods(updatedPeriods);

        // 即使没有激活的周期，也应该保留配置（只是visiblePeriods为空）
        // 只有当periods本身为空时才清理配置（那是handleDeletePeriod的职责）
        const newConfig = {
            periods: updatedPeriods.map(p => p.period),
            periodColors: updatedPeriods.map(p => p.color),
            visiblePeriods: updatedPeriods.filter(p => p.isActive).map(p => p.period),
            stdDevs: updatedPeriods.map(p => p.stdDev !== undefined ? p.stdDev : 2.0) // BOLL专用
        };
        console.log(`[IndicatorDropdownPanel] 切换周期状态 ${indicator}:`, newConfig);

        // 强制触发配置更新
        try {
            onUpdateConfig(indicator, newConfig);

            // 添加延迟确保状态更新完成
            setTimeout(() => {
                console.log(`[IndicatorDropdownPanel] 切换周期状态更新完成 ${indicator}`);
            }, 10);
        } catch (error) {
            console.error(`[IndicatorDropdownPanel] 切换周期状态失败 ${indicator}:`, error);
        }
    };

    // 更新BOLL周期的标准差值
    const handleUpdateStdDev = (period: number, newStdDev: number) => {
        const updatedPeriods = periods.map(p =>
            p.period === period ? {...p, stdDev: newStdDev} : p
        );
        setPeriods(updatedPeriods);

        // 构建新的配置对象
        const newConfig = {
            periods: updatedPeriods.map(p => p.period),
            periodColors: updatedPeriods.map(p => p.color),
            visiblePeriods: updatedPeriods.filter(p => p.isActive).map(p => p.period),
            stdDevs: updatedPeriods.map(p => p.stdDev !== undefined ? p.stdDev : 2.0) // BOLL专用
        };
        console.log(`[IndicatorDropdownPanel] 更新标准差 ${indicator}:`, newConfig);

        try {
            onUpdateConfig(indicator, newConfig);
        } catch (error) {
            console.error(`[IndicatorDropdownPanel] 更新标准差失败 ${indicator}:`, error);
        }
    };

    if (!isVisible) {
        return null;
    }

    return (
        <PortalContainer>
            <Card
                ref={panelRef}
                className={className}
                style={{
                    position: 'fixed',
                    top: position.top,
                    left: position.left,
                    zIndex: 9999,
                    width: '280px',
                    maxHeight: '400px',
                    overflow: 'auto',
                    backgroundColor: '#2a2a2a',
                    border: '1px solid #404040',
                    boxShadow: '0 8px 24px rgba(0, 0, 0, 0.4)',
                    borderRadius: '8px',
                    ...style
                }}
                size="small"
            >
                <div style={{padding: '8px'}}>
                    {/* 标题和关闭按钮 */}
                    <div style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: '12px'
                    }}>
                        <Space>
                            <span style={{fontSize: '14px', color: '#fff'}}>{config.icon}</span>
                            <Text strong style={{color: '#fff', fontSize: '12px'}}>
                                {config.name} 配置
                            </Text>
                        </Space>
                        <Button
                            type="text"
                            size="small"
                            icon={<CloseOutlined/>}
                            onClick={onClose}
                            style={{color: '#999'}}
                        />
                    </div>

                    {/* 周期列表 */}
                    <div style={{marginBottom: '12px'}}>
                        <Text style={{color: '#ccc', fontSize: '11px', marginBottom: '8px', display: 'block'}}>
                            周期配置 (最多7个)
                        </Text>
                        <div style={{maxHeight: '150px', overflowY: 'auto'}}>
                            {periods.map((periodItem) => (
                                <div
                                    key={periodItem.period}
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        padding: '4px 0',
                                        borderBottom: '1px solid #404040'
                                    }}
                                >
                                    <Space size="small">
                                        <Checkbox
                                            checked={periodItem.isActive}
                                            onChange={(e) => handleTogglePeriod(periodItem.period, e.target.checked)}
                                        />
                                        <Tag
                                            color={periodItem.isActive ? periodItem.color : 'default'}
                                            style={{
                                                margin: 0,
                                                opacity: periodItem.isActive ? 1 : 0.5
                                            }}
                                        >
                                            {config.name}({periodItem.period})
                                        </Tag>
                                        {indicator === 'BOLL' && (
                                            <InputNumber
                                                size="small"
                                                min={0.1}
                                                max={5.0}
                                                step={0.1}
                                                value={periodItem.stdDev || 2.0}
                                                onChange={(value) => handleUpdateStdDev(periodItem.period, value || 2.0)}
                                                placeholder="标准差"
                                                style={{
                                                    width: '70px',
                                                    fontSize: '11px'
                                                }}
                                            />
                                        )}
                                    </Space>
                                    <Button
                                        type="text"
                                        size="small"
                                        icon={<DeleteOutlined/>}
                                        onClick={() => handleDeletePeriod(periodItem.period)}
                                        style={{
                                            color: '#ff4d4f',
                                            fontSize: '10px',
                                            padding: '0 4px'
                                        }}
                                    />
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* 添加新周期 */}
                    <div style={{marginBottom: '12px'}}>
                        {showNewPeriodInput ? (
                            <Row gutter={8} align="middle">
                                <Col flex={1}>
                                    <InputNumber
                                        size="small"
                                        min={config.minPeriod}
                                        max={config.maxPeriod}
                                        value={newPeriod}
                                        onChange={(value) => setNewPeriod(value || config.defaultPeriod)}
                                        placeholder={`周期 (${config.minPeriod}-${config.maxPeriod})`}
                                        style={{width: '100%'}}
                                    />
                                </Col>
                                <Col>
                                    <Button
                                        type="primary"
                                        size="small"
                                        icon={<CheckOutlined/>}
                                        onClick={handleAddPeriod}
                                    />
                                </Col>
                                <Col>
                                    <Button
                                        size="small"
                                        icon={<CloseOutlined/>}
                                        onClick={() => {
                                            setShowNewPeriodInput(false);
                                            setNewPeriod(config.defaultPeriod);
                                        }}
                                    />
                                </Col>
                            </Row>
                        ) : (
                            <Button
                                type="dashed"
                                size="small"
                                icon={<PlusOutlined/>}
                                onClick={() => setShowNewPeriodInput(true)}
                                disabled={periods.length >= 7}
                                style={{width: '100%'}}
                            >
                                添加周期 {periods.length >= 7 && '(已达上限)'}
                            </Button>
                        )}
                    </div>

                    {/* 快捷预设 */}
                    <Divider style={{margin: '8px 0', borderColor: '#404040'}}/>
                    <Space size="small" wrap>
                        <Button
                            size="small"
                            onClick={() => {
                                const presetPeriods = indicator === 'RSI' ? [14, 21, 28] : [5, 10, 20];
                                const presetItems = presetPeriods.map((period, index) => ({
                                    period,
                                    color: colorSequence[index % colorSequence.length],
                                    isActive: true
                                }));
                                setPeriods(presetItems);
                                onUpdateConfig(indicator, {
                                    periods: presetItems.map(p => p.period),
                                    periodColors: presetItems.map(p => p.color)
                                });
                            }}
                        >
                            短期组合
                        </Button>
                        <Button
                            size="small"
                            onClick={() => {
                                const presetPeriods = indicator === 'RSI' ? [14, 28] : [20, 30, 50];
                                const presetItems = presetPeriods.map((period, index) => ({
                                    period,
                                    color: colorSequence[(index + 3) % colorSequence.length],
                                    isActive: true
                                }));
                                setPeriods(presetItems);
                                onUpdateConfig(indicator, {
                                    periods: presetItems.map(p => p.period),
                                    periodColors: presetItems.map(p => p.color)
                                });
                            }}
                        >
                            中期组合
                        </Button>
                    </Space>
                </div>
            </Card>
        </PortalContainer>
    );
};

export default IndicatorDropdownPanel;