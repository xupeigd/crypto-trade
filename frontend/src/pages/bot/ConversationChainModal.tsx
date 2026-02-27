import React, {useEffect, useState} from 'react';
import {Empty, message, Modal, Space, Spin, Tag, Tooltip, Typography} from 'antd';
import {BranchesOutlined, MessageOutlined, ArrowLeftOutlined, ExportOutlined} from '@ant-design/icons';
import {botService, PromptHistoryResponse} from '../../services/botService';
import chatService from '../../services/chatService';
import {ChatMessage} from '../../types/chat';
import PromptItem from './PromptItem';
import ChatMessageComponent from '../../components/chat/ChatMessage';

const {Text} = Typography;

/**
 * 视图模式
 */
type ViewMode = 'chain' | 'session';

/**
 * 单个item的展开状态接口
 * 从PromptItem复制,保持一致
 */
interface ItemExpandedState {
    promptContent: boolean; // Prompt内容是否展开
    aiResponse: boolean;   // AI模型响应是否展开
}

interface ConversationChainModalProps {
    visible: boolean;
    onClose: () => void;
    recordId: number | null;
    onTransfer?: (sessionId: number, userId: string) => void; // 转移到AI助手的回调
}

/**
 * 会话链路弹窗组件
 * 展示完整的会话链路(从最顶层父级到当前记录)
 * 支持切换到会话消息视图
 */
const ConversationChainModal: React.FC<ConversationChainModalProps> = ({
                                                                           visible,
                                                                           onClose,
                                                                           recordId,
                                                                           onTransfer
                                                                       }) => {
    const [chainData, setChainData] = useState<PromptHistoryResponse[]>([]);
    const [loading, setLoading] = useState(false);

    // 视图模式：chain(会话链路) | session(会话消息)
    const [viewMode, setViewMode] = useState<ViewMode>('chain');
    const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
    const [currentUserId, setCurrentUserId] = useState<string>('default');
    const [sessionMessages, setSessionMessages] = useState<ChatMessage[]>([]);
    const [sessionLoading, setSessionLoading] = useState(false);

    // 管理每个会话记录的展开状态
    const [expandedItems, setExpandedItems] = useState<Record<string, ItemExpandedState>>({});

    /**
     * 更新指定item的展开状态
     * @param itemId 项目ID
     * @param key 状态key
     * @param value 是否展开
     */
    const updateItemExpandedState = (itemId: string, key: keyof ItemExpandedState, value: boolean) => {
        setExpandedItems(prev => {
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
     */
    const getCompleteExpandedState = (itemId: string): ItemExpandedState => {
        const existingState = expandedItems[itemId];

        if (existingState &&
            typeof existingState.promptContent === 'boolean' &&
            typeof existingState.aiResponse === 'boolean') {
            return existingState;
        }

        return {
            promptContent: existingState?.promptContent || false,
            aiResponse: existingState?.aiResponse || false
        };
    };

    /**
     * 加载会话链路数据
     */
    const loadConversationChain = async () => {
        if (!recordId) {
            return;
        }

        try {
            setLoading(true);
            const response = await botService.getConversationChain(recordId);

            if (response.success && response.data) {
                setChainData(response.data);
            } else {
                message.error(response.message || '获取会话链路失败');
                setChainData([]);
            }
        } catch (error) {
            console.error('获取会话链路失败:', error);
            message.error('获取会话链路失败');
            setChainData([]);
        } finally {
            setLoading(false);
        }
    };

    /**
     * 切换到会话消息视图
     * @param sessionId 会话ID
     */
    const switchToSessionView = async (sessionId: number) => {
        setCurrentSessionId(sessionId);
        setViewMode('session');
        loadSessionMessages(sessionId);
    };

    /**
     * 切换回会话链路视图
     */
    const switchToChainView = () => {
        setViewMode('chain');
        setCurrentSessionId(null);
        setSessionMessages([]);
    };

    /**
     * 加载会话消息
     */
    const loadSessionMessages = async (sessionId: number) => {
        try {
            setSessionLoading(true);
            const messages = await chatService.getSessionMessages(sessionId);
            setSessionMessages(messages);
            
            // 从消息中提取userId（如果消息中有userId字段）
            if (messages.length > 0 && messages[0].userId) {
                setCurrentUserId(messages[0].userId);
            } else {
                setCurrentUserId('default');
            }
        } catch (error) {
            console.error('获取会话消息失败:', error);
            message.error('获取会话消息失败');
            setSessionMessages([]);
            setCurrentUserId('default');
        } finally {
            setSessionLoading(false);
        }
    };

    /**
     * 处理转移到AI助手
     */
    const handleTransferToChat = () => {
        if (currentSessionId && onTransfer) {
            onTransfer(currentSessionId, currentUserId);
            onClose(); // 关闭弹窗
        } else {
            message.warning('没有可转移的会话');
        }
    };

    // 当弹窗打开时加载数据
    useEffect(() => {
        if (visible && recordId) {
            loadConversationChain();
        }
    }, [visible, recordId]);

    // 当弹窗关闭时清空数据
    useEffect(() => {
        if (!visible) {
            setChainData([]);
            setExpandedItems({});
            setViewMode('chain');
            setCurrentSessionId(null);
            setSessionMessages([]);
        }
    }, [visible]);

    return (
        <Modal
            title={
                <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    width: '100%',
                    paddingRight: '40px'  // 为关闭图标留出空间
                }}>
                    {viewMode === 'chain' ? (
                        <Space>
                            <BranchesOutlined style={{color: '#9254de'}}/>
                            <span>会话链路</span>
                            {chainData.length > 0 && (
                                <Text style={{fontSize: '12px', color: 'rgba(255, 255, 255, 0.65)'}}>
                                    {chainData.length} 条记录
                                </Text>
                            )}
                        </Space>
                    ) : (
                        <Space>
                            <Tooltip title="返回会话链路">
                                <span
                                    style={{
                                        cursor: 'pointer',
                                        fontSize: '16px',
                                        color: '#1890ff',
                                        display: 'flex',
                                        alignItems: 'center'
                                    }}
                                    onClick={switchToChainView}
                                >
                                    <ArrowLeftOutlined />
                                </span>
                            </Tooltip>
                            <MessageOutlined style={{color: '#52c41a'}}/>
                            <span>会话消息</span>
                            {sessionMessages.length > 0 && (
                                <Text style={{fontSize: '12px', color: 'rgba(255, 255, 255, 0.65)'}}>
                                    {sessionMessages.length} 条消息
                                </Text>
                            )}
                        </Space>
                    )}

                    <Space>
                        {/* 会话图标按钮 - 仅在链路模式下显示 */}
                        {viewMode === 'chain' && chainData.length > 0 && (
                            <Tooltip title="查看会话消息">
                                <span
                                    style={{
                                        cursor: 'pointer',
                                        fontSize: '16px',
                                        color: '#ffffff',
                                        display: 'flex',
                                        alignItems: 'center'
                                    }}
                                    onClick={() => {
                                        // 获取当前记录的sessionId，如果没有则取第一条记录的sessionId
                                        const targetItem = chainData.find(item => item.decisionId === String(recordId));
                                        const sessionId = targetItem?.chatSessionId || chainData[0]?.chatSessionId;
                                        if (sessionId) {
                                            switchToSessionView(sessionId);
                                        } else {
                                            message.warning('该记录没有关联的会话消息');
                                        }
                                    }}
                                >
                                    <MessageOutlined />
                                </span>
                            </Tooltip>
                        )}

                        {/* 转移按钮 - 仅在会话消息模式下显示 */}
                        {viewMode === 'session' && currentSessionId && (
                            <Tooltip title="转移到AI助手">
                                <span
                                    style={{
                                        cursor: 'pointer',
                                        fontSize: '16px',
                                        color: '#ffffff',
                                        display: 'flex',
                                        alignItems: 'center'
                                    }}
                                    onClick={handleTransferToChat}
                                >
                                    <ExportOutlined />
                                </span>
                            </Tooltip>
                        )}
                    </Space>
                </div>
            }
            open={visible}
            onCancel={onClose}
            width="90%"
            footer={null}
            style={{
                backgroundColor: 'transparent', // 移除Modal外层的背景色，由content控制
                paddingBottom: 0
            }}
            styles={{
                content: {
                    backgroundColor: '#1f1f1f',
                    borderRadius: '16px', // 增加圆角
                    border: '1px solid #303030', // 增加边框
                    padding: 0,
                    overflow: 'hidden' // 确保圆角生效
                },
                header: {
                    backgroundColor: '#1f1f1f',
                    padding: '16px 24px',
                    borderBottom: '1px solid #303030',
                    marginBottom: 0
                },
                body: {
                    backgroundColor: '#1f1f1f',
                    maxHeight: '75vh',
                    overflowY: 'auto',
                    padding: '24px' // 增加内边距
                }
            }}
        >
            {viewMode === 'chain' ? (
                // 会话链路视图
                loading ? (
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
                ) : chainData.length === 0 ? (
                    <Empty
                        description="暂无会话链路数据"
                        style={{marginTop: '60px'}}
                    />
                ) : (
                    <div>
                        {chainData.map((item, index) => (
                            <div key={item.decisionId} style={{marginBottom: '16px'}}>
                                {/* 序号标识 */}
                                <div style={{
                                    marginBottom: '8px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '8px'
                                }}>
                                    <Tag
                                        color={index === chainData.length - 1 ? 'blue' : 'default'}
                                        style={{margin: 0}}
                                    >
                                        #{index + 1}
                                    </Tag>
                                    {index === chainData.length - 1 && (
                                        <Text style={{fontSize: '12px', color: 'rgba(255, 255, 255, 0.6)'}}>
                                            (当前记录)
                                        </Text>
                                    )}
                                </div>

                                {/* 使用PromptItem组件展示 */}
                                <PromptItem
                                    item={item}
                                    expandedState={getCompleteExpandedState(item.decisionId)}
                                    onExpandedStateChange={(key, value) =>
                                        updateItemExpandedState(item.decisionId, key, value)
                                    }
                                    // 在弹窗中不显示parentId点击,避免嵌套弹窗
                                    onParentIdClick={undefined}
                                />
                            </div>
                        ))}
                    </div>
                )
            ) : (
                // 会话消息视图
                sessionLoading ? (
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '200px'
                    }}>
                        <Spin size="large">
                            <div style={{marginTop: 8, color: '#999'}}>加载消息中...</div>
                        </Spin>
                    </div>
                ) : sessionMessages.length === 0 ? (
                    <Empty
                        description="暂无会话消息"
                        style={{marginTop: '60px'}}
                    />
                ) : (
                    <div>
                        {sessionMessages.map((msg) => (
                            <ChatMessageComponent key={msg.messageId} message={msg} />
                        ))}
                    </div>
                )
            )}
        </Modal>
    );
};

export default ConversationChainModal;