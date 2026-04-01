package com.crypto.trade.repository;

import com.crypto.trade.entity.SkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SkillConfigRepository
 * 技能配置数据访问层
 *
 * @author page
 * @date 2026-03-16
 */
@Repository
public interface SkillConfigRepository
        extends JpaRepository<SkillConfig, Long> {

    Optional<SkillConfig> findByName(String name);

    List<SkillConfig> findByIsActiveTrue();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

}
