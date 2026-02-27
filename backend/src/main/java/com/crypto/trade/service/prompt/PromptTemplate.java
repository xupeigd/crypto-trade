//package com.crypto.trade.service.prompt;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
/// **
// * Prompt模板引擎
// * 提供模板变量替换和条件渲染功能
// */
//@Slf4j
//@Component
/**
 * PromptTemplate
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
//public class PromptTemplate {
//
//    // 变量占位符模式
//    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+(?:\\.\\w+)*)\\}\\}");
//
//    // 条件块模式
//    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile(
//            "\\{\\{#if\\s+(\\w+)\\}\\}(.*?)\\{\\{/if\\}\\}",
//            Pattern.DOTALL
//    );
//
//    // 循环块模式
//    private static final Pattern LOOP_PATTERN = Pattern.compile(
//            "\\{\\{#each\\s+(\\w+)\\}\\}(.*?)\\{\\{/each\\}\\}",
//            Pattern.DOTALL
//    );
//
//    // 模板缓存
//    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
//
//    /**
//     * 处理模板，替换变量和执行条件渲染
//     *
//     * @param template  模板字符串
//     * @param variables 变量Map
//     * @return 处理后的字符串
//     */
//    public String processTemplate(String template, Map<String, Object> variables) {
//        if (template == null || template.trim().isEmpty()) {
//            return template;
//        }
//
//        try {
//            String result = template;
//
//            // 处理条件块
//            result = processConditionals(result, variables);
//
//            // 处理循环块
//            result = processLoops(result, variables);
//
//            // 处理变量替换
//            result = processVariables(result, variables);
//
//            return result;
//
//        } catch (Exception e) {
//            log.error("模板处理失败: {}", e.getMessage(), e);
//            return template; // 返回原始模板
//        }
//    }
//
//    /**
//     * 处理变量替换
//     *
//     * @param template  模板字符串
//     * @param variables 变量Map
//     * @return 处理后的字符串
//     */
//    private String processVariables(String template, Map<String, Object> variables) {
//        Matcher matcher = VARIABLE_PATTERN.matcher(template);
//        StringBuffer result = new StringBuffer();
//
//        while (matcher.find()) {
//            String variablePath = matcher.group(1);
//            Object value = getNestedValue(variables, variablePath);
//
//            String replacement = value != null ? value.toString() : "";
//            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
//        }
//
//        matcher.appendTail(result);
//        return result.toString();
//    }
//
//    /**
//     * 处理条件块
//     *
//     * @param template  模板字符串
//     * @param variables 变量Map
//     * @return 处理后的字符串
//     */
//    private String processConditionals(String template, Map<String, Object> variables) {
//        Matcher matcher = CONDITIONAL_PATTERN.matcher(template);
//        StringBuffer result = new StringBuffer();
//
//        while (matcher.find()) {
//            String conditionVar = matcher.group(1);
//            String content = matcher.group(2);
//
//            Object value = variables.get(conditionVar);
//            boolean conditionMet = value != null &&
//                    (value instanceof Boolean ? (Boolean) value : !value.toString().isEmpty());
//
//            if (conditionMet) {
//                matcher.appendReplacement(result, Matcher.quoteReplacement(content));
//            } else {
//                matcher.appendReplacement(result, "");
//            }
//        }
//
//        matcher.appendTail(result);
//        return result.toString();
//    }
//
//    /**
//     * 处理循环块
//     *
//     * @param template  模板字符串
//     * @param variables 变量Map
//     * @return 处理后的字符串
//     */
//    private String processLoops(String template, Map<String, Object> variables) {
//        Matcher matcher = LOOP_PATTERN.matcher(template);
//        StringBuffer result = new StringBuffer();
//
//        while (matcher.find()) {
//            String loopVar = matcher.group(1);
//            String content = matcher.group(2);
//
//            Object value = variables.get(loopVar);
//            StringBuilder loopResult = new StringBuilder();
//
//            if (value instanceof Iterable) {
//                for (Object item : (Iterable<?>) value) {
//                    if (item instanceof Map) {
//                        // 为每个项目创建单独的变量Map
//                        @SuppressWarnings("unchecked")
//                        Map<String, Object> itemVariables = (Map<String, Object>) item;
//                        loopResult.append(processTemplate(content, itemVariables));
//                    } else {
//                        // 简单项，添加到变量Map中
//                        Map<String, Object> itemVariables = Map.of("item", item);
//                        loopResult.append(processTemplate(content, itemVariables));
//                    }
//                }
//            }
//
//            matcher.appendReplacement(result, Matcher.quoteReplacement(loopResult.toString()));
//        }
//
//        matcher.appendTail(result);
//        return result.toString();
//    }
//
//    /**
//     * 获取嵌套对象的值
//     * 支持点号分隔的路径，如 "user.name"
//     *
//     * @param variables 变量Map
//     * @param path      变量路径
//     * @return 值
//     */
//    @SuppressWarnings("unchecked")
//    private Object getNestedValue(Map<String, Object> variables, String path) {
//        String[] parts = path.split("\\.");
//        Object current = variables;
//
//        for (String part : parts) {
//            if (current instanceof Map) {
//                current = ((Map<String, Object>) current).get(part);
//            } else if (current != null) {
//                // 尝试使用反射获取属性值
//                try {
//                    java.lang.reflect.Field field = current.getClass().getField(part);
//                    current = field.get(current);
//                } catch (Exception e) {
//                    // 忽略反射异常
//                    current = null;
//                }
//            } else {
//                return null;
//            }
//
//            if (current == null) {
//                return null;
//            }
//        }
//
//        return current;
//    }
//
//    /**
//     * 缓存模板
//     *
//     * @param key      缓存键
//     * @param template 模板内容
//     */
//    public void cacheTemplate(String key, String template) {
//        templateCache.put(key, template);
//        log.debug("缓存模板: {}", key);
//    }
//
//    /**
//     * 获取缓存的模板
//     *
//     * @param key 缓存键
//     * @return 模板内容，如果不存在则返回null
//     */
//    public String getCachedTemplate(String key) {
//        return templateCache.get(key);
//    }
//
//    /**
//     * 清除模板缓存
//     *
//     * @param key 缓存键，如果为null则清除所有缓存
//     */
//    public void clearCache(String key) {
//        if (key == null) {
//            templateCache.clear();
//            log.debug("清除所有模板缓存");
//        } else {
//            templateCache.remove(key);
//            log.debug("清除模板缓存: {}", key);
//        }
//    }
//
//    /**
//     * 获取缓存大小
//     *
//     * @return 缓存条目数
//     */
//    public int getCacheSize() {
//        return templateCache.size();
//    }
//}