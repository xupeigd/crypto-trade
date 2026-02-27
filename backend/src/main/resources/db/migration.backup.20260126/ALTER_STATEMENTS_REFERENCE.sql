-- =======================================================
-- ALTER TABLE Statements Reference
-- These are extracted from migration scripts for reference
-- =======================================================

-- Statement 1
ALTER TABLE `t_llm_call_records`
    ADD COLUMN `call_source` VARCHAR(20) NULL COMMENT '调用来源: SCHEDULED(定时任务)/DIRECT(直接API)/MANUAL(页面手动)'
AFTER `parent_id`;

-- Statement 2
ALTER TABLE t_cex_instruments
    ADD COLUMN is_live_trading BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否实盘交易(TRUE=实盘,FALSE=模拟)';

-- Statement 3
ALTER TABLE t_trade_actions
    ADD COLUMN executed_time DATETIME COMMENT '执行时间';

-- Statement 4
ALTER TABLE t_trade_actions
    ADD COLUMN execution_time_ms BIGINT COMMENT '执行耗时(ms)';

-- Statement 5
ALTER TABLE t_trade_actions
    ADD COLUMN order_id VARCHAR(100) COMMENT '订单ID';

-- Statement 6
ALTER TABLE t_trade_actions
    ADD COLUMN executed_price DECIMAL(20, 8) COMMENT '实际成交价';

-- Statement 7
ALTER TABLE t_trade_actions
    ADD COLUMN executed_size DECIMAL(20, 8) COMMENT '实际成交数量';

-- Statement 8
ALTER TABLE t_trade_actions
    ADD COLUMN error_message TEXT COMMENT '错误信息';

-- Statement 9
ALTER TABLE t_trade_actions
    ADD COLUMN parent_action_id BIGINT COMMENT '父动作ID,重放时指向原始动作';

-- Statement 10
ALTER TABLE t_trade_actions
    ADD COLUMN execution_source VARCHAR(20) DEFAULT 'INITIAL' COMMENT '执行来源:INITIAL/REPLAY';

-- Statement 11
ALTER TABLE t_trade_actions
    ADD COLUMN replay_count INT DEFAULT 0 COMMENT '重放次数';

-- Statement 12
ALTER TABLE t_futures_ticker_data
    ADD COLUMN is_live_trading BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否实盘交易(TRUE=实盘,FALSE=模拟)';

-- Statement 13
ALTER TABLE t_risk_control_orders
    ADD COLUMN pos_side VARCHAR(20) COMMENT '持仓方向(long/short)' AFTER order_source;

-- Statement 14
ALTER TABLE t_risk_control_orders
    ADD COLUMN lever DECIMAL(10, 2) COMMENT '杠杆倍数' AFTER pos_side;

-- Statement 15
ALTER TABLE t_risk_control_orders
    ADD COLUMN original_amount DECIMAL(20, 8) COMMENT '原始成本金额(USDT)' AFTER lever;

-- Statement 16
ALTER TABLE `t_ai_model_configs`
    ADD COLUMN `model_type` VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT '模型类型：LOCAL-本地模型，REMOTE-远端模型',
ADD COLUMN `api_url` VARCHAR(500) COMMENT 'API调用地址（远端模型必填）',
ADD COLUMN `api_key` VARCHAR(500) COMMENT 'API密钥（远端模型，加密存储）',
ADD COLUMN `api_format` VARCHAR(50) COMMENT 'API格式：openai, claude, custom等',
ADD COLUMN `timeout_seconds` INT NOT NULL DEFAULT 60 COMMENT '请求超时时间（秒）',
ADD COLUMN `retry_count` INT NOT NULL DEFAULT 3 COMMENT '重试次数',
ADD COLUMN `max_concurrent` INT NOT NULL DEFAULT 5 COMMENT '最大并发请求数';

-- Statement 17
ALTER TABLE `t_ai_model_configs`
    ADD INDEX `idx_model_type` (`model_type`),
ADD INDEX `idx_api_format` (`api_format`);

-- Statement 18
ALTER TABLE t_cex_api_keys
    ADD COLUMN storage_type VARCHAR(10) NOT NULL DEFAULT 'DB';

-- Statement 19
ALTER TABLE t_cex_api_keys RENAME COLUMN api_key TO access_key;

-- Statement 20
ALTER TABLE `t_trade_decisions`
    ADD COLUMN `record_id` BIGINT NULL COMMENT 'LLM调用记录ID(关联t_llm_call_records表)'
AFTER `call_stats_id`;

-- Statement 21
ALTER TABLE `t_trade_decisions`
    MODIFY COLUMN `record_id` BIGINT NOT NULL COMMENT 'LLM调用记录ID(关联t_llm_call_records表)';

-- Statement 22
ALTER TABLE `t_trade_decisions` DROP FOREIGN KEY IF EXISTS `fk_trade_decision_call_stats`;

-- Statement 23
ALTER TABLE `t_trade_decisions` DROP COLUMN IF EXISTS `call_stats_id`;

-- Statement 24
ALTER TABLE `t_trade_decisions`
DROP
COLUMN IF EXISTS `input_total_equity`,
  DROP
COLUMN IF EXISTS `input_available_balance`,
  DROP
COLUMN IF EXISTS `input_used_margin`,
  DROP
COLUMN IF EXISTS `input_unrealized_pnl`,
  DROP
COLUMN IF EXISTS `input_margin_ratio`,
  DROP
COLUMN IF EXISTS `input_position_details`,
  DROP
COLUMN IF EXISTS `input_call_count`,
  DROP
COLUMN IF EXISTS `input_last_call_time`;

-- Statement 25
ALTER TABLE `t_trade_decisions`
    ADD COLUMN `pos_side` VARCHAR(20) COMMENT '持仓方向(long/short)'
AFTER `timeframe`;

-- Statement 26
ALTER TABLE `t_trade_decisions`
    ADD COLUMN `priority` INT COMMENT '优先级(工具调用时使用)'
AFTER `pos_side`;

-- Statement 27
ALTER TABLE `t_trade_decisions`
    ADD COLUMN `limit_count` INT COMMENT '查询条数(工具调用时使用)'
AFTER `priority`;

-- Statement 28
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_action` `action` VARCHAR (20) COMMENT '交易动作(BUY/SELL/HOLD/QUERY/ATTENTION)';

-- Statement 29
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_price` `price` DECIMAL (20, 8) COMMENT '决策价格';

-- Statement 30
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_quantity` `quantity` DECIMAL (20, 8) COMMENT '决策数量';

-- Statement 31
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_take_profit` `take_profit` DECIMAL (20, 8) COMMENT '止盈价格';

-- Statement 32
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_stop_loss` `stop_loss` DECIMAL (20, 8) COMMENT '止损价格';

-- Statement 33
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_confidence` `confidence` DECIMAL (5, 2) COMMENT '置信度(0-100)';

-- Statement 34
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `decision_reasoning` `reasoning` TEXT COMMENT '决策推理过程';

-- Statement 35
ALTER TABLE `t_trade_decisions`
    CHANGE COLUMN `target_inst_id` `inst_id` VARCHAR (50) COMMENT '目标合约代码';

-- Statement 36
ALTER TABLE `t_trade_decisions`
DROP
COLUMN `decision_risk_level`;

-- Statement 37
ALTER TABLE `t_trade_decisions`
DROP
COLUMN `selected_instruments`;

-- Statement 38
ALTER TABLE `t_trade_decisions`
DROP
COLUMN `thinking_process`;

-- Statement 39
ALTER TABLE `t_trade_decisions`
DROP
COLUMN `full_response`;

-- Statement 40
ALTER TABLE `t_trade_decisions`
DROP
COLUMN `segments_json`;

-- Statement 41
ALTER TABLE `t_risk_control_orders`
    ADD COLUMN `estimated_total_capital` DECIMAL(20, 8) COMMENT '预估总占用资金(保证金+手续费)'
AFTER `original_amount`;

-- Statement 42
ALTER TABLE `t_trade_decisions_backup` COMMENT = 'TradeDecision备份表 - 已废弃,改用LlmCallRecord管理AI决策';

-- Statement 43
ALTER TABLE t_trade_actions
    ADD COLUMN open_close VARCHAR(20) COMMENT '开平仓类型：open=开仓，close=平仓';

-- Statement 44
ALTER TABLE t_ai_model_configs
    ADD COLUMN extra_body TEXT COMMENT '额外的请求体参数(JSON格式),用于覆盖或补充默认API参数';

-- Statement 45
ALTER TABLE t_ai_model_configs MODIFY COLUMN extra_body TEXT COMMENT '额外的请求体参数(JSON格式),例如: {"temperature": 0.8, "top_p": 0.9}';

-- Statement 46
ALTER TABLE t_llm_call_record
    ADD COLUMN round_number INT COMMENT '对话轮次,第1轮为1,第2轮为2...';

-- Statement 47
ALTER TABLE t_llm_call_record
    ADD COLUMN conversation_state VARCHAR(50) COMMENT '会话状态: PROCESSING/COMPLETED/TERMINATED/NEED_MORE_INFO/ERROR_STATE';

-- Statement 48
ALTER TABLE t_llm_call_record
    MODIFY COLUMN round_number INT COMMENT '对话轮次,第1轮为1,第2轮为2...用于标识多轮对话的顺序';

-- Statement 49
ALTER TABLE t_llm_call_record
    MODIFY COLUMN conversation_state VARCHAR (50) COMMENT '会话状态: PROCESSING-处理中,COMPLETED-已完成,TERMINATED-已终止,NEED_MORE_INFO-需要更多信息,ERROR_STATE-错误状态';

-- Statement 50
ALTER TABLE t_cex_trading_orders
    ADD COLUMN cl_ord_id VARCHAR(50);

-- Statement 51
ALTER TABLE t_cex_trading_orders
    ADD COLUMN inst_type VARCHAR(20);

-- Statement 52
ALTER TABLE t_cex_trading_orders
    ADD COLUMN rebate DECIMAL(38, 8);

-- Statement 53
ALTER TABLE t_cex_trading_orders
    ADD COLUMN rebate_ccy VARCHAR(10);

-- Statement 54
ALTER TABLE t_risk_control_config MODIFY COLUMN current_trading_style VARCHAR (20) NOT NULL;

-- Statement 55
ALTER TABLE t_risk_control_config MODIFY COLUMN default_trading_style VARCHAR (20) NOT NULL;

-- Statement 56
ALTER TABLE t_trading_style_history MODIFY COLUMN old_style VARCHAR (20) NOT NULL;

-- Statement 57
ALTER TABLE t_trading_style_history MODIFY COLUMN new_style VARCHAR (20) NOT NULL;

-- Statement 58
ALTER TABLE t_risk_control_config MODIFY COLUMN current_trading_style ENUM('C1_CONSERVATIVE','C2_CAUTIOUS','C3_MODERATE','C4_ACTIVE','C5_AGGRESSIVE') NOT NULL COMMENT '当前交易风格：C1_CONSERVATIVE(保守型3%/5%), C2_CAUTIOUS(谨慎型5%/10%), C3_MODERATE(稳健型8%/15%), C4_ACTIVE(积极型12%/20%), C5_AGGRESSIVE(激进型18%/30%)';

-- Statement 59
ALTER TABLE t_risk_control_config MODIFY COLUMN default_trading_style ENUM('C1_CONSERVATIVE','C2_CAUTIOUS','C3_MODERATE','C4_ACTIVE','C5_AGGRESSIVE') NOT NULL COMMENT '默认交易风格：C1_CONSERVATIVE(保守型3%/5%), C2_CAUTIOUS(谨慎型5%/10%), C3_MODERATE(稳健型8%/15%), C4_ACTIVE(积极型12%/20%), C5_AGGRESSIVE(激进型18%/30%)';

-- Statement 60
ALTER TABLE t_trading_style_history MODIFY COLUMN old_style ENUM('C1_CONSERVATIVE','C2_CAUTIOUS','C3_MODERATE','C4_ACTIVE','C5_AGGRESSIVE') NOT NULL COMMENT '原交易风格';

-- Statement 61
ALTER TABLE t_trading_style_history MODIFY COLUMN new_style ENUM('C1_CONSERVATIVE','C2_CAUTIOUS','C3_MODERATE','C4_ACTIVE','C5_AGGRESSIVE') NOT NULL COMMENT '新交易风格';

-- Statement 62
ALTER TABLE t_trade_actions
    ADD COLUMN risk_control_status VARCHAR(20) COMMENT '风控状态:PENDING/APPROVED/REJECTED/BYPASSED';

-- Statement 63
ALTER TABLE t_trade_actions
    ADD COLUMN risk_control_id BIGINT COMMENT '关联的风控订单ID';

-- Statement 64
ALTER TABLE t_llm_call_records
ADD COLUMN prompt_generation_time_ms BIGINT COMMENT 'Prompt生成耗时(毫秒),记录生成完整Prompt所需的时间';

-- Statement 65
ALTER TABLE t_llm_call_records
ADD COLUMN llm_call_time_ms BIGINT COMMENT '大模型调用耗时(毫秒),记录AI模型API调用的实际耗时';

-- Statement 66
ALTER TABLE t_llm_call_records
ADD COLUMN post_action_time_ms BIGINT COMMENT '后置动作耗时(毫秒),记录响应解析、决策提取等后置处理时间';

-- Statement 67
ALTER TABLE t_futures_ticker_data
    ADD COLUMN ts_hour_str VARCHAR(20) COMMENT '时间戳小时格式字符串(yyyy-MM-dd HH)';

-- Statement 68
ALTER TABLE t_futures_ticker_data
    ADD UNIQUE KEY uk_vendor_inst_id_hour (vendor, inst_id, ts_hour_str);

-- Statement 69
ALTER TABLE t_position_snapshot
    ADD COLUMN pos_id TEXT;

-- Statement 70
ALTER TABLE t_cex_api_keys
    ADD COLUMN is_live_trading BOOLEAN NOT NULL DEFAULT FALSE;

-- Statement 71
ALTER TABLE t_okx_positions
    ADD COLUMN api_key_id INTEGER;

-- Statement 72
ALTER TABLE t_trading_orders
    ADD COLUMN take_profit_price DECIMAL(38, 8),          -- 止盈价格
ADD COLUMN stop_loss_price DECIMAL(38,8),             -- 止损价格
ADD COLUMN take_profit_pct DECIMAL(8,4),              -- 止盈百分比
ADD COLUMN stop_loss_pct DECIMAL(8,4);

-- Statement 73
ALTER TABLE t_trading_orders
    -- 添加系统订单UUID
    ADD COLUMN order_uuid VARCHAR(50) UNIQUE COMMENT '系统订单UUID' AFTER id,

    -- 添加业务扩展字段
    ADD COLUMN strategy_id BIGINT COMMENT '策略ID' AFTER source,
    ADD COLUMN bot_id BIGINT COMMENT '机器人ID' AFTER strategy_id,
    ADD COLUMN risk_control_id BIGINT COMMENT '风控ID' AFTER bot_id,

    -- 添加时间字段
    ADD COLUMN submitted_time TIMESTAMP COMMENT '提交到CEX的时间' AFTER created_time,
    ADD COLUMN completed_time TIMESTAMP COMMENT '完成时间' AFTER submitted_time;

-- Statement 74
ALTER TABLE t_trading_orders
    ADD COLUMN cex_order_id VARCHAR(50) COMMENT '关联的CEX订单ID' AFTER order_status;

-- Statement 75
ALTER TABLE t_trading_orders COMMENT = '系统交易订单表（业务视角）';

-- Statement 76
ALTER TABLE t_cex_trading_orders COMMENT = 'CEX交易订单表（CEX视角）';

-- Statement 77
ALTER TABLE t_kline_data
    ADD COLUMN confirm TINYINT(1) DEFAULT 0
COMMENT 'K线状态: 0未完结，1已完结';

-- Statement 78
ALTER TABLE t_trading_orders
    ADD COLUMN IF NOT EXISTS take_profit_price DECIMAL (38,8);

-- Statement 79
ALTER TABLE t_trading_orders
    ADD COLUMN IF NOT EXISTS stop_loss_price DECIMAL (38,8);

-- Statement 80
ALTER TABLE t_trading_orders
    ADD COLUMN IF NOT EXISTS take_profit_pct DECIMAL (8,4);

-- Statement 81
ALTER TABLE t_trading_orders
    ADD COLUMN IF NOT EXISTS stop_loss_pct DECIMAL (8,4);

-- Statement 82
ALTER TABLE t_trading_orders
    ADD COLUMN pos_side VARCHAR(20);

-- Statement 83
ALTER TABLE t_risk_control_orders
    ADD COLUMN action_id BIGINT COMMENT '关联的TradeAction ID';

