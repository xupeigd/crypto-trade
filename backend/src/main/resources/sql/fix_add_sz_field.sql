-- 为t_trading_orders表添加sz字段,记录下单数量
-- 创建时间: 2026-01-08
-- 说明: sz字段用于记录下单时的委托数量(合约张数),与amt字段(金额)区分开

ALTER TABLE t_trading_orders
    ADD COLUMN sz DECIMAL(38, 8) COMMENT '委托数量(合约张数)';
