import React from 'react';
import {Button, Card, Space, Typography} from 'antd';
import {
    DownloadOutlined,
    EyeOutlined,
    FullscreenOutlined,
    ReloadOutlined,
    ShareAltOutlined,
    SwapOutlined
} from '@ant-design/icons';
import './ChartModalEnhanced.css';

const {Text} = Typography;

interface BottomTradingBarProps {
    onQuickBuy?: () => void;
    onQuickSell?: () => void;
    onFullscreen?: () => void;
    onRefresh?: () => void;
    onExport?: () => void;
    onShare?: () => void;
    onToggleTheme?: () => void;
    currentPrice?: number;
    priceChange?: number;
}

const BottomTradingBar: React.FC<BottomTradingBarProps> = ({
                                                               onQuickBuy,
                                                               onQuickSell,
                                                               onFullscreen,
                                                               onRefresh,
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
        }
    ];

    return (
        <Card
            className="trading-bar"
            size="small"
            styles={{
                body: {
                    backgroundColor: 'rgba(26, 26, 26, 0.85)',
                    padding: '12px',
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center'
                }
            }}
        >
            <div className="trading-section">
                <Space size="large" align="center">
                    {/* 快速买卖按钮 */}
                    <Space size="small">
                        <Button
                            className="quick-action-button primary"
                            icon={<SwapOutlined rotate={90}/>}
                            onClick={onQuickBuy}
                            style={{
                                height: '40px',
                                fontSize: '14px',
                                fontWeight: 'bold',
                                minWidth: '120px'
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
                                fontWeight: 'bold',
                                minWidth: '120px'
                            }}
                        >
                            开空 {currentPrice ? `$${currentPrice.toFixed(2)}` : ''}
                        </Button>
                    </Space>

                    {/* 快速策略 */}
                    <Space size="small">
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
                                    height: '36px',
                                    padding: '4px 8px'
                                }}
                                title={strategy.description}
                            >
                                <Space size="small">
                                    <span>{strategy.icon}</span>
                                    <Text style={{color: '#ffffff', fontSize: '12px'}}>
                                        {strategy.name}
                                    </Text>
                                </Space>
                            </Button>
                        ))}
                    </Space>

                    {/* 工具按钮 */}
                    <Space size="small">
                        <Button
                            className="quick-action-button"
                            icon={<FullscreenOutlined/>}
                            onClick={onFullscreen}
                            size="small"
                            title="全屏显示"
                        />
                        <Button
                            className="quick-action-button"
                            icon={<ReloadOutlined/>}
                            onClick={onRefresh}
                            size="small"
                            title="刷新数据"
                        />
                        <Button
                            className="quick-action-button"
                            icon={<EyeOutlined/>}
                            onClick={onToggleTheme}
                            size="small"
                            title="切换主题"
                        />
                        <Button
                            className="quick-action-button"
                            icon={<DownloadOutlined/>}
                            onClick={onExport}
                            size="small"
                            title="导出图表"
                        />
                        <Button
                            className="quick-action-button"
                            icon={<ShareAltOutlined/>}
                            onClick={onShare}
                            size="small"
                            title="分享分析"
                        />
                    </Space>

                    {/* 市场状态信息 */}
                    {priceChange !== undefined && (
                        <Space size="small">
                            <div className="realtime-info-item" style={{margin: 0}}>
                                <Text style={{color: '#888888', fontSize: '12px'}}>
                                    状态:
                                </Text>
                                <Text
                                    style={{
                                        color: priceChange >= 0 ? '#52c41a' : '#ff4d4f',
                                        fontSize: '12px',
                                        fontWeight: 'bold',
                                        marginLeft: '4px'
                                    }}
                                >
                                    {priceChange >= 0 ? '📈 上涨' : '📉 下跌'} {Math.abs(priceChange).toFixed(2)}%
                                </Text>
                            </div>
                        </Space>
                    )}
                </Space>
            </div>
        </Card>
    );
};

export default BottomTradingBar;