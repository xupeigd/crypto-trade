package com.crypto.trade.service;

import com.crypto.trade.entity.AgentConfig;
import com.crypto.trade.entity.AgentRelation;
import com.crypto.trade.enums.AgentRelationType;
import com.crypto.trade.repository.AgentRelationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * MultiAgentService
 * 多智能体协作服务类
 *
 * @author page
 * @date 2026-03-22
 */
@Slf4j
@Service
public class MultiAgentService {

    @Autowired
    private AgentRelationRepository agentRelationRepository;

    @Autowired
    private AgentConfigService agentConfigService;

    @Autowired
    private SkillConfigService skillConfigService;

    @Autowired
    private UnifiedModelFactory unifiedModelFactory;

    /**
     * 获取指定Agent的子Agent列表
     */
    public List<AgentRelation> getSubAgents(Long agentId) {
        return agentRelationRepository.findByAgentIdAndIsActiveTrueOrderByPriorityAsc(agentId);
    }

    /**
     * 判断是否需要多Agent处理
     */
    public boolean needsMultiAgent(Long agentId) {
        List<AgentRelation> subAgents = getSubAgents(agentId);
        return subAgents != null && !subAgents.isEmpty();
    }

    /**
     * 多Agent协作处理
     *
     * @param agentId     主Agent ID
     * @param userMessage 用户消息
     * @return 处理结果
     */
    public String multiAgentChat(Long agentId, String userMessage) {
        // 获取主Agent配置
        AgentConfig mainAgent = agentConfigService.getAgentConfigById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent不存在: " + agentId));

        // 获取子Agent列表
        List<AgentRelation> subAgents = getSubAgents(agentId);
        if (subAgents.isEmpty()) {
            // 没有子Agent，降级为单Agent处理
            log.info("Agent {} 没有配置子Agent，降级为单Agent处理", agentId);
            return null;
        }

        // 根据关系类型选择处理策略
        AgentRelationType relationType = subAgents.get(0).getRelationType();

        return switch (relationType) {
            case MASTER_SLAVE -> handleMasterSlave(mainAgent, subAgents, userMessage);
            case COLLABORATIVE -> handleCollaborative(mainAgent, subAgents, userMessage);
            case HIERARCHICAL -> handleHierarchical(mainAgent, subAgents, userMessage);
        };
    }

    /**
     * 主从模式：主Agent分解任务，并行调用子Agent，汇总结果
     */
    private String handleMasterSlave(AgentConfig mainAgent, List<AgentRelation> subAgents, String userMessage) {
        log.info("执行主从模式，Agent数量: {}", subAgents.size() + 1);

        // 构建主Agent的系统提示
        String systemPrompt = buildSystemPrompt(mainAgent);

        // 分解任务给子Agent
        List<CompletableFuture<String>> futures = subAgents.parallelStream()
                .map(relation -> CompletableFuture.supplyAsync(() -> {
                    AgentConfig subAgent = agentConfigService.getAgentConfigById(relation.getSubAgentId()).orElse(null);
                    if (subAgent == null) {
                        log.warn("子Agent不存在: {}", relation.getSubAgentId());
                        return "";
                    }
                    return callSubAgent(subAgent, relation.getDelegationPrompt(), userMessage);
                }))
                .collect(Collectors.toList());

        // 等待所有子Agent完成
        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(result -> !result.isEmpty())
                .collect(Collectors.toList());

        // 汇总结果
        return aggregateResults(results);
    }

    /**
     * 协作模式：多个Agent轮流处理，共享上下文
     */
    private String handleCollaborative(AgentConfig mainAgent, List<AgentRelation> subAgents, String userMessage) {
        log.info("执行协作模式，Agent数量: {}", subAgents.size() + 1);

        StringBuilder context = new StringBuilder("用户请求: ").append(userMessage).append("\n\n");
        String currentMessage = userMessage;

        // 轮流调用每个Agent
        for (AgentRelation relation : subAgents) {
            AgentConfig subAgent = agentConfigService.getAgentConfigById(relation.getSubAgentId()).orElse(null);
            if (subAgent == null) {
                continue;
            }

            String prompt = context.toString() + "\n\n请处理以上任务。";
            String result = callSubAgent(subAgent, relation.getDelegationPrompt(), prompt);

            // 将结果加入上下文
            context.append("Agent ").append(subAgent.getName()).append(" 的结果:\n").append(result).append("\n\n");
            currentMessage = result;
        }

        return currentMessage;
    }

    /**
     * 层级模式：顺序调用，形成调用链
     */
    private String handleHierarchical(AgentConfig mainAgent, List<AgentRelation> subAgents, String userMessage) {
        log.info("执行层级模式，Agent数量: {}", subAgents.size() + 1);

        String currentMessage = userMessage;

        // 按优先级顺序调用
        for (AgentRelation relation : subAgents) {
            AgentConfig subAgent = agentConfigService.getAgentConfigById(relation.getSubAgentId()).orElse(null);
            if (subAgent == null) {
                continue;
            }

            // 将上一个Agent的结果作为输入
            currentMessage = callSubAgent(subAgent, relation.getDelegationPrompt(), currentMessage);
        }

        return currentMessage;
    }

    /**
     * 调用子Agent
     */
    private String callSubAgent(AgentConfig subAgent, String delegationPrompt, String userMessage) {
        try {
            // 构建子Agent的系统提示
            String systemPrompt = buildSystemPrompt(subAgent);
            if (delegationPrompt != null && !delegationPrompt.isEmpty()) {
                systemPrompt = delegationPrompt + "\n\n" + systemPrompt;
            }

            log.info("调用子Agent: {}, 消息: {}", subAgent.getName(), userMessage);

            // 构建消息列表
            List<UnifiedModelFactory.Message> messages = new ArrayList<>();
            messages.add(UnifiedModelFactory.Message.system(systemPrompt));
            messages.add(UnifiedModelFactory.Message.user(userMessage));

            // 获取默认模型配置
            var modelConfig = unifiedModelFactory.getDefaultModelConfig();
            if (modelConfig == null) {
                throw new IllegalStateException("未配置模型");
            }

            // 调用模型获取回复
            String response = unifiedModelFactory.callWithMessages(messages, modelConfig.getModelId());
            log.info("子Agent {} 返回结果: {}", subAgent.getName(), response);

            return response;
        } catch (Exception e) {
            log.error("调用子Agent失败: {}", subAgent.getName(), e);
            return "处理失败: " + e.getMessage();
        }
    }

    /**
     * 构建系统提示
     */
    private String buildSystemPrompt(AgentConfig agentConfig) {
        StringBuilder sb = new StringBuilder();

        if (agentConfig.getSystemPrompt() != null && !agentConfig.getSystemPrompt().isEmpty()) {
            sb.append(agentConfig.getSystemPrompt());
        }

        // 注入Skill Prompt
        if (agentConfig.getSkills() != null && !agentConfig.getSkills().isEmpty()) {
            List<Long> skillIds = skillConfigService.parseSkillIds(agentConfig.getSkills());
            if (!skillIds.isEmpty()) {
                List<com.crypto.trade.entity.SkillConfig> skills = skillConfigService.getSkillConfigsByIds(skillIds);
                for (com.crypto.trade.entity.SkillConfig skill : skills) {
                    if (skill.getIsActive() && skill.getSkillPrompt() != null) {
                        sb.append("\n\n---\n## 技能: ").append(skill.getName()).append("\n");
                        sb.append(skillConfigService.buildFullPrompt(skill));
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * 聚合多个Agent的结果
     */
    public String aggregateResults(List<String> results) {
        if (results == null || results.isEmpty()) {
            return "无结果";
        }

        if (results.size() == 1) {
            return results.get(0);
        }

        // 简单的结果拼接，实际可根据需求优化
        StringBuilder sb = new StringBuilder("综合分析结果:\n\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append("--- 结果 ").append(i + 1).append(" ---\n");
            sb.append(results.get(i)).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 创建Agent关系
     */
    public AgentRelation createRelation(AgentRelation relation) {
        // 检查是否已存在
        if (agentRelationRepository.existsByAgentIdAndSubAgentId(
                relation.getAgentId(), relation.getSubAgentId())) {
            throw new IllegalArgumentException("关系已存在");
        }
        return agentRelationRepository.save(relation);
    }

    /**
     * 删除Agent关系
     */
    public void deleteRelation(Long id) {
        agentRelationRepository.deleteById(id);
    }
}
