import {api} from '../../services/api';
import {
    ApiResponse,
    ChangeStatsResponse,
    CurrentModeResponse,
    CurrentTradingStyleResponse,
    DateRangeParams,
    ExecutionMode,
    HistoryQueryParams,
    ResetRequest,
    RiskControlInfo,
    RiskModeHistory,
    SetExecutionModeRequest,
    SetExecutionModeResponse,
    SetRiskModeRequest,
    SetRiskModeResponse,
    SetTradingStyleRequest,
    SetTradingStyleResponse,
    TodayStatsResponse,
    TradingStyle,
    TradingStyleHistory
} from './types';

/**
 * AI交易风控配置API服务
 */
export const aiTradingService = {
    /**
     * 获取风控模式配置信息
     */
    getRiskControlInfo: async (): Promise<RiskControlInfo> => {
        const response = await api.get<ApiResponse<RiskControlInfo>>('/risk-control/info');

        // 适配后端ApiResponse结构
        if (response.data && response.data.success && response.data.data) {
            return response.data.data;
        }

        throw new Error('获取风控配置信息失败：无效的响应数据');
    },

    /**
     * 获取当前风控模式
     */
    getCurrentMode: async (): Promise<CurrentModeResponse> => {
        const response = await api.get<ApiResponse<CurrentModeResponse>>('/risk-control/current-mode');

        // 适配新的后端结构：ApiResponse<CurrentModeResponse>
        if (response.data && response.data.success && response.data.data) {
            return response.data.data;
        }

        throw new Error('获取风控模式失败：无效的响应数据');
    },

    /**
     * 设置风控模式
     */
    setRiskMode: async (request: SetRiskModeRequest): Promise<SetRiskModeResponse> => {

        const response = await api.post('/risk-control/mode', request);
        return response.data;
    },

    /**
     * 重置为默认风控模式
     */
    resetToDefault: async (request: ResetRequest): Promise<SetRiskModeResponse> => {

        const response = await api.post('/risk-control/reset', request);
        return response.data;
    },

    /**
     * 获取风控模式变更历史记录
     */
    getHistory: async (params?: HistoryQueryParams): Promise<RiskModeHistory[]> => {
        try {
            const response = await api.get<ApiResponse<RiskModeHistory[]>>('/risk-control/history', {params});

            // 适配后端ApiResponse结构
            if (response.data && response.data.success && Array.isArray(response.data.data)) {
                return response.data.data;
            }

            // 如果失败或数据格式不正确，返回空数组
            console.warn('获取历史记录失败或数据格式不正确:', response.data);
            return [];
        } catch (error) {
            console.error('获取历史记录异常:', error);
            return [];
        }
    },

    /**
     * 获取指定时间范围内的历史记录
     */
    getHistoryByRange: async (params: DateRangeParams): Promise<RiskModeHistory[]> => {
        const response = await api.get('/risk-control/history/range', {params});
        return response.data;
    },

    /**
     * 获取最近一次风控模式变更
     */
    getLastChange: async (): Promise<RiskModeHistory | null> => {
        const response = await api.get('/risk-control/history/last');
        return response.data;
    },

    /**
     * 获取指定时间范围内的变更统计
     */
    getChangeStats: async (params: DateRangeParams): Promise<ChangeStatsResponse> => {
        const response = await api.get('/risk-control/history/count', {params});
        return response.data;
    },

    /**
     * 获取今日统计信息
     */
    getTodayStats: async (): Promise<TodayStatsResponse> => {

        const response = await api.get('/risk-control/history/today');
        return response.data;
    }
};

/**
 * AI交易配置工具函数
 */
export const aiTradingUtils = {
    /**
     * 格式化风控模式显示文本
     */
    formatModeText: (mode: string): string => {
        const modeMap: { [key: string]: string } = {
            'AUTO': '自动模式',
            'MANUAL': '手动模式'
        };
        return modeMap[mode] || mode;
    },

    /**
     * 获取风控模式颜色
     */
    getModeColor: (mode: string): string => {
        const colorMap: { [key: string]: string } = {
            'AUTO': '#52c41a', // 绿色
            'MANUAL': '#fa8c16' // 橙色
        };
        return colorMap[mode] || '#1890ff';
    },

    /**
     * 获取风控模式状态
     */
    getModeStatus: (mode: string): 'success' | 'warning' | 'error' | 'default' => {
        const statusMap: { [key: string]: 'success' | 'warning' | 'error' | 'default' } = {
            'AUTO': 'success',
            'MANUAL': 'warning'
        };
        return statusMap[mode] || 'default';
    },

    /**
     * 解析日期
     */
    parseDate: (dateTime: string | number | any[]): Date | null => {
        if (!dateTime) return null;
        
        try {
            // 处理数组格式 [year, month, day, hour, minute, second, nano]
            if (Array.isArray(dateTime)) {
                if (dateTime.length < 3) return null;
                const year = dateTime[0];
                const month = dateTime[1] - 1; // JS month is 0-indexed
                const day = dateTime[2];
                const hour = dateTime[3] || 0;
                const minute = dateTime[4] || 0;
                const second = dateTime[5] || 0;
                const nano = dateTime[6] || 0;
                const ms = Math.floor(nano / 1000000);
                
                return new Date(year, month, day, hour, minute, second, ms);
            }

            // 处理数字时间戳
            const timestamp = Number(dateTime);
            if (!isNaN(timestamp) && timestamp > 0) {
                 return new Date(timestamp);
            }
            
            // 处理字符串
            const date = new Date(dateTime as string);
            if (!isNaN(date.getTime())) {
                return date;
            }
            
            return null;
        } catch (e) {
            return null;
        }
    },

    /**
     * 格式化时间 yyyy-MM-dd HH:mm:ss
     */
    formatDateTime: (dateTime: string | number | any[]): string => {
        if (!dateTime) return '-';
        try {
            const date = aiTradingUtils.parseDate(dateTime);

            if (!date || isNaN(date.getTime())) {
                return String(dateTime);
            }

            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hour = String(date.getHours()).padStart(2, '0');
            const minute = String(date.getMinutes()).padStart(2, '0');
            const second = String(date.getSeconds()).padStart(2, '0');

            return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
        } catch (error) {
            return String(dateTime);
        }
    },

    /**
     * 格式化日期
     */
    formatDate: (date: string | number | any[]): string => {
        if (!date) return '-';
        try {
            const d = aiTradingUtils.parseDate(date);

            if (!d || isNaN(d.getTime())) {
                return String(date);
            }

            return d.toLocaleDateString('zh-CN');
        } catch (error) {
            return String(date);
        }
    },

    /**
     * 获取相对时间（如：2小时前）
     */
    getRelativeTime: (dateTime: string | number | any[]): string => {
        if (!dateTime) return '-';
        try {
            const now = new Date();
            const target = aiTradingUtils.parseDate(dateTime);

            if (!target || isNaN(target.getTime())) {
                return String(dateTime);
            }

            const diff = now.getTime() - target.getTime();

            const minutes = Math.floor(diff / (1000 * 60));
            const hours = Math.floor(diff / (1000 * 60 * 60));
            const days = Math.floor(diff / (1000 * 60 * 60 * 24));

            if (minutes < 1) return '刚刚';
            if (minutes < 60) return `${minutes}分钟前`;
            if (hours < 24) return `${hours}小时前`;
            if (days < 30) return `${days}天前`;

            return target.toLocaleDateString('zh-CN');
        } catch (error) {
            return String(dateTime);
        }
    },

    /**
     * 截取操作者信息
     */
    truncateOperatorInfo: (info?: string): string => {
        if (!info) return '-';
        if (info.length <= 50) return info;
        return info.substring(0, 50) + '...';
    },

    /**
     * 解析操作者IP地址
     */
    extractIP: (operatorInfo?: string): string => {
        if (!operatorInfo) return '-';
        const ipMatch = operatorInfo.match(/IP:\s*([^\s,]+)/);
        return ipMatch ? ipMatch[1] : '-';
    },

    // ========== 交易风格相关API方法 ==========

    /**
     * 获取当前交易风格
     */
    getCurrentTradingStyle: async (): Promise<CurrentTradingStyleResponse> => {

        const response = await api.get('/risk-control/current-trading-style');
        return response.data;
    },

    /**
     * 设置交易风格
     */
    setTradingStyle: async (request: SetTradingStyleRequest): Promise<SetTradingStyleResponse> => {

        const response = await api.post('/risk-control/trading-style', request);
        return {
            success: true,
            message: '交易风格设置成功',
            history: response.data,
            currentTradingStyle: request.style
        };
    },

    /**
     * 重置交易风格为默认值
     */
    resetTradingStyleToDefault: async (request: ResetRequest): Promise<SetTradingStyleResponse> => {

        const response = await api.post('/risk-control/trading-style/reset', request);
        return {
            success: true,
            message: '交易风格已重置为默认值',
            history: response.data,
            currentTradingStyle: 'NEUTRAL' as TradingStyle
        };
    },

    /**
     * 获取交易风格变更历史记录
     */
    getTradingStyleHistory: async (limit?: number): Promise<TradingStyleHistory[]> => {
        try {
            const params = limit ? {limit} : {};
            const response = await api.get<ApiResponse<TradingStyleHistory[]>>('/risk-control/trading-style/history', {params});

            // 适配后端ApiResponse结构
            if (response.data && response.data.success && Array.isArray(response.data.data)) {
                return response.data.data;
            }

            // 如果失败或数据格式不正确，返回空数组
            console.warn('获取交易风格历史记录失败或数据格式不正确:', response.data);
            return [];
        } catch (error) {
            console.error('获取交易风格历史记录异常:', error);
            return [];
        }
    },

    /**
     * 获取指定时间范围内的交易风格变更历史记录
     */
    getTradingStyleHistoryByTimeRange: async (params: DateRangeParams): Promise<TradingStyleHistory[]> => {

        const response = await api.get('/risk-control/trading-style/history/timerange', {params});
        return response.data;
    },

    /**
     * 获取最近一次交易风格变更
     */
    getLastTradingStyleChange: async (): Promise<TradingStyleHistory | null> => {

        const response = await api.get('/risk-control/trading-style/history/last');
        return response.data;
    },

    /**
     * 统计指定时间范围内的交易风格变更次数
     */
    countTradingStyleChangesInPeriod: async (params: DateRangeParams): Promise<ChangeStatsResponse> => {

        const response = await api.get('/risk-control/trading-style/history/count', {params});
        return response.data;
    },

    /**
     * 获取今日交易风格变更统计
     */
    getTodayTradingStyleStatistics: async (): Promise<TodayStatsResponse> => {

        const response = await api.get('/risk-control/trading-style/history/today');
        return response.data;
    },

    // ========== 执行模式相关API方法 ==========

    /**
     * 获取当前执行模式
     */
    getCurrentExecutionMode: async (): Promise<ExecutionMode> => {
        const response = await api.get<ApiResponse<ExecutionMode>>('/risk-control/execution-mode');
        if (response.data && response.data.success && response.data.data) {
            return response.data.data;
        }
        throw new Error('获取执行模式失败');
    },

    /**
     * 设置执行模式
     */
    setExecutionMode: async (request: SetExecutionModeRequest): Promise<SetExecutionModeResponse> => {
        const response = await api.post<ApiResponse<string>>('/risk-control/execution-mode', request);
        return {
            success: response.data?.success ?? true,
            message: response.data?.message ?? '执行模式设置成功'
        };
    },

    /**
     * 重置执行模式为全局配置
     */
    resetExecutionMode: async (request: ResetRequest): Promise<SetExecutionModeResponse> => {
        const response = await api.post<ApiResponse<string>>('/risk-control/execution-mode/reset', request);
        return {
            success: response.data?.success ?? true,
            message: response.data?.message ?? '执行模式已重置为全局配置'
        };
    }
};

export default aiTradingService;