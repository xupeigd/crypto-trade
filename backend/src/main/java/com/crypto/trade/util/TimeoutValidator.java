package com.crypto.trade.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * TimeoutValidator
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Component
public class TimeoutValidator {

    /**
     * 检查任务是否已超时
     *
     * @param triggerTime    任务触发时间
     * @param timeoutSeconds 超时时间（秒）
     * @return 如果已超时返回true，否则返回false
     */
    public boolean isTimeout(LocalDateTime triggerTime, Integer timeoutSeconds) {
        if (null == triggerTime || null == timeoutSeconds || timeoutSeconds <= 0) {
            return false;
        }
        LocalDateTime timeoutTime = triggerTime.plusSeconds(timeoutSeconds);
        return LocalDateTime.now().isAfter(timeoutTime);
    }

    /**
     * 检查任务是否即将超时
     *
     * @param triggerTime    任务触发时间
     * @param timeoutSeconds 超时时间（秒）
     * @param warningSeconds 警告时间（秒）
     * @return 如果即将超时返回true，否则返回false
     */
    public boolean isAboutToTimeout(LocalDateTime triggerTime, Integer timeoutSeconds, int warningSeconds) {
        if (null == triggerTime || null == timeoutSeconds || timeoutSeconds <= 0) {
            return false;
        }
        LocalDateTime warningTime = triggerTime.plusSeconds(timeoutSeconds - warningSeconds);
        return LocalDateTime.now().isAfter(warningTime);
    }

    /**
     * 计算剩余时间（秒）
     *
     * @param triggerTime    任务触发时间
     * @param timeoutSeconds 超时时间（秒）
     * @return 剩余时间（秒），如果已超时返回负数
     */
    public long getRemainingSeconds(LocalDateTime triggerTime, Integer timeoutSeconds) {
        if (null == triggerTime || null == timeoutSeconds || timeoutSeconds <= 0) {
            return -1;
        }
        LocalDateTime timeoutTime = triggerTime.plusSeconds(timeoutSeconds);
        return java.time.Duration.between(LocalDateTime.now(), timeoutTime).getSeconds();
    }
}