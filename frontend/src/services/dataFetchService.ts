import api from './api';
import {CreateDataFetchConfigRequest, DataFetchConfig, UpdateDataFetchConfigRequest} from '../types/dataFetch';

export interface ApiResponse<T> {
    code: number;
    message: string;
    data: T;
    success: boolean;
}

export const dataFetchService = {
    // 获取所有配置
    getAllConfigs: async (): Promise<DataFetchConfig[]> => {
        const response = await api.get('/data-fetch-configs');
        const apiResponse: ApiResponse<DataFetchConfig[]> = response.data;
        return apiResponse.data;
    },

    // 根据ID获取配置
    getConfigById: async (id: number): Promise<DataFetchConfig> => {
        const response = await api.get(`/data-fetch-configs/${id}`);
        const apiResponse: ApiResponse<DataFetchConfig> = response.data;
        return apiResponse.data;
    },

    // 根据任务ID获取配置
    getConfigByTaskId: async (taskId: number): Promise<DataFetchConfig> => {
        const response = await api.get(`/data-fetch-configs/task/${taskId}`);
        const apiResponse: ApiResponse<DataFetchConfig> = response.data;
        return apiResponse.data;
    },

    // 根据交易所名称获取配置
    getConfigsByCexName: async (cexName: string): Promise<DataFetchConfig[]> => {
        const response = await api.get(`/data-fetch-configs/cex/${cexName}`);
        const apiResponse: ApiResponse<DataFetchConfig[]> = response.data;
        return apiResponse.data;
    },

    // 获取需要鉴权的配置
    getAuthRequiredConfigs: async (): Promise<DataFetchConfig[]> => {
        const response = await api.get(`/data-fetch-configs/auth-required`);
        const apiResponse: ApiResponse<DataFetchConfig[]> = response.data;
        return apiResponse.data;
    },

    // 获取活跃配置
    getActiveConfigs: async (): Promise<DataFetchConfig[]> => {
        const response = await api.get(`/data-fetch-configs/active`);
        const apiResponse: ApiResponse<DataFetchConfig[]> = response.data;
        return apiResponse.data;
    },

    // 创建配置
    createConfig: async (config: CreateDataFetchConfigRequest): Promise<DataFetchConfig> => {
        const response = await api.post('/data-fetch-configs', config);
        const apiResponse: ApiResponse<DataFetchConfig> = response.data;
        return apiResponse.data;
    },

    // 更新配置
    updateConfig: async (id: number, config: UpdateDataFetchConfigRequest): Promise<DataFetchConfig> => {
        const response = await api.put(`/data-fetch-configs/${id}`, config);
        const apiResponse: ApiResponse<DataFetchConfig> = response.data;
        return apiResponse.data;
    },

    // 删除配置
    deleteConfig: async (id: number): Promise<void> => {
        const response = await api.delete(`/data-fetch-configs/${id}`);
        // 删除成功
        return;
    },

    // 根据任务ID删除配置
    deleteConfigByTaskId: async (taskId: number): Promise<void> => {
        const response = await api.delete(`/data-fetch-configs/task/${taskId}`);
        // 删除成功
        return;
    },

    // 根据鉴权Key ID获取配置
    getConfigsByAuthKeyId: async (keyId: number): Promise<DataFetchConfig[]> => {
        const response = await api.get(`/data-fetch-configs/auth-key/${keyId}`);
        const apiResponse: ApiResponse<DataFetchConfig[]> = response.data;
        return apiResponse.data;
    },
};

export default dataFetchService;