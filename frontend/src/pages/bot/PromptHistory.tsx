import React, {useEffect, useState} from 'react';
import {Button, Card, Empty, Input, message, Pagination, Select, Space, Spin, Tooltip, Typography} from 'antd';
import {
    CheckCircleOutlined,
    DownOutlined,
    ExclamationCircleOutlined,
    HistoryOutlined,
    PauseOutlined,
    PlayCircleOutlined,
    ReloadOutlined,
    RobotOutlined,
    UpOutlined
} from '@ant-design/icons';
import PromptItem from './PromptItem';
import ConversationChainModal from './ConversationChainModal';
import {botService, PromptHistoryResponse} from '../../services/botService';

const {Text} = Typography;
const {Search} = Input;
const {Option} = Select;

interface PromptHistoryProps {
    apiKeyId: number | null;
    refreshTrigger: number;
    chainModalVisible?: boolean;
    onOpenChainModal?: (recordId: number) => void;
    onCloseChainModal?: () => void;
    selectedRecordId?: number | null;
    onTransferToChat?: (sessionId: number, userId: string) => void;
}

/**
 * 单个item的展开状态接口
 * 所有字段都是必需的，确保展开状态正确传递
 */
interface ItemExpandedState {
    promptContent: boolean; // Prompt内容是否展开
    aiResponse: boolean;   // AI模型响应是否展开
}

const PromptHistory: React.FC<PromptHistoryProps> = ({
    apiKeyId,
    refreshTrigger,
    chainModalVisible,
    onOpenChainModal,
    onCloseChainModal,
    selectedRecordId,
    onTransferToChat
}) => {
    const [prompts, setPrompts] = useState<PromptHistoryResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const [searchText, setSearchText] = useState('');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [actionFilter, setActionFilter] = useState<string>('all');
    const [modelFilter, setModelFilter] = useState<string>('all');
    const [callSourceFilter, setCallSourceFilter] = useState<string>('all');
    const [openCloseFilter, setOpenCloseFilter] = useState<string>('all');
    const [availableModels, setAvailableModels] = useState<string[]>([]);
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize] = useState(20);
    const [total, setTotal] = useState(0);
    const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(true);
    const [refreshInterval, setRefreshInterval] = useState<ReturnType<typeof setInterval> | null>(null);
    // 面板折叠状态，默认为false（展开状态）
    const [collapsed, setCollapsed] = useState(false);
    // 保存折叠前的自动刷新状态，用于展开时恢复
    const [prevAutoRefreshEnabled, setPrevAutoRefreshEnabled] = useState(true);

    // 管理每个item的展开状态，使用Map存储避免数据刷新时状态丢失
    const [expandedItems, setExpandedItems] = useState<Record<string, ItemExpandedState>>({});

    /**
     * 更新指定item的展开状态
     * 确保状态对象始终包含所有字段，避免undefined导致的状态丢失
     * @param itemId 项目ID
     * @param key 状态key（promptContent/aiResponse/thinking/json）
     * @param value 是否展开
     */
    const updateItemExpandedState = (itemId: string, key: keyof ItemExpandedState, value: boolean) => {
        setExpandedItems(prev => {
            // 获取当前item的状态，如果不存在则初始化所有字段为false
            const currentItemState = prev[itemId] || {
                promptContent: false,
                aiResponse: false
            };

            return {
                ...prev,
                [itemId]: {
                    ...currentItemState,
                    [key]: value
                }
            };
        });
    };

    /**
     * 确保指定item的展开状态对象完整
     * 如果状态不存在或字段缺失，返回默认的完整状态对象
     * @param decisionId 决策ID
     * @returns 完整的展开状态对象
     */
    const getCompleteExpandedState = (decisionId: string): ItemExpandedState => {
        const existingState = expandedItems[decisionId];

        // 如果状态已存在且完整，直接返回
        if (existingState &&
            typeof existingState.promptContent === 'boolean' &&
            typeof existingState.aiResponse === 'boolean') {
            return existingState;
        }

        // 否则返回默认的完整状态（全部折叠）
        return {
            promptContent: existingState?.promptContent || false,
            aiResponse: existingState?.aiResponse || false
        };
    };

    /**
     * 获取prompt历史
     * @param isBackground 是否为后台静默模式(不显示loading,不弹错误提示)
     */
    const fetchPromptHistory = async (isBackground = false) => {
        if (!apiKeyId) {
            setPrompts([]);
            setTotal(0);
            return;
        }

        try {
            // 静默模式不显示loading状态
            if (!isBackground) {
                setLoading(true);
            }

            const response = await botService.getPromptHistory(
                apiKeyId,
                1000,
                actionFilter !== 'all' ? actionFilter : undefined,
                modelFilter !== 'all' ? modelFilter : undefined,
                callSourceFilter !== 'all' ? callSourceFilter : undefined,
                openCloseFilter !== 'all' ? openCloseFilter : undefined,
                currentPage,
                pageSize
            );

            if (response.success && response.data) {
                setPrompts(response.data);
                setTotal(typeof (response as any).total === 'number' ? (response as any).total : response.data.length);

                // 提取所有可用的模型名称
                const models = Array.from(new Set(response.data.map(p => p.modelName).filter(Boolean)));
                setAvailableModels(models);
            } else {
                // 静默模式下不显示错误消息,避免打扰用户
                if (!isBackground) {
                    message.error(response.message || '获取prompt历史失败');
                }
                setPrompts([]);
                setTotal(0);
            }
        } catch (error) {
            console.error('获取prompt历史失败:', error);
            // 静默模式下不显示错误消息
            if (!isBackground) {
                message.error('获取prompt历史失败');
            }
            setPrompts([]);
            setTotal(0);
        } finally {
            // 静默模式下不重置loading状态
            if (!isBackground) {
                setLoading(false);
            }
        }
    };

    // 过滤和搜索prompts
    const getFilteredPrompts = () => {
        let filtered = prompts;

        // 状态过滤
        if (statusFilter !== 'all') {
            filtered = filtered.filter(prompt => prompt.status === statusFilter);
        }

        // 文本搜索
        if (searchText) {
            const lowerSearchText = searchText.toLowerCase();
            filtered = filtered.filter(prompt =>
                prompt.promptContent?.toLowerCase().includes(lowerSearchText) ||
                prompt.reasoning?.toLowerCase().includes(lowerSearchText) ||
                prompt.instId?.toLowerCase().includes(lowerSearchText) ||
                prompt.action?.toLowerCase().includes(lowerSearchText)
            );
        }

        return filtered;
    };

    // 当前页数据直接来自服务端，前端仅进行轻量过滤显示
    const getCurrentPagePrompts = () => {
        return getFilteredPrompts();
    };

    // 刷新数据
    const handleRefresh = () => {
        setCurrentPage(1);
        fetchPromptHistory(false); // 手动刷新时显示loading
    };

    /**
     * 切换面板折叠/展开状态
     * 折叠时停止定时刷新，展开时恢复之前的刷新状态
     */
    const handleToggleCollapse = () => {
        if (!collapsed) {
            // 准备折叠时，保存当前刷新状态并停止刷新
            setPrevAutoRefreshEnabled(autoRefreshEnabled);
            setAutoRefreshEnabled(false);
        } else {
            // 展开时，恢复之前的刷新状态
            setAutoRefreshEnabled(prevAutoRefreshEnabled);
        }
        setCollapsed(!collapsed);
    };

    // 调用大模型
    // const handleCallModel = async (decisionId: string) => {
    //     try {
    //         // 这里可以调用后端API来处理未完成的prompt
    //         message.info('调用大模型功能开发中...');
    //     } catch (error) {
    //         console.error('调用大模型失败:', error);
    //         message.error('调用大模型失败');
    //     }
    // };

    // 页面变化
    const handlePageChange = (page: number) => {
        setCurrentPage(page);
    };

    // 重置过滤器
    const handleResetFilters = () => {
        setSearchText('');
        setStatusFilter('all');
        setActionFilter('all');
        setModelFilter('all');
        setCallSourceFilter('all');
        setOpenCloseFilter('all');
        setCurrentPage(1);
    };

    // 处理会话链路点击,打开会话链路弹窗
    const handleParentIdClick = (recordId: number) => {
        onOpenChainModal?.(recordId);
    };

    // 关闭会话链路弹窗
    const handleChainModalClose = () => {
        onCloseChainModal?.();
    };

    // 计算过滤后的总数
    const filteredPrompts = getFilteredPrompts();
    const filteredTotal = filteredPrompts.length;

    // 计算是否有任何项目处于展开状态
    const hasAnyExpandedItem = Object.values(expandedItems).some(state => 
        state.promptContent || state.aiResponse
    );

    // 清理定时器
    useEffect(() => {
        return () => {
            if (refreshInterval) {
                clearInterval(refreshInterval);
            }
        };
    }, [refreshInterval]);

    // 自动刷新逻辑
    // 优化:使用静默模式拉取数据,避免loading闪烁和错误提示打扰用户
    useEffect(() => {
        // 只有在自动刷新开启、选择了apiKeyId、面板未折叠且没有展开的项目时才启动定时器
        if (autoRefreshEnabled && apiKeyId && !collapsed && !hasAnyExpandedItem) {
            // 设置每30秒刷新一次
            const interval = setInterval(() => {
                // 静默模式:不显示loading,不弹错误提示,成功后才更新数据
                fetchPromptHistory(true);
            }, 30000);
            setRefreshInterval(interval);
        } else {
            // 清除定时器
            if (refreshInterval) {
                clearInterval(refreshInterval);
                setRefreshInterval(null);
            }
        }
    }, [autoRefreshEnabled, apiKeyId, collapsed, actionFilter, modelFilter, callSourceFilter, openCloseFilter, hasAnyExpandedItem]);

    // 初始化和刷新时加载数据
    // 注意:这里使用非静默模式,因为用户主动切换筛选条件时应该有反馈
    useEffect(() => {
        if (apiKeyId) {
            fetchPromptHistory(false);
        }
    }, [apiKeyId, refreshTrigger, actionFilter, modelFilter, callSourceFilter, openCloseFilter, currentPage]);

    // 重置页面当过滤器变化
    useEffect(() => {
        setCurrentPage(1);
    }, [searchText, statusFilter]);

    return (
        <>
            <Card
                title={
                    <Space>
                        <HistoryOutlined style={{color: '#1677ff'}}/>
                        <span>Prompt历史记录</span>
                        <Text style={{fontSize: '12px', color: 'rgba(255, 255, 255, 0.6)'}}>
                            ({total} 条记录)
                        </Text>
                    </Space>
                }
                extra={
                    <Space>
                        <Tooltip title={autoRefreshEnabled ? "关闭自动刷新" : "开启自动刷新"}>
                            <Button
                                type="text"
                                icon={autoRefreshEnabled ? <PauseOutlined/> : <PlayCircleOutlined/>}
                                onClick={() => setAutoRefreshEnabled(!autoRefreshEnabled)}
                                style={{color: autoRefreshEnabled ? 'rgba(255, 193, 7, 0.8)' : 'rgba(255, 255, 255, 0.6)'}}
                            />
                        </Tooltip>
                        <Tooltip title="手动刷新数据">
                            <Button
                                type="text"
                                icon={<ReloadOutlined/>}
                                onClick={handleRefresh}
                                loading={loading}
                                style={{color: 'rgba(255, 255, 255, 0.6)'}}
                            />
                        </Tooltip>
                        <Tooltip title={collapsed ? "展开面板" : "折叠面板"}>
                            <Button
                                type="text"
                                icon={collapsed ? <DownOutlined/> : <UpOutlined/>}
                                onClick={handleToggleCollapse}
                                style={{color: collapsed ? 'rgba(255, 255, 255, 0.6)' : '#1677ff'}}
                            />
                        </Tooltip>
                    </Space>
                }
                style={{
                    height: collapsed ? 'auto' : '100%',
                    maxHeight: collapsed ? 'auto' : '78vh',
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343',
                    borderRadius: '8px',
                    display: 'flex',
                    flexDirection: 'column'
                }}
                styles={{
                    header: {
                        backgroundColor: '#1a1a1a',
                        borderBottom: '1px solid #434343',
                        color: 'rgba(255, 255, 255, 0.95)'
                    },
                    body: collapsed ? {
                        height: '30px',
                        minHeight: '30px',
                        padding: 0,
                        overflow: 'hidden'
                    } : {
                        padding: '16px',
                        flex: 1,
                        display: 'flex',
                        flexDirection: 'column',
                        overflow: 'hidden'
                    }
                }}
            >
                {/* 面板内容：折叠时隐藏，展开时显示 */}
                {!collapsed && (
                    <>
                        {/* 搜索和过滤器 */}
                {/* 搜索和过滤器 */}
                <div style={{marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '12px'}}>
                    {/* 第一行：搜索框、状态、决策 */}
                    <div style={{display: 'flex', gap: '12px', alignItems: 'center'}}>
                        <Search
                            placeholder="搜索prompt内容、决策推理、思考过程、合约代码、模型名称、调用来源..."
                            value={searchText}
                            onChange={(e) => setSearchText(e.target.value)}
                            style={{width: 320}}
                            allowClear
                        />

                        <Select
                            value={statusFilter}
                            onChange={setStatusFilter}
                            style={{width: 120}}
                            placeholder="状态筛选"
                        >
                            <Option value="all">全部状态</Option>
                            <Option value="SUCCESS">
                                <Space>
                                    <CheckCircleOutlined style={{color: '#52c41a'}}/>
                                    成功
                                </Space>
                            </Option>
                            <Option value="PROCESSING">
                                <Space>
                                    <RobotOutlined style={{color: '#1677ff'}}/>
                                    处理中
                                </Space>
                            </Option>
                            <Option value="FAILED">
                                <Space>
                                    <ExclamationCircleOutlined style={{color: '#ff4d4f'}}/>
                                    失败
                                </Space>
                            </Option>
                        </Select>

                        <Select
                            value={actionFilter}
                            onChange={(value) => {
                                setActionFilter(value);
                                setCurrentPage(1);
                            }}
                            style={{width: 140}}
                            placeholder="决策筛选"
                        >
                            <Option value="all">全部决策</Option>
                            <Option value="SELL">
                                <Space>
                                    <span>📉</span>
                                    卖出
                                </Space>
                            </Option>
                            <Option value="BUY">
                                <Space>
                                    <span>📈</span>
                                    买入
                                </Space>
                            </Option>
                            <Option value="HOLD">
                                <Space>
                                    <span>⏸️</span>
                                    持有
                                </Space>
                            </Option>
                            <Option value="ATTENTION">
                                <Space>
                                    <span>⚠️</span>
                                    关注
                                </Space>
                            </Option>
                            <Option value="CANCEL_ORDER">
                                <Space>
                                    <span>❌</span>
                                    取消订单
                                </Space>
                            </Option>
                            <Option value="QUERY">
                                <Space>
                                    <span>🔍</span>
                                    查询
                                </Space>
                            </Option>
                        </Select>
                    </div>

                    {/* 第二行：模型、来源、开/平仓 */}
                    <div style={{display: 'flex', gap: '12px', alignItems: 'center'}}>
                        <Select
                            value={modelFilter}
                            onChange={(value) => {
                                setModelFilter(value);
                                setCurrentPage(1);
                            }}
                            style={{width: 140}}
                            placeholder="模型筛选"
                        >
                            <Option value="all">全部模型</Option>
                            {availableModels.map(model => (
                                <Option key={model} value={model}>
                                    {model}
                                </Option>
                            ))}
                        </Select>

                        <Select
                            value={callSourceFilter}
                            onChange={(value) => {
                                setCallSourceFilter(value);
                                setCurrentPage(1);
                            }}
                            style={{width: 140}}
                            placeholder="调用来源"
                        >
                            <Option value="all">全部来源</Option>
                            <Option value="SCHEDULED">
                                <Space>
                                    <span>⏰</span>
                                    AI
                                </Space>
                            </Option>
                            <Option value="MANUAL">
                                <Space>
                                    <span>👆</span>
                                    HUM
                                </Space>
                            </Option>
                            <Option value="DIRECT">
                                <Space>
                                    <span>🔗</span>
                                    BOT
                                </Space>
                            </Option>
                        </Select>

                        <Select
                            value={openCloseFilter}
                            onChange={(value) => {
                                setOpenCloseFilter(value);
                                setCurrentPage(1);
                            }}
                            style={{width: 120}}
                            placeholder="开平仓"
                        >
                            <Option value="all">全部</Option>
                            <Option value="OPEN">开仓</Option>
                            <Option value="CLOSE">平仓</Option>
                        </Select>

                        {(searchText || statusFilter !== 'all' || actionFilter !== 'all' || modelFilter !== 'all' || callSourceFilter !== 'all' || openCloseFilter !== 'all') && (
                            <Button size="small" onClick={handleResetFilters}>
                                重置筛选
                            </Button>
                        )}
                    </div>
                </div>

                {/* Prompt列表 */}
                <div style={{
                    flex: 1,
                    overflowY: 'auto',
                    paddingRight: '4px' // 滚动条的空间
                }}>
                    {loading ? (
                        <div style={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '200px'
                        }}>
                            <Spin size="large">
                                <div style={{marginTop: 8, color: '#999'}}>加载中...</div>
                            </Spin>
                        </div>
                    ) : !apiKeyId ? (
                        <Empty
                            description="请先选择API Key"
                            style={{marginTop: '60px'}}
                        />
                    ) : getCurrentPagePrompts().length === 0 ? (
                        <Empty
                            description={searchText || statusFilter !== 'all' ? '没有找到匹配的记录' : '暂无prompt历史记录'}
                            style={{marginTop: '60px'}}
                        />
                    ) : (
                        <div>
                            {getCurrentPagePrompts().map((prompt) => (
                                <PromptItem
                                    key={prompt.decisionId}
                                    item={prompt}
                                    // onCallModel={handleCallModel}
                                    expandedState={getCompleteExpandedState(prompt.decisionId)}
                                    onExpandedStateChange={(key, value) =>
                                        updateItemExpandedState(prompt.decisionId, key, value)
                                    }
                                    onParentIdClick={handleParentIdClick}
                                />
                            ))}
                        </div>
                    )}
                </div>

                {/* 分页 */}
                {filteredTotal > 0 && (
                    <div style={{
                        marginTop: '16px',
                        display: 'flex',
                        justifyContent: 'center',
                        paddingTop: '16px',
                        borderTop: '1px solid #434343'
                    }}>
                        <Pagination
                            current={currentPage}
                            total={total}
                            pageSize={pageSize}
                            onChange={handlePageChange}
                            showSizeChanger={false}
                            showQuickJumper
                            showTotal={(total, range) =>
                                `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`
                            }
                            style={{
                                color: 'rgba(255, 255, 255, 0.88)'
                            }}
                        />
                    </div>
                )}
                    </>
                )}
            </Card>

            {/* 会话链路弹窗 */}
            <ConversationChainModal
                visible={!!chainModalVisible}
                onClose={handleChainModalClose}
                recordId={selectedRecordId || null}
                onTransfer={onTransferToChat}
            />
        </>
    );
};

export default PromptHistory;
