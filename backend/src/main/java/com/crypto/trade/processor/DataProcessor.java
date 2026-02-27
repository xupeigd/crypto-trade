package com.crypto.trade.processor;

import com.crypto.trade.entity.DataFetchConfig;

/**
 * DataProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface DataProcessor {

    /**
     * 处理API响应数据
     *
     * @param rawData         API返回的原始数据
     * @param targetTable     目标DuckDB表名
     * @param dataFetchConfig
     * @return 处理结果信息
     */
    String process(String rawData, String targetTable, DataFetchConfig dataFetchConfig);
}