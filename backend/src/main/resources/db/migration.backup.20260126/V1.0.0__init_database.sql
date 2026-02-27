-- =======================================================
-- Crypto Trade Database - Initial Schema
--
-- Generated from migration scripts + Entity classes
-- Date: 2026-01-26
-- Total tables: 30 (22 from migration + 8 from entities)
-- =======================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS crypto_trade
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE crypto_trade;

-- 设置外键检查
SET FOREIGN_KEY_CHECKS = 0;

-- =======================================================
-- Drop existing tables (in reverse order of dependencies)
-- =======================================================

DROP TABLE IF EXISTS t_trading_orders;
DROP TABLE IF EXISTS t_trade_decisions;
DROP TABLE IF EXISTS t_task_executions;
DROP TABLE IF EXISTS t_scheduled_tasks;
DROP TABLE IF EXISTS t_risk_mode_history;
DROP TABLE IF EXISTS t_proxy_service_configs;
DROP TABLE IF EXISTS t_position_snapshot;
DROP TABLE IF EXISTS t_order_executions;
DROP TABLE IF EXISTS t_llm_call_stats;
DROP TABLE IF EXISTS t_llm_audit_logs;
DROP TABLE IF EXISTS t_kline_data;
DROP TABLE IF EXISTS t_data_fetch_configs;
DROP TABLE IF EXISTS t_conversation_actions;
DROP TABLE IF EXISTS t_chat_sessions;
DROP TABLE IF EXISTS t_chat_messages;
DROP TABLE IF EXISTS t_cex_trading_orders;
DROP TABLE IF EXISTS t_cex_instruments;
DROP TABLE IF EXISTS t_cex_balances;
DROP TABLE IF EXISTS t_cex_api_keys;
DROP TABLE IF EXISTS t_cex_api_call_records;
DROP TABLE IF EXISTS t_ai_model_configs;
DROP TABLE IF EXISTS t_account_equity_snapshot;
DROP TABLE IF EXISTS t_trade_balance_snapshots;
DROP TABLE IF EXISTS t_trading_style_history;
DROP TABLE IF EXISTS t_trade_actions;
DROP TABLE IF EXISTS t_risk_control_orders;
DROP TABLE IF EXISTS t_risk_control_config;
DROP TABLE IF EXISTS t_llm_call_records;
DROP TABLE IF EXISTS t_futures_ticker_data;
DROP TABLE IF EXISTS t_funding_rate_data;

-- =======================================================
-- Create tables
-- =======================================================

-- t_account_equity_snapshot
CREATE TABLE IF NOT EXISTS t_account_equity_snapshot
(
    equity_id
    INTEGER
    PRIMARY
    KEY
    AUTOINCREMENT,
    api_key_id
    INTEGER
    NOT
    NULL, -- 关联的API密钥ID
    total_equity_usdt
    DECIMAL
(
    38,
    8
) NOT NULL, -- 总权益(USDT)
    available_equity_usdt DECIMAL
(
    38,
    8
) NOT NULL, -- 可用权益(USDT)
    frozen_equity_usdt DECIMAL
(
    38,
    8
) DEFAULT 0, -- 冻结权益(USDT)
    margin_equity_usdt DECIMAL
(
    38,
    8
) DEFAULT 0, -- 保证金权益(USDT)
    update_time TEXT NOT NULL, -- 更新时间
    created_time TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_time TEXT DEFAULT CURRENT_TIMESTAMP
    );

-- t_ai_model_configs
CREATE TABLE `t_ai_model_configs`
(
    `config_id`      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `model_id`       VARCHAR(100) NOT NULL UNIQUE COMMENT '模型ID',
    `display_name`   VARCHAR(200) NOT NULL COMMENT '显示名称',
    `provider`       VARCHAR(50)  NOT NULL COMMENT '提供商',
    `parameter_size` INT COMMENT '参数规模',
    `description`    TEXT COMMENT '模型描述',
    `is_active`      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `max_tokens`     BIGINT COMMENT '最大token数',
    `cost_per_token` DECIMAL(10, 6) COMMENT '每token成本',
    `default_model`  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否为默认模型',
    `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX            `idx_model_id` (`model_id`),
    INDEX            `idx_provider` (`provider`),
    INDEX            `idx_is_active` (`is_active`),
    INDEX            `idx_default_model` (`default_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型配置表';

-- t_cex_api_call_records
CREATE TABLE t_cex_api_call_records
(
    -- 主键
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- API类型和交易所信息
    api_type       VARCHAR(50)  NOT NULL COMMENT 'API类型(place_order/close_position/cancel_order)',
    exchange       VARCHAR(50)  NOT NULL COMMENT '交易所(okx/binance/bybit)',
    http_method    VARCHAR(10)  NOT NULL COMMENT 'HTTP方法(GET/POST/DELETE)',

    -- 请求相关信息
    api_path       VARCHAR(500) NOT NULL COMMENT 'API路径',
    request_params TEXT COMMENT '请求参数JSON',
    call_time      TIMESTAMP    NOT NULL COMMENT '调用开始时间',

    -- 响应相关信息
    http_status    INT COMMENT 'HTTP状态码',
    response_body  TEXT COMMENT '响应体JSON',
    response_time  TIMESTAMP COMMENT '响应时间',
    duration_ms    BIGINT COMMENT '耗时(毫秒)',

    -- 订单相关信息
    order_id       VARCHAR(100) COMMENT '订单ID',
    inst_id        VARCHAR(100) COMMENT '合约代码',
    order_type     VARCHAR(50) COMMENT '订单类型(market/limit/take_profit/stop_loss)',

    -- 状态和错误信息
    status         VARCHAR(50)  NOT NULL COMMENT '调用状态(pending/success/failed/timeout)',
    error_message  TEXT COMMENT '错误信息',

    -- 审计字段
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX          idx_api_type (api_type) COMMENT 'API类型索引',
    INDEX          idx_exchange (exchange) COMMENT '交易所索引',
    INDEX          idx_call_time (call_time) COMMENT '调用时间索引',
    INDEX          idx_order_id (order_id) COMMENT '订单ID索引',
    INDEX          idx_inst_id (inst_id) COMMENT '合约代码索引',
    INDEX          idx_status (status) COMMENT '状态索引',
    INDEX          idx_create_time (create_time) COMMENT '创建时间索引'
) COMMENT='CEX API调用记录表';

-- t_cex_api_keys
CREATE TABLE IF NOT EXISTS t_cex_api_keys
(
    key_id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    cex_name
    VARCHAR
(
    50
) NOT NULL,
    access_key VARCHAR
(
    200
) NOT NULL,
    secret_key VARCHAR
(
    200
) NOT NULL,
    pass_phrase VARCHAR
(
    200
),
    storage_type VARCHAR
(
    10
) NOT NULL DEFAULT 'DB',
    status VARCHAR
(
    20
) DEFAULT 'active',
    is_live_trading BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR
(
    500
),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cex_name_status
(
    cex_name,
    status
),
    INDEX idx_storage_type
(
    storage_type
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_cex_balances
CREATE TABLE IF NOT EXISTS t_cex_balances
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    api_key_id
    BIGINT
    NOT
    NULL,
    cex_name
    VARCHAR
(
    20
) NOT NULL,
    currency VARCHAR
(
    20
) NOT NULL,
    balance DECIMAL
(
    38,
    8
) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL
(
    38,
    8
) NOT NULL DEFAULT 0,
    available_balance DECIMAL
(
    38,
    8
) GENERATED ALWAYS AS
(
    balance
    -
    frozen_balance
) STORED,
    balance_type VARCHAR
(
    20
) NOT NULL DEFAULT 'funding',
    last_update_time DATETIME NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_key_currency_type
(
    api_key_id,
    currency,
    balance_type
),
    INDEX idx_last_update
(
    last_update_time
),
    FOREIGN KEY
(
    api_key_id
) REFERENCES t_cex_api_keys
(
    key_id
) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_cex_instruments
CREATE TABLE `t_cex_instruments`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider`            varchar(20)  NOT NULL COMMENT '服务商名称',
    `inst_type`           varchar(20)  NOT NULL COMMENT '合约类型：SPOT, SWAP, FUTURES, OPTION',
    `inst_id`             varchar(100) NOT NULL COMMENT '合约ID',
    `base_ccy`            varchar(20)  DEFAULT NULL COMMENT '基础货币',
    `quote_ccy`           varchar(20)  DEFAULT NULL COMMENT '计价货币',
    `settle_ccy`          varchar(20)  DEFAULT NULL COMMENT '结算货币',
    `category`            varchar(20)  DEFAULT NULL COMMENT '合约分类',
    `ct_val`              varchar(50)  DEFAULT NULL COMMENT '合约面值',
    `ct_mult`             varchar(50)  DEFAULT NULL COMMENT '合约乘数',
    `ct_val_ccy`          varchar(20)  DEFAULT NULL COMMENT '合约面值货币',
    `opt_type`            varchar(10)  DEFAULT NULL COMMENT '期权类型',
    `stk`                 varchar(50)  DEFAULT NULL COMMENT '行权价',
    `list_time`           varchar(50)  DEFAULT NULL COMMENT '上市时间',
    `exp_time`            varchar(50)  DEFAULT NULL COMMENT '到期时间',
    `lever`               varchar(20)  DEFAULT NULL COMMENT '杠杆倍数',
    `tick_sz`             varchar(50)  DEFAULT NULL COMMENT '价格精度',
    `lot_sz`              varchar(50)  DEFAULT NULL COMMENT '下单数量精度',
    `min_sz`              varchar(50)  DEFAULT NULL COMMENT '最小下单数量',
    `max_lmt_sz`          varchar(50)  DEFAULT NULL COMMENT '最大限价单数量',
    `max_mkt_sz`          varchar(50)  DEFAULT NULL COMMENT '最大市价单数量',
    `max_ts_sz`           varchar(50)  DEFAULT NULL COMMENT '最大挂单数量',
    `state`               varchar(20)  DEFAULT NULL COMMENT '合约状态：live, suspend, preopen',
    `alias`               varchar(100) DEFAULT NULL COMMENT '合约别名',
    `max_lmt`             varchar(50)  DEFAULT NULL COMMENT '最大持仓限制',
    `max_mkt`             varchar(50)  DEFAULT NULL COMMENT '最大市场限制',
    `position_idx`        varchar(20)  DEFAULT NULL COMMENT '持仓方向模式',
    `is_leverage`         varchar(10)  DEFAULT NULL COMMENT '是否支持杠杆',
    `fee_rate`            varchar(20)  DEFAULT NULL COMMENT '交易费率',
    `data_ingestion_time` datetime     NOT NULL COMMENT '数据入库时间',
    `created_at`          datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_inst_id` (`provider`, `inst_id`),
    KEY                   `idx_provider` (`provider`),
    KEY                   `idx_inst_type` (`inst_type`),
    KEY                   `idx_data_ingestion_time` (`data_ingestion_time`),
    KEY                   `idx_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CEX交易所合约信息表';

-- t_cex_trading_orders
CREATE TABLE t_cex_trading_orders
(
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- CEX订单标识
    order_id         VARCHAR(50)    NOT NULL UNIQUE COMMENT 'CEX返回的订单ID',
    exchange         VARCHAR(10)    NOT NULL COMMENT '交易所（okx/binance/bybit）',
    api_key_id       BIGINT         NOT NULL COMMENT 'API Key ID（逻辑关联，无外键）',

    -- 订单详情
    inst_id          VARCHAR(50)    NOT NULL COMMENT '合约品种',
    side             VARCHAR(20)    NOT NULL COMMENT '方向（buy/sell）',
    pos_side         VARCHAR(20)    NOT NULL COMMENT '持仓方向（long/short/net）',
    order_type       VARCHAR(20)    NOT NULL COMMENT '订单类型（market/limit）',
    td_mode          VARCHAR(20)    NOT NULL COMMENT '交易模式（isolated/cross）',
    ccy              VARCHAR(10)    NOT NULL COMMENT '保证金币种',

    -- 委托数量和价格
    sz               DECIMAL(38, 8) NOT NULL COMMENT '委托数量',
    px               DECIMAL(38, 8) COMMENT '委托价格',
    amt              DECIMAL(38, 8) COMMENT '委托金额',

    -- 订单状态（CEX视角）
    order_state      VARCHAR(20)    NOT NULL COMMENT 'CEX订单状态（live/partially_filled/filled/canceled/failed）',
    state_msg        VARCHAR(100) COMMENT '状态描述',

    -- 成交信息
    avg_px           DECIMAL(38, 8) COMMENT '成交均价',
    filled_sz        DECIMAL(38, 8) DEFAULT 0 COMMENT '已成交数量',
    filled_amt       DECIMAL(38, 8) DEFAULT 0 COMMENT '已成交金额',
    fill_ratio       DECIMAL(8, 4)  DEFAULT 0 COMMENT '成交比例（%）',

    -- 手续费
    fee              DECIMAL(38, 8) DEFAULT 0 COMMENT '手续费',
    fee_ccy          VARCHAR(10) COMMENT '手续费币种',

    -- CEX返回的原始数据
    acc_fill_sz      VARCHAR(50) COMMENT '累计成交数量字符串',
    c_time           TIMESTAMP COMMENT 'CEX订单创建时间',
    u_time           TIMESTAMP COMMENT 'CEX订单最后更新时间',

    -- 同步元数据
    last_sync_time   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP COMMENT '最后同步时间',
    sync_status      VARCHAR(20)    DEFAULT 'synced' COMMENT '同步状态（synced/syncing/failed/stale）',
    sync_error_msg   TEXT COMMENT '同步错误信息',
    sync_retry_count INT            DEFAULT 0 COMMENT '同步重试次数',

    -- 时间戳
    created_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    exec_time        TIMESTAMP COMMENT '最后成交时间',

    -- 外键约束
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CEX交易订单表';

-- t_chat_messages
CREATE TABLE IF NOT EXISTS t_chat_messages
(
    message_id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    session_id
    BIGINT
    NOT
    NULL,
    role
    VARCHAR
(
    20
) NOT NULL,
    content TEXT NOT NULL,
    tokens_used INTEGER,
    processing_time_ms BIGINT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY
(
    session_id
) REFERENCES t_chat_sessions
(
    session_id
) ON DELETE CASCADE,
    INDEX idx_session_created
(
    session_id,
    created_time
),
    INDEX idx_role
(
    role
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_chat_sessions
CREATE TABLE IF NOT EXISTS t_chat_sessions
(
    session_id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    session_name
    VARCHAR
(
    100
),
    user_id VARCHAR
(
    50
) DEFAULT 'default',
    status VARCHAR
(
    20
) DEFAULT 'active',
    model_name VARCHAR
(
    50
) DEFAULT 'deepseek-r1:14b',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status
(
    user_id,
    status
),
    INDEX idx_model_name
(
    model_name
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_conversation_actions
CREATE TABLE `t_conversation_actions`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`         VARCHAR(255) NOT NULL COMMENT '会话ID',
    `message_id`         VARCHAR(255) NOT NULL COMMENT '消息ID',
    `decision_id`        VARCHAR(255) NOT NULL COMMENT '决策ID',
    `action_type`        VARCHAR(50)  NOT NULL COMMENT '动作类型：k_line, position_info, balance_info等',
    `action_parameters`  TEXT COMMENT 'JSON格式的动作参数',
    `action_result`      TEXT COMMENT 'JSON格式的执行结果',
    `status`             VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SUCCESS/FAILED',
    `processing_time_ms` BIGINT COMMENT '处理时间（毫秒）',
    `error_message`      TEXT COMMENT '错误消息',
    `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX                `idx_session_id` (`session_id`),
    INDEX                `idx_decision_id` (`decision_id`),
    INDEX                `idx_action_type` (`action_type`),
    INDEX                `idx_status` (`status`),
    INDEX                `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话动作记录表';

-- t_data_fetch_configs
CREATE TABLE IF NOT EXISTS t_data_fetch_configs
(
    config_id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    task_id
    BIGINT
    NOT
    NULL
    UNIQUE,
    cex_base_url
    VARCHAR
(
    200
) NOT NULL,
    api_path VARCHAR
(
    200
) NOT NULL,
    http_method VARCHAR
(
    10
) NOT NULL,
    request_params TEXT,
    requires_auth BOOLEAN NOT NULL DEFAULT FALSE,
    auth_key_id BIGINT,
    signature_class VARCHAR
(
    200
),
    data_processor_class VARCHAR
(
    200
) NOT NULL,
    target_duckdb_table VARCHAR
(
    100
) NOT NULL,
    response_mapping TEXT,
    requires_proxy BOOLEAN NOT NULL DEFAULT FALSE,
    proxy_id BIGINT,
    INDEX idx_task_id
(
    task_id
),
    INDEX idx_auth_key
(
    auth_key_id
),
    INDEX idx_proxy_id
(
    proxy_id
),
    FOREIGN KEY
(
    auth_key_id
) REFERENCES t_cex_api_keys
(
    key_id
) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_kline_data
CREATE TABLE `t_kline_data`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider`     varchar(20)  NOT NULL COMMENT '供应商名称',
    `inst_id`      varchar(100) NOT NULL COMMENT '合约ID',
    `timeframe`    varchar(10)  NOT NULL COMMENT '时间周期：1m,5m,15m,1H,4H,1D',
    `kline_time`   bigint       NOT NULL COMMENT 'K线时间点（毫秒时间戳）',
    `open_price`   decimal(20, 8) DEFAULT NULL COMMENT '开盘价',
    `high_price`   decimal(20, 8) DEFAULT NULL COMMENT '最高价',
    `low_price`    decimal(20, 8) DEFAULT NULL COMMENT '最低价',
    `close_price`  decimal(20, 8) DEFAULT NULL COMMENT '收盘价',
    `volume`       decimal(30, 8) DEFAULT NULL COMMENT '成交量（基础货币）',
    `quote_volume` decimal(30, 8) DEFAULT NULL COMMENT '成交量（计价货币）',
    `quote_asset`  varchar(20)    DEFAULT NULL COMMENT '计价资产',
    `base_asset`   varchar(20)    DEFAULT NULL COMMENT '基础资产',
    `created_at`   datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_timeframe_kline_time` (`provider`, `inst_id`, `timeframe`, `kline_time`),
    KEY            `idx_provider_inst_id` (`provider`, `inst_id`),
    KEY            `idx_inst_id_timeframe` (`inst_id`, `timeframe`),
    KEY            `idx_kline_time` (`kline_time`),
    KEY            `idx_provider_timeframe` (`provider`, `timeframe`),
    KEY            `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='K线数据表';

-- t_llm_audit_logs
CREATE TABLE `t_llm_audit_logs`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`         VARCHAR(100) NOT NULL COMMENT '会话ID，用于关联同一次业务调用',
    `call_stats_id`      BIGINT       NOT NULL COMMENT 'LLM调用统计ID，关联t_llm_call_stats表',
    `api_key_id`         BIGINT       NOT NULL COMMENT 'API密钥ID，关联t_cex_api_keys表',
    `model_name`         VARCHAR(100) NOT NULL COMMENT '使用的AI模型名称',
    `prompt_content`     LONGTEXT     NOT NULL COMMENT '完整的prompt内容',
    `ai_response`        LONGTEXT COMMENT 'AI原始响应内容',
    `processing_time_ms` BIGINT COMMENT '处理时间（毫秒）',
    `call_status`        ENUM('SUCCESS', 'FAILED') NOT NULL DEFAULT 'SUCCESS' COMMENT '调用状态',
    `error_message`      TEXT COMMENT '错误信息（仅在失败时有值）',
    `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX                `idx_session_id` (`session_id`),
    INDEX                `idx_call_stats_id` (`call_stats_id`),
    INDEX                `idx_api_key_id` (`api_key_id`),
    INDEX                `idx_model_name` (`model_name`),
    INDEX                `idx_call_status` (`call_status`),
    INDEX                `idx_created_time` (`created_time`),
    FOREIGN KEY (`call_stats_id`) REFERENCES `t_llm_call_stats` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`api_key_id`) REFERENCES `t_cex_api_keys` (`key_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM调用审计日志表';

-- t_llm_call_stats
CREATE TABLE `t_llm_call_stats`
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `api_key_id`         BIGINT       NOT NULL COMMENT 'API密钥ID',
    `model_name`         VARCHAR(100) NOT NULL DEFAULT 'deepseek-r1:14b' COMMENT '模型名称',
    `call_count`         INT          NOT NULL DEFAULT 1 COMMENT '调用次数（本次调用 increment后的值）',
    `current_call_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '当前调用时间',
    `last_call_time`     DATETIME COMMENT '上次调用时间',
    `session_id`         VARCHAR(100) COMMENT '会话ID（可选）',
    `processing_time_ms` BIGINT COMMENT '本次调用处理时间（毫秒）',
    `success`            BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '调用是否成功',
    `error_message`      TEXT COMMENT '错误信息（如果调用失败）',
    `created_time`       DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`       DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX                `idx_api_key_id` (`api_key_id`),
    INDEX                `idx_model_name` (`model_name`),
    INDEX                `idx_current_call_time` (`current_call_time`),
    INDEX                `idx_created_time` (`created_time`),
    FOREIGN KEY (`api_key_id`) REFERENCES `t_cex_api_keys` (`key_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM调用统计表';

-- t_order_executions
CREATE TABLE IF NOT EXISTS t_order_executions
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    order_id
    VARCHAR
(
    50
) NOT NULL UNIQUE,
    execution_id VARCHAR
(
    50
),
    api_key_id BIGINT,
    execution_time DATETIME,
    order_result VARCHAR
(
    50
),
    error_msg TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_id
(
    order_id
),
    INDEX idx_execution_time
(
    execution_time
),
    INDEX idx_api_key
(
    api_key_id
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_position_snapshot
CREATE TABLE IF NOT EXISTS t_position_snapshot
(
    snapshot_id
    INTEGER
    PRIMARY
    KEY
    AUTOINCREMENT,
    api_key_id
    INTEGER
    NOT
    NULL, -- 关联的API密钥ID
    inst_id
    TEXT
    NOT
    NULL, -- 合约代码(如 ETH-USDT-SWAP)
    inst_type
    TEXT
    NOT
    NULL, -- 合约类型(SWAP/FUTURES/OPTION)
    pos_side
    TEXT
    NOT
    NULL, -- 持仓方向(long/short/net)
    pos
    DECIMAL
(
    38,
    8
) NOT NULL, -- 持仓数量(张)
    avail_pos DECIMAL
(
    38,
    8
) DEFAULT 0, -- 可用持仓数量
    avg_px DECIMAL
(
    38,
    8
), -- 平均开仓价格
    mark_px DECIMAL
(
    38,
    8
), -- 标记价格
    last_px DECIMAL
(
    38,
    8
), -- 最新成交价格
    lever DECIMAL
(
    8,
    2
), -- 杠杆倍数
    margin DECIMAL
(
    38,
    8
), -- 保证金
    imr DECIMAL
(
    38,
    8
), -- 初始保证金
    mmr DECIMAL
(
    38,
    8
), -- 维持保证金
    mgn_ratio DECIMAL
(
    38,
    8
), -- 保证金率
    mgn_mode TEXT, -- 保证金模式(isolated/cross)
    upl DECIMAL
(
    38,
    8
), -- 未实现盈亏(按标记价格)
    upl_last_px DECIMAL
(
    38,
    8
), -- 未实现盈亏(按最新价)
    realized_pnl DECIMAL
(
    38,
    8
), -- 已实现盈亏
    notional_usd DECIMAL
(
    38,
    8
), -- 持仓名义价值(USD)
    liq_px DECIMAL
(
    38,
    8
), -- 预估强平价
    fee DECIMAL
(
    38,
    8
), -- 累计手续费
    funding_fee DECIMAL
(
    38,
    8
), -- 累计资金费用
    ccy TEXT, -- 保证金币种
    ctime INTEGER, -- 创建时间(毫秒)
    utime INTEGER, -- 持仓更新时间(毫秒)
    data_ingestion_time INTEGER, -- 数据摄入时间(毫秒)
    update_time TEXT NOT NULL, -- 快照更新时间
    created_time TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_time TEXT DEFAULT CURRENT_TIMESTAMP
    );

-- t_proxy_service_configs
CREATE TABLE IF NOT EXISTS t_proxy_service_configs
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    proxy_name
    VARCHAR
(
    50
) NOT NULL,
    proxy_type VARCHAR
(
    20
) NOT NULL,
    host VARCHAR
(
    100
) NOT NULL,
    port INTEGER NOT NULL,
    auth_type VARCHAR
(
    20
) NOT NULL,
    username VARCHAR
(
    100
),
    password VARCHAR
(
    200
),
    status VARCHAR
(
    20
) NOT NULL DEFAULT 'active',
    last_check_time DATETIME,
    description TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_proxy_type_status
(
    proxy_type,
    status
),
    INDEX idx_host_port
(
    host,
    port
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_risk_mode_history
CREATE TABLE t_risk_mode_history
(
    history_id    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史记录ID',

    -- 风控模式信息
    old_mode      VARCHAR(20) NOT NULL COMMENT '修改前的风控模式(AUTO/MANUAL)',
    new_mode      VARCHAR(20) NOT NULL COMMENT '修改后的风控模式(AUTO/MANUAL)',

    -- 修改操作信息
    change_reason VARCHAR(500) COMMENT '修改原因',
    operator_info VARCHAR(200) COMMENT '操作者信息(IP地址、用户标识等)',

    -- 时间戳
    created_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    INDEX         idx_created_time (created_time),
    INDEX         idx_old_mode (old_mode),
    INDEX         idx_new_mode (new_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控模式修改历史记录表';

-- t_scheduled_tasks
CREATE TABLE IF NOT EXISTS t_scheduled_tasks
(
    task_id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    task_name
    VARCHAR
(
    100
) NOT NULL UNIQUE,
    task_type VARCHAR
(
    20
) NOT NULL,
    cron_expression VARCHAR
(
    50
),
    timeout_seconds INTEGER DEFAULT 300,
    status VARCHAR
(
    20
) DEFAULT 'active',
    parent_task_id BIGINT,
    parameters TEXT,
    description VARCHAR
(
    500
),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_type_status
(
    task_type,
    status
),
    INDEX idx_parent_task
(
    parent_task_id
),
    FOREIGN KEY
(
    parent_task_id
) REFERENCES t_scheduled_tasks
(
    task_id
)
                                                    ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_task_executions
CREATE TABLE IF NOT EXISTS t_task_executions
(
    execution_id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    task_id
    BIGINT
    NOT
    NULL,
    trigger_type
    VARCHAR
(
    20
) NOT NULL,
    parent_task_name VARCHAR
(
    100
) NOT NULL,
    trigger_time DATETIME NOT NULL,
    actual_execute_time DATETIME,
    finish_time DATETIME,
    execution_status VARCHAR
(
    20
) NOT NULL,
    execution_result TEXT,
    error_message TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_execution_lookup
(
    task_id,
    parent_task_name,
    execution_status,
    created_time
),
    INDEX idx_execution_status_created
(
    execution_status,
    created_time
),
    FOREIGN KEY
(
    task_id
) REFERENCES t_scheduled_tasks
(
    task_id
) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- t_trade_decisions
CREATE TABLE `t_trade_decisions`
(
    `id`                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `decision_id`             VARCHAR(50)  NOT NULL UNIQUE COMMENT '决策ID（UUID格式）',
    `api_key_id`              BIGINT       NOT NULL COMMENT 'API密钥ID',
    `call_stats_id`           BIGINT       NOT NULL COMMENT '关联的调用统计ID',
    `model_name`              VARCHAR(100) NOT NULL DEFAULT 'deepseek-r1:14b' COMMENT '使用的模型名称',

    -- 输入数据
    `input_total_equity`      DECIMAL(20, 8) COMMENT '输入的总权益',
    `input_available_balance` DECIMAL(20, 8) COMMENT '输入的可用余额',
    `input_used_margin`       DECIMAL(20, 8) COMMENT '输入的已用保证金',
    `input_unrealized_pnl`    DECIMAL(20, 8) COMMENT '输入的未实现盈亏',
    `input_margin_ratio`      DECIMAL(10, 4) COMMENT '输入的保证金使用率',
    `input_position_details`  TEXT COMMENT '输入的持仓详情JSON',
    `input_call_count`        INT COMMENT '输入的调用次数',
    `input_last_call_time`    DATETIME COMMENT '输入的上次调用时间',

    -- 输出决策
    `decision_action`         VARCHAR(20) COMMENT '决策动作：BUY/SELL/HOLD',
    `decision_price`          DECIMAL(20, 8) COMMENT '建议价格',
    `decision_quantity`       DECIMAL(20, 8) COMMENT '建议数量',
    `decision_take_profit`    DECIMAL(20, 8) COMMENT '建议止盈价格',
    `decision_stop_loss`      DECIMAL(20, 8) COMMENT '建议止损价格',
    `decision_risk_level`     VARCHAR(10) COMMENT '风险等级：LOW/MEDIUM/HIGH',
    `decision_confidence`     DECIMAL(5, 2) COMMENT '决策置信度 0-100',
    `decision_reasoning`      TEXT COMMENT '决策推理过程',
    `full_response`           TEXT COMMENT '完整的模型响应',

    -- 状态和元数据
    `status`                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSED/ERROR',
    `processing_time_ms`      BIGINT COMMENT '处理时间（毫秒）',
    `error_message`           TEXT COMMENT '错误信息（如果有）',
    `executed`                BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否已执行',
    `execution_time`          DATETIME COMMENT '执行时间',
    `execution_result`        TEXT COMMENT '执行结果',

    `created_time`            DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`            DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX                     `idx_decision_id` (`decision_id`),
    INDEX                     `idx_api_key_id` (`api_key_id`),
    INDEX                     `idx_call_stats_id` (`call_stats_id`),
    INDEX                     `idx_model_name` (`model_name`),
    INDEX                     `idx_decision_action` (`decision_action`),
    INDEX                     `idx_status` (`status`),
    INDEX                     `idx_created_time` (`created_time`),
    INDEX                     `idx_executed` (`executed`),
    FOREIGN KEY (`api_key_id`) REFERENCES `t_cex_api_keys` (`key_id`) ON DELETE CASCADE,
    FOREIGN KEY (`call_stats_id`) REFERENCES `t_llm_call_stats` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI交易决策建议表';

-- t_trading_orders
CREATE TABLE IF NOT EXISTS t_trading_orders
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT,
    order_id
    VARCHAR
(
    50
) NOT NULL UNIQUE,
    api_key_id BIGINT,
    vendor VARCHAR
(
    10
) NOT NULL,
    inst_id VARCHAR
(
    50
) NOT NULL,
    side VARCHAR
(
    20
) NOT NULL,
    order_type VARCHAR
(
    20
) NOT NULL,
    td_mode VARCHAR
(
    20
) NOT NULL,
    ccy VARCHAR
(
    10
) NOT NULL,
    lever DECIMAL
(
    8,
    2
) NOT NULL,
    pos_side VARCHAR
(
    20
),
    sz DECIMAL
(
    38,
    8
) NOT NULL,
    px DECIMAL
(
    38,
    8
),
    amt DECIMAL
(
    38,
    8
) NOT NULL,
    order_state VARCHAR
(
    20
) NOT NULL,
    avg_px DECIMAL
(
    38,
    8
),
    filled_sz DECIMAL
(
    38,
    8
) DEFAULT 0,
    filled_amt DECIMAL
(
    38,
    8
) DEFAULT 0,
    fee DECIMAL
(
    38,
    8
) DEFAULT 0,
    fee_ccy VARCHAR
(
    10
),
    source VARCHAR
(
    20
) DEFAULT 'web',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    exec_time DATETIME,
    order_result VARCHAR
(
    50
),
    error_msg TEXT,
    take_profit_price DECIMAL
(
    38,
    8
),
    stop_loss_price DECIMAL
(
    38,
    8
),
    take_profit_pct DECIMAL
(
    8,
    4
),
    stop_loss_pct DECIMAL
(
    8,
    4
),
    INDEX idx_order_id
(
    order_id
),
    INDEX idx_api_key_id
(
    api_key_id
),
    INDEX idx_vendor_inst_id
(
    vendor,
    inst_id
),
    INDEX idx_order_state
(
    order_state
),
    INDEX idx_created_time
(
    created_time
),
    FOREIGN KEY
(
    api_key_id
) REFERENCES t_cex_api_keys
(
    key_id
)
                                                    ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE =utf8mb4_unicode_ci;

-- =======================================================
-- Enable foreign key checks
-- =======================================================

SET FOREIGN_KEY_CHECKS = 1;

-- t_funding_rate_data

CREATE TABLE IF NOT EXISTS t_funding_rate_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cex_name VARCHAR(20) NOT NULL,
    inst_id VARCHAR(50) NOT NULL,
    funding_rate DECIMAL(18, 8),
    funding_rate_timestamp DATETIME,
    next_funding_rate DECIMAL(18, 8),
    next_funding_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cex_inst (cex_name, inst_id),
    INDEX idx_funding_timestamp (funding_rate_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_futures_ticker_data

CREATE TABLE IF NOT EXISTS t_futures_ticker_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cex_name VARCHAR(20) NOT NULL,
    inst_id VARCHAR(50) NOT NULL,
    last_price DECIMAL(38, 8),
    mark_price DECIMAL(38, 8),
    index_price DECIMAL(38, 8),
    volume_24h DECIMAL(38, 8),
    open_interest DECIMAL(38, 8),
    open_interest_value DECIMAL(38, 8),
    funding_rate DECIMAL(18, 8),
    ts_hour_str VARCHAR(20),
    is_live_trading BOOLEAN DEFAULT FALSE,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cex_inst (cex_name, inst_id),
    INDEX idx_ts_hour (ts_hour_str),
    INDEX idx_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_llm_call_records

CREATE TABLE IF NOT EXISTS t_llm_call_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    call_source VARCHAR(20),
    api_key_id BIGINT NOT NULL,
    session_id VARCHAR(100),
    model_name VARCHAR(100),
    call_count INT,
    round_number INT,
    conversation_state VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    prompt TEXT,
    response TEXT,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    estimated_cost DECIMAL(18, 8),
    error_message TEXT,
    prompt_generation_time_ms BIGINT,
    llm_call_time_ms BIGINT,
    post_action_time_ms BIGINT,
    call_start_time DATETIME,
    call_end_time DATETIME,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_api_key_id (api_key_id),
    INDEX idx_session_id (session_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status),
    INDEX idx_call_start_time (call_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_risk_control_config

CREATE TABLE IF NOT EXISTS t_risk_control_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mode VARCHAR(20) NOT NULL,
    mode_description TEXT,
    trading_style VARCHAR(20) NOT NULL,
    trading_style_description TEXT,
    automatic_trade_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    thinking_mode_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mode (mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_risk_control_orders

CREATE TABLE IF NOT EXISTS t_risk_control_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(50) NOT NULL UNIQUE,
    action_id BIGINT,
    estimated_capital DECIMAL(38, 8),
    cex_name VARCHAR(20),
    inst_id VARCHAR(50),
    side VARCHAR(20),
    order_type VARCHAR(20),
    leverage DECIMAL(8, 2),
    amount DECIMAL(38, 8),
    execution_status VARCHAR(20),
    error_message TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_action_id (action_id),
    INDEX idx_execution_status (execution_status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_trade_actions

CREATE TABLE IF NOT EXISTS t_trade_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    action_id VARCHAR(50) NOT NULL UNIQUE,
    llm_call_record_id BIGINT,
    action_type VARCHAR(20) NOT NULL,
    cex_name VARCHAR(20),
    inst_id VARCHAR(50),
    side VARCHAR(20),
    position_side VARCHAR(20),
    order_type VARCHAR(20),
    leverage DECIMAL(8, 2),
    amount DECIMAL(38, 8),
    confidence_score DECIMAL(5, 2),
    reasoning TEXT,
    open_close VARCHAR(20),
    risk_control_status VARCHAR(20),
    execution_status VARCHAR(20),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_llm_call_record (llm_call_record_id),
    INDEX idx_action_type (action_type),
    INDEX idx_inst_id (inst_id),
    INDEX idx_execution_status (execution_status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_trade_balance_snapshots

CREATE TABLE IF NOT EXISTS t_trade_balance_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key_id BIGINT NOT NULL,
    cex_name VARCHAR(20) NOT NULL,
    total_equity DECIMAL(38, 8) NOT NULL,
    available_balance DECIMAL(38, 8) NOT NULL,
    used_balance DECIMAL(38, 8) NOT NULL,
    frozen_balance DECIMAL(38, 8) NOT NULL DEFAULT 0,
    snapshot_time DATETIME NOT NULL,
    is_live_trading BOOLEAN DEFAULT FALSE,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_api_key_snapshot_time (api_key_id, snapshot_time),
    INDEX idx_snapshot_time (snapshot_time),
    INDEX idx_is_live_trading (is_live_trading)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- t_trading_style_history

CREATE TABLE IF NOT EXISTS t_trading_style_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trading_style VARCHAR(20) NOT NULL,
    trading_style_description TEXT,
    changed_reason VARCHAR(100),
    changed_time DATETIME NOT NULL,
    INDEX idx_changed_time (changed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


