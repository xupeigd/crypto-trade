package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.repository.ProxyServiceConfigRepository;
import com.crypto.trade.service.CexProxyBindingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ConfigGeneratorService
 * Freqtrade配置文件生成服务
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class ConfigGeneratorService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CexProxyBindingService cexProxyBindingService;
    @Autowired
    private ProxyServiceConfigRepository proxyServiceConfigRepository;

    /**
     * 生成Freqtrade配置文件
     *
     * @param instanceDir  实例目录
     * @param apiKey       API Key
     * @param strategyName 策略名称
     * @param dryRun       是否模拟运行
     * @return 配置文件路径
     */
    public String generateConfigFile(String instanceDir, ApiKey apiKey,
                                     String strategyName, boolean dryRun, String instanceId) throws Exception {
        Path freqtradeDir = Paths.get(instanceDir, "freqtrade");
        Path configPath = freqtradeDir.resolve("config.json");

        // 创建配置JSON
        ObjectNode config = objectMapper.createObjectNode();

        // 基本配置
        config.put("max_open_trades", 3);
        config.put("stake_currency", "USDT");
        config.put("stake_amount", "unlimited");
        config.put("tradable_balance_ratio", 0.80);
        config.put("fiat_display_currency", "USD");
        config.put("timeframe", "5m");
        config.put("dry_run", dryRun);
        config.put("dry_run_wallet", 1000);
        config.put("cancel_open_orders_on_exit", false);
        config.put("trading_mode", "futures");
        config.put("margin_mode", "isolated");

        // 未成交超时配置
        ObjectNode unfilledtimeout = config.putObject("unfilledtimeout");
        unfilledtimeout.put("entry", 10);
        unfilledtimeout.put("exit", 10);
        unfilledtimeout.put("exit_timeout_count", 0);
        unfilledtimeout.put("unit", "minutes");

        // 入场定价配置
        ObjectNode entryPricing = config.putObject("entry_pricing");
        entryPricing.put("price_side", "same");
        entryPricing.put("use_order_book", true);
        entryPricing.put("order_book_top", 1);
        entryPricing.put("price_last_balance", 0.0);
        ObjectNode checkDepthOfMarket = entryPricing.putObject("check_depth_of_market");
        checkDepthOfMarket.put("enabled", false);
        checkDepthOfMarket.put("bids_to_ask_delta", 1);

        // 出场定价配置
        ObjectNode exitPricing = config.putObject("exit_pricing");
        exitPricing.put("price_side", "same");
        exitPricing.put("use_order_book", true);
        exitPricing.put("order_book_top", 1);

        // 交易所配置
        ObjectNode exchange = config.putObject("exchange");
        String exchangeName = getExchangeName(apiKey.getCexName());
        exchange.put("name", exchangeName);
        exchange.put("key", apiKey.getAccessKey());
        exchange.put("secret", apiKey.getSecretKey());

        // OKX需要password
        if ("okx".equalsIgnoreCase(exchangeName) && apiKey.getPassPhrase() != null) {
            exchange.put("password", apiKey.getPassPhrase());
        }

        // CCXT配置
        ObjectNode ccxtConfig = exchange.putObject("ccxt_config");
        ccxtConfig.put("enableRateLimit", true);

        // 代理配置
        ProxyServiceConfig proxyConfig = getProxyConfig(apiKey.getCexName());
        if (proxyConfig != null) {
            String proxyUrl = buildProxyUrl(proxyConfig);
            ObjectNode proxies = ccxtConfig.putObject("proxies");
            proxies.put("http", proxyUrl);
            proxies.put("https", proxyUrl);
            log.debug("为交易所 {} 添加代理配置: {}", apiKey.getCexName(), proxyUrl);
        }

        // CCXT异步配置
        ObjectNode ccxtAsyncConfig = exchange.putObject("ccxt_async_config");
        ccxtAsyncConfig.put("enableRateLimit", true);
        if (proxyConfig != null) {
            ccxtAsyncConfig.put("aiohttp_proxy", buildProxyUrl(proxyConfig));
        }

        // 沙箱模式：实盘=false，模拟=true
        exchange.put("sandbox", dryRun);

        // 交易对白名单/黑名单
        exchange.putPOJO("pair_whitelist", objectMapper.readValue(
                "[\"BTC/USDT:USDT\", \"ETH/USDT:USDT\", \"ADA/USDT:USDT\", \"XRP/USDT:USDT\", \"SOL/USDT:USDT\"]",
                com.fasterxml.jackson.databind.JsonNode.class));
        exchange.putPOJO("pair_blacklist", objectMapper.readValue(
                "[\"BNB/.*\"]",
                com.fasterxml.jackson.databind.JsonNode.class));

        // 交易对列表配置
        config.putPOJO("pairlists", objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("method", "StaticPairList")));

        // Telegram配置（默认关闭）
        ObjectNode telegram = config.putObject("telegram");
        telegram.put("enabled", false);
        telegram.put("token", "");
        telegram.put("chat_id", "");

        // 策略配置
//        config.put("strategy", strategyName);

        // API Server配置
        ObjectNode apiServer = config.putObject("api_server");
        apiServer.put("enabled", true);
        apiServer.put("listen_ip_address", "0.0.0.0");
        apiServer.put("listen_port", 8080);
        apiServer.put("username", "user" + instanceId);
        apiServer.put("password", "passwd:" + instanceId);

        // 机器人名称
        config.put("bot_name", "freqtrade");

        // 初始状态
        config.put("initial_state", "running");

        // 强制买入（默认关闭）
        config.put("forcebuy_enable", false);

        // 内部配置
        ObjectNode internals = config.putObject("internals");
        internals.put("process_throttle_secs", 5);

        // 写入文件
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        }

        log.debug("生成配置文件: {}", configPath);
        return configPath.toString();
    }

    /**
     * 更新配置文件中的策略名称
     *
     * @param instanceDir  实例目录
     * @param strategyName 策略名称
     */
    public void updateStrategy(String instanceDir, String strategyName) throws Exception {
        Path configPath = Paths.get(instanceDir, "freqtrade", "config.json");
        if (!Files.exists(configPath)) {
            log.warn("配置文件不存在: {}", configPath);
            return;
        }

        ObjectNode config = (ObjectNode) objectMapper.readTree(configPath.toFile());
        config.put("strategy", strategyName);

        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            writer.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        }

        log.info("更新策略名称: {} -> {}", configPath, strategyName);
    }

    /**
     * 转换CEX名称为Freqtrade exchange名称
     */
    private String getExchangeName(String cexName) {
        if (cexName == null) {
            return "okx";
        }
        return switch (cexName.toUpperCase()) {
            case "OKX" -> "okx";
            case "BINANCE" -> "binance";
            case "BYBIT" -> "bybit";
            default -> cexName.toLowerCase();
        };
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
     * 生成实例ID（md5前8位）
     *
     * @param apiKeyId     API Key ID
     * @param strategyName 策略名称
     * @param port         端口
     * @param isDryRun     是否模拟盘（true=模拟，false=实盘）
     * @return 实例ID
     */
    public String generateInstanceId(Long apiKeyId, String strategyName, Integer port, Boolean isDryRun) {
        // isDryRun: "sim" 表示模拟, "live" 表示实盘
        String mode = isDryRun ? "sim" : "live";
        String input = apiKeyId + "_" + strategyName + "_" + port + "_" + mode;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            // 回退方案：使用时间戳
            return String.valueOf(System.currentTimeMillis() % 100000000);
        }
    }
}
