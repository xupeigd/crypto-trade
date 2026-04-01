import {api} from './api';
import {isAxiosError} from 'axios';

const API_BASE_URL = '/freqtrade-configs';

export interface FreqtradeConfig {
    id: number;
    name: string;
    configName?: string;
    exchange: string;
    apiKey?: string;
    apiSecret?: string;
    apiHost?: string;
    apiPort?: number;
    userDataDir?: string;
    startupMode?: string;
    status: string;
    isActive?: boolean;
    createdAt?: string;
    updatedAt?: string;
}

class FreqtradeConfigService {
    // 获取所有配置
    async getAllConfigs(): Promise<any[]> {
        try {
            const response = await api.get(`${API_BASE_URL}`);
            return response.data.data;
        } catch (error) {
            console.error('获取Freqtrade配置失败:', error);
            throw error;
        }
    }

    // 获取所有启用的配置
    async getActiveConfigs(): Promise<any[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/active`);
            return response.data.data;
        } catch (error) {
            console.error('获取启用的Freqtrade配置失败:', error);
            throw error;
        }
    }

    // 根据ID获取配置
    async getConfigById(id: number): Promise<any> {
        try {
            const response = await api.get(`${API_BASE_URL}/${id}`);
            return response.data.data;
        } catch (error) {
            console.error('获取Freqtrade配置失败:', error);
            throw error;
        }
    }

    // 创建配置
    async createConfig(config: any): Promise<any> {
        try {
            const response = await api.post(`${API_BASE_URL}`, config);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '创建Freqtrade配置失败';
                throw new Error(errorMessage);
            }
            console.error('创建Freqtrade配置失败:', error);
            throw error;
        }
    }

    // 更新配置
    async updateConfig(id: number, config: any): Promise<any> {
        try {
            const response = await api.put(`${API_BASE_URL}/${id}`, config);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '更新Freqtrade配置失败';
                throw new Error(errorMessage);
            }
            console.error('更新Freqtrade配置失败:', error);
            throw error;
        }
    }

    // 删除配置
    async deleteConfig(id: number): Promise<void> {
        try {
            await api.delete(`${API_BASE_URL}/${id}`);
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '删除Freqtrade配置失败';
                throw new Error(errorMessage);
            }
            console.error('删除Freqtrade配置失败:', error);
            throw error;
        }
    }

    // 切换启用状态
    async toggleStatus(id: number): Promise<any> {
        try {
            const response = await api.put(`${API_BASE_URL}/${id}/toggle-status`);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '切换状态失败';
                throw new Error(errorMessage);
            }
            console.error('切换状态失败:', error);
            throw error;
        }
    }
}

export const freqtradeConfigService = new FreqtradeConfigService();
