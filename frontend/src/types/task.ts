export interface ScheduledTask {
    taskId: number;
    taskName: string;
    taskType: 'data_fetch' | 'data_calculation' | 'system_maintenance';
    cronExpression?: string;
    timeoutSeconds?: number;
    status: 'active' | 'inactive';
    parentTaskId?: number;
    parameters?: string;
    description?: string;
    createdTime?: string;
    updatedTime?: string;
}

export interface TaskExecution {
    executionId: number;
    taskId: number;
    triggerType: 'cron' | 'parent' | 'manual';
    parentTaskName: string;
    triggerTime: string;
    actualExecuteTime?: string;
    finishTime?: string;
    executionStatus: 'pending' | 'running' | 'success' | 'failed' | 'timeout' | 'skipped';
    executionResult?: string;
    errorMessage?: string;
    durationMillis?: number; // 新增：执行时长（毫秒）
}

export interface CreateTaskRequest {
    taskName: string;
    taskType: 'data_fetch' | 'data_calculation' | 'system_maintenance';
    cronExpression?: string;
    timeoutSeconds?: number;
    status?: 'active' | 'inactive';
    parentTaskId?: number;
    parameters?: string;
    description?: string;
}

export interface UpdateTaskRequest extends Partial<CreateTaskRequest> {
}

export interface UpdateTaskStatusRequest {
    status: 'active' | 'inactive';
}

// 为了向后兼容，保留 TaskStatusUpdate 别名
export type TaskStatusUpdate = UpdateTaskStatusRequest;