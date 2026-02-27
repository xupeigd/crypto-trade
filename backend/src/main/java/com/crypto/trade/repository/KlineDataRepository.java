package com.crypto.trade.repository;

import com.crypto.trade.entity.KlineData;
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
 * KlineDataRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface KlineDataRepository extends JpaRepository<KlineData, Long> {

    /**
     * 根据供应商、合约和时间帧查找最新的K线数据
     */
    List<KlineData> findByProviderAndInstIdAndTimeframeOrderByKlineTimeDesc(String provider, String instId, String timeframe);

    /**
     * 获取指定合约和时间帧的最新K线记录
     */
    Optional<KlineData> findTopByProviderAndInstIdAndTimeframeOrderByKlineTimeDesc(String provider, String instId, String timeframe);

    /**
     * 获取指定时间范围内的K线数据
     */
    @Query("SELECT k FROM KlineData k WHERE k.provider = :provider " +
            "AND k.instId = :instId AND k.timeframe = :timeframe " +
            "AND k.klineTime BETWEEN :startTime AND :endTime " +
            "ORDER BY k.klineTime ASC")
    List<KlineData> findByProviderAndInstIdAndTimeframeAndTimeRange(@Param("provider") String provider,
                                                                    @Param("instId") String instId,
                                                                    @Param("timeframe") String timeframe,
                                                                    @Param("startTime") Long startTime,
                                                                    @Param("endTime") Long endTime);

    /**
     * 获取指定时间范围内的K线数据（升序）
     */
    @Query("SELECT k FROM KlineData k WHERE k.provider = :provider " +
            "AND k.instId = :instId AND k.timeframe = :timeframe " +
            "AND k.klineTime BETWEEN :startTime AND :endTime " +
            "ORDER BY k.klineTime ASC")
    List<KlineData> findByProviderAndInstIdAndTimeframeAndTimeRangeAsc(@Param("provider") String provider,
                                                                       @Param("instId") String instId,
                                                                       @Param("timeframe") String timeframe,
                                                                       @Param("startTime") Long startTime,
                                                                       @Param("endTime") Long endTime);

    /**
     * 获取所有供应商信息
     */
    @Query("SELECT DISTINCT k.provider FROM KlineData k")
    List<String> findAllProviders();

    /**
     * 获取指定供应商的所有合约ID
     */
    @Query("SELECT DISTINCT k.instId FROM KlineData k WHERE k.provider = :provider")
    List<String> findInstIdsByProvider(@Param("provider") String provider);

    /**
     * 获取指定合约的所有时间帧
     */
    @Query("SELECT DISTINCT k.timeframe FROM KlineData k WHERE k.provider = :provider AND k.instId = :instId")
    List<String> findTimeframesByProviderAndInstId(@Param("provider") String provider,
                                                   @Param("instId") String instId);

    /**
     * 统计指定合约和时间帧的数据条数
     */
    @Query("SELECT COUNT(k) FROM KlineData k WHERE k.provider = :provider AND k.instId = :instId AND k.timeframe = :timeframe")
    long countByProviderAndInstIdAndTimeframe(@Param("provider") String provider,
                                              @Param("instId") String instId,
                                              @Param("timeframe") String timeframe);

    /**
     * 删除指定时间之前的数据
     */
    @Modifying
    @Query("DELETE FROM KlineData k WHERE k.provider = :provider AND k.instId = :instId AND k.timeframe = :timeframe AND k.klineTime < :cutoffTime")
    int deleteByProviderAndInstIdAndTimeframeAndKlineTimeBefore(@Param("provider") String provider,
                                                                @Param("instId") String instId,
                                                                @Param("timeframe") String timeframe,
                                                                @Param("cutoffTime") Long cutoffTime);

    /**
     * 删除最早的数据（用于保持最大记录数限制）
     */
    @Modifying
    @Query("DELETE FROM KlineData k WHERE k.id IN " +
            "(SELECT k1.id FROM KlineData k1 " +
            "WHERE k1.provider = :provider AND k1.instId = :instId AND k1.timeframe = :timeframe " +
            "ORDER BY k1.klineTime ASC LIMIT :deleteCount)")
    int deleteOldestByProviderAndInstIdAndTimeframe(@Param("provider") String provider,
                                                    @Param("instId") String instId,
                                                    @Param("timeframe") String timeframe,
                                                    @Param("deleteCount") int deleteCount);

    /**
     * 批量保存或更新（基于唯一索引自动实现覆盖更新）
     */
    @Override
    <S extends KlineData> List<S> saveAll(Iterable<S> entities);

    /**
     * 批量插入指定K线数据（忽略重复，用于提高性能）
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO t_kline_data " +
            "(provider, inst_id, timeframe, kline_time, open_price, high_price, low_price, close_price, volume, quote_volume, quote_asset, base_asset, confirm, created_at, updated_at) " +
            "VALUES (:#{#entity.provider}, :#{#entity.instId}, :#{#entity.timeframe}, :#{#entity.klineTime}, " +
            ":#{#entity.openPrice}, :#{#entity.highPrice}, :#{#entity.lowPrice}, :#{#entity.closePrice}, " +
            ":#{#entity.volume}, :#{#entity.quoteVolume}, :#{#entity.quoteAsset}, :#{#entity.baseAsset}, " +
            ":#{#entity.confirm}, NOW(), NOW())",
            nativeQuery = true)
    int batchInsert(@Param("entity") KlineData entity);

    /**
     * 批量插入或更新K线数据（当数据存在时更新）
     * 使用ON DUPLICATE KEY UPDATE确保原子性操作，避免唯一键冲突
     */
    @Modifying
    @Query(value = "INSERT INTO t_kline_data " +
            "(provider, inst_id, timeframe, kline_time, open_price, high_price, low_price, close_price, volume, quote_volume, quote_asset, base_asset, confirm, created_at, updated_at) " +
            "VALUES (:#{#entity.provider}, :#{#entity.instId}, :#{#entity.timeframe}, :#{#entity.klineTime}, " +
            ":#{#entity.openPrice}, :#{#entity.highPrice}, :#{#entity.lowPrice}, :#{#entity.closePrice}, " +
            ":#{#entity.volume}, :#{#entity.quoteVolume}, :#{#entity.quoteAsset}, :#{#entity.baseAsset}, " +
            ":#{#entity.confirm}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "open_price = VALUES(open_price), high_price = VALUES(high_price), low_price = VALUES(low_price), " +
            "close_price = VALUES(close_price), volume = VALUES(volume), quote_volume = VALUES(quote_volume), " +
            "quote_asset = VALUES(quote_asset), base_asset = VALUES(base_asset), confirm = VALUES(confirm), updated_at = NOW()",
            nativeQuery = true)
    @Transactional
    int batchUpsert(@Param("entity") KlineData entity);

    /**
     * 检查K线数据是否已存在
     */
    boolean existsByProviderAndInstIdAndTimeframeAndKlineTime(String provider, String instId, String timeframe, Long klineTime);

    /**
     * 获取指定数据的更新时间
     */
    @Query("SELECT k.updatedAt FROM KlineData k WHERE k.provider = :provider AND k.instId = :instId AND k.timeframe = :timeframe AND k.klineTime = :klineTime")
    Optional<LocalDateTime> findUpdatedAtByProviderAndInstIdAndTimeframeAndKlineTime(@Param("provider") String provider,
                                                                                     @Param("instId") String instId,
                                                                                     @Param("timeframe") String timeframe,
                                                                                     @Param("klineTime") Long klineTime);
}