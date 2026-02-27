-- =======================================================
-- Crypto Trade Database - Default Data Initialization
--
-- Date: 2026-01-26
-- Description: 插入系统运行所需的默认配置数据
-- =======================================================

USE crypto_trade;

-- =======================================================
-- 1. 计划任务配置
-- =======================================================

INSERT IGNORE INTO t_scheduled_tasks (task_id, task_name, task_type, cron_expression, timeout_seconds, status, description) VALUES
(1, 'fetch_okx_funding_rates', 'data_fetch', '0 0/5 * * * ?', 60, 'active', '获取OKX资金费率数据'),
(2, 'fetch_okf_ticker_data', 'data_fetch', '0 0/1 * * * ?', 120, 'active', '获取OKX行情数据'),
(3, 'fetch_okx_balance', 'data_fetch', '0 0/10 * * * ?', 60, 'active', '获取OKX账户余额'),
(4, 'calculate_funding_arbitrage', 'data_calculation', '0 0/5 * * * ?', 180, 'active', '计算资金费套利机会'),
(5, 'cleanup_old_executions', 'data_calculation', '0 0 0 1/1 * ?', 300, 'active', '清理过期的执行记录');

-- =======================================================
-- 2. 数据获取配置
-- =======================================================

INSERT IGNORE INTO t_data_fetch_configs (config_id, task_id, cex_base_url, api_path, http_method, requires_auth, data_processor_class, target_duckdb_table, response_mapping) VALUES
(1, 1, 'https://www.okx.com', '/api/v5/public/funding-rate', 'GET', FALSE, 'com.crypto.trade.processor.OkxFundingRateProcessor', 'okx_funding_rates', '{"data":"data"}'),
(2, 2, 'https://www.okx.com', '/api/v5/market/ticker', 'GET', FALSE, 'com.crypto.trade.processor.OkxTickerProcessor', 'okx_tickers', '{"data":"data"}'),
(3, 3, 'https://www.okx.com', '/api/v5/account/balance', 'GET', TRUE, 'com.crypto.trade.processor.OkxBalanceProcessor', 'okx_balances', '{"data":"data"}');

-- =======================================================
-- 3. 存储过程
-- =======================================================

-- 清理旧执行记录（保留最近30天）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS CleanupOldExecutions()
BEGIN
    DELETE FROM t_task_executions
    WHERE created_time < DATE_SUB(NOW(), INTERVAL 30 DAY);

    SELECT ROW_COUNT() AS deleted_count;
END //
DELIMITER ;

-- 获取CEX余额汇总
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS GetCexBalanceSummary(IN p_api_key_id BIGINT)
BEGIN
    SELECT
        api_key_id,
        cex_name,
        balance_type,
        currency,
        SUM(balance) AS total_balance,
        SUM(frozen_balance) AS total_frozen,
        SUM(available_balance) AS total_available,
        MAX(last_update_time) AS last_update
    FROM t_cex_balances
    WHERE api_key_id = p_api_key_id
    GROUP BY api_key_id, cex_name, balance_type, currency
    ORDER BY total_available DESC;
END //
DELIMITER ;

-- =======================================================
-- 4. 视图
-- =======================================================

-- 活跃交易订单汇总
CREATE OR REPLACE VIEW v_active_orders AS
SELECT
    to1.id,
    to1.order_id,
    to1.api_key_id,
    tca.cex_name,
    to1.inst_id,
    to1.side,
    to1.order_type,
    to1.sz,
    to1.filled_sz,
    (to1.filled_sz / to1.sz) * 100 AS fill_percentage,
    to1.avg_px,
    to1.order_state,
    to1.take_profit_price,
    to1.stop_loss_price,
    to1.created_time,
    to1.updated_time
FROM t_trading_orders to1
LEFT JOIN t_cex_api_keys tca ON to1.api_key_id = tca.key_id
WHERE to1.order_state IN ('live', 'partially_filled')
ORDER BY to1.created_time DESC;

-- 任务执行统计
CREATE OR REPLACE VIEW v_task_execution_stats AS
SELECT
    task_id,
    task_name,
    task_type,
    status,
    COUNT(*) AS total_executions,
    COUNT(CASE WHEN execution_status = 'success' THEN 1 END) AS success_count,
    COUNT(CASE WHEN execution_status = 'failed' THEN 1 END) AS failed_count,
    COUNT(CASE WHEN execution_status = 'running' THEN 1 END) AS running_count,
    ROUND(COUNT(CASE WHEN execution_status = 'success' THEN 1 END) * 100.0 / COUNT(*), 2) AS success_rate,
    MAX(created_time) AS last_execution
FROM t_task_executions te
JOIN t_scheduled_tasks st ON te.task_id = st.task_id
GROUP BY task_id, task_name, task_type, status
ORDER BY success_rate DESC, total_executions DESC;

-- =======================================================
-- 5. 默认AI模型配置 (可选,根据实际需求配置)
-- =======================================================

-- 本地模型示例
INSERT IGNORE INTO t_ai_model_configs (
    model_id, display_name, provider, parameter_size, description,
    is_active, max_tokens, default_model, model_type, api_format,
    timeout_seconds, retry_count, max_concurrent
) VALUES
(
    'deepseek-r1:14b',
    'DeepSeek R1 14B',
    'deepseek',
    14,
    '默认交易决策模型',
    TRUE,
    128000,
    TRUE,
    'LOCAL',
    'ollama',
    60,
    3,
    5
),
(
    'qwen3:4b',
    'Qwen3 4B',
    'alibaba',
    4,
    '轻量级快速响应模型',
    TRUE,
    8192,
    FALSE,
    'LOCAL',
    'ollama',
    60,
    3,
    5
);

-- =======================================================
-- End of Default Data Initialization
-- =======================================================
