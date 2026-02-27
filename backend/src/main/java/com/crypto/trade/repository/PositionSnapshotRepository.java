package com.crypto.trade.repository;

import com.crypto.trade.entity.PositionSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * PositionSnapshotRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface PositionSnapshotRepository
        extends JpaRepository<PositionSnapshot, Long> {

    /**
     * 根据API密钥ID、合约代码、持仓方向、持仓ID等查找唯一快照(旧方法,已废弃)
     *
     * @deprecated 使用 findByApiKeyIdAndInstIdAndPosSideAndPosId 代替
     */
    Optional<PositionSnapshot> findByApiKeyIdAndInstIdAndPosSideAndPosIdAndTypeAndCtimeAndUtime(
            Long apiKeyId, String instId, String posSide, String posId, String type, Long ctime, Long utime);

    /**
     * 根据API密钥ID查找快照,按utime降序排列
     */
    List<PositionSnapshot> findByApiKeyIdOrderByUtimeDesc(Long apiKeyId);

    /**
     * 根据API密钥ID和合约类型查找快照,按utime降序排列
     */
    List<PositionSnapshot> findByApiKeyIdAndInstTypeOrderByUtimeDesc(Long apiKeyId, String instType);

    /**
     * 根据API密钥ID和合约类型及时间范围查找快照,按utime降序排列
     * 在数据库层过滤时间范围,提升查询性能
     *
     * @param apiKeyId   API密钥ID
     * @param instType   合约类型
     * @param beforeTime 结束时间戳(毫秒),为null时不限制
     * @return 仓位快照列表
     */
    List<PositionSnapshot> findByApiKeyIdAndInstTypeAndUtimeBeforeOrderByUtimeDesc(Long apiKeyId, String instType,
                                                                                   Long beforeTime);

    /**
     * 根据API密钥ID及时间范围查找快照,按utime降序排列
     * 在数据库层过滤时间范围,提升查询性能
     *
     * @param apiKeyId   API密钥ID
     * @param beforeTime 结束时间戳(毫秒),为null时不限制
     * @return 仓位快照列表
     */
    List<PositionSnapshot> findByApiKeyIdAndUtimeBeforeOrderByUtimeDesc(Long apiKeyId, Long beforeTime);

    /**
     * 删除指定时间之前更新的快照数据(用于自动清理过期数据)
     * 修改: 基于updatedTime而非createdTime,避免删除仍在更新的活跃仓位
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PositionSnapshot p WHERE p.updatedTime < :before")
    int deleteOldSnapshots(@Param("before") LocalDateTime before);

    Page<PositionSnapshot> findOneByApiKeyIdAndInstType(Long apiKeyId, String instType, Pageable pageable);

}
