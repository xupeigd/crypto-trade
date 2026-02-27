package com.crypto.trade.repository;

import com.crypto.trade.entity.FuturesTickerData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FuturesTickerDataRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface FuturesTickerDataRepository
        extends JpaRepository<FuturesTickerData, Long> {

    /**
     * 获取指定合约的最新价格数据
     */
    @Query("SELECT f FROM FuturesTickerData f WHERE f.instId = :instId " +
            "ORDER BY f.dataIngestionTime DESC LIMIT 1")
    Optional<FuturesTickerData> findLatestByInstId(@Param("instId") String instId);

    /**
     * 获取指定合约的最新价格数据（SWAP类型）
     */
    @Query("SELECT f FROM FuturesTickerData f WHERE f.instId = :instId AND f.instType = 'SWAP' " +
            "ORDER BY f.dataIngestionTime DESC LIMIT 1")
    Optional<FuturesTickerData> findLatestSwapByInstId(@Param("instId") String instId);

    /**
     * 获取Top30永续合约数据（按交易额排序）
     */
    @Query("SELECT f FROM FuturesTickerData f " +
            "WHERE f.instType = 'SWAP' " +
            "AND f.vendor = :vendor " +
            "AND f.isLiveTrading = :isLiveTrading " +
            "AND f.instId LIKE '%-USDT-%' " +
            "AND f.last > 0 " +
            "AND f.volCcy24h > 0 " +
            "AND f.dataIngestionTime >= :since " +
            "ORDER BY (f.last * f.volCcy24h) DESC")
    List<FuturesTickerData> findTopSwapContractsOrderByVolume(@Param("vendor") String vendor,
                                                              @Param("isLiveTrading") Boolean isLiveTrading,
                                                              @Param("since") LocalDateTime since);


    /**
     * 批量插入或更新（基于vendor, instId, tsHourStr唯一索引）
     * 注意：这里使用saveAll配合唯一索引来实现upsert效果
     */
    @Override
    <S extends FuturesTickerData> List<S> saveAll(Iterable<S> entities);

    /**
     * 获取指定供应商和交易模式的最新小时数据
     */
    @Query("SELECT f FROM FuturesTickerData f " +
            "WHERE f.vendor = :vendor " +
            "AND f.isLiveTrading = :isLiveTrading " +
            "AND f.tsHourStr = :hourStr " +
            "ORDER BY f.dataIngestionTime DESC")
    List<FuturesTickerData> findByVendorAndIsLiveTradingAndTsHourStr(
            @Param("vendor") String vendor,
            @Param("isLiveTrading") Boolean isLiveTrading,
            @Param("hourStr") String hourStr);

    /**
     * 获取指定供应商和合约类型的Top30数据（按24小时USDT交易额排序）
     */
    @Query("SELECT f FROM FuturesTickerData f " +
            "WHERE f.vendor = :vendor " +
            "AND f.instType = :instType " +
            "AND f.instId LIKE '%-USDT-%' " +
            "AND f.last > 0 " +
            "AND f.volCcy24h > 0 " +
            "AND f.tsHourStr = :hourStr " +
            "ORDER BY (f.last * f.volCcy24h) DESC")
    List<FuturesTickerData> findTop30ByVendorAndInstTypeAndHourStr(@Param("vendor") String vendor,
                                                                   @Param("instType") String instType,
                                                                   @Param("hourStr") String hourStr);


    /**
     * 获取TopN永续合约（按24小时USDT交易额排序）
     * 按供应商和交易模式区分，避免数据混合
     */
    @Query("SELECT f FROM FuturesTickerData f " +
            "WHERE f.vendor = :vendor " +
            "AND f.isLiveTrading = :isLiveTrading " +
            "AND f.instType = 'SWAP' " +
            "AND f.instId LIKE '%-USDT-%' " +
            "AND f.last > 0 " +
            "AND f.volCcy24h > 0 " +
            "AND f.tsHourStr = :hourStr " +
            "ORDER BY (f.last * f.volCcy24h) DESC")
    List<FuturesTickerData> findTopNSwapByVolume24h(@Param("vendor") String vendor, @Param("isLiveTrading") Boolean isLiveTrading,
                                                    @Param("hourStr") String hourStr, Pageable pageable);

    /**
     * 获取TopN永续合约（按24小时涨跌幅排序）
     * 按供应商和交易模式区分，避免数据混合
     */
    @Query("SELECT f FROM FuturesTickerData f " +
            "WHERE f.vendor = :vendor " +
            "AND f.isLiveTrading = :isLiveTrading " +
            "AND f.instType = 'SWAP' " +
            "AND f.instId LIKE '%-USDT-%' " +
            "AND f.last > 0 " +
            "AND f.open24h > 0 " +
            "AND f.tsHourStr = :hourStr " +
            "ORDER BY ((f.last - f.open24h) / f.open24h) DESC")
    List<FuturesTickerData> findTopNSwapByChange24h(@Param("vendor") String vendor, @Param("isLiveTrading") Boolean isLiveTrading,
                                                    @Param("hourStr") String hourStr, Pageable pageable);

    /**
     * 获取指定供应商和交易模式的最新小时字符串
     * <p>
     * 用于启动时精细化检查，确保每个vendor的每种交易模式都有当前小时的数据。
     * </p>
     *
     * @param vendor        供应商名称（如"OKX"）
     * @param isLiveTrading 交易模式（false=模拟，true=实盘）
     * @return 最新小时字符串，无数据时返回空Optional
     */
    @Query("SELECT f.tsHourStr FROM FuturesTickerData f " +
            "WHERE f.vendor = :vendor " +
            "AND f.isLiveTrading = :isLiveTrading " +
            "AND f.tsHourStr IS NOT NULL " +
            "ORDER BY f.tsHourStr DESC " +
            "LIMIT 1")
    Optional<String> findLatestTsHourStrByVendorAndIsLiveTrading(@Param("vendor") String vendor, @Param("isLiveTrading") Boolean isLiveTrading);
}