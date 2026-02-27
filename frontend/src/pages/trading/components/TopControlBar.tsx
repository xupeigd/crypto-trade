import React from 'react';
import {Card, Space, Typography} from 'antd';
import {ClockCircleOutlined} from '@ant-design/icons';
import {IndicatorConfig, TechnicalIndicator} from '../../../hooks/useChartState';
import TechnicalIndicatorsConfigPanel from './TechnicalIndicatorsConfigPanel';
import './ChartModalEnhanced.css';

// 时间周期配置
const TIME_PERIODS = [
    {label: '1m', value: '1m'},
    {label: '5m', value: '5m'},
    {label: '1h', value: '1h'},
    {label: '4h', value: '4h'},
    {label: '1d', value: '1d'}
];

const {Text} = Typography;

interface TopControlBarProps {
    timeFrame: string;
    onTimeFrameChange: (timeFrame: string) => void;
    selectedIndicators: TechnicalIndicator[];
    indicatorConfigs: Record<TechnicalIndicator, IndicatorConfig>;
    onToggleIndicator: (indicator: TechnicalIndicator) => void;
    onUpdateIndicatorConfig: (indicator: TechnicalIndicator, config: Partial<IndicatorConfig>) => void;
    onClearAllIndicators?: () => void; // 新增：清除所有指标函数
}

const TopControlBar: React.FC<TopControlBarProps> = ({
                                                         timeFrame,
                                                         onTimeFrameChange,
                                                         selectedIndicators,
                                                         indicatorConfigs,
                                                         onToggleIndicator,
                                                         onUpdateIndicatorConfig,
                                                         onClearAllIndicators
                                                     }) => {
    return (
        <Card
            className="top-bar"
            size="small"
            styles={{
                body: {
                    backgroundColor: 'rgba(26, 26, 26, 0.85)',
                    padding: '12px',
                    display: 'grid',
                    gridTemplateColumns: 'auto 1fr',
                    gap: '16px',
                    alignItems: 'center'
                }
            }}
        >
            {/* 时间帧控制区域 */}
            <div className="timeframe-section">
                <Space align="center">
                    <ClockCircleOutlined style={{color: '#b0b0b0', fontSize: '14px'}}/>
                    <Text className="time-label-enhanced">时间帧:</Text>
                    <Space size={4}>
                        {TIME_PERIODS.map((periodItem) => (
                            <button
                                key={periodItem.value}
                                className={`timeframe-btn ${timeFrame === periodItem.value ? 'active' : ''}`}
                                onClick={() => onTimeFrameChange(periodItem.value)}
                                style={{
                                    padding: '4px 8px',
                                    fontSize: '12px',
                                    borderRadius: '4px',
                                    border: '1px solid rgba(255, 255, 255, 0.2)',
                                    backgroundColor: timeFrame === periodItem.value
                                        ? 'rgba(24, 144, 255, 0.2)'
                                        : 'rgba(255, 255, 255, 0.05)',
                                    color: timeFrame === periodItem.value ? '#1890ff' : '#b0b0b0',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s ease',
                                    outline: 'none'
                                }}
                                onMouseEnter={(e) => {
                                    if (timeFrame !== periodItem.value) {
                                        e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.1)';
                                        e.currentTarget.style.color = '#ffffff';
                                    }
                                }}
                                onMouseLeave={(e) => {
                                    if (timeFrame !== periodItem.value) {
                                        e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.05)';
                                        e.currentTarget.style.color = '#b0b0b0';
                                    }
                                }}
                            >
                                {periodItem.label}
                            </button>
                        ))}
                    </Space>
                </Space>
            </div>

            {/* 技术分析区域 */}
            <div className="analysis-section">
                <TechnicalIndicatorsConfigPanel
                    selectedIndicators={selectedIndicators}
                    indicatorConfigs={indicatorConfigs}
                    onToggleIndicator={onToggleIndicator}
                    onUpdateIndicatorConfig={onUpdateIndicatorConfig}
                    onClearAllIndicators={onClearAllIndicators}
                    style={{margin: 0, border: 'none', backgroundColor: 'transparent'}}
                    className="modal-indicators-panel"
                />
            </div>
        </Card>
    );
};

export default TopControlBar;
