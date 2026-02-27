import api from './api';
import {
    ApiResponse,
    CexKeyCreateRequest,
    CexKeyDecryptedModel,
    CexKeyModel,
    CexKeyStatusRequest,
    CexKeyUpdateRequest
} from '../types/cexKey';

export const cexKeyService = {
    /**
     * 获取所有API Key（安全模型，不包含敏感字段）
     */
    getAllKeys: async (): Promise<CexKeyModel[]> => {
        try {
            const response = await api.get<ApiResponse<CexKeyModel[]>>('/cex-keys');

            if (!response.data.success) {
                throw new Error(response.data.message || '获取密钥列表失败');
            }

            return response.data.data || [];
        } catch (error) {
            console.error('获取密钥列表失败:', error);
            throw error;
        }
    },

    /**
     * 创建API Key
     */
    createKey: async (key: CexKeyCreateRequest): Promise<CexKeyModel> => {
        try {
            const response = await api.post<ApiResponse<CexKeyModel>>('/cex-keys', key);

            if (!response.data.success) {
                throw new Error(response.data.message || '创建密钥失败');
            }

            if (!response.data.data) {
                throw new Error('创建密钥返回数据为空');
            }

            return response.data.data;
        } catch (error) {
            console.error('创建密钥失败:', error);
            throw error;
        }
    },

    /**
     * 更新API Key
     */
    updateKey: async (id: number, key: CexKeyUpdateRequest): Promise<CexKeyModel> => {
        try {
            const response = await api.put<ApiResponse<CexKeyModel>>(`/cex-keys/${id}`, key);

            if (!response.data.success) {
                throw new Error(response.data.message || '更新密钥失败');
            }

            if (!response.data.data) {
                throw new Error('更新密钥返回数据为空');
            }

            return response.data.data;
        } catch (error) {
            console.error('更新密钥失败:', error);
            throw error;
        }
    },

    /**
     * 删除API Key
     */
    deleteKey: async (id: number): Promise<string> => {
        try {
            const response = await api.delete<ApiResponse<string>>(`/cex-keys/${id}`);

            if (!response.data.success) {
                throw new Error(response.data.message || '删除密钥失败');
            }

            return response.data.data || '删除成功';
        } catch (error) {
            console.error('删除密钥失败:', error);
            throw error;
        }
    },

    /**
     * 更新API Key状态
     */
    updateKeyStatus: async (id: number, statusRequest: CexKeyStatusRequest): Promise<CexKeyModel> => {
        try {
            const response = await api.put<ApiResponse<CexKeyModel>>(
                `/cex-keys/${id}/status`, statusRequest);

            if (!response.data.success) {
                throw new Error(response.data.message || '更新密钥状态失败');
            }

            if (!response.data.data) {
                throw new Error('更新密钥状态返回数据为空');
            }

            return response.data.data;
        } catch (error) {
            console.error('更新密钥状态失败:', error);
            throw error;
        }
    },

    /**
     * 获取解密后的密钥配置信息
     * 返回包含敏感字段的完整信息，用于前端显示
     */
    getDecryptedKey: async (id: number): Promise<CexKeyDecryptedModel> => {
        try {
            const response = await api.get<ApiResponse<CexKeyDecryptedModel>>(`/cex-keys/${id}/decrypted`);

            if (!response.data.success) {
                throw new Error(response.data.message || '获取解密密钥配置失败');
            }

            if (!response.data.data) {
                throw new Error('获取解密密钥配置返回数据为空');
            }

            return response.data.data;
        } catch (error) {
            console.error('获取解密密钥配置失败:', error);
            throw error;
        }
    },

    /**
     * 验证密钥请求参数
     */
    validateCreateRequest: (request: CexKeyCreateRequest): { isValid: boolean; errors: string[] } => {
        const errors: string[] = [];

        if (!request.cexName || request.cexName.trim().length === 0) {
            errors.push('CEX名称不能为空');
        }

        if (!request.accessKey || request.accessKey.trim().length === 0) {
            errors.push('访问密钥不能为空');
        }

        if (!request.secretKey || request.secretKey.trim().length === 0) {
            errors.push('秘密密钥不能为空');
        }

        if (!request.storageType || !['DB', 'ENV'].includes(request.storageType)) {
            errors.push('存储类型必须是DB或ENV');
        }

        return {
            isValid: errors.length === 0,
            errors
        };
    },

    /**
     * 验证状态值
     */
    validateStatus: (status: string): boolean => {
        return ['active', 'inactive'].includes(status);
    },

    /**
     * 获取存储类型显示名称
     */
    getStorageTypeDisplayName: (storageType: string): string => {
        switch (storageType) {
            case 'DB':
                return '数据库存储';
            case 'ENV':
                return '环境变量存储';
            default:
                return '未知';
        }
    },

    /**
     * 获取状态显示名称
     */
    getStatusDisplayName: (status: string): string => {
        switch (status) {
            case 'active':
                return '激活';
            case 'inactive':
                return '未激活';
            default:
                return status || '未知';
        }
    },

    /**
     * 获取交易模式显示名称
     */
    getTradingModeDisplayName: (isLiveTrading?: boolean): string => {
        return isLiveTrading ? '实盘交易' : '模拟交易';
    },

    /**
     * 格式化显示掩码
     */
    maskSensitiveValue: (value: string): string => {
        if (!value || value.length <= 8) {
            return '****';
        }
        return value.substring(0, 4) + '****' + value.substring(value.length - 4);
    }
};

export default cexKeyService;