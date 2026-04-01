package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.*;
import com.crypto.trade.repository.BacktestResultRepository;
import com.crypto.trade.repository.BacktestTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回测异步执行器
 * 独立处理回测任务的异步执行，避免循环依赖
 */
@Slf4j
@Service
public class BacktestAsyncRunner {

    @Autowired
    BacktestTaskRepository taskRepository;
    @Autowired
    BacktestResultRepository resultRepository;
    @Autowired
    FreqtradeDockerService dockerService;
    @Autowired
    ConfigGeneratorService configGenerator;
    @Autowired
    StrategyDeployService strategyDeployService;
    @Autowired
    ObjectMapper objectMapper;

    @Async
    public void runBacktestAsync(Long taskId, FreqtradeConfig config, StrategyConfig strategy, String timeRange, String timeframe) {
        BacktestTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus("RUNNING");
        taskRepository.save(task);

        try {
            // 创建虚拟ApiKey
            ApiKey mockApiKey = new ApiKey();
            mockApiKey.setCexName("okx");
            mockApiKey.setAccessKey("mock");
            mockApiKey.setSecretKey("mock");
            mockApiKey.setPassPhrase("mock");

            // 1. 创建独立实例目录
            String instanceDir = dockerService.createBacktestDirectory(config.getUserDataDir(), taskId.toString());

            // 2. 生成 config.json
            configGenerator.generateConfigFile(instanceDir, mockApiKey, strategy.getStrategyName(), true, "backtest-" + taskId);

            // 3. 部署策略文件
            Path strategiesDir = Paths.get(instanceDir, "freqtrade", "strategies");
            strategyDeployService.deployStrategyToDir(strategiesDir.toString(), strategy.getStrategyName(), strategy.getStrategyCode());

            // 4. 生成 docker-compose.yml
            String globalDataDir = Paths.get(config.getUserDataDir(), "data").toString();
            dockerService.generateBacktestDockerComposeFile(
                    instanceDir,
                    taskId.toString(),
                    config.getDockerImage(),
                    strategy.getStrategyName(),
                    timeRange,
                    timeframe,
                    mockApiKey.getCexName(),
                    globalDataDir
            );

            // 5. 运行 Docker 容器
            dockerService.runBacktestContainer(instanceDir);

            // 5.1 等待容器完成（通过 docker ps 判断容器是否还在运行）
            while (dockerService.isBacktestContainerRunning(instanceDir)) {
                Thread.sleep(5000); // 每5秒检查一次
            }

            // 5.2 导出容器日志到文件
            dockerService.exportContainerLogsToFile(instanceDir);

            // 容器已停止，继续解析结果

            // 6. 解析回测结果（优先从 result.json， fallback 到日志）
            Path logPath = Paths.get(instanceDir, "freqtrade", "freqtrade.log");
            Path resultsDir = Paths.get(instanceDir, "freqtrade", "backtest_results");
            Path resultJsonPath = resultsDir.resolve("result.json");
            String resultPathStr = resultJsonPath.toString();

            BacktestResult result = null;

            // 优先从 result.json 解析
            if (Files.exists(resultJsonPath)) {
                try {
                    result = parseBacktestResultFromJson(resultJsonPath.toFile(), taskId, strategy.getStrategyName());
                    log.info("从 result.json 解析回测结果成功");
                } catch (Exception e) {
                    log.warn("从 result.json 解析失败，尝试从日志解析: {}", e.getMessage());
                }
            }

            // 如果 result.json 不存在或解析失败，从日志解析
            if (result == null && Files.exists(logPath)) {
                Path metaJsonPath = findLatestMetaJson(resultsDir);
                String metaPathStr = metaJsonPath != null ? metaJsonPath.toString() : "";
                result = parseBacktestResultFromLog(logPath.toFile(), taskId, strategy.getStrategyName(), metaPathStr);
            }

            if (result != null) {
                resultRepository.save(result);
            } else {
                throw new RuntimeException("无法解析回测结果");
            }

            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("Backtest failed for task {}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    /**
     * 查找最新的 .meta.json 文件
     */
    private Path findLatestMetaJson(Path resultsDir) {
        try {
            if (!Files.exists(resultsDir)) {
                log.warn("结果目录不存在: {}", resultsDir);
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
            if (latest != null) {
                log.info("找到回测结果文件: {}", latest);
            }
            return latest;
        } catch (Exception e) {
            log.error("查找回测结果文件失败", e);
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
                // 查找策略摘要行: | Strategy002 |      0 |         0.00 |           0.000 | ...
                if (line.contains("| " + strategyName + " |")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 6) {
                        try {
                            totalTrades = Integer.parseInt(parts[2].trim());
                            // Avg Profit %
                            new BigDecimal(parts[3].trim());
                            // Tot Profit USDT
                            totalProfitAbs = new BigDecimal(parts[4].trim());
                            // Tot Profit %
                            totalProfitPct = new BigDecimal(parts[5].trim());
                        } catch (NumberFormatException e) {
                            log.warn("解析交易数据失败: {}", line);
                        }
                    }
                    // 查找 Drawdown 信息 (在同一行的末尾)
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

            // 如果没有找到策略摘要，检查是否 "No trades made"
            if (totalTrades == 0) {
                log.info("回测完成，无交易或无法解析");
            } else {
                log.info("解析回测结果: trades={}, profit={}, profit%={}%",
                        totalTrades, totalProfitAbs, totalProfitPct);
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
            // 尝试直接获取根节点的数据（某些格式可能直接在根节点）
            JsonNode tradesNode = rootNode.path("trades");
            if (tradesNode.isArray()) {
                int totalTrades = tradesNode.size();
                BigDecimal totalProfitAbs = BigDecimal.ZERO;
                BigDecimal totalProfitPct = BigDecimal.ZERO;
                BigDecimal winRate = BigDecimal.ZERO;
                BigDecimal maxDrawdownAbs = BigDecimal.ZERO;
                BigDecimal maxDrawdownPct = BigDecimal.ZERO;
                BigDecimal sharpeRatio = BigDecimal.ZERO;

                // 尝试从根节点读取汇总字段
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

        // 正常解析策略数据（从 result.json 直接读取字段）
        int totalTrades = strategyNode.path("total_trades").asInt(0);
        // winrate 是 0-1 之间的小数，转为百分比
        BigDecimal winRate = strategyNode.path("winrate").decimalValue().multiply(new BigDecimal("100"));
        // profit_total 已经是百分比形式（-0.01 表示 -1%）
        BigDecimal totalProfitPct = strategyNode.path("profit_total").decimalValue().multiply(new BigDecimal("100"));
        BigDecimal totalProfitAbs = new BigDecimal(strategyNode.path("profit_total_abs").asText("0"));
        // max_drawdown_account 是小数形式，转为百分比
        BigDecimal maxDrawdownPct = strategyNode.path("max_drawdown_account").decimalValue().multiply(new BigDecimal("100"));
        BigDecimal maxDrawdownAbs = new BigDecimal(strategyNode.path("max_drawdown_abs").asText("0"));
        // sharpe_ratio 直接读取
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
