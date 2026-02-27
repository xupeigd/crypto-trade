package com.crypto.trade.service.auth;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DataFetchConfig;

import java.net.http.HttpRequest;

/**
 * AuthService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public interface AuthService {

    /**
     * 检查是否支持指定的鉴权类型
     *
     * @param signatureClass 签名类全限定名
     * @return 是否支持
     */
    boolean supports(String signatureClass);

    /**
     * 为HTTP请求添加鉴权信息
     *
     * @param builder  HTTP请求构建器
     * @param config   数据获取配置
     * @param authInfo 认证密钥信息
     */
    void addAuthHeaders(HttpRequest.Builder builder, DataFetchConfig config, ApiKey authInfo);

    /**
     * 验证鉴权配置是否有效
     *
     * @param config   数据获取配置
     * @param authInfo 认证密钥信息
     * @return 验证结果
     */
    AuthValidationResult validateAuthConfig(DataFetchConfig config, ApiKey authInfo);

    /**
     * 获取鉴权类型标识
     *
     * @return 鉴权类型标识
     */
    String getAuthType();

    /**
     * 鉴权验证结果
     */
    class AuthValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public AuthValidationResult(boolean valid) {
            this(valid, null);
        }

        public AuthValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static AuthValidationResult success() {
            return new AuthValidationResult(true);
        }

        public static AuthValidationResult failure(String errorMessage) {
            return new AuthValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}