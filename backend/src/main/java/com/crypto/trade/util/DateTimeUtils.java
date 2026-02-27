package com.crypto.trade.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DateTimeUtils
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
public class DateTimeUtils {

    /**
     * UTC+8 时区
     */
    private static final ZoneId UTC_PLUS_8_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 标准日期时间格式
     */
    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取当前 UTC+8 时间
     *
     * @return LocalDateTime 当前时间的 UTC+8 表示
     */
    public static LocalDateTime nowUtc8() {
        return LocalDateTime.now(UTC_PLUS_8_ZONE);
    }

    /**
     * 获取当前 UTC+8 时间戳（毫秒）
     *
     * @return long 当前 UTC+8 时间的毫秒时间戳
     */
    public static long nowUtc8Timestamp() {
        return ZonedDateTime.now(UTC_PLUS_8_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 将本地时间转换为 UTC+8 时间
     *
     * @param localDateTime 本地时间
     * @return LocalDateTime UTC+8 时间
     */
    public static LocalDateTime toUtc8(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(UTC_PLUS_8_ZONE).toLocalDateTime();
    }

    /**
     * 将 UTC+8 时间转换为时间戳（毫秒）
     *
     * @param utc8DateTime UTC+8 时间
     * @return long 毫秒时间戳
     */
    public static long toTimestamp(LocalDateTime utc8DateTime) {
        if (utc8DateTime == null) {
            return 0;
        }
        return utc8DateTime.atZone(UTC_PLUS_8_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 将时间戳转换为 UTC+8 时间
     *
     * @param timestamp 毫秒时间戳
     * @return LocalDateTime UTC+8 时间
     */
    public static LocalDateTime fromTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                UTC_PLUS_8_ZONE
        );
    }

    /**
     * 格式化时间为标准字符串（UTC+8）
     *
     * @param utc8DateTime UTC+8 时间
     * @return String 格式化的时间字符串
     */
    public static String formatUtc8(LocalDateTime utc8DateTime) {
        if (utc8DateTime == null) {
            return null;
        }
        return utc8DateTime.format(STANDARD_FORMATTER);
    }

    /**
     * 获取当前时间的格式化字符串（UTC+8）
     *
     * @return String 格式化的当前时间字符串
     */
    public static String formatNowUtc8() {
        return formatUtc8(nowUtc8());
    }

    /**
     * 验证时间是否为 UTC+8 时区
     * （此方法主要用于调试和验证）
     *
     * @param dateTime 要验证的时间
     * @return boolean 是否符合 UTC+8 时区期望
     */
    public static boolean isValidUtc8(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        // 简单验证：检查时间是否在合理范围内
        LocalDateTime now = nowUtc8();
        return !dateTime.isAfter(now.plusMinutes(1)) && !dateTime.isBefore(now.minusYears(1));
    }

    /**
     * 获取时区信息（用于日志和调试）
     *
     * @return String 当前使用的时区信息
     */
    public static String getTimeZoneInfo() {
        return String.format("UTC+8 Zone: %s, System Zone: %s",
                UTC_PLUS_8_ZONE, ZoneId.systemDefault());
    }
}