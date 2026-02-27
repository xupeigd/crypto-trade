package com.crypto.trade.repository;

import com.crypto.trade.entity.AuditStatus;
import com.crypto.trade.entity.RiskControlOrder;
import com.crypto.trade.entity.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RiskControlOrderRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface RiskControlOrderRepository extends JpaRepository<RiskControlOrder, Long> {

    /**
     * 根据原始订单ID查找
     *
     * @param originalOrderId 原始订单ID
     * @return 风控订单信息
     */
    Optional<RiskControlOrder> findByOriginalOrderId(String originalOrderId);

    /**
     * 根据审核状态查找，按创建时间倒序
     *
     * @param auditStatus 审核状态
     * @return 风控订单列表
     */
    List<RiskControlOrder> findByAuditStatusOrderByCreateTimeDesc(AuditStatus auditStatus);

    /**
     * 根据API Key ID和审核状态查找，按创建时间倒序
     *
     * @param apiKeyId    API Key ID
     * @param auditStatus 审核状态
     * @return 风控订单列表
     */
    List<RiskControlOrder> findByApiKeyIdAndAuditStatusOrderByCreateTimeDesc(
            Long apiKeyId, AuditStatus auditStatus);

    /**
     * 根据风控等级查找，按创建时间倒序
     *
     * @param riskLevel 风控等级
     * @return 风控订单列表
     */
    List<RiskControlOrder> findByRiskLevelOrderByCreateTimeDesc(RiskLevel riskLevel);

    /**
     * 统计各状态的订单数量
     *
     * @return 状态和数量的映射
     */
    @Query("SELECT r.auditStatus, COUNT(r) FROM RiskControlOrder r GROUP BY r.auditStatus")
    List<Object[]> countByAuditStatus();

    /**
     * 统计各风控等级的订单数量
     *
     * @return 风控等级和数量的映射
     */
    @Query("SELECT r.riskLevel, COUNT(r) FROM RiskControlOrder r GROUP BY r.riskLevel")
    List<Object[]> countByRiskLevel();

    /**
     * 查找最近创建的风控订单
     *
     * @param limit 限制数量
     * @return 风控订单列表
     */
    @Query(value = "SELECT * FROM t_risk_control_orders ORDER BY create_time DESC LIMIT ?1", nativeQuery = true)
    List<RiskControlOrder> findRecentOrders(int limit);

    /**
     * 根据审核人查找订单
     *
     * @param auditor 审核人
     * @return 风控订单列表
     */
    List<RiskControlOrder> findByAuditorOrderByCreateTimeDesc(String auditor);

    /**
     * 根据审核状态统计订单数量
     *
     * @param auditStatus 审核状态
     * @return 订单数量
     */
    long countByAuditStatus(AuditStatus auditStatus);

    /**
     * 【新增】根据actionId查找风控订单
     *
     * @param actionId 关联的TradeAction ID
     * @return 风控订单信息
     */
    Optional<RiskControlOrder> findByActionId(Long actionId);

    /**
     * 【新增】根据多个actionId批量查找风控订单
     *
     * @param actionIds 关联的TradeAction ID列表
     * @return 风控订单列表
     */
    List<RiskControlOrder> findByActionIdIn(List<Long> actionIds);

}