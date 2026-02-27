import {
    ApiResponse,
    AuditStatus,
    BatchOperationRequest,
    OrderSide,
    OrderType,
    RiskControlOrder,
    RiskControlStatistics,
    RiskLevel
} from '../types/riskControl';
import {CurrentModeResponse} from '../pages/ai-trading/types';
import {api} from './api';

/**
 * 风控控制服务
 */
export class RiskControlService {
    private static readonly BASE_URL = '/risk-control';
    private static riskModeCache: { data: CurrentModeResponse; timestamp: number } | null = null;
    private static readonly CACHE_DURATION = 5000; // 5秒缓存，提高实时性

    /**
     * 清除风控模式缓存（强制刷新）
     */
    static clearRiskModeCache(): void {
        RiskControlService.riskModeCache = null;
    }

    /**
     * 获取待审核订单列表
     */
    async getPendingOrders(): Promise<ApiResponse<RiskControlOrder[]>> {
        try {
            const response = await api.get(`${RiskControlService.BASE_URL}/orders/pending`);
            return response.data;
        } catch (error: any) {
            console.error('获取待审核订单失败:', error);
            throw error;
        }
    }

    /**
     * 根据ID获取订单详情
     */
    async getOrderById(orderId: number): Promise<ApiResponse<RiskControlOrder>> {
        try {
            const response = await api.get(`${RiskControlService.BASE_URL}/orders/${orderId}`);
            return response.data;
        } catch (error: any) {
            console.error('获取订单详情失败:', error);
            throw error;
        }
    }

    /**
     * 【新增】根据recordId查询所有关联的风控订单
     */
    async getOrdersByRecordId(recordId: number): Promise<ApiResponse<RiskControlOrder[]>> {
        try {
            const response = await api.get(`${RiskControlService.BASE_URL}/orders/by-record-id/${recordId}`);
            return response.data;
        } catch (error: any) {
            console.error('根据recordId查询风控订单失败:', error);
            throw error;
        }
    }

    /**
     * 审核通过订单
     */
    async approveOrder(orderId: number): Promise<ApiResponse<void>> {
        try {
            const response = await api.post(`${RiskControlService.BASE_URL}/orders/${orderId}/approve`);
            return response.data;
        } catch (error: any) {
            console.error('审核通过订单失败:', error);
            throw error;
        }
    }

    /**
     * 驳回订单
     */
    async rejectOrder(orderId: number, reason: string): Promise<ApiResponse<void>> {
        try {
            const response = await api.post(`${RiskControlService.BASE_URL}/orders/${orderId}/reject`, {reason});
            return response.data;
        } catch (error: any) {
            console.error('驳回订单失败:', error);
            throw error;
        }
    }

    /**
     * 获取审核统计信息
     */
    async getStatistics(): Promise<ApiResponse<RiskControlStatistics>> {
        try {
            const response = await api.get(`${RiskControlService.BASE_URL}/orders/statistics`);
            return response.data;
        } catch (error: any) {
            console.error('获取审核统计信息失败:', error);
            throw error;
        }
    }

    /**
     * 批量审核操作
     */
    async batchOperation(request: BatchOperationRequest): Promise<ApiResponse<any>> {
        try {
            const response = await api.post(`${RiskControlService.BASE_URL}/orders/batch`, request);
            return response.data;
        } catch (error: any) {
            console.error('批量审核操作失败:', error);
            throw error;
        }
    }

    /**
     * 创建风控订单（临时方法，用于测试）
     */
    async createRiskOrder(orderData: any): Promise<ApiResponse<any>> {
        try {
            const response = await api.post(`${RiskControlService.BASE_URL}/orders`, orderData);
            return response.data;
        } catch (error: any) {
            console.error('创建风控订单失败:', error);
            throw error;
        }
    }

    /**
     * 创建测试风控订单
     */
    async createTestOrder(): Promise<ApiResponse<any>> {
        try {
            const response = await api.post(`${RiskControlService.BASE_URL}/orders/test`);
            return response.data;
        } catch (error: any) {
            console.error('创建测试风控订单失败:', error);
            throw error;
        }
    }

    /**
     * 格式化风控等级显示文本
     */
    getRiskLevelText(riskLevel: RiskLevel): string {
        switch (riskLevel) {
            case RiskLevel.HIGH:
                return '高风险';
            case RiskLevel.MEDIUM:
                return '中风险';
            case RiskLevel.LOW:
                return '低风险';
            default:
                return '未知';
        }
    }

    /**
     * 格式化审核状态显示文本
     */
    getAuditStatusText(status: AuditStatus): string {
        switch (status) {
            case AuditStatus.PENDING:
                return '待审核';
            case AuditStatus.APPROVED:
                return '已通过';
            case AuditStatus.REJECTED:
                return '已驳回';
            default:
                return '未知';
        }
    }

    /**
     * 获取风控等级颜色
     */
    getRiskLevelColor(riskLevel: RiskLevel): string {
        switch (riskLevel) {
            case RiskLevel.HIGH:
                return 'red';
            case RiskLevel.MEDIUM:
                return 'orange';
            case RiskLevel.LOW:
                return 'green';
            default:
                return 'default';
        }
    }

    /**
     * 获取审核状态颜色
     */
    getAuditStatusColor(status: AuditStatus): string {
        switch (status) {
            case AuditStatus.PENDING:
                return 'processing';
            case AuditStatus.APPROVED:
                return 'success';
            case AuditStatus.REJECTED:
                return 'error';
            default:
                return 'default';
        }
    }

    /**
     * 格式化订单类型显示文本
     */
    getOrderTypeText(orderType: OrderType): string {
        switch (orderType) {
            case OrderType.LIMIT:
                return '限价单';
            case OrderType.MARKET:
                return '市价单';
            default:
                return '未知';
        }
    }

    /**
     * 格式化订单方向显示文本
     */
    getOrderSideText(side: OrderSide): string {
        switch (side) {
            case OrderSide.BUY:
                return '开多';
            case OrderSide.SELL:
                return '开空';
            default:
                return '未知';
        }
    }

    /**
     * 格式化时间显示
     */
    formatDateTime(timeStr?: string): string {
        if (!timeStr) return '-';
        try {
            const date = new Date(timeStr);
            return date.toLocaleString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch {
            return timeStr;
        }
    }

    /**
     * 格式化数量显示
     */
    formatQuantity(quantity: string): string {
        try {
            const num = parseFloat(quantity);
            if (isNaN(num)) return quantity;
            return num.toLocaleString('zh-CN', {
                minimumFractionDigits: 0,
                maximumFractionDigits: 8
            });
        } catch {
            return quantity;
        }
    }

    /**
     * 格式化价格显示
     */
    formatPrice(price?: string): string {
        if (!price) return '-';
        try {
            const num = parseFloat(price);
            if (isNaN(num)) return price;
            return num.toLocaleString('zh-CN', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 8
            });
        } catch {
            return price;
        }
    }

    /**
     * 获取当前风控模式（带缓存）
     */
    async getCurrentRiskMode(): Promise<CurrentModeResponse> {
        try {
            const now = Date.now();

            // 检查缓存是否有效
            if (RiskControlService.riskModeCache &&
                (now - RiskControlService.riskModeCache.timestamp) < RiskControlService.CACHE_DURATION) {

                // 验证缓存数据有效性
                if (RiskControlService.riskModeCache.data &&
                    RiskControlService.riskModeCache.data.currentMode) {
                    return RiskControlService.riskModeCache.data;
                } else {
                    // 缓存数据无效，清除缓存
                    RiskControlService.riskModeCache = null;
                }
            }

            // 重新获取数据
            const response = await api.get<ApiResponse<CurrentModeResponse>>('/risk-control/current-mode');

            // 适配新的后端结构：ApiResponse<CurrentModeResponse>
            if (!response.data || !response.data.success || !response.data.data) {
                throw new Error('API响应格式不正确');
            }

            const modeData = response.data.data;

            // 验证响应数据
            if (!modeData.currentMode) {
                throw new Error('风控模式数据格式不正确');
            }

            // 更新缓存 - 缓存实际的 CurrentModeResponse 数据
            RiskControlService.riskModeCache = {
                data: modeData,
                timestamp: now
            };

            return modeData;
        } catch (error: any) {
            console.error('获取风控模式失败:', error);
            // 清除缓存，避免使用过期数据
            RiskControlService.riskModeCache = null;
            throw error;
        }
    }

    /**
     * 设置风控模式
     */
    async setRiskMode(mode: string): Promise<ApiResponse<any>> {
        try {
            const response = await api.post('/risk-control/mode', {mode});
            return response.data;
        } catch (error: any) {
            console.error('设置风控模式失败:', error);
            throw error;
        }
    }

    /**
     * 获取当前交易风格
     */
    async getCurrentTradingStyle(): Promise<ApiResponse<any>> {
        try {
            const response = await api.get('/risk-control/current-trading-style');
            return response.data;
        } catch (error: any) {
            console.error('获取交易风格失败:', error);
            throw error;
        }
    }

    /**
     * 设置交易风格
     */
    async setTradingStyle(style: string): Promise<ApiResponse<any>> {
        try {
            const response = await api.post('/risk-control/trading-style', {style});
            return response.data;
        } catch (error: any) {
            console.error('设置交易风格失败:', error);
            throw error;
        }
    }

    /**
     * 验证API响应
     */
    validateApiResponse(response: any): boolean {
        return response && response.hasOwnProperty('success');
    }

    /**
     * 安全的API调用，带有重试机制
     */
    async safeApiCall<T>(
        apiCall: () => Promise<any>,
        retries: number = 3,
        delay: number = 1000
    ): Promise<T | null> {
        try {
            const response = await apiCall();
            if (this.validateApiResponse(response)) {
                return response;
            }
            console.warn('API响应格式不正确:', response);
            return null;
        } catch (error: any) {
            if (retries > 0) {
                console.warn(`API调用失败，${delay}ms后重试 (${retries}次剩余):`, error.message);
                await new Promise(resolve => setTimeout(resolve, delay));
                return this.safeApiCall(apiCall, retries - 1, delay * 2);
            }
            console.error('API调用最终失败:', error);
            return null;
        }
    }
}

// 导出单例实例
export const riskControlService = new RiskControlService();