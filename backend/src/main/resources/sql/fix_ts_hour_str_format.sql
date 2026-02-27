-- 修复 t_futures_ticker_data 表中 ts_hour_str 格式错误的问题
-- 问题：ts_hour_str 包含分钟（如 "2025-12-25 13:12"），导致唯一索引冲突
-- 解决：修正为小时格式（"2025-12-25 13"），并删除重复数据

USE
crypto_trade;

-- 1. 查看错误格式数据的数量
SELECT COUNT(*) as error_count
FROM t_futures_ticker_data
WHERE ts_hour_str LIKE '%:%';

-- 2. 查看错误数据示例（前10条）
SELECT vendor, inst_id, ts_hour_str, data_ingestion_time
FROM t_futures_ticker_data
WHERE ts_hour_str LIKE '%:%'
ORDER BY data_ingestion_time DESC LIMIT 10;

-- 3. 修正错误格式（将 "2025-12-25 13:12" 转换为 "2025-12-25 13"）
UPDATE t_futures_ticker_data
SET ts_hour_str = SUBSTRING_INDEX(ts_hour_str, ':', 2)
WHERE ts_hour_str LIKE '%:%';

-- 4. 验证修正后的数据格式
SELECT vendor, inst_id, ts_hour_str, data_ingestion_time
FROM t_futures_ticker_data
WHERE ts_hour_str LIKE '%:%' LIMIT 10;

-- 5. 删除重复数据，保留最新的一条（基于 data_ingestion_time）
DELETE
t1 FROM t_futures_ticker_data t1
INNER JOIN t_futures_ticker_data t2
WHERE t1.vendor = t2.vendor
  AND t1.inst_id = t2.inst_id
  AND t1.ts_hour_str = t2.ts_hour_str
  AND t1.data_ingestion_time < t2.data_ingestion_time;

-- 6. 验证删除后的数据量
SELECT vendor,
       ts_hour_str,
       COUNT(*) as count
FROM t_futures_ticker_data
GROUP BY vendor, ts_hour_str
HAVING COUNT (*) > 1;

-- 7. 查看总数据量
SELECT COUNT(*) as total_count
FROM t_futures_ticker_data;

-- 8. 查看最新的几条数据
SELECT vendor, inst_id, ts_hour_str, data_ingestion_time
FROM t_futures_ticker_data
ORDER BY data_ingestion_time DESC LIMIT 10;
