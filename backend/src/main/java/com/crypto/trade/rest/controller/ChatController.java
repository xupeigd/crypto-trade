package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.SendMessageRequest;
import com.crypto.trade.model.ChatMessageModel;
import com.crypto.trade.model.ChatSessionModel;
import com.crypto.trade.model.SendMessageResponseModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天控制器
 * 提供聊天会话和消息管理的API接口
 * 所有接口返回值使用ApiResponse<T>格式和Model包装
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    ChatService chatService;

    /**
     * 获取用户会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionModel>> getUserSessions(@RequestParam(defaultValue = "default") String userId,
                                                               @RequestParam(required = false) Long agentId) {
        try {
            log.debug("获取用户会话列表 - userId: {}, agentId: {}", userId, agentId);
            List<ChatSessionModel> sessions = chatService.getUserSessionsModel(userId, agentId);
            log.debug("获取用户会话列表成功 - userId: {}, 会话数量: {}", userId, sessions.size());
            return ApiResponse.ok(sessions);
        } catch (Exception e) {
            log.error("获取用户会话列表失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            return ApiResponse.fail("获取会话列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionModel> createNewSession(@RequestParam(defaultValue = "default") String userId,
                                                          @RequestParam(required = false) String sessionName,
                                                          @RequestParam(required = false) Long agentId) {
        try {
            log.debug("创建新会话 - userId: {}, sessionName: {}, agentId: {}", userId, sessionName, agentId);
            ChatSessionModel session = chatService.createSessionModel(sessionName, userId, agentId);
            log.debug("创建新会话成功 - userId: {}, sessionId: {}", userId, session.getSessionId());
            return ApiResponse.ok(session);
        } catch (Exception e) {
            log.error("创建新会话失败 - userId: {}, sessionName: {}, agentId: {}, error: {}", userId, sessionName, agentId, e.getMessage(), e);
            return ApiResponse.fail("创建会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新会话
     */
    @GetMapping("/sessions/latest")
    public ApiResponse<ChatSessionModel> getLatestSession(@RequestParam(defaultValue = "default") String userId) {
        try {
            log.debug("获取最新会话 - userId: {}", userId);
            ChatSessionModel session = chatService.getLatestSessionModel(userId);
            if (null == session) {
                log.debug("未找到最新会话 - userId: {}", userId);
                return ApiResponse.fail("未找到会话");
            }
            log.debug("获取最新会话成功 - userId: {}, sessionId: {}", userId, session.getSessionId());
            return ApiResponse.ok(session);
        } catch (Exception e) {
            log.error("获取最新会话失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            return ApiResponse.fail("获取最新会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageModel>> getSessionMessages(@PathVariable Long sessionId,
                                                                  @RequestParam(defaultValue = "default") String userId) {
        try {
            log.debug("获取会话消息 - sessionId: {}, userId: {}", sessionId, userId);
            List<ChatMessageModel> messages = chatService.getSessionMessagesModel(sessionId);
            log.debug("获取会话消息成功 - sessionId: {}, 消息数量: {}", sessionId, messages.size());
            return ApiResponse.ok(messages);
        } catch (Exception e) {
            log.error("获取会话消息失败 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage(), e);
            return ApiResponse.fail("获取会话消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送消息
     */
    @PostMapping("/send")
    public ApiResponse<SendMessageResponseModel> sendMessage(@RequestBody SendMessageRequest request) {
        try {
            // 设置默认userId
            String userId = request.getUserId();
            if (null == userId || userId.trim().isEmpty()) {
                userId = "default";
            }

            log.debug("发送消息 - sessionId: {}, userId: {}, message长度: {}, systemPrompt: {}", request.getSessionId(), userId,
                    null != request.getMessage() ? request.getMessage().length() : 0,
                    null != request.getSystemPrompt() ? "已设置" : "未设置");

            // 验证消息内容
            if (null == request.getMessage() || request.getMessage().trim().isEmpty()) {
                return ApiResponse.fail("消息内容不能为空");
            }

            SendMessageResponseModel response = chatService.sendMessageModel(request.getSessionId(), request.getMessage(), userId, request.getSystemPrompt());
            if (response.isSuccess()) {
                log.debug("发送消息成功 - sessionId: {}, messageId: {}", response.getSessionId(), response.getMessageId());
                return ApiResponse.ok(response);
            } else {
                log.warn("发送消息失败 - sessionId: {}, userId: {}, error: {}", request.getSessionId(), userId, response.getMessage());
                return ApiResponse.fail(response.getMessage());
            }
        } catch (Exception e) {
            log.error("发送消息失败 - sessionId: {}, userId: {}, error: {}",
                    null != request ? request.getSessionId() : null,
                    null != request ? request.getUserId() : null,
                    e.getMessage(), e);
            return ApiResponse.fail("发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 更新会话名称
     */
    @PutMapping("/sessions/{sessionId}/name")
    public ApiResponse<ChatSessionModel> updateSessionName(@PathVariable Long sessionId, @RequestParam String sessionName,
                                                           @RequestParam(defaultValue = "default") String userId) {
        try {
            log.debug("更新会话名称 - sessionId: {}, sessionName: {}, userId: {}", sessionId, sessionName, userId);
            // 验证会话名称
            if (null == sessionName || sessionName.trim().isEmpty()) {
                return ApiResponse.fail("会话名称不能为空");
            }
            ChatSessionModel session = chatService.updateSessionNameModel(sessionId, sessionName, userId);
            log.debug("更新会话名称成功 - sessionId: {}, sessionName: {}", sessionId, sessionName);
            return ApiResponse.ok(session);
        } catch (Exception e) {
            log.error("更新会话名称失败 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage(), e);
            return ApiResponse.fail("更新会话名称失败: " + e.getMessage());
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<String> deleteSession(@PathVariable Long sessionId, @RequestParam(defaultValue = "default") String userId) {
        try {
            log.debug("删除会话 - sessionId: {}, userId: {}", sessionId, userId);
            chatService.deleteSession(sessionId, userId);
            log.debug("删除会话成功 - sessionId: {}, userId: {}", sessionId, userId);
            return ApiResponse.ok("会话删除成功");
        } catch (Exception e) {
            log.error("删除会话失败 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage(), e);
            return ApiResponse.fail("删除会话失败: " + e.getMessage());
        }
    }

}