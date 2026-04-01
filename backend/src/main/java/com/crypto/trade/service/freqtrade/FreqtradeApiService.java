package com.crypto.trade.service.freqtrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/**
 * FreqtradeApiService
 * Freqtrade REST API通信服务
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class FreqtradeApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FreqtradeApiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 默认的API用户名和密码（与config.json中一致）
     */
    private static final String DEFAULT_USERNAME = "freqtrader";
    private static final String DEFAULT_PASSWORD = "SuperSecurePassword";

    /**
     * 构建API URL
     */
    private String buildUrl(String host, Integer port, String endpoint) {
        return String.format("http://%s:%d/api/v1%s", host, port, endpoint);
    }

    /**
     * 发送GET请求（带Basic Auth认证）
     */
    private JsonNode get(String host, Integer port, String endpoint) {
        return get(host, port, endpoint, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * 发送GET请求（带Basic Auth认证）
     */
    private JsonNode get(String host, Integer port, String endpoint, String username, String password) {
        String url = buildUrl(host, port, endpoint);
        log.debug("Freqtrade API GET: {}", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 添加Basic Auth认证
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (RestClientException e) {
            log.error("Freqtrade API请求失败: {} - {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("Freqtrade API解析响应失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 发送POST请求（带Basic Auth认证）
     */
    private JsonNode post(String host, Integer port, String endpoint, Object body) {
        return post(host, port, endpoint, body, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * 发送POST请求（带Basic Auth认证）
     */
    private JsonNode post(String host, Integer port, String endpoint, Object body, String username, String password) {
        String url = buildUrl(host, port, endpoint);
        log.debug("Freqtrade API POST: {}", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 添加Basic Auth认证
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (RestClientException e) {
            log.error("Freqtrade API请求失败: {} - {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("Freqtrade API解析响应失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查API是否可用
     */
    public boolean isApiAvailable(String host, Integer port) {
        try {
            JsonNode response = get(host, port, "/ping");
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取Bot状态
     */
    public BotStatus getBotStatus(String host, Integer port) {
        BotStatus status = new BotStatus();
        try {
            JsonNode response = get(host, port, "/status");
            if (response != null && response.isArray()) {
                status.setTrades(parseTrades(response));
                status.setRunning(true);
            }
        } catch (Exception e) {
            log.error("获取Bot状态失败: {}", e.getMessage());
            status.setRunning(false);
            status.setError(e.getMessage());
        }
        return status;
    }

    /**
     * 获取盈亏统计
     */
    public ProfitSummary getProfit(String host, Integer port) {
        return getProfit(host, port, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * 获取盈亏统计（带认证信息）
     */
    public ProfitSummary getProfit(String host, Integer port, String username, String password) {
        try {
            JsonNode response = get(host, port, "/profit", username, password);
            if (response != null) {
                ProfitSummary summary = new ProfitSummary();
                summary.setProfitAll(response.has("profit_all_coin") ? new BigDecimal(response.get("profit_all_coin").asText()) : BigDecimal.ZERO);
                summary.setProfitRatio(response.has("profit_all_ratio") ? new BigDecimal(response.get("profit_all_ratio").asText()) : BigDecimal.ZERO);
                summary.setTradeCount(response.has("trade_count") ? response.get("trade_count").asInt() : 0);
                summary.setWinningTrades(response.has("winning_trades") ? response.get("winning_trades").asInt() : 0);
                return summary;
            }
        } catch (Exception e) {
            log.error("获取盈亏统计失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取当前持仓
     */
    public List<TradeInfo> getStatus(String host, Integer port) {
        return getStatus(host, port, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * 获取当前持仓（带认证信息）
     */
    public List<TradeInfo> getStatus(String host, Integer port, String username, String password) {
        try {
            JsonNode response = get(host, port, "/status", username, password);
            if (response != null && response.isArray()) {
                return parseTrades(response);
            }
        } catch (Exception e) {
            log.error("获取当前持仓失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 获取交易历史
     */
    public List<TradeInfo> getTrades(String host, Integer port, Integer limit) {
        return getTrades(host, port, limit, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * 获取交易历史（带认证信息）
     */
    public List<TradeInfo> getTrades(String host, Integer port, Integer limit, String username, String password) {
        try {
            String endpoint = limit != null ? "/trades?limit=" + limit : "/trades";
            JsonNode response = get(host, port, endpoint, username, password);
            if (response != null && response.isArray()) {
                return parseTrades(response);
            }
        } catch (Exception e) {
            log.error("获取交易历史失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 获取余额
     */
    public BalanceInfo getBalance(String host, Integer port) {
        try {
            JsonNode response = get(host, port, "/balance");
            if (response != null) {
                BalanceInfo balance = new BalanceInfo();
                balance.setCurrency(response.has("currency") ? response.get("currency").asText() : "USDT");
                balance.setTotal(response.has("total") ? new BigDecimal(response.get("total").asText()) : BigDecimal.ZERO);
                balance.setFree(response.has("free") ? new BigDecimal(response.get("free").asText()) : BigDecimal.ZERO);
                balance.setUsed(response.has("used") ? new BigDecimal(response.get("used").asText()) : BigDecimal.ZERO);
                return balance;
            }
        } catch (Exception e) {
            log.error("获取余额失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 启动Bot
     */
    public boolean startBot(String host, Integer port) {
        try {
            JsonNode response = post(host, port, "/start", new HashMap<>());
            return response != null && response.has("status") && "running".equals(response.get("status").asText());
        } catch (Exception e) {
            log.error("启动Bot失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 停止Bot
     */
    public boolean stopBot(String host, Integer port) {
        try {
            JsonNode response = post(host, port, "/stop", new HashMap<>());
            return response != null && response.has("status") && "stopped".equals(response.get("status").asText());
        } catch (Exception e) {
            log.error("停止Bot失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析交易信息
     */
    private List<TradeInfo> parseTrades(JsonNode tradesNode) {
        List<TradeInfo> trades = new ArrayList<>();
        for (JsonNode node : tradesNode) {
            TradeInfo trade = new TradeInfo();
            trade.setTradeId(node.has("trade_id") ? node.get("trade_id").asLong() : 0);
            trade.setPair(node.has("pair") ? node.get("pair").asText() : "");
            trade.setAmount(node.has("amount") ? new BigDecimal(node.get("amount").asText()) : BigDecimal.ZERO);
            trade.setAmountRequested(node.has("amount_requested") ? new BigDecimal(node.get("amount_requested").asText()) : BigDecimal.ZERO);
            trade.setOpenRate(node.has("open_rate") ? new BigDecimal(node.get("open_rate").asText()) : BigDecimal.ZERO);
            trade.setCurrentRate(node.has("current_rate") ? new BigDecimal(node.get("current_rate").asText()) : BigDecimal.ZERO);
            trade.setProfit(node.has("profit") ? new BigDecimal(node.get("profit").asText()) : BigDecimal.ZERO);
            trade.setProfitPct(node.has("profit_pct") ? new BigDecimal(node.get("profit_pct").asText()) : BigDecimal.ZERO);
            trade.setOpenDate(node.has("open_date") ? node.get("open_date").asText() : "");
            trade.setIsOpen(node.has("is_open") && node.get("is_open").asBoolean());
            trades.add(trade);
        }
        return trades;
    }

    /**
     * Bot状态信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BotStatus {
        private boolean running;
        private List<TradeInfo> trades = new ArrayList<>();
        private String error;

        public boolean isRunning() {
            return running;
        }
    }

    /**
     * 盈亏统计信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProfitSummary {
        private BigDecimal profitAll;
        private BigDecimal profitRatio;
        private Integer tradeCount;
        private Integer winningTrades;
    }

    /**
     * 交易信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TradeInfo {
        private Long tradeId;
        private String pair;
        private BigDecimal amount;
        private BigDecimal amountRequested;
        private BigDecimal openRate;
        private BigDecimal currentRate;
        private BigDecimal profit;
        private BigDecimal profitPct;
        private String openDate;
        private boolean isOpen;

        public void setIsOpen(boolean open) {
            this.isOpen = open;
        }
    }

    /**
     * 余额信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BalanceInfo {
        private String currency;
        private BigDecimal total;
        private BigDecimal free;
        private BigDecimal used;
    }
}
