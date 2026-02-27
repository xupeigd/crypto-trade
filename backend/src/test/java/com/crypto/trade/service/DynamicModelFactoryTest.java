package com.crypto.trade.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

/**
 * DynamicModelFactory测试
 * 验证模型切换功能是否正常工作
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.ollama.base-url=http://localhost:11434"
})
public class DynamicModelFactoryTest {

    @Test
    public void testModelSwitching() {
        System.out.println("=== 测试DynamicModelFactory模型切换功能 ===");

        // 创建OllamaApi实例
        OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");

        // 测试不同模型的调用
        String[] testModels = {
                "deepseek-r1:14b",
                "qwen3:4b"
        };

        String testPrompt = "你好，请简单介绍一下你自己。";

        for (String model : testModels) {
            try {
                System.out.println("\n--- 测试模型: " + model + " ---");

                // 创建消息
                OllamaApi.Message message = new OllamaApi.Message(
                        OllamaApi.Message.Role.USER, testPrompt, null, null);

                // 创建请求
                OllamaApi.ChatRequest request = new OllamaApi.ChatRequest(
                        model,
                        List.of(message),
                        null, // stream
                        null, // format
                        null, // options
                        null, // tools
                        null  // system
                );

                // 调用API
                long startTime = System.currentTimeMillis();
                OllamaApi.ChatResponse response = ollamaApi.chat(request);
                long processingTime = System.currentTimeMillis() - startTime;

                if (response != null && response.message() != null) {
                    String result = response.message().content();
                    System.out.println("响应成功:");
                    System.out.println("处理时间: " + processingTime + "ms");
                    System.out.println("响应长度: " + result.length() + "字符");
                    System.out.println("响应前100字符: " + result.substring(0, Math.min(100, result.length())));
                } else {
                    System.out.println("响应失败: 无响应内容");
                }

            } catch (Exception e) {
                System.err.println("模型 " + model + " 调用失败: " + e.getMessage());
                if (e.getCause() != null) {
                    System.err.println("根本原因: " + e.getCause().getMessage());
                }
            }
        }

        System.out.println("\n=== 测试完成 ===");
    }

    @Test
    public void testModelAvailability() {
        System.out.println("=== 检查Ollama可用模型 ===");

        try {
            OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");
            var response = ollamaApi.listModels();

            if (response != null && response.models() != null) {
                System.out.println("Ollama中可用的模型:");
                for (var model : response.models()) {
                    System.out.println("- " + model.name() + " (大小: " + model.size() + ")");
                }
            } else {
                System.out.println("无法获取模型列表");
            }

        } catch (Exception e) {
            System.err.println("检查模型可用性失败: " + e.getMessage());
        }

        System.out.println("=== 检查完成 ===");
    }
}