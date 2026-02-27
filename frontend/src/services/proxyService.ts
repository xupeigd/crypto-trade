import api from './api';
import {ApiResponse} from '../types/common';
import {
    AssociatedTask,
    CreateProxyServiceConfigRequest,
    ProxyServiceConfig,
    TaskCountResponse,
    TestConnectionResponse,
    UpdateProxyServiceConfigRequest
} from '../types/proxy';

export const proxyService = {
    // 获取所有代理配置
    getAllConfigs: async (): Promise<ProxyServiceConfig[]> => {
        const response = await api.get<ApiResponse<ProxyServiceConfig[]>>('/proxy-service-configs');
        return response.data.data || [];
    },

    // 根据ID获取代理配置
    getConfigById: async (id: number): Promise<ProxyServiceConfig | null> => {
        const response = await api.get<ApiResponse<ProxyServiceConfig>>(`/proxy-service-configs/${id}`);
        return response.data.data || null;
    },

    // 获取活跃的代理配置
    getActiveConfigs: async (): Promise<ProxyServiceConfig[]> => {
        const response = await api.get<ApiResponse<ProxyServiceConfig[]>>('/proxy-service-configs/active');
        return response.data.data || [];
    },

    // 创建代理配置
    createConfig: async (config: CreateProxyServiceConfigRequest): Promise<ProxyServiceConfig | null> => {
        const response = await api.post<ApiResponse<ProxyServiceConfig>>('/proxy-service-configs', config);
        return response.data.data || null;
    },

    // 更新代理配置
    updateConfig: async (id: number, config: UpdateProxyServiceConfigRequest): Promise<ProxyServiceConfig | null> => {
        const response = await api.put<ApiResponse<ProxyServiceConfig>>(`/proxy-service-configs/${id}`, config);
        return response.data.data || null;
    },

    // 删除代理配置
    deleteConfig: async (id: number): Promise<boolean> => {
        const response = await api.delete<ApiResponse<void>>(`/proxy-service-configs/${id}`);
        return response.data.code === 0 || false;
    },

    // 测试代理连接
    testConnection: async (id: number): Promise<TestConnectionResponse | null> => {
        const response = await api.post<ApiResponse<TestConnectionResponse>>(`/proxy-service-configs/${id}/test`);
        return response.data.data || null;
    },

    // 获取代理配置关联的任务数量
    getAssociatedTaskCount: async (id: number): Promise<TaskCountResponse | null> => {
        const response = await api.get<ApiResponse<TaskCountResponse>>(`/proxy-service-configs/${id}/task-count`);
        return response.data.data || null;
    },

    // 获取代理配置关联的任务列表
    getAssociatedTasks: async (id: number): Promise<AssociatedTask[]> => {
        const response = await api.get<ApiResponse<AssociatedTask[]>>(`/proxy-service-configs/${id}/tasks`);
        return response.data.data || [];
    }
};