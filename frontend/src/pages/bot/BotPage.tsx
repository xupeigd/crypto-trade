import React, {useEffect, useState} from 'react';
import {Card, message, Spin} from 'antd';
import {RobotOutlined} from '@ant-design/icons';
import BotStatus from './BotStatus';
import BotControl from './BotControl';
import PromptHistory from './PromptHistory';
import PromptEditor from '../../components/bot/PromptEditor';
import {botService} from '../../services/botService';
import {AIInfoModel} from '../../types/aiInfoModel';

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

const BotPage: React.FC = () => {
    const [botStatus, setBotStatus] = useState<BotStatusType | null>(null);
    const [loading, setLoading] = useState(true);
    const [selectedApiKeyId, setSelectedApiKeyId] = useState<number | null>(null);
    const [selectedModel, setSelectedModel] = useState<AIInfoModel | null>(null);
    const [refreshTrigger, setRefreshTrigger] = useState(0);
    const [promptGenerateTrigger, setPromptGenerateTrigger] = useState(0);
    const [screenSize, setScreenSize] = useState<'large' | 'medium' | 'small'>('large');
    const [triggering, setTriggering] = useState(false);
    const [generatingPrompt, setGeneratingPrompt] = useState(false);
    // 权益弹窗状态管理
    const [showEquityModal, setShowEquityModal] = useState(false);
    // 会话链路弹窗状态管理
    const [chainModalVisible, setChainModalVisible] = useState(false);
    const [selectedRecordId, setSelectedRecordId] = useState<number | null>(null);
    // BotControl折叠状态管理
    const [botControlCollapsed, setBotControlCollapsed] = useState(true);

    // 计算是否有任何弹窗打开
    const hasOpenModal = showEquityModal || chainModalVisible;

    // 处理转移到AI助手
    const handleTransferToChat = (sessionId: number, userId: string) => {
        // 直接通过window事件通知ChatFloatWindow加载指定会话并展开
        window.dispatchEvent(new CustomEvent('chat-transfer', {
            detail: {sessionId, userId: userId || 'default'}
        }));
        message.success('已转移到AI助手');
    };

    // 获取BOT状态
    const fetchBotStatus = async (apiKeyId?: number, isBackground = false) => {
        try {
            if (!isBackground) {
                setLoading(true);
            }
            const response = await botService.getBotStatus(apiKeyId);
            if (response.success && response.data) {
                setBotStatus(response.data);
                setSelectedApiKeyId(response.data.apiKeyId);
            } else {
                if (!isBackground) {
                    message.error(response.message || '获取BOT状态失败');
                }
            }
        } catch (error) {
            console.error('获取BOT状态失败:', error);
            if (!isBackground) {
                message.error('获取BOT状态失败');
            }
        } finally {
            if (!isBackground) {
                setLoading(false);
            }
        }
    };

    // 手动触发BOT
    const handleTriggerBot = async (apiKeyId: number): Promise<boolean> => {
        try {
            setTriggering(true);
            // 获取选中的模型名称
            const modelName = selectedModel?.modelId;
            // 调用triggerBot,传递模型名称
            const response = await botService.triggerBot(apiKeyId, modelName);

            if (response.success) {
                message.success('BOT触发成功');
                // 刷新状态和prompt历史
                setRefreshTrigger(prev => prev + 1);
                fetchBotStatus(apiKeyId);
                return true; // 返回成功状态
            } else {
                message.error(response.message || 'BOT触发失败');
                return false; // 返回失败状态
            }
        } catch (error) {
            console.error('BOT触发失败:', error);
            message.error('BOT触发失败');
            return false; // 返回失败状态
        } finally {
            setTriggering(false);
        }
    };

    // API Key切换
    const handleApiKeyChange = (apiKeyId: number) => {
        setSelectedApiKeyId(apiKeyId);
        fetchBotStatus(apiKeyId);
    };

    // 模型切换
    const handleModelChange = (model: AIInfoModel | null) => {
        setSelectedModel(model);
    };

    // 处理生成prompt
    const handleGeneratePrompt = (apiKeyId: number) => {
        setPromptGenerateTrigger(prev => prev + 1);
    };

    // 处理prompt生成完成
    const handlePromptGenerated = (promptData: any) => {
        // 当prompt生成完成或AI调用完成后，可以刷新历史记录
        setRefreshTrigger(prev => prev + 1);
    };

    // 初始化加载
    useEffect(() => {
        fetchBotStatus();
    }, []);

    // 屏幕尺寸检测
    useEffect(() => {
        const handleResize = () => {
            const width = window.innerWidth;
            if (width < 1024) {
                setScreenSize('small');
            } else if (width < 1600) {
                setScreenSize('medium');
            } else {
                setScreenSize('large');
            }
        };

        handleResize(); // 初始设置
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    // 定时刷新状态 - 弹窗打开时暂停
    // 优化:使用静默模式拉取数据,避免loading闪烁
    useEffect(() => {
        // 如果有弹窗打开,不设置定时器
        if (hasOpenModal) {
            return;
        }

        const interval = setInterval(() => {
            if (selectedApiKeyId) {
                // 静默模式:不显示loading,成功后才更新状态,避免闪烁
                fetchBotStatus(selectedApiKeyId, true);
            }
        }, 30000); // 30秒刷新一次

        return () => clearInterval(interval);
    }, [selectedApiKeyId, hasOpenModal]);

    return (
        <div style={{padding: '0', flex: 1, display: 'flex', flexDirection: 'column', minHeight: '100vh'}}>
            {/* 标题 */}
            <div style={{
                marginBottom: '16px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
            }}>
                <RobotOutlined style={{fontSize: '24px', color: '#1677ff'}}/>
                <h2 style={{margin: 0, color: 'rgba(255, 255, 255, 0.95)'}}>
                    AI Trading
                </h2>
            </div>

            {/* 主要内容区域 - 使用flex布局 */}
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                flex: 1,
                gap: '16px'
            }}>
                {/* 上栏 - BOT状态 */}
                <div style={{flex: '0 0 auto', minHeight: '50px'}}>
                    <Card
                        style={{
                            height: '100%',
                            backgroundColor: '#1f1f1f',
                            border: '1px solid #434343',
                            borderRadius: '8px'
                        }}
                        styles={{
                            body: {
                                padding: '16px',
                                height: '100%',
                                display: 'flex',
                                alignItems: 'center'
                            }
                        }}
                    >
                        {loading ? (
                            <div style={{display: 'flex', justifyContent: 'center', width: '100%'}}>
                                <Spin size="small"/>
                            </div>
                        ) : (
                            botStatus && (
                                <BotStatus
                                    status={botStatus}
                                    showEquityModal={showEquityModal}
                                    onOpenEquityModal={() => setShowEquityModal(true)}
                                    onCloseEquityModal={() => setShowEquityModal(false)}
                                />
                            )
                        )}
                    </Card>
                </div>

                {/* 下栏 - 两栏布局 */}
                <div style={{
                    flex: 1,
                    display: screenSize === 'small' ? 'block' : 'flex',
                    gap: '16px',
                    minHeight: 0
                }}>
                    {screenSize === 'small' ? (
                        // 小屏幕：垂直布局
                        <div style={{flex: 1, display: 'flex', flexDirection: 'column', gap: '16px'}}>
                            {/* 控制面板 */}
                            <div style={{flex: '0 0 auto', minHeight: botControlCollapsed ? '80px' : '180px'}}>
                                <BotControl
                                    selectedApiKeyId={selectedApiKeyId}
                                    selectedModel={selectedModel}
                                    onTriggerBot={handleTriggerBot}
                                    onApiKeyChange={handleApiKeyChange}
                                    onModelChange={handleModelChange}
                                    onGeneratePrompt={handleGeneratePrompt}
                                    refreshTrigger={refreshTrigger}
                                    botStatus={botStatus}
                                    collapsed={botControlCollapsed}
                                    setCollapsed={setBotControlCollapsed}
                                    generatingPrompt={generatingPrompt}
                                />
                            </div>
                            {/* Prompt编辑器 */}
                            <div style={{flex: '1 1 auto', minHeight: '240px', position: 'relative'}}>
                                <PromptEditor
                                    apiKeyId={selectedApiKeyId}
                                    selectedModel={selectedModel}
                                    onPromptGenerated={handlePromptGenerated}
                                    triggerGenerate={promptGenerateTrigger}
                                    onGeneratingChange={setGeneratingPrompt}
                                />
                            </div>
                            {/* Prompt历史 */}
                            <div style={{flex: '1 1 auto', minHeight: '200px', position: 'relative'}}>
                                <PromptHistory
                                    apiKeyId={selectedApiKeyId}
                                    refreshTrigger={refreshTrigger}
                                    chainModalVisible={chainModalVisible}
                                    onOpenChainModal={(recordId) => {
                                        setSelectedRecordId(recordId);
                                        setChainModalVisible(true);
                                    }}
                                    onCloseChainModal={() => {
                                        setChainModalVisible(false);
                                        setSelectedRecordId(null);
                                    }}
                                    selectedRecordId={selectedRecordId}
                                    onTransferToChat={handleTransferToChat}
                                />
                            </div>
                        </div>
                    ) : (
                        // 中大屏幕：左右布局
                        <>
                            {/* 左侧区域 - 上下布局 */}
                            <div style={{
                                flex: '2 2 40%',  // 修改：左右比例 2:3
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '16px',
                                height: '100%', // 确保高度撑满
                                maxWidth: screenSize === 'medium' ? '550px' : '600px',  // 防止宽度过大
                                minWidth: screenSize === 'medium' ? '400px' : '440px',   // 保持最小宽度
                                overflow: 'hidden'  // 防止内容溢出
                            }}>
                                {/* 上部 - BotControl */}
                                <div style={{flex: '0 0 auto', minHeight: botControlCollapsed ? '80px' : '180px'}}>
                                    <BotControl
                                        selectedApiKeyId={selectedApiKeyId}
                                        selectedModel={selectedModel}
                                        onTriggerBot={handleTriggerBot}
                                        onApiKeyChange={handleApiKeyChange}
                                        onModelChange={handleModelChange}
                                        onGeneratePrompt={handleGeneratePrompt}
                                        refreshTrigger={refreshTrigger}
                                        botStatus={botStatus}
                                        collapsed={botControlCollapsed}
                                        setCollapsed={setBotControlCollapsed}
                                        generatingPrompt={generatingPrompt}
                                    />
                                </div>

                                {/* 下部 - PromptEditor */}
                                <div style={{flex: '1 1 auto', minHeight: '320px', position: 'relative'}}>
                                    <PromptEditor
                                        apiKeyId={selectedApiKeyId}
                                        selectedModel={selectedModel}
                                        onPromptGenerated={handlePromptGenerated}
                                        triggerGenerate={promptGenerateTrigger}
                                        onGeneratingChange={setGeneratingPrompt}
                                    />
                                </div>
                            </div>

                            {/* 右侧区域 - Prompt历史 */}
                            <div style={{
                                flex: '3 3 60%', // 修改：左右比例 2:3
                                maxWidth: screenSize === 'medium' ? '700px' : '800px',  // 防止宽度过大
                                minWidth: screenSize === 'medium' ? '450px' : '500px',  // 保持最小宽度
                                overflow: 'hidden'  // 防止内容溢出
                            }}>
                                <PromptHistory
                                    apiKeyId={selectedApiKeyId}
                                    refreshTrigger={refreshTrigger}
                                    chainModalVisible={chainModalVisible}
                                    onOpenChainModal={(recordId) => {
                                        setSelectedRecordId(recordId);
                                        setChainModalVisible(true);
                                    }}
                                    onCloseChainModal={() => {
                                        setChainModalVisible(false);
                                        setSelectedRecordId(null);
                                    }}
                                    selectedRecordId={selectedRecordId}
                                    onTransferToChat={handleTransferToChat}
                                />
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default BotPage;
