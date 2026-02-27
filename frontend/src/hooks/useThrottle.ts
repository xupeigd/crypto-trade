import {useCallback, useRef} from 'react';

/**
 * 节流Hook
 * @param callback 需要节流的函数
 * @param delay 节流间隔（毫秒）
 * @returns 节流后的函数
 */
export const useThrottle = <T extends (...args: any[]) => any>(
    callback: T,
    delay: number
): T => {
    const lastRunRef = useRef<number>(0);
    const timeoutRef = useRef<number | null>(null);

    return useCallback((...args: Parameters<T>) => {
        const now = Date.now();

        if (now - lastRunRef.current >= delay) {
            // 如果距离上次执行已经超过延迟时间，立即执行
            lastRunRef.current = now;
            callback(...args);
        } else {
            // 否则在延迟时间后执行
            if (timeoutRef.current) {
                clearTimeout(timeoutRef.current);
            }

            timeoutRef.current = window.setTimeout(() => {
                lastRunRef.current = Date.now();
                callback(...args);
            }, delay - (now - lastRunRef.current));
        }
    }, [callback, delay]) as T;
};