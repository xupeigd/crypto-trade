import {api} from './api';

export interface DashboardStatistics {
    activeTasks: number;
    totalApiKeys: number;
    dataFetchConfigs: number;
    todaySuccessExecutions: number;
    todayTotalExecutions: number;
    todaySuccessRate: number;
}

export interface ApiResponse<T> {
    code: number;
    message: string;
    data: T;
    success: boolean;
}

export const dashboardService = {
    async getStatistics(): Promise<DashboardStatistics> {
        const response = await api.get('/dashboard/statistics');
        const apiResponse: ApiResponse<DashboardStatistics> = response.data;
        return apiResponse.data;
    },
};

export default dashboardService;