package com.crypto.trade.repository;

import com.crypto.trade.entity.CexInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CexInstrumentRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface CexInstrumentRepository
        extends JpaRepository<CexInstrument, Long> {

    /**
     * 根据服务商和合约ID查找(不区分交易模式)
     */
    Optional<CexInstrument> findByProviderAndInstId(String provider, String instId);

    /**
     * 根据服务商、合约ID和交易模式查找
     */
    Optional<CexInstrument> findByProviderAndInstIdAndIsLiveTrading(String provider, String instId, Boolean isLiveTrading);

    /**
     * 获取指定服务商和合约ID的最新活跃合约信息
     * 优先返回state='live'的合约，按数据获取时间降序排列(不区分交易模式)
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider AND c.instId = :instId ORDER BY CASE WHEN c.state = 'live' THEN 1 ELSE 2 END, c.dataIngestionTime DESC")
    Optional<CexInstrument> findLatestActiveByProviderAndInstId(@Param("provider") String provider,
                                                                @Param("instId") String instId);

    /**
     * 获取指定服务商、合约ID和交易模式的最新活跃合约信息
     * 优先返回state='live'的合约，按数据获取时间降序排列
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider AND c.instId = :instId AND c.isLiveTrading = :isLiveTrading ORDER BY CASE WHEN c.state = 'live' THEN 1 ELSE 2 END, c.dataIngestionTime DESC")
    Optional<CexInstrument> findLatestActiveByProviderAndInstIdAndIsLiveTrading(@Param("provider") String provider,
                                                                                @Param("instId") String instId,
                                                                                @Param("isLiveTrading") Boolean isLiveTrading);

    /**
     * 根据服务商和交易模式查找
     */
    List<CexInstrument> findByProviderAndIsLiveTrading(String provider, Boolean isLiveTrading);

    /**
     * 根据服务商、交易模式和状态查找
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider AND c.isLiveTrading = :isLiveTrading AND c.state = :state ORDER BY c.instId")
    List<CexInstrument> findByProviderAndIsLiveTradingAndState(@Param("provider") String provider,
                                                               @Param("isLiveTrading") Boolean isLiveTrading,
                                                               @Param("state") String state);

    /**
     * 根据服务商统计数量(按交易模式)
     */
    long countByProviderAndIsLiveTrading(String provider, Boolean isLiveTrading);

    /**
     * 检查指定服务商是否有指定时间后的数据
     */
    @Query("SELECT COUNT(c) > 0 FROM CexInstrument c WHERE c.provider = :provider AND c.dataIngestionTime > :sinceTime")
    boolean existsByProviderAndDataIngestionTimeAfter(@Param("provider") String provider,
                                                      @Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 检查是否有6小时内的数据（任意服务商）
     */
    @Query("SELECT COUNT(c) > 0 FROM CexInstrument c WHERE c.dataIngestionTime > :sinceTime")
    boolean existsAnyDataIngestedAfter(@Param("sinceTime") LocalDateTime sinceTime);

    /**
     * 获取指定服务商最新的合约信息
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider ORDER BY c.dataIngestionTime DESC")
    List<CexInstrument> findLatestByProvider(@Param("provider") String provider);

    /**
     * 获取指定服务商和合约类型的最新合约信息
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider AND c.instType = :instType ORDER BY c.dataIngestionTime DESC")
    List<CexInstrument> findLatestByProviderAndInstType(@Param("provider") String provider,
                                                        @Param("instType") String instType);

    /**
     * 获取所有服务商信息
     */
    @Query("SELECT DISTINCT c.provider FROM CexInstrument c")
    List<String> findAllProviders();

    /**
     * 删除指定服务商在指定时间之前的数据
     */
    @Modifying
    @Query("DELETE FROM CexInstrument c WHERE c.provider = :provider AND c.dataIngestionTime < :cutoffTime")
    void deleteByProviderAndDataIngestionTimeBefore(@Param("provider") String provider,
                                                    @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 删除指定服务商、交易模式在指定时间之前的数据
     */
    @Modifying
    @Query("DELETE FROM CexInstrument c WHERE c.provider = :provider AND c.isLiveTrading = :isLiveTrading AND c.dataIngestionTime < :cutoffTime")
    int deleteByProviderAndIsLiveTradingAndDataIngestionTimeBefore(@Param("provider") String provider,
                                                                   @Param("isLiveTrading") Boolean isLiveTrading,
                                                                   @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 根据服务商和合约状态查找
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider AND c.state = :state ORDER BY c.instId")
    List<CexInstrument> findByProviderAndState(@Param("provider") String provider,
                                               @Param("state") String state);

    /**
     * 批量保存或更新（基于唯一索引 provider + instId）
     */
    @Override
    <S extends CexInstrument> List<S> saveAll(Iterable<S> entities);

    /**
     * 获取指定时间范围内的数据
     */
    @Query("SELECT c FROM CexInstrument c WHERE c.provider = :provider AND c.dataIngestionTime BETWEEN :startTime AND :endTime ORDER BY c.dataIngestionTime DESC")
    List<CexInstrument> findByProviderAndTimeRange(@Param("provider") String provider,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 根据服务商统计数量
     */
    long countByProvider(String provider);

    /**
     * 批量插入或更新合约信息（使用MySQL ON DUPLICATE KEY UPDATE语法）
     * 这个方法可以避免重复键错误，实现覆盖更新功能
     * 唯一索引: provider + inst_id + is_live_trading
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO t_cex_instruments " +
            "(provider, is_live_trading, inst_type, inst_id, base_ccy, quote_ccy, settle_ccy, category, " +
            "ct_val, ct_mult, ct_val_ccy, opt_type, stk, list_time, exp_time, lever, " +
            "tick_sz, lot_sz, min_sz, max_lmt_sz, max_mkt_sz, max_ts_sz, state, alias, " +
            "max_lmt, max_mkt, position_idx, is_leverage, fee_rate, data_ingestion_time) " +
            "VALUES " +
            "(:#{#instrument.provider}, :#{#instrument.isLiveTrading}, :#{#instrument.instType}, :#{#instrument.instId}, :#{#instrument.baseCcy}, :#{#instrument.quoteCcy}, :#{#instrument.settleCcy}, :#{#instrument.category}, " +
            ":#{#instrument.ctVal}, :#{#instrument.ctMult}, :#{#instrument.ctValCcy}, :#{#instrument.optType}, :#{#instrument.stk}, :#{#instrument.listTime}, :#{#instrument.expTime}, :#{#instrument.lever}, " +
            ":#{#instrument.tickSz}, :#{#instrument.lotSz}, :#{#instrument.minSz}, :#{#instrument.maxLmtSz}, :#{#instrument.maxMktSz}, :#{#instrument.maxTsSz}, :#{#instrument.state}, :#{#instrument.alias}, " +
            ":#{#instrument.maxLmt}, :#{#instrument.maxMkt}, :#{#instrument.positionIdx}, :#{#instrument.isLeverage}, :#{#instrument.feeRate}, :#{#instrument.dataIngestionTime}) " +
            "ON DUPLICATE KEY UPDATE " +
            "inst_type = VALUES(inst_type), base_ccy = VALUES(base_ccy), quote_ccy = VALUES(quote_ccy), settle_ccy = VALUES(settle_ccy), category = VALUES(category), " +
            "ct_val = VALUES(ct_val), ct_mult = VALUES(ct_mult), ct_val_ccy = VALUES(ct_val_ccy), opt_type = VALUES(opt_type), stk = VALUES(stk), " +
            "list_time = VALUES(list_time), exp_time = VALUES(exp_time), lever = VALUES(lever), tick_sz = VALUES(tick_sz), lot_sz = VALUES(lot_sz), " +
            "min_sz = VALUES(min_sz), max_lmt_sz = VALUES(max_lmt_sz), max_mkt_sz = VALUES(max_mkt_sz), max_ts_sz = VALUES(max_ts_sz), " +
            "state = VALUES(state), alias = VALUES(alias), max_lmt = VALUES(max_lmt), max_mkt = VALUES(max_mkt), " +
            "position_idx = VALUES(position_idx), is_leverage = VALUES(is_leverage), fee_rate = VALUES(fee_rate), data_ingestion_time = VALUES(data_ingestion_time)",
            nativeQuery = true)
    void upsertInstrument(@Param("instrument") CexInstrument instrument);

    /**
     * 检查指定服务商、交易模式是否有指定时间后的数据
     * <p>
     * 用于启动时精细化检查，确保每个CEX的每种交易模式都有最新数据。
     * </p>
     *
     * @param provider      服务商名称（如"OKX"）
     * @param isLiveTrading 交易模式（false=模拟，true=实盘）
     * @param sinceTime     检查的起始时间
     * @return true表示有数据
     */
    @Query("SELECT COUNT(c) > 0 FROM CexInstrument c " +
            "WHERE c.provider = :provider " +
            "AND c.isLiveTrading = :isLiveTrading " +
            "AND c.dataIngestionTime > :sinceTime")
    boolean existsByProviderAndIsLiveTradingAndDataIngestionTimeAfter(
            @Param("provider") String provider,
            @Param("isLiveTrading") Boolean isLiveTrading,
            @Param("sinceTime") LocalDateTime sinceTime);
}