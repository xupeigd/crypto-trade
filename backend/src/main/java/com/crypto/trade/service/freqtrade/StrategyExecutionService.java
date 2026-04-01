package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.FreqtradeConfig;
import com.crypto.trade.entity.FreqtradeInstance;
import com.crypto.trade.entity.StrategyConfig;
import com.crypto.trade.repository.FreqtradeConfigRepository;
import com.crypto.trade.repository.FreqtradeInstanceRepository;
import com.crypto.trade.repository.StrategyConfigRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * StrategyExecutionService
 * 策略执行协调服务
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class StrategyExecutionService {

    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    FreqtradeConfigRepository freqtradeConfigRepository;
    @Autowired
    StrategyConfigRepository strategyConfigRepository;
    @Autowired
    FreqtradeInstanceRepository instanceRepository;
    @Autowired
    FreqtradeDockerService dockerService;
    @Autowired
    StrategyDeployService deployService;
    @Autowired
    ConfigGeneratorService configGenerator;
    @Autowired
    InstanceLogService logService;

    /**
     * 启动策略执行
     *
     * @param apiKeyId          API Key ID
     * @param freqtradeConfigId Freqtrade配置ID
     * @param strategyConfigId  策略配置ID
     * @return 启动的实例信息
     */
    @Transactional
    public FreqtradeInstance startExecution(Long apiKeyId, Long freqtradeConfigId, Long strategyConfigId, Boolean isDryRun) throws Exception {
        log.info("启动策略执行: apiKeyId={}, freqtradeConfigId={}, strategyConfigId={}, isDryRun={}",
                apiKeyId, freqtradeConfigId, strategyConfigId, isDryRun);

        // 1. 验证配置
        ApiKey apiKey = apiKeyService.getDecryptedKey(apiKeyId);
        if (apiKey == null) {
            throw new IllegalArgumentException("API Key不存在: " + apiKeyId);
        }

        FreqtradeConfig freqtradeConfig = freqtradeConfigRepository.findById(freqtradeConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Freqtrade配置不存在: " + freqtradeConfigId));

        StrategyConfig strategyConfig = strategyConfigRepository.findById(strategyConfigId)
                .orElseThrow(() -> new IllegalArgumentException("策略配置不存在: " + strategyConfigId));

        // 2. 检查是否已有运行中的实例（区分模拟/实盘）
        Optional<FreqtradeInstance> existingInstance = instanceRepository
                .findByApiKeyIdAndFreqtradeConfigIdAndStrategyConfigIdAndIsDryRun(
                        apiKeyId, freqtradeConfigId, strategyConfigId, isDryRun);
        if (existingInstance.isPresent() && FreqtradeInstance.Status.RUNNING.equals(existingInstance.get().getStatus())) {
            String mode = isDryRun ? "模拟" : "实盘";
            throw new IllegalStateException(mode + "模式下该策略已在运行中: " + existingInstance.get().getInstanceName());
        }

        // 3. 检查Docker是否可用
        if (!dockerService.isDockerAvailable()) {
            throw new RuntimeException("Docker不可用，请确保Docker已安装并运行");
        }

        // 4. 检查用户目录权限
        String userDataDir = freqtradeConfig.getUserDataDir();
        if (!dockerService.checkDirectoryPermission(userDataDir)) {
            throw new RuntimeException("用户数据目录无读写权限: " + userDataDir);
        }

        // 5. 分配端口
        Integer port = allocatePort(freqtradeConfig);
        if (port == null) {
            throw new RuntimeException("无法分配可用端口");
        }

        // 6. 生成实例ID（包含模拟/实盘标识）
        String instanceId = configGenerator.generateInstanceId(apiKeyId, strategyConfig.getStrategyName(), port, isDryRun);

        // 7. 创建实例目录
        String instanceDir = dockerService.createInstanceDirectory(userDataDir, instanceId);

        // 8. 生成config.json
        configGenerator.generateConfigFile(instanceDir, apiKey, strategyConfig.getStrategyName(), isDryRun, instanceId);

        // 9. 部署策略代码
        if (strategyConfig.getStrategyCode() != null && !strategyConfig.getStrategyCode().isEmpty()) {
            Path strategiesDir = Paths.get(instanceDir, "freqtrade", "strategies");
            deployService.deployStrategyToDir(strategiesDir.toString(), strategyConfig.getStrategyName(), strategyConfig.getStrategyCode());
        }

        // 10. 生成docker-compose.yml
        dockerService.generateDockerComposeFile(instanceDir, instanceId, port, freqtradeConfig.getDockerImage(), strategyConfig.getStrategyName(), apiKey.getCexName());

        // 11. 创建实例记录（状态：STARTING）
        String instanceName = "freq-" + instanceId;
        FreqtradeInstance instance = FreqtradeInstance.builder()
                .apiKeyId(apiKeyId)
                .freqtradeConfigId(freqtradeConfigId)
                .strategyConfigId(strategyConfigId)
                .instanceId(instanceId)
                .instanceName(instanceName)
                .instanceDir(instanceDir)
                .apiPort(port)
                .status(FreqtradeInstance.Status.STARTING)
                .startedAt(LocalDateTime.now())
                .isDryRun(isDryRun)
                .build();

        instance = instanceRepository.save(instance);

        try {
            // 12. 启动Docker容器
            String containerId = dockerService.startContainer(instanceDir);
            instance.setContainerId(containerId);
            instance.setStatus(FreqtradeInstance.Status.RUNNING);
            instance = instanceRepository.save(instance);

            log.info("策略执行启动成功: instanceId={}, instanceName={}, port={}",
                    instanceId, instanceName, port);

            return instance;

        } catch (Exception e) {
            // 启动失败，更新状态
            instance.setStatus(FreqtradeInstance.Status.ERROR);
            instance.setLastError(e.getMessage());
            instance.setStoppedAt(LocalDateTime.now());
            instanceRepository.save(instance);

            throw new RuntimeException("启动Docker容器失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止策略执行
     */
    @Transactional
    public FreqtradeInstance stopExecution(Long instanceId) throws Exception {
        log.info("停止策略执行: instanceId={}", instanceId);

        FreqtradeInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例不存在: " + instanceId));

        if (!FreqtradeInstance.Status.RUNNING.equals(instance.getStatus()) &&
                !FreqtradeInstance.Status.STARTING.equals(instance.getStatus())) {
            log.warn("实例未在运行中，当前状态: {}", instance.getStatus());
            return instance;
        }

        try {
            // 停止容器
            if (instance.getInstanceDir() != null) {
                dockerService.stopContainer(instance.getInstanceDir());
            }

            instance.setStatus(FreqtradeInstance.Status.STOPPED);
            instance.setStoppedAt(LocalDateTime.now());
            instance = instanceRepository.save(instance);

            // 清除日志缓存
            logService.clearLogBuffer(instanceId);

            log.info("策略执行已停止: instanceId={}", instanceId);
            return instance;

        } catch (Exception e) {
            log.error("停止容器失败: {}", e.getMessage());
            instance.setStatus(FreqtradeInstance.Status.ERROR);
            instance.setLastError(e.getMessage());
            instance.setStoppedAt(LocalDateTime.now());
            instanceRepository.save(instance);
            throw e;
        }
    }

    /**
     * 重启已停止的实例
     */
    @Transactional
    public FreqtradeInstance restartExecution(Long instanceId) throws Exception {
        log.info("重启实例: instanceId={}", instanceId);

        FreqtradeInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例不存在: " + instanceId));

        // 检查实例是否已在运行
        if (FreqtradeInstance.Status.RUNNING.equals(instance.getStatus())) {
            throw new IllegalStateException("实例已在运行中");
        }

        String instanceDir = instance.getInstanceDir();
        if (instanceDir == null) {
            throw new IllegalStateException("实例目录不存在");
        }

        // 检查配置文件是否存在
        Path freqtradeDir = Paths.get(instanceDir, "freqtrade");
        Path configPath = freqtradeDir.resolve("config.json");
        Path dockerComposePath = Paths.get(instanceDir, "docker-compose.yml");

        if (!Files.exists(configPath)) {
            throw new IllegalStateException("配置文件不存在: " + configPath);
        }
        if (!Files.exists(dockerComposePath)) {
            throw new IllegalStateException("Docker Compose配置文件不存在: " + dockerComposePath);
        }

        try {
            // 设置为启动中状态
            instance.setStatus(FreqtradeInstance.Status.STARTING);
            instance.setLastError(null);
            instance = instanceRepository.save(instance);

            // 启动Docker容器
            String containerId = dockerService.startContainer(instanceDir);
            instance.setContainerId(containerId);
            instance.setStatus(FreqtradeInstance.Status.RUNNING);
            instance.setStartedAt(LocalDateTime.now());
            instance.setStoppedAt(null);
            instance = instanceRepository.save(instance);

            log.info("实例重启成功: instanceId={}, containerId={}", instanceId, containerId);
            return instance;

        } catch (Exception e) {
            log.error("重启实例失败: {}", e.getMessage());
            instance.setStatus(FreqtradeInstance.Status.ERROR);
            instance.setLastError(e.getMessage());
            instanceRepository.save(instance);
            throw e;
        }
    }

    /**
     * 删除实例（停止并删除容器）
     */
    @Transactional
    public void deleteInstance(Long instanceId) throws Exception {
        log.info("删除实例: instanceId={}", instanceId);

        FreqtradeInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例不存在: " + instanceId));

        // 先停止容器
        if (FreqtradeInstance.Status.RUNNING.equals(instance.getStatus())) {
            stopExecution(instanceId);
        }

        // 删除容器
        if (instance.getInstanceDir() != null) {
            try {
                dockerService.removeContainer(instance.getInstanceDir());
            } catch (Exception e) {
                log.warn("删除容器失败: {}", e.getMessage());
            }
        }

        // 清除日志缓存
        logService.clearLogBuffer(instanceId);

        // 删除实例记录
        instanceRepository.delete(instance);
        log.info("实例已删除: instanceId={}", instanceId);
    }

    /**
     * 获取所有实例
     */
    public List<FreqtradeInstance> getAllInstances() {
        return instanceRepository.findAll();
    }

    /**
     * 获取运行中的实例
     */
    public List<FreqtradeInstance> getRunningInstances() {
        return instanceRepository.findActiveInstances();
    }

    /**
     * 获取实例详情
     */
    public FreqtradeInstance getInstance(Long instanceId) {
        return instanceRepository.findById(instanceId).orElse(null);
    }

    /**
     * 更新实例状态（从Docker同步）
     */
    @Transactional
    public void syncInstanceStatus(Long instanceId) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getInstanceDir() == null) {
            return;
        }

        // 检查容器是否还在运行
        boolean containerRunning = dockerService.isContainerRunning(instance.getInstanceDir());
        if (!containerRunning && FreqtradeInstance.Status.RUNNING.equals(instance.getStatus())) {
            instance.setStatus(FreqtradeInstance.Status.STOPPED);
            instance.setStoppedAt(LocalDateTime.now());
            instanceRepository.save(instance);
        }
    }

    /**
     * 获取实例的API Key
     */
    public ApiKey getInstanceApiKey(Long instanceId) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return null;
        }
        return apiKeyService.getKeyById(instance.getApiKeyId()).orElse(null);
    }

    /**
     * 获取实例的Freqtrade配置
     */
    public FreqtradeConfig getInstanceConfig(Long instanceId) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return null;
        }
        return freqtradeConfigRepository.findById(instance.getFreqtradeConfigId()).orElse(null);
    }

    /**
     * 获取实例的策略配置
     */
    public StrategyConfig getInstanceStrategy(Long instanceId) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return null;
        }
        return strategyConfigRepository.findById(instance.getStrategyConfigId()).orElse(null);
    }

    /**
     * 获取实例的config.json内容
     */
    public String getInstanceConfigContent(Long instanceId) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getInstanceDir() == null) {
            return null;
        }
        Path configPath = Paths.get(instance.getInstanceDir(), "freqtrade", "config.json");
        try {
            return Files.readString(configPath);
        } catch (Exception e) {
            log.error("读取config.json失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 分配可用端口
     *
     * @param config Freqtrade配置
     * @return 可用端口，如果无可用端口返回null
     */
    private Integer allocatePort(FreqtradeConfig config) {
        int minPort = config.getPortRangeMin() != null ? config.getPortRangeMin() : 8081;
        int maxPort = config.getPortRangeMax() != null ? config.getPortRangeMax() : 8999;

        Optional<Integer> maxUsedPort = instanceRepository.findMaxApiPort();
        int nextPort = maxUsedPort.map(p -> Math.max(p + 1, minPort)).orElse(minPort);

        while (nextPort <= maxPort) {
            if (!instanceRepository.isPortInUse(nextPort)) {
                return nextPort;
            }
            nextPort++;
        }

        return null;
    }
}