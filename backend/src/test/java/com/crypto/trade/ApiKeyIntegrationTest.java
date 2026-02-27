package com.crypto.trade;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.service.cex.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ApiKeyIntegrationTest {

    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    ApiKeyRepository apiKeyRepository;

    @Test
    public void testCreateAndRetrieveCexKey() {
        // 创建测试数据
        ApiKey apiKey = new ApiKey();
        apiKey.setCexName("binance");
        apiKey.setAccessKey("test-api-key-123");
        apiKey.setSecretKey("test-secret-key-456");
        apiKey.setPassPhrase("test-passphrase");
        apiKey.setStatus("active");
        apiKey.setDescription("测试API Key");

        // 保存API Key
        ApiKey savedKey = apiKeyRepository.save(apiKey);

        // 验证保存成功
        assertNotNull(savedKey.getKeyId());
        assertEquals("binance", savedKey.getCexName());
        assertEquals("test-api-key-123", savedKey.getAccessKey());

        // 验证加密存储
        assertNotEquals("test-secret-key-456", savedKey.getSecretKey());

        // 获取所有API Key
        List<ApiKey> allKeys = apiKeyService.getAllKeys();
        assertFalse(allKeys.isEmpty());

        // 通过ID获取
        ApiKey retrievedKey = apiKeyRepository.findById(savedKey.getKeyId()).orElse(null);
        assertNotNull(retrievedKey);
        assertEquals(savedKey.getKeyId(), retrievedKey.getKeyId());
    }

    @Test
    public void testUpdateCexKeyStatus() {
        // 创建测试数据
        ApiKey apiKey = new ApiKey();
        apiKey.setCexName("okx");
        apiKey.setAccessKey("okx-api-key");
        apiKey.setSecretKey("okx-secret-key");
        apiKey.setStatus("active");
        apiKey.setDescription("OKX测试Key");

        ApiKey savedKey = apiKeyRepository.save(apiKey);

        // 更新状态
        apiKeyService.updateKeyStatus(savedKey.getKeyId(), "inactive");

        // 验证状态更新
        ApiKey updatedKey = apiKeyRepository.findById(savedKey.getKeyId()).orElse(null);
        assertEquals("inactive", updatedKey.getStatus());
    }

    @Test
    public void testGetActiveKeys() {
        // 创建活跃和禁用的Key
        ApiKey activeKey = new ApiKey();
        activeKey.setCexName("huobi");
        activeKey.setAccessKey("huobi-active");
        activeKey.setSecretKey("huobi-secret");
        activeKey.setStatus("active");
        apiKeyRepository.save(activeKey);

        ApiKey inactiveKey = new ApiKey();
        inactiveKey.setCexName("huobi");
        inactiveKey.setAccessKey("huobi-inactive");
        inactiveKey.setSecretKey("huobi-secret2");
        inactiveKey.setStatus("inactive");
        apiKeyRepository.save(inactiveKey);

        // 获取活跃Key
        List<ApiKey> activeKeys = apiKeyService.getActiveKeys();
        assertEquals(1, activeKeys.size());
        assertEquals("active", activeKeys.get(0).getStatus());
    }
}