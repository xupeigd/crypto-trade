package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.BacktestResult;
import com.crypto.trade.entity.BacktestTask;
import com.crypto.trade.entity.StrategyConfig;
import com.crypto.trade.repository.BacktestResultRepository;
import com.crypto.trade.repository.BacktestTaskRepository;
import com.crypto.trade.repository.StrategyConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回测任务状态恢复服务
 * 系统启动后检查 RUNNING 状态的任务，恢复其真实状态
 */
@Slf4j
@Service
public class BacktestStatusRecoveryService {

    @Autowired
    BacktestTaskRepository taskRepository;
    @Autowired
    BacktestResultRepository resultRepository;
    @Autowired
    FreqtradeDockerService dockerService;
    @Autowired
    StrategyConfigRepository strategyRepository;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 定时检查 RUNNING 状态的任务，每10秒执行一次
     */
    @Scheduled(fixedRate = 10000)
    public void recoverBacktestTasks() {
        log.debug("开始恢复回测任务状态...");
        List<BacktestTask> runningTasks = taskRepository.findByStatus("RUNNING");
        if (runningTasks.isEmpty()) {
            log.debug("没有需要恢复的 RUNNING 任务");
            return;
        }
        log.debug("发现 {} 个 RUNNING 状态的任务需要恢复", runningTasks.size());

        for (BacktestTask task : runningTasks) {
            recoverTaskStatus(task);
        }
        log.debug("回测任务状态恢复完成");
    }

    /**
     * 恢复单个任务的状态
     */
    private void recoverTaskStatus(BacktestTask task) {
        String userDataDir = task.getUserDataDir();
        if (userDataDir == null || userDataDir.isEmpty()) {
            log.warn("任务 {} 没有 userDataDir，无法恢复状态", task.getId());
            task.setStatus("FAILED");
            task.setErrorMsg("缺少数据目录信息");
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
            return;
        }

        // 构建实例目录路径
        String instanceDir = Paths.get(userDataDir, "backtests", "task_" + task.getId()).toString();

        // 检查 docker 容器是否还在运行
        boolean isRunning = dockerService.isBacktestContainerRunning(instanceDir);
        if (isRunning) {
            log.debug("任务 {} 的容器仍在运行中", task.getId());
            return;
        }

        log.debug("任务 {} 的容器已停止，开始解析结果", task.getId());

        // 导出容器日志到文件
        dockerService.exportContainerLogsToFile(instanceDir);

        // 容器已停止，解析结果
        try {
            StrategyConfig strategy = strategyRepository.findById(task.getStrategyConfigId()).orElse(null);
            String strategyName = strategy != null ? strategy.getStrategyName() : "";

            BacktestResult result = parseBacktestResult(instanceDir, task.getId(), strategyName);

            if (result != null) {
                resultRepository.save(result);
                task.setStatus("SUCCESS");
                log.debug("任务 {} 已成功完成", task.getId());
            } else {
                task.setStatus("FAILED");
                task.setErrorMsg("无法解析回测结果");
                log.warn("任务 {} 无法解析回测结果", task.getId());
            }
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMsg("解析结果失败: " + e.getMessage());
            log.error("任务 {} 解析结果失败", task.getId(), e);
        }

        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    /**
     * 解析回测结果
     */
    private BacktestResult parseBacktestResult(String instanceDir, Long taskId, String strategyName) {
        Path logPath = Paths.get(instanceDir, "freqtrade", "freqtrade.log");
        Path resultsDir = Paths.get(instanceDir, "freqtrade", "backtest_results");
        Path resultJsonPath = resultsDir.resolve("result.json");

        BacktestResult result = null;

        // 优先从 result.json 解析
        if (Files.exists(resultJsonPath)) {
            try {
                result = parseBacktestResultFromJson(resultJsonPath.toFile(), taskId, strategyName);
                log.debug("从 result.json 解析回测结果成功");
            } catch (Exception e) {
                log.warn("从 result.json 解析失败，尝试从日志解析: {}", e.getMessage());
            }
        }

        // 如果 result.json 不存在或解析失败，从日志解析
        if (result == null && Files.exists(logPath)) {
            Path metaJsonPath = findLatestMetaJson(resultsDir);
            String metaPathStr = metaJsonPath != null ? metaJsonPath.toString() : "";
            result = parseBacktestResultFromLog(logPath.toFile(), taskId, strategyName, metaPathStr);
        }

        return result;
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
     * 从日志文件解析回测结果
     */
    private BacktestResult parseBacktestResultFromLog(File logFile, Long taskId, String strategyName, String resultPath) {
        int totalTrades = 0;
        BigDecimal totalProfitAbs = BigDecimal.ZERO;
        BigDecimal totalProfitPct = BigDecimal.ZERO;
        BigDecimal winRate = BigDecimal.ZERO;
        BigDecimal maxDrawdownAbs = BigDecimal.ZERO;
        BigDecimal maxDrawdownPct = BigDecimal.ZERO;
        BigDecimal sharpeRatio = BigDecimal.ZERO;

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("| " + strategyName + " |")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 6) {
                        try {
                            totalTrades = Integer.parseInt(parts[2].trim());
                            totalProfitAbs = new BigDecimal(parts[4].trim());
                            totalProfitPct = new BigDecimal(parts[5].trim());
                        } catch (NumberFormatException e) {
                            log.warn("解析交易数据失败: {}", line);
                        }
                    }
                    Pattern drawdownPattern = Pattern.compile("([-\\d.]+)\\s+USDT\\s+([-\\d.]+)%");
                    Matcher matcher = drawdownPattern.matcher(line);
                    if (matcher.find()) {
                        try {
                            maxDrawdownAbs = new BigDecimal(matcher.group(1));
                            maxDrawdownPct = new BigDecimal(matcher.group(2));
                        } catch (NumberFormatException e) {
                            log.warn("解析Drawdown失败: {}", line);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.error("解析回测结果日志失败", e);
        }

        return BacktestResult.builder()
                .taskId(taskId)
                .totalProfitAbs(totalProfitAbs)
                .totalProfitPct(totalProfitPct)
                .maxDrawdownAbs(maxDrawdownAbs)
                .maxDrawdownPct(maxDrawdownPct)
                .winRate(winRate)
                .sharpeRatio(sharpeRatio)
                .totalTrades(totalTrades)
                .resultJsonPath(resultPath)
                .build();
    }

    /**
     * 从 result.json 文件解析回测结果
     */
    private BacktestResult parseBacktestResultFromJson(File jsonFile, Long taskId, String strategyName) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonFile);
        JsonNode strategyNode = rootNode.path("strategy").path(strategyName);

        if (strategyNode.isMissingNode()) {
            JsonNode tradesNode = rootNode.path("trades");
            if (tradesNode.isArray()) {
                int totalTrades = tradesNode.size();
                BigDecimal totalProfitAbs = BigDecimal.ZERO;
                BigDecimal totalProfitPct = BigDecimal.ZERO;
                BigDecimal winRate = BigDecimal.ZERO;
                BigDecimal maxDrawdownAbs = BigDecimal.ZERO;
                BigDecimal maxDrawdownPct = BigDecimal.ZERO;
                BigDecimal sharpeRatio = BigDecimal.ZERO;

                totalTrades = rootNode.path("total_trades").asInt(totalTrades);
                winRate = rootNode.path("winrate").decimalValue().multiply(new BigDecimal("100"));
                totalProfitAbs = new BigDecimal(rootNode.path("profit_total_abs").asText("0"));
                totalProfitPct = rootNode.path("profit_total").decimalValue().multiply(new BigDecimal("100"));
                maxDrawdownAbs = new BigDecimal(rootNode.path("max_drawdown_abs").asText("0"));
                maxDrawdownPct = rootNode.path("max_drawdown_account").decimalValue().multiply(new BigDecimal("100"));
                sharpeRatio = rootNode.path("sharpe").decimalValue();

                return BacktestResult.builder()
                        .taskId(taskId)
                        .totalProfitAbs(totalProfitAbs)
                        .totalProfitPct(totalProfitPct)
                        .maxDrawdownAbs(maxDrawdownAbs)
                        .maxDrawdownPct(maxDrawdownPct)
                        .winRate(winRate)
                        .sharpeRatio(sharpeRatio)
                        .totalTrades(totalTrades)
                        .resultJsonPath(jsonFile.getAbsolutePath())
                        .build();
            }
            throw new RuntimeException("未找到策略数据: " + strategyName);
        }

        int totalTrades = strategyNode.path("total_trades").asInt(0);
        BigDecimal winRate = strategyNode.path("winrate").decimalValue().multiply(new BigDecimal("100"));
        BigDecimal totalProfitPct = strategyNode.path("profit_total").decimalValue().multiply(new BigDecimal("100"));
        BigDecimal totalProfitAbs = new BigDecimal(strategyNode.path("profit_total_abs").asText("0"));
        BigDecimal maxDrawdownPct = strategyNode.path("max_drawdown_account").decimalValue().multiply(new BigDecimal("100"));
        BigDecimal maxDrawdownAbs = new BigDecimal(strategyNode.path("max_drawdown_abs").asText("0"));
        BigDecimal sharpeRatio = strategyNode.path("sharpe").decimalValue();

        return BacktestResult.builder()
                .taskId(taskId)
                .totalProfitAbs(totalProfitAbs)
                .totalProfitPct(totalProfitPct)
                .maxDrawdownAbs(maxDrawdownAbs)
                .maxDrawdownPct(maxDrawdownPct)
                .winRate(winRate)
                .sharpeRatio(sharpeRatio)
                .totalTrades(totalTrades)
                .resultJsonPath(jsonFile.getAbsolutePath())
                .build();
    }
}