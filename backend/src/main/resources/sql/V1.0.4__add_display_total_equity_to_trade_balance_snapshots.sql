ALTER TABLE t_trade_balance_snapshots
    ADD COLUMN display_total_equity_usdt DECIMAL(38, 8) NULL AFTER total_equity_usdt;

UPDATE t_trade_balance_snapshots
SET display_total_equity_usdt = total_equity_usdt
WHERE display_total_equity_usdt IS NULL;
