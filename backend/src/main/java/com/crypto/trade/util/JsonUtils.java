package com.crypto.trade.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * JsonUtils
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
public class JsonUtils {

    /**
     * 线程本地ObjectMapper实例，确保线程安全
     */
    private static final ThreadLocal<ObjectMapper> OBJECT_MAPPER_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setVisibility(objectMapper.getVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY));
        objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS)
                .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
        return objectMapper;
    });

    /**
     * 私有构造函数，防止实例化
     */
    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 获取或创建ObjectMapper实例
     *
     * @return ObjectMapper实例
     */
    private static ObjectMapper getOrCreateObjectMapper() {
        return OBJECT_MAPPER_THREAD_LOCAL.get();
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param object 要转换的对象
     * @param <T>    对象类型
     * @return JSON字符串，转换失败时返回"{}"
     */
    public static <T> String toJsonString(T object) {
        if (null == object) {
            return "null";
        }
        try {
            return getOrCreateObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert object to JSON string: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * 将对象转换为JSON字符串，抛出异常版本
     *
     * @param object 要转换的对象
     * @param nul    占位参数，用于方法重载
     * @param <T>    对象类型
     * @return JSON字符串
     * @throws JsonProcessingException 转换异常
     */
    public static <T> String toJsonString(T object, Void nul) throws JsonProcessingException {
        if (null == object) {
            return "null";
        }
        return getOrCreateObjectMapper().writeValueAsString(object);
    }

    /**
     * 将对象转换为指定类型的对象
     *
     * @param instance 源对象
     * @param tClass   目标类型
     * @param <F>      源对象类型
     * @param <T>      目标类型
     * @return 转换后的对象，转换失败时返回null
     */
    public static <F, T> T transform(F instance, Class<T> tClass) {
        if (null == instance) {
            log.warn("Source instance is null");
            return null;
        }
        if (null == tClass) {
            log.warn("Target class is null");
            return null;
        }
        ObjectMapper objectMapper = getOrCreateObjectMapper();
        try {
            String instanceStr = objectMapper.writeValueAsString(instance);
            return objectMapper.readValue(instanceStr, tClass);
        } catch (IOException e) {
            log.warn("Failed to transform object to type {}: {}",
                    tClass.getSimpleName(), e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将对象转换为指定类型的对象，抛出异常版本
     *
     * @param instance 源对象
     * @param tClass   目标类型
     * @param nul      占位参数，用于方法重载
     * @param <F>      源对象类型
     * @param <T>      目标类型
     * @return 转换后的对象
     * @throws IOException              转换异常
     * @throws IllegalArgumentException 参数为空异常
     */
    public static <F, T> T transform(F instance, Class<T> tClass, Void nul) throws IOException {
        if (null == instance) {
            throw new IllegalArgumentException("Source instance cannot be null");
        }
        if (null == tClass) {
            throw new IllegalArgumentException("Target class cannot be null");
        }
        ObjectMapper objectMapper = getOrCreateObjectMapper();
        String instanceStr = objectMapper.writeValueAsString(instance);
        return objectMapper.readValue(instanceStr, tClass);
    }

    /**
     * 将JSON字符串转换为指定类型的对象
     *
     * @param jsonStr JSON字符串
     * @param clazz   目标类型
     * @param <T>     目标类型
     * @return 转换后的对象，转换失败时返回null
     */
    public static <T> T transform(String jsonStr, Class<T> clazz) {
        if (null == jsonStr || jsonStr.trim().isEmpty()) {
            log.warn("JSON string is null or empty");
            return null;
        }
        if (null == clazz) {
            log.warn("Target class is null");
            return null;
        }
        try {
            return getOrCreateObjectMapper().readValue(jsonStr, clazz);
        } catch (IOException e) {
            log.warn("Failed to transform JSON string to object of type {}: {}",
                    clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象
     *
     * @param jsonStr JSON字符串
     * @param clazz   目标类型
     * @param <T>     目标类型
     * @return 转换后的对象，转换失败时返回null
     */
    public static <T> T parseTo(String jsonStr, Class<T> clazz) {
        if (null == jsonStr || jsonStr.trim().isEmpty()) {
            log.warn("JSON string is null or empty");
            return null;
        }
        if (null == clazz) {
            log.warn("Target class is null");
            return null;
        }
        try {
            return getOrCreateObjectMapper().readValue(jsonStr, clazz);
        } catch (IOException e) {
            log.warn("Failed to parse JSON string to object of type {}: {}",
                    clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象，抛出异常版本
     *
     * @param jsonStr JSON字符串
     * @param clazz   目标类型
     * @param nul     占位参数，用于方法重载
     * @param <T>     目标类型
     * @return 转换后的对象
     * @throws IOException              转换异常
     * @throws IllegalArgumentException 参数为空异常
     */
    public static <T> T parseTo(String jsonStr, Class<T> clazz, Void nul) throws IOException {
        if (null == jsonStr || jsonStr.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        if (null == clazz) {
            throw new IllegalArgumentException("Target class cannot be null");
        }
        ObjectMapper objectMapper = getOrCreateObjectMapper();
        return objectMapper.readValue(jsonStr, clazz);
    }

    /**
     * 清理当前线程的ObjectMapper实例
     * 建议在线程结束时调用，避免内存泄漏
     */
    public static void removeObjectMapper() {
        OBJECT_MAPPER_THREAD_LOCAL.remove();
    }

}
