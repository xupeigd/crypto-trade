import api from './api';
import {Execution} from '../types/execution';

export interface ApiResponse<T> {
    code: number;
    message: string;
    data: T;
    success: boolean;
}

export const executionService = {
    // 获取所有执行记录
    getAllExecutions: async (): Promise<Execution[]> => {
        const response = await api.get('/executions');
        const apiResponse: ApiResponse<Execution[]> = response.data;
        return apiResponse.data;
    },

    // 根据ID获取执行记录
    getExecutionById: async (id: number): Promise<Execution> => {
        const response = await api.get(`/executions/${id}`);
        const apiResponse: ApiResponse<Execution> = response.data;
        return apiResponse.data;
    },

    // 根据任务ID获取执行记录
    getExecutionsByTaskId: async (taskId: number): Promise<Execution[]> => {
        const response = await api.get(`/executions/task/${taskId}`);
        const apiResponse: ApiResponse<Execution[]> = response.data;
        return apiResponse.data;
    },

    // 根据状态获取执行记录
    getExecutionsByStatus: async (status: string): Promise<Execution[]> => {
        const response = await api.get(`/executions/status/${status}`);
        const apiResponse: ApiResponse<Execution[]> = response.data;
        return apiResponse.data;
    },

    // 根据触发类型获取执行记录
    getExecutionsByTriggerType: async (triggerType: string): Promise<Execution[]> => {
        const response = await api.get(`/executions/trigger/${triggerType}`);
        const apiResponse: ApiResponse<Execution[]> = response.data;
        return apiResponse.data;
    },

    // 获取最近执行记录
    getRecentExecutions: async (taskId: number, limit: number = 10): Promise<Execution[]> => {
        const response = await api.get(`/executions/task/${taskId}/recent?limit=${limit}`);
        const apiResponse: ApiResponse<Execution[]> = response.data;
        return apiResponse.data;
    },

    // 获取指定时间范围内的执行记录
    getRecentExecutionsSince: async (hours: number = 1): Promise<Execution[]> => {
        const response = await api.get(`/executions/recent?hours=${hours}`);
        const apiResponse: ApiResponse<Execution[]> = response.data;
        return apiResponse.data;
    },

    // 获取成功执行次数
    getSuccessfulExecutionCount: async (taskId: number, hours: number = 24): Promise<number> => {
        const response = await api.get(`/executions/task/${taskId}/success-count?hours=${hours}`);
        const apiResponse: ApiResponse<number> = response.data;
        return apiResponse.data;
    },

    // 删除执行记录
    deleteExecution: async (id: number): Promise<void> => {
        await api.delete(`/executions/${id}`);
        // 删除成功
        return;
    },
};