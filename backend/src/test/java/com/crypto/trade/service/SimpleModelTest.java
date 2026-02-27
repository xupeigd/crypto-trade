package com.crypto.trade.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;

import java.util.List;

/**
 * 简单的模型切换测试，不依赖Spring Context
 */
public class SimpleModelTest {

    @Test
    public void testDirectOllamaApi() {
        System.out.println("=== 测试直接调用Ollama API ===");

        try {
            // 创建OllamaApi实例
            OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");

            // 测试模型列表
            System.out.println("\n--- 获取可用模型列表 ---");
            var modelsResponse = ollamaApi.listModels();

            if (modelsResponse != null && modelsResponse.models() != null) {
                System.out.println("可用模型:");
                for (var model : modelsResponse.models()) {
                    System.out.println("- " + model.name() + " (大小: " + model.size() + ")");
                }
            }

            // 测试不同模型的调用
            String[] testModels = {
                    "deepseek-r1:14b",
                    "qwen3:4b"
            };

            String testPrompt = "你好，请简单回答1+1等于几？";

            for (String model : testModels) {
                System.out.println("\n--- 测试模型: " + model + " ---");

                try {
                    // 创建消息
                    OllamaApi.Message message = new OllamaApi.Message(
                            OllamaApi.Message.Role.USER, testPrompt, null, null);

                    // 创建请求
                    OllamaApi.ChatRequest request = new OllamaApi.ChatRequest(
                            model,
                            List.of(message),
                            false, // stream - 必须明确设置
                            null, // format
                            null, // options
                            null, // tools
                            null  // system
                    );

                    // 调用API并计时
                    long startTime = System.currentTimeMillis();
                    OllamaApi.ChatResponse response = ollamaApi.chat(request);
                    long processingTime = System.currentTimeMillis() - startTime;

                    if (response != null && response.message() != null) {
                        String result = response.message().content();
                        System.out.println("✓ 调用成功");
                        System.out.println("处理时间: " + processingTime + "ms");
                        System.out.println("响应内容: " + result.trim());
                    } else {
                        System.out.println("✗ 响应失败: 无响应内容");
                    }

                } catch (Exception e) {
                    System.out.println("✗ 模型 " + model + " 调用失败: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== 测试完成 ===");
    }
}