import React, {useRef, useState} from 'react';
import {Button, Card, Divider, Space} from 'antd';
import {AppstoreOutlined, DashboardOutlined} from '@ant-design/icons';
import {IndicatorConfig, TechnicalIndicator} from '../../../hooks/useChartState';
import IndicatorButton from './IndicatorButton';
import IndicatorDropdownPanel from './IndicatorDropdownPanel';
import PresetStrategyPanel from './PresetStrategyPanel';
import {IndicatorPreset} from '../../../config/technicalIndicatorsPresets';

interface TechnicalIndicatorsConfigPanelProps {
    selectedIndicators: TechnicalIndicator[];
    indicatorConfigs: Record<TechnicalIndicator, IndicatorConfig>;
    onToggleIndicator: (indicator: TechnicalIndicator) => void;
    onUpdateIndicatorConfig: (indicator: TechnicalIndicator, config: Partial<IndicatorConfig>) => void;
    onActivateIndicator?: (indicator: TechnicalIndicator) => void; // 新增：激活指标函数
    onClearAllIndicators?: () => void; // 新增：清除所有指标函数
    className?: string;
    style?: React.CSSProperties;
}

/**
 * 按钮式技术指标配置面板
 * 提供简洁的按钮式指标选择和向下弹出配置功能
 */
const TechnicalIndicatorsConfigPanel: React.FC<TechnicalIndicatorsConfigPanelProps> = ({
                                                                                           selectedIndicators,
                                                                                           indicatorConfigs,
                                                                                           onToggleIndicator,
                                                                                           onUpdateIndicatorConfig,
                                                                                           onActivateIndicator,
                                                                                           onClearAllIndicators,
                                                                                           className,
                                                                                           style
                                                                                       }) => {
    const [activeIndicator, setActiveIndicator] = useState<TechnicalIndicator | null>(null);
    const [dropdownOpen, setDropdownOpen] = useState<boolean>(false);
    const [presetsVisible, setPresetsVisible] = useState<boolean>(false);
    const buttonRefs = useRef<Record<TechnicalIndicator, HTMLButtonElement | null>>({} as Record<TechnicalIndicator, HTMLButtonElement | null>);
    const presetsButtonRef = useRef<HTMLButtonElement>(null);

    // 指标特定颜色映射 - 与CandlestickChart和IndicatorDropdownPanel保持一致
    const indicatorColorMap: Record<string, string[]> = {
        EMA: ['#1890ff', '#00d2d3', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#13c2c2'],
        SMA: ['#eb2f96', '#fa541c', '#fadb14', '#52c41a', '#1890ff', '#722ed1', '#13c2c2'],
        WMA: ['#a0d911', '#faad14', '#fa8c16', '#f5222d', '#cf1322', '#722ed1', '#2f54eb'],
        RSI: ['#9254de', '#13c2c2', '#52c41a', '#fa8c16', '#f5222d', '#722ed1', '#1890ff'],
        BOLL: ['#ff4d4f', '#52c41a', '#1890ff', '#fa8c16', '#722ed1', '#faad14', '#13c2c2'],
        MACD: ['#fa541c', '#1890ff', '#52c41a', '#fadb14', '#f5222d', '#722ed1', '#13c2c2'],
        KDJ: ['#13c2c2', '#9254de', '#fa8c16', '#52c41a', '#f5222d', '#1890ff', '#722ed1'],
        CCI: ['#eb2f96', '#fa541c', '#fadb14', '#52c41a', '#1890ff', '#722ed1', '#13c2c2'],
        ATR: ['#a0d911', '#faad14', '#fa8c16', '#f5222d', '#cf1322', '#722ed1', '#2f54eb'],
        OBV: ['#52c41a', '#1890ff', '#fa8c16', '#f5222d', '#722ed1', '#faad14', '#13c2c2'],
        ADX: ['#9254de', '#13c2c2', '#fa8c16', '#f5222d', '#1890ff', '#722ed1', '#faad14']
    };

    // 技术指标配置信息
    const indicatorConfigsInfo = {
        EMA: {
            name: 'EMA',
            fullName: 'Exponential Moving Average',
            description: '指数移动平均线，对近期价格给予更高权重',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#1890ff'
        },
        SMA: {
            name: 'SMA',
            fullName: 'Simple Moving Average',
            description: '简单移动平均线，计算指定周期内的平均价格',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#ff9800'
        },
        WMA: {
            name: 'WMA',
            fullName: 'Weighted Moving Average',
            description: '加权移动平均线，按时间权重递减计算平均价格',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#722ed1'
        },
        BOLL: {
            name: 'BOLL',
            fullName: 'Bollinger Bands',
            description: '布林带，包含上轨、中轨、下轨的价格通道指标',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#52c41a'
        },
        RSI: {
            name: 'RSI',
            fullName: 'Relative Strength Index',
            description: '相对强弱指数，测量价格变动的速度和幅度',
            defaultPeriod: 14,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#9254de'
        },
        MACD: {
            name: 'MACD',
            fullName: 'Moving Average Convergence Divergence',
            description: '移动平均收敛发散指标，识别趋势变化',
            defaultPeriod: 12,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#fa541c'
        },
        KDJ: {
            name: 'KDJ',
            fullName: 'Stochastic Oscillator',
            description: '随机指标，判断超买超卖状态',
            defaultPeriod: 9,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#13c2c2'
        },
        CCI: {
            name: 'CCI',
            fullName: 'Commodity Channel Index',
            description: '商品通道指数，识别周期性趋势',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#eb2f96'
        },
        ATR: {
            name: 'ATR',
            fullName: 'Average True Range',
            description: '平均真实波幅，衡量市场波动性',
            defaultPeriod: 14,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#a0d911'
        },
        OBV: {
            name: 'OBV',
            fullName: 'On Balance Volume',
            description: '能量潮，衡量资金流向',
            defaultPeriod: 20,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#52c41a'
        },
        ADX: {
            name: 'ADX',
            fullName: 'Average Directional Index',
            description: '平均方向指数，衡量趋势强度',
            defaultPeriod: 14,
            minPeriod: 1,
            maxPeriod: 60,
            color: '#9254de'
        }
    };

    // 处理指标按钮点击
    const handleIndicatorClick = (indicator: TechnicalIndicator) => {
        const wasActive = selectedIndicators.includes(indicator);

        if (!wasActive) {
            // 如果之前未激活，只打开配置面板，不激活指标
            setActiveIndicator(indicator);
            setDropdownOpen(true);
        } else {
            // 如果之前已激活，切换配置面板状态
            if (activeIndicator === indicator) {
                setDropdownOpen(!dropdownOpen);
                if (!dropdownOpen) {
                    setActiveIndicator(null);
                }
            } else {
                setActiveIndicator(indicator);
                setDropdownOpen(true);
            }
        }
    };

    // 处理配置面板关闭
    const handleDropdownClose = () => {
        setDropdownOpen(false);
        // 如果指标没有被选中，则将其标记为未激活状态
        if (activeIndicator && !selectedIndicators.includes(activeIndicator)) {
            setActiveIndicator(null);
        }
    };

    // 应用快捷配置
    const applyQuickConfig = (indicators: TechnicalIndicator[]) => {
        indicators.forEach(indicator => {
            if (!selectedIndicators.includes(indicator)) {
                onToggleIndicator(indicator);
                // 设置默认的多周期配置
                const config = indicatorConfigsInfo[indicator];
                const colors = indicatorColorMap[indicator] || ['#1890ff', '#00d2d3', '#52c41a'];
                
                if (indicator === 'RSI') {
                    onUpdateIndicatorConfig(indicator, {
                        periods: [7, 14, 21],
                        periodColors: [colors[0], colors[1], colors[2]]
                    });
                } else if (['EMA', 'SMA', 'WMA'].includes(indicator)) {
                    onUpdateIndicatorConfig(indicator, {
                        periods: [5, 10, 20],
                        periodColors: [colors[0], colors[1], colors[2]]
                    });
                } else if (indicator === 'BOLL') {
                    onUpdateIndicatorConfig(indicator, {
                        periods: [20, 30, 50],
                        periodColors: [colors[0], colors[1], colors[2]],
                        stdDev: 2
                    });
                } else if (indicator === 'KDJ') {
                    onUpdateIndicatorConfig(indicator, {
                        periods: [9, 14, 21],
                        periodColors: [colors[0], colors[1], colors[2]]
                    });
                } else if (['CCI', 'ATR', 'OBV', 'ADX'].includes(indicator)) {
                    onUpdateIndicatorConfig(indicator, {
                        periods: [14, 20, 30],
                        periodColors: [colors[0], colors[1], colors[2]]
                    });
                }
            }
        });
    };

    // 应用预设策略
    const applyPreset = (preset: IndicatorPreset) => {
        // console.log('[TechnicalIndicatorsConfigPanel] 应用预设策略:', preset.name, {
        //     指标列表: preset.indicators,
        //     配置详情: preset.configs
        // });

        // 清空当前配置
        selectedIndicators.forEach(indicator => {
            onToggleIndicator(indicator);
        });
        setActiveIndicator(null);
        setDropdownOpen(false);

        // 直接同步应用新的配置，避免状态不同步
        preset.indicators.forEach(indicator => {
            onToggleIndicator(indicator);

            // 立即更新配置
            const config = preset.configs[indicator];
            if (config) {
                // console.log(`[TechnicalIndicatorsConfigPanel] 应用 ${indicator} 配置:`, {
                //     原始配置: config,
                //     包含periods: !!config.periods,
                //     包含period: !!config.period,
                //     periods数量: config.periods?.length
                // });
                onUpdateIndicatorConfig(indicator, config);
            } else {
                // console.warn(`[TechnicalIndicatorsConfigPanel] 指标 ${indicator} 没有配置数据`);
            }
        });
    };

    // 清除所有配置
    const clearAllConfig = () => {
        // console.log('[TechnicalIndicatorsConfigPanel] 清除所有配置');
        // 使用新的清除方法，同时清理选中状态和配置数据
        if (onClearAllIndicators) {
            onClearAllIndicators();
        } else {
            // 兼容性：如果没有传递onClearAllIndicators，则使用原有逻辑
            selectedIndicators.forEach(indicator => {
                onToggleIndicator(indicator);
            });
        }

        // 清理本地状态
        setActiveIndicator(null);
        setDropdownOpen(false);
        setPresetsVisible(false);
    };

    return (
        <>
            <Card
                size="small"
                className={className}
                style={{
                    backgroundColor: '#1f1f1f',
                    border: '1px solid transparent',
                    ...style
                }}
                styles={{
                    body: {
                        padding: '4px'
                    }
                }}
            >
                <Space direction="vertical" style={{width: '100%'}} size={2}>
                    {/* 指标按钮行 */}
                    <div className="indicator-buttons-section">
                        <Space wrap size={2} style={{ alignItems: 'center' }}>
                            <span style={{
                                color: '#ffffff',
                                fontSize: '12px',
                                fontWeight: '500',
                                whiteSpace: 'nowrap',
                                display: 'inline-flex',
                                alignItems: 'center',
                                marginRight: '5px'
                            }}>指标: </span>
                            {Object.keys(indicatorConfigsInfo).map((indicator) => {
                                const indicatorKey = indicator as TechnicalIndicator;
                                const config = indicatorConfigsInfo[indicatorKey];
                                const isActive = selectedIndicators.includes(indicatorKey);

                                return (
                                    <IndicatorButton
                                        key={indicatorKey}
                                        ref={(el) => {
                                            buttonRefs.current[indicatorKey] = el;
                                        }}
                                        indicator={indicatorKey}
                                        isActive={isActive}
                                        color={config.color}
                                        name={config.name}
                                        description={config.description}
                                        onClick={() => handleIndicatorClick(indicatorKey)}
                                    />
                                );
                            })}
                        </Space>

                        {/* 弹出配置面板 */}
                        {activeIndicator && (
                            <IndicatorDropdownPanel
                                indicator={activeIndicator}
                                indicatorConfig={indicatorConfigs[activeIndicator]}
                                isVisible={dropdownOpen}
                                onClose={handleDropdownClose}
                                onUpdateConfig={onUpdateIndicatorConfig}
                                onActivateIndicator={onActivateIndicator}
                                selectedIndicators={selectedIndicators}
                                triggerElement={buttonRefs.current[activeIndicator]}
                            />
                        )}
                    </div>

                    <Divider style={{margin: '2px 0', borderColor: '#404040'}}/>

                    {/* 快捷操作 */}
                    <Space wrap size={2} style={{ alignItems: 'center' }}>
                        <span style={{
                            color: '#ffffff',
                            fontSize: '12px',
                            fontWeight: '500',
                            whiteSpace: 'nowrap',
                            display: 'inline-flex',
                            alignItems: 'center',
                            marginRight: '5px'
                        }}>分析: </span>
                        <Button
                            size="small"
                            icon={<DashboardOutlined/>}
                            onClick={() => applyQuickConfig(['EMA', 'RSI'])}
                        >
                            常用组合
                        </Button>
                        <Button
                            size="small"
                            onClick={() => applyQuickConfig(['EMA', 'SMA', 'BOLL'])}
                        >
                            趋势分析
                        </Button>
                        <Button
                            ref={presetsButtonRef}
                            size="small"
                            icon={<AppstoreOutlined/>}
                            onClick={() => setPresetsVisible(true)}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '4px'
                            }}
                        >
                            预设策略
                        </Button>
                        <Button
                            size="small"
                            onClick={clearAllConfig}
                        >
                            清除所有
                        </Button>
                    </Space>
                </Space>
            </Card>

            {/* 独立的预设策略面板 */}
            <PresetStrategyPanel
                isVisible={presetsVisible}
                onClose={() => setPresetsVisible(false)}
                onApplyPreset={applyPreset}
                triggerElement={presetsButtonRef.current}
            />
        </>
    );
};

export default TechnicalIndicatorsConfigPanel;