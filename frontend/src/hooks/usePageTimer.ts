import {useCallback, useEffect, useRef, useState} from 'react';

/**
 * 页面定时器配置接口
 */
export interface UsePageTimerOptions {
    /** 定时间隔（毫秒），默认5000 */
    interval?: number;
    /** 是否自动启动，默认true */
    autoStart?: boolean;
    /** 最大重试次数，默认5 */
    maxRetries?: number;
    /** 是否启用定时器，默认true */
    enabled?: boolean;
    /** localStorage存储key，用于持久化状态 */
    persistKey?: string;
    /** 依赖项，变化时重新计时 */
    deps?: React.DependencyList;
    /** 定时器类型，默认为interval */
    timerType?: 'interval' | 'timeout';
}

/**
 * 页面定时器返回值接口
 */
export interface UsePageTimerReturn {
    /** 定时器是否激活 */
    isActive: boolean;
    /** 定时器是否暂停 */
    isPaused: boolean;
    /** 当前重试次数 */
    retryCount: number;
    /** 启动定时器 */
    start: () => void;
    /** 停止定时器 */
    stop: () => void;
    /** 暂停定时器 */
    pause: () => void;
    /** 恢复定时器 */
    resume: () => void;
    /** 重启定时器 */
    restart: () => void;
}

/**
 * 页面定时器Hook
 *
 * 提供统一的定时器管理功能，支持：
 * - 重试机制
 * - 状态持久化
 * - 依赖项触发
 * - 多种定时器类型
 * - 不受页面可见性影响，始终运行
 *
 * @param callback 定时器回调函数
 * @param options 配置选项
 * @returns 定时器控制函数和状态
 *
 * @example
 * ```tsx
 * // 基本使用
 * const { isActive, start, stop } = usePageTimer(() => {
 *   fetchData();
 * }, { interval: 5000 });
 *
 * // 带依赖项使用
 * const { isActive } = usePageTimer(() => {
 *   fetchData(selectedVendor);
 * }, {
 *   interval: 5000,
 *   deps: [selectedVendor],
 *   persistKey: 'my-page-timer'
 * });
 *
 * // 10秒间隔的交易仪表板定时器
 * const { isActive } = usePageTimer(() => {
 *   handleAutoRefresh();
 * }, {
 *   interval: 10000,
 *   persistKey: 'trading-dashboard-timer'
 * });
 * ```
 */
export const usePageTimer = (
    callback: () => Promise<void> | void,
    options: UsePageTimerOptions = {}
): UsePageTimerReturn => {
    // 合并默认配置
    const {
        interval = 5000,
        autoStart = true,
        maxRetries = 5,
        enabled = true,
        persistKey,
        deps = [],
        timerType = 'interval'
    } = options;

    // 状态管理
    const [isActive, setIsActive] = useState(false);
    const [isPaused, setIsPaused] = useState(false);
    const [retryCount, setRetryCount] = useState(0);

    // 引用管理
    const timerRef = useRef<number | null>(null);
    const callbackRef = useRef(callback);
    const retryTimeoutRef = useRef<number | null>(null);

    // 防止循环引用的标志
    const isInitializingRef = useRef(false);

    // 更新回调函数引用
    useEffect(() => {
        callbackRef.current = callback;
    }, [callback]);

    // 清理定时器
    const clearTimer = useCallback(() => {
        if (timerRef.current) {
            if (timerType === 'interval') {
                window.clearInterval(timerRef.current);
            } else {
                window.clearTimeout(timerRef.current);
            }
            timerRef.current = null;
        }
    }, [timerType]);

    // 保存状态到localStorage
    const saveState = useCallback((active: boolean) => {
        if (persistKey) {
            try {
                localStorage.setItem(persistKey, JSON.stringify({
                    wasActive: active,
                    timestamp: Date.now()
                }));
            } catch (error) {
                console.warn(`保存定时器状态到localStorage失败 (${persistKey}):`, error);
            }
        }
    }, [persistKey]);

    // 执行回调函数
    const executeCallback = useCallback(async () => {
        if (!callbackRef.current) return;

        try {
            await callbackRef.current();
            setRetryCount(0); // 成功后重置重试次数
        } catch (error) {
            console.error('定时器回调执行失败:', error);

            // 重试机制
            setRetryCount(currentRetryCount => {
                if (currentRetryCount < maxRetries) {
                    const newRetryCount = currentRetryCount + 1;
                    console.log(`定时器执行失败，${newRetryCount}/${maxRetries} 次重试`);

                    // 使用递增退避策略
                    const retryDelay = Math.min(1000 * Math.pow(2, newRetryCount - 1), 10000);

                    retryTimeoutRef.current = window.setTimeout(() => {
                        // 直接重试，不需要检查isActive，因为这是异步回调
                        executeCallback();
                    }, retryDelay);

                    return newRetryCount;
                } else {
                    console.error(`定时器执行失败，已达到最大重试次数 ${maxRetries}`);
                    return 0;
                }
            });
        }
    }, [maxRetries]);

    // 启动定时器
    const start = useCallback(() => {
        if (!enabled || isInitializingRef.current) return;

        clearTimer();

        setIsActive(true);
        setIsPaused(false);
        setRetryCount(0);
        saveState(true);

        console.log(`启动页面定时器，间隔: ${interval}ms, 类型: ${timerType}`);

        // 立即执行一次回调
        executeCallback();

        // 设置定时器
        if (timerType === 'interval') {
            timerRef.current = window.setInterval(executeCallback, interval);
        } else {
            const scheduleNext = () => {
                timerRef.current = window.setTimeout(() => {
                    executeCallback();
                    // 继续调度下一个执行
                    if (isActive && !isPaused) {
                        scheduleNext();
                    }
                }, interval);
            };
            scheduleNext();
        }
    }, [enabled, interval, timerType, clearTimer, saveState, executeCallback]);

    // 停止定时器
    const stop = useCallback(() => {
        clearTimer();
        if (retryTimeoutRef.current) {
            window.clearTimeout(retryTimeoutRef.current);
            retryTimeoutRef.current = null;
        }

        setIsActive(false);
        setIsPaused(false);
        setRetryCount(0);
        saveState(false);

        console.log('停止页面定时器');
    }, [clearTimer, saveState]);

    // 暂停定时器
    const pause = useCallback(() => {
        if (!isActive) return;

        clearTimer();
        setIsPaused(true);
        console.log('暂停页面定时器');
    }, [isActive, clearTimer]);

    // 恢复定时器
    const resume = useCallback(() => {
        if (!isActive || !isPaused) return;

        setIsPaused(false);

        // 重新启动定时器
        if (timerType === 'interval') {
            timerRef.current = window.setInterval(executeCallback, interval);
        } else {
            const scheduleNext = () => {
                timerRef.current = window.setTimeout(() => {
                    executeCallback();
                    if (isActive && !isPaused) {
                        scheduleNext();
                    }
                }, interval);
            };
            scheduleNext();
        }
        console.log('恢复页面定时器');
    }, [isActive, isPaused, timerType, interval, executeCallback]);

    // 重启定时器
    const restart = useCallback(() => {
        stop();
        // 使用setTimeout避免循环依赖
        setTimeout(() => {
            start();
        }, 0);
    }, [stop, start]);

    // 从localStorage恢复状态 - 简化逻辑
    useEffect(() => {
        if (persistKey && !isInitializingRef.current) {
            try {
                const savedState = localStorage.getItem(persistKey);
                if (savedState) {
                    const {wasActive, timestamp} = JSON.parse(savedState);
                    // 检查状态是否过期（超过1小时）
                    const isExpired = Date.now() - timestamp > 3600000;

                    if (wasActive && !isExpired && autoStart && enabled) {
                        isInitializingRef.current = true;
                        start();
                        setTimeout(() => {
                            isInitializingRef.current = false;
                        }, 100);
                    }
                }
            } catch (error) {
                console.warn(`从localStorage恢复定时器状态失败 (${persistKey}):`, error);
            }
        }
    }, [persistKey, autoStart, enabled, start]);

    // 处理依赖项变化 - 简化逻辑
    useEffect(() => {
        if (isActive && deps.length > 0) {
            // 停止当前定时器，延迟重启避免循环
            const timer = setTimeout(() => {
                restart();
            }, 100);
            return () => clearTimeout(timer);
        }
    }, deps);

    // 处理enabled变化 - 移除循环依赖
    useEffect(() => {
        if (enabled && !isActive && autoStart && !isInitializingRef.current) {
            start();
        } else if (!enabled && isActive) {
            stop();
        }
    }, [enabled, autoStart]);

    // 自动启动 - 简化逻辑
    useEffect(() => {
        if (autoStart && enabled && !isActive) {
            // 延迟启动，避免组件初始化时的问题
            const timer = setTimeout(() => {
                start();
            }, 100);

            return () => {
                clearTimeout(timer);
            };
        }
    }, [autoStart, enabled, start]);

    return {
        isActive,
        isPaused,
        retryCount,
        start,
        stop,
        pause,
        resume,
        restart
    };
};

export default usePageTimer;