import React from 'react';
import {Space, Typography} from 'antd';
import {BulbOutlined, DashboardOutlined, SettingOutlined} from '@ant-design/icons';
import './ChartModalEnhanced.css';

const {Text} = Typography;

interface LeftInfoPanelProps {
    selectedIndicators: string[];
    indicatorConfigs: Record<string, any>;
    onToggleIndicator?: (indicator: string) => void;
    realtimeData?: {
        price: number;
        change24h: number;
        volume: number;
        high24h: number;
        low24h: number;
    };
}

const LeftInfoPanel: React.FC<LeftInfoPanelProps> = ({
                                                         selectedIndicators,
                                                         indicatorConfigs,
                                                         onToggleIndicator,
                                                         realtimeData
                                                     }) => {
    const indicators = [
        {key: 'EMA', name: '指数移动平均', icon: '📈', color: '#1890ff'},
        {key: 'SMA', name: '简单移动平均', icon: '📊', color: '#52c41a'},
        {key: 'WMA', name: '加权移动平均', icon: '📉', color: '#722ed1'},
        {key: 'BOLL', name: '布林带', icon: '🎯', color: '#fa8c16'},
        {key: 'RSI', name: '相对强弱指数', icon: '⚡', color: '#13c2c2'},
        {key: 'MACD', name: 'MACD', icon: '🔄', color: '#eb2f96'}
    ];

    return (
        <div className="left-panel">
            {/* 技术指标部分 */}
            <div className="left-panel-section">
                <div className="left-panel-title">
                    <SettingOutlined/>
                    <span>技术指标</span>
                </div>
                <Space direction="vertical" style={{width: '100%'}} size="small">
                    {indicators.map((indicator) => {
                        const isActive = selectedIndicators.includes(indicator.key);
                        const config = indicatorConfigs[indicator.key];
                        const periods = config?.periods || [];

                        return (
                            <div
                                key={indicator.key}
                                className={`indicator-item ${isActive ? 'active' : ''}`}
                                onClick={() => onToggleIndicator?.(indicator.key)}
                            >
                                <Space style={{width: '100%', justifyContent: 'space-between'}}>
                                    <Space>
                                        <span style={{fontSize: '14px'}}>{indicator.icon}</span>
                                        <Text style={{color: isActive ? '#ffffff' : '#888888', fontSize: '12px'}}>
                                            {indicator.name}
                                        </Text>
                                    </Space>
                                    {isActive && periods.length > 0 && (
                                        <Space size="small">
                                            {periods.slice(0, 2).map((period: number, index: number) => (
                                                <span
                                                    key={index}
                                                    style={{
                                                        backgroundColor: indicator.color,
                                                        color: '#ffffff',
                                                        fontSize: '10px',
                                                        padding: '2px 6px',
                                                        borderRadius: '4px'
                                                    }}
                                                >
                                                    {period}
                                                </span>
                                            ))}
                                            {periods.length > 2 && (
                                                <span style={{color: '#888888', fontSize: '10px'}}>
                                                    +{periods.length - 2}
                                                </span>
                                            )}
                                        </Space>
                                    )}
                                </Space>
                            </div>
                        );
                    })}
                </Space>
            </div>

            {/* 实时市场数据 */}
            {realtimeData && (
                <div className="left-panel-section">
                    <div className="left-panel-title">
                        <DashboardOutlined/>
                        <span>实时数据</span>
                    </div>
                    <Space direction="vertical" style={{width: '100%'}} size="small">
                        <div className="realtime-info-item">
                            <div className="info-label">当前价格</div>
                            <div className="info-value">
                                ${realtimeData.price.toFixed(2)}
                            </div>
                        </div>
                        <div className="realtime-info-item">
                            <div className="info-label">24h变化</div>
                            <div className={`info-value ${realtimeData.change24h >= 0 ? 'positive' : 'negative'}`}>
                                {realtimeData.change24h >= 0 ? '+' : ''}{realtimeData.change24h.toFixed(2)}%
                            </div>
                        </div>
                        <div className="realtime-info-item">
                            <div className="info-label">24h成交量</div>
                            <div className="info-value">
                                {(realtimeData.volume / 1000000).toFixed(2)}M
                            </div>
                        </div>
                        <div className="realtime-info-item">
                            <div className="info-label">24h最高</div>
                            <div className="info-value">
                                ${realtimeData.high24h.toFixed(2)}
                            </div>
                        </div>
                        <div className="realtime-info-item">
                            <div className="info-label">24h最低</div>
                            <div className="info-value">
                                ${realtimeData.low24h.toFixed(2)}
                            </div>
                        </div>
                    </Space>
                </div>
            )}

            {/* 快速提示 */}
            <div className="left-panel-section">
                <div className="left-panel-title">
                    <BulbOutlined/>
                    <span>快速提示</span>
                </div>
                <Space direction="vertical" style={{width: '100%'}} size="small">
                    <div className="realtime-info-item">
                        <Text style={{color: '#b0b0b0', fontSize: '11px', lineHeight: '1.4'}}>
                            💡 点击指标名称可以快速启用/禁用技术指标
                        </Text>
                    </div>
                    <div className="realtime-info-item">
                        <Text style={{color: '#b0b0b0', fontSize: '11px', lineHeight: '1.4'}}>
                            📊 支持同时显示多个周期的技术指标进行对比分析
                        </Text>
                    </div>
                    <div className="realtime-info-item">
                        <Text style={{color: '#b0b0b0', fontSize: '11px', lineHeight: '1.4'}}>
                            🎯 使用鼠标滚轮可以快速缩放K线图时间范围
                        </Text>
                    </div>
                </Space>
            </div>
        </div>
    );
};

export default LeftInfoPanel;