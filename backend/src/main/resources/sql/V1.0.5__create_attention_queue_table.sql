-- 创建ATTENTION队列表
-- 用于记录ATTENTION触发的模型调用信息
CREATE TABLE t_attention_queue
(
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    record_id              BIGINT NOT NULL COMMENT '触发的LlmCallRecord的ID',
    api_key_id             BIGINT NOT NULL COMMENT 'API密钥ID',
    inst_id                VARCHAR(50) COMMENT '合约代码（如BTC-USDT-SWAP）',
    priority               INT COMMENT '优先级',
    timeframe              VARCHAR(20) COMMENT '时间周期 (1m, 5m, 1H, etc.)',
    query_limit            INT COMMENT '查询条数限制',
    expected_trigger_time  DATETIME NOT NULL COMMENT '预期触发时间',
    actual_trigger_time    DATETIME COMMENT '实际触发时间',
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/EXECUTED/EXPIRED',
    remark                 VARCHAR(500) COMMENT '备注',
    create_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX idx_record_id (record_id),
    INDEX idx_status (status),
    INDEX idx_expected_trigger_time (expected_trigger_time),
    INDEX idx_api_key_id (api_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ATTENTION队列表';
