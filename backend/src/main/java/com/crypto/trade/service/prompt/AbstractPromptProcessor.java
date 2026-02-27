package com.crypto.trade.service.prompt;

import com.crypto.trade.model.SegmentModel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * AbstractPromptProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
public abstract class AbstractPromptProcessor
        implements PromptProcessor {

    protected Map<String, Object> parameters = Map.of();

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * 获取处理器参数值
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @param <T>          参数类型
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    protected <T> T getParameter(String key, T defaultValue) {
        return (T) parameters.getOrDefault(key, defaultValue);
    }

    /**
     * 获取布尔类型参数
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected boolean getBooleanParameter(String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 获取整数类型参数
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected int getIntParameter(String key, int defaultValue) {
        Object value = parameters.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("无法解析整数参数: {} = {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取字符串类型参数
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected String getStringParameter(String key, String defaultValue) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 格式化BigDecimal数值
     *
     * @param value 数值
     * @return 格式化后的字符串
     */
    protected String formatBigDecimal(java.math.BigDecimal value) {
        if (null == value) {
            return "0.00";
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 安全地执行处理操作，统一异常处理
     *
     * @param context   处理上下文
     * @param operation 要执行的操作
     * @return 处理结果
     * @throws PromptProcessException 处理异常
     */
    protected SegmentModel safeProcess(PromptContext context, ProcessingOperation operation) throws PromptProcessException {
        String processorName = getName();
        log.debug("开始执行Prompt处理器: {}", processorName);

        try {
            if (!shouldExecute(context)) {
                log.debug("处理器 {} 不满足执行条件，跳过", processorName);
                return SegmentModel.builder()
                        .title(getName())
                        .content("")
                        .category(SegmentModel.Category.DATA)
                        .priority(getPriority())
                        .showTitle(false)
                        .build();
            }

            SegmentModel result = operation.execute();
            log.debug("处理器 {} 执行成功，生成内容长度: {}", processorName,
                    result.getContent() != null ? result.getContent().length() : 0);
            return result;

        } catch (PromptProcessException e) {
            log.error("处理器 {} 执行失败: {}", processorName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("处理器 {} 执行发生异常", processorName, e);
            throw new PromptProcessException(
                    "处理器执行失败: " + e.getMessage(),
                    e,
                    processorName,
                    PromptProcessException.ErrorCodes.PROCESSING_FAILED
            );
        }
    }

    /**
     * 默认情况下总是执行
     */
    @Override
    public boolean shouldExecute(PromptContext context) {
        return true;
    }

    /**
     * 处理操作函数式接口
     */
    @FunctionalInterface
    protected interface ProcessingOperation {
        SegmentModel execute() throws Exception;
    }
}