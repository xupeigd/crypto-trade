package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.proxy.AssociatedTaskResponse;
import com.crypto.trade.dto.proxy.ProxyServiceConfigResponse;
import com.crypto.trade.dto.proxy.TaskCountResponse;
import com.crypto.trade.dto.proxy.TestConnectionResponse;
import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.CreateProxyServiceConfigReq;
import com.crypto.trade.model.request.UpdateProxyServiceConfigReq;
import com.crypto.trade.service.ProxyServiceConfigService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ProxyServiceConfigController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/proxy-service-configs")
public class ProxyServiceConfigController {

    @Autowired
    ProxyServiceConfigService proxyServiceConfigService;

    /**
     * 获取所有代理配置
     *
     * @return ApiResponse<List < ProxyServiceConfigResponse>> 代理配置列表的统一响应格式
     */
    @GetMapping
    public ApiResponse<List<ProxyServiceConfigResponse>> getAllConfigs() {
        try {
            log.debug("=== getAllConfigs 方法开始执行 ===");
            List<ProxyServiceConfig> configs = proxyServiceConfigService.getAllConfigs();
            log.debug("从数据库获取到 {} 个代理配置", configs.size());

            List<ProxyServiceConfigResponse> configResponses = configs.stream()
                    .map(this::convertToProxyServiceConfigResponse)
                    .collect(Collectors.toList());
            log.debug("转换为DTO后得到 {} 个配置响应", configResponses.size());
            ApiResponse<List<ProxyServiceConfigResponse>> response = ApiResponse.ok(configResponses);
            log.debug("创建的ApiResponse - code: {}, success: {}, data size: {}",
                    response.getCode(), response.getSuccess(),
                    response.getData() != null ? response.getData().size() : 0);
            log.debug("获取所有代理配置成功，数量: {}", configResponses.size());
            return response;
        } catch (Exception e) {
            log.error("获取所有代理配置失败", e);
            return ApiResponse.fail("获取代理配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取代理配置
     *
     * @param id 代理配置ID
     * @return ApiResponse<ProxyServiceConfigResponse> 代理配置详情的统一响应格式
     */
    @GetMapping("/{id}")
    public ApiResponse<ProxyServiceConfigResponse> getConfigById(@PathVariable Long id) {
        try {
            ProxyServiceConfig config = proxyServiceConfigService.getProxyConfigById(id);
            if (null == config) {
                log.warn("按ID查询代理配置失败，配置不存在: {}", id);
                return ApiResponse.fail("代理配置不存在");
            }
            ProxyServiceConfigResponse response = convertToProxyServiceConfigResponse(config);
            log.debug("按ID查询代理配置成功，代理配置ID: {}", id);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("按ID查询代理配置失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("查询代理配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有活跃的代理配置
     *
     * @return ApiResponse<List < ProxyServiceConfigResponse>> 活跃代理配置列表的统一响应格式
     */
    @GetMapping("/active")
    public ApiResponse<List<ProxyServiceConfigResponse>> getActiveConfigs() {
        try {
            List<ProxyServiceConfig> configs = proxyServiceConfigService.getActiveConfigs();
            List<ProxyServiceConfigResponse> configResponses = configs.stream()
                    .map(this::convertToProxyServiceConfigResponse)
                    .collect(Collectors.toList());
            log.debug("获取活跃代理配置成功，数量: {}", configResponses.size());
            return ApiResponse.ok(configResponses);
        } catch (Exception e) {
            log.error("获取活跃代理配置失败", e);
            return ApiResponse.fail("获取活跃代理配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建代理配置
     *
     * @param request 创建代理配置请求
     * @return ApiResponse<ProxyServiceConfigResponse> 创建代理配置的统一响应格式
     */
    @PostMapping
    public ApiResponse<ProxyServiceConfigResponse> createConfig(@Valid @RequestBody CreateProxyServiceConfigReq request) {
        try {
            // 转换请求为Entity
            ProxyServiceConfig config = convertToProxyServiceConfig(request);
            ProxyServiceConfig createdConfig = proxyServiceConfigService.createProxyConfig(config);
            ProxyServiceConfigResponse response = convertToProxyServiceConfigResponse(createdConfig);
            log.debug("创建代理配置成功，代理配置ID: {}, 代理名称: {}", response.getProxyId(), response.getProxyName());
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("创建代理配置失败: {}", e.getMessage());
            return ApiResponse.fail("创建代理配置失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建代理配置失败", e);
            return ApiResponse.fail("创建代理配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新代理配置
     *
     * @param id      代理配置ID
     * @param request 更新代理配置请求
     * @return ApiResponse<ProxyServiceConfigResponse> 更新代理配置的统一响应格式
     */
    @PutMapping("/{id}")
    public ApiResponse<ProxyServiceConfigResponse> updateConfig(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateProxyServiceConfigReq request) {
        try {
            // 转换请求为Entity
            ProxyServiceConfig config = convertToProxyServiceConfig(request);
            ProxyServiceConfig updatedConfig = proxyServiceConfigService.updateProxyConfig(id, config);
            ProxyServiceConfigResponse response = convertToProxyServiceConfigResponse(updatedConfig);
            log.debug("更新代理配置成功，代理配置ID: {}", id);
            return ApiResponse.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("更新代理配置失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("更新代理配置失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新代理配置失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("更新代理配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除代理配置
     *
     * @param id 代理配置ID
     * @return ApiResponse<Void> 删除结果的统一响应格式
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        try {
            proxyServiceConfigService.deleteProxyConfig(id);
            log.debug("删除代理配置成功，代理配置ID: {}", id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            log.warn("删除代理配置失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("删除代理配置失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("删除代理配置失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("删除代理配置失败: " + e.getMessage());
        }
    }

    /**
     * 测试代理连接
     *
     * @param id 代理配置ID
     * @return ApiResponse<TestConnectionResponse> 测试结果的统一响应格式
     */
    @PostMapping("/{id}/test")
    public ApiResponse<TestConnectionResponse> testConnection(@PathVariable Long id) {
        try {
            boolean success = proxyServiceConfigService.testProxyConnection(id);
            TestConnectionResponse response = TestConnectionResponse.builder()
                    .success(success)
                    .message(success ? "代理连接测试成功" : "代理连接测试失败")
                    .build();
            log.debug("测试代理连接完成，代理配置ID: {}, 结果: {}", id, success);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("测试代理连接失败，代理配置ID: {}", id, e);
            TestConnectionResponse response = TestConnectionResponse.builder()
                    .success(false)
                    .message("测试过程中发生异常: " + e.getMessage())
                    .build();
            return ApiResponse.ok(response);
        }
    }

    /**
     * 获取代理配置关联的任务数量
     *
     * @param id 代理配置ID
     * @return ApiResponse<TaskCountResponse> 任务数量的统一响应格式
     */
    @GetMapping("/{id}/task-count")
    public ApiResponse<TaskCountResponse> getAssociatedTaskCount(@PathVariable Long id) {
        try {
            Long count = proxyServiceConfigService.getAssociatedTaskCount(id);
            TaskCountResponse response = TaskCountResponse.builder()
                    .count(count)
                    .build();
            log.debug("获取代理配置关联任务数量成功，代理配置ID: {}, 数量: {}", id, count);
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("获取代理配置关联任务数量失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("获取关联任务数量失败: " + e.getMessage());
        }
    }

    /**
     * 获取代理配置关联的任务列表
     *
     * @param id 代理配置ID
     * @return ApiResponse<List < AssociatedTaskResponse>> 关联任务列表的统一响应格式
     */
    @GetMapping("/{id}/tasks")
    public ApiResponse<List<AssociatedTaskResponse>> getAssociatedTasks(@PathVariable Long id) {
        try {
            List<ScheduledTask> tasks = proxyServiceConfigService.getAssociatedTasks(id);
            List<AssociatedTaskResponse> taskResponses = tasks.stream()
                    .map(this::convertToAssociatedTaskResponse)
                    .collect(Collectors.toList());
            log.debug("获取代理配置关联任务列表成功，代理配置ID: {}, 数量: {}", id, taskResponses.size());
            return ApiResponse.ok(taskResponses);
        } catch (Exception e) {
            log.error("获取代理配置关联任务列表失败，代理配置ID: {}", id, e);
            return ApiResponse.fail("获取关联任务列表失败: " + e.getMessage());
        }
    }

    // ==================== 私有转换方法 ====================

    /**
     * 将Entity转换为ProxyServiceConfigResponse
     */
    private ProxyServiceConfigResponse convertToProxyServiceConfigResponse(ProxyServiceConfig config) {
        return ProxyServiceConfigResponse.builder()
                .proxyId(config.getProxyId())
                .proxyName(config.getProxyName())
                .proxyType(config.getProxyType())
                .serverHost(config.getServerHost())
                .serverPort(config.getServerPort())
                .status(config.getStatus())
                .description(config.getDescription())
                .createdTime(config.getCreatedTime())
                .updatedTime(config.getUpdatedTime())
                .build();
    }

    /**
     * 将CreateProxyServiceConfigRequest转换为ProxyServiceConfig Entity
     */
    private ProxyServiceConfig convertToProxyServiceConfig(CreateProxyServiceConfigReq request) {
        ProxyServiceConfig config = new ProxyServiceConfig();
        config.setProxyName(request.getProxyName());
        config.setProxyType(request.getProxyType());
        config.setServerHost(request.getServerHost());
        config.setServerPort(request.getServerPort());
        config.setStatus(request.getStatus() != null ? request.getStatus() : "active");
        config.setDescription(request.getDescription());
        return config;
    }

    /**
     * 将UpdateProxyServiceConfigRequest转换为ProxyServiceConfig Entity
     */
    private ProxyServiceConfig convertToProxyServiceConfig(UpdateProxyServiceConfigReq request) {
        ProxyServiceConfig config = new ProxyServiceConfig();
        config.setProxyName(request.getProxyName());
        config.setProxyType(request.getProxyType());
        config.setServerHost(request.getServerHost());
        config.setServerPort(request.getServerPort());
        config.setStatus(request.getStatus());
        config.setDescription(request.getDescription());
        return config;
    }

    /**
     * 将ScheduledTask Entity转换为AssociatedTaskResponse
     */
    private AssociatedTaskResponse convertToAssociatedTaskResponse(ScheduledTask task) {
        return AssociatedTaskResponse.builder()
                .taskId(task.getTaskId())
                .taskName(task.getTaskName())
                .taskType(task.getTaskType())
                .cronExpression(task.getCronExpression())
                .timeoutSeconds(task.getTimeoutSeconds())
                .status(task.getStatus())
                .parentTaskId(task.getParentTaskId())
                .parameters(task.getParameters())
                .description(task.getDescription())
                .createdTime(task.getCreatedTime())
                .updatedTime(task.getUpdatedTime())
                .build();
    }

}