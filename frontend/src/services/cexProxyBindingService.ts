import api from './api';
import {ApiResponse} from '../types/common';
import {
    CexProxyBinding,
    CreateCexProxyBindingRequest,
    UpdateCexProxyBindingRequest
} from '../types/cexProxyBinding';

export const cexProxyBindingService = {
    getAll: async (): Promise<CexProxyBinding[]> => {
        const response = await api.get<ApiResponse<CexProxyBinding[]>>('/cex-proxy-bindings');
        return response.data.data || [];
    },

    create: async (data: CreateCexProxyBindingRequest): Promise<CexProxyBinding | null> => {
        const response = await api.post<ApiResponse<CexProxyBinding>>('/cex-proxy-bindings', data);
        return response.data.data || null;
    },

    update: async (id: number, data: UpdateCexProxyBindingRequest): Promise<CexProxyBinding | null> => {
        const response = await api.put<ApiResponse<CexProxyBinding>>(`/cex-proxy-bindings/${id}`, data);
        return response.data.data || null;
    },

    delete: async (id: number): Promise<boolean> => {
        const response = await api.delete<ApiResponse<void>>(`/cex-proxy-bindings/${id}`);
        return response.data.code === 0;
    }
};
