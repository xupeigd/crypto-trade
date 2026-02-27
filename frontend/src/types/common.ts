/**
 * 刷新状态类型
 */
export type RefreshStatus = 'idle' | 'loading' | 'success' | 'error' | 'active' | 'paused';

/**
 * 通用的API响应类型
 */
export interface ApiResponse<T = any> {
    code: string | number;
    message: string;
    success?: boolean;
    data: T;
    timestamp: number;
}

/**
 * 分页请求参数
 */
export interface PaginationParams {
    page: number;
    size: number;
}

/**
 * 分页响应数据
 */
export interface PaginatedResponse<T> {
    list: T[];
    total: number;
    page: number;
    size: number;
}