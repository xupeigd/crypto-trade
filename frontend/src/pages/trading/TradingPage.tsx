import React, {useEffect, useState} from 'react';
import {Alert, Badge, Col, Row, Select, Spin, Tag, Typography} from 'antd';
import {LeftOutlined, RightOutlined, RobotOutlined, SafetyCertificateOutlined} from '@ant-design/icons';
import TradingForm from './components/TradingForm';
import OrderList from './components/OrderList';
import AccountDetailsCard from '../../components/AccountDetailsCard';
import RiskControlOrderList from '../../components/risk-control/RiskControlOrderList';
import {getActiveApiKeys} from '../../services/tradingService';
import {aiTradingService} from '../ai-trading/aiTradingService';
import {
    RiskMode,
    RiskModeColorMap,
    RiskModeIconMap,
    TradingStyleColorMap,
    TradingStyleIconMap,
    TradingStyleMap
} from '../ai-trading/types';
import './TradingPage.css';

const {Title, Text} = Typography;

interface ApiKey {
    keyId: number;
    keyName: string;
    accessKey: string;
    vendor: string;
    status: string;
    isLiveTrading: boolean;
    createdTime: string;
}


const OkxTradingPage: React.FC = () => {
    const [apiKeys, setApiKeys] = useState<ApiKey[]>([]);
    const [selectedApiKey, setSelectedApiKey] = useState<number | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [riskControlInfo, setRiskControlInfo] = useState<any>(null);
    // 风控栏目显示状态 - 默认隐藏
    const [riskControlVisible, setRiskControlVisible] = useState(false);

    // 获取API Key列表
    const fetchApiKeys = async () => {
        try {
            const response = await getActiveApiKeys();
            setApiKeys(response.data.data || []);
            if (response.data.data && response.data.data.length > 0 && !selectedApiKey) {
                setSelectedApiKey(response.data.data[0].keyId);
            }
        } catch (err: any) {
            console.error('获取API Key失败:', err);
            setError('获取API Key失败');
        }
    };

    // 获取风控模式信息
    const fetchRiskControlInfo = async () => {
        try {
            const response = await aiTradingService.getRiskControlInfo();
            setRiskControlInfo(response);
        } catch (err: any) {
            console.error('获取风控模式失败:', err);
        }
    };


    // 初始化数据加载
    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            setError(null);
            try {
                // 并行获取API Keys和风控模式信息
                await Promise.all([
                    fetchApiKeys(),
                    fetchRiskControlInfo()
                ]);
            } finally {
                setLoading(false);
            }
        };
        loadData();
    }, []);

    if (loading) {
        return (
            <div className="trading-page">
                <div className="loading-container">
                    <Spin size="large"/>
                    <Text style={{marginTop: 16, display: 'block'}}>加载交易数据中...</Text>
                </div>
            </div>
        );
    }

    return (
        <div className="trading-page">
            <div className="page-header">
                <Title level={2}>永续合约交易</Title>
                <div className="header-info">
                    {riskControlInfo && (
                        <>
                            <div className="risk-control-display" style={{marginRight: '16px'}}>
                                <RobotOutlined style={{marginRight: '6px', color: 'rgba(255, 255, 255, 0.65)'}}/>
                                <Text strong style={{marginRight: '6px', color: 'rgba(255, 255, 255, 0.85)'}}>
                                    风控:
                                </Text>
                                <Badge
                                    color={RiskModeColorMap[riskControlInfo.currentMode as keyof typeof RiskModeColorMap]}
                                    text={
                                        <span style={{color: 'rgba(255, 255, 255, 0.85)'}}>
                                            {RiskModeIconMap[riskControlInfo.currentMode as keyof typeof RiskModeIconMap]} {riskControlInfo.currentMode === RiskMode.AUTO ? '自动' : '手动'}
                                        </span>
                                    }
                                />
                            </div>
                            <div className="trading-style-display" style={{marginRight: '16px'}}>
                                <span style={{marginRight: '6px', color: 'rgba(255, 255, 255, 0.65)'}}>
                                    {TradingStyleIconMap[riskControlInfo.currentTradingStyle as keyof typeof TradingStyleIconMap]}
                                </span>
                                <Text strong style={{marginRight: '6px', color: 'rgba(255, 255, 255, 0.85)'}}>
                                    交易风格:
                                </Text>
                                <Badge
                                    color={TradingStyleColorMap[riskControlInfo.currentTradingStyle as keyof typeof TradingStyleColorMap]}
                                    text={
                                        <span style={{color: 'rgba(255, 255, 255, 0.85)'}}>
                                            {TradingStyleMap[riskControlInfo.currentTradingStyle as keyof typeof TradingStyleMap]}
                                        </span>
                                    }
                                />
                            </div>
                        </>
                    )}
                    <div className="api-key-selector">
                        <Text strong style={{marginRight: 8}}>API Key: </Text>
                        <Select
                            value={selectedApiKey}
                            onChange={setSelectedApiKey}
                            placeholder="选择API Key"
                            style={{
                                minWidth: 180,
                                maxWidth: 250
                            }}
                            size="middle"
                            showSearch
                            filterOption={(input, option) =>
                                (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                            }
                            allowClear
                        >
                            {apiKeys.map((apiKey) => (
                                <Select.Option key={apiKey.keyId} value={apiKey.keyId}>
                                    <div style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between'
                                    }}>
                                        <span>{apiKey.keyName}</span>
                                        <Tag
                                            color={apiKey.isLiveTrading ? 'red' : 'green'}
                                            style={{marginLeft: 8}}
                                        >
                                            {apiKey.isLiveTrading ? '实盘' : '模拟'}
                                        </Tag>
                                    </div>
                                </Select.Option>
                            ))}
                        </Select>
                    </div>
                </div>
            </div>

            {error && (
                <Alert
                    message="加载失败"
                    description={error}
                    type="error"
                    style={{marginBottom: 16}}
                    closable
                    onClose={() => setError(null)}
                />
            )}

            <div className="trading-container" style={{position: 'relative'}}>
                {/* 侧边栏控制把手 - 仅在隐藏时显示 */}
                {!riskControlVisible && (
                    <div
                        onClick={() => setRiskControlVisible(true)}
                        style={{
                            position: 'absolute',
                            top: '150px', // 调整垂直位置，避开头部
                            right: '0',
                            width: '16px',
                            height: '100px',
                            background: '#1f1f1f',
                            borderRadius: '8px 0 0 8px', // 左侧圆角
                            border: '1px solid #424242',
                            borderRight: 'none',
                            cursor: 'pointer',
                            zIndex: 99,
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '-2px 0 8px rgba(0,0,0,0.2)',
                            transition: 'all 0.3s'
                        }}
                        title="展开风控审核"
                        className="risk-control-handle"
                    >
                        <SafetyCertificateOutlined style={{color: '#faad14', fontSize: '12px', marginBottom: '8px'}} />
                        <div style={{
                            writingMode: 'vertical-rl',
                            textOrientation: 'upright',
                            color: 'rgba(255,255,255,0.85)',
                            fontSize: '10px',
                            letterSpacing: '2px',
                        }}>
                            风控
                        </div>
                        <LeftOutlined style={{fontSize: '10px', marginTop: '8px', color: 'rgba(255,255,255,0.45)'}}/>
                    </div>
                )}

                <Row gutter={[16, 16]}>
                    <Col xs={24} lg={riskControlVisible ? 13 : 15} style={{transition: 'all 0.3s ease'}}>
                        {/* 账户详情卡片 */}
                        <div style={{marginBottom: 16}}>
                            <AccountDetailsCard
                                apiKeyId={selectedApiKey}
                                refreshInterval={5000}
                                autoRefresh={true}
                                title="账户详情"
                            />
                        </div>

                        {/* 交易表单 */}
                        <TradingForm
                            apiKeys={apiKeys}
                            selectedApiKey={selectedApiKey}
                            onTradeSuccess={() => {
                                // 交易成功后可以刷新订单列表
                            }}
                        />
                    </Col>
                    <Col xs={24} lg={riskControlVisible ? 7 : 9} style={{transition: 'all 0.3s ease'}}>
                        <OrderList
                            apiKeyId={selectedApiKey}
                            onOrderUpdate={() => {
                                // 订单更新后的处理
                            }}
                        />
                    </Col>
                    
                    {/* 风控列表栏目 - 仅在visible为true时渲染 */}
                    {riskControlVisible && (
                        <Col xs={24} lg={4} style={{transition: 'all 0.3s ease', position: 'relative', zIndex: 100}}>
                            <div style={{position: 'relative', height: '100%'}}>
                                {/* 关闭按钮 - 调整样式为侧边提手 */}
                                <div
                                    onClick={() => setRiskControlVisible(false)}
                                    style={{
                                        position: 'absolute',
                                        left: '-12px', // 贴在左侧外
                                        top: '150px', // 与展开前的提手位置对齐
                                        width: '12px',
                                        height: '100px',
                                        background: '#1f1f1f',
                                        borderRadius: '8px 0 0 8px', // 左侧圆角
                                        border: '1px solid #424242',
                                        borderRight: 'none',
                                        cursor: 'pointer',
                                        zIndex: 10,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        boxShadow: '-2px 0 8px rgba(0,0,0,0.2)',
                                        color: 'rgba(255, 255, 255, 0.45)'
                                    }}
                                    title="收起风控审核"
                                    className="risk-control-close-handle"
                                >
                                    <RightOutlined style={{fontSize: '10px'}} />
                                </div>
                                <RiskControlOrderList
                                    apiKeyId={selectedApiKey}
                                    onOrderUpdate={() => {
                                        // 风控审核操作后的处理
                                    }}
                                />
                            </div>
                        </Col>
                    )}
                </Row>
            </div>
        </div>
    );
};

export default OkxTradingPage;