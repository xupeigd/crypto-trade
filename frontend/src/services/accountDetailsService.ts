import axios from 'axios';

// API基础配置
const API_BASE_URL = '';

// 创建axios实例
const accountDetailsApi = axios.create({
    baseURL: API_BASE_URL,
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 请求拦截器
accountDetailsApi.interceptors.request.use(
    (config) => {
        // console.log(`API请求: ${config.method?.toUpperCase()} ${config.url}`);
        return config;
    },
    (error) => {
        console.error('API请求错误:', error);
        return Promise.reject(error);
    }
);

// 响应拦截器
accountDetailsApi.interceptors.response.use(
    (response) => {
        // console.log(`API响应: ${response.status} ${response.config.url}`);
        return response;
    },
    (error) => {
        console.error('API响应错误:', error);
        return Promise.reject(error);
    }
);

// API响应类型定义
export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data: T;
    timestamp: string;
}

// 账户详情类型定义
export interface AccountDetails {
    totalEquity: number;        // 账户权益（账户估值）
    usedMargin: number;         // 已用保证金
    availableBalance: number;   // 可用余额
    unrealizedPnl: number;      // 未实现盈亏
    marginRatio: number;        // 保证金使用率（百分比）
    lastUpdateTime: string;     // 最后更新时间
}

// API服务函数
export const accountDetailsService = {
    /**
     * 获取账户详情信息
     * @param apiKeyId API Key ID
     */
    getAccountDetails: (apiKeyId: number) => {
        return accountDetailsApi.get<ApiResponse<AccountDetails>>(`/cex-balances/${apiKeyId}/account-details`);
    }
};

// 独立导出函数，方便直接使用
export const getAccountDetails = (apiKeyId: number) => accountDetailsService.getAccountDetails(apiKeyId);

export default accountDetailsService;