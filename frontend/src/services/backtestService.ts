import {api} from './api';

export interface BacktestTaskRequest {
    taskName: string;
    freqtradeConfigId: number;
    strategyConfigId: number;
    timeRange: string;
    timeframe: string;
}

export interface BacktestTaskResponse {
    id: number;
    taskName: string;
    freqtradeConfigId: number;
    freqtradeConfigName?: string;
    strategyConfigId: number;
    strategyName?: string;
    timeRange: string;
    timeframe: string;
    status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
    errorMsg?: string;
    createdAt?: string;
    finishedAt?: string;
    
    totalProfitAbs?: number;
    totalProfitPct?: number;
    maxDrawdownAbs?: number;
    maxDrawdownPct?: number;
    winRate?: number;
    totalTrades?: number;
}

export interface BacktestResultResponse {
    taskId: number;
    totalProfitAbs: number;
    totalProfitPct: number;
    maxDrawdownAbs: number;
    maxDrawdownPct: number;
    winRate: number;
    totalTrades: number;
    sharpeRatio?: number;
    strategyName?: string;
    freqtradeConfigName?: string;
    timeRange?: string;
    timeframe?: string;
    dailyProfit: any[];
    trades: any[];
    logs: string;
}

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

class BacktestService {
    async createBacktest(request: BacktestTaskRequest): Promise<number> {
        const response = await api.post('/api/backtests', request);
        return response.data.data;
    }

    async getBacktests(status?: string, page: number = 0, size: number = 10): Promise<PageResponse<BacktestTaskResponse>> {
        const params = new URLSearchParams();
        if (status) params.append('status', status);
        params.append('page', page.toString());
        params.append('size', size.toString());
        
        const response = await api.get(`/api/backtests?${params.toString()}`);
        return response.data.data;
    }

    async getBacktestResult(id: number): Promise<BacktestResultResponse> {
        const response = await api.get(`/api/backtests/${id}/result`);
        return response.data.data;
    }

    async stopBacktest(id: number): Promise<void> {
        await api.post(`/api/backtests/${id}/stop`);
    }

    async deleteBacktest(id: number): Promise<void> {
        await api.delete(`/api/backtests/${id}`);
    }
}

export const backtestService = new BacktestService();
