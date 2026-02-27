package com.crypto.trade.service;

import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.repository.ProxyServiceConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ProxyServiceConfigService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Service
public class ProxyServiceConfigService {

    @Autowired
    private ProxyServiceConfigRepository proxyServiceConfigRepository;

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
        try {
            ProxyServiceConfig config = getProxyConfigById(proxyId);
            return testProxyConnection(config);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 测试代理连接
     */
    public boolean testProxyConnection(ProxyServiceConfig config) {
        try {
            Proxy proxy = createProxy(config);

            HttpClient client = HttpClient.newBuilder()
                    .proxy(ProxySelector.of((InetSocketAddress) proxy.address()))
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // 尝试连接一个测试URL
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://www.google.com"))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
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