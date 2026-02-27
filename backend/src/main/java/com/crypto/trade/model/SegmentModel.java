package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * SegmentModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentModel {

    /**
     * 段落标题
     */
    private String title;

    /**
     * 段落内容
     */
    private String content;

    /**
     * 内容分类
     */
    private String category;

    /**
     * 显示优先级（数值越小优先级越高）
     */
    @Builder.Default
    private Integer priority = 50;

    /**
     * 是否应该显示标题
     */
    @Builder.Default
    private Boolean showTitle = true;

    /**
     * 额外的元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 创建数据类段落
     *
     * @param title   标题
     * @param content 内容
     * @return SegmentModel
     */
    public static SegmentModel createDataSegment(String title, String content) {
        return SegmentModel.builder()
                .title(title)
                .content(content)
                .category(Category.DATA)
                .priority(Priority.MEDIUM)
                .showTitle(true)
                .build();
    }

    /**
     * 创建分析类段落
     *
     * @param title   标题
     * @param content 内容
     * @return SegmentModel
     */
    public static SegmentModel createAnalysisSegment(String title, String content) {
        return SegmentModel.builder()
                .title(title)
                .content(content)
                .category(Category.ANALYSIS)
                .priority(Priority.MEDIUM)
                .showTitle(true)
                .build();
    }

    /**
     * 创建指令类段落
     *
     * @param title   标题
     * @param content 内容
     * @return SegmentModel
     */
    public static SegmentModel createInstructionSegment(String title, String content) {
        return SegmentModel.builder()
                .title(title)
                .content(content)
                .category(Category.INSTRUCTION)
                .priority(Priority.LOWEST)
                .showTitle(true)
                .build();
    }

    /**
     * 创建思考类段落
     *
     * @param title   标题
     * @param content 内容
     * @return SegmentModel
     */
    public static SegmentModel createThinkingSegment(String title, String content) {
        return SegmentModel.builder()
                .title(title)
                .content(content)
                .category(Category.THINKING)
                .priority(Priority.LOW)
                .showTitle(true)
                .build();
    }

    /**
     * 创建市场类段落
     *
     * @param title   标题
     * @param content 内容
     * @return SegmentModel
     */
    public static SegmentModel createMarketSegment(String title, String content) {
        return SegmentModel.builder()
                .title(title)
                .content(content)
                .category(Category.MARKET)
                .priority(Priority.MEDIUM_LOW)
                .showTitle(true)
                .build();
    }

    /**
     * 创建无标题段落
     *
     * @param content 内容
     * @return SegmentModel
     */
    public static SegmentModel createContentOnly(String content) {
        return SegmentModel.builder()
                .content(content)
                .showTitle(false)
                .build();
    }

    /**
     * 添加元数据
     *
     * @param key   键
     * @param value 值
     * @return 当前实例，支持链式调用
     */
    public SegmentModel addMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 获取元数据
     *
     * @param key 键
     * @param <T> 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    /**
     * 检查是否应该包含在最终Prompt中
     *
     * @return 是否包含
     */
    public boolean shouldInclude() {
        return content != null && !content.trim().isEmpty();
    }

    /**
     * 检查段落是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return content == null || content.trim().isEmpty();
    }

    @Override
    public String toString() {
        if (!shouldInclude()) {
            return "";
        }

        if (!showTitle) {
            return content;
        }

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) {
            sb.append("=== ").append(title).append(" ===\n");
        }
        sb.append(content);

        return sb.toString();
    }

    /**
     * 段落类型常量
     */
    public static class Category {
        public static final String DATA = "DATA";           // 数据类信息（统计、账户、持仓等）
        public static final String ANALYSIS = "ANALYSIS";   // 分析类信息（技术指标、风险评估等）
        public static final String INSTRUCTION = "INSTRUCTION"; // 指令类信息（决策要求、格式规范等）
        public static final String THINKING = "THINKING";   // 思考类信息（思考模式、推理过程等）
        public static final String MARKET = "MARKET";       // 市场类信息（行情数据、概况等）
    }

    /**
     * 段落优先级常量
     */
    public static class Priority {
        public static final int HIGHEST = 10;     // 最高优先级（统计信息）
        public static final int HIGH = 20;        // 高优先级（账户信息）
        public static final int MEDIUM_HIGH = 30; // 中高优先级（持仓信息）
        public static final int MEDIUM = 40;      // 中等优先级（技术指标）
        public static final int MEDIUM_LOW = 50;  // 中低优先级（市场数据）
        public static final int LOW = 60;         // 低优先级（思考模式）
        public static final int LOWEST = 90;      // 最低优先级（决策要求）
    }

    /**
     * 自定义Builder类，支持addMetadata方法
     */
    public static class SegmentModelBuilder {
        private Map<String, Object> metadata = new HashMap<>();
        // Builder字段
        private String title;
        private String content;
        private String category;
        private Integer priority;
        private Boolean showTitle;

        public SegmentModelBuilder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public SegmentModelBuilder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public SegmentModel build() {
            SegmentModel model = new SegmentModel();
            model.title = this.title;
            model.content = this.content;
            model.category = this.category;
            model.priority = this.priority != null ? this.priority : 50;
            model.showTitle = this.showTitle != null ? this.showTitle : true;
            model.metadata = this.metadata != null ? this.metadata : new HashMap<>();
            return model;
        }
    }
}