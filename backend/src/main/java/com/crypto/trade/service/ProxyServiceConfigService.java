package com.crypto.trade.service;

import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.repository.ProxyServiceConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ProxyServiceConfigService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Service
@Slf4j
public class ProxyServiceConfigService {

    @Autowired
    private ProxyServiceConfigRepository proxyServiceConfigRepository;
    @Autowired
    private CexProxyBindingService cexProxyBindingService;
    @Value("${proxy.test.tcp-timeout-ms:5000}")
    private int tcpProbeTimeoutMs;

    /**
     * 获取所有代理配置
     */
    public List<ProxyServiceConfig> getAllConfigs() {
        return proxyServiceConfigRepository.findAll();
    }

    /**
     * 根据ID获取代理配置
     */
    public ProxyServiceConfig getProxyConfigById(Long proxyId) {
        return proxyServiceConfigRepository.findById(proxyId)
                .orElseThrow(() -> new IllegalArgumentException("Proxy config not found with id: " + proxyId));
    }

    /**
     * 获取所有活跃的代理配置
     */
    public List<ProxyServiceConfig> getActiveConfigs() {
        return proxyServiceConfigRepository.findActiveConfigs();
    }

    /**
     * 创建代理配置
     */
    public ProxyServiceConfig createProxyConfig(ProxyServiceConfig config) {
        // 验证代理名称唯一性
        if (proxyServiceConfigRepository.existsByProxyName(config.getProxyName())) {
            throw new IllegalArgumentException("Proxy name already exists: " + config.getProxyName());
        }

        // 验证代理类型
        if (!isValidProxyType(config.getProxyType())) {
            throw new IllegalArgumentException("Invalid proxy type: " + config.getProxyType() + ". Must be HTTP or SOCKS5");
        }

        // 验证端口范围
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            throw new IllegalArgumentException("Invalid port number: " + config.getServerPort());
        }

        config.setCreatedTime(LocalDateTime.now());
        config.setUpdatedTime(LocalDateTime.now());
        return proxyServiceConfigRepository.save(config);
    }

    /**
     * 更新代理配置
     */
    public ProxyServiceConfig updateProxyConfig(Long proxyId, ProxyServiceConfig config) {
        ProxyServiceConfig existingConfig = getProxyConfigById(proxyId);

        // 验证代理名称唯一性（排除当前配置）
        if (proxyServiceConfigRepository.existsByProxyNameAndProxyIdNot(config.getProxyName(), proxyId)) {
            throw new IllegalArgumentException("Proxy name already exists: " + config.getProxyName());
        }

        // 验证代理类型
        if (!isValidProxyType(config.getProxyType())) {
            throw new IllegalArgumentException("Invalid proxy type: " + config.getProxyType() + ". Must be HTTP or SOCKS5");
        }

        // 验证端口范围
        if (config.getServerPort() < 1 || config.getServerPort() > 65535) {
            throw new IllegalArgumentException("Invalid port number: " + config.getServerPort());
        }

        existingConfig.setProxyName(config.getProxyName());
        existingConfig.setProxyType(config.getProxyType());
        existingConfig.setServerHost(config.getServerHost());
        existingConfig.setServerPort(config.getServerPort());
        existingConfig.setStatus(config.getStatus());
        existingConfig.setDescription(config.getDescription());
        existingConfig.setUpdatedTime(LocalDateTime.now());

        return proxyServiceConfigRepository.save(existingConfig);
    }

    /**
     * 删除代理配置
     */
    public void deleteProxyConfig(Long proxyId) {
        ProxyServiceConfig config = getProxyConfigById(proxyId);

        // 检查是否有关联的任务
        Long taskCount = getAssociatedTaskCount(proxyId);
        if (taskCount > 0) {
            throw new IllegalStateException("该代理配置被 " + taskCount + " 个任务使用，无法删除");
        }
        long cexBindingCount = cexProxyBindingService.countActiveBindingsByProxyId(proxyId);
        if (cexBindingCount > 0) {
            throw new IllegalStateException("该代理配置被 " + cexBindingCount + " 个交易所绑定使用，无法删除");
        }

        proxyServiceConfigRepository.delete(config);
    }

    /**
     * 获取代理配置关联的任务数量
     */
    public Long getAssociatedTaskCount(Long proxyId) {
        return proxyServiceConfigRepository.countAssociatedTasks(proxyId);
    }

    /**
     * 获取代理配置关联的任务列表
     */
    public List<ScheduledTask> getAssociatedTasks(Long proxyId) {
        return proxyServiceConfigRepository.findAssociatedTasks(proxyId);
    }

    /**
     * 测试代理连接
     */
    public boolean testProxyConnection(Long proxyId) {
        return testProxyConnectionResult(proxyId).isSuccess();
    }

    /**
     * 测试代理连接
     */
    public boolean testProxyConnection(ProxyServiceConfig config) {
        return testProxyConnectionResult(config).isSuccess();
    }

    public ProxyConnectionTestResult testProxyConnectionResult(Long proxyId) {
        try {
            ProxyServiceConfig config = getProxyConfigById(proxyId);
            return testProxyConnectionResult(config);
        } catch (Exception e) {
            return ProxyConnectionTestResult.fail("获取代理配置失败: " + e.getMessage());
        }
    }

    public ProxyConnectionTestResult testProxyConnectionResult(ProxyServiceConfig config) {
        if (config == null) {
            return ProxyConnectionTestResult.fail("代理配置为空");
        }
        log.info("代理测试开始: proxyId={}, proxyName={}, host={}, port={}, type={}",
                config.getProxyId(), config.getProxyName(), config.getServerHost(), config.getServerPort(), config.getProxyType());
        String sourceInfo = buildSourceInfo();
        log.info("代理测试环境: source={}", sourceInfo);
        String tcpError = probeProxyTcp(config.getServerHost(), config.getServerPort(), tcpProbeTimeoutMs);
        String tcpStatus;
        if (tcpError != null) {
            log.warn("代理测试TCP失败: proxyId={}, host={}, port={}, error={}",
                    config.getProxyId(), config.getServerHost(), config.getServerPort(), tcpError);
            tcpStatus = "[TCP] 预检失败: " + tcpError;
        } else {
            log.info("代理测试TCP成功: proxyId={}, host={}, port={}",
                    config.getProxyId(), config.getServerHost(), config.getServerPort());
            tcpStatus = "[TCP] 预检通过";
        }
        List<String> testUrls = buildTestUrls();
        List<String> errors = new ArrayList<>();
        try {
            Proxy proxy = createProxy(config);

            HttpClient client = HttpClient.newBuilder()
                    .proxy(ProxySelector.of((InetSocketAddress) proxy.address()))
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            for (String url : testUrls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
                    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                    int status = response.statusCode();
                    if (status >= 200 && status < 400) {
                        String suggestion = tcpError == null ? "" : buildLanSuggestion(config);
                        return ProxyConnectionTestResult.success(tcpStatus + "。"
                                + "[HTTP] 代理访问成功，目标: " + url + "，状态码: " + status
                                + "。测试源: " + sourceInfo);
                    }
                    errors.add("目标 " + url + " 返回状态码 " + status);
                } catch (Exception ex) {
                    errors.add("目标 " + url + " 失败: " + classifyException(ex));
                }
            }
            String suggestion = buildProxyTypeSuggestion(config);
            String detail = errors.isEmpty() ? "未知错误" : String.join("；", errors);
            String lanSuggestion = tcpError == null ? "" : buildLanSuggestion(config);
            return ProxyConnectionTestResult.fail(tcpStatus + "。"
                    + "[HTTP] 经代理访问目标失败。"
                    + (suggestion.isEmpty() ? "" : suggestion + "。")
                    + (lanSuggestion.isEmpty() ? "" : "建议: " + lanSuggestion + "。")
                    + " 详情: " + detail
                    + "。测试源: " + sourceInfo);
        } catch (Exception e) {
            return ProxyConnectionTestResult.fail("[HTTP] 代理请求阶段异常: " + classifyException(e)
                    + "。测试源: " + sourceInfo);
        }
    }

    private String buildSourceInfo() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostName() + "/" + localHost.getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private List<String> buildTestUrls() {
        List<String> urls = new ArrayList<>();
        urls.add("https://www.gstatic.com/generate_204");
        urls.add("https://www.baidu.com");
        urls.add("http://example.com");
        return urls;
    }

    private String buildProxyTypeSuggestion(ProxyServiceConfig config) {
        if (config == null) {
            return "";
        }
        if ("SOCKS5".equalsIgnoreCase(config.getProxyType()) && Integer.valueOf(7890).equals(config.getServerPort())) {
            return "检测到 SOCKS5 + 7890，若使用 Clash 常见配置建议改为 HTTP + 7890 或 SOCKS5 + 7891";
        }
        return "";
    }

    private String buildLanSuggestion(ProxyServiceConfig config) {
        if (config == null || config.getServerHost() == null) {
            return "";
        }
        String host = config.getServerHost().trim();
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
            return "检查代理服务是否监听 0.0.0.0，并确认目标主机防火墙已放行端口 " + config.getServerPort()
                    + "；同时确认协议类型为 " + config.getProxyType();
        }
        return "";
    }

    private String probeProxyTcp(String host, Integer port, int timeoutMs) {
        if (host == null || host.trim().isEmpty() || port == null) {
            return "主机或端口为空";
        }
        String normalizedHost = host.trim();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(normalizedHost, port), timeoutMs);
            return null;
        } catch (Exception firstError) {
            List<String> errors = new ArrayList<>();
            errors.add("默认路由: " + classifyException(firstError));
            for (InetAddress localAddress : getLocalIpv4Addresses()) {
                try (Socket socket = new Socket()) {
                    socket.bind(new InetSocketAddress(localAddress, 0));
                    socket.connect(new InetSocketAddress(normalizedHost, port), timeoutMs);
                    log.info("代理TCP探测通过绑定本地地址成功: localAddress={}, target={}:{}", localAddress.getHostAddress(), normalizedHost, port);
                    return null;
                } catch (Exception bindError) {
                    errors.add("绑定" + localAddress.getHostAddress() + ": " + classifyException(bindError));
                }
            }
            return String.join("；", errors);
        }
    }

    private List<InetAddress> getLocalIpv4Addresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取本地IPv4地址失败: {}", e.getMessage());
        }
        return addresses;
    }

    private String classifyException(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ConnectException) {
            return "连接被拒绝，代理端口未监听";
        }
        if (cause instanceof SocketTimeoutException) {
            return "连接超时";
        }
        if (cause instanceof UnknownHostException) {
            return "DNS解析失败";
        }
        if (cause instanceof NoRouteToHostException) {
            return "网络不可达或无路由";
        }
        if (cause instanceof SSLException) {
            return "TLS握手失败";
        }
        return cause.getMessage() == null ? "网络异常" : cause.getMessage();
    }

    public static class ProxyConnectionTestResult {
        private final boolean success;
        private final String message;

        private ProxyConnectionTestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ProxyConnectionTestResult success(String message) {
            return new ProxyConnectionTestResult(true, message);
        }

        public static ProxyConnectionTestResult fail(String message) {
            return new ProxyConnectionTestResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 根据代理配置创建Proxy对象
     */
    public Proxy createProxy(ProxyServiceConfig config) {
        InetSocketAddress address = new InetSocketAddress(config.getServerHost(), config.getServerPort());

        switch (config.getProxyType().toUpperCase()) {
            case "HTTP":
                return new Proxy(Proxy.Type.HTTP, address);
            case "SOCKS5":
                return new Proxy(Proxy.Type.SOCKS, address);
            default:
                throw new IllegalArgumentException("Unsupported proxy type: " + config.getProxyType());
        }
    }

    /**
     * 验证代理类型是否有效
     */
    private boolean isValidProxyType(String proxyType) {
        return "HTTP".equalsIgnoreCase(proxyType) || "SOCKS5".equalsIgnoreCase(proxyType);
    }
}
