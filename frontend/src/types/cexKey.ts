// API响应包装类型定义
export interface ApiResponse<T> {
    code?: string;
    message?: string;
    success?: boolean;
    data?: T;
}

/**
 * CEX API密钥安全模型（不包含敏感字段）
 * 用于前端显示和管理
 */
export interface CexKeyModel {
    keyId?: number;
    keyName?: string;
    cexName: string;
    storageType: 'DB' | 'ENV';
    status: 'active' | 'inactive';
    isLiveTrading?: boolean; // 实盘交易标识：false=模拟交易，true=实盘交易
    description?: string;
    createdTime?: string;
    updatedTime?: string;
    // 移除敏感字段：accessKey, secretKey, passPhrase
}

/**
 * CEX API密钥解密模型（包含敏感字段）
 * 用于解密接口返回完整信息
 */
export interface CexKeyDecryptedModel {
    keyId?: number;
    keyName?: string;
    cexName: string;
    storageType: 'DB' | 'ENV';
    status: 'active' | 'inactive';
    isLiveTrading?: boolean; // 实盘交易标识：false=模拟交易，true=实盘交易
    description?: string;
    createdTime?: string;
    updatedTime?: string;
    // 敏感字段 - 仅在解密接口中返回
    accessKey: string;    // 访问密钥
    secretKey: string;    // 秘密密钥
    passPhrase?: string;  // 通行短语（可选）
}

/**
 * 创建CEX API密钥请求
 * 包含敏感字段，仅用于创建时发送到后端
 */
export interface CexKeyCreateRequest {
    keyName?: string;
    cexName: string;
    accessKey: string;    // 敏感字段，仅用于创建
    secretKey: string;    // 敏感字段，仅用于创建
    passPhrase?: string;  // 敏感字段，仅用于创建
    storageType: 'DB' | 'ENV';
    status: 'active' | 'inactive';
    isLiveTrading?: boolean; // 实盘交易标识：false=模拟交易，true=实盘交易
    description?: string;
}

/**
 * 更新CEX API密钥请求
 * 不包含敏感字段，只允许更新配置信息
 */
export interface CexKeyUpdateRequest {
    keyName?: string;
    cexName?: string;
    storageType?: 'DB' | 'ENV';
    status?: 'active' | 'inactive';
    isLiveTrading?: boolean;
    description?: string;
}

/**
 * 密钥状态更新请求
 */
export interface CexKeyStatusRequest {
    status: 'active' | 'inactive';
    changeReason?: string;
}

// 向后兼容的别名（可选）
export type CexApiKey = CexKeyModel;
export type CreateCexKeyRequest = CexKeyCreateRequest;
export type UpdateCexKeyRequest = CexKeyUpdateRequest;
export type CexKeyStatusUpdate = CexKeyStatusRequest;

/**
 * 存储类型枚举值
 */
export const STORAGE_TYPE = {
    DB: 'DB' as const,
    ENV: 'ENV' as const
} as const;

/**
 * 状态枚举值
 */
export const STATUS = {
    ACTIVE: 'active' as const,
    INACTIVE: 'inactive' as const
} as const;

/**
 * CEX名称枚举
 */
export const CEX_NAME = {
    OKX: 'OKX',
    BINANCE: 'BINANCE',
    HUOBI: 'HUOBI'
} as const;