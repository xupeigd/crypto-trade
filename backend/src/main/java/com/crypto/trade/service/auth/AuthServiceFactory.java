package com.crypto.trade.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AuthServiceFactory
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class AuthServiceFactory {

    private final List<AuthService> authServices;

    @Autowired
    public AuthServiceFactory(List<AuthService> authServices) {
        this.authServices = authServices;
        log.debug("注册的鉴权服务数量: {}", authServices.size());
        for (AuthService service : authServices) {
            log.debug("注册鉴权服务: {}", service.getAuthType());
        }
    }

    /**
     * 根据签名类名称获取对应的鉴权服务
     *
     * @param signatureClass 签名类全限定名
     * @return 对应的鉴权服务，如果找不到则返回无鉴权服务
     */
    public AuthService getAuthService(String signatureClass) {
        log.debug("查找鉴权服务 - 签名类: {}", signatureClass);

        Optional<AuthService> authService = authServices.stream()
                .filter(service -> service.supports(signatureClass))
                .findFirst();

        if (authService.isPresent()) {
            AuthService service = authService.get();
            log.debug("找到鉴权服务: {}", service.getAuthType());
            return service;
        } else {
            log.warn("未找到匹配的鉴权服务，使用无鉴权模式 - 签名类: {}", signatureClass);
            // 返回无鉴权服务作为fallback
            return getNoAuthService();
        }
    }

    /**
     * 获取无鉴权服务
     */
    private AuthService getNoAuthService() {
        return authServices.stream()
                .filter(service -> service instanceof NoAuthServiceImpl)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("无鉴权服务未注册"));
    }

    /**
     * 检查指定的鉴权类型是否可用
     *
     * @param signatureClass 签名类全限定名
     * @return 是否可用
     */
    public boolean isAuthTypeSupported(String signatureClass) {
        return authServices.stream()
                .anyMatch(service -> service.supports(signatureClass));
    }

    /**
     * 获取所有支持的鉴权类型
     *
     * @return 支持的鉴权类型列表
     */
    public List<String> getSupportedAuthTypes() {
        return authServices.stream()
                .map(AuthService::getAuthType)
                .toList();
    }
}