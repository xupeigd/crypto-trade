export interface AIInfoModel {
    configId?: number;
    modelId: string;
    displayName: string;
    provider: string;
    parameterSize: number;
    description: string;
    isActive: boolean;
    maxTokens: number;
    costPerToken: number;
    defaultModel: boolean;
    // 新增字段
    modelType?: string;           // 模型类型：LOCAL/REMOTE
    apiUrl?: string;              // API URL（本地模型为服务地址，远端模型为API地址）
    apiKey?: string;               // API密钥（已脱敏显示）
    apiFormat?: string;            // API格式：ollama/openai/claude/custom
    timeoutSeconds?: number;       // 超时时间（秒）
    retryCount?: number;           // 重试次数
    maxConcurrent?: number;        // 最大并发数
    extraBody?: string;            // 额外的请求体参数(JSON格式)
}

// 模型类型枚举
export const ModelType = {
    LOCAL: 'LOCAL',
    REMOTE: 'REMOTE'
} as const;

export type ModelType = typeof ModelType[keyof typeof ModelType];

// API格式枚举
export const ApiFormat = {
    OLLAMA: 'ollama',
    OPENAI: 'openai',
    CLAUDE: 'claude',
    CUSTOM: 'custom'
} as const;

export type ApiFormat = typeof ApiFormat[keyof typeof ApiFormat];

// 模型类型显示名称
export const ModelTypeLabels = {
    [ModelType.LOCAL]: '本地模型',
    [ModelType.REMOTE]: '远端模型'
} as const;

// API格式显示名称
export const ApiFormatLabels = {
    [ApiFormat.OLLAMA]: 'Ollama',
    [ApiFormat.OPENAI]: 'OpenAI',
    [ApiFormat.CLAUDE]: 'Claude',
    [ApiFormat.CUSTOM]: '自定义'
} as const;