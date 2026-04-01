package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.AgentRelation;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.MultiAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AgentRelationController
 * 智能体关系REST控制器
 *
 * @author page
 * @date 2026-03-22
 */
@Slf4j
@RestController
@RequestMapping("/agent-relations")
public class AgentRelationController {

    @Autowired
    private MultiAgentService multiAgentService;

    /**
     * 获取指定Agent的子Agent列表
     */
    @GetMapping("/{agentId}")
    public ApiResponse<List<AgentRelation>> getSubAgents(@PathVariable Long agentId) {
        try {
            log.debug("获取Agent {} 的子Agent列表", agentId);
            List<AgentRelation> subAgents = multiAgentService.getSubAgents(agentId);
            return ApiResponse.ok(subAgents);
        } catch (Exception e) {
            log.error("获取子Agent列表失败: {}", e.getMessage(), e);
            return ApiResponse.fail("获取子Agent列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建Agent关系
     */
    @PostMapping
    public ApiResponse<AgentRelation> createRelation(@RequestBody AgentRelation relation) {
        try {
            log.debug("创建Agent关系: {} -> {}", relation.getAgentId(), relation.getSubAgentId());
            AgentRelation created = multiAgentService.createRelation(relation);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建Agent关系失败: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建Agent关系失败: {}", e.getMessage(), e);
            return ApiResponse.fail("创建Agent关系失败: " + e.getMessage());
        }
    }

    /**
     * 删除Agent关系
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        try {
            log.debug("删除Agent关系: {}", id);
            multiAgentService.deleteRelation(id);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("删除Agent关系失败: {}", e.getMessage(), e);
            return ApiResponse.fail("删除Agent关系失败: " + e.getMessage());
        }
    }

    /**
     * 判断是否需要多Agent处理
     */
    @GetMapping("/needs-multi/{agentId}")
    public ApiResponse<Boolean> needsMultiAgent(@PathVariable Long agentId) {
        try {
            boolean needs = multiAgentService.needsMultiAgent(agentId);
            return ApiResponse.ok(needs);
        } catch (Exception e) {
            log.error("检查多Agent失败: {}", e.getMessage(), e);
            return ApiResponse.fail("检查多Agent失败: " + e.getMessage());
        }
    }
}
