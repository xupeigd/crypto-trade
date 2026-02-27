import React from 'react';
import {Button, Space, Typography} from 'antd';
import {
    BulbOutlined,
    DownloadOutlined,
    EyeOutlined,
    FullscreenOutlined,
    ReloadOutlined,
    SettingOutlined,
    ShareAltOutlined,
    SwapOutlined,
    ThunderboltOutlined
} from '@ant-design/icons';
import './ChartModalEnhanced.css';

const {Text} = Typography;

interface RightQuickPanelProps {
    onQuickBuy?: () => void;
    onQuickSell?: () => void;
    onFullscreen?: () => void;
    onRefresh?: () => void;
    onSettings?: () => void;
    onExport?: () => void;
    onShare?: () => void;
    onToggleTheme?: () => void;
    currentPrice?: number;
    priceChange?: number;
}

const RightQuickPanel: React.FC<RightQuickPanelProps> = ({
                                                             onQuickBuy,
                                                             onQuickSell,
                                                             onFullscreen,
                                                             onRefresh,
                                                             onSettings,
                                                             onExport,
                                                             onShare,
                                                             onToggleTheme,
                                                             currentPrice,
                                                             priceChange
                                                         }) => {
    const quickStrategies = [
        {
            name: '趋势突破',
            description: '价格突破关键阻力位时买入',
            action: 'buy',
            icon: '🚀'
        },
        {
            name: '回调买入',
            description: '价格回调至支撑位时买入',
            action: 'buy',
            icon: '📈'
        },
        {
            name: '反弹卖出',
            description: '价格反弹至阻力位时卖出',
            action: 'sell',
            icon: '📉'
        },
        {
            name: '止损平仓',
            description: '快速止损退出当前仓位',
            action: 'sell',
            icon: '⛔'
        }
    ];

    return (
        <div className="right-panel">
            {/* 快速交易 */}
            <div className="left-panel-section">
                <div className="left-panel-title">
                    <ThunderboltOutlined/>
                    <span>快速交易</span>
                </div>
                <Space direction="vertical" style={{width: '100%'}} size="small">
                    <Button
                        className="quick-action-button primary"
                        icon={<SwapOutlined rotate={90}/>}
                        onClick={onQuickBuy}
                        style={{
                            height: '40px',
                            fontSize: '14px',
                            fontWeight: 'bold'
                        }}
                    >
                        开多 {currentPrice ? `$${currentPrice.toFixed(2)}` : ''}
                    </Button>
                    <Button
                        className="quick-action-button danger"
                        icon={<SwapOutlined rotate={-90}/>}
                        onClick={onQuickSell}
                        style={{
                            height: '40px',
                            fontSize: '14px',
                            fontWeight: 'bold'
                        }}
                    >
                        开空 {currentPrice ? `$${currentPrice.toFixed(2)}` : ''}
                    </Button>
                </Space>
            </div>

            {/* 快速策略 */}
            <div className="left-panel-section">
                <div className="left-panel-title">
                    <BulbOutlined/>
                    <span>快速策略</span>
                </div>
                <Space direction="vertical" style={{width: '100%'}} size="small">
                    {quickStrategies.map((strategy, index) => (
                        <Button
                            key={index}
                            className="quick-action-button"
                            onClick={() => {
                                if (strategy.action === 'buy' && onQuickBuy) {
                                    onQuickBuy();
                                } else if (strategy.action === 'sell' && onQuickSell) {
                                    onQuickSell();
                                }
                            }}
                            style={{
                                height: 'auto',
                                padding: '8px 12px',
                                textAlign: 'left'
                            }}
                        >
                            <Space direction="vertical" size="small" style={{width: '100%'}}>
                                <Space>
                                    <span style={{fontSize: '14px'}}>{strategy.icon}</span>
                                    <Text style={{color: '#ffffff', fontSize: '12px', fontWeight: 500}}>
                                        {strategy.name}
                                    </Text>
                                </Space>
                                <Text style={{color: '#888888', fontSize: '10px'}}>
                                    {strategy.description}
                                </Text>
                            </Space>
                        </Button>
                    ))}
                </Space>
            </div>

            <div className="left-panel-section">
                <div className="left-panel-title">
                    <SettingOutlined/>
                    <span>工具选项</span>
                </div>
                <Space direction="vertical" style={{width: '100%'}} size="small">
                    <Button
                        className="quick-action-button"
                        icon={<FullscreenOutlined/>}
                        onClick={onFullscreen}
                        size="small"
                    >
                        全屏显示
                    </Button>
                    <Button
                        className="quick-action-button"
                        icon={<ReloadOutlined/>}
                        onClick={onRefresh}
                        size="small"
                    >
                        刷新数据
                    </Button>
                    <Button
                        className="quick-action-button"
                        icon={<EyeOutlined/>}
                        onClick={onToggleTheme}
                        size="small"
                    >
                        切换主题
                    </Button>
                    <Button
                        className="quick-action-button"
                        icon={<DownloadOutlined/>}
                        onClick={onExport}
                        size="small"
                    >
                        导出图表
                    </Button>
                    <Button
                        className="quick-action-button"
                        icon={<ShareAltOutlined/>}
                        onClick={onShare}
                        size="small"
                    >
                        分享分析
                    </Button>
                </Space>
            </div>

            {/* 当前状态信息 */}
            <div className="left-panel-section">
                <div className="left-panel-title">
                    <EyeOutlined/>
                    <span>当前状态</span>
                </div>
                <Space direction="vertical" style={{width: '100%'}} size="small">
                    <div className="realtime-info-item">
                        <div className="info-label">价格状态</div>
                        <div className={`info-value ${priceChange && priceChange >= 0 ? 'positive' : 'negative'}`}>
                            {priceChange && priceChange >= 0 ? '📈 上涨' : '📉 下跌'}
                            {priceChange && (
                                <span style={{marginLeft: '4px'}}>
                                    {Math.abs(priceChange).toFixed(2)}%
                                </span>
                            )}
                        </div>
                    </div>
                    <div className="realtime-info-item">
                        <div className="info-label">市场状态</div>
                        <div className="info-value" style={{color: '#1890ff'}}>
                            🔵 交易中
                        </div>
                    </div>
                    <div className="realtime-info-item">
                        <div className="info-label">更新时间</div>
                        <div className="info-value" style={{fontSize: '11px'}}>
                            {new Date().toLocaleTimeString()}
                        </div>
                    </div>
                </Space>
            </div>
        </div>
    );
};

export default RightQuickPanel;