import api from './api';
import {ApiResponse} from '../types/common';
import {CreateTaskRequest, ScheduledTask, TaskExecution, TaskStatusUpdate, UpdateTaskRequest} from '../types/task';

export const taskService = {
    // 获取所有任务
    getAllTasks: async (): Promise<ScheduledTask[]> => {
        const response = await api.get<ApiResponse<ScheduledTask[]>>('/tasks');
        return response.data.data || [];
    },

    // 根据ID获取任务
    getTaskById: async (id: number): Promise<ScheduledTask | null> => {
        const response = await api.get<ApiResponse<ScheduledTask>>(`/tasks/${id}`);
        return response.data.data || null;
    },

    // 根据名称获取任务
    getTaskByName: async (name: string): Promise<ScheduledTask | null> => {
        const response = await api.get<ApiResponse<ScheduledTask>>(`/tasks/name/${name}`);
        return response.data.data || null;
    },

    // 根据类型获取任务
    getTasksByType: async (type: string): Promise<ScheduledTask[]> => {
        const response = await api.get<ApiResponse<ScheduledTask[]>>(`/tasks/type/${type}`);
        return response.data.data || [];
    },

    // 获取活跃任务
    getActiveTasks: async (): Promise<ScheduledTask[]> => {
        const response = await api.get<ApiResponse<ScheduledTask[]>>('/tasks/active');
        return response.data.data || [];
    },

    // 获取子任务
    getChildTasks: async (parentTaskId: number): Promise<ScheduledTask[]> => {
        const response = await api.get<ApiResponse<ScheduledTask[]>>(`/tasks/${parentTaskId}/children`);
        return response.data.data || [];
    },

    // 创建任务
    createTask: async (task: CreateTaskRequest): Promise<ScheduledTask | null> => {
        const response = await api.post<ApiResponse<ScheduledTask>>('/tasks', task);
        return response.data.data || null;
    },

    // 更新任务
    updateTask: async (id: number, task: UpdateTaskRequest): Promise<ScheduledTask | null> => {
        const response = await api.put<ApiResponse<ScheduledTask>>(`/tasks/${id}`, task);
        return response.data.data || null;
    },

    // 删除任务
    deleteTask: async (id: number): Promise<boolean> => {
        const response = await api.delete<ApiResponse<void>>(`/tasks/${id}`);
        return response.data.code === '0' || false;
    },

    // 更新任务状态
    updateTaskStatus: async (id: number, status: 'active' | 'inactive'): Promise<ScheduledTask | null> => {
        const request: TaskStatusUpdate = {status};
        const response = await api.put<ApiResponse<ScheduledTask>>(`/tasks/${id}/status`, request);
        return response.data.data || null;
    },

    // 执行任务
    executeTask: async (id: number): Promise<TaskExecution | null> => {
        const response = await api.post<ApiResponse<TaskExecution>>(`/tasks/${id}/execute`);
        return response.data.data || null;
    },

    // 触发任务
    triggerTask: async (id: number): Promise<boolean> => {
        const response = await api.post<ApiResponse<void>>(`/tasks/${id}/trigger`);
        return response.data.code === '0' || false;
    },
};