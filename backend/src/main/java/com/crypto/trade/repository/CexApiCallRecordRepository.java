package com.crypto.trade.repository;

import com.crypto.trade.entity.CexApiCallRecord;
import com.crypto.trade.enums.CexApiCallStatus;
import com.crypto.trade.enums.CexApiType;
import com.crypto.trade.enums.CexExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CexApiCallRecordRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface CexApiCallRecordRepository
        extends JpaRepository<CexApiCallRecord, Long> {

    /**
     * 根据API类型查询调用记录
     *
     * @param apiType API类型
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByApiType(CexApiType apiType);

    /**
     * 根据交易所查询调用记录
     *
     * @param exchange 交易所
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByExchange(CexExchange exchange);

    /**
     * 根据合约代码查询调用记录
     *
     * @param instId 合约代码
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByInstId(String instId);

    /**
     * 根据订单ID查询调用记录
     *
     * @param orderId 订单ID
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByOrderId(String orderId);

    /**
     * 根据状态查询调用记录
     *
     * @param status 调用状态
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByStatus(CexApiCallStatus status);

    /**
     * 根据API类型和状态查询调用记录
     *
     * @param apiType API类型
     * @param status  调用状态
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByApiTypeAndStatus(CexApiType apiType, CexApiCallStatus status);

    /**
     * 根据交易所和状态查询调用记录
     *
     * @param exchange 交易所
     * @param status   调用状态
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByExchangeAndStatus(CexExchange exchange, CexApiCallStatus status);

    /**
     * 查询最近N条调用记录
     *
     * @param limit 限制数量
     * @return 调用记录列表
     */
    @Query("SELECT r FROM CexApiCallRecord r ORDER BY r.callTime DESC")
    List<CexApiCallRecord> findRecentCalls(@Param("limit") int limit);

    /**
     * 根据时间范围查询调用记录
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByCallTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据API类型和时间范围查询调用记录
     *
     * @param apiType   API类型
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByApiTypeAndCallTimeBetween(
            CexApiType apiType,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 根据交易所和时间范围查询调用记录
     *
     * @param exchange  交易所
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByExchangeAndCallTimeBetween(
            CexExchange exchange,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * 查询指定时间之后仍处于调用中状态的记录
     * <p>
     * 用于找出可能超时或卡住的调用
     * </p>
     *
     * @param beforeTime 时间阈值
     * @return 调用记录列表
     */
    List<CexApiCallRecord> findByStatusAndCallTimeBefore(
            CexApiCallStatus status,
            LocalDateTime beforeTime
    );

    /**
     * 统计指定API类型的调用次数
     *
     * @param apiType API类型
     * @return 调用次数
     */
    long countByApiType(CexApiType apiType);

    /**
     * 统计指定交易所的调用次数
     *
     * @param exchange 交易所
     * @return 调用次数
     */
    long countByExchange(CexExchange exchange);

    /**
     * 统计指定状态的调用次数
     *
     * @param status 调用状态
     * @return 调用次数
     */
    long countByStatus(CexApiCallStatus status);

    /**
     * 批量更新超时的调用记录
     * <p>
     * 将指定时间前仍处于pending状态的记录标记为超时
     * </p>
     *
     * @param timeoutTime 超时时间阈值
     * @return 更新的记录数
     */
    @Modifying
    @Query("UPDATE CexApiCallRecord r SET r.status = 'timeout', r.updateTime = :updateTime " +
            "WHERE r.status = 'pending' AND r.callTime < :timeoutTime")
    int markTimeoutRecords(
            @Param("timeoutTime") LocalDateTime timeoutTime,
            @Param("updateTime") LocalDateTime updateTime
    );

    /**
     * 删除指定时间之前的记录
     * <p>
     * 用于定期清理历史数据
     * </p>
     *
     * @param beforeTime 时间阈值
     */
    @Modifying
    @Query("DELETE FROM CexApiCallRecord r WHERE r.createTime < :beforeTime")
    void deleteByCreateTimeBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
