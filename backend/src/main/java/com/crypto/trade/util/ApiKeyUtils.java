package com.crypto.trade.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * ApiKeyUtils
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class ApiKeyUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";
    private static final int KEY_LENGTH = 256;
    private static final int MASK_LENGTH = 8;

    @Value("${app.api-key.encryption.key:default-encryption-key-32-chars-long}")
    private String encryptionKey;

    /**
     * 生成随机密钥（用于初始化）
     */
    public static String generateRandomKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("生成随机密钥失败", e);
            throw new RuntimeException("生成随机密钥失败", e);
        }
    }

    /**
     * 验证密钥格式
     */
    public static boolean isValidKeyFormat(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }

        // 检查是否为Base64格式
        try {
            Base64.getDecoder().decode(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 加密API密钥
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("API密钥加密失败", e);
            throw new RuntimeException("API密钥加密失败", e);
        }
    }

    /**
     * 解密API密钥
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("API密钥解密失败", e);
            // 如果解密失败，可能已经是明文，直接返回
            return encryptedText;
        }
    }

    /**
     * 脱敏显示API密钥
     * 规则：显示前4位和后4位，中间用*代替
     */
    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return apiKey;
        }

        if (apiKey.length() <= MASK_LENGTH) {
            return "*".repeat(apiKey.length());
        }

        String maskedPart = "*".repeat(apiKey.length() - MASK_LENGTH);
        return apiKey.substring(0, 4) + maskedPart + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 检查是否为加密的密钥
     */
    public boolean isEncrypted(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        try {
            // 尝试解密，如果成功则认为是加密的
            String decrypted = decrypt(apiKey);
            return !decrypted.equals(apiKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取密钥规范
     */
    private SecretKeySpec getSecretKey() {
        // 确保密钥长度为32字节（256位）
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));

        // 如果密钥不足32字节，用0填充
        for (int i = keyBytes.length; i < 32; i++) {
            key[i] = 0;
        }

        return new SecretKeySpec(key, ALGORITHM);
    }
}