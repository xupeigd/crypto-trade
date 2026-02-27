package com.crypto.trade.service.signature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * OKXSignatureService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class OKXSignatureService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneId.of("UTC"));

    /**
     * 生成OKX API签名
     *
     * @param timestamp   时间戳
     * @param method      HTTP方法（GET/POST）
     * @param requestPath 请求路径
     * @param body        请求体（GET请求为空字符串）
     * @param secretKey   API密钥
     * @return Base64编码的签名
     */
    public String sign(String timestamp, String method, String requestPath, String body, String secretKey) {
        try {
            // 构建签名字符串：timestamp + method + requestPath + body
            String signString = timestamp + method.toUpperCase() + requestPath + body;

            log.debug("生成OKX签名 - 签名字符串: {}", signString);

            // 使用HMAC-SHA256算法生成签名
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] signatureBytes = mac.doFinal(signString.getBytes(StandardCharsets.UTF_8));

            // Base64编码签名
            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            log.debug("OKX签名生成成功 - 长度: {}", signature.length());
            return signature;

        } catch (NoSuchAlgorithmException e) {
            log.error("HMAC-SHA256算法不可用", e);
            throw new RuntimeException("HMAC-SHA256算法不可用", e);
        } catch (InvalidKeyException e) {
            log.error("无效的密钥格式", e);
            throw new RuntimeException("无效的密钥格式", e);
        } catch (Exception e) {
            log.error("生成签名时发生未知错误", e);
            throw new RuntimeException("生成签名失败", e);
        }
    }

    /**
     * 获取当前时间戳（ISO 8601格式，精确到毫秒）
     *
     * @return ISO 8601格式时间戳字符串
     */
    public String getCurrentTimestamp() {
        return TIMESTAMP_FORMATTER.format(Instant.now());
    }

    /**
     * 验证签名参数
     *
     * @param timestamp   时间戳
     * @param method      HTTP方法
     * @param requestPath 请求路径
     * @param secretKey   API密钥
     * @return 参数是否有效
     */
    public boolean validateSignatureParams(String timestamp, String method, String requestPath, String secretKey) {
        if (null == timestamp || timestamp.trim().isEmpty()) {
            log.warn("时间戳参数为空");
            return false;
        }

        if (null == method || method.trim().isEmpty()) {
            log.warn("HTTP方法参数为空");
            return false;
        }

        if (null == requestPath || requestPath.trim().isEmpty()) {
            log.warn("请求路径参数为空");
            return false;
        }

        if (null == secretKey || secretKey.trim().isEmpty()) {
            log.warn("API密钥参数为空");
            return false;
        }

        try {
            // 验证时间戳格式（ISO 8601）
            java.time.Instant.parse(timestamp);
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("时间戳格式无效: {}", timestamp);
            return false;
        }

        return true;
    }
}