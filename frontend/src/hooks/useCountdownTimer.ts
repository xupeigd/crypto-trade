import {useCallback, useEffect, useRef, useState} from 'react';

interface UseCountdownTimerOptions {
    /** 时间戳（毫秒） */
    timestamp?: string;
    /** 更新间隔（毫秒），默认1000ms */
    interval?: number;
    /** 是否启用定时器 */
    enabled?: boolean;
}

interface UseCountdownTimerReturn {
    /** 倒计时文本 */
    countdown: string;
    /** 是否已结算 */
    isSettled: boolean;
}

/**
 * 倒计时定时器Hook
 * 优化的倒计时组件，使用requestAnimationFrame提高性能
 */
export const useCountdownTimer = ({
                                      timestamp,
                                      interval = 1000,
                                      enabled = true
                                  }: UseCountdownTimerOptions): UseCountdownTimerReturn => {
    const [countdown, setCountdown] = useState<string>('-');
    const [isSettled, setIsSettled] = useState<boolean>(false);
    const animationFrameRef = useRef<number | null>(null);
    const lastUpdateRef = useRef<number>(0);

    // 计算倒计时文本
    const calculateCountdown = useCallback((): string => {
        if (!timestamp) return '-';

        try {
            const now = Date.now();
            const targetTime = parseInt(timestamp);
            const diffMs = targetTime - now;

            if (diffMs <= 0) return '已结算';

            const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
            const diffHours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const diffMins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            const diffSecs = Math.floor((diffMs % (1000 * 60)) / 1000);

            if (diffDays > 0) {
                return `${diffDays}d${diffHours}h${diffMins}m${diffSecs}s`;
            } else if (diffHours > 0) {
                return `${diffHours}h${diffMins}m${diffSecs}s`;
            } else {
                return `${diffMins}m${diffSecs}s`;
            }
        } catch (e) {
            return '-';
        }
    }, [timestamp]);

    // 优化的定时器，使用requestAnimationFrame
    useEffect(() => {
        if (!enabled || !timestamp) {
            setCountdown('-');
            setIsSettled(false);
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
                animationFrameRef.current = null;
            }
            return;
        }

        const updateCountdown = (currentTime: number) => {
            // 检查是否需要更新（基于间隔时间）
            if (currentTime - lastUpdateRef.current >= interval) {
                const newCountdown = calculateCountdown();
                setCountdown(newCountdown);
                setIsSettled(newCountdown === '已结算');
                lastUpdateRef.current = currentTime;
            }

            // 如果还没有结算，继续动画循环
            if (calculateCountdown() !== '已结算') {
                animationFrameRef.current = requestAnimationFrame(updateCountdown);
            }
        };

        // 立即更新一次
        const initialCountdown = calculateCountdown();
        setCountdown(initialCountdown);
        setIsSettled(initialCountdown === '已结算');
        lastUpdateRef.current = performance.now();

        // 开始动画循环
        if (initialCountdown !== '已结算') {
            animationFrameRef.current = requestAnimationFrame(updateCountdown);
        }

        // 清理函数
        return () => {
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
                animationFrameRef.current = null;
            }
        };
    }, [enabled, timestamp, interval, calculateCountdown]);

    return {countdown, isSettled};
};

// 保持向后兼容的简单倒计时函数
export const formatTimeToFunding = (timestamp?: string): string => {
    if (!timestamp) return '-';
    try {
        const now = Date.now();
        const fundingTime = parseInt(timestamp);
        const diffMs = fundingTime - now;

        if (diffMs <= 0) return '已结算';

        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const diffHours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const diffMins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
        const diffSecs = Math.floor((diffMs % (1000 * 60)) / 1000);

        if (diffDays > 0) {
            return `${diffDays}d${diffHours}h${diffMins}m${diffSecs}s`;
        } else if (diffHours > 0) {
            return `${diffHours}h${diffMins}m${diffSecs}s`;
        } else {
            return `${diffMins}m${diffSecs}s`;
        }
    } catch (e) {
        return '-';
    }
};