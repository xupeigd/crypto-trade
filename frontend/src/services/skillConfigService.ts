import {api} from './api';
import {isAxiosError} from 'axios';

const API_BASE_URL = '/skill-configs';

export interface SkillConfig {
    id?: number;
    name: string;
    description?: string;
    skillPrompt: string;
    outputFormat?: string;
    requiredTools?: string;
    executionHint?: string;
    isActive?: boolean;
    createdAt?: string;
    updatedAt?: string;
}

class SkillConfigService {
    // 获取所有技能配置
    async getAllSkillConfigs(): Promise<SkillConfig[]> {
        try {
            const response = await api.get(`${API_BASE_URL}`);
            return response.data.data;
        } catch (error) {
            console.error('获取所有技能配置失败:', error);
            throw error;
        }
    }

    // 获取所有活跃的技能配置
    async getActiveSkillConfigs(): Promise<SkillConfig[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/active`);
            return response.data.data;
        } catch (error) {
            console.error('获取活跃技能配置失败:', error);
            throw error;
        }
    }

    // 根据ID获取技能配置
    async getSkillConfigById(id: number): Promise<SkillConfig> {
        try {
            const response = await api.get(`${API_BASE_URL}/${id}`);
            return response.data.data;
        } catch (error) {
            console.error('根据ID获取技能配置失败:', error);
            throw error;
        }
    }

    // 创建技能配置
    async createSkillConfig(config: SkillConfig): Promise<SkillConfig> {
        try {
            const response = await api.post(`${API_BASE_URL}`, config);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '创建技能配置失败';
                throw new Error(errorMessage);
            }
            console.error('创建技能配置失败:', error);
            throw error;
        }
    }

    // 更新技能配置
    async updateSkillConfig(id: number, config: SkillConfig): Promise<SkillConfig> {
        try {
            const response = await api.put(`${API_BASE_URL}/${id}`, config);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '更新技能配置失败';
                throw new Error(errorMessage);
            }
            console.error('更新技能配置失败:', error);
            throw error;
        }
    }

    // 删除技能配置
    async deleteSkillConfig(id: number): Promise<void> {
        try {
            await api.delete(`${API_BASE_URL}/${id}`);
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '删除技能配置失败';
                throw new Error(errorMessage);
            }
            console.error('删除技能配置失败:', error);
            throw error;
        }
    }

    // 获取可用的工具列表
    async getAvailableTools(): Promise<Array<{name: string; description: string}>> {
        try {
            const response = await api.get(`${API_BASE_URL}/tools`);
            return response.data.data;
        } catch (error) {
            console.error('获取可用工具列表失败:', error);
            throw error;
        }
    }
}

export const skillConfigService = new SkillConfigService();
