package com.crypto.trade.service;

import com.crypto.trade.entity.StrategyConfig;
import com.crypto.trade.repository.StrategyConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * StrategyConfigService
 * 策略配置服务类
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class StrategyConfigService {

    @Autowired
    private StrategyConfigRepository strategyConfigRepository;

    /**
     * 获取所有配置
     */
    public List<StrategyConfig> getAllConfigs() {
        return strategyConfigRepository.findAll();
    }

    /**
     * 获取所有启用的配置
     */
    public List<StrategyConfig> getActiveConfigs() {
        return strategyConfigRepository.findByIsActiveTrue();
    }

    /**
     * 根据ID获取配置
     */
    public StrategyConfig getConfigById(Long id) {
        return strategyConfigRepository.findById(id).orElse(null);
    }

    /**
     * 创建配置
     */
    @Transactional
    public StrategyConfig createConfig(StrategyConfig config) {
        // 检查名称是否重复
        if (strategyConfigRepository.existsByStrategyName(config.getStrategyName())) {
            throw new IllegalArgumentException("策略名称已存在: " + config.getStrategyName());
        }
        return strategyConfigRepository.save(config);
    }

    /**
     * 更新配置
     */
    @Transactional
    public StrategyConfig updateConfig(Long id, StrategyConfig config) {
        StrategyConfig existing = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略配置不存在: " + id));

        // 检查名称是否重复
        if (strategyConfigRepository.existsByStrategyNameAndIdNot(config.getStrategyName(), id)) {
            throw new IllegalArgumentException("策略名称已存在: " + config.getStrategyName());
        }

        existing.setTitle(config.getTitle());
        existing.setStrategyName(config.getStrategyName());
        existing.setVersion(config.getVersion());
        existing.setPrompt(config.getPrompt());
        existing.setStrategyCode(config.getStrategyCode());
        existing.setDescription(config.getDescription());
        existing.setIsActive(config.getIsActive());

        return strategyConfigRepository.save(existing);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void deleteConfig(Long id) {
        strategyConfigRepository.deleteById(id);
    }

    /**
     * 切换启用状态
     */
    @Transactional
    public StrategyConfig toggleStatus(Long id) {
        StrategyConfig config = strategyConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略配置不存在: " + id));
        config.setIsActive(!config.getIsActive());
        return strategyConfigRepository.save(config);
    }
}
