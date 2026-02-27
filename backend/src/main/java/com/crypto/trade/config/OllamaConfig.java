package com.crypto.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OllamaConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Configuration
@ConfigurationProperties(prefix = "spring.ai.ollama")
public class OllamaConfig {

    private String baseUrl = "http://localhost:11434";
    private Chat chat = new Chat();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public static class Chat {
        private Options options = new Options();

        public Options getOptions() {
            return options;
        }

        public void setOptions(Options options) {
            this.options = options;
        }
    }

    public static class Options {
        private String model = "llama3.1:8b";
        private Double temperature = 0.7;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }
    }
}