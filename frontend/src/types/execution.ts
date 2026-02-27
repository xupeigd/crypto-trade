export interface Execution {
    executionId?: number;
    taskId: number;
    triggerType: 'cron' | 'parent' | 'manual';
    parentTaskName: string;
    triggerTime: string;
    actualExecuteTime?: string;
    finishTime?: string;
    executionStatus: 'pending' | 'running' | 'success' | 'failed' | 'timeout' | 'skipped';
    executionResult?: string;
    errorMessage?: string;
    createdTime?: string;
}

export interface ExecutionStats {
    total: number;
    success: number;
    failed: number;
    running: number;
    pending: number;
}