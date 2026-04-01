package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.BacktestResult;
import com.crypto.trade.entity.BacktestTask;
import com.crypto.trade.entity.FreqtradeConfig;
import com.crypto.trade.entity.StrategyConfig;
import com.crypto.trade.repository.BacktestResultRepository;
import com.crypto.trade.repository.BacktestTaskRepository;
import com.crypto.trade.repository.FreqtradeConfigRepository;
import com.crypto.trade.repository.StrategyConfigRepository;
import com.crypto.trade.rest.controller.model.request.BacktestTaskRequest;
import com.crypto.trade.rest.controller.model.response.BacktestResultResponse;
import com.crypto.trade.rest.controller.model.response.BacktestTaskResponse;
import com.crypto.trade.service.cex.ApiKeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

@Slf4j
@Service
public class BacktestServiceImpl implements BacktestService {

    @Autowired
    BacktestTaskRepository taskRepository;
    @Autowired
    BacktestResultRepository resultRepository;
    @Autowired
    FreqtradeConfigRepository configRepository;
    @Autowired
    StrategyConfigRepository strategyRepository;
    @Autowired
    FreqtradeDockerService dockerService;
    @Autowired
    ConfigGeneratorService configGenerator;
    @Autowired
    StrategyDeployService strategyDeployService;
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    BacktestAsyncRunner backtestAsyncRunner;

    @Override
    public BacktestTask createAndStartBacktest(BacktestTaskRequest request) {
        FreqtradeConfig config = configRepository.findById(request.getFreqtradeConfigId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Freqtrade Config ID"));
        StrategyConfig strategy = strategyRepository.findById(request.getStrategyConfigId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Strategy Config ID"));

        BacktestTask task = BacktestTask.builder()
                .taskName(request.getTaskName())
                .freqtradeConfigId(config.getId())
                .strategyConfigId(strategy.getId())
                .timeRange(request.getTimeRange())
                .timeframe(request.getTimeframe())
                .userDataDir(config.getUserDataDir())
                .status("PENDING")
                .build();

        task = taskRepository.save(task);

        // Run backtest async
        backtestAsyncRunner.runBacktestAsync(task.getId(), config, strategy, request.getTimeRange(), request.getTimeframe());

        return task;
    }

    @Override
    public Page<BacktestTaskResponse> getBacktestTasks(String status, Pageable pageable) {
        Page<BacktestTask> page;
        if (status != null && !status.isEmpty()) {
            page = taskRepository.findByStatus(status, pageable);
        } else {
            page = taskRepository.findAll(pageable);
        }

        return page.map(task -> {
            BacktestTaskResponse response = new BacktestTaskResponse();
            response.setId(task.getId());
            response.setTaskName(task.getTaskName());
            response.setFreqtradeConfigId(task.getFreqtradeConfigId());
            response.setStrategyConfigId(task.getStrategyConfigId());
            response.setTimeRange(task.getTimeRange());
            response.setTimeframe(task.getTimeframe());
            response.setStatus(task.getStatus());
            response.setErrorMsg(task.getErrorMsg());
            response.setCreatedAt(task.getCreatedAt());
            response.setFinishedAt(task.getFinishedAt());

            configRepository.findById(task.getFreqtradeConfigId())
                    .ifPresent(c -> response.setFreqtradeConfigName(c.getConfigName()));
            strategyRepository.findById(task.getStrategyConfigId())
                    .ifPresent(s -> response.setStrategyName(s.getStrategyName()));

            resultRepository.findByTaskId(task.getId()).ifPresent(res -> {
                response.setTotalProfitAbs(res.getTotalProfitAbs());
                response.setTotalProfitPct(res.getTotalProfitPct());
                response.setMaxDrawdownAbs(res.getMaxDrawdownAbs());
                response.setMaxDrawdownPct(res.getMaxDrawdownPct());
                response.setWinRate(res.getWinRate());
                response.setSharpeRatio(res.getSharpeRatio());
                response.setTotalTrades(res.getTotalTrades());
            });

            return response;
        });
    }

    @Override
    public BacktestResultResponse getBacktestResult(Long taskId) {
        // 找不到结果时返回空结果（处理 RUNNING 状态）
        Optional<BacktestResult> optResult = resultRepository.findByTaskId(taskId);

        // 先获取 BacktestTask 信息（用于 RUNNING 状态或正常返回）
        BacktestTask backtestTask = taskRepository.findById(taskId).orElse(null);
        String strategyName = null;
        String freqtradeConfigName = null;
        String timeRange = null;
        String timeframe = null;

        if (backtestTask != null) {
            timeRange = backtestTask.getTimeRange();
            timeframe = backtestTask.getTimeframe();

            // 获取策略名称
            StrategyConfig strategyConfig = strategyRepository.findById(backtestTask.getStrategyConfigId()).orElse(null);
            if (strategyConfig != null) {
                strategyName = strategyConfig.getStrategyName();
            }

            // 获取配置名称
            FreqtradeConfig freqtradeConfig = configRepository.findById(backtestTask.getFreqtradeConfigId()).orElse(null);
            if (freqtradeConfig != null) {
                freqtradeConfigName = freqtradeConfig.getConfigName();
            }
        }

        if (!optResult.isPresent()) {
            return createEmptyResponse(taskId, strategyName, freqtradeConfigName, timeRange, timeframe);
        }

        BacktestResult result = optResult.get();

        BacktestResultResponse response = new BacktestResultResponse();
        response.setTaskId(taskId);
        response.setTotalProfitAbs(result.getTotalProfitAbs());
        response.setTotalProfitPct(result.getTotalProfitPct());
        response.setMaxDrawdownAbs(result.getMaxDrawdownAbs());
        response.setMaxDrawdownPct(result.getMaxDrawdownPct());
        response.setWinRate(result.getWinRate());
        response.setTotalTrades(result.getTotalTrades());
        response.setSharpeRatio(result.getSharpeRatio());

        // 读取 JSON 文件提取交易记录
        List<Map<String, Object>> dailyProfit = new ArrayList<>();
        List<Map<String, Object>> trades = new ArrayList<>();
        try {
            // 设置任务参数到响应（已在前面获取）
            response.setTimeRange(timeRange);
            response.setTimeframe(timeframe);
            response.setStrategyName(strategyName);
            response.setFreqtradeConfigName(freqtradeConfigName);

            // 获取 FreqtradeConfig 用于读取结果文件
            FreqtradeConfig freqtradeConfig = configRepository.findById(backtestTask.getFreqtradeConfigId()).orElse(null);
            if (freqtradeConfig == null) {
                throw new IllegalArgumentException("Freqtrade config not found");
            }

            // 优先查找 result.json，如果不存在则查找 .meta.json
            Path resultsDir = Paths.get(freqtradeConfig.getUserDataDir(), "backtests", "task_" + taskId, "freqtrade", "backtest_results");
            Path jsonPath = resultsDir.resolve("result.json");

            // 如果 result.json 不存在，查找最新的 .meta.json
            if (!Files.exists(jsonPath)) {
                jsonPath = findLatestMetaJson(resultsDir);
            }

            if (jsonPath != null && Files.exists(jsonPath)) {
                JsonNode rootNode = objectMapper.readTree(jsonPath.toFile());

                // 处理不同格式：可能是 {"strategy": {...}} 或直接是策略数据
                JsonNode strategyNode = rootNode.path("strategy");

                // 如果没有 strategy 字段，可能直接在根节点
                JsonNode dataNode = !strategyNode.isMissingNode() ?
                        strategyNode.path(strategyNode.fieldNames().next()) : rootNode;

                if (!dataNode.isMissingNode()) {
                    // trades
                    JsonNode tradesNode = dataNode.path("trades");
                    if (tradesNode.isArray()) {
                        for (JsonNode trade : tradesNode) {
                            Map<String, Object> tradeMap = objectMapper.convertValue(trade, Map.class);
                            // 添加方向字段：is_short=true -> "做空", is_short=false -> "做多"
                            Boolean isShort = trade.path("is_short").asBoolean(false);
                            tradeMap.put("type", isShort ? "做空" : "做多");
                            trades.add(tradeMap);
                        }
                    }

                    // daily profit - 可能是数组格式
                    JsonNode dailyProfitNode = dataNode.path("daily_profit");
                    if (dailyProfitNode.isArray()) {
                        for (JsonNode dp : dailyProfitNode) {
                            // daily_profit 可能是 [[date, value], ...] 格式
                            if (dp.isArray() && dp.size() >= 2) {
                                Map<String, Object> dpMap = new java.util.LinkedHashMap<>();
                                dpMap.put("date", dp.get(0).asText());
                                dpMap.put("profit", dp.get(1).asText());
                                dailyProfit.add(dpMap);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取回测JSON文件失败: {}", e.getMessage());
        }

        response.setDailyProfit(dailyProfit);
        response.setTrades(trades);

        // 读取日志
        String logContent = "暂无日志";
        try {
            BacktestTask task = taskRepository.findById(taskId).orElse(null);
            if (task != null) {
                FreqtradeConfig config = configRepository.findById(task.getFreqtradeConfigId()).orElse(null);
                if (config != null) {
                    Path logPath = Paths.get(config.getUserDataDir(), "backtests", "task_" + taskId, "freqtrade", "freqtrade.log");
                    if (Files.exists(logPath)) {
                        logContent = Files.readString(logPath);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取日志文件失败: {}", e.getMessage());
        }
        response.setLogs(logContent);

        return response;
    }

    @Override
    public void stopBacktest(Long taskId) {
        BacktestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if ("RUNNING".equals(task.getStatus())) {
            try {
                FreqtradeConfig config = configRepository.findById(task.getFreqtradeConfigId()).orElse(null);
                if (config != null) {
                    Path instanceDir = Paths.get(config.getUserDataDir(), "backtests", "task_" + taskId);
                    if (Files.exists(instanceDir)) {
                        dockerService.stopContainer(instanceDir.toString());
                    }
                }
            } catch (Exception e) {
                log.warn("停止回测容器失败: {}", e.getMessage());
            }

            task.setStatus("FAILED");
            task.setErrorMsg("Stopped by user");
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    @Override
    public void deleteBacktest(Long taskId) {
        BacktestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        // 只有成功或失败的任务可以删除
        if (!"SUCCESS".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            throw new IllegalArgumentException("只能删除成功或失败的任务");
        }

        // 删除本地目录
        try {
            FreqtradeConfig config = configRepository.findById(task.getFreqtradeConfigId()).orElse(null);
            if (config != null) {
                Path instanceDir = Paths.get(config.getUserDataDir(), "backtests", "task_" + taskId);
                if (Files.exists(instanceDir)) {
                    // 停止容器（如果还在运行）
                    dockerService.stopContainer(instanceDir.toString());
                    // 删除目录
                    deleteDirectory(instanceDir);
                    log.debug("删除回测目录: {}", instanceDir);
                }
            }
        } catch (Exception e) {
            log.warn("删除回测目录失败: {}", e.getMessage());
        }

        // 删除数据库记录
        resultRepository.findByTaskId(taskId).ifPresent(resultRepository::delete);
        taskRepository.delete(task);
        log.debug("删除回测任务: {}", taskId);
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * 查找最新的 .meta.json 文件
     */
    private Path findLatestMetaJson(Path resultsDir) {
        try {
            if (!Files.exists(resultsDir)) {
                return null;
            }
            Path latest = Files.list(resultsDir)
                    .filter(p -> p.toString().endsWith(".meta.json"))
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (Exception e) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
            return latest;
        } catch (Exception e) {
            log.warn("查找回测结果文件失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 创建空结果响应（用于 RUNNING 状态）
     */
    private BacktestResultResponse createEmptyResponse(Long taskId, String strategyName, String freqtradeConfigName, String timeRange, String timeframe) {
        BacktestResultResponse response = new BacktestResultResponse();
        response.setTaskId(taskId);
        response.setStrategyName(strategyName);
        response.setFreqtradeConfigName(freqtradeConfigName);
        response.setTimeRange(timeRange);
        response.setTimeframe(timeframe);
        response.setTotalProfitAbs(BigDecimal.ZERO);
        response.setTotalProfitPct(BigDecimal.ZERO);
        response.setMaxDrawdownAbs(BigDecimal.ZERO);
        response.setMaxDrawdownPct(BigDecimal.ZERO);
        response.setWinRate(BigDecimal.ZERO);
        response.setTotalTrades(0);
        response.setSharpeRatio(BigDecimal.ZERO);
        response.setDailyProfit(new ArrayList<>());
        response.setTrades(new ArrayList<>());
        response.setLogs("任务运行中，暂无结果...");
        return response;
    }
}
