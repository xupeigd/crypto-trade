package com.crypto.trade;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DataFetchConfig;
import com.crypto.trade.entity.ScheduledTask;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.repository.DataFetchConfigRepository;
import com.crypto.trade.repository.ScheduledTaskRepository;
import com.crypto.trade.service.data.ApiConfigService;
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
public class DataFetchConfigIntegrationTest {

    @Autowired
    private ApiConfigService apiConfigService;

    @Autowired
    private DataFetchConfigRepository dataFetchConfigRepository;

    @Autowired
    private ScheduledTaskRepository scheduledTaskRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    public void testCreateAndRetrieveDataFetchConfig() {
        // 创建测试任务
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("数据获取配置测试任务");
        task.setTaskType("data_fetch");
        task.setCronExpression("0 */5 * * * ?");
        task.setStatus("active");
        task.setParentTaskId(0L);
        ScheduledTask savedTask = scheduledTaskRepository.save(task);

        // 创建数据获取配置
        DataFetchConfig config = new DataFetchConfig();
        config.setTaskId(savedTask.getTaskId());
        config.setCexBaseUrl("https://api.binance.com");
        config.setApiPath("/api/v3/ticker/price");
        config.setHttpMethod("GET");
        config.setRequiresAuth(false);
        config.setDataProcessorClass("com.crypto.trade.impl.processor.MarketDataProcessor");
        config.setTargetDuckdbTable("market_data");
        config.setSignatureClass("");

        // 保存配置
        DataFetchConfig savedConfig = dataFetchConfigRepository.save(config);

        // 验证保存成功
        assertNotNull(savedConfig.getConfigId());
        assertEquals(savedTask.getTaskId(), savedConfig.getTaskId());
        assertEquals("https://api.binance.com", savedConfig.getCexBaseUrl());

        // 获取所有配置
        List<DataFetchConfig> allConfigs = apiConfigService.getAllConfigs();
        assertFalse(allConfigs.isEmpty());

        // 通过任务ID获取配置
        DataFetchConfig configByTaskId = apiConfigService.getConfigByTaskId(savedTask.getTaskId());
        assertNotNull(configByTaskId);
        assertEquals(savedTask.getTaskId(), configByTaskId.getTaskId());
    }

    @Test
    public void testDataFetchConfigWithAuth() {
        // 创建测试任务
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("需要鉴权的配置测试");
        task.setTaskType("data_fetch");
        task.setCronExpression("0 */5 * * * ?");
        task.setStatus("active");
        task.setParentTaskId(0L);
        ScheduledTask savedTask = scheduledTaskRepository.save(task);

        // 创建API Key
        ApiKey apiKey = new ApiKey();
        apiKey.setCexName("binance");
        apiKey.setAccessKey("auth-test-key");
        apiKey.setSecretKey("auth-test-secret");
        apiKey.setStatus("active");
        ApiKey savedKey = apiKeyRepository.save(apiKey);

        // 创建需要鉴权的配置
        DataFetchConfig config = new DataFetchConfig();
        config.setTaskId(savedTask.getTaskId());
        config.setCexBaseUrl("https://api.binance.com");
        config.setApiPath("/api/v3/account");
        config.setHttpMethod("GET");
        config.setRequiresAuth(true);
        config.setAuthKeyId(savedKey.getKeyId());
        config.setDataProcessorClass("com.crypto.trade.impl.processor.AccountDataProcessor");
        config.setTargetDuckdbTable("account_data");
        config.setSignatureClass("com.crypto.trade.impl.signature.BinanceSignatureService");

        DataFetchConfig savedConfig = dataFetchConfigRepository.save(config);

        // 验证配置
        assertNotNull(savedConfig.getConfigId());
        assertTrue(savedConfig.getRequiresAuth());
        assertEquals(savedKey.getKeyId(), savedConfig.getAuthKeyId());

        // 获取需要鉴权的配置
        List<DataFetchConfig> authRequiredConfigs = apiConfigService.getAuthRequiredConfigs();
        assertFalse(authRequiredConfigs.isEmpty());
        assertTrue(authRequiredConfigs.get(0).getRequiresAuth());
    }

    @Test
    public void testGetConfigsByCexName() {
        // 创建多个配置
        ScheduledTask task1 = new ScheduledTask();
        task1.setTaskName("币安配置1");
        task1.setTaskType("data_fetch");
        task1.setCronExpression("0 */5 * * * ?");
        task1.setStatus("active");
        task1.setParentTaskId(0L);
        ScheduledTask savedTask1 = scheduledTaskRepository.save(task1);

        DataFetchConfig config1 = new DataFetchConfig();
        config1.setTaskId(savedTask1.getTaskId());
        config1.setCexBaseUrl("https://api.binance.com");
        config1.setApiPath("/api/v3/ticker/price");
        config1.setHttpMethod("GET");
        config1.setRequiresAuth(false);
        config1.setDataProcessorClass("com.crypto.trade.impl.processor.MarketDataProcessor");
        config1.setTargetDuckdbTable("market_data");
        dataFetchConfigRepository.save(config1);

        ScheduledTask task2 = new ScheduledTask();
        task2.setTaskName("币安配置2");
        task2.setTaskType("data_fetch");
        task2.setCronExpression("0 */10 * * * ?");
        task2.setStatus("active");
        task2.setParentTaskId(0L);
        ScheduledTask savedTask2 = scheduledTaskRepository.save(task2);

        DataFetchConfig config2 = new DataFetchConfig();
        config2.setTaskId(savedTask2.getTaskId());
        config2.setCexBaseUrl("https://api.binance.com");
        config2.setApiPath("/api/v3/klines");
        config2.setHttpMethod("GET");
        config2.setRequiresAuth(false);
        config2.setDataProcessorClass("com.crypto.trade.impl.processor.KlineDataProcessor");
        config2.setTargetDuckdbTable("kline_data");
        dataFetchConfigRepository.save(config2);

        // 按交易所名称获取配置
        List<DataFetchConfig> binanceConfigs = apiConfigService.getConfigsByCexName("binance");
        assertEquals(2, binanceConfigs.size());
        for (DataFetchConfig config : binanceConfigs) {
            assertTrue(config.getCexBaseUrl().contains("binance"));
        }
    }
}