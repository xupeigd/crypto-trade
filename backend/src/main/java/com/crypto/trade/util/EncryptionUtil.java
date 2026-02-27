package com.crypto.trade.util;

import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * EncryptionUtil
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Service
@Component
public class EncryptionUtil {

    // 简单的Base64编码/解码，用于演示目的
    // 在生产环境中应该使用真正的加密库

    public String encrypt(String plainText) {
        if (null == plainText || plainText.trim().isEmpty()) {
            return plainText;
        }
        return Base64.getEncoder().encodeToString(plainText.getBytes());
    }

    public String decrypt(String encryptedText) {
        if (null == encryptedText || encryptedText.trim().isEmpty()) {
            return encryptedText;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            return new String(decodedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt text", e);
        }
    }

    public String generateApiKey() {
        return Base64.getEncoder().encodeToString(
                KeyGenerators.secureRandom(32).generateKey()
        ).substring(0, 32);
    }

    public String generateSecretKey() {
        return Base64.getEncoder().encodeToString(
                KeyGenerators.secureRandom(64).generateKey()
        ).substring(0, 64);
    }

}