package com.crypto.trade.service;

import com.crypto.trade.entity.FreqtradeConfig;
import com.crypto.trade.repository.FreqtradeConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FreqtradeConfigService
 * Freqtrade配置服务类
 *
 * @author page
 * @date 2026-03-22
 */
@Slf4j
@Service
public class FreqtradeConfigService {

    @Autowired
    private FreqtradeConfigRepository freqtradeConfigRepository;

    /**
     * 获取所有配置
     */
    public List<FreqtradeConfig> getAllConfigs() {
        return freqtradeConfigRepository.findAll();
    }

    /**
     * 获取所有启用的配置
     */
    public List<FreqtradeConfig> getActiveConfigs() {
        return freqtradeConfigRepository.findByIsActiveTrue();
    }

    /**
     * 根据ID获取配置
     */
    public FreqtradeConfig getConfigById(Long id) {
        return freqtradeConfigRepository.findById(id).orElse(null);
    }

    /**
     * 创建配置
     */
    @Transactional
    public FreqtradeConfig createConfig(FreqtradeConfig config) {
        // 检查名称是否重复
        if (freqtradeConfigRepository.existsByConfigName(config.getConfigName())) {
            throw new IllegalArgumentException("配置名称已存在: " + config.getConfigName());
        }
        return freqtradeConfigRepository.save(config);
    }

    /**
     * 更新配置
     */
    @Transactional
    public FreqtradeConfig updateConfig(Long id, FreqtradeConfig config) {
        FreqtradeConfig existing = freqtradeConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在: " + id));

        // 检查名称是否重复
        if (freqtradeConfigRepository.existsByConfigNameAndIdNot(config.getConfigName(), id)) {
            throw new IllegalArgumentException("配置名称已存在: " + config.getConfigName());
        }

        existing.setConfigName(config.getConfigName());
        existing.setStartupMode(config.getStartupMode());
        existing.setProcessPath(config.getProcessPath());
        existing.setDockerImage(config.getDockerImage());
        existing.setUserDataDir(config.getUserDataDir());
        existing.setApiHost(config.getApiHost());
        existing.setPortRangeMin(config.getPortRangeMin());
        existing.setPortRangeMax(config.getPortRangeMax());
        existing.setIsActive(config.getIsActive());
        existing.setDescription(config.getDescription());

        return freqtradeConfigRepository.save(existing);
    }

    /**
     * 删除配置
     */
    @Transactional
    public void deleteConfig(Long id) {
        freqtradeConfigRepository.deleteById(id);
    }

    /**
     * 切换启用状态
     */
    @Transactional
    public FreqtradeConfig toggleStatus(Long id) {
        FreqtradeConfig config = freqtradeConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在: " + id));
        config.setIsActive(!config.getIsActive());
        return freqtradeConfigRepository.save(config);
    }
}
