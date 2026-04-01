export interface CexProxyBinding {
    bindingId?: number;
    cexName: string;
    proxyId: number;
    proxyName?: string;
    proxyType?: string;
    serverHost?: string;
    serverPort?: number;
    status: 'active' | 'inactive';
    description?: string;
    createdTime?: string;
    updatedTime?: string;
}

export interface CreateCexProxyBindingRequest {
    cexName: string;
    proxyId: number;
    status: 'active' | 'inactive';
    description?: string;
}

export interface UpdateCexProxyBindingRequest {
    cexName: string;
    proxyId: number;
    status: 'active' | 'inactive';
    description?: string;
}
