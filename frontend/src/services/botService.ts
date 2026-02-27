// 本地定义的API响应接口，符合后端格式
interface ApiResponse<T = any> {
    code?: string;
    message?: string;
    success: boolean;
    data: T;
    total?: number;
}

// SegmentModel接口定义
export interface SegmentModel {
    segmentId: string;
    title: string;
    content: string;
    category: 'DATA' | 'ANALYSIS' | 'INSTRUCTION' | 'THINKING' | 'MARKET';
    priority: number;
    showTitle: boolean;
    metadata?: Record<string, any>;
}

// SegmentModel分类常量
export const SegmentCategory = {
    DATA: 'DATA',
    ANALYSIS: 'ANALYSIS',
    INSTRUCTION: 'INSTRUCTION',
    THINKING: 'THINKING',
    MARKET: 'MARKET'
} as const;

// SegmentModel优先级常量
export const SegmentPriority = {
    HIGHEST: 10,
    HIGH: 20,
    MEDIUM_HIGH: 30,
    MEDIUM: 40,
    MEDIUM_LOW: 50,
    LOW: 60,
    LOWEST: 90
} as const;

// 定义响应数据类型
export interface BotStatusResponse {
    callCount: number;
    lastCallTime: number | null;
    modelName: string;
    apiKeyId: number;
    apiKeyDescription: string;
    riskControlMode: string;
    tradingStyle: string;
    automaticTradeEnabled: boolean;
    systemStatus: string;
    lastProcessingTime: number;
}

export interface ApiKeyInfo {
    keyId: number;
    keyName: string;
    vendor: string;
    status: string;
    isLiveTrading: boolean;
}

export interface PromptHistoryResponse {
    decisionId: string;
    apiKeyId: number;
    modelName: string;
    createdTime: number | null;

    // 核心决策字段（与action item对应）
    action: string; // BUY/SELL/HOLD/QUERY/ATTENTION
    instId: string; // 目标合约代码
    price: number | null;
    quantity: number | null;
    takeProfit: number | null;
    stopLoss: number | null;
    confidence: number | null;
    reasoning: string;
    timeframe: string;
    posSide?: string | null; // 持仓方向: long/short
    openCloses?: string | null; // 开平仓类型集合（英文逗号分隔，如："open,close"）
    priority?: number | null; // 优先级（工具调用时使用）
    limitCount?: number | null; // 查询条数（工具调用时使用）

    // 元数据字段
    taskId?: string | null;
    submittedTime?: number | null;
    startedTime?: number | null;
    promptContent: string;
    /**
     * 注意：segments字段已废弃，前端应使用parsePromptToSegments(promptContent)动态生成
     * 保留字段定义是为了兼容性，但后端不再返回此字段
     * @deprecated 使用parsePromptToSegments(promptContent)代替
     */
    segments?: SegmentModel[]; // 已废弃：Prompt内容的结构化段落数据（不再从后端返回）
    responseSegments?: SegmentModel[]; // AI响应的结构化段落数据（保持不变）
    status: string;
    errorMessage: string | null;
    executed: boolean;
    executionTime: string | null;
    executionResult: string | null;
    processingTimeMs: number | null;
    inputTotalEquity: number | null;
    inputAvailableBalance: number | null;
    inputUsedMargin: number | null;
    inputUnrealizedPnl: number | null;
    inputMarginRatio: number | null;
    parentId?: number | null; // 父级记录ID,用于多轮对话
    chatSessionId?: number | null; // 聊天会话ID,用于获取ChatMessage消息列表
    callSource?: string | null; // 调用来源: SCHEDULED(定时任务)/MANUAL(手动提交)/DIRECT(API调用)

    relatedOrderCount?: number; // 关联订单数量

    // 补充字段
    decisionRiskLevel?: string;
    decisionConfidence?: number;
    fullResponse?: string;

    // 风控状态和交易动作状态
    riskControlStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'BYPASSED' | null; // 风控状态（使用后端原始值）
    tradeActionStatus?: 'PENDING' | 'EXECUTING' | 'SUCCESS' | 'FAILED' | null; // 交易动作状态
}

// 新增：Prompt生成响应接口
export interface PromptGenerateResponse {
    taskId: string;
    apiKeyId: number;
    status: string;
    message: string;
    promptContent: string;
    segments?: SegmentModel[]; // 新增：结构化的段落数据
    accountData: string;
    positionDetails: string;
    generateTime: number;
    estimatedTokens: number;
    processingTimeMs?: number; // 新增：Prompt生成耗时（毫秒）
    success: boolean;
    errorMessage?: string;
    balanceSnapshotId?: number; // 账户余额快照ID，用于关联生成prompt时的TradeBalanceSnapshot记录
}

// 新增：调用模型响应接口
export interface CallModelResponse {
    taskId: string;
    apiKeyId: number;
    status: string;
    message: string;
    aiResponse: string;
    tradeDecision: any;
    callTime: number;
    processingTimeMs: number;
    success: boolean;
    errorMessage?: string;
    modelName: string;
    callStatsId: number;
    decisionId: string;
}

// 新增：调用模型请求接口
export interface CallModelRequest {
    apiKeyId: number;
    promptContent: string;
    taskId?: string;
    saveToHistory?: boolean;
    modelName?: string;
    customParams?: any;
    balanceSnapshotId?: number; // 账户余额快照ID，用于关联生成prompt时的TradeBalanceSnapshot记录
}

// Action执行历史响应接口
export interface ActionHistoryResponse {
    actionId: number;
    executionSource: string; // INITIAL-初始，REPLAY-重放
    createdTime: number | null; // 创建时间（时间戳）
    status: string; // PENDING-待执行，SUCCESS-成功，FAILED-失败
    replayCount: number | null; // 重放次数（仅对INITIAL记录有效）
    executedTime: string | null; // 执行时间
}

// 账户余额快照响应接口
export interface TradeBalanceSnapshotResponse {
    snapshotId: number;
    apiKeyId: number;
    cexName: string;
    totalEquityUsdt: number;
    availableEquityUsdt: number;
    usedMarginUsdt: number;
    unrealizedPnlUsdt: number;
    marginRatio: number;
    maxAvailableAmount: number;
    source: string;
    snapshotTime: number;
    recordId: number;
}

// 新增：AI模型配置接口
export interface AIModelConfig {
    configId: number;
    modelId: string;
    displayName: string;
    provider: string;
    parameterSize?: number;
    description?: string;
    isActive: boolean;
    maxTokens?: number;
    costPerToken?: number;
    defaultModel: boolean;
    modelType: string;
    apiUrl?: string;
    apiKey?: string;
    apiFormat?: string;
    timeoutSeconds?: number;
    retryCount?: number;
    maxConcurrent?: number;
}

class BotService {
    private readonly baseUrl = '';  // 后端已移除context-path,统一使用根路径

    /**
     * 获取BOT状态
     */
    async getBotStatus(apiKeyId?: number): Promise<ApiResponse<BotStatusResponse>> {
        try {
            let url = `${this.baseUrl}/bot/status`;
            if (apiKeyId) {
                url += `?apiKeyId=${apiKeyId.toString()}`;
            }

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取BOT状态失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取BOT状态失败',
                data: null as any
            };
        }
    }

    /**
     * 获取可用的API Key列表
     */
    async getActiveApiKeys(): Promise<ApiResponse<ApiKeyInfo[]>> {
        try {
            const response = await fetch(`${this.baseUrl}/bot/api-keys`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取API Key列表失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取API Key列表失败',
                data: []
            };
        }
    }

    /**
     * 手动触发BOT
     * @param apiKeyId API密钥ID
     * @param modelName 可选的模型名称,如果不提供则使用后端默认模型
     */
    async triggerBot(apiKeyId: number, modelName?: string): Promise<ApiResponse<any>> {
        try {
            let url = `${this.baseUrl}/bot/trigger?apiKeyId=${apiKeyId.toString()}`;

            // 如果提供了模型名称,添加到URL参数
            if (modelName && modelName.trim()) {
                url += `&modelName=${encodeURIComponent(modelName)}`;
            }

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('触发BOT失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '触发BOT失败',
                data: null as any
            };
        }
    }

    /**
     * 获取prompt历史列表
     * 支持多重筛选：动作类型、AI模型、调用来源
     * 支持分页查询
     * @param apiKeyId API密钥ID
     * @param limit 限制数量（总记录数上限）
     * @param actionFilter 决策动作筛选(BUY/SELL/HOLD/QUERY/ATTENTION)
     * @param modelFilter AI模型筛选
     * @param callSourceFilter 调用来源筛选(SCHEDULED/DIRECT/MANUAL)
     * @param page 页码（从1开始）
     * @param size 每页大小
     */
    async getPromptHistory(
        apiKeyId: number,
        limit: number = 50,
        actionFilter?: string,
        modelFilter?: string,
        callSourceFilter?: string,
        openCloseFilter?: string,
        page: number = 1,
        size: number = 20
    ): Promise<ApiResponse<PromptHistoryResponse[]>> {
        try {
            let url = `${this.baseUrl}/bot/prompts?apiKeyId=${apiKeyId.toString()}&limit=${limit.toString()}`;

            // 添加action筛选参数
            if (actionFilter) {
                url += `&actionFilter=${actionFilter}`;
            }

            // 添加模型筛选参数
            if (modelFilter) {
                url += `&modelFilter=${encodeURIComponent(modelFilter)}`;
            }

            // 添加调用来源筛选参数
            if (callSourceFilter) {
                url += `&callSourceFilter=${callSourceFilter}`;
            }

            // 添加开平仓筛选参数
            if (openCloseFilter) {
                url += `&openCloseFilter=${openCloseFilter}`;
            }

            // 添加分页参数
            url += `&page=${page}&size=${size}`;

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取prompt历史失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取prompt历史失败',
                data: []
            };
        }
    }

    /**
     * 获取最新的prompt
     */
    async getLatestPrompt(apiKeyId?: number): Promise<ApiResponse<PromptHistoryResponse>> {
        try {
            let url = `${this.baseUrl}/bot/prompts/latest`;
            if (apiKeyId) {
                url += `?apiKeyId=${apiKeyId.toString()}`;
            }

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取最新prompt失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取最新prompt失败',
                data: null as any
            };
        }
    }

    /**
     * 生成prompt（不调用AI模型）
     */
    async generatePrompt(apiKeyId: number): Promise<ApiResponse<PromptGenerateResponse>> {
        try {
            const url = `${this.baseUrl}/bot/generate-prompt?apiKeyId=${apiKeyId.toString()}`;

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('生成prompt失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '生成prompt失败',
                data: null as any
            };
        }
    }

    /**
     * 使用自定义prompt调用AI模型
     */
    async callModel(request: CallModelRequest): Promise<ApiResponse<CallModelResponse>> {
        try {
            const response = await fetch(`${this.baseUrl}/bot/call-model`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(request),
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('调用AI模型失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '调用AI模型失败',
                data: null as any
            };
        }
    }

    /**
     * 获取所有活跃的AI模型配置
     */
    async getActiveModels(): Promise<ApiResponse<AIModelConfig[]>> {
        try {
            const response = await fetch(`${this.baseUrl}/ai-model-configs/active`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取活跃模型配置失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取活跃模型配置失败',
                data: []
            };
        }
    }

    /**
     * 获取默认的AI模型配置
     */
    async getDefaultModel(): Promise<ApiResponse<AIModelConfig>> {
        try {
            const response = await fetch(`${this.baseUrl}/ai-model-configs/default`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取默认模型配置失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取默认模型配置失败',
                data: null as any
            };
        }
    }

    /**
     * 获取会话链路
     * 根据记录ID获取完整的会话链路(从最顶层父级到当前记录)
     */
    async getConversationChain(recordId: number): Promise<ApiResponse<PromptHistoryResponse[]>> {
        try {
            const url = `${this.baseUrl}/bot/conversation-chain?recordId=${recordId.toString()}`;

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取会话链路失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取会话链路失败',
                data: []
            };
        }
    }

    /**
     * 重放历史决策
     * 从历史AI响应中重新解析决策并执行交易
     * @param recordId 历史记录ID
     * @returns 重放结果
     */
    async replayDecision(recordId: number): Promise<ApiResponse<{
        success: boolean;
        message: string;
        recordId: number;
        originalModelName: string;
        actionCount: number;
        executionResults: any[];
        executedAt: string;
    }>> {
        try {
            const response = await fetch(`${this.baseUrl}/bot/replay-decision`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({recordId}),
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('重放决策失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '重放决策失败',
                data: null as any
            };
        }
    }

    /**
     * 重放单个action决策
     * 从历史TradeAction中重放指定的单个决策
     * @param recordId 历史记录ID（用于校验action归属）
     * @param actionId 动作ID
     * @returns 重放结果
     */
    async replaySingleAction(recordId: number, actionId: number): Promise<ApiResponse<{
        success: boolean;
        message: string;
        recordId: number;
        actionId: number;
        originalModelName: string;
        actionType: string;
        instId: string;
        executionResult: any;
        replayActions: any[];
        executedAt: string;
    }>> {
        try {
            const response = await fetch(`${this.baseUrl}/bot/replay-single-action`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({recordId, actionId}),
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('重放单个action失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '重放单个action失败',
                data: null as any
            };
        }
    }

    /**
     * 获取Action执行历史
     * 根据actionId获取该action及其所有重放记录的执行历史
     * @param actionId 动作ID
     * @returns Action执行历史列表
     */
    async getActionHistory(actionId: number): Promise<ApiResponse<ActionHistoryResponse[]>> {
        try {
            const url = `${this.baseUrl}/bot/action-history?actionId=${actionId.toString()}`;

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取Action执行历史失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取Action执行历史失败',
                data: []
            };
        }
    }

    /**
     * 获取账户余额快照列表
     * 用于权益图表展示
     * @param apiKeyId API密钥ID
     * @param limit 限制数量,默认50
     * @returns 账户余额快照列表
     */
    async getBalanceSnapshots(apiKeyId: number, limit: number = 50): Promise<ApiResponse<TradeBalanceSnapshotResponse[]>> {
        try {
            const url = `${this.baseUrl}/bot/balance-snapshots?apiKeyId=${apiKeyId.toString()}&limit=${limit.toString()}`;

            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();
            return result;
        } catch (error) {
            console.error('获取账户余额快照失败:', error);
            return {
                success: false,
                message: error instanceof Error ? error.message : '获取账户余额快照失败',
                data: []
            };
        }
    }
}

// ==================== Prompt切割工具函数 ====================
/**
 * 从promptContent中解析出segments数组
 * 切割格式：=== {title} ===
 * @param promptContent 完整的prompt内容
 * @returns 解析后的segments数组
 */
export function parsePromptToSegments(promptContent: string): SegmentModel[] {
    // 如果内容为空，返回空数组
    if (!promptContent || promptContent.trim().length === 0) {
        return [];
    }

    // 标准化换行符
    const normalizedContent = promptContent.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

    // 正则表达式：匹配 === {title} ===
    const titlePattern = /^={3}\s*(.+?)\s*={3}\s*$/gm;
    const segments: SegmentModel[] = [];

    let lastIndex = 0;
    let match: RegExpExecArray | null;
    let currentTitle: string | null = null;

    // 遍历所有匹配的标题
    while ((match = titlePattern.exec(normalizedContent)) !== null) {
        const title = match[1].trim();
        const matchStart = match.index;
        const matchEnd = match.index + match[0].length;

        // 如果有上一个标题，提取上一个段落的内容
        if (currentTitle !== null) {
            const content = normalizedContent.substring(lastIndex, matchStart).trim();
            // 修改：即使content为空也生成segment，这样可以显示所有处理器
            segments.push(createSegmentFromTitle(currentTitle, content));
        }

        // 更新当前标题和位置
        currentTitle = title;
        lastIndex = matchEnd;
    }

    // 处理最后一个段落
    if (currentTitle !== null) {
        const content = normalizedContent.substring(lastIndex).trim();
        // 修改：即使content为空也生成segment
        segments.push(createSegmentFromTitle(currentTitle, content));
    }

    // 如果没有找到任何标题，将整个内容作为一个段落
    if (segments.length === 0 && normalizedContent.trim().length > 0) {
        segments.push({
            segmentId: generateSegmentId(),
            title: 'Prompt内容',
            content: normalizedContent.trim(),
            category: 'INSTRUCTION',
            priority: 40,
            showTitle: true
        });
    }

    // console.log(`【前端解析】解析出 ${segments.length} 个segments`);
    return segments;
}

/**
 * 根据标题创建segment对象
 * 自动识别category和priority
 */
function createSegmentFromTitle(title: string, content: string): SegmentModel {
    // 自动识别category
    const category = determineCategory(title);

    // 自动识别priority
    const priority = determinePriority(title);

    return {
        segmentId: generateSegmentId(),
        title,
        content,
        category,
        priority,
        showTitle: true
    };
}

/**
 * 根据标题确定category
 */
function determineCategory(title: string): SegmentModel['category'] {
    const lowerTitle = title.toLowerCase();

    // DATA分类关键词 - 数据类信息（统计、账户、持仓、订单等）
    if (lowerTitle.includes('统计') || lowerTitle.includes('账户') || lowerTitle.includes('持仓') ||
        lowerTitle.includes('余额') || lowerTitle.includes('可用') || lowerTitle.includes('保证金') ||
        lowerTitle.includes('账户信息') || lowerTitle.includes('持仓信息') || lowerTitle.includes('账户统计') ||
        lowerTitle.includes('订单') || lowerTitle.includes('委托') || lowerTitle.includes('等待成交') ||
        lowerTitle.includes('仓位历史') || lowerTitle.includes('订单历史')) {
        return 'DATA';
    }

    // ANALYSIS分类关键词 - 分析类信息（技术指标、风险评估、策略建议等）
    if (lowerTitle.includes('技术指标') || lowerTitle.includes('指标') || lowerTitle.includes('分析') ||
        lowerTitle.includes('风险评估') || lowerTitle.includes('市场') || lowerTitle.includes('行情') ||
        lowerTitle.includes('策略建议') || lowerTitle.includes('策略') || lowerTitle.includes('建议')) {
        return 'ANALYSIS';
    }

    // INSTRUCTION分类关键词 - 指令类信息（决策要求、格式规范、交易规则等）
    if (lowerTitle.includes('决策') || lowerTitle.includes('要求') || lowerTitle.includes('规则') ||
        lowerTitle.includes('指令') || lowerTitle.includes('格式') || lowerTitle.includes('交易规则')) {
        return 'INSTRUCTION';
    }

    // THINKING分类关键词 - 思考类信息（思考模式、推理过程等）
    if (lowerTitle.includes('思考') || lowerTitle.includes('推理') || lowerTitle.includes('模式')) {
        return 'THINKING';
    }

    // MARKET分类关键词 - 市场类信息（行情数据、市场概况等）
    if (lowerTitle.includes('市场概况') || lowerTitle.includes('市场数据') || lowerTitle.includes('行情数据') ||
        lowerTitle.includes('合约概况') || lowerTitle.includes('top')) {
        return 'MARKET';
    }

    // 默认为INSTRUCTION
    return 'INSTRUCTION';
}

/**
 * 根据标题确定priority
 */
function determinePriority(title: string): number {
    const lowerTitle = title.toLowerCase();

    // 统计 - 最高优先级
    if (lowerTitle.includes('统计')) {
        return 10; // HIGHEST
    }

    // 账户/持仓 - 高优先级
    if (lowerTitle.includes('账户') || lowerTitle.includes('持仓')) {
        return 20; // HIGH
    }

    // 订单历史/委托订单 - 高优先级
    if (lowerTitle.includes('订单历史') || lowerTitle.includes('委托') || lowerTitle.includes('等待成交')) {
        return 25; // HIGH
    }

    // 技术指标 - 中等优先级
    if (lowerTitle.includes('技术指标') || lowerTitle.includes('指标')) {
        return 30; // MEDIUM_HIGH
    }

    // 市场数据/策略建议 - 中等优先级
    if (lowerTitle.includes('市场数据') || lowerTitle.includes('策略建议') || lowerTitle.includes('策略')) {
        return 35; // MEDIUM_HIGH
    }

    // 决策要求/交易规则 - 中等优先级
    if (lowerTitle.includes('决策') || lowerTitle.includes('规则') || lowerTitle.includes('要求') || lowerTitle.includes('交易规则')) {
        return 40; // MEDIUM
    }

    // 仓位历史 - 中等优先级
    if (lowerTitle.includes('仓位历史')) {
        return 45; // MEDIUM
    }

    // 思考模式 - 低优先级
    if (lowerTitle.includes('思考') || lowerTitle.includes('模式')) {
        return 60; // LOW
    }

    // 默认中等优先级
    return 40; // MEDIUM
}

/**
 * 生成唯一的segmentId
 */
function generateSegmentId(): string {
    return `segment_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * 将segments数组转换为完整的promptContent
 * @param segments segments数组
 * @returns 完整的prompt内容
 */
export function segmentsToPrompt(segments: SegmentModel[]): string {
    if (!segments || segments.length === 0) {
        return '';
    }

    // 按priority排序
    const sortedSegments = [...segments].sort((a, b) => a.priority - b.priority);

    // 组装成完整prompt
    const promptParts = sortedSegments.map(segment => {
        if (segment.showTitle && segment.title) {
            return `=== ${segment.title} ===\n${segment.content}`;
        } else {
            return segment.content;
        }
    });

    return promptParts.join('\n\n');
}

// 创建单例实例
export const botService = new BotService();

export default botService;
