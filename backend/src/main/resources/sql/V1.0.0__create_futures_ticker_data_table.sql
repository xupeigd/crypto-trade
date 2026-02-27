-- 创建期货行情数据表
CREATE TABLE t_futures_ticker_data
(
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    vendor              VARCHAR(10) NOT NULL COMMENT '交易所',
    inst_type           VARCHAR(20) NOT NULL COMMENT '合约类型',
    data_ingestion_time DATETIME    NOT NULL COMMENT '数据摄入时间',
    inst_id             VARCHAR(50) NOT NULL COMMENT '合约ID',
    last                DECIMAL(38, 8) COMMENT '最新价格',
    last_sz             DECIMAL(38, 8) COMMENT '最新数量',
    bid_price           DECIMAL(38, 8) COMMENT '买价',
    bid_sz              DECIMAL(38, 8) COMMENT '买量',
    ask_price           DECIMAL(38, 8) COMMENT '卖价',
    ask_sz              DECIMAL(38, 8) COMMENT '卖量',
    open_24h            DECIMAL(38, 8) COMMENT '24H开盘价',
    high_24h            DECIMAL(38, 8) COMMENT '24H最高价',
    low_24h             DECIMAL(38, 8) COMMENT '24H最低价',
    sod_utc0            DECIMAL(38, 8) COMMENT 'UTC开盘价',
    sod_utc8            DECIMAL(38, 8) COMMENT 'UTC+8开盘价',
    ts                  BIGINT COMMENT '时间戳',
    ts_mins_str         VARCHAR(20) COMMENT '时间字符串',
    vol_ccy_24h         DECIMAL(38, 8) COMMENT '24H币种交易量',
    vol_24h             DECIMAL(38, 8) COMMENT '24H合约交易量',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 复合索引
    INDEX               idx_vendor_inst_type_time (vendor, inst_type, data_ingestion_time),
    INDEX               idx_inst_id_time (inst_id, data_ingestion_time),
    INDEX               idx_ts (ts),
    INDEX               idx_ingestion_time (data_ingestion_time),
    INDEX               idx_vendor_inst_type (vendor, inst_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='期货行情数据表';