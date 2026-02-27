// 代理服务配置类型定义
export interface ProxyServiceConfig {
    proxyId?: number;
    proxyName: string;
    proxyType: 'HTTP' | 'SOCKS5';
    serverHost: string;
    serverPort: number;
    status: 'active' | 'inactive';
    description?: string;
    createdTime?: string;
    updatedTime?: string;
    associatedTaskCount?: number;
}

export interface CreateProxyServiceConfigRequest {
    proxyName: string;
    proxyType: 'HTTP' | 'SOCKS5';
    serverHost: string;
    serverPort: number;
    status: 'active' | 'inactive';
    description?: string;
}

export interface UpdateProxyServiceConfigRequest extends Partial<CreateProxyServiceConfigRequest> {
}

export interface TestConnectionResponse {
    success: boolean;
    message: string;
}

// 关联任务信息
export interface AssociatedTask {
    taskId?: number;
    taskName: string;
    taskType: 'data_fetch' | 'data_calculation';
    cronExpression?: string;
    timeoutSeconds?: number;
    status: 'active' | 'inactive';
    parentTaskId?: number;
    parameters?: string;
    description?: string;
    createdTime?: string;
    updatedTime?: string;
}

// 任务数量响应
export interface TaskCountResponse {
    count: number;
}