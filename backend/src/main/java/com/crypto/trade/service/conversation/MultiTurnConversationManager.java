package com.crypto.trade.service.conversation;

/**
 * MultiTurnConversationManager
 * 管理类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface MultiTurnConversationManager {

    /**
     * 启动新的多轮会话
     *
     * @param request 会话请求
     * @return 会话上下文
     */
    ConversationContext startConversation(ConversationRequest request);

    /**
     * 继续现有会话（执行下一轮）
     *
     * @param sessionId       会话ID
     * @param currentResponse 当前AI响应
     * @return 会话上下文
     */
    ConversationContext continueConversation(Long sessionId, String currentResponse);

    /**
     * 获取会话状态
     *
     * @param sessionId 会话ID
     * @return 会话上下文
     */
    ConversationContext getContext(Long sessionId);

    /**
     * 标记会话完成
     *
     * @param sessionId     会话ID
     * @param finalResponse 最终响应
     */
    void completeConversation(Long sessionId, String finalResponse);

    /**
     * 终止会话
     *
     * @param sessionId 会话ID
     * @param reason    终止原因
     */
    void terminateConversation(Long sessionId, String reason);

    /**
     * 判断是否需要继续对话
     *
     * @param context    会话上下文
     * @param aiResponse AI响应
     * @return 是否继续
     */
    boolean shouldContinue(ConversationContext context, String aiResponse);
}