export interface DataFetchConfig {
    configId?: number;
    taskId: number;
    cexBaseUrl: string;
    apiPath: string;
    httpMethod: 'GET' | 'POST' | 'PUT' | 'DELETE';
    requestParams?: string;
    requiresAuth: boolean;
    authKeyId?: number;
    signatureClass?: string;
    dataProcessorClass: string;
    targetDuckdbTable: string;
    responseMapping?: string;
    requiresProxy: boolean;
    proxyId?: number;
    proxyServiceConfig?: {
        proxyId?: number;
        proxyName: string;
        proxyType: 'HTTP' | 'SOCKS5';
        serverHost: string;
        serverPort: number;
        status: 'active' | 'inactive';
        description?: string;
    };
}

export interface CreateDataFetchConfigRequest {
    taskId: number;
    cexBaseUrl: string;
    apiPath: string;
    httpMethod: 'GET' | 'POST' | 'PUT' | 'DELETE';
    requestParams?: string;
    requiresAuth: boolean;
    authKeyId?: number;
    signatureClass?: string;
    dataProcessorClass: string;
    targetDuckdbTable: string;
    responseMapping?: string;
    requiresProxy: boolean;
    proxyId?: number;
}

export interface UpdateDataFetchConfigRequest extends Partial<CreateDataFetchConfigRequest> {
}