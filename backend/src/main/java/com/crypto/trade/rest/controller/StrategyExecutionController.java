package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.response.FreqtradeInstanceModel;
import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.FreqtradeConfig;
import com.crypto.trade.entity.FreqtradeInstance;
import com.crypto.trade.entity.StrategyConfig;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.service.freqtrade.FreqtradeApiService;
import com.crypto.trade.service.freqtrade.InstanceLogService;
import com.crypto.trade.service.freqtrade.StrategyExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * StrategyExecutionController
 * 策略执行REST API控制器
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@RestController
@RequestMapping("/strategy-execution")
public class StrategyExecutionController {

    @Autowired
    StrategyExecutionService executionService;
    @Autowired
    InstanceLogService logService;

    /**
     * 启动策略执行
     */
    @PostMapping("/start")
    public ApiResponse<FreqtradeInstanceModel> startExecution(@RequestParam Long apiKeyId, @RequestParam Long freqtradeConfigId,
                                                              @RequestParam Long strategyConfigId, @RequestParam Boolean isDryRun) {
        try {
            log.info("收到启动请求: apiKeyId={}, freqtradeConfigId={}, strategyConfigId={}, isDryRun={}",
                    apiKeyId, freqtradeConfigId, strategyConfigId, isDryRun);
            FreqtradeInstance instance = executionService.startExecution(apiKeyId, freqtradeConfigId, strategyConfigId, isDryRun);
            return ApiResponse.ok(FreqtradeInstanceModel.fromEntity(instance));
        } catch (IllegalStateException e) {
            log.warn("策略已在运行: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("启动策略执行失败", e);
            return ApiResponse.fail("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止策略执行
     */
    @PostMapping("/stop/{instanceId}")
    public ApiResponse<FreqtradeInstanceModel> stopExecution(@PathVariable Long instanceId) {
        try {
            log.info("收到停止请求: instanceId={}", instanceId);
            FreqtradeInstance instance = executionService.stopExecution(instanceId);
            return ApiResponse.ok(FreqtradeInstanceModel.fromEntity(instance));
        } catch (Exception e) {
            log.error("停止策略执行失败", e);
            return ApiResponse.fail("停止失败: " + e.getMessage());
        }
    }

    /**
     * 重启已停止的实例
     */
    @PostMapping("/restart/{instanceId}")
    public ApiResponse<FreqtradeInstanceModel> restartExecution(@PathVariable Long instanceId) {
        try {
            log.info("收到重启请求: instanceId={}", instanceId);
            FreqtradeInstance instance = executionService.restartExecution(instanceId);
            return ApiResponse.ok(FreqtradeInstanceModel.fromEntity(instance));
        } catch (IllegalStateException e) {
            log.warn("实例已在运行: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            log.error("重启实例失败", e);
            return ApiResponse.fail("重启失败: " + e.getMessage());
        }
    }

    /**
     * 删除实例
     */
    @DeleteMapping("/{instanceId}")
    public ApiResponse<Void> deleteInstance(@PathVariable Long instanceId) {
        try {
            executionService.deleteInstance(instanceId);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("删除实例失败", e);
            return ApiResponse.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有实例
     */
    @GetMapping("/instances")
    public ApiResponse<List<FreqtradeInstanceModel>> getAllInstances() {
        List<FreqtradeInstanceModel> instances = executionService.getAllInstances().stream()
                .map(FreqtradeInstanceModel::fromEntity)
                .collect(Collectors.toList());
        return ApiResponse.ok(instances);
    }

    /**
     * 获取运行中的实例
     */
    @GetMapping("/instances/running")
    public ApiResponse<List<FreqtradeInstanceModel>> getRunningInstances() {
        List<FreqtradeInstanceModel> instances = executionService.getRunningInstances().stream()
                .map(FreqtradeInstanceModel::fromEntity)
                .collect(Collectors.toList());
        return ApiResponse.ok(instances);
    }

    /**
     * 获取实例详情
     */
    @GetMapping("/instances/{instanceId}")
    public ApiResponse<Map<String, Object>> getInstanceDetail(@PathVariable Long instanceId) {
        FreqtradeInstance instance = executionService.getInstance(instanceId);
        if (instance == null) {
            return ApiResponse.fail("实例不存在");
        }

        ApiKey apiKey = executionService.getInstanceApiKey(instanceId);
        FreqtradeConfig config = executionService.getInstanceConfig(instanceId);
        StrategyConfig strategy = executionService.getInstanceStrategy(instanceId);

        Map<String, Object> result = new HashMap<>();
        result.put("instance", FreqtradeInstanceModel.fromEntity(instance));
        result.put("apiKey", apiKey != null ? createApiKeyInfo(apiKey) : null);
        result.put("config", config);
        result.put("strategy", strategy);

        return ApiResponse.ok(result);
    }

    /**
     * 获取实例状态（同步更新）
     */
    @GetMapping("/status/{instanceId}")
    public ApiResponse<FreqtradeInstanceModel> syncAndGetStatus(@PathVariable Long instanceId) {
        executionService.syncInstanceStatus(instanceId);
        FreqtradeInstance instance = executionService.getInstance(instanceId);
        if (instance == null) {
            return ApiResponse.fail("实例不存在");
        }
        return ApiResponse.ok(FreqtradeInstanceModel.fromEntity(instance));
    }

    /**
     * 获取实例日志
     */
    @GetMapping("/logs/{instanceId}")
    public ApiResponse<String> getInstanceLogs(
            @PathVariable Long instanceId,
            @RequestParam(required = false, defaultValue = "200") Integer tail) {
        String logs = logService.getInstanceLogs(instanceId, tail);
        return ApiResponse.ok(logs);
    }

    /**
     * 搜索日志
     */
    @GetMapping("/logs/{instanceId}/search")
    public ApiResponse<List<String>> searchLogs(
            @PathVariable Long instanceId,
            @RequestParam String keyword) {
        List<String> matches = logService.searchLogs(instanceId, keyword);
        return ApiResponse.ok(matches);
    }

    /**
     * 获取实例的交易记录
     */
    @GetMapping("/trades/{instanceId}")
    public ApiResponse<List<FreqtradeApiService.TradeInfo>> getTrades(
            @PathVariable Long instanceId,
            @RequestParam(required = false) Integer limit) {
        FreqtradeInstance instance = executionService.getInstance(instanceId);
        if (instance == null) {
            return ApiResponse.fail("实例不存在");
        }

        FreqtradeConfig config = executionService.getInstanceConfig(instanceId);
        if (config == null) {
            return ApiResponse.fail("配置不存在");
        }

        // 根据 instanceId 计算认证信息
        String username = "user" + instance.getInstanceId();
        String password = "passwd:" + instance.getInstanceId();

        FreqtradeApiService apiService = new FreqtradeApiService();
        List<FreqtradeApiService.TradeInfo> trades = apiService.getTrades(config.getApiHost(), instance.getApiPort(), limit, username, password);
        return ApiResponse.ok(trades);
    }

    /**
     * 获取实例的盈亏统计
     */
    @GetMapping("/profit/{instanceId}")
    public ApiResponse<FreqtradeApiService.ProfitSummary> getProfit(@PathVariable Long instanceId) {
        FreqtradeInstance instance = executionService.getInstance(instanceId);
        if (instance == null) {
            return ApiResponse.fail("实例不存在");
        }

        FreqtradeConfig config = executionService.getInstanceConfig(instanceId);
        if (config == null) {
            return ApiResponse.fail("配置不存在");
        }

        // 根据 instanceId 计算认证信息
        String username = "user" + instance.getInstanceId();
        String password = "passwd:" + instance.getInstanceId();

        FreqtradeApiService apiService = new FreqtradeApiService();
        FreqtradeApiService.ProfitSummary profit = apiService.getProfit(config.getApiHost(), instance.getApiPort(), username, password);
        return ApiResponse.ok(profit);
    }

    /**
     * 获取实例的当前持仓
     */
    @GetMapping("/positions/{instanceId}")
    public ApiResponse<List<FreqtradeApiService.TradeInfo>> getPositions(@PathVariable Long instanceId) {
        FreqtradeInstance instance = executionService.getInstance(instanceId);
        if (instance == null) {
            return ApiResponse.fail("实例不存在");
        }

        FreqtradeConfig config = executionService.getInstanceConfig(instanceId);
        if (config == null) {
            return ApiResponse.fail("配置不存在");
        }

        // 根据 instanceId 计算认证信息
        String username = "user" + instance.getInstanceId();
        String password = "passwd:" + instance.getInstanceId();

        FreqtradeApiService apiService = new FreqtradeApiService();
        List<FreqtradeApiService.TradeInfo> positions = apiService.getStatus(config.getApiHost(), instance.getApiPort(), username, password);
        return ApiResponse.ok(positions);
    }

    /**
     * 获取实例的config.json内容
     */
    @GetMapping("/config/{instanceId}")
    public ApiResponse<String> getInstanceConfig(@PathVariable Long instanceId) {
        String content = executionService.getInstanceConfigContent(instanceId);
        if (content == null) {
            return ApiResponse.fail("实例不存在或config.json读取失败");
        }
        return ApiResponse.ok(content);
    }

    /**
     * 创建API Key信息（隐藏敏感信息）
     */
    private Map<String, Object> createApiKeyInfo(ApiKey apiKey) {
        Map<String, Object> info = new HashMap<>();
        info.put("keyId", apiKey.getKeyId());
        info.put("keyName", apiKey.getKeyName());
        info.put("cexName", apiKey.getCexName());
        info.put("isLiveTrading", apiKey.getIsLiveTrading());
        info.put("description", apiKey.getDescription());
        return info;
    }
}
