import {api} from './api';
import {isAxiosError} from 'axios';

const API_BASE_URL = '/strategy-execution';

/**
 * Freqtrade实例接口
 */
export interface FreqtradeInstance {
    id: number;
    apiKeyId: number;
    freqtradeConfigId: number;
    strategyConfigId: number;
    instanceId: string;
    instanceName: string;
    instanceDir?: string;
    containerId?: string;
    apiPort: number;
    status: 'STARTING' | 'RUNNING' | 'STOPPED' | 'ERROR';
    pid?: string;
    isDryRun?: boolean;
    startedAt?: string;
    stoppedAt?: string;
    lastError?: string;
    profitRatio?: number;
    totalTrades?: number;
    winningTrades?: number;
    createdAt?: string;
    updatedAt?: string;
}

/**
 * Freqtrade配置接口
 */
export interface FreqtradeConfig {
    id: number;
    configName: string;
    startupMode: string;
    processPath?: string;
    dockerImage?: string;
    configFile?: string;
    strategyDir?: string;
    apiHost?: string;
    apiPort?: number;
    dbPath?: string;
    isActive?: boolean;
    description?: string;
}

/**
 * 策略配置接口
 */
export interface StrategyConfig {
    id: number;
    title: string;
    strategyName: string;
    version?: string;
    prompt?: string;
    strategyCode?: string;
    description?: string;
    isActive?: boolean;
}

/**
 * 交易信息接口
 */
export interface TradeInfo {
    tradeId: number;
    pair: string;
    amount: number;
    amountRequested: number;
    openRate: number;
    currentRate: number;
    profit: number;
    profitPct: number;
    openDate: string;
    isOpen: boolean;
}

/**
 * 盈亏统计接口
 */
export interface ProfitSummary {
    profitAll: number;
    profitRatio: number;
    tradeCount: number;
    winningTrades: number;
}

/**
 * 实例详情接口
 */
export interface InstanceDetail {
    instance: FreqtradeInstance;
    config: FreqtradeConfig;
    strategy: StrategyConfig;
}

class StrategyExecutionService {
    /**
     * 启动策略执行
     */
    async startExecution(apiKeyId: number, freqtradeConfigId: number, strategyConfigId: number, isDryRun: boolean): Promise<FreqtradeInstance> {
        try {
            const response = await api.post(`${API_BASE_URL}/start`, null, {
                params: {apiKeyId, freqtradeConfigId, strategyConfigId, isDryRun}
            });
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '启动策略执行失败';
                throw new Error(errorMessage);
            }
            throw error;
        }
    }

    /**
     * 停止策略执行
     */
    async stopExecution(instanceId: number): Promise<FreqtradeInstance> {
        try {
            const response = await api.post(`${API_BASE_URL}/stop/${instanceId}`);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '停止策略执行失败';
                throw new Error(errorMessage);
            }
            throw error;
        }
    }

    /**
     * 重启策略实例
     */
    async restartExecution(instanceId: number): Promise<FreqtradeInstance> {
        try {
            const response = await api.post(`${API_BASE_URL}/restart/${instanceId}`);
            return response.data.data;
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '启动实例失败';
                throw new Error(errorMessage);
            }
            throw error;
        }
    }

    /**
     * 删除实例
     */
    async deleteInstance(instanceId: number): Promise<void> {
        try {
            await api.delete(`${API_BASE_URL}/${instanceId}`);
        } catch (error) {
            if (isAxiosError(error)) {
                const errorMessage = error.response?.data?.message || '删除实例失败';
                throw new Error(errorMessage);
            }
            throw error;
        }
    }

    /**
     * 获取所有实例
     */
    async getAllInstances(): Promise<FreqtradeInstance[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/instances`);
            return response.data.data;
        } catch (error) {
            console.error('获取实例列表失败:', error);
            throw error;
        }
    }

    /**
     * 获取运行中的实例
     */
    async getRunningInstances(): Promise<FreqtradeInstance[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/instances/running`);
            return response.data.data;
        } catch (error) {
            console.error('获取运行中实例失败:', error);
            throw error;
        }
    }

    /**
     * 获取实例详情
     */
    async getInstanceDetail(instanceId: number): Promise<InstanceDetail> {
        try {
            const response = await api.get(`${API_BASE_URL}/instances/${instanceId}`);
            return response.data.data;
        } catch (error) {
            console.error('获取实例详情失败:', error);
            throw error;
        }
    }

    /**
     * 获取实例状态（同步更新）
     */
    async syncAndGetStatus(instanceId: number): Promise<FreqtradeInstance> {
        try {
            const response = await api.get(`${API_BASE_URL}/status/${instanceId}`);
            return response.data.data;
        } catch (error) {
            console.error('获取实例状态失败:', error);
            throw error;
        }
    }

    /**
     * 获取实例日志
     */
    async getInstanceLogs(instanceId: number, tail: number = 200): Promise<string> {
        try {
            const response = await api.get(`${API_BASE_URL}/logs/${instanceId}`, {
                params: {tail}
            });
            return response.data.data;
        } catch (error) {
            console.error('获取实例日志失败:', error);
            throw error;
        }
    }

    /**
     * 搜索日志
     */
    async searchLogs(instanceId: number, keyword: string): Promise<string[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/logs/${instanceId}/search`, {
                params: {keyword}
            });
            return response.data.data;
        } catch (error) {
            console.error('搜索日志失败:', error);
            throw error;
        }
    }

    /**
     * 获取交易记录
     */
    async getTrades(instanceId: number, limit?: number): Promise<TradeInfo[]> {
        try {
            const params = limit ? {limit} : {};
            const response = await api.get(`${API_BASE_URL}/trades/${instanceId}`, {params});
            return response.data.data;
        } catch (error) {
            console.error('获取交易记录失败:', error);
            throw error;
        }
    }

    /**
     * 获取盈亏统计
     */
    async getProfit(instanceId: number): Promise<ProfitSummary> {
        try {
            const response = await api.get(`${API_BASE_URL}/profit/${instanceId}`);
            return response.data.data;
        } catch (error) {
            console.error('获取盈亏统计失败:', error);
            throw error;
        }
    }

    /**
     * 获取当前持仓
     */
    async getPositions(instanceId: number): Promise<TradeInfo[]> {
        try {
            const response = await api.get(`${API_BASE_URL}/positions/${instanceId}`);
            return response.data.data;
        } catch (error) {
            console.error('获取当前持仓失败:', error);
            throw error;
        }
    }

    /**
     * 获取实例的config.json内容
     */
    async getInstanceConfig(instanceId: number): Promise<string> {
        try {
            const response = await api.get(`${API_BASE_URL}/config/${instanceId}`);
            return response.data.data;
        } catch (error) {
            console.error('获取config.json失败:', error);
            throw error;
        }
    }
}

export const strategyExecutionService = new StrategyExecutionService();
