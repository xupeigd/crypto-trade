package com.crypto.trade.service;

import com.crypto.trade.entity.AgentConfig;
import com.crypto.trade.entity.ExecutionMode;
import com.crypto.trade.entity.RiskControlConfig;
import com.crypto.trade.repository.AgentConfigRepository;
import com.crypto.trade.repository.RiskControlConfigRepository;
import com.crypto.trade.service.conversation.TradingConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ExecutionModeResolver
 * 执行模式解析器
 * 优先级: 智能体 > AI交易 > 全局配置
 *
 * @author page
 * @date 2026-03-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionModeResolver {

    private final AgentConfigRepository agentConfigRepository;
    private final RiskControlConfigRepository riskControlConfigRepository;
    private final TradingConfigProperties tradingConfigProperties;

    /**
     * 解析执行模式
     * 优先级: Agent > AI Trading (RiskControlConfig) > Global (TradingConfigProperties)
     *
     * @param agentId       智能体ID
     * @param riskControlId 风控配置ID（AI交易配置ID）
     * @return 执行模式
     */
    public ExecutionMode resolve(Long agentId, Long riskControlId) {
        // 1. 智能体级别（最高优先级）
        if (agentId != null) {
            AgentConfig agent = agentConfigRepository.findById(agentId).orElse(null);
            if (agent != null && agent.getExecutionMode() != null) {
                log.debug("使用智能体级别执行模式: agentId={}, mode={}", agentId, agent.getExecutionMode());
                return agent.getExecutionMode();
            }
        }

        // 2. AI交易级别（RiskControlConfig）
        if (riskControlId != null) {
            RiskControlConfig config = riskControlConfigRepository.findById(riskControlId).orElse(null);
            if (config != null && config.getExecutionMode() != null) {
                log.debug("使用AI交易级别执行模式: riskControlId={}, mode={}", riskControlId, config.getExecutionMode());
                return config.getExecutionMode();
            }
        }

        // 3. 全局配置（TradingConfigProperties）
        ExecutionMode globalMode = tradingConfigProperties.getExecutionMode();
        if (globalMode != null) {
            log.debug("使用全局配置执行模式: mode={}", globalMode);
            return globalMode;
        }

        // 4. 默认实盘模式
        log.debug("未配置执行模式，使用默认实盘模式");
        return ExecutionMode.LIVE;
    }

    /**
     * 仅根据智能体ID解析执行模式
     */
    public ExecutionMode resolveByAgentId(Long agentId) {
        return resolve(agentId, null);
    }

    /**
     * 仅根据风控配置ID解析执行模式
     */
    public ExecutionMode resolveByRiskControlId(Long riskControlId) {
        return resolve(null, riskControlId);
    }

    /**
     * 根据API Key ID解析执行模式
     * 由于API Key不直接关联执行模式，这里检查AI交易级别的配置
     * 如果AI交易配置中有设置执行模式，则使用该配置；否则使用全局配置
     *
     * @param apiKeyId API Key ID
     * @return 执行模式
     */
    public ExecutionMode resolveByApiKeyId(Long apiKeyId) {
        // 检查AI交易级别配置（RiskControlConfig表，固定ID=1）
        RiskControlConfig config = riskControlConfigRepository.findById(1L).orElse(null);
        if (config != null && config.getExecutionMode() != null) {
            log.debug("使用AI交易级别执行模式（通过apiKeyId查询）: apiKeyId={}, mode={}", apiKeyId, config.getExecutionMode());
            return config.getExecutionMode();
        }

        // 降级使用全局配置
        return getGlobalExecutionMode();
    }

    /**
     * 获取全局执行模式
     */
    public ExecutionMode getGlobalExecutionMode() {
        return resolve(null, null);
    }

    /**
     * 判断是否为模拟模式
     */
    public boolean isDryRun(Long agentId, Long riskControlId) {
        return ExecutionMode.DRY_RUN.equals(resolve(agentId, riskControlId));
    }

    /**
     * 判断是否为实盘模式
     */
    public boolean isLive(Long agentId, Long riskControlId) {
        return ExecutionMode.LIVE.equals(resolve(agentId, riskControlId));
    }

    /**
     * 检查系统是否在任何地方启用了模拟模式
     * 只要全局、风控(AI交易)或任何启用的智能体配置了DRY_RUN，就返回true
     *
     * @return 是否有任何配置启用了模拟模式
     */
    public boolean isAnyDryRunEnabled() {
        // 1. 检查全局配置
        if (ExecutionMode.DRY_RUN.equals(tradingConfigProperties.getExecutionMode())) {
            return true;
        }

        // 2. 检查风控配置 (RiskControlConfig)
        RiskControlConfig config = riskControlConfigRepository.findById(1L).orElse(null);
        if (config != null && ExecutionMode.DRY_RUN.equals(config.getExecutionMode())) {
            return true;
        }

        // 3. 检查是否有任何智能体启用了模拟模式
        return agentConfigRepository.existsByExecutionMode(ExecutionMode.DRY_RUN);
    }
}
