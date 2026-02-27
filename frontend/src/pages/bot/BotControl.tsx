import React, {useEffect, useState, useRef} from 'react';
import {Button, Card, Divider, message, Select, Space, Tag, Typography} from 'antd';
import {
    DownOutlined,
    KeyOutlined,
    PlayCircleOutlined,
    RobotOutlined,
    SecurityScanOutlined,
    SettingOutlined,
    ThunderboltOutlined,
    UpOutlined
} from '@ant-design/icons';
import {AIModelConfig, botService} from '../../services/botService';
import {AIInfoModel} from '../../types/aiInfoModel';
import {aiTradingService} from '../ai-trading/aiTradingService';
import {RiskControlInfo} from '../ai-trading/types';

const {Title, Text} = Typography;
const {Option} = Select;

interface ApiKeyInfo {
    keyId: number;
    keyName: string;
    vendor: string;
    status: string;
    isLiveTrading: boolean;
}

interface BotStatusType {
    callCount: number;
    lastCallTime: number | null;
    modelName: string;
    apiKeyId: number;
    apiKeyDescription: string;
    riskControlMode: string;
    tradingStyle: string;
    automaticTradeEnabled: boolean;
    systemStatus: string;
    lastProcessingTime: number;
}

interface BotControlProps {
    selectedApiKeyId: number | null;
    selectedModel: AIInfoModel | null;
    onTriggerBot: (apiKeyId: number) => void;
    onApiKeyChange: (apiKeyId: number) => void;
    onModelChange: (model: AIInfoModel | null) => void;
    onGeneratePrompt?: (apiKeyId: number) => void;
    refreshTrigger: number;
    botStatus: BotStatusType | null;
    collapsed: boolean;
    setCollapsed: (collapsed: boolean) => void;
    generatingPrompt?: boolean;
}

const BotControl: React.FC<BotControlProps> = ({
                                                   selectedApiKeyId,
                                                   selectedModel,
                                                   onTriggerBot,
                                                   onApiKeyChange,
                                                   onModelChange,
                                                   onGeneratePrompt,
                                                   refreshTrigger,
                                                   botStatus,
                                                   collapsed,
                                                   setCollapsed,
                                                   generatingPrompt
                                               }) => {
    const [apiKeys, setApiKeys] = useState<ApiKeyInfo[]>([]);
    const [models, setModels] = useState<AIInfoModel[]>([]);
    const [loading, setLoading] = useState(false);
    const [triggering, setTriggering] = useState(false);
    const [riskControlInfo, setRiskControlInfo] = useState<RiskControlInfo | null>(null);

    // 倒计时状态管理: 用于BOT触发后的30秒冷却
    const [countdown, setCountdown] = useState(0); // 倒计时剩余秒数,0表示未启动
    const countdownTimerRef = useRef<number | null>(null); // 定时器引用,用于清理

    // 交易风格映射
    const getTradingStyleConfig = (style: string) => {
        const styleMap: Record<string, { text: string; color: string; style?: React.CSSProperties }> = {
            '保守型': {
                text: '保守型',
                color: 'rgba(82, 196, 26, 0.3)', // Green
                style: {color: '#52c41a', border: '1px solid #52c41a'}
            },
            '谨慎型': {
                text: '谨慎型',
                color: 'rgba(160, 217, 17, 0.3)', // Lime
                style: {color: '#a0d911', border: '1px solid #a0d911'}
            },
            '稳健型': {
                text: '稳健型',
                color: 'rgba(250, 173, 20, 0.3)', // Gold/Yellow
                style: {color: '#faad14', border: '1px solid #faad14'}
            },
            '积极型': {
                text: '积极型',
                color: 'rgba(250, 140, 22, 0.3)', // Orange
                style: {color: '#fa8c16', border: '1px solid #fa8c16'}
            },
            '激进型': {
                text: '激进型',
                color: 'rgba(255, 77, 79, 0.3)', // Red
                style: {color: '#ff4d4f', border: '1px solid #ff4d4f'}
            }
        };
        return styleMap[style] || {text: style, color: 'default'};
    };

    // 风控模式映射
    const getRiskModeConfig = (mode: string) => {
        const modeMap: Record<string, { text: string; color: string; style?: React.CSSProperties }> = {
            'AUTO': {
                text: '自动模式',
                color: 'rgba(250, 173, 20, 0.3)', // 橙色透明
                style: {color: '#faad14', border: '1px solid #faad14'}
            },
            'MANUAL': {
                text: '人工模式',
                color: 'rgba(82, 196, 26, 0.3)', // 绿色透明
                style: {color: '#52c41a', border: '1px solid #52c41a'}
            },
            '自动模式': {
                text: '自动模式',
                color: 'rgba(250, 173, 20, 0.3)',
                style: {color: '#faad14', border: '1px solid #faad14'}
            }, // 兼容旧数据
            '手动模式': {
                text: '人工模式',
                color: 'rgba(82, 196, 26, 0.3)',
                style: {color: '#52c41a', border: '1px solid #52c41a'}
            }  // 兼容旧数据
        };
        return modeMap[mode] || {text: mode, color: 'default'};
    };

    // 获取模型列表
    const fetchModels = async () => {
        try {
            // 调用API获取活跃的模型配置
            const response = await botService.getActiveModels();

            if (response.success && response.data) {
                // 转换AIModelConfig为AIInfoModel格式
                const modelList: AIInfoModel[] = response.data.map((config: AIModelConfig) => ({
                    modelId: config.modelId,
                    displayName: config.displayName,
                    provider: config.provider,
                    parameterSize: config.parameterSize || 0, // 默认值为0
                    description: config.description || '', // 默认值为空字符串
                    isActive: config.isActive,
                    maxTokens: config.maxTokens || 0, // 默认值为0
                    costPerToken: config.costPerToken || 0, // 默认值为0
                    defaultModel: config.defaultModel
                }));

                setModels(modelList);

                // 如果没有选中模型，尝试获取默认模型或选中第一个
                if (!selectedModel && modelList.length > 0) {
                    const defaultModel = modelList.find(m => m.modelId === response.data.find((c: AIModelConfig) => c.defaultModel)?.modelId) || modelList[0];
                    onModelChange(defaultModel);
                }
            } else {
                console.warn('获取模型配置失败:', response.message);
                // 不回退到硬编码，设置空数组
                setModels([]);
            }
        } catch (error) {
            console.error('获取模型列表失败:', error);
            // 不回退到硬编码，设置空数组
            setModels([]);
        }
    };

    
    // 获取API Key列表
    const fetchApiKeys = async () => {
        try {
            setLoading(true);
            const response = await botService.getActiveApiKeys();
            if (response.success && response.data) {
                setApiKeys(response.data);
            } else {
                message.error(response.message || '获取API Key列表失败');
            }
        } catch (error) {
            console.error('获取API Key列表失败:', error);
            message.error('获取API Key列表失败');
        } finally {
            setLoading(false);
        }
    };

    // 处理生成prompt
    const handleGeneratePrompt = () => {
        if (!selectedApiKeyId) {
            message.warning('请先选择API Key');
            return;
        }

        if (onGeneratePrompt) {
            onGeneratePrompt(selectedApiKeyId);
        }
    };

    // 启动倒计时函数: 在BOT触发成功后启动30秒冷却倒计时
    const startCountdown = () => {
        // 清除之前的定时器,防止重复启动
        if (countdownTimerRef.current) {
            clearInterval(countdownTimerRef.current);
        }

        // 初始化倒计时为30秒
        setCountdown(30);

        // 启动定时器,每秒递减
        countdownTimerRef.current = setInterval(() => {
            setCountdown(prev => {
                if (prev <= 1) {
                    // 倒计时结束,清理定时器
                    if (countdownTimerRef.current) {
                        clearInterval(countdownTimerRef.current);
                        countdownTimerRef.current = null;
                    }
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    };

    // 处理BOT触发（保留原有功能）
    const handleTrigger = async () => {
        if (!selectedApiKeyId) {
            message.warning('请先选择API Key');
            return;
        }

        try {
            setTriggering(true);
            await onTriggerBot(selectedApiKeyId);

            // 接口调用成功后启动30秒倒计时
            startCountdown();
        } catch (error) {
            console.error('触发BOT失败:', error);
        } finally {
            setTriggering(false);
        }
    };

    // API Key切换
    const handleApiKeyChange = (apiKeyId: number) => {
        onApiKeyChange(apiKeyId);
    };

    // 初始化和刷新时加载数据
    useEffect(() => {
        fetchApiKeys();
        fetchModels();
        fetchRiskControlInfo();
    }, [refreshTrigger]);

    // 组件卸载时清理定时器,防止内存泄漏
    useEffect(() => {
        return () => {
            if (countdownTimerRef.current) {
                clearInterval(countdownTimerRef.current);
            }
        };
    }, []);

    // 获取风控模式信息
    const fetchRiskControlInfo = async () => {
        try {
            const info = await aiTradingService.getRiskControlInfo();
            setRiskControlInfo(info);
        } catch (error) {
            console.error('获取风控模式信息失败:', error);
        }
    };

    return (
        <Card
            title={
                <Space>
                    <SettingOutlined style={{color: '#1677ff'}}/>
                    <span>BOT控制面板</span>
                </Space>
            }
            extra={
                <Button
                    type="text"
                    icon={collapsed ? <DownOutlined /> : <UpOutlined />}
                    onClick={() => setCollapsed(!collapsed)}
                    style={{color: collapsed ? 'rgba(255, 255, 255, 0.65)' : '#1677ff'}}
                />
            }
            style={{
                backgroundColor: '#1f1f1f',
                border: '1px solid #434343',
                borderRadius: '8px',
                maxWidth: '100%',  // 确保不超出父容器
                overflow: 'hidden'  // 防止内容溢出
            }}
            styles={{
                header: {
                    backgroundColor: '#1a1a1a',
                    borderBottom: '1px solid #434343',
                    color: 'rgba(255, 255, 255, 0.95)'
                },
                body: {
                    padding: '10px',
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'auto'  // 允许内容滚动,防止溢出
                }
            }}
        >
            <div style={{display: 'flex', flexDirection: 'column'}}>
                {/* 上部分 - 配置信息 */}
                {!collapsed && (
                <div style={{flex: '0 0 auto', marginBottom: '8px'}}>
                    <Space direction="vertical" size="small" style={{width: '100%'}}>
                        {/* API Key选择 */}
                        <div>
                            <Space align="center" style={{width: '100%', justifyContent: 'space-between'}}>
                                <Space>
                                    <KeyOutlined style={{color: '#1677ff'}}/>
                                    <Text strong style={{color: 'rgba(255, 255, 255, 0.95)', fontSize: '12px'}}>
                                        API Key
                                    </Text>
                                </Space>
                                <Select
                                    value={selectedApiKeyId}
                                    onChange={handleApiKeyChange}
                                    placeholder="选择API Key"
                                    loading={loading}
                                    style={{width: '180px', fontSize: '12px'}}
                                    size="small"
                                >
                                    {apiKeys.map(key => (
                                        <Option key={key.keyId} value={key.keyId}>
                                            <Space>
                                                <span style={{fontSize: '12px'}}>{key.keyName}</span>
                                                {key.isLiveTrading && (
                                                    <Tag color="red" style={{fontSize: '10px', lineHeight: '18px'}}>实盘</Tag>
                                                )}
                                                <Tag color={key.status === 'active' ? 'success' : 'default'} style={{fontSize: '10px', lineHeight: '18px'}}>
                                                    {key.status}
                                                </Tag>
                                            </Space>
                                        </Option>
                                    ))}
                                </Select>
                            </Space>
                        </div>

                        {/* AI模型选择 */}
                        <div>
                            <Space align="center" style={{width: '100%', justifyContent: 'space-between'}}>
                                <Space>
                                    <RobotOutlined style={{color: '#1677ff'}}/>
                                    <Text strong style={{color: 'rgba(255, 255, 255, 0.95)', fontSize: '12px'}}>
                                        AI模型
                                    </Text>
                                </Space>
                                <Select
                                    value={selectedModel?.modelId}
                                    onChange={(modelId) => {
                                        const model = models.find(m => m.modelId === modelId);
                                        onModelChange(model || null);
                                    }}
                                    placeholder="选择AI模型"
                                    style={{width: '180px', fontSize: '12px'}}
                                    size="small"
                                >
                                    {models.map(model => (
                                        <Option key={model.modelId} value={model.modelId}>
                                            <Space>
                                                <span style={{fontSize: '12px'}}>{model.displayName}</span>
                                                <Tag color="blue" style={{fontSize: '10px', lineHeight: '18px'}}>{model.parameterSize}B</Tag>
                                            </Space>
                                        </Option>
                                    ))}
                                </Select>
                            </Space>
                        </div>

                        {/* 风控模式和交易风格展示 */}
                        <div>
                            <Space align="center" style={{width: '100%', justifyContent: 'space-between'}}>
                                <Space>
                                    <SecurityScanOutlined style={{color: '#faad14'}}/>
                                    <Text strong style={{color: 'rgba(255, 255, 255, 0.95)', fontSize: '12px'}}>
                                        风控模式
                                    </Text>
                                </Space>
                                {riskControlInfo && (
                            <Tag color={getRiskModeConfig(riskControlInfo.currentMode).color}
                                 style={{
                                     fontSize: '12px',
                                     padding: '2px 8px',
                                     ...getRiskModeConfig(riskControlInfo.currentMode).style
                                 }}>
                                {getRiskModeConfig(riskControlInfo.currentMode).text}
                            </Tag>
                        )}
                            </Space>
                        </div>

                        <div>
                            <Space align="center" style={{width: '100%', justifyContent: 'space-between'}}>
                                <Space>
                                    <ThunderboltOutlined style={{color: '#52c41a'}}/>
                                    <Text strong style={{color: 'rgba(255, 255, 255, 0.95)', fontSize: '12px'}}>
                                        交易风格
                                    </Text>
                                </Space>
                                {botStatus && (
                                    <Tag color={getTradingStyleConfig(botStatus.tradingStyle).color}
                                         style={{
                                             fontSize: '12px',
                                             padding: '2px 8px',
                                             ...getTradingStyleConfig(botStatus.tradingStyle).style
                                         }}>
                                        {getTradingStyleConfig(botStatus.tradingStyle).text}
                                    </Tag>
                                )}
                            </Space>
                        </div>

                        <Divider style={{margin: '8px 0', borderColor: '#434343'}}/>
                    </Space>
                </div>
                )}

                {/* 下部分 - BOT触发按钮 */}
                <div style={{
                    flex: '0 0 auto',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    padding: '8px 0'
                }}>
                    <Space direction="horizontal" size="middle" align="center"
                           style={{width: '100%', justifyContent: 'center'}}>
                        {/* 生成Prompt按钮 */}
                        <Button
                            type="primary"
                            size="large"
                            icon={<PlayCircleOutlined/>}
                            onClick={handleGeneratePrompt}
                            disabled={!selectedApiKeyId || apiKeys.length === 0}
                            loading={generatingPrompt}
                            style={{
                                height: '46px',
                                width: '170px',
                                fontSize: '14px',
                                fontWeight: 'bold',
                                borderRadius: '8px',
                                background: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
                                border: 'none',
                                boxShadow: '0 4px 12px rgba(82, 196, 26, 0.3)'
                            }}
                        >
                            生成Prompt
                        </Button>

                        {/* 传统触发BOT按钮 */}
                        <Button
                            type="default"
                            size="large"
                            icon={<ThunderboltOutlined/>}
                            onClick={handleTrigger}
                            loading={triggering}
                            disabled={!selectedApiKeyId || apiKeys.length === 0 || countdown > 0}
                            style={{
                                height: '46px',
                                width: '170px',
                                fontSize: '14px',
                                fontWeight: 'bold',
                                borderRadius: '8px'
                            }}
                        >
                            {triggering ? '执行中...' : countdown > 0 ? `请等待 ${countdown} 秒` : '直接触发BOT'}
                        </Button>
                    </Space>
                </div>
            </div>
        </Card>
    );
};

export default BotControl;