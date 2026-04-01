import axios from 'axios';
import {isAxiosError} from 'axios';
import {ApiResponse, ChatMessage, ChatSession, SendMessageRequest, SendMessageResponse} from '../types/chat';

const API_BASE_URL = '/chat';

// 为chat服务创建专用的axios实例，设置5分钟超时
const chatApi = axios.create({
    baseURL: API_BASE_URL,
    timeout: 300000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 请求拦截器
chatApi.interceptors.request.use(
    (config) => {
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 响应拦截器
chatApi.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        console.error('Chat API Error:', error);
        return Promise.reject(error);
    }
);

class ChatService {
    // 获取用户所有会话
    async getUserSessions(userId: string = 'default', agentId?: number): Promise<ChatSession[]> {
        try {
            const params: any = {userId};
            if (agentId) {
                params.agentId = agentId;
            }
            const response = await chatApi.get(`/sessions`, {
                params
            });
            const apiResponse: ApiResponse<ChatSession[]> = response.data;
            return apiResponse.data;
        } catch (error) {
            console.error('获取会话列表失败:', error);
            throw error;
        }
    }

    // 创建新会话
    async createNewSession(sessionName?: string, userId: string = 'default', agentId?: number): Promise<ChatSession> {
        try {
            const params: any = {userId};
            if (sessionName) {
                params.sessionName = sessionName;
            }
            if (agentId) {
                params.agentId = agentId;
            }
            const response = await chatApi.post(`/sessions`, null, {
                params
            });
            const apiResponse: ApiResponse<ChatSession> = response.data;
            return apiResponse.data;
        } catch (error) {
            console.error('创建会话失败:', error);
            throw error;
        }
    }

    // 获取最新会话
    async getLatestSession(userId: string = 'default'): Promise<ChatSession | null> {
        try {
            const response = await chatApi.get(`/sessions/latest`, {
                params: {userId}
            });
            const apiResponse: ApiResponse<ChatSession> = response.data;
            return apiResponse.data;
        } catch (error) {
            if (isAxiosError(error) && error.response?.status === 404) {
                return null;
            }
            console.error('获取最新会话失败:', error);
            throw error;
        }
    }

    // 获取会话消息
    async getSessionMessages(sessionId: number): Promise<ChatMessage[]> {
        try {
            const response = await chatApi.get(`/sessions/${sessionId}/messages`);
            const apiResponse: ApiResponse<ChatMessage[]> = response.data;
            return apiResponse.data;
        } catch (error) {
            console.error('获取会话消息失败:', error);
            throw error;
        }
    }

    // 发送消息
    async sendMessage(request: SendMessageRequest): Promise<SendMessageResponse> {
        try {
            const response = await chatApi.post(`/send`, request);
            const apiResponse: ApiResponse<SendMessageResponse> = response.data;
            return apiResponse.data;
        } catch (error) {
            console.error('发送消息失败:', error);
            throw error;
        }
    }

    // 更新会话名称
    async updateSessionName(sessionId: number, sessionName: string, userId: string = 'default'): Promise<ChatSession> {
        try {
            const response = await chatApi.put(`/sessions/${sessionId}/name`, null, {
                params: {sessionName, userId}
            });
            const apiResponse: ApiResponse<ChatSession> = response.data;
            return apiResponse.data;
        } catch (error) {
            console.error('更新会话名称失败:', error);
            throw error;
        }
    }

    // 删除会话
    async deleteSession(sessionId: number, userId: string = 'default'): Promise<void> {
        try {
            await chatApi.delete(`/sessions/${sessionId}`, {
                params: {userId}
            });
            // 删除成功
            return;
        } catch (error) {
            console.error('删除会话失败:', error);
            throw error;
        }
    }

    // 健康检查
    async healthCheck(): Promise<{ status: string; timestamp: number; service: string }> {
        try {
            const response = await chatApi.get(`/health`);
            const apiResponse: ApiResponse<{ status: string; timestamp: number; service: string }> = response.data;
            return apiResponse.data;
        } catch (error) {
            console.error('健康检查失败:', error);
            throw error;
        }
    }
}

export default new ChatService();