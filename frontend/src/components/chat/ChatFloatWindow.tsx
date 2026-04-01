import React, {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {Drawer, Dropdown, Tooltip, Typography} from 'antd';
import {CopyOutlined, DeleteOutlined, DownOutlined, HistoryOutlined, PlusOutlined, ToolOutlined, UpOutlined} from '@ant-design/icons';
import {useChat} from '../../hooks/useChat';
import MarkdownRenderer from '../common/MarkdownRenderer';
import dayjs from 'dayjs';

// console.log('ChatFloatWindow component loaded');

const {Title, Text} = Typography;

// 可折叠的消息内容组件 - 定义在组件外部避免状态丢失
const CollapsibleMessageContent: React.FC<{
    children: React.ReactNode;
    role: 'user' | 'assistant';
    isExpanded: boolean;
    onToggleExpand: () => void;
}> = ({children, role, isExpanded, onToggleExpand}) => {
    const [isHovered, setIsHovered] = useState(false);
    const [copied, setCopied] = useState(false);
    const [contentLength, setContentLength] = useState(0);
    const contentRef = useRef<HTMLDivElement>(null);

    // 折叠阈值配置
    const COLLAPSE_THRESHOLD = 120;
    const MAX_HEIGHT = 100; // 折叠时的最大高度

    // 使用useLayoutEffect在内容挂载后计算长度，避免每次渲染都访问DOM
    useLayoutEffect(() => {
        if (contentRef.current) {
            const length = contentRef.current.innerText.length;
            setContentLength(length);
        }
    }, [children]);

    // 判断是否需要显示折叠按钮
    const shouldShowCollapseButton = contentLength > COLLAPSE_THRESHOLD;

    // 处理复制功能
    const handleCopy = async () => {
        try {
            if (contentRef.current) {
                const text = contentRef.current.innerText;
                await navigator.clipboard.writeText(text);
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
            }
        } catch (error) {
            console.error('复制失败:', error);
        }
    };

    return (
        <div
            style={{position: 'relative'}}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
        >
            {/* 复制按钮（hover时显示） */}
            {isHovered && (
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        handleCopy();
                    }}
                    style={{
                        position: 'absolute',
                        top: '1px',
                        right: '32px',
                        padding: '4px',
                        fontSize: '14px',
                        lineHeight: '1',
                        height: '24px',
                        width: '24px',
                        backgroundColor: 'transparent',
                        border: 'none',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        zIndex: 100,
                        transition: 'all 0.2s ease',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                    }}
                    title={copied ? '已复制' : '复制'}
                >
                    <CopyOutlined style={{
                        fontSize: '14px',
                        color: copied ? '#52c41a' : '#ffffff'
                    }}/>
                </button>
            )}

            {/* 展开/折叠按钮（只在内容超过120字符时显示） */}
            {shouldShowCollapseButton && (
                <Tooltip title={isExpanded ? '收起' : '展开全文'} placement="top">
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            onToggleExpand();
                        }}
                        style={{
                            position: 'absolute',
                            top: '1px',
                            right: '12px',
                            padding: '4px',
                            fontSize: '14px',
                            lineHeight: '1',
                            height: '24px',
                            width: '24px',
                            backgroundColor: 'transparent',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            zIndex: 100,
                            transition: 'all 0.2s ease',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}
                    >
                        {isExpanded ? (
                            <UpOutlined style={{
                                fontSize: '14px',
                                color: '#1890ff'
                            }}/>
                        ) : (
                            <DownOutlined style={{
                                fontSize: '14px',
                                color: '#ffffff'
                            }}/>
                        )}
                    </button>
                </Tooltip>
            )}

            {/* 消息内容容器 */}
            <div
                ref={contentRef}
                style={{
                    maxHeight: isExpanded ? 'none' : (shouldShowCollapseButton ? `${MAX_HEIGHT}px` : 'none'),
                    overflow: isExpanded ? 'visible' : (shouldShowCollapseButton ? 'hidden' : 'visible'),
                    transition: 'max-height 0.3s ease',
                    position: 'relative',
                    wordBreak: 'break-word',
                    paddingRight: shouldShowCollapseButton ? '30px' : '0'
                }}
            >
                {children}

                {/* 渐变遮罩（仅在折叠时显示） */}
                {shouldShowCollapseButton && !isExpanded && (
                    <div
                        style={{
                            position: 'absolute',
                            bottom: 0,
                            left: 0,
                            right: 0,
                            height: '60px',
                            background: role === 'user'
                                ? 'linear-gradient(transparent, rgba(24, 144, 255, 0.3))'
                                : 'linear-gradient(transparent, #2a2a2a)',
                            pointerEvents: 'none'
                        }}
                    />
                )}
            </div>
        </div>
    );
};

const ChatFloatWindow: React.FC = () => {
    const [isOpen, setIsOpen] = useState(false);
    const [chatError, setChatError] = useState<string | null>(null);
    const [expandedThinking, setExpandedThinking] = useState<Set<number>>(new Set());
    const [expandedMessages, setExpandedMessages] = useState<Set<number>>(new Set());
    const [expandedChartMessages, setExpandedChartMessages] = useState<Set<number>>(new Set()); // 图表扩展模式的消息
    const [showHistoryDropdown, setShowHistoryDropdown] = useState(false);
    const [sessionsWithMessages, setSessionsWithMessages] = useState<Set<number>>(new Set());
    const [isTransferred, setIsTransferred] = useState(false); // 标识是否通过会话转移打开
    const [tempSystemPrompt, setTempSystemPrompt] = useState<string | null>(null); // 临时预设（从智能体配置传入）
    const [tempAgentName, setTempAgentName] = useState<string | null>(null); // 临时智能体名称（从智能体配置传入）
    const [shouldCreateNewSession, setShouldCreateNewSession] = useState(false); // 关闭后再次打开时是否创建新会话
    const messagesEndRef = useRef<HTMLDivElement>(null);

    // 添加动画样式
    useEffect(() => {
        const style = document.createElement('style');
        style.textContent = `
      @keyframes pulse {
        0%, 100% { opacity: 0.3; transform: scale(0.8); }
        50% { opacity: 1; transform: scale(1); }
      }
      @keyframes slideDown {
        0% { opacity: 0; transform: translateY(-10px); max-height: 0; }
        100% { opacity: 1; transform: translateY(0); max-height: 300px; }
      }
      @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }
      .thinking-content {
        color: rgba(255, 255, 255, 0.45);
        font-style: italic;
        font-size: 0.9em;
        border-left: 2px solid rgba(255, 255, 255, 0.2);
        padding-left: 12px;
        margin: 8px 0;
      }
      .actual-content {
        color: #ffffff;
        margin-top: 8px;
      }
    `;
        document.head.appendChild(style);
        return () => {
            document.head.removeChild(style);
        };
    }, []);

    // 创建一个简化的fallback hook，避免渲染失败
    const fallbackChatHook = {
        sessions: [],
        currentSession: null,
        messages: [],
        isLoading: false,
        error: chatError,
        sendMessage: async () => {
            throw new Error('Chat功能不可用');
        },
        createSession: async () => {
            throw new Error('Chat功能不可用');
        },
        switchSession: async () => {
            throw new Error('Chat功能不可用');
        },
        loadSessionById: async () => {
            throw new Error('Chat功能不可用');
        },
        loadSessions: async () => {
            throw new Error('Chat功能不可用');
        },
        deleteSession: async () => {
            throw new Error('Chat功能不可用');
        },
        updateSessionName: async () => {
            throw new Error('Chat功能不可用');
        },
        clearError: () => setChatError(null)
    };

    // 使用useChat hook，使用useState来管理错误状态
    const chatHook = useChat();

    // 如果hook出错，使用fallback - 但优先使用真实的hook
    if (chatError) {
        console.log('Chat error detected:', chatError);
    }

    console.log('=== ChatFloatWindow Hook Debug ===');
    console.log('chatHook:', chatHook);
    console.log('chatHook?.sendMessage:', typeof chatHook?.sendMessage);

    // 安全地获取chat数据 - 优先使用真实hook，只在真正无法使用时才fallback
    const sessions = chatHook?.sessions || [];
    const currentSession = chatHook?.currentSession || null;
    const messages = chatHook?.messages || [];
    // 预处理：过滤掉 function_call 消息，用于显示
    const displayMessages = useMemo(() => {
        return messages.filter(msg => !(msg.role === 'assistant' && msg.content.startsWith('[function_call]')));
    }, [messages]);
    const isLoading = chatHook?.isLoading || false;
    const error = chatHook?.error || chatError || null;

    // 只有在hook完全不可用时才使用fallback的sendMessage
    const sendMessage = chatHook?.sendMessage || fallbackChatHook.sendMessage;
    console.log('Final sendMessage function:', typeof sendMessage);
    const createSession = chatHook?.createSession || fallbackChatHook.createSession;
    const switchSession = chatHook?.switchSession || fallbackChatHook.switchSession;
    const loadSessionById = chatHook?.loadSessionById || fallbackChatHook.loadSessionById;
    const loadSessions = chatHook?.loadSessions || fallbackChatHook.loadSessions;
    const deleteSession = chatHook?.deleteSession || fallbackChatHook.deleteSession;
    const updateSessionName = chatHook?.updateSessionName || fallbackChatHook.updateSessionName;
    const clearError = chatHook?.clearError || (() => setChatError(null));

    // 辅助函数：获取指定索引处连续tool消息的数量
    const getToolGroupCount = useCallback((index: number): number => {
        if (displayMessages[index]?.role !== 'tool') return 0;
        // 检查是否是组的第一个消息（前面没有tool消息，或者前面不是tool消息）
        if (index > 0 && displayMessages[index - 1]?.role === 'tool') {
            return 0; // 不是组的第一个消息
        }
        // 计算从当前索引开始的连续tool消息数量
        let count = 0;
        let idx = index;
        while (idx < displayMessages.length && displayMessages[idx]?.role === 'tool') {
            count++;
            idx++;
        }
        return count;
    }, [displayMessages]);

    // 辅助函数：切换tool消息组的展开状态
    const toggleToolGroupExpand = useCallback((index: number) => {
        // 找到当前tool消息组的起始位置
        let startIdx = index;
        while (startIdx > 0 && displayMessages[startIdx - 1]?.role === 'tool') {
            startIdx--;
        }
        // 计算组内所有消息的索引
        const groupIndices: number[] = [];
        let idx = startIdx;
        while (idx < displayMessages.length && displayMessages[idx]?.role === 'tool') {
            groupIndices.push(idx);
            idx++;
        }
        // 检查是否已展开（组内任意一条消息展开即视为展开）
        const isAnyExpanded = groupIndices.some(i => expandedMessages.has(i));
        // 切换整个组的展开状态
        setExpandedMessages(prev => {
            const newSet = new Set(prev);
            if (isAnyExpanded) {
                // 收起：删除组内所有索引
                groupIndices.forEach(i => newSet.delete(i));
            } else {
                // 展开：添加组内所有索引
                groupIndices.forEach(i => newSet.add(i));
            }
            return newSet;
        });
    }, [displayMessages, expandedMessages]);

    // 当有消息时，记录该会话为有消息的会话
    useEffect(() => {
        if (currentSession && messages.length > 0) {
            setSessionsWithMessages(prev => new Set(prev).add(currentSession.sessionId));
        }
    }, [currentSession, messages.length]);

    // 当抽屉重新打开时，如果是智能体会话则切换到默认会话
    useEffect(() => {
        if (isOpen && !isTransferred) {
            const restoreDefaultSession = async () => {
                try {
                    // 清除保存的会话
                    localStorage.removeItem('currentChatSession');

                    // 等待sessions加载完成
                    if (sessions.length === 0) {
                        return;
                    }

                    // 检查当前会话是否是智能体会话
                    const isAgentSession = currentSession?.sessionName?.includes('智能体');

                    // 如果当前是智能体会话，才切换到默认会话
                    if (isAgentSession) {
                        const defaultSessions = sessions.filter(s => s.userId === 'default');
                        if (defaultSessions.length > 0 && switchSession) {
                            await switchSession(defaultSessions[0]);
                        }
                    }
                } catch (error) {
                    console.error('恢复default会话失败:', error);
                }
            };

            restoreDefaultSession();
        }
    }, [isOpen, isTransferred, sessions, currentSession, switchSession]);

    // 当shouldCreateNewSession为true且Chat打开时，创建新的默认会话
    useEffect(() => {
        if (isOpen && !isTransferred && shouldCreateNewSession) {
            const createNewDefaultSession = async () => {
                try {
                    localStorage.removeItem('currentChatSession');
                    const newSession = await createSession(undefined, 'default');
                    if (newSession && loadSessionById) {
                        await loadSessionById(newSession.sessionId, 'default');
                    }
                } catch (error) {
                    console.error('创建默认会话失败:', error);
                }
            };
            createNewDefaultSession();
            setShouldCreateNewSession(false);
        }
    }, [isOpen, isTransferred, shouldCreateNewSession, createSession, loadSessionById]);

    // 解析AI消息内容，将思考过程和实际回复分开
    const parseAIContent = (content: string) => {
        // 处理HTML编码的情况，先解码
        let decodedContent = content
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&');

        // 首先处理<thinking>标签（这是正常的思考过程标签）
        const thinkingRegex = /<thinking>([\s\S]*?)<\/thinking>/i;
        const thinkingMatch = decodedContent.match(thinkingRegex);

        if (thinkingMatch) {
            const thinkContent = thinkingMatch[1].trim();
            let actualContent = decodedContent.replace(thinkingMatch[0], '').trim();
            // 去掉可能存在的<think>标签
            actualContent = actualContent
                .replace(/<think>/gi, '')
                .replace(/<\/think>/gi, '')
                .trim();
            // 将=== {title} ===替换为## {title}
            const formattedContent = actualContent.replace(/===\s*(.+?)\s*===/g, '## $1');

            return {
                thinkContent: thinkContent || null,
                actualContent: formattedContent || '抱歉，我暂时无法提供回复。'
            };
        }

        // 然后处理<think>标签（错误的标签，直接去掉）
        if (decodedContent.includes('<think>')) {
            const formattedContent = decodedContent
                .replace(/<think>/gi, '')
                .replace(/<\/think>/gi, '')
                .trim()
                .replace(/===\s*(.+?)\s*===/g, '## $1');

            return {
                thinkContent: null,
                actualContent: formattedContent || '抱歉，我暂时无法提供回复。'
            };
        }

        // 将=== {title} ===替换为## {title}
        const formattedContent = content.replace(/===\s*(.+?)\s*===/g, '## $1');

        return {
            thinkContent: null,
            actualContent: formattedContent
        };
    };

    // 处理发送消息
    const handleSendMessage = async (message: string) => {
        console.log('=== ChatFloatWindow handleSendMessage called ===');
        console.log('Message:', message);
        console.log('sendMessage function:', typeof sendMessage);
        console.log('chatHook?.sendMessage:', !!chatHook?.sendMessage);
        console.log('chatError:', chatError);
        console.log('currentSession:', currentSession);
        console.log('currentSession?.userId:', currentSession?.userId);

        try {
            // 先清除之前的错误状态
            setChatError(null);

            if (sendMessage) {
                console.log('Calling sendMessage function...');
                // 使用当前会话的userId，如果没有则使用默认值
                const userId = currentSession?.userId || 'default';
                console.log('Using userId:', userId);
                // 传递临时预设（如果有）
                await sendMessage(message, userId, tempSystemPrompt || undefined);
                console.log('sendMessage completed successfully');
            } else {
                console.error('sendMessage is not available');
                setChatError('聊天功能暂时不可用');
            }
        } catch (error) {
            console.error('发送消息失败:', error);
            // 只有在不是"Chat功能不可用"错误时才设置错误状态
            if (error instanceof Error && error.message !== 'Chat功能不可用') {
                setChatError('发送消息失败，请稍后重试');
            }
        }
    };

    // 切换思维链展开/折叠状态
    const toggleThinking = (messageIndex: number) => {
        setExpandedThinking(prev => {
            const newSet = new Set(prev);
            if (newSet.has(messageIndex)) {
                newSet.delete(messageIndex);
            } else {
                newSet.add(messageIndex);
            }
            return newSet;
        });
        // 展开后滚动到底部
        setTimeout(() => {
            messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
        }, 100);
    };

    // 切换消息内容展开/折叠状态
    const toggleMessageExpand = (messageIndex: number) => {
        setExpandedMessages(prev => {
            const newSet = new Set(prev);
            if (newSet.has(messageIndex)) {
                newSet.delete(messageIndex);
            } else {
                newSet.add(messageIndex);
            }
            return newSet;
        });
    };

    // 切换图表扩展模式
    const toggleChartExpand = (messageIndex: number, isExpanded: boolean) => {
        setExpandedChartMessages(prev => {
            const newSet = new Set(prev);
            if (isExpanded) {
                newSet.add(messageIndex);
            } else {
                newSet.delete(messageIndex);
            }
            return newSet;
        });
    };

    // 自动滚动到底部
    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
    };

    // 当消息或加载状态变化时自动滚动
    useEffect(() => {
        scrollToBottom();
    }, [messages, isLoading]);

    // 监听chat-transfer事件，处理转移到AI助手的请求
    useEffect(() => {
        const handleChatTransfer = (event: Event) => {
            const detail = (event as CustomEvent<any>).detail;
            const sessionId = detail?.sessionId;
            const userId = detail?.userId;

            if (!loadSessionById || !sessionId) return;

            void (async () => {
                try {
                    setIsTransferred(true);
                    await loadSessionById(sessionId, userId || 'default');
                    setIsOpen(true);
                    console.log('Chat transfer completed: session', sessionId, 'userId', userId, 'loaded');
                } catch (error) {
                    console.error('加载会话失败:', error);
                    setIsTransferred(false);
                }
            })();
        };

        // 添加事件监听器
        window.addEventListener('chat-transfer', handleChatTransfer);

        // 清理函数
        return () => {
            window.removeEventListener('chat-transfer', handleChatTransfer);
        };
    }, [loadSessionById]);

    // 监听agent-chat-start事件，处理从智能体配置打开聊天的请求
    useEffect(() => {
        const handleAgentChatStart = (event: Event) => {
            const detail = (event as CustomEvent<any>).detail;
            const agentConfig = detail?.agentConfig;

            if (!agentConfig) return;

            void (async () => {
                try {
                    // 设置临时预设
                    setTempSystemPrompt(agentConfig.systemPrompt || null);
                    setTempAgentName(agentConfig.name || null);
                    setIsTransferred(true);

                    // 创建新会话，传递agentId
                    const newSession = await createSession(agentConfig.name || '智能体对话', 'default', agentConfig.id);

                    // 加载该智能体的会话列表（等待完成）
                    if (loadSessions) {
                        await loadSessions('default', agentConfig.id);
                    }

                    // 使用 switchSession 切换到新会话，确保 currentSession 被正确设置
                    if (newSession && switchSession) {
                        await switchSession(newSession);
                    }

                    setIsOpen(true);
                    console.log('Agent chat started with systemPrompt:', agentConfig.systemPrompt);
                } catch (error) {
                    console.error('启动智能体聊天失败:', error);
                    setTempSystemPrompt(null);
                    setTempAgentName(null);
                    setIsTransferred(false);
                }
            })();
        };

        window.addEventListener('agent-chat-start', handleAgentChatStart);

        return () => {
            window.removeEventListener('agent-chat-start', handleAgentChatStart);
        };
    }, [createSession, loadSessionById]);

    // 获取未读消息数
    const getUnreadCount = () => {
        return sessions ? Math.max(0, sessions.length - 1) : 0;
    };

    return (
        <>

            {/* 浮动按钮 */}
            <div
                style={{
                    position: 'fixed',
                    bottom: 24,
                    right: 90,
                    width: 56,
                    height: 56,
                    background: 'rgb(31, 31, 31)',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    color: '#ffffff',
                    fontSize: '18px',
                    boxShadow: '0 3px 6px -4px rgba(0,0,0,.12), 0 6px 16px 0 rgba(0,0,0,.08), 0 9px 28px 8px rgba(0,0,0,.05)',
                    border: 'none',
                    zIndex: 9999,
                    transition: 'all 0.3s ease'
                }}
                onClick={async () => {
                    console.log('聊天按钮被点击');
                    // 清除智能体相关状态
                    setTempAgentName(null);
                    setTempSystemPrompt(null);
                    setShouldCreateNewSession(false);

                    // 加载默认会话列表（不传agentId，后端返回agentId为null的会话）
                    if (loadSessions) {
                        await loadSessions('default');
                    }

                    // 切换到默认会话，确保 currentSession 被正确设置
                    if (switchSession) {
                        // 从会话列表中找到默认会话（agentId为null/undefined）
                        const defaultSession = sessions.find(s => s.userId === 'default' && !s.agentId);
                        if (defaultSession) {
                            await switchSession(defaultSession);
                        } else {
                            // 如果没有默认会话，创建新会话
                            const newSession = await createSession(undefined, 'default');
                            if (newSession) {
                                await switchSession(newSession);
                            }
                        }
                    }

                    setIsOpen(true);
                }}
                onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'scale(1.05)';
                    e.currentTarget.style.background = 'rgb(51, 51, 51)';
                    e.currentTarget.style.boxShadow = '0 3px 6px -4px rgba(0,0,0,.12), 0 6px 16px 0 rgba(0,0,0,.08), 0 9px 28px 8px rgba(0,0,0,.05)';
                }}
                onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'scale(1)';
                    e.currentTarget.style.background = 'rgb(31, 31, 31)';
                    e.currentTarget.style.boxShadow = '0 3px 6px -4px rgba(0,0,0,.12), 0 6px 16px 0 rgba(0,0,0,.08), 0 9px 28px 8px rgba(0,0,0,.05)';
                }}
                title="AI Chat"
            >
                💬
            </div>

            {/* 浮动聊天窗口 */}
            <Drawer
                title={
                    <div
                        style={{display: 'flex', alignItems: 'center', width: '100%', justifyContent: 'space-between'}}>
                        <span>{tempAgentName || 'AI Chat'}</span>
                        <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                            {/* 新会话按钮 */}
                            <button
                                onClick={async (e) => {
                                    e.stopPropagation();
                                    try {
                                        const newSession = await createSession();
                                        if (newSession) {
                                            await switchSession(newSession);
                                        }
                                    } catch (error) {
                                        console.error('创建会话失败:', error);
                                    }
                                }}
                                style={{
                                    backgroundColor: 'transparent',
                                    border: 'none',
                                    color: '#ffffff',
                                    cursor: 'pointer',
                                    padding: '4px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    transition: 'color 0.2s ease'
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.color = '#1890ff';
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.color = '#ffffff';
                                }}
                                title="新会话"
                            >
                                <PlusOutlined style={{fontSize: '16px'}}/>
                            </button>

                            {/* 历史会话按钮 */}
                            <Dropdown
                                open={showHistoryDropdown}
                                onOpenChange={setShowHistoryDropdown}
                                trigger={['click']}
                                popupRender={() => (
                                    <div style={{
                                        backgroundColor: '#2a2a2a',
                                        border: '1px solid #404040',
                                        borderRadius: '8px',
                                        padding: '8px',
                                        minWidth: '280px',
                                        maxHeight: '400px',
                                        overflowY: 'auto'
                                    }}>
                                        {(() => {
                                            // 获取当前会话的agentId
                                            const currentAgentId = currentSession?.agentId;
                                            const isAgentMode = !!currentAgentId;

                                            // 根据模式过滤会话（前端兜底过滤）
                                            const filteredSessions = sessions.filter(session => {
                                                // 智能体模式下，只显示agentId匹配的会话
                                                if (isAgentMode) {
                                                    return session.agentId === currentAgentId;
                                                }
                                                // 默认模式下，只显示没有agentId的会话
                                                return !session.agentId;
                                            });

                                            // 如果过滤后没有会话，显示提示
                                            if (filteredSessions.length === 0) {
                                                return (
                                                    <div style={{
                                                        color: 'rgba(255, 255, 255, 0.5)',
                                                        padding: '12px 16px',
                                                        textAlign: 'center',
                                                        fontSize: '14px'
                                                    }}>
                                                        {isAgentMode ? '暂无智能体会话' : '暂无历史会话'}
                                                    </div>
                                                );
                                            }

                                            return filteredSessions.map((session) => (
                                                    <div
                                                        key={session.sessionId}
                                                        onClick={() => {
                                                            switchSession(session);
                                                            setShowHistoryDropdown(false);
                                                        }}
                                                        style={{
                                                            padding: '12px 16px',
                                                            color: currentSession?.sessionId === session.sessionId ? '#1890ff' : '#ffffff',
                                                            cursor: 'pointer',
                                                            borderRadius: '4px',
                                                            marginBottom: '4px',
                                                            fontSize: '14px',
                                                            backgroundColor: currentSession?.sessionId === session.sessionId ? 'rgba(24, 144, 255, 0.1)' : 'transparent',
                                                            transition: 'background-color 0.2s ease'
                                                        }}
                                                        onMouseEnter={(e) => {
                                                            if (currentSession?.sessionId !== session.sessionId) {
                                                                e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.05)';
                                                            }
                                                        }}
                                                        onMouseLeave={(e) => {
                                                            if (currentSession?.sessionId !== session.sessionId) {
                                                                e.currentTarget.style.backgroundColor = 'transparent';
                                                            }
                                                        }}
                                                    >
                                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                                                            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', flex: 1, overflow: 'hidden' }}>
                                                                <div style={{ fontSize: '14px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                                    {session.sessionName || `会话 ${session.sessionId}`}
                                                                </div>
                                                                <div style={{ fontSize: '12px', color: 'rgba(255, 255, 255, 0.45)' }}>
                                                                    {dayjs(session.createdTime).format('YY-MM-DD HH:mm')}
                                                                </div>
                                                            </div>
                                                            <DeleteOutlined
                                                                onClick={(e) => {
                                                                    e.stopPropagation();
                                                                    if (window.confirm('确定要删除该会话吗？')) {
                                                                        deleteSession(session.sessionId, session.userId || 'default');
                                                                        if (currentSession?.sessionId === session.sessionId) {
                                                                            const remainingSessions = filteredSessions.filter(s => s.sessionId !== session.sessionId);
                                                                            if (remainingSessions.length > 0) {
                                                                                switchSession(remainingSessions[0]);
                                                                            }
                                                                        }
                                                                    }
                                                                }}
                                                                style={{
                                                                    fontSize: '14px',
                                                                    color: 'rgba(255, 255, 255, 0.45)',
                                                                    cursor: 'pointer',
                                                                    padding: '4px',
                                                                    marginLeft: '8px',
                                                                    transition: 'color 0.2s ease'
                                                                }}
                                                                onMouseEnter={(e) => {
                                                                    e.currentTarget.style.color = '#ff4d4f';
                                                                }}
                                                                onMouseLeave={(e) => {
                                                                    e.currentTarget.style.color = 'rgba(255, 255, 255, 0.45)';
                                                                }}
                                                                title="删除会话"
                                                            />
                                                        </div>
                                                    </div>
                                                ));
                                            })()}
                                    </div>
                                )}
                            >
                                <button
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        setShowHistoryDropdown(!showHistoryDropdown);
                                    }}
                                    style={{
                                        backgroundColor: 'transparent',
                                        border: 'none',
                                        color: '#ffffff',
                                        cursor: 'pointer',
                                        padding: '4px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        transition: 'color 0.2s ease'
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.color = '#1890ff';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.color = '#ffffff';
                                    }}
                                    title="历史会话"
                                >
                                    <HistoryOutlined style={{fontSize: '16px'}}/>
                                </button>
                            </Dropdown>
                        </div>
                    </div>
                }
                placement="right"
                onClose={() => {
                    setIsOpen(false);
                    setIsTransferred(false); // 清除转移标志
                    setTempSystemPrompt(null); // 清除临时预设
                    setTempAgentName(null); // 清除临时智能体名称
                    // 清除保存的会话，这样下次打开时会恢复到default会话
                    localStorage.removeItem('currentChatSession');
                    setShouldCreateNewSession(true); // 强制下次打开时创建新会话
                }}
                open={isOpen}
                width={'50vw'}
                zIndex={10001}
                styles={{
                    body: {backgroundColor: '#1f1f1f', padding: 0},
                    header: {backgroundColor: '#1f1f1f', color: '#ffffff', borderBottom: '1px solid #303030'}
                }}
                style={{color: '#ffffff'}}
            >
                <div style={{height: '100%', display: 'flex', flexDirection: 'column'}}>
                    {/* 消息显示区域 */}
                    <div style={{
                        flex: 1,
                        padding: '16px',
                        overflowY: 'auto',
                        backgroundColor: '#1f1f1f'
                    }}>
                        {/* 错误显示 */}
                        {chatError && (
                            <div style={{
                                marginBottom: '16px',
                                padding: '4px 8px',
                                borderRadius: '8px',
                                backgroundColor: 'rgba(255, 77, 79, 0.1)',
                                border: '1px solid rgba(255, 77, 79, 0.3)',
                                color: '#ff4d4f'
                            }}>
                                <div style={{fontSize: '12px', marginBottom: '4px'}}>⚠️ 错误提示</div>
                                <div style={{fontSize: '14px'}}>{chatError}</div>
                                <button
                                    onClick={() => setChatError(null)}
                                    style={{
                                        marginTop: '8px',
                                        padding: '4px 8px',
                                        fontSize: '12px',
                                        backgroundColor: 'rgba(255, 77, 79, 0.2)',
                                        border: '1px solid rgba(255, 77, 79, 0.4)',
                                        borderRadius: '4px',
                                        color: '#ff4d4f',
                                        cursor: 'pointer'
                                    }}
                                >
                                    关闭
                                </button>
                            </div>
                        )}

                        {!chatError && (
                            <>
                                {displayMessages.length === 0 ? (
                                    <div style={{textAlign: 'center', marginTop: '50px'}}>
                                        <p style={{color: 'rgba(255, 255, 255, 0.65)'}}>欢迎使用AI Chat！</p>
                                        <p style={{color: 'rgba(255, 255, 255, 0.65)'}}>您可以在这里询问关于交易和市场的问题。</p>
                                    </div>
                                ) : (
                                    displayMessages.map((msg, displayIndex) => {
                                        // 处理 role=tool 的消息：默认折叠，点击展开
                                        // 如果是连续的tool消息（前面还有tool消息），跳过渲染
                                        if (msg.role === 'tool' && displayIndex > 0 && displayMessages[displayIndex - 1]?.role === 'tool') {
                                            // 如果不是组的第一个消息，且组未展开，跳过渲染
                                            // 找到组的起始位置
                                            let startIdx = displayIndex;
                                            while (startIdx > 0 && displayMessages[startIdx - 1]?.role === 'tool') {
                                                startIdx--;
                                            }
                                            if (!expandedMessages.has(startIdx)) {
                                                return null;
                                            }
                                        }
                                        const isTool = msg.role === 'tool';
                                        const isToolExpanded = expandedMessages.has(displayIndex);
                                        return (
                                        <div key={displayIndex} style={{
                                            marginBottom: '16px',
                                            display: 'flex',
                                            justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
                                            alignItems: 'flex-start'
                                        }}>
                                            {/* tool 角色图标 - 背景色30%透明，显示角标数字 */}
                                            {isTool && (
                                                <Tooltip title={isToolExpanded ? "点击收起" : `点击展开 ${getToolGroupCount(displayIndex)} 个工具调用结果`}>
                                                    <div
                                                        onClick={() => toggleToolGroupExpand(displayIndex)}
                                                        style={{
                                                            position: 'relative',
                                                            width: '32px',
                                                            height: '32px',
                                                            borderRadius: '50%',
                                                            backgroundColor: isToolExpanded ? 'rgba(250, 140, 22, 0.5)' : 'rgba(250, 140, 22, 0.3)',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            justifyContent: 'center',
                                                            cursor: 'pointer',
                                                            marginRight: '8px',
                                                            flexShrink: 0,
                                                            transition: 'all 0.2s ease',
                                                            border: isToolExpanded ? '1px solid rgba(250, 140, 22, 0.8)' : '1px solid rgba(250, 140, 22, 0.5)'
                                                        }}
                                                        onMouseEnter={(e) => {
                                                            e.currentTarget.style.backgroundColor = 'rgba(250, 140, 22, 0.5)';
                                                            e.currentTarget.style.transform = 'scale(1.1)';
                                                            e.currentTarget.style.boxShadow = '0 2px 8px rgba(250, 140, 22, 0.3)';
                                                        }}
                                                        onMouseLeave={(e) => {
                                                            e.currentTarget.style.backgroundColor = isToolExpanded ? 'rgba(250, 140, 22, 0.5)' : 'rgba(250, 140, 22, 0.3)';
                                                            e.currentTarget.style.transform = 'scale(1)';
                                                            e.currentTarget.style.boxShadow = 'none';
                                                        }}
                                                    >
                                                        <ToolOutlined style={{color: '#fa8c16', fontSize: '16px'}} />
                                                        {/* 角标数字 */}
                                                        {!isToolExpanded && getToolGroupCount(displayIndex) > 1 && (
                                                            <div style={{
                                                                position: 'absolute',
                                                                top: '-4px',
                                                                right: '-4px',
                                                                backgroundColor: '#fa8c16',
                                                                color: '#ffffff',
                                                                fontSize: '10px',
                                                                fontWeight: 'bold',
                                                                padding: '0 4px',
                                                                borderRadius: '8px',
                                                                minWidth: '14px',
                                                                height: '14px',
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                                justifyContent: 'center',
                                                                border: '1px solid #1f1f1f'
                                                            }}>
                                                                {getToolGroupCount(displayIndex)}
                                                            </div>
                                                        )}
                                                    </div>
                                                </Tooltip>
                                            )}
                                            {/* 非 tool 角色或 tool 展开时显示消息内容 */}
                                            {(!isTool || isToolExpanded) && (
                                            <div style={{
                                                position: 'relative',
                                                width: expandedChartMessages.has(displayIndex) ? '100%' : 'auto',
                                                maxWidth: expandedChartMessages.has(displayIndex) ? '100%' : '80%',
                                                padding: '8px',
                                                borderRadius: '8px',
                                                backgroundColor: msg.role === 'user' ? 'rgba(24, 144, 255, 0.3)' : isTool ? '#3a3a3a' : '#2a2a2a',
                                                color: '#ffffff',
                                                wordBreak: 'break-word',
                                                border: msg.role === 'user' ? 'none' : '1px solid #404040',
                                                whiteSpace: 'pre-wrap'
                                            }}>
                                                {isTool && (
                                                    <div style={{
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                        marginBottom: '8px',
                                                        paddingBottom: '8px',
                                                        borderBottom: '1px solid #404040'
                                                    }}>
                                                        <span style={{fontSize: '12px', color: '#fa8c16'}}>
                                                            🔧 工具调用结果 {getToolGroupCount(displayIndex) > 1 && `(${getToolGroupCount(displayIndex)}个)`}
                                                        </span>
                                                        <button
                                                            onClick={() => toggleToolGroupExpand(displayIndex)}
                                                            style={{
                                                                padding: '2px 8px',
                                                                fontSize: '11px',
                                                                backgroundColor: 'transparent',
                                                                border: '1px solid rgba(255, 255, 255, 0.2)',
                                                                borderRadius: '4px',
                                                                color: 'rgba(255, 255, 255, 0.6)',
                                                                cursor: 'pointer'
                                                            }}
                                                        >
                                                            收起
                                                        </button>
                                                    </div>
                                                )}
                                                {/* tool 消息组展开时，渲染组内所有消息 */}
                                                {isTool ? (
                                                    // 获取组内所有消息并渲染
                                                    (() => {
                                                        const groupMessages: typeof msg[] = [];
                                                        let idx = displayIndex;
                                                        while (idx < displayMessages.length && displayMessages[idx]?.role === 'tool') {
                                                            groupMessages.push(displayMessages[idx]);
                                                            idx++;
                                                        }
                                                        return groupMessages.map((toolMsg, toolIdx) => (
                                                            <div key={toolIdx} style={{
                                                                marginBottom: toolIdx < groupMessages.length - 1 ? '12px' : '0',
                                                                paddingBottom: toolIdx < groupMessages.length - 1 ? '12px' : '0',
                                                                borderBottom: toolIdx < groupMessages.length - 1 ? '1px solid #505050' : 'none'
                                                            }}>
                                                                <div style={{
                                                                    fontSize: '11px',
                                                                    color: 'rgba(255, 255, 255, 0.4)',
                                                                    marginBottom: '4px'
                                                                }}>
                                                                    工具调用 #{toolIdx + 1}
                                                                </div>
                                                                <MarkdownRenderer
                                                                    content={toolMsg.content}
                                                                    style={{
                                                                        backgroundColor: 'transparent',
                                                                        padding: '0',
                                                                        borderRadius: '0'
                                                                    }}
                                                                    showCopyButton={false}
                                                                    onChartModeChange={(isChart) => toggleChartExpand(displayIndex + toolIdx, isChart)}
                                                                />
                                                            </div>
                                                        ));
                                                    })()
                                                ) : (
                                                <CollapsibleMessageContent
                                                    role={msg.role}
                                                                                                                isExpanded={expandedMessages.has(displayIndex)}
                                                                                                                onToggleExpand={() => toggleMessageExpand(displayIndex)}                                                >
                                                    {msg.role === 'user' ? (
                                                        (() => {
                                                            // 将=== {title} ===替换为## {title}
                                                            const formattedContent = msg.content.replace(/===\s*(.+?)\s*===/g, '## $1');
                                                            return <MarkdownRenderer content={formattedContent} style={{
                                                                backgroundColor: 'transparent',
                                                                padding: '0',
                                                                borderRadius: '0'
                                                            }} showCopyButton={false} isUser={true}
                                                                onChartModeChange={(isChart) => toggleChartExpand(displayIndex, isChart)}/>;
                                                        })()
                                                    ) : (
                                                        (() => {
                                                            const {
                                                                thinkContent,
                                                                actualContent
                                                            } = parseAIContent(msg.content);
                                                            const isExpanded = expandedThinking.has(displayIndex);

                                                            return (
                                                                <>
                                                                    {actualContent && (
                                                                        <div className="actual-content">
                                                                            <MarkdownRenderer content={actualContent}
                                                                                              style={{
                                                                                                  backgroundColor: 'transparent',
                                                                                                  padding: '0',
                                                                                                  borderRadius: '0'
                                                                                              }}
                                                                                              showCopyButton={false}
                                                                                              onChartModeChange={(isChart) => toggleChartExpand(displayIndex, isChart)}/>
                                                                        </div>
                                                                    )}

                                                                    {thinkContent && (
                                                                        <div style={{marginTop: '12px'}}>
                                                                            <button
                                                                                onClick={() => toggleThinking(displayIndex)}
                                                                                style={{
                                                                                    padding: '6px 12px',
                                                                                    fontSize: '12px',
                                                                                    backgroundColor: 'rgba(255, 255, 255, 0.05)',
                                                                                    border: '1px solid rgba(255, 255, 255, 0.1)',
                                                                                    borderRadius: '12px',
                                                                                    color: 'rgba(255, 255, 255, 0.6)',
                                                                                    cursor: 'pointer',
                                                                                    transition: 'all 0.2s ease',
                                                                                    display: 'flex',
                                                                                    alignItems: 'center',
                                                                                    gap: '6px'
                                                                                }}
                                                                                onMouseEnter={(e) => {
                                                                                    e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.1)';
                                                                                    e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.2)';
                                                                                    e.currentTarget.style.color = 'rgba(255, 255, 255, 0.8)';
                                                                                }}
                                                                                onMouseLeave={(e) => {
                                                                                    e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.05)';
                                                                                    e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
                                                                                    e.currentTarget.style.color = 'rgba(255, 255, 255, 0.6)';
                                                                                }}
                                                                            >
                                                                                <span>{isExpanded ? '🙈' : '🤔'}</span>
                                                                                <span>{isExpanded ? '隐藏思考过程' : '查看思考过程'}</span>
                                                                            </button>

                                                                            {isExpanded && (
                                                                                <div
                                                                                    style={{
                                                                                        marginTop: '8px',
                                                                                        padding: '12px',
                                                                                        backgroundColor: 'rgba(255, 255, 255, 0.05)',
                                                                                        border: '1px solid rgba(255, 255, 255, 0.1)',
                                                                                        borderRadius: '12px',
                                                                                        color: 'rgba(255, 255, 255, 0.7)',
                                                                                        fontSize: '13px',
                                                                                        lineHeight: '1.4',
                                                                                        whiteSpace: 'pre-wrap',
                                                                                        animation: 'slideDown 0.3s ease-out'
                                                                                    }}
                                                                                >
                                                                                    <div style={{
                                                                                        fontSize: '12px',
                                                                                        marginBottom: '6px',
                                                                                        opacity: 0.8
                                                                                    }}>
                                                                                        💭 思考过程：
                                                                                    </div>

                                                                                    <div>{thinkContent}</div>
                                                                                </div>
                                                                            )}
                                                                        </div>
                                                                    )}
                                                                </>
                                                            );
                                                        })()
                                                    )}
                                                </CollapsibleMessageContent>
                                                )}

                                                {/* 时间戳 */}
                                                <div style={{marginTop: 4}}>
                                                    <Text style={{fontSize: 11, color: 'rgba(255, 255, 255, 0.45)'}}>
                                                        {msg.createdTime}
                                                    </Text>
                                                </div>
                                            </div>
                                        )}
                                        </div>
                                        );
                                    })
                                )}
                                {isLoading && (
                                    <div style={{
                                        marginBottom: '16px',
                                        display: 'flex',
                                        justifyContent: 'flex-start'
                                    }}>
                                        <div style={{
                                            padding: '16px 20px',
                                            borderRadius: '18px',
                                            backgroundColor: '#2a2a2a',
                                            border: '1px solid #404040',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '6px'
                                        }}>
                                            <div style={{
                                                width: '6px',
                                                height: '6px',
                                                borderRadius: '50%',
                                                backgroundColor: 'rgba(24, 144, 255, 0.3)',
                                                animation: 'pulse 1.5s ease-in-out infinite',
                                                animationDelay: '0s'
                                            }}></div>
                                            <div style={{
                                                width: '6px',
                                                height: '6px',
                                                borderRadius: '50%',
                                                backgroundColor: 'rgba(24, 144, 255, 0.3)',
                                                animation: 'pulse 1.5s ease-in-out infinite',
                                                animationDelay: '0.2s'
                                            }}></div>
                                            <div style={{
                                                width: '6px',
                                                height: '6px',
                                                borderRadius: '50%',
                                                backgroundColor: 'rgba(24, 144, 255, 0.3)',
                                                animation: 'pulse 1.5s ease-in-out infinite',
                                                animationDelay: '0.4s'
                                            }}></div>
                                        </div>
                                    </div>
                                )}
                                {/* 用于自动滚动的不可见元素 */}
                                <div ref={messagesEndRef} style={{height: '1px'}}/>
                            </>
                        )}
                    </div>

                    {/* 输入框 */}
                    <div style={{
                        borderTop: '1px solid #303030',
                        padding: '16px',
                        backgroundColor: '#1f1f1f'
                    }}>
                        <div style={{
                            position: 'relative'
                        }}>
              <textarea
                  placeholder="请输入您的问题..."
                  style={{
                      width: '100%',
                      resize: 'none',
                      padding: '12px 16px',
                      paddingRight: '80px',
                      borderRadius: '20px',
                      backgroundColor: '#2a2a2a',
                      border: '1px solid #404040',
                      color: '#ffffff',
                      fontSize: '14px',
                      lineHeight: '1.5',
                      minHeight: '52px',
                      maxHeight: '120px',
                      overflowY: 'auto',
                      outline: 'none',
                      transition: 'border-color 0.3s ease'
                  }}
                  onFocus={(e) => {
                      e.target.style.borderColor = '#1890ff';
                  }}
                  onBlur={(e) => {
                      e.target.style.borderColor = '#404040';
                  }}
                  onKeyPress={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          const value = e.currentTarget.value.trim();
                          if (value) {
                              handleSendMessage(value);
                              e.currentTarget.value = '';
                          }
                      }
                  }}
              />

                            {/* 发送按钮 */}
                            <div style={{
                                position: 'absolute',
                                right: '8px',
                                bottom: '6px',
                                display: 'flex',
                                gap: '4px',
                                alignItems: 'center'
                            }}>
                                <button
                                    onClick={() => {
                                        const textarea = document.querySelector('textarea') as HTMLTextAreaElement;
                                        const value = textarea?.value.trim();
                                        if (value && !isLoading) {
                                            handleSendMessage(value);
                                            textarea.value = '';
                                        }
                                    }}
                                    disabled={isLoading}
                                    style={{
                                        width: '28px',
                                        height: '28px',
                                        borderRadius: '50%',
                                        border: 'none',
                                        backgroundColor: isLoading ? '#8c8c8c' : 'rgba(24, 144, 255, 0.3)',
                                        color: '#ffffff',
                                        cursor: isLoading ? 'not-allowed' : 'pointer',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: '12px',
                                        transition: 'all 0.3s ease',
                                        opacity: isLoading ? 0.6 : 1
                                    }}
                                    onMouseEnter={(e) => {
                                        if (!isLoading) {
                                            e.currentTarget.style.backgroundColor = 'rgba(24, 144, 255, 0.5)';
                                        }
                                    }}
                                    onMouseLeave={(e) => {
                                        if (!isLoading) {
                                            e.currentTarget.style.backgroundColor = 'rgba(24, 144, 255, 0.3)';
                                        }
                                    }}
                                >
                                    {isLoading ? (
                                        <div style={{
                                            width: '12px',
                                            height: '12px',
                                            border: '2px solid rgba(255, 255, 255, 0.3)',
                                            borderTop: '2px solid #ffffff',
                                            borderRadius: '50%',
                                            animation: 'spin 1s linear infinite'
                                        }}></div>
                                    ) : (
                                        '➤'
                                    )}
                                </button>
                            </div>

                            {/* 快捷提示 */}
                            <div style={{marginTop: '4px', textAlign: 'center'}}>
                <span style={{fontSize: '11px', color: 'rgba(255, 255, 255, 0.45)'}}>
                  按 Enter 发送，Shift + Enter 换行
                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </Drawer>
        </>
    );
};

export default ChatFloatWindow;
