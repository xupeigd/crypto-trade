package com.crypto.trade.repository;

import com.crypto.trade.entity.CexTradingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CexTradingOrderRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface CexTradingOrderRepository extends JpaRepository<CexTradingOrder, Long> {

    /**
     * 根据CEX订单ID查询
     *
     * @param orderId CEX订单ID
     * @return 订单记录
     */
    Optional<CexTradingOrder> findByOrderId(String orderId);

    /**
     * 根据交易所和API Key查询订单
     *
     * @param apiKeyId API Key ID
     * @param exchange 交易所
     * @return 订单列表
     */
    List<CexTradingOrder> findByApiKeyIdAndExchange(Long apiKeyId, String exchange);

    /**
     * 根据合约代码查询订单
     *
     * @param instId 合约代码
     * @return 订单列表
     */
    List<CexTradingOrder> findByInstId(String instId);

    /**
     * 根据订单状态查询订单
     *
     * @param orderState 订单状态
     * @return 订单列表
     */
    List<CexTradingOrder> findByOrderState(String orderState);

    /**
     * 根据多个订单状态查询订单
     *
     * @param states 订单状态列表
     * @return 订单列表
     */
    List<CexTradingOrder> findByOrderStateIn(List<String> states);

    /**
     * 根据同步状态查询订单
     *
     * @param syncStatus 同步状态
     * @return 订单列表
     */
    List<CexTradingOrder> findBySyncStatus(String syncStatus);

    /**
     * 查询需要同步的订单
     * <p>
     * 条件：订单状态为活跃（live或partially_filled）且同步状态为待同步或同步失败
     * 状态流转：pending(待同步) → syncing(同步中) → completed(已完成)/failed(失败)
     * </p>
     *
     * @return 需要同步的订单列表
     */
    @Query("SELECT o FROM CexTradingOrder o WHERE o.orderState IN ('live', 'partially_filled') AND (o.syncStatus = 'pending' OR o.syncStatus = 'failed')")
    List<CexTradingOrder> findOrdersNeedSync();

    /**
     * 查询同步超时的订单
     * <p>
     * 条件：最后同步时间早于指定时间
     * </p>
     *
     * @param time 时间阈值
     * @return 同步超时的订单列表
     */
    List<CexTradingOrder> findByLastSyncTimeBefore(LocalDateTime time);

    /**
     * 查询同步超时的活跃订单
     *
     * @param time 时间阈值
     * @return 同步超时的订单列表
     */
    @Query("SELECT o FROM CexTradingOrder o WHERE o.orderState IN ('live', 'partially_filled') AND o.lastSyncTime < :time")
    List<CexTradingOrder> findActiveOrdersWithStaleSync(@Param("time") LocalDateTime time);

    /**
     * 根据API Key和合约代码查询最新订单
     *
     * @param apiKeyId API Key ID
     * @param instId   合约代码
     * @return 订单列表
     */
    @Query("SELECT o FROM CexTradingOrder o WHERE o.apiKeyId = :apiKeyId AND o.instId = :instId ORDER BY o.createdTime DESC")
    List<CexTradingOrder> findRecentOrdersByApiKeyAndInstId(
            @Param("apiKeyId") Long apiKeyId,
            @Param("instId") String instId
    );

    /**
     * 统计指定交易所的订单数量
     *
     * @param exchange 交易所
     * @return 订单数量
     */
    long countByExchange(String exchange);

    /**
     * 统计指定状态的订单数量
     *
     * @param orderState 订单状态
     * @return 订单数量
     */
    long countByOrderState(String orderState);

    /**
     * 统计指定API Key的订单数量
     *
     * @param apiKeyId API Key ID
     * @return 订单数量
     */
    long countByApiKeyId(Long apiKeyId);

    /**
     * 批量更新订单同步状态
     *
     * @param syncStatus   新的同步状态
     * @param lastSyncTime 最后同步时间
     * @param orderIds     订单ID列表
     * @return 更新的记录数
     */
    @Modifying
    @Query("UPDATE CexTradingOrder o SET o.syncStatus = :syncStatus, o.lastSyncTime = :lastSyncTime WHERE o.id IN :orderIds")
    int batchUpdateSyncStatus(
            @Param("syncStatus") String syncStatus,
            @Param("lastSyncTime") LocalDateTime lastSyncTime,
            @Param("orderIds") List<Long> orderIds
    );

    /**
     * 删除指定时间之前的订单
     * <p>
     * 用于定期清理历史数据
     * </p>
     *
     * @param beforeTime 时间阈值
     */
    @Modifying
    @Query("DELETE FROM CexTradingOrder o WHERE o.createdTime < :beforeTime AND o.orderState IN ('filled', 'canceled', 'failed')")
    void deleteCompletedOrdersBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
