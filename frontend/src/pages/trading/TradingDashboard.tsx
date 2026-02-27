import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Alert, Button, Card, Col, Divider, Row, Space, Spin, Switch, Typography} from 'antd';
import {useNavigate} from 'react-router-dom';
import {SyncOutlined} from '@ant-design/icons';
import BalanceCards, {BalanceCardsRef} from '../../components/trading/BalanceCards';
import {OkxPositionService} from '../../services/okxPositionService';
import type {OkxPositionSummary} from '../../types/okxPosition';
import {usePageTimer} from '../../hooks/usePageTimer';

const {Title} = Typography;

const TradingDashboard: React.FC = () => {
    const navigate = useNavigate();
    const [positionSummary, setPositionSummary] = useState<OkxPositionSummary | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>('');
    const [selectedCex, setSelectedCex] = useState<string>('ALL'); // 添加vendor状态管理
    const [isRefreshing, setIsRefreshing] = useState(false);
    const balanceCardsRef = useRef<BalanceCardsRef>(null);

    // 获取持仓数据 - 使用新的OkxPositionService
    const fetchPositionData = async () => {
        setLoading(true);
        setError('');
        try {
            // 获取最新持仓数据
            const positions = await OkxPositionService.getLatestPositions({vendor: selectedCex});

            // 计算汇总统计
            const summary = OkxPositionService.calculatePositionSummary(positions);
            setPositionSummary(summary);
        } catch (error) {
            console.error('获取持仓数据失败:', error);
            setError('获取持仓数据失败');
        } finally {
            setLoading(false);
        }
    };

    // 使用页面级定时器Hook，保持10秒间隔
    const {isActive: isTimerActive, start: startTimer, stop: stopTimer} = usePageTimer(
        async () => {
            console.log('交易仪表板定时器触发，刷新持仓和余额数据');
            try {
                // 并行请求持仓和余额数据
                await Promise.all([
                    fetchPositionData(),
                    balanceCardsRef.current?.refreshBalance()
                ]);
                console.log('定时器刷新完成，持仓和余额数据已更新');
            } catch (error) {
                console.error('定时器刷新失败:', error);
            }
        },
        {
            interval: 10000, // 保持原有的10秒间隔
            persistKey: 'trading-dashboard-timer', // 页面独立的存储key
            autoStart: false // 手动控制启动
        }
    );

    // 切换自动刷新状态
    const toggleAutoRefresh = useCallback((enabled: boolean) => {
        if (enabled) {
            startTimer();
        } else {
            stopTimer();
        }
    }, [startTimer, stopTimer]);

    // 手动刷新函数 - 统一管理持仓和余额数据刷新
    const handleManualRefresh = useCallback(async () => {
        if (isRefreshing) return;

        console.log('执行手动刷新，更新持仓和余额数据');
        setIsRefreshing(true);

        try {
            // 并行请求持仓和余额数据
            await Promise.all([
                fetchPositionData(),
                balanceCardsRef.current?.refreshBalance()
            ]);
            console.log('手动刷新完成，持仓和余额数据已更新');
        } catch (error) {
            console.error('手动刷新失败:', error);
        } finally {
            setIsRefreshing(false);
        }
    }, [isRefreshing]);

    // 初始化数据 - 只初始化持仓数据，避免重复调用
    useEffect(() => {
        console.log("TradingDashboard 初始化，获取持仓数据");
        fetchPositionData();
    }, []);
    // 监听 selectedCex 变化，刷新持仓数据
    useEffect(() => {
        if (selectedCex) {
            console.log('selectedCex 变化，刷新持仓数据:', selectedCex);
            fetchPositionData();
        }
    }, [selectedCex]);

    return (
        <div style={{padding: '24px'}}>
            {/* 页面标题和控制开关 */}
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px'}}>
                <Title level={2}>交易仪表板</Title>
                <Space split={<span style={{color: '#d9d9d9', margin: '0 4px'}}>|</span>}>
                    <Space>
                        <SyncOutlined style={{color: isTimerActive ? '#52c41a' : '#8c8c8c'}}/>
                        <span>自动刷新</span>
                        <Switch
                            checked={isTimerActive}
                            onChange={() => toggleAutoRefresh(!isTimerActive)}
                            size="small"
                        />
                    </Space>
                    <span
                        className={isTimerActive || isRefreshing ? "cursor-not-allowed" : "cursor-pointer text-blue-600"}
                        onClick={() => {
                            console.log('TradingDashboard 手动刷新按钮被点击，isTimerActive:', isTimerActive, 'isRefreshing:', isRefreshing);
                            if (!isTimerActive && !isRefreshing) {
                                handleManualRefresh();
                            }
                        }}
                        style={{
                            color: isTimerActive || isRefreshing ? '#d9d9d9' : '#1890ff',
                            pointerEvents: isTimerActive || isRefreshing ? 'none' : 'auto'
                        }}
                    >
            {isRefreshing ? '刷新中...' : '手动刷新'}
          </span>
                </Space>
            </div>

            {/* 账户余额统计卡片 */}
            <div style={{marginBottom: 32}}>
                <BalanceCards
                    ref={balanceCardsRef}
                    selectedCex={selectedCex}
                    onCexChange={setSelectedCex}
                />
            </div>

            <Divider/>

            {/* 持仓统计卡片 */}
            <Row gutter={16} style={{marginBottom: 24}}>
                <Col span={8}>
                    <Card
                        title="总持仓"
                        variant="borderless"
                    >
                        <Spin spinning={loading || isRefreshing}>
                            <div style={{fontSize: '24px', fontWeight: 'bold', color: '#1890ff'}}>
                                {(positionSummary?.longPositions || 0) + (positionSummary?.shortPositions || 0)}
                            </div>
                            <div style={{color: '#999'}}>活跃持仓</div>
                        </Spin>
                    </Card>
                </Col>
                <Col span={8}>
                    <Card title="未结盈亏" variant="borderless">
                        <Spin spinning={loading || isRefreshing}>
                            <div style={{
                                fontSize: '24px',
                                fontWeight: 'bold',
                                color: (positionSummary?.totalUpl || 0) >= 0 ? '#52c41a' : '#ff4d4f'
                            }}>
                                {(positionSummary?.totalUpl || 0) >= 0 ? '+' : ''}
                                {OkxPositionService.formatNumber(positionSummary?.totalUpl || 0)}
                            </div>
                            <div style={{color: '#999'}}>未实现盈亏</div>
                        </Spin>
                    </Card>
                </Col>
                <Col span={8}>
                    <Card title="保证金使用" variant="borderless">
                        <Spin spinning={loading || isRefreshing}>
                            <div style={{fontSize: '24px', fontWeight: 'bold', color: '#fa8c16'}}>
                                {OkxPositionService.formatPercentage((positionSummary?.totalMargin || 0) / 100)}
                            </div>
                            <div style={{color: '#999'}}>使用率</div>
                        </Spin>
                    </Card>
                </Col>
            </Row>

            {/* 错误提示 */}
            {error && (
                <Alert
                    message={error}
                    type="error"
                    showIcon
                    closable
                    action={
                        <span className="cursor-pointer text-blue-600" onClick={fetchPositionData}>
              重试
            </span>
                    }
                    style={{marginBottom: 24}}
                />
            )}

            <Row gutter={16}>
                <Col span={12}>
                    <Card title="快捷操作" variant="borderless">
                        <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                            <Button
                                type="primary"
                                block
                                onClick={() => navigate('/positions')}
                            >
                                查看持仓详情
                            </Button>
                            <Button
                                block
                                onClick={() => navigate('/system/cex-keys')}
                            >
                                管理API密钥
                            </Button>
                            <Button
                                block
                                onClick={() => navigate('/system')}
                            >
                                系统管理
                            </Button>
                        </div>
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="最近活动" variant="borderless">
                        <div style={{color: '#666'}}>
                            <p>• BTC/USDT 多头仓位开仓</p>
                            <p>• ETH/USDT 空头仓位平仓</p>
                            <p>• SOL/USDT 多头仓位加仓</p>
                        </div>
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default TradingDashboard;