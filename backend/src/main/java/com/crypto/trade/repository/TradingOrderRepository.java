package com.crypto.trade.repository;

import com.crypto.trade.entity.TradingOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TradingOrderRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface TradingOrderRepository
        extends JpaRepository<TradingOrder, Long> {

    /**
     * 根据系统订单UUID查找订单
     *
     * @param orderUuid 系统订单UUID
     * @return 订单记录
     */
    Optional<TradingOrder> findByOrderUuid(String orderUuid);

    /**
     * 根据CEX订单ID查找订单
     *
     * @param cexOrderId CEX订单ID
     * @return 订单记录
     */
    Optional<TradingOrder> findByCexOrderId(String cexOrderId);

    /**
     * 根据API Key ID查找订单
     *
     * @param apiKeyId API Key ID
     * @return 订单列表
     */
    List<TradingOrder> findByApiKeyId(Long apiKeyId);

    /**
     * 根据API Key ID和订单状态查找订单
     *
     * @param apiKeyId    API Key ID
     * @param orderStatus 系统订单状态
     * @return 订单列表
     */
    List<TradingOrder> findByApiKeyIdAndOrderStatus(Long apiKeyId, String orderStatus);

    /**
     * 根据API Key ID查找活跃订单
     * <p>
     * 活跃订单定义：状态为 pending/submitted/canceling
     * </p>
     *
     * @param apiKeyId API Key ID
     * @return 活跃订单列表
     */
    @Query("SELECT o FROM TradingOrder o WHERE o.apiKeyId = :apiKeyId " +
            "AND o.orderStatus IN ('pending', 'submitted', 'canceling') " +
            "ORDER BY o.createdTime DESC")
    List<TradingOrder> findActiveOrdersByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 根据多个API Key ID查找活跃订单
     *
     * @param apiKeyIds API Key ID列表
     * @return 活跃订单列表
     */
    @Query("SELECT o FROM TradingOrder o WHERE (o.apiKeyId IN :apiKeyIds OR o.apiKeyId IS NULL) " +
            "AND o.orderStatus IN ('pending', 'submitted', 'canceling') " +
            "ORDER BY o.createdTime DESC")
    List<TradingOrder> findActiveOrdersByApiKeys(@Param("apiKeyIds") List<Long> apiKeyIds);

    /**
     * 根据API Key ID查找历史订单
     * <p>
     * 历史订单定义：状态为 success/failed/canceled
     * 放宽条件：当completedTime为空时，回退到createdTime进行时间过滤
     * 使用JOIN FETCH立即加载关联的CexOrder实体
     * </p>
     *
     * @param apiKeyId API Key ID
     * @param since    起始时间
     * @return 历史订单列表
     */
    @Query("SELECT o FROM TradingOrder o " +
            "LEFT JOIN FETCH o.cexOrder " +
            "WHERE o.apiKeyId = :apiKeyId " +
            "AND o.orderStatus IN ('success', 'failed', 'canceled') " +
            "AND (o.completedTime >= :since OR (o.completedTime IS NULL AND o.createdTime >= :since)) " +
            "ORDER BY o.createdTime DESC")
    List<TradingOrder> findHistoryOrdersByApiKeyId(@Param("apiKeyId") Long apiKeyId, @Param("since") LocalDateTime since);

    /**
     * 查找指定API Key的历史订单(分页)
     *
     * @param apiKeyId API Key ID
     * @param since    起始时间
     * @param pageable 分页参数
     * @return 历史订单分页结果
     */
    @Query("SELECT o FROM TradingOrder o " +
            "LEFT JOIN FETCH o.cexOrder " +
            "WHERE o.apiKeyId = :apiKeyId " +
// 性能优化：不使用orderStatus过滤，保持接口语义完整
// 如果需要只查询已完成订单，可以单独添加新的查询方法
//            "AND o.orderStatus IN ('success', 'failed', 'canceled') " +
            "AND (o.completedTime >= :since OR (o.completedTime IS NULL AND o.createdTime >= :since)) " +
            "ORDER BY o.createdTime DESC")
    Page<TradingOrder> findHistoryOrdersByApiKeyIdPage(@Param("apiKeyId") Long apiKeyId, @Param("since") LocalDateTime since,
                                                       Pageable pageable);

    /**
     * 根据多个API Key ID查找历史订单
     *
     * @param apiKeyIds API Key ID列表
     * @param since     起始时间
     * @return 历史订单列表
     */
    @Query("SELECT o FROM TradingOrder o WHERE (o.apiKeyId IN :apiKeyIds OR o.apiKeyId IS NULL) " +
            "AND o.orderStatus IN ('success', 'failed', 'canceled') " +
            "AND o.completedTime >= :since " +
            "ORDER BY o.completedTime DESC")
    List<TradingOrder> findHistoryOrdersByApiKeys(@Param("apiKeyIds") List<Long> apiKeyIds, @Param("since") LocalDateTime since);

    /**
     * 根据合约品种和API Key ID查找订单
     *
     * @param instId   合约品种
     * @param apiKeyId API Key ID
     * @return 订单列表
     */
    List<TradingOrder> findByInstIdAndApiKeyIdOrderByCreatedTimeDesc(String instId, Long apiKeyId);

    /**
     * 查找指定时间范围内的订单
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 订单列表
     */
    @Query("SELECT o FROM TradingOrder o WHERE o.createdTime BETWEEN :startTime AND :endTime " +
            "ORDER BY o.createdTime DESC")
    List<TradingOrder> findByCreatedTimeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定API Key的订单数量
     *
     * @param apiKeyId API Key ID
     * @return 订单数量
     */
    @Query("SELECT COUNT(o) FROM TradingOrder o WHERE o.apiKeyId = :apiKeyId")
    Long countByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 统计指定API Key的指定状态订单数量
     *
     * @param apiKeyId    API Key ID
     * @param orderStatus 订单状态
     * @return 订单数量
     */
    @Query("SELECT COUNT(o) FROM TradingOrder o WHERE o.apiKeyId = :apiKeyId AND o.orderStatus = :orderStatus")
    Long countByApiKeyIdAndOrderStatus(@Param("apiKeyId") Long apiKeyId, @Param("orderStatus") String orderStatus);

    /**
     * 查找最近的订单
     *
     * @param apiKeyId API Key ID
     * @return 订单列表
     */
    @Query("SELECT o FROM TradingOrder o WHERE o.apiKeyId = :apiKeyId ORDER BY o.createdTime DESC")
    List<TradingOrder> findRecentOrdersByApiKeyId(@Param("apiKeyId") Long apiKeyId);

    /**
     * 根据订单状态查找订单
     *
     * @param orderStatus 订单状态
     * @return 订单列表
     */
    List<TradingOrder> findByOrderStatusOrderByCreatedTimeDesc(String orderStatus);

    /**
     * 删除指定时间之前的订单记录
     *
     * @param before 时间阈值
     * @return 删除数量
     */
    @Query("DELETE FROM TradingOrder o WHERE o.createdTime < :before")
    int deleteOldOrders(@Param("before") LocalDateTime before);

    /**
     * 检查系统订单UUID是否存在
     *
     * @param orderUuid 系统订单UUID
     * @return 是否存在
     */
    boolean existsByOrderUuid(String orderUuid);

    /**
     * 根据机器人ID查找订单
     *
     * @param botId 机器人ID
     * @return 订单列表
     */
    List<TradingOrder> findByBotId(Long botId);

    /**
     * 根据策略ID查找订单
     *
     * @param strategyId 策略ID
     * @return 订单列表
     */
    List<TradingOrder> findByStrategyId(Long strategyId);

    /**
     * 根据调用记录ID查找订单
     *
     * @param recordId 调用记录ID
     * @return 订单列表
     */
    List<TradingOrder> findByRecordIdOrderByCreatedTimeDesc(Long recordId);

    /**
     * 根据调用记录ID统计订单数量
     *
     * @param recordId 调用记录ID
     * @return 订单数量
     */
    Long countByRecordId(Long recordId);

    /**
     * 检查订单是否已存在(增强去重)
     *
     * @param apiKeyId   API密钥ID
     * @param cexOrderId CEX订单ID
     * @param instId     合约品种
     * @return 是否存在
     */
    boolean existsByApiKeyIdAndCexOrderIdAndInstId(Long apiKeyId, String cexOrderId, String instId);
}
