export interface ChatMessage {
    messageId: number;
    sessionId: number;
    userId?: string;
    role: 'user' | 'assistant';
    content: string;
    tokensUsed?: number;
    processingTimeMs?: number;
    createdTime: string;
}

export interface ChatSession {
    sessionId: number;
    sessionName: string;
    userId: string;
    status: 'active' | 'archived';
    modelName: string;
    createdTime: string;
    updatedTime: string;
    messages?: ChatMessage[];
}

export interface SendMessageRequest {
    sessionId?: number;
    message: string;
    userId?: string;
}

export interface SendMessageResponse {
    success: boolean;
    message: string;
    sessionId?: number;
    messageId?: number;
    assistantReply?: string;
    tokensUsed?: number;
    processingTimeMs?: number;
}

export interface ApiResponse<T> {
    code: number;
    message: string;
    data: T;
    success: boolean;
}

export interface ChatState {
    sessions: ChatSession[];
    currentSession: ChatSession | null;
    messages: ChatMessage[];
    isLoading: boolean;
    isConnected: boolean;
    error: string | null;
}

export interface WebSocketMessage {
    sessionId: number;
    message: string;
    userId?: string;
}