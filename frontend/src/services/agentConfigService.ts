import {api} from './api';
import {isAxiosError} from 'axios';

const API_BASE_URL = '/agent-configs';

class AgentConfigService {
    // 获取所有智能体配置
    async getAllAgentConfigs(): Promise<any[]> {
        try {
            const response = await api.get(`${API_BASE_URL}`);
            return response.data.data;
        } catch (error) {
            console.error('获取所有智能体配置失败:', error);
            throw error;
        }
    }

    // 获取所有活跃的智能体配置
    async getActiveAgentConfigs(): Promise<any[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/active`);
            return response.data.data;
        } catch (error) {
            console.error('获取活跃智能体配置失败:', error);
            throw error;
        }
    }

    // 根据ID获取智能体配置
    async getAgentConfigById(id: number): Promise<any> {
        try {
            const response = await api.get(`${API_BASE_URL}/${id}`);
            return response.data.data;
        } catch (error) {
            console.error('根据ID获取智能体配置失败:', error);
            throw error;
        }
    }

    // 创建智能体配置
    async createAgentConfig(config: any): Promise<any> {
        try {
            const response = await api.post(`${API_BASE_URL}`, config);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '创建智能体配置失败';
                throw new Error(errorMessage);
            }
            console.error('创建智能体配置失败:', error);
            throw error;
        }
    }

    // 更新智能体配置
    async updateAgentConfig(id: number, config: any): Promise<any> {
        try {
            const response = await api.put(`${API_BASE_URL}/${id}`, config);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '更新智能体配置失败';
                throw new Error(errorMessage);
            }
            console.error('更新智能体配置失败:', error);
            throw error;
        }
    }

    // 删除智能体配置
    async deleteAgentConfig(id: number): Promise<void> {
        try {
            await api.delete(`${API_BASE_URL}/${id}`);
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '删除智能体配置失败';
                throw new Error(errorMessage);
            }
            console.error('删除智能体配置失败:', error);
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

    // 获取可绑定的模型列表
    async getAvailableModels(): Promise<Array<{configId: number; modelId: string; displayName: string; provider: string}>> {
        try {
            const response = await api.get(`${API_BASE_URL}/models`);
            return response.data.data;
        } catch (error) {
            console.error('获取可用模型列表失败:', error);
            throw error;
        }
    }

    // 获取指定Agent的子Agent列表
    async getSubAgents(agentId: number): Promise<any[]> {
        try {
            const response = await api.get(`/agent-relations/${agentId}`);
            return response.data.data;
        } catch (error) {
            console.error('获取子Agent列表失败:', error);
            throw error;
        }
    }

    // 保存Agent关系
    async saveAgentRelation(relation: any): Promise<any> {
        try {
            const response = await api.post('/agent-relations', relation);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '保存Agent关系失败';
                throw new Error(errorMessage);
            }
            console.error('保存Agent关系失败:', error);
            throw error;
        }
    }

    // 删除Agent关系
    async deleteAgentRelation(id: number): Promise<void> {
        try {
            await api.delete(`/agent-relations/${id}`);
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '删除Agent关系失败';
                throw new Error(errorMessage);
            }
            console.error('删除Agent关系失败:', error);
            throw error;
        }
    }
}

export const agentConfigService = new AgentConfigService();
