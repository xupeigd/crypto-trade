-- =======================================================
-- Crypto Trade Database - Add Message ID to LLM Call Records
--
-- Date: 2026-02-03
-- Description: 为 t_llm_call_records 表添加 user_message_id 和 assistant_message_id 字段
--              用于关联 ChatMessage，消除数据冗余
-- =======================================================

USE db_crypto_trade;

-- =======================================================
-- 添加用户消息ID字段
-- =======================================================
ALTER TABLE `t_llm_call_records`
ADD COLUMN `user_message_id` BIGINT NULL COMMENT '用户消息ID(关联t_chat_messages.message_id, role=user)' AFTER `session_id`;

-- =======================================================
-- 添加助手消息ID字段
-- =======================================================
ALTER TABLE `t_llm_call_records`
ADD COLUMN `assistant_message_id` BIGINT NULL COMMENT '助手消息ID(关联t_chat_messages.message_id, role=assistant)' AFTER `user_message_id`;

-- =======================================================
-- 添加索引以优化查询性能
-- =======================================================
ALTER TABLE `t_llm_call_records`
ADD INDEX `idx_user_message_id` (`user_message_id`);

ALTER TABLE `t_llm_call_records`
ADD INDEX `idx_assistant_message_id` (`assistant_message_id`);

-- =======================================================
-- End of Migration
-- =======================================================