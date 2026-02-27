package com.crypto.trade.rest.controller;

import com.crypto.trade.model.DataFetchConfigModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.CreateDataFetchConfigReq;
import com.crypto.trade.model.request.UpdateDataFetchConfigReq;
import com.crypto.trade.service.data.ApiConfigService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DataFetchConfigController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/data-fetch-configs")
public class DataFetchConfigController {

    @Autowired
    ApiConfigService apiConfigService;

    /**
     * 获取所有配置
     *
     * @return ApiResponse<List < DataFetchConfigModel>> 所有配置的统一响应格式
     */
    @GetMapping
    public ApiResponse<List<DataFetchConfigModel>> getAllConfigs() {
        try {
            log.debug("获取所有数据获取配置");
            List<DataFetchConfigModel> configs = apiConfigService.getAllConfigsModel();
            log.debug("获取所有数据获取配置成功 - 配置数量: {}", configs.size());
            return ApiResponse.ok(configs);
        } catch (Exception e) {
            log.error("获取所有数据获取配置失败", e);
            return ApiResponse.fail("获取所有数据获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取配置
     *
     * @param id 配置ID
     * @return ApiResponse<DataFetchConfigModel> 配置信息的统一响应格式
     */
    @GetMapping("/{id}")
    public ApiResponse<DataFetchConfigModel> getConfigById(@PathVariable Long id) {
        try {
            log.debug("根据ID获取数据获取配置 - ID: {}", id);
            DataFetchConfigModel config = apiConfigService.getConfigByIdModel(id);
            if (null != config) {
                log.debug("根据ID获取数据获取配置成功 - ID: {}", id);
                return ApiResponse.ok(config);
            } else {
                log.warn("未找到ID为 {} 的数据获取配置", id);
                return ApiResponse.fail("未找到配置: " + id);
            }
        } catch (Exception e) {
            log.error("根据ID获取数据获取配置失败 - ID: {}", id, e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据任务ID获取配置
     *
     * @param taskId 任务ID
     * @return ApiResponse<DataFetchConfigModel> 配置信息的统一响应格式
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<DataFetchConfigModel> getConfigByTaskId(@PathVariable Long taskId) {
        try {
            log.debug("根据任务ID获取数据获取配置 - 任务ID: {}", taskId);
            DataFetchConfigModel config = apiConfigService.getConfigByTaskIdModel(taskId);
            if (null != config) {
                log.debug("根据任务ID获取数据获取配置成功 - 任务ID: {}", taskId);
                return ApiResponse.ok(config);
            } else {
                log.warn("未找到任务ID为 {} 的数据获取配置", taskId);
                return ApiResponse.fail("未找到配置: " + taskId);
            }
        } catch (Exception e) {
            log.error("根据任务ID获取数据获取配置失败 - 任务ID: {}", taskId, e);
            return ApiResponse.fail("获取配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建配置
     *
     * @param request 创建配置请求
     * @return ApiResponse<DataFetchConfigModel> 创建的配置信息的统一响应格式
     */
    @PostMapping
    public ApiResponse<DataFetchConfigModel> createConfig(@Valid @RequestBody CreateDataFetchConfigReq request) {
        try {
            log.debug("创建数据获取配置 - 任务ID: {}", request.getTaskId());
            DataFetchConfigModel createdConfig = apiConfigService.createConfigFromRequest(request);
            log.debug("创建数据获取配置成功 - 配置ID: {}, 任务ID: {}", createdConfig.getConfigId(), createdConfig.getTaskId());
            return ApiResponse.ok(createdConfig);
        } catch (IllegalArgumentException e) {
            log.warn("创建数据获取配置失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail("创建配置失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建数据获取配置失败", e);
            return ApiResponse.fail("创建配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新配置
     *
     * @param id      配置ID
     * @param request 更新的配置请求
     * @return ApiResponse<DataFetchConfigModel> 更新的配置信息的统一响应格式
     */
    @PutMapping("/{id}")
    public ApiResponse<DataFetchConfigModel> updateConfig(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateDataFetchConfigReq request) {
        try {
            log.debug("更新数据获取配置 - ID: {}", id);
            DataFetchConfigModel updatedConfig = apiConfigService.updateConfigFromRequest(id, request);
            log.debug("更新数据获取配置成功 - 配置ID: {}, 任务ID: {}", updatedConfig.getConfigId(), updatedConfig.getTaskId());
            return ApiResponse.ok(updatedConfig);
        } catch (IllegalArgumentException e) {
            log.warn("更新数据获取配置失败 - 配置不存在: {}", e.getMessage());
            return ApiResponse.fail("更新配置失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新数据获取配置失败 - ID: {}", id, e);
            return ApiResponse.fail("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return ApiResponse<String> 删除结果的统一响应格式
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteConfig(@PathVariable Long id) {
        try {
            log.debug("删除数据获取配置 - ID: {}", id);
            apiConfigService.deleteConfig(id);
            log.debug("删除数据获取配置成功 - ID: {}", id);
            return ApiResponse.ok("删除配置成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除数据获取配置失败 - 配置不存在: {}", e.getMessage());
            return ApiResponse.fail("删除配置失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("删除数据获取配置失败 - ID: {}", id, e);
            return ApiResponse.fail("删除配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据任务ID删除配置
     *
     * @param taskId 任务ID
     * @return ApiResponse<String> 删除结果的统一响应格式
     */
    @DeleteMapping("/task/{taskId}")
    public ApiResponse<String> deleteConfigByTaskId(@PathVariable Long taskId) {
        try {
            log.debug("根据任务ID删除数据获取配置 - 任务ID: {}", taskId);
            apiConfigService.deleteConfigByTaskId(taskId);
            log.debug("根据任务ID删除数据获取配置成功 - 任务ID: {}", taskId);
            return ApiResponse.ok("删除配置成功");
        } catch (Exception e) {
            log.error("根据任务ID删除数据获取配置失败 - 任务ID: {}", taskId, e);
            return ApiResponse.fail("删除配置失败: " + e.getMessage());
        }
    }
}