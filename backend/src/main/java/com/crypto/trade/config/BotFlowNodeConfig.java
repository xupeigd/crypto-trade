package com.crypto.trade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "bot.flow")
public class BotFlowNodeConfig {
    private boolean enabled = true;
    private Display display = new Display();
    private List<Node> nodes = new ArrayList<>();

    @Data
    public static class Display {
        private int maxDisplayCount = 5;
        private int failedRetentionSeconds = 30;
    }

    @Data
    public static class Node {
        private String code;
        private String name;
        private int orderNo;
        private boolean enabled = true;
    }

    public List<Node> getEnabledNodesSorted() {
        if (nodes == null || nodes.isEmpty()) {
            return defaultNodes();
        }
        List<Node> enabledNodes = nodes.stream()
                .filter(Node::isEnabled)
                .filter(node -> node.getCode() != null && !node.getCode().isBlank())
                .sorted(Comparator.comparingInt(Node::getOrderNo))
                .collect(Collectors.toList());
        return enabledNodes.isEmpty() ? defaultNodes() : enabledNodes;
    }

    private List<Node> defaultNodes() {
        List<Node> defaults = new ArrayList<>();
        defaults.add(buildNode("PROMPT_BUILD", "构建Prompt", 1));
        defaults.add(buildNode("MODEL_CALL", "调用模型", 2));
        defaults.add(buildNode("RISK_CONTROL", "风控审核", 3));
        defaults.add(buildNode("TRADE_ACTION", "执行交易", 4));
        defaults.add(buildNode("COMPLETE", "完成", 5));
        return defaults;
    }

    private Node buildNode(String code, String name, int orderNo) {
        Node node = new Node();
        node.setCode(code);
        node.setName(name);
        node.setOrderNo(orderNo);
        node.setEnabled(true);
        return node;
    }
}
