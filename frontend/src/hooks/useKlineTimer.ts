import {useCallback, useEffect, useRef, useState} from 'react';

// 时间帧与更新间隔的映射配置（单位：毫秒）
export const TIMEFRAME_INTERVALS = {
    '1m': 59 * 1000,       // 59秒
    '5m': 299 * 1000,      // 4分59秒
    '1h': 3599 * 1000,     // 59分59秒
    '5h': 5 * 3599 * 1000, // 4小时59分59秒
    '1d': 86399 * 1000     // 23小时59分59秒
};

/**
 * 计算下次K线生成时间（每分钟的10秒时刻）
 * @param timeframe 时间帧
 * @param currentTime 当前时间
 * @returns 下次更新时间
 */
function calculateNextKlineTime(timeframe: string, currentTime: Date = new Date()): Date {
    console.log('🧮 [calculateNextKlineTime] 计算时间:', {
        timeframe,
        inputTime: currentTime.toLocaleTimeString()
    });
    const next = new Date(currentTime);

    switch (timeframe) {
        case '1m':
            // 下分钟的10秒
            next.setMinutes(next.getMinutes() + 1, 10, 0);
            break;
        case '5m':
            // 下一个5分钟间隔的10秒 (如 5, 10, 15...)
            const currentMin = next.getMinutes();
            const next5Min = Math.floor(currentMin / 5) * 5 + 5;
            if (next5Min >= 60) {
                next.setHours(next.getHours() + 1, 0, 10, 0);
            } else {
                next.setMinutes(next5Min, 10, 0);
            }
            break;
        case '1h':
            // 下小时的10秒
            next.setHours(next.getHours() + 1, 0, 10, 0);
            break;
        case '5h':
            // 下一个5小时间隔的10秒
            const currentHour5h = next.getHours();
            const next5Hour = Math.floor(currentHour5h / 5) * 5 + 5;
            if (next5Hour >= 24) {
                next.setDate(next.getDate() + 1);
                next.setHours(0, 0, 10, 0);
            } else {
                next.setHours(next5Hour, 0, 10, 0);
            }
            break;
        case '1d':
            // 明天的00:00:10
            next.setDate(next.getDate() + 1);
            next.setHours(0, 0, 10, 0);
            break;
        default:
            // 默认按1分钟处理
            next.setMinutes(next.getMinutes() + 1, 10, 0);
            break;
    }

    return next;
}

export interface UseKlineTimerOptions {
    timeframe: string;
    onRefresh: () => Promise<void> | void;
    enabled?: boolean;
    onError?: (error: Error) => void;
}

export interface UseKlineTimerReturn {
    isActive: boolean;
    lastUpdateTime: Date | null;
    nextUpdateTime: Date | null;
    pause: () => void;
    resume: () => void;
    toggle: () => void;
    timeUntilNextUpdate: number;
}

/**
 * 简化版K线图定时更新Hook
 * 专注于定时器核心功能，避免复杂的依赖管理
 */
export function useKlineTimer({
                                  timeframe,
                                  onRefresh,
                                  enabled = true,
                                  onError
                              }: UseKlineTimerOptions): UseKlineTimerReturn {
    const timerRef = useRef<number | null>(null);
    const countdownRef = useRef<number | null>(null);
    const pendingTimerRef = useRef<number | null>(null); // 追踪延迟定时器
    const isActiveRef = useRef<boolean>(enabled);
    const lastUpdateTimeRef = useRef<Date | null>(null);
    const nextUpdateTimeRef = useRef<Date | null>(null);
    const [timeUntilNextUpdate, setTimeUntilNextUpdate] = useState(0);

    // 添加时间帧变化追踪
    const lastTimeframeRef = useRef<string>(timeframe);

    // 使用稳定的ref存储回调，避免依赖变化
    const onRefreshRef = useRef(onRefresh);
    const onErrorRef = useRef(onError);

    // 更新ref引用
    useEffect(() => {
        onRefreshRef.current = onRefresh;
    }, [onRefresh]);

    useEffect(() => {
        onErrorRef.current = onError;
    }, [onError]);


    // 清理所有定时器
    const clearTimers = useCallback(() => {
        console.log('🧹 [useKlineTimer] 清理所有定时器');

        if (timerRef.current) {
            window.clearTimeout(timerRef.current);
            timerRef.current = null;
        }
        if (countdownRef.current) {
            window.clearInterval(countdownRef.current);
            countdownRef.current = null;
        }
        if (pendingTimerRef.current) {
            window.clearTimeout(pendingTimerRef.current);
            pendingTimerRef.current = null;
        }
        // 重置倒计时状态
        setTimeUntilNextUpdate(0);
    }, []);

    // 更新倒计时显示
    const updateCountdown = useCallback(() => {
        if (nextUpdateTimeRef.current && isActiveRef.current) {
            const now = Date.now();
            const timeUntil = Math.max(0, nextUpdateTimeRef.current.getTime() - now);
            setTimeUntilNextUpdate(timeUntil);
        }
    }, []);

    // 执行数据刷新 - 优化状态同步
    const performRefresh = useCallback(async () => {
        try {
            const now = Date.now();
            const timeUntil = nextUpdateTimeRef.current ? nextUpdateTimeRef.current.getTime() - now : 0;

            // 优化状态同步：立即同步显示状态
            setTimeUntilNextUpdate(Math.max(0, timeUntil));

            console.log('🔄 [useKlineTimer] 开始执行数据刷新:', {
                nextUpdateTime: nextUpdateTimeRef.current?.toLocaleTimeString(),
                timeUntil: Math.round(timeUntil / 1000) + 's',
                currentTime: new Date(now).toLocaleTimeString()
            });

            // 同步更新：立即显示"更新中"状态
            setTimeUntilNextUpdate(0);

            if (onRefreshRef.current) {
                // 执行数据刷新
                await onRefreshRef.current();
                lastUpdateTimeRef.current = new Date();

                const duration = Date.now() - now;
                console.log('✅ [useKlineTimer] 数据刷新完成:', {
                    lastUpdate: lastUpdateTimeRef.current.toLocaleTimeString(),
                    duration: duration + 'ms',
                    nextUpdatePlanned: nextUpdateTimeRef.current?.toLocaleTimeString()
                });

                // 注意：定时器设置由主useEffect统一管理，不再在这里递归创建
            }
        } catch (error) {
            console.error('❌ [useKlineTimer] 数据刷新失败:', error);
            if (onErrorRef.current) {
                onErrorRef.current(error instanceof Error ? error : new Error('定时更新失败'));
            }
            // 注意：定时器设置由主useEffect统一管理，错误时不重复创建
        }
    }, []);

    // 设置定时器内部实现 - 避免循环依赖
    const setupTimerInternal = useCallback(() => {
        clearTimers();

        if (!isActiveRef.current) {
            console.log('⏸️ [useKlineTimer] 定时器暂停，跳过设置');
            return;
        }

        // 检测时间帧变化
        const timeframeChanged = lastTimeframeRef.current !== timeframe;
        if (timeframeChanged) {
            console.log('🔄 [useKlineTimer] 检测到时间帧变化:', {
                from: lastTimeframeRef.current,
                to: timeframe
            });
            lastTimeframeRef.current = timeframe;
        }

        // 计算下次K线生成的准确时间
        nextUpdateTimeRef.current = calculateNextKlineTime(timeframe);

        // 立即更新倒计时状态，确保同步
        const now = new Date();
        const timeUntil = Math.max(0, nextUpdateTimeRef.current.getTime() - now.getTime());
        setTimeUntilNextUpdate(timeUntil);

        const delay = nextUpdateTimeRef.current.getTime() - now.getTime();

        // 详细的调试日志
        const expectedInterval = TIMEFRAME_INTERVALS[timeframe as keyof typeof TIMEFRAME_INTERVALS] || 0;
        console.log('🕐 [useKlineTimer] 设置定时器:', {
            timeframe,
            timeframeChanged,
            currentTime: now.toLocaleTimeString() + '.' + now.getMilliseconds().toString().padStart(3, '0'),
            nextUpdateTime: nextUpdateTimeRef.current.toLocaleTimeString() + '.' + nextUpdateTimeRef.current.getMilliseconds().toString().padStart(3, '0'),
            delay: Math.round(delay / 1000) + 's',
            delayMs: delay + 'ms',
            expectedInterval: Math.round(expectedInterval / 1000) + 's',
            expectedIntervalMs: expectedInterval + 'ms',
            nextSeconds: nextUpdateTimeRef.current.getSeconds(),
            targetTime: `${nextUpdateTimeRef.current.getHours()}:${nextUpdateTimeRef.current.getMinutes().toString().padStart(2, '0')}:10`,
            timerActive: isActiveRef.current
        });

        // 如果计算出的时间已经过了，立即执行并重新计算
        if (delay <= 0) {
            console.log('⚡ [useKlineTimer] 延迟时间已过，立即执行刷新');
            performRefresh().then(() => {
                if (isActiveRef.current) {
                    console.log('🔄 [useKlineTimer] 刷新完成，设置下次定时器');
                    setupTimerInternal();
                } else {
                    console.log('⏸️ [useKlineTimer] 定时器已停止，不再设置下次定时器');
                }
            });
            return;
        }

        // 设置主要的数据更新定时器
        timerRef.current = window.setTimeout(async () => {
            const refreshStartTime = Date.now();
            console.log('⏰ [useKlineTimer] 定时器触发，开始刷新', {
                triggerTime: new Date(refreshStartTime).toLocaleTimeString() + '.' + new Date(refreshStartTime).getMilliseconds().toString().padStart(3, '0'),
                plannedTime: nextUpdateTimeRef.current?.toLocaleTimeString() + '.' + nextUpdateTimeRef.current?.getMilliseconds().toString().padStart(3, '0')
            });

            await performRefresh();

            // 如果仍然激活，自动设置下次定时器
            if (isActiveRef.current) {
                console.log('🔄 [useKlineTimer] 刷新完成，设置下次定时器');
                setupTimerInternal();
            } else {
                console.log('⏸️ [useKlineTimer] 定时器已停止，不再设置下次定时器');
            }
        }, delay);

        // 设置倒计时更新定时器 - 提高精度到100ms
        console.log('⏱️ [useKlineTimer] 启动倒计时定时器，间隔100ms');
        countdownRef.current = window.setInterval(() => {
            updateCountdown();

            // 每秒输出一次详细的倒计时状态
            const now = Date.now();
            const currentCountdown = nextUpdateTimeRef.current ? Math.max(0, nextUpdateTimeRef.current.getTime() - now) : 0;
            if (currentCountdown > 0 && currentCountdown % 1000 < 100) { // 约每秒输出一次
                console.log('⏱️ [useKlineTimer] 倒计时状态:', {
                    currentTime: new Date(now).toLocaleTimeString() + '.' + new Date(now).getMilliseconds().toString().padStart(3, '0'),
                    timeUntilNext: Math.round(currentCountdown / 1000) + 's',
                    timeUntilNextMs: currentCountdown + 'ms',
                    nextUpdate: nextUpdateTimeRef.current?.toLocaleTimeString()
                });
            }
        }, 100);
    }, [timeframe, clearTimers, updateCountdown]);

    // 设置定时器 - 外部接口
    const setupTimer = useCallback(() => {
        setupTimerInternal();
    }, [setupTimerInternal]);

    // 暂停定时器
    const pause = useCallback(() => {
        console.log('⏸️ [useKlineTimer] 暂停定时器');
        isActiveRef.current = false;
        clearTimers();
        nextUpdateTimeRef.current = null;
        setTimeUntilNextUpdate(0);
    }, [clearTimers]);

    // 恢复定时器
    const resume = useCallback(() => {
        console.log('▶️ [useKlineTimer] 恢复定时器');
        isActiveRef.current = true;
        setupTimer();
    }, [setupTimer]);

    // 切换定时器状态
    const toggle = useCallback(() => {
        if (isActiveRef.current) {
            pause();
        } else {
            resume();
        }
    }, [pause, resume]);

    // 主effect - 只在enabled或timeframe变化时触发
    useEffect(() => {
        if (enabled) {
            if (isActiveRef.current) {
                setupTimer();
            } else {
                resume();
            }
        } else {
            pause();
        }

        // 清理函数
        return () => {
            clearTimers();
        };
    }, [enabled, timeframe]); // 移除函数依赖，避免循环依赖

    // 返回状态和控制方法
    return {
        isActive: isActiveRef.current,
        lastUpdateTime: lastUpdateTimeRef.current,
        nextUpdateTime: nextUpdateTimeRef.current,
        pause,
        resume,
        toggle,
        timeUntilNextUpdate
    };
}