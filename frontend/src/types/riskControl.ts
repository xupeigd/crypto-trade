// 风控审核状态枚举
export enum AuditStatus {
    PENDING = 'PENDING',
    APPROVED = 'APPROVED',
    REJECTED = 'REJECTED'
}

// 风控等级枚举
export enum RiskLevel {
    HIGH = 'HIGH',
    MEDIUM = 'MEDIUM',
    LOW = 'LOW'
}

// 订单类型枚举
export enum OrderType {
    LIMIT = 'LIMIT',
    MARKET = 'MARKET'
}

// 订单方向枚举
export enum OrderSide {
    BUY = 'BUY',
    SELL = 'SELL'
}

// 风控订单接口
export interface RiskControlOrder {
    orderId: number;
    originalOrderId: string;
    apiKeyId: number;
    symbol: string;
    orderType: OrderType | null;
    side: OrderSide | null;      // 修改为可选，因为后端可能返回null
    quantity: string;
    price?: string;
    takeProfitPrice?: string;  // 止盈价格
    stopLossPrice?: string;    // 止损价格
    posSide?: string;          // 持仓方向(long/short)
    lever?: string;            // 杠杆倍数
    originalAmount?: string;   // 原始成本金额
    estimatedTotalCapital?: string;  // 预估总占用资金
    auditStatus: AuditStatus | null;  // 修改为可选
    auditor?: string;
    auditTime?: string;
    rejectionReason?: string;
    createTime: string;
    updateTime?: string;
    orderSource: string;
    riskLevel: RiskLevel;
}

// API响应接口
export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    message: string;
}

// 风控订单列表组件Props
export interface RiskControlOrderListProps {
    apiKeyId: number | null;
    onOrderUpdate: () => void;
}

// 订单审核弹窗组件Props
export interface OrderApprovalModalProps {
    order: RiskControlOrder | null;
    visible: boolean;
    apiKeyId: number | null;
    onClose: () => void;
    onApprove: (orderId: number) => void;
    onReject: (orderId: number, reason: string) => void;
}

// 风控统计信息接口
export interface RiskControlStatistics {
    statusStats: Record<string, number>;
    riskStats: Record<string, number>;
    pendingCount: number;
}

// 风控审核请求接口
export interface AuditRequest {
    orderId: number;
    action: 'approve' | 'reject';
    reason?: string;
}

// 实时价格接口
export interface RealtimePrice {
    lastPrice: number;  // 改为 lastPrice 匹配后端
    change24h?: number;
    changePercent?: number;
}

// 止盈止损计算结果接口
export interface StopLossCalculation {
    price: string;
    percentage: number;
    expectedProfit?: number;
    expectedLoss?: number;
}

// 批量操作请求接口
export interface BatchOperationRequest {
    operation: 'approve' | 'reject';
    orderIds: number[];
    reason?: string;
}