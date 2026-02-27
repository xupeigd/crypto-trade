import {useCallback, useEffect, useState} from 'react';
import {ChatSession, ChatState} from '../types/chat';
import chatService from '../services/chatService';

// 添加调试日志
console.log('useChat hook loaded');

export const useChat = () => {
    const [state, setState] = useState<ChatState>({
        sessions: [],
        currentSession: null,
        messages: [],
        isLoading: false,
        isConnected: false,
        error: null
    });

    // 加载用户会话列表
    const loadSessions = useCallback(async (userId: string = 'default') => {
        try {
            setState(prev => ({...prev, isLoading: true, error: null}));
            const sessions = await chatService.getUserSessions(userId);
            setState(prev => ({
                ...prev,
                sessions,
                isLoading: false
            }));
        } catch (error) {
            console.error('加载会话列表失败:', error);
            setState(prev => ({
                ...prev,
                error: '加载会话列表失败',
                isLoading: false
            }));
        }
    }, []);

    // 创建新会话
    const createSession = useCallback(async (sessionName?: string, userId: string = 'default') => {
        try {
            setState(prev => ({...prev, isLoading: true, error: null}));
            const newSession = await chatService.createNewSession(sessionName, userId);
            setState(prev => ({
                ...prev,
                sessions: [newSession, ...prev.sessions],
                isLoading: false
            }));
            return newSession;
        } catch (error) {
            console.error('创建会话失败:', error);
            setState(prev => ({
                ...prev,
                error: '创建会话失败',
                isLoading: false
            }));
            throw error;
        }
    }, []);

    // 加载会话消息
    const loadMessages = useCallback(async (sessionId: number) => {
        try {
            setState(prev => ({...prev, isLoading: true, error: null}));
            const messages = await chatService.getSessionMessages(sessionId);
            setState(prev => ({
                ...prev,
                messages,
                isLoading: false
            }));
            return messages;
        } catch (error) {
            console.error('加载消息失败:', error);
            setState(prev => ({
                ...prev,
                error: '加载消息失败',
                isLoading: false
            }));
            throw error;
        }
    }, []);

    // 切换会话
    const switchSession = useCallback(async (session: ChatSession) => {
        try {
            setState(prev => ({...prev, isLoading: true, currentSession: session}));

            // 加载历史消息
            await loadMessages(session.sessionId);

            setState(prev => ({...prev, isLoading: false}));
        } catch (error) {
            console.error('切换会话失败:', error);
            setState(prev => ({
                ...prev,
                error: '切换会话失败',
                isLoading: false
            }));
        }
    }, [loadMessages]);

    // 通过sessionId加载会话（用于从其他页面转移会话）
    const loadSessionById = useCallback(async (sessionId: number, userId: string = 'default') => {
        try {
            setState(prev => ({...prev, isLoading: true}));

            // 加载会话消息
            await loadMessages(sessionId);

            // 尝试从sessions列表中找到该会话
            const targetSession = state.sessions.find(s => s.sessionId === sessionId);
            if (targetSession) {
                setState(prev => ({...prev, currentSession: targetSession}));
            } else {
                // 如果找不到，创建一个临时的会话对象，使用传入的userId
                const tempSession: ChatSession = {
                    sessionId: sessionId,
                    sessionName: `会话 ${sessionId}`,
                    userId: userId,
                    status: 'active',
                    modelName: '',
                    createdTime: new Date().toISOString(),
                    updatedTime: new Date().toISOString()
                };
                setState(prev => ({...prev, currentSession: tempSession}));
            }

            setState(prev => ({...prev, isLoading: false}));
        } catch (error) {
            console.error('加载会话失败:', error);
            setState(prev => ({
                ...prev,
                error: '加载会话失败',
                isLoading: false
            }));
        }
    }, [loadMessages, state.sessions]);

    // 发送消息
    const sendMessage = useCallback(async (message: string, userId: string = 'default') => {
        if (!message.trim()) {
            return;
        }

        try {
            // 立即显示用户消息
            const userMessage = {
                messageId: Date.now(), // 临时ID
                sessionId: state.currentSession?.sessionId || 0,
                role: 'user' as const,
                content: message.trim(),
                tokensUsed: 0,
                processingTimeMs: 0,
                createdTime: new Date().toISOString()
            };

            setState(prev => ({
                ...prev,
                messages: [...prev.messages, userMessage],
                isLoading: true,
                error: null
            }));

            let sessionId = state.currentSession?.sessionId;

            // 如果没有当前会话，创建一个新会话
            if (!sessionId) {
                const newSession = await createSession(undefined, userId);
                sessionId = newSession.sessionId;
                setState(prev => ({...prev, currentSession: newSession}));
            }

            // 确保sessionId存在
            if (!sessionId) {
                throw new Error('无法获取有效的会话ID');
            }

            // 发送消息
            const response = await chatService.sendMessage({
                sessionId,
                message: message.trim(),
                userId
            });

            if (response.success && response.sessionId) {
                // 重新加载消息以获取AI回复（会替换临时用户消息）
                await loadMessages(response.sessionId);
            }

            setState(prev => ({...prev, isLoading: false}));
        } catch (error) {
            console.error('发送消息失败:', error);
            setState(prev => ({
                ...prev,
                error: '发送消息失败',
                isLoading: false
            }));
            throw error;
        }
    }, [state.currentSession, createSession, switchSession, loadMessages]);

    // 删除会话
    const deleteSession = useCallback(async (sessionId: number, userId: string = 'default') => {
        try {
            await chatService.deleteSession(sessionId, userId);

            setState(prev => ({
                ...prev,
                sessions: prev.sessions.filter(s => s.sessionId !== sessionId),
                currentSession: prev.currentSession?.sessionId === sessionId ? null : prev.currentSession,
                messages: prev.currentSession?.sessionId === sessionId ? [] : prev.messages
            }));
        } catch (error) {
            console.error('删除会话失败:', error);
            setState(prev => ({
                ...prev,
                error: '删除会话失败'
            }));
            throw error;
        }
    }, []);

    // 更新会话名称
    const updateSessionName = useCallback(async (sessionId: number, sessionName: string, userId: string = 'default') => {
        try {
            const updatedSession = await chatService.updateSessionName(sessionId, sessionName, userId);

            setState(prev => ({
                ...prev,
                sessions: prev.sessions.map(s =>
                    s.sessionId === sessionId ? updatedSession : s
                ),
                currentSession: prev.currentSession?.sessionId === sessionId ? updatedSession : prev.currentSession
            }));
        } catch (error) {
            console.error('更新会话名称失败:', error);
            setState(prev => ({
                ...prev,
                error: '更新会话名称失败'
            }));
            throw error;
        }
    }, []);

    // 清除错误
    const clearError = useCallback(() => {
        setState(prev => ({...prev, error: null}));
    }, []);

    // 初始化
    useEffect(() => {
        const initializeChat = async () => {
            try {
                setState(prev => ({...prev, isLoading: true}));

                // 加载default用户的会话列表
                const sessions = await chatService.getUserSessions('default');
                setState(prev => ({...prev, sessions}));

                // 尝试从localStorage恢复上次使用的会话
                try {
                    const savedSession = localStorage.getItem('currentChatSession');
                    if (savedSession) {
                        const sessionData = JSON.parse(savedSession);
                        const restoredSession = sessions.find(s => s.sessionId === sessionData.sessionId);
                        if (restoredSession) {
                            setState(prev => ({...prev, currentSession: restoredSession}));
                            await loadMessages(restoredSession.sessionId);
                            return;
                        }
                    }
                } catch (error) {
                    console.log('恢复会话失败，将加载default会话:', error);
                }

                // 如果没有恢复的会话，使用default用户的会话
                if (sessions.length > 0) {
                    const latestDefaultSession = sessions[0];
                    setState(prev => ({...prev, currentSession: latestDefaultSession}));
                    await loadMessages(latestDefaultSession.sessionId);
                } else {
                    // 如果没有default会话，创建一个新的
                    const newSession = await chatService.createNewSession(undefined, 'default');
                    setState(prev => ({
                        ...prev,
                        sessions: [newSession],
                        currentSession: newSession
                    }));
                }
            } catch (error) {
                console.error('初始化聊天失败:', error);
                setState(prev => ({
                    ...prev,
                    error: '初始化聊天失败',
                    isLoading: false
                }));
            }
        };

        initializeChat();
    }, []);

    // 持久化currentSession到localStorage
    useEffect(() => {
        if (state.currentSession) {
            localStorage.setItem('currentChatSession', JSON.stringify({
                sessionId: state.currentSession.sessionId,
                userId: state.currentSession.userId,
                sessionName: state.currentSession.sessionName
            }));
        } else {
            localStorage.removeItem('currentChatSession');
        }
    }, [state.currentSession]);

    return {
        ...state,
        loadSessions,
        createSession,
        loadMessages,
        switchSession,
        loadSessionById,
        sendMessage,
        deleteSession,
        updateSessionName,
        clearError
    };
};