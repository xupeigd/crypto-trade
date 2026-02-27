package com.crypto.trade.service.task;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DataFetchConfig;
import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.entity.TaskExecution;
import com.crypto.trade.processor.DataProcessor;
import com.crypto.trade.repository.ScheduledTaskRepository;
import com.crypto.trade.repository.TaskExecutionRepository;
import com.crypto.trade.service.ProxyServiceConfigService;
import com.crypto.trade.service.cex.ApiKeyService;
import com.crypto.trade.service.data.ApiConfigService;
import com.crypto.trade.util.DuplicateChecker;
import com.crypto.trade.util.HttpRequestBuilder;
import com.crypto.trade.util.TimeoutValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * TaskExecutorService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Slf4j
@Service
public class TaskExecutorService {

//    private static final Logger logger = LoggerFactory.getLogger(TaskExecutorService.class);

    private final TaskExecutionRepository taskExecutionRepository;
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final DuplicateChecker duplicateChecker;
    private final TimeoutValidator timeoutValidator;
    private final TaskTriggerService taskTriggerService;
    private final ApiConfigService apiConfigService;
    private final ProxyServiceConfigService proxyServiceConfigService;
    private final ApiKeyService apiKeyService;
    private final ApplicationContext applicationContext;
    private final HttpRequestBuilder httpRequestBuilder;

    @Autowired
    public TaskExecutorService(TaskExecutionRepository taskExecutionRepository,
                               ScheduledTaskRepository scheduledTaskRepository,
                               DuplicateChecker duplicateChecker,
                               TimeoutValidator timeoutValidator,
                               @Lazy TaskTriggerService taskTriggerService,
                               ApiConfigService apiConfigService,
                               ProxyServiceConfigService proxyServiceConfigService,
                               ApiKeyService apiKeyService,
                               ApplicationContext applicationContext,
                               HttpRequestBuilder httpRequestBuilder) {
        this.taskExecutionRepository = taskExecutionRepository;
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.duplicateChecker = duplicateChecker;
        this.timeoutValidator = timeoutValidator;
        this.taskTriggerService = taskTriggerService;
        this.apiConfigService = apiConfigService;
        this.proxyServiceConfigService = proxyServiceConfigService;
        this.apiKeyService = apiKeyService;
        this.applicationContext = applicationContext;
        this.httpRequestBuilder = httpRequestBuilder;
    }

    /**
     * 执行任务
     */
    @Async("taskExecutor")
    public CompletableFuture<TaskExecution> executeTask(ScheduledTask task, String triggerType, String parentTaskName) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("开始执行任务 - 任务ID: {}, 任务名称: {}, 触发类型: {}, 父任务: {}",
                    task.getTaskId(), task.getTaskName(), triggerType, parentTaskName);

            // 创建执行记录
            TaskExecution execution = createExecutionRecord(task, triggerType, parentTaskName);

            try {
                // 使用时间窗口检查重复执行，排除当前执行记录
                int timeWindowMinutes = duplicateChecker.getTimeWindowByTriggerType(triggerType);
                if (duplicateChecker.hasDuplicateExecutionInTimeWindow(
                        task.getTaskId(), parentTaskName, execution.getExecutionId(), timeWindowMinutes)) {
                    log.warn("跳过重复执行 - 任务ID: {}, 执行ID: {}, 时间窗口: {}分钟",
                            task.getTaskId(), execution.getExecutionId(), timeWindowMinutes);
                    execution.setExecutionStatus("skipped");
                    execution.setErrorMessage("Duplicate execution detected in time window");
                    execution.setFinishTime(LocalDateTime.now());
                    return taskExecutionRepository.save(execution);
                }

                // 检查超时
                if (timeoutValidator.isTimeout(execution.getTriggerTime(), task.getTimeoutSeconds())) {
                    log.warn("任务执行超时 - 任务ID: {}, 超时时间: {}秒, 执行ID: {}",
                            task.getTaskId(), task.getTimeoutSeconds(), execution.getExecutionId());
                    execution.setExecutionStatus("timeout");
                    execution.setErrorMessage("Task timeout before execution");
                    execution.setFinishTime(LocalDateTime.now());
                    return taskExecutionRepository.save(execution);
                }

                // 更新执行状态为运行中
                execution.setExecutionStatus("running");
                execution.setActualExecuteTime(LocalDateTime.now());
                taskExecutionRepository.save(execution);
                log.info("任务开始运行 - 任务ID: {}, 执行ID: {}", task.getTaskId(), execution.getExecutionId());

                // 执行具体任务逻辑
                String result = executeTaskLogic(task);

                // 更新执行结果为成功
                execution.setExecutionStatus("success");
                execution.setExecutionResult(result);
                execution.setFinishTime(LocalDateTime.now());
                log.info("任务执行成功 - 任务ID: {}, 执行ID: {}, 耗时: {}ms, 结果: {}",
                        task.getTaskId(), execution.getExecutionId(),
                        java.time.Duration.between(execution.getActualExecuteTime(), execution.getFinishTime()).toMillis(),
                        result);

                // 如果是父任务触发，触发子任务
                if ("parent".equals(triggerType)) {
                    log.info("触发子任务 - 父任务ID: {}", task.getTaskId());
                    taskTriggerService.triggerChildTasks(task.getTaskId());
                }

            } catch (Exception e) {
                // 更新执行结果为失败
                execution.setExecutionStatus("failed");
                execution.setErrorMessage(e.getMessage());
                execution.setFinishTime(LocalDateTime.now());
                log.error("任务执行失败 - 任务ID: {}, 执行ID: {}, 错误信息: {}",
                        task.getTaskId(), execution.getExecutionId(), e.getMessage(), e);
            }

            return taskExecutionRepository.save(execution);
        });
    }

    /**
     * 创建执行记录
     */
    private TaskExecution createExecutionRecord(ScheduledTask task, String triggerType, String parentTaskName) {
        TaskExecution execution = new TaskExecution();
        execution.setTaskId(task.getTaskId());
        execution.setTriggerType(triggerType);
        execution.setParentTaskName(parentTaskName);
        execution.setTriggerTime(LocalDateTime.now());
        execution.setExecutionStatus("pending");
        execution.setCreatedTime(LocalDateTime.now());
        return taskExecutionRepository.save(execution);
    }

    /**
     * 执行具体任务逻辑
     */
    private String executeTaskLogic(ScheduledTask task) {
        // 根据任务类型执行不同的逻辑
        switch (task.getTaskType()) {
            case "data_fetch":
                return executeDataFetchTask(task);
            case "data_calculation":
                return executeDataCalculationTask(task);
            default:
                throw new IllegalArgumentException("Unknown task type: " + task.getTaskType());
        }
    }

    /**
     * 执行数据获取任务
     */
    private String executeDataFetchTask(ScheduledTask task) {
        log.info("执行数据获取任务 - 任务ID: {}, 任务类型: data_fetch", task.getTaskId());

        try {
            // 获取数据获取配置
            DataFetchConfig config = apiConfigService.getConfigByTaskId(task.getTaskId());
            if (null == config) {
                throw new RuntimeException("未找到任务ID为 " + task.getTaskId() + " 的数据获取配置");
            }

            log.info("获取数据获取配置成功 - API地址: {}{}, HTTP方法: {}",
                    config.getCexBaseUrl(), config.getApiPath(), config.getHttpMethod());

            // 调用CEX API
            String apiResponse = callCexApi(config);
            log.info("API调用成功 - 响应长度: {} 字符", apiResponse.length());

            // 处理响应数据并写入DuckDB
            String result = processApiResponse(apiResponse, config);
            log.info("数据处理成功 - 目标表: {}", config.getTargetDuckdbTable());

            return "数据获取任务执行成功: " + result;

        } catch (Exception e) {
            log.error("执行数据获取任务失败 - 任务ID: {}, 错误: {}", task.getTaskId(), e.getMessage(), e);
            throw new RuntimeException("执行数据获取任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用CEX API
     */
    private String callCexApi(DataFetchConfig config) {
        try {
            // 获取HTTP客户端配置
            HttpClient client = createHttpClient(config);

            // 获取认证信息
            ApiKey authInfo = null;
            if (config.getRequiresAuth() && config.getAuthKeyId() != null) {
                try {
                    authInfo = apiKeyService.getDecryptedKey(config.getAuthKeyId());
                    log.debug("获取认证信息成功 - 认证Key ID: {}", config.getAuthKeyId());
                } catch (Exception e) {
                    log.warn("获取认证信息失败 - Key ID: {}, 错误: {}", config.getAuthKeyId(), e.getMessage());
                }
            }

            // 构建请求
            HttpRequest request = httpRequestBuilder.buildRequest(config, authInfo);

            // 执行请求
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("API调用成功 - 状态码: {}, 响应长度: {} 字符",
                    response.statusCode(), response.body().length());

            return response.body();
        } catch (Exception e) {
            log.error("API调用失败 - URL: {}{}, 错误: {}",
                    config.getCexBaseUrl(), config.getApiPath(), e.getMessage(), e);
            throw new RuntimeException("API调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建HTTP客户端
     */
    private HttpClient createHttpClient(DataFetchConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);

        // 如果需要代理，配置代理
        if (Boolean.TRUE.equals(config.getRequiresProxy()) && config.getProxyId() != null) {
            try {
                var proxyConfig = proxyServiceConfigService.getProxyConfigById(config.getProxyId());
                if (null != proxyConfig && "active".equals(proxyConfig.getStatus())) {
                    Proxy proxy = proxyServiceConfigService.createProxy(proxyConfig);
                    builder.proxy(ProxySelector.of((InetSocketAddress) proxy.address()));
                }
            } catch (Exception e) {
                // 代理配置获取失败，继续使用无代理方式
                log.warn("获取代理配置失败: {}", e.getMessage());
            }
        }

        return builder.build();
    }


    /**
     * 处理API响应数据
     */
    private String processApiResponse(String apiResponse, DataFetchConfig config) {
        String className = config.getDataProcessorClass();
        log.debug("从Spring容器获取数据处理器Bean: {}", className);

        try {
            // 检查类是否存在
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                log.error("数据处理器类不存在: {} - 配置ID: {}", className, config.getConfigId());
                log.warn("请检查处理器类是否已实现或配置是否正确");
                throw new RuntimeException("数据处理器类不存在: " + className +
                        "。请联系管理员修复配置或实现对应的处理器类。", e);
            }

            // 检查Spring容器中是否存在对应的Bean
            Object processorInstance;
            try {
                processorInstance = applicationContext.getBean(clazz);
            } catch (Exception e) {
                log.error("Spring容器中未找到处理器Bean: {} - 确保类已正确注册为Spring Bean", className);
                throw new RuntimeException("Spring容器中未找到处理器Bean: " + className +
                        "。请确保该类已使用@Component或其他注解正确注册。", e);
            }

            // 检查是否实现了DataProcessor接口
            if (processorInstance instanceof DataProcessor) {
                @SuppressWarnings("PatternVariableCanBeUsed") DataProcessor dataProcessor = (DataProcessor) processorInstance;
                log.debug("数据处理器验证成功: {}, 开始处理数据", className);
                return dataProcessor.process(apiResponse, config.getTargetDuckdbTable(), config);
            } else {
                log.error("数据处理器类未实现DataProcessor接口: {}", className);
                throw new RuntimeException("数据处理器类 " + className + " 未实现 DataProcessor 接口");
            }

        } catch (RuntimeException e) {
            // 重新抛出运行时异常
            throw e;
        } catch (Exception e) {
            log.error("处理API响应数据时发生意外错误 - 配置类: {}, 错误: {}", className, e.getMessage(), e);
            throw new RuntimeException("处理API响应数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行数据计算任务
     */
    private String executeDataCalculationTask(ScheduledTask task) {
        // TODO: 实现数据计算逻辑
        // 1. 从DuckDB读取数据
        // 2. 执行计算逻辑
        // 3. 将结果写入SQLite
        return "Data calculation task executed successfully";
    }

    /**
     * 手动执行任务
     */
    public TaskExecution executeTaskManually(Long taskId) {
        ScheduledTask task = scheduledTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));

        return executeTask(task, "manual", "st").join();
    }
}