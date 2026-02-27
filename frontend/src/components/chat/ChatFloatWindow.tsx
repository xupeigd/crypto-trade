import React, {useEffect, useLayoutEffect, useRef, useState} from 'react';
import {Drawer, Dropdown, Tooltip, Typography} from 'antd';
import {CopyOutlined, DownOutlined, HistoryOutlined, PlusOutlined, UpOutlined} from '@ant-design/icons';
import {useChat} from '../../hooks/useChat';
import MarkdownRenderer from '../common/MarkdownRenderer';

// console.log('ChatFloatWindow component loaded');

const {Title, Text} = Typography;

const ChatFloatWindow: React.FC = () => {
    const [isOpen, setIsOpen] = useState(false);
    const [chatError, setChatError] = useState<string | null>(null);
    const [expandedThinking, setExpandedThinking] = useState<Set<number>>(new Set());
    const [expandedMessages, setExpandedMessages] = useState<Set<number>>(new Set());
    const [showHistoryDropdown, setShowHistoryDropdown] = useState(false);
    const [sessionsWithMessages, setSessionsWithMessages] = useState<Set<number>>(new Set());
    const [isTransferred, setIsTransferred] = useState(false); // 标识是否通过会话转移打开
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
    const isLoading = chatHook?.isLoading || false;
    const error = chatHook?.error || chatError || null;

    // 只有在hook完全不可用时才使用fallback的sendMessage
    const sendMessage = chatHook?.sendMessage || fallbackChatHook.sendMessage;
    console.log('Final sendMessage function:', typeof sendMessage);
    const createSession = chatHook?.createSession || fallbackChatHook.createSession;
    const switchSession = chatHook?.switchSession || fallbackChatHook.switchSession;
    const loadSessionById = chatHook?.loadSessionById || fallbackChatHook.loadSessionById;
    const deleteSession = chatHook?.deleteSession || fallbackChatHook.deleteSession;
    const updateSessionName = chatHook?.updateSessionName || fallbackChatHook.updateSessionName;
    const clearError = chatHook?.clearError || (() => setChatError(null));

    // 当有消息时，记录该会话为有消息的会话
    useEffect(() => {
        if (currentSession && messages.length > 0) {
            setSessionsWithMessages(prev => new Set(prev).add(currentSession.sessionId));
        }
    }, [currentSession, messages.length]);

    // 当抽屉重新打开时，恢复到default会话（仅当不是通过会话转移打开时）
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

                    // 查找default用户的会话
                    const defaultSessions = sessions.filter(s => s.userId === 'default');
                    if (defaultSessions.length > 0 && switchSession) {
                        const latestDefaultSession = defaultSessions[0];
                        // 检查当前会话是否已经是default会话，避免重复切换
                        if (currentSession?.sessionId !== latestDefaultSession.sessionId) {
                            await switchSession(latestDefaultSession);
                        }
                    }
                } catch (error) {
                    console.error('恢复default会话失败:', error);
                }
            };

            restoreDefaultSession();
        }
    }, [isOpen, isTransferred, sessions, currentSession, switchSession]);

    // 解析AI消息内容，将思考过程和实际回复分开
    const parseAIContent = (content: string) => {
        // 处理HTML编码的情况，先解码
        const decodedContent = content
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&');
        // 首先尝试匹配<thinking>标签
        let thinkingRegex = /<thinking>([\s\S]*?)<\/thinking>/i;
        let match = decodedContent.match(thinkingRegex);

        // 如果没有匹配到，尝试匹配<think>标签
        if (!match) {
            thinkingRegex = /<think>([\s\S]*?)<\/think>/i;
            match = decodedContent.match(thinkingRegex);
        }

        if (match) {
            const thinkContent = match[1].trim();
            const actualContent = decodedContent.replace(match[0], '').trim();
            // 将=== {title} ===替换为## {title}
            const formattedContent = actualContent.replace(/===\s*(.+?)\s*===/g, '## $1');

            return {
                thinkContent: thinkContent || null,
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
                await sendMessage(message, userId);
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

    // 可折叠的消息内容组件
    const CollapsibleMessageContent: React.FC<{
        index: number;
        children: React.ReactNode;
        role: 'user' | 'assistant';
    }> = ({index, children, role}) => {
        const [isHovered, setIsHovered] = useState(false);
        const [copied, setCopied] = useState(false);
        const [contentLength, setContentLength] = useState(0);
        const contentRef = useRef<HTMLDivElement>(null);
        const isExpanded = expandedMessages.has(index);

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
                                toggleMessageExpand(index);
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
                onClick={() => {
                    console.log('聊天按钮被点击');
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
                        <span>AI Chat</span>
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
                                dropdownRender={() => (
                                    <div style={{
                                        backgroundColor: '#2a2a2a',
                                        border: '1px solid #404040',
                                        borderRadius: '8px',
                                        padding: '8px',
                                        minWidth: '280px',
                                        maxHeight: '400px',
                                        overflowY: 'auto'
                                    }}>
                                        {sessions.filter(session =>
                                            sessionsWithMessages.has(session.sessionId) || currentSession?.sessionId === session.sessionId
                                        ).length === 0 ? (
                                            <div style={{
                                                color: 'rgba(255, 255, 255, 0.5)',
                                                padding: '12px 16px',
                                                textAlign: 'center',
                                                fontSize: '14px'
                                            }}>
                                                暂无历史会话
                                            </div>
                                        ) : (
                                            sessions
                                                .filter(session =>
                                                    sessionsWithMessages.has(session.sessionId) || currentSession?.sessionId === session.sessionId
                                                )
                                                .map((session) => (
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
                                                        {session.sessionName || `会话 ${session.sessionId}`}
                                                    </div>
                                                ))
                                        )}
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
                    // 清除保存的会话，这样下次打开时会恢复到default会话
                    localStorage.removeItem('currentChatSession');
                }}
                open={isOpen}
                width={600}
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
                                {messages.length === 0 ? (
                                    <div style={{textAlign: 'center', marginTop: '50px'}}>
                                        <p style={{color: 'rgba(255, 255, 255, 0.65)'}}>欢迎使用AI Chat！</p>
                                        <p style={{color: 'rgba(255, 255, 255, 0.65)'}}>您可以在这里询问关于交易和市场的问题。</p>
                                    </div>
                                ) : (
                                    messages.map((msg, index) => (
                                        <div key={index} style={{
                                            marginBottom: '16px',
                                            display: 'flex',
                                            justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start'
                                        }}>
                                            <div style={{
                                                position: 'relative',
                                                maxWidth: '80%',
                                                padding: '8px',
                                                borderRadius: '8px',
                                                backgroundColor: msg.role === 'user' ? 'rgba(24, 144, 255, 0.3)' : '#2a2a2a',
                                                color: '#ffffff',
                                                wordBreak: 'break-word',
                                                border: msg.role === 'user' ? 'none' : '1px solid #404040',
                                                whiteSpace: 'pre-wrap'
                                            }}>
                                                <CollapsibleMessageContent index={index} role={msg.role}>
                                                    {msg.role === 'user' ? (
                                                        (() => {
                                                            // 将=== {title} ===替换为## {title}
                                                            const formattedContent = msg.content.replace(/===\s*(.+?)\s*===/g, '##                                                 ');
                                                            return <MarkdownRenderer content={formattedContent} style={{
                                                                backgroundColor: 'transparent',
                                                                padding: '0',
                                                                borderRadius: '0'
                                                            }} showCopyButton={false} isUser={true}/>;
                                                        })()
                                                    ) : (
                                                        (() => {
                                                            const {
                                                                thinkContent,
                                                                actualContent
                                                            } = parseAIContent(msg.content);
                                                            const isExpanded = expandedThinking.has(index);

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
                                                                                              showCopyButton={false}/>
                                                                        </div>
                                                                    )}

                                                                    {thinkContent && (
                                                                        <div style={{marginTop: '12px'}}>
                                                                            <button
                                                                                onClick={() => toggleThinking(index)}
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

                                                {/* 时间戳 */}
                                                <div style={{marginTop: 4}}>
                                                    <Text style={{fontSize: 11, color: 'rgba(255, 255, 255, 0.45)'}}>
                                                        {msg.createdTime}
                                                    </Text>
                                                </div>
                                            </div>
                                        </div>
                                    ))
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
