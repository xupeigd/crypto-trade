import {api} from './api';
import {isAxiosError} from 'axios';
import {AIInfoModel} from '../types/aiInfoModel';

const API_BASE_URL = '/ai-model-configs';

class AiModelConfigService {
    // API密钥脱敏显示工具函数
    static maskApiKey(apiKey: string): string {
        if (!apiKey || apiKey.length < 8) {
            return apiKey;
        }

        const visibleChars = 4; // 显示前4个字符
        const maskedChars = apiKey.length - visibleChars;
        return apiKey.slice(0, visibleChars) + '•'.repeat(maskedChars);
    }

    // 判断是否为脱敏的API密钥
    static isMaskedApiKey(apiKey: string): boolean {
        return apiKey?.includes('•');
    }

    // 获取所有AI模型配置
    async getAllModels(): Promise<AIInfoModel[]> {
        try {
            const response = await api.get(`${API_BASE_URL}`);
            return response.data.data;
        } catch (error) {
            console.error('获取所有AI模型配置失败:', error);
            throw error;
        }
    }

    // 获取所有活跃的AI模型配置
    async getActiveModels(): Promise<AIInfoModel[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/active`);
            return response.data.data;
        } catch (error) {
            console.error('获取活跃AI模型配置失败:', error);
            throw error;
        }
    }

    // 根据modelId获取模型配置
    async getModelByModelId(modelId: string): Promise<AIInfoModel> {
        try {
            const response = await api.get(`${API_BASE_URL}/model/${modelId}`);
            return response.data.data;
        } catch (error) {
            console.error('根据modelId获取模型配置失败:', error);
            throw error;
        }
    }

    // 获取默认模型配置
    async getDefaultModel(): Promise<AIInfoModel> {
        try {
            const response = await api.get(`${API_BASE_URL}/default`);
            return response.data.data;
        } catch (error) {
            console.error('获取默认模型配置失败:', error);
            throw error;
        }
    }

    // 创建新的AI模型配置
    async createModel(modelInfo: Omit<AIInfoModel, 'configId'>): Promise<AIInfoModel> {
        try {
            const response = await api.post(`${API_BASE_URL}`, modelInfo);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '创建模型配置失败';
                throw new Error(errorMessage);
            }
            console.error('创建AI模型配置失败:', error);
            throw error;
        }
    }

    // 更新AI模型配置
    async updateModel(configId: number, modelInfo: Omit<AIInfoModel, 'configId'>): Promise<AIInfoModel> {
        try {
            const response = await api.put(`${API_BASE_URL}/${configId}`, modelInfo);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '更新模型配置失败';
                throw new Error(errorMessage);
            }
            console.error('更新AI模型配置失败:', error);
            throw error;
        }
    }

    // 删除AI模型配置（软删除）
    async deleteModel(configId: number): Promise<void> {
        try {
            await api.delete(`${API_BASE_URL}/${configId}`);
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '删除模型配置失败';
                throw new Error(errorMessage);
            }
            console.error('删除AI模型配置失败:', error);
            throw error;
        }
    }

    // 设置默认模型
    async setDefaultModel(configId: number): Promise<AIInfoModel> {
        try {
            const response = await api.put(`${API_BASE_URL}/${configId}/set-default`);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '设置默认模型失败';
                throw new Error(errorMessage);
            }
            console.error('设置默认模型失败:', error);
            throw error;
        }
    }

    // 切换模型活跃状态
    async toggleModelStatus(configId: number): Promise<AIInfoModel> {
        try {
            const response = await api.put(`${API_BASE_URL}/${configId}/toggle-status`);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '切换模型状态失败';
                throw new Error(errorMessage);
            }
            console.error('切换模型状态失败:', error);
            throw error;
        }
    }

    // 获取本地模型列表
    async getLocalModels(): Promise<AIInfoModel[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/local`);
            return response.data.data;
        } catch (error) {
            console.error('获取本地模型列表失败:', error);
            throw error;
        }
    }

    // 获取远端模型列表
    async getRemoteModels(): Promise<AIInfoModel[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/remote`);
            return response.data.data;
        } catch (error) {
            console.error('获取远端模型列表失败:', error);
            throw error;
        }
    }

    // 测试模型连接
    async testModelConnection(configId: number): Promise<{ success: boolean; message: string }> {
        try {
            const response = await api.post(`${API_BASE_URL}/${configId}/test-connection`);
            return response.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '测试连接失败';
                return {success: false, message: errorMessage};
            }
            console.error('测试模型连接失败:', error);
            return {success: false, message: '测试连接时发生错误'};
        }
    }

    // 获取支持的模型类型
    async getModelTypes(): Promise<string[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/model-types`);
            return response.data.data;
        } catch (error) {
            console.error('获取支持的模型类型失败:', error);
            throw error;
        }
    }

    // 获取支持的API格式
    async getApiFormats(): Promise<string[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/api-formats`);
            return response.data.data;
        } catch (error) {
            console.error('获取支持的API格式失败:', error);
            throw error;
        }
    }
}

export const aiModelConfigService = new AiModelConfigService();