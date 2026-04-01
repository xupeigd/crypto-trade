package com.crypto.trade.service.freqtrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * StrategyDeployService
 * 策略部署服务 - 将策略代码写入Freqtrade用户数据目录
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class StrategyDeployService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 部署策略代码到指定目录
     *
     * @param strategiesDir 策略目录路径
     * @param strategyName  策略名称
     * @param strategyCode  策略代码
     * @return 是否部署成功
     */
    public boolean deployStrategyToDir(String strategiesDir, String strategyName, String strategyCode) {
        if (strategiesDir == null || strategiesDir.isEmpty()) {
            log.error("策略目录路径为空");
            return false;
        }

        if (strategyName == null || strategyName.isEmpty()) {
            log.error("策略名称为空");
            return false;
        }

        if (strategyCode == null || strategyCode.isEmpty()) {
            log.warn("策略代码为空，跳过部署");
            return true;
        }

        try {
            Path strategyDir = Paths.get(strategiesDir);
            if (!Files.exists(strategyDir)) {
                Files.createDirectories(strategyDir);
                log.info("创建策略目录: {}", strategyDir);
            }

            // 写入策略文件
            String fileName = normalizeStrategyFileName(strategyName);
            Path filePath = strategyDir.resolve(fileName);

            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(strategyCode);
            }

            log.debug("策略代码部署成功: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("部署策略代码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 部署策略代码到Freqtrade策略目录
     *
     * @param userDataDir  用户数据目录路径
     * @param strategyName 策略名称
     * @param strategyCode 策略代码
     * @return 是否部署成功
     */
    public boolean deployStrategy(String userDataDir, String strategyName, String strategyCode) {
        if (userDataDir == null || userDataDir.isEmpty()) {
            log.error("用户数据目录路径为空");
            return false;
        }

        if (strategyName == null || strategyName.isEmpty()) {
            log.error("策略名称为空");
            return false;
        }

        if (strategyCode == null || strategyCode.isEmpty()) {
            log.warn("策略代码为空，跳过部署");
            return true;
        }

        try {
            // 策略目录在用户数据目录下的strategies子目录
            Path strategyDir = Paths.get(userDataDir, "strategies");
            if (!Files.exists(strategyDir)) {
                Files.createDirectories(strategyDir);
                log.info("创建策略目录: {}", strategyDir);
            }

            // 写入策略文件
            String fileName = normalizeStrategyFileName(strategyName);
            Path filePath = strategyDir.resolve(fileName);

            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(strategyCode);
            }

            log.debug("策略代码部署成功: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("部署策略代码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新Freqtrade配置文件中的策略名称
     *
     * @param userDataDir  用户数据目录路径
     * @param strategyName 策略名称
     * @return 是否更新成功
     */
    public boolean updateConfigStrategy(String userDataDir, String strategyName) {
        if (userDataDir == null || userDataDir.isEmpty()) {
            log.error("用户数据目录路径为空");
            return false;
        }

        // 配置文件在用户数据目录下的config.json
        Path configPath = Paths.get(userDataDir, "config.json");
        File file = configPath.toFile();

        if (!file.exists()) {
            log.error("配置文件不存在: {}", configPath);
            return false;
        }

        try {
            // 读取配置文件
            JsonNode config = objectMapper.readTree(file);

            // 更新策略名称
            if (config instanceof ObjectNode) {
                ((ObjectNode) config).put("strategy", strategyName);
            }

            // 写回配置文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, config);

            log.info("配置文件策略更新成功: {} -> {}", configPath, strategyName);
            return true;
        } catch (IOException e) {
            log.error("更新配置文件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查策略文件是否存在
     */
    public boolean strategyFileExists(String userDataDir, String strategyName) {
        if (userDataDir == null || strategyName == null) {
            return false;
        }

        String fileName = normalizeStrategyFileName(strategyName);
        Path filePath = Paths.get(userDataDir, "strategies", fileName);
        return Files.exists(filePath);
    }

    /**
     * 读取策略文件内容
     */
    public String readStrategyFile(String userDataDir, String strategyName) {
        if (userDataDir == null || strategyName == null) {
            return null;
        }

        try {
            String fileName = normalizeStrategyFileName(strategyName);
            Path filePath = Paths.get(userDataDir, "strategies", fileName);
            return Files.readString(filePath);
        } catch (IOException e) {
            log.error("读取策略文件失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 删除策略文件
     */
    public boolean deleteStrategyFile(String userDataDir, String strategyName) {
        if (userDataDir == null || strategyName == null) {
            return false;
        }

        try {
            String fileName = normalizeStrategyFileName(strategyName);
            Path filePath = Paths.get(userDataDir, "strategies", fileName);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除策略文件失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 规范化策略文件名
     * 确保文件名以.py结尾
     */
    private String normalizeStrategyFileName(String strategyName) {
        if (strategyName.endsWith(".py")) {
            return strategyName;
        }
        return strategyName + ".py";
    }

    /**
     * 验证策略代码基本语法
     * 仅检查是否包含基本结构
     */
    public boolean validateStrategyCode(String strategyCode) {
        if (strategyCode == null || strategyCode.isEmpty()) {
            return false;
        }

        // 检查是否包含IStrategy类定义
        boolean hasClass = strategyCode.contains("class ") && strategyCode.contains("IStrategy");
        // 检查是否包含必要的导入
        boolean hasImport = strategyCode.contains("import") || strategyCode.contains("from");

        if (!hasClass) {
            log.warn("策略代码缺少IStrategy类定义");
            return false;
        }

        return true;
    }
}