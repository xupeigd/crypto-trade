package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.repository.ProxyServiceConfigRepository;
import com.crypto.trade.service.CexProxyBindingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FreqtradeDockerService
 * Freqtrade Docker容器管理服务（使用docker-compose）
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class FreqtradeDockerService {

    private static final String FREQTRADE_IMAGE_DEFAULT = "freqtradeorg/freqtrade:2026.2";
    private static final Pattern CONTAINER_ID_PATTERN = Pattern.compile("^([a-f0-9]{12,64})");

    @Autowired
    CexProxyBindingService cexProxyBindingService;
    @Autowired
    ProxyServiceConfigRepository proxyServiceConfigRepository;

    /**
     * 检查Docker是否可用
     */
    public boolean isDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.error("Docker不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查目录读写权限
     *
     * @param dirPath 目录路径
     * @return 是否有读写权限
     */
    public boolean checkDirectoryPermission(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            log.error("目录路径为空");
            return false;
        }

        Path path = Paths.get(dirPath);

        // 检查目录是否存在，不存在则创建
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("创建目录: {}", dirPath);
            } catch (Exception e) {
                log.error("创建目录失败: {}", e.getMessage());
                return false;
            }
        }

        // 检查读写权限
        File dir = path.toFile();
        if (!dir.canRead() || !dir.canWrite()) {
            log.error("目录无读写权限: {}", dirPath);
            return false;
        }

        return true;
    }

    /**
     * 创建实例目录结构
     *
     * @param baseDir    基础目录（userDataDir）
     * @param instanceId 实例ID
     * @return 实例目录路径
     */
    public String createInstanceDirectory(String baseDir, String instanceId) throws Exception {
        Path instancesDir = Paths.get(baseDir, "instances");
        Path instanceDir = instancesDir.resolve(instanceId);
        Path freqtradeDir = instanceDir.resolve("freqtrade");
        Path strategiesDir = freqtradeDir.resolve("strategies");

        // 创建目录结构
        Files.createDirectories(strategiesDir);
        log.info("创建实例目录结构: {}", instanceDir);

        return instanceDir.toString();
    }

    /**
     * 生成docker-compose.yml文件
     *
     * @param instanceDir  实例目录（绝对路径）
     * @param instanceId   实例ID
     * @param apiPort      API端口
     * @param dockerImage  Docker镜像
     * @param strategyName 策略名称
     * @param cexName      交易所名称（用于查询代理绑定）
     * @return docker-compose.yml文件路径
     */
    public String generateDockerComposeFile(String instanceDir, String instanceId,
                                            Integer apiPort, String dockerImage,
                                            String strategyName, String cexName) throws Exception {
        String imageName = dockerImage != null && !dockerImage.isEmpty()
                ? dockerImage : FREQTRADE_IMAGE_DEFAULT;

        String containerName = "freq-" + instanceId;
        String freqtradePath = instanceDir + "/freqtrade";

        // 获取代理配置
        ProxyServiceConfig proxyConfig = getProxyConfig(cexName);

        StringBuilder yaml = new StringBuilder();
        yaml.append("services:\n");
        yaml.append("  freq:\n");
        yaml.append("    image: ").append(imageName).append("\n");
        yaml.append("    container_name: ").append(containerName).append("\n");
        yaml.append("    environment: \n");
        yaml.append("      - TZ=Asia/Shanghai\n");

        // 添加代理环境变量
        if (proxyConfig != null) {
            String proxyUrl = buildProxyUrl(proxyConfig);
            yaml.append("      - HTTP_PROXY=").append(proxyUrl).append("\n");
            yaml.append("      - HTTPS_PROXY=").append(proxyUrl).append("\n");
            yaml.append("      - NO_PROXY=localhost,127.0.0.1\n");
            log.info("为交易所 {} 添加代理配置: {}", cexName, proxyUrl);
        }

        yaml.append("    ports:\n");
        yaml.append("      - \"").append(apiPort).append(":8080\"\n");
        yaml.append("    volumes:\n");
        yaml.append("      - ").append(freqtradePath).append(":/freqtrade/user_data\n");
        yaml.append("    deploy:\n");
        yaml.append("      resources:\n");
        yaml.append("        limits:\n");
        yaml.append("          cpus: '1'\n");
        yaml.append("          memory: 256M\n");
        yaml.append("    command: > \n");
        yaml.append("      trade \n");
        yaml.append("      --config /freqtrade/user_data/config.json \n");
        yaml.append("      --logfile /freqtrade/user_data/freqtrade.log \n");
        yaml.append("      --strategy ").append(strategyName).append(" \n");
        yaml.append("    restart: unless-stopped\n");
        yaml.append("    extra_hosts: \n");
        yaml.append("      - \"host.docker.internal:host-gateway\"");

        Path composePath = Paths.get(instanceDir, "docker-compose.yml");
        try (FileWriter writer = new FileWriter(composePath.toFile())) {
            writer.write(yaml.toString());
        }

        log.info("生成docker-compose.yml: {}", composePath);
        return composePath.toString();
    }

    /**
     * 获取交易所绑定的代理配置
     */
    private ProxyServiceConfig getProxyConfig(String cexName) {
        if (cexName == null || cexName.isEmpty()) {
            return null;
        }

        try {
            var binding = cexProxyBindingService.getActiveBindingByCex(cexName);
            if (binding.isPresent()) {
                Long proxyId = binding.get().getProxyId();
                return proxyServiceConfigRepository.findById(proxyId).orElse(null);
            }
        } catch (Exception e) {
            log.warn("获取代理配置失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 构建代理URL
     */
    private String buildProxyUrl(ProxyServiceConfig proxy) {
        if (proxy == null) {
            return null;
        }
        String proxyType = proxy.getProxyType() != null ? proxy.getProxyType().toLowerCase() : "http";
        return proxyType + "://" + proxy.getServerHost() + ":" + proxy.getServerPort();
    }

    /**
     * 启动Docker容器（使用docker-compose）
     *
     * @param instanceDir 实例目录
     * @return 容器ID
     */
    public String startContainer(String instanceDir) throws Exception {
        Path composePath = Paths.get(instanceDir, "docker-compose.yml");
        if (!Files.exists(composePath)) {
            throw new RuntimeException("docker-compose.yml文件不存在: " + composePath);
        }

        log.info("启动Docker容器: docker compose -f {} up -d", composePath);

        ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                composePath.toString(), "up", "-d");
        pb.directory(new File(instanceDir));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String containerId = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Docker Compose] {}", line);
                // 尝试从输出中提取容器ID
                Matcher matcher = CONTAINER_ID_PATTERN.matcher(line.trim());
                if (matcher.find() && containerId == null) {
                    containerId = matcher.group(1);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("启动Docker容器失败，退出码: " + exitCode);
        }

        // 如果没有从输出中获取到容器ID，尝试通过容器名获取
        if (containerId == null || containerId.isEmpty()) {
            containerId = getContainerIdByCompose(instanceDir);
        }

        log.info("容器启动成功，容器ID: {}", containerId);
        return containerId;
    }

    /**
     * 通过docker-compose获取容器ID
     */
    private String getContainerIdByCompose(String instanceDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                    Paths.get(instanceDir, "docker-compose.yml").toString(), "ps", "-q");
            pb.directory(new File(instanceDir));
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.warn("获取容器ID失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 停止Docker容器
     */
    public void stopContainer(String instanceDir) throws Exception {
        if (instanceDir == null || instanceDir.isEmpty()) {
            log.warn("实例目录为空，跳过停止操作");
            return;
        }

        Path composePath = Paths.get(instanceDir, "docker-compose.yml");
        if (!Files.exists(composePath)) {
            log.warn("docker-compose.yml文件不存在，跳过停止操作");
            return;
        }

        log.info("停止Docker容器: docker compose -f {} down", composePath);

        ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                composePath.toString(), "down");
        pb.directory(new File(instanceDir));
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.warn("停止容器失败，退出码: {}", exitCode);
        }
    }

    /**
     * 删除Docker容器
     */
    public void removeContainer(String instanceDir) throws Exception {
        if (instanceDir == null || instanceDir.isEmpty()) {
            log.warn("实例目录为空，跳过删除操作");
            return;
        }

        Path composePath = Paths.get(instanceDir, "docker-compose.yml");
        if (!Files.exists(composePath)) {
            log.warn("docker-compose.yml文件不存在，跳过删除操作");
            return;
        }

        log.info("删除Docker容器: docker compose -f {} down -v", composePath);

        ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                composePath.toString(), "down", "-v");
        pb.directory(new File(instanceDir));
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.warn("删除容器失败，退出码: {}", exitCode);
        }
    }

    /**
     * 检查容器是否在运行
     */
    public boolean isContainerRunning(String instanceDir) {
        if (instanceDir == null || instanceDir.isEmpty()) {
            return false;
        }

        try {
            Path composePath = Paths.get(instanceDir, "docker-compose.yml");
            if (!Files.exists(composePath)) {
                return false;
            }

            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                    composePath.toString(), "ps", "-q");
            pb.directory(new File(instanceDir));
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line != null && !line.isEmpty();
            }
        } catch (Exception e) {
            log.error("检查容器状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取容器日志
     */
    public String getContainerLogs(String instanceDir, int tail) {
        if (instanceDir == null || instanceDir.isEmpty()) {
            return "";
        }

        try {
            Path composePath = Paths.get(instanceDir, "docker-compose.yml");
            if (!Files.exists(composePath)) {
                return "docker-compose.yml文件不存在";
            }

            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                    composePath.toString(), "logs", "--tail", String.valueOf(tail));
            pb.directory(new File(instanceDir));
            Process process = pb.start();

            StringBuilder logs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append("\n");
                }
            }

            process.waitFor();
            return logs.toString();
        } catch (Exception e) {
            log.error("获取容器日志失败: {}", e.getMessage());
            return "获取日志失败: " + e.getMessage();
        }
    }

    /**
     * 导出容器日志到文件（追加写入）
     */
    public void exportContainerLogsToFile(String instanceDir) {
        try {
            Path composePath = Paths.get(instanceDir, "docker-compose.yml");
            if (!Files.exists(composePath)) {
                log.warn("docker-compose.yml 不存在，跳过导出日志");
                return;
            }

            // 获取所有日志
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                    composePath.toString(), "logs");
            pb.directory(new File(instanceDir));
            Process process = pb.start();

            StringBuilder logs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append("\n");
                }
            }

            process.waitFor();

            // 追加写入到 freqtrade.log 文件
            Path logFile = Paths.get(instanceDir, "freqtrade", "freqtrade.log");
            if (!Files.exists(logFile.getParent())) {
                Files.createDirectories(logFile.getParent());
            }
            Files.writeString(logFile, logs.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            log.debug("导出容器日志到: {}", logFile);
        } catch (Exception e) {
            log.error("导出容器日志失败: {}", e.getMessage());
        }
    }

    /**
     * 创建回测实例目录结构
     *
     * @param baseDir 基础目录（userDataDir）
     * @param taskId  回测任务ID
     * @return 实例目录路径
     */
    public String createBacktestDirectory(String baseDir, String taskId) throws Exception {
        Path backtestsDir = Paths.get(baseDir, "backtests");
        Path instanceDir = backtestsDir.resolve("task_" + taskId);
        Path freqtradeDir = instanceDir.resolve("freqtrade");
        Path strategiesDir = freqtradeDir.resolve("strategies");
        Path resultsDir = freqtradeDir.resolve("backtest_results");

        Files.createDirectories(strategiesDir);
        Files.createDirectories(resultsDir);
        log.debug("创建回测实例目录结构: {}", instanceDir);

        return instanceDir.toString();
    }

    /**
     * 生成回测的docker-compose.yml文件
     */
    public String generateBacktestDockerComposeFile(String instanceDir, String taskId,
                                                    String dockerImage, String strategyName,
                                                    String timeRange, String timeframe,
                                                    String cexName, String globalDataDir) throws Exception {
        String imageName = dockerImage != null && !dockerImage.isEmpty()
                ? dockerImage : FREQTRADE_IMAGE_DEFAULT;

        String containerName = "freq-" + taskId;
        String freqtradePath = instanceDir + "/freqtrade";

        ProxyServiceConfig proxyConfig = getProxyConfig(cexName);

        StringBuilder yaml = new StringBuilder();
//        yaml.append("version: '3'\n");
        yaml.append("services:\n");
        yaml.append("  freq:\n");
        yaml.append("    image: ").append(imageName).append("\n");
        yaml.append("    container_name: ").append(containerName).append("\n");
        yaml.append("    environment: \n");
        yaml.append("      - TZ=Asia/Shanghai\n");

        if (proxyConfig != null) {
            String proxyUrl = buildProxyUrl(proxyConfig);
            yaml.append("      - HTTP_PROXY=").append(proxyUrl).append("\n");
            yaml.append("      - HTTPS_PROXY=").append(proxyUrl).append("\n");
            yaml.append("      - NO_PROXY=localhost,127.0.0.1\n");
        }

        yaml.append("    volumes:\n");
        yaml.append("      - ").append(freqtradePath).append(":/freqtrade/user_data\n");
//        if (globalDataDir != null && !globalDataDir.isEmpty()) {
//            yaml.append("      - ").append(globalDataDir).append(":/freqtrade/user_data/data\n");
//        }

        // 资源限制
        yaml.append("    deploy:\n");
        yaml.append("      resources:\n");
        yaml.append("        limits:\n");
        yaml.append("          cpus: '1'\n");
        yaml.append("          memory: 256M\n");

        yaml.append("    entrypoint: [\"/bin/sh\", \"-c\"] \n");

        // 合并下载数据与回测命令：先下载数据，再执行回测
        String fullCmd = "freqtrade download-data --config /freqtrade/user_data/config.json --timerange "
                + (timeRange != null ? timeRange : "") + " --timeframe " + (timeframe != null ? timeframe : "")
                + " && freqtrade backtesting --config /freqtrade/user_data/config.json --strategy " + strategyName
                + " --timerange " + (timeRange != null ? timeRange : "")
                + " --timeframe " + (timeframe != null ? timeframe : "")
                + " --logfile /freqtrade/user_data/freqtrade.log"
                + " && "
                + "LATEST_ZIP=$$(cat /freqtrade/user_data/backtest_results/.last_result.json | sed -n 's/.*\"latest_backtest\":\"\\([^\"]*\\)\".*/\\1/p') && "
                + "LATEST_BASE=$$(echo $$LATEST_ZIP | sed 's/\\.zip$$//') && "
                + "if [ -f \"/freqtrade/user_data/backtest_results/$${LATEST_BASE}.json\" ]; then "
                + "  cp \"/freqtrade/user_data/backtest_results/$${LATEST_BASE}.json\" \"/freqtrade/user_data/backtest_results/result.json\" && echo \"Copied json to result.json\"; "
                + "else "
                + "  cp \"/freqtrade/user_data/backtest_results/$$LATEST_ZIP\" \"/freqtrade/user_data/backtest_results/result.zip\" && "
                + "  python -m zipfile -e \"/freqtrade/user_data/backtest_results/result.zip\" /freqtrade/user_data/backtest_results/ && "
                + "  mv \"/freqtrade/user_data/backtest_results/$${LATEST_BASE}.json\" \"/freqtrade/user_data/backtest_results/result.json\" && "
                + "  echo \"Unzipped and renamed to result.json\"; "
                + "fi";

        yaml.append("    command:\n");
        yaml.append("      - >\n");
        yaml.append("        ").append(fullCmd).append("\n");

        yaml.append("    extra_hosts: \n");
        yaml.append("      - \"host.docker.internal:host-gateway\"");

        Path composePath = Paths.get(instanceDir, "docker-compose.yml");
        try (FileWriter writer = new FileWriter(composePath.toFile())) {
            writer.write(yaml.toString());
        }

        log.debug("生成回测docker-compose.yml: {}", composePath);
        return composePath.toString();
    }

    /**
     * 运行回测容器并收集日志
     */
    public void runBacktestContainer(String instanceDir) throws Exception {
        Path composePath = Paths.get(instanceDir, "docker-compose.yml");
        if (!Files.exists(composePath)) {
            throw new RuntimeException("docker-compose.yml文件不存在: " + composePath);
        }

        log.debug("执行回测容器: docker compose -f {} up", composePath);

        ProcessBuilder pb = new ProcessBuilder("docker", "compose", "-f",
                composePath.toString(), "up", "-d");
        pb.directory(new File(instanceDir));
        pb.redirectErrorStream(true);

        // Redirect output to a log file
        File logFile = new File(instanceDir, "freqtrade.log");
        pb.redirectOutput(logFile);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("启动回测容器失败，退出码: " + exitCode);
        }
        // 使用 -d 后台运行，启动成功即返回
        // 注意：调用方需要轮询检查结果文件或容器状态
    }

    /**
     * 检查回测容器是否仍在运行
     */
    public boolean isBacktestContainerRunning(String instanceDir) {
        return isContainerRunning(instanceDir);
    }

    /**
     * 获取回测结果目录
     */
    public Path getBacktestResultsDir(String instanceDir) {
        return Paths.get(instanceDir, "freqtrade", "backtest_results");
    }

    /**
     * 检查回测结果文件是否存在
     */
    public boolean isBacktestResultReady(String instanceDir) {
        Path resultsDir = getBacktestResultsDir(instanceDir);
        Path resultJson = resultsDir.resolve("result.json");
        return Files.exists(resultJson) || findLatestMetaJson(resultsDir) != null;
    }

    /**
     * 等待回测完成（轮询检查）
     */
    public void waitForBacktestComplete(String instanceDir, long timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!isBacktestContainerRunning(instanceDir)) {
                // 容器已停止，检查结果
                break;
            }
            if (isBacktestResultReady(instanceDir)) {
                // 结果已生成
                break;
            }
            Thread.sleep(5000); // 每5秒检查一次
        }
    }

    /**
     * 判断时间范围是否跨越多个月份
     * 格式: "20260301-20260307" 或 "20260301-20260401"
     */
    private boolean isCrossMonth(String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) {
            return false;
        }
        String[] parts = timeRange.split("-");
        if (parts.length != 2) {
            return false;
        }
        String startMonth = parts[0].length() >= 6 ? parts[0].substring(4, 6) : "";
        String endMonth = parts[1].length() >= 6 ? parts[1].substring(4, 6) : "";
        return !startMonth.isEmpty() && !endMonth.isEmpty() && !startMonth.equals(endMonth);
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
}
