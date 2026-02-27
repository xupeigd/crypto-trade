package com.crypto.trade.service.auth;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DataFetchConfig;
import com.crypto.trade.service.signature.OKXSignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.http.HttpRequest;
import java.util.Objects;

/**
 * OKXAuthService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class OKXAuthService
        implements AuthService {

    private static final String OKX_SIGNATURE_CLASS = "com.crypto.trade.service.signature.OKXSignatureService";

    @Autowired
    OKXSignatureService okxSignatureService;

    @Override
    public boolean supports(String signatureClass) {
        return OKX_SIGNATURE_CLASS.equals(signatureClass);
    }

    @Override
    public void addAuthHeaders(HttpRequest.Builder builder, DataFetchConfig config, ApiKey authInfo) {
        try {
            log.debug("添加OKX鉴权头 - API Key: {}", maskApiKey(authInfo.getAccessKey()));

            String signPath = builder.build().uri().toString().replace(config.getCexBaseUrl(), "");

            // 获取请求体用于签名
            String requestBody = getRequestBodyForSigning(config);

            // 生成时间戳
            String timestamp = okxSignatureService.getCurrentTimestamp();

            // 生成签名
            String signature = okxSignatureService.sign(
                    timestamp,
                    config.getHttpMethod(),
                    signPath,
                    requestBody,
                    authInfo.getSecretKey()
            );

            // 设置OKX认证头
            builder.header("OK-ACCESS-KEY", authInfo.getAccessKey());
            builder.header("OK-ACCESS-SIGN", signature);
            builder.header("OK-ACCESS-TIMESTAMP", timestamp);
            builder.header("OK-ACCESS-PASSPHRASE", authInfo.getPassPhrase());
            if (!Objects.equals(true, authInfo.getIsLiveTrading())) {
                builder.header("x-simulated-trading", "1");
            }

            log.debug("OKX鉴权头设置完成 - 时间戳: {}, 签名长度: {}", timestamp, signature.length());

        } catch (Exception e) {
            log.error("设置OKX鉴权头失败: {}", e.getMessage(), e);
            throw new RuntimeException("设置OKX鉴权头失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AuthValidationResult validateAuthConfig(DataFetchConfig config, ApiKey authInfo) {
        if (null == authInfo) {
            return AuthValidationResult.failure("认证密钥信息为空");
        }

        if (authInfo.getAccessKey() == null || authInfo.getAccessKey().trim().isEmpty()) {
            return AuthValidationResult.failure("Access Key为空");
        }

        if (authInfo.getSecretKey() == null || authInfo.getSecretKey().trim().isEmpty()) {
            return AuthValidationResult.failure("Secret Key为空");
        }

        if (authInfo.getPassPhrase() == null || authInfo.getPassPhrase().trim().isEmpty()) {
            return AuthValidationResult.failure("Pass Phrase为空");
        }

        // 验证签名服务参数
        if (!okxSignatureService.validateSignatureParams(
                okxSignatureService.getCurrentTimestamp(),
                config.getHttpMethod(),
                config.getApiPath(),
                authInfo.getSecretKey())) {
            return AuthValidationResult.failure("签名参数验证失败");
        }

        return AuthValidationResult.success();
    }

    @Override
    public String getAuthType() {
        return "OKX";
    }

    /**
     * 获取用于签名的请求体
     */
    private String getRequestBodyForSigning(DataFetchConfig config) {
        if ("GET".equalsIgnoreCase(config.getHttpMethod())) {
            return "";
        }

        // 对于POST/PUT请求，使用配置的请求参数
        if (config.getRequestParams() != null && !config.getRequestParams().trim().isEmpty()) {
            return config.getRequestParams();
        }

        return "";
    }

    /**
     * 掩码API Key用于日志输出
     */
    private String maskApiKey(String apiKey) {
        if (null == apiKey || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }

}