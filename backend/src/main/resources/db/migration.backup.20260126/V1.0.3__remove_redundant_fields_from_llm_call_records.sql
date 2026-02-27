-- =======================================================
-- Crypto Trade Database - Remove Redundant Fields from LLM Call Records
--
-- Date: 2026-02-03
-- Description: 删除 t_llm_call_records 表中的冗余字段 prompt_content 和 original_response_content
--              这些字段的内容已通过 user_message_id 和 assistant_message_id 关联 ChatMessage 获取
-- =======================================================

USE db_crypto_trade;

-- =======================================================
-- 删除冗余字段
-- =======================================================

-- 删除 prompt_content 字段（通过 user_message_id 关联 ChatMessage）
ALTER TABLE `t_llm_call_records`
DROP COLUMN `prompt_content`;

-- 删除 original_response_content 字段（通过 assistant_message_id 关联 ChatMessage）
ALTER TABLE `t_llm_call_records`
DROP COLUMN `original_response_content`;

-- =======================================================
-- 说明
-- =======================================================
-- response_content 字段保留，用于存储 ClearVisionUtils 处理后的响应（仅当处理有变化时）
-- 原始响应通过 assistant_message_id 关联 ChatMessage 获取
-- 原始 prompt 通过 user_message_id 关联 ChatMessage 获取

-- =======================================================
-- End of Migration
-- =======================================================