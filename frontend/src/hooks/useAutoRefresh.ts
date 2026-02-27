import {useCallback, useEffect, useRef, useState} from 'react';
import {usePageVisibility} from './usePageVisibility';

export type RefreshStatus = 'idle' | 'loading' | 'success' | 'error';

export interface AutoRefreshOptions {
    /** 刷新间隔，默认5秒 */
    interval?: number;
    /** 是否启用自动刷新，默认true */
    enabled?: boolean;
    /** 最大重试次数，默认5次 */
    maxRetries?: number;
    /** 初始重试间隔，默认5秒 */
    initialRetryInterval?: number;
    /** 全局状态键，用于共享自动刷新状态 */
    globalKey?: string;
}

export interface AutoRefreshReturn<T> {
    /** 当前刷新状态 */
    status: RefreshStatus;
    /** 是否正在刷新 */
    isLoading: boolean;
    /** 执行手动刷新 */
    refresh: () => Promise<void>;
    /** 错误信息 */
    error: string | null;
    /** 重试次数 */
    retryCount: number;
    /** 是否启用自动刷新 */
    autoRefreshEnabled: boolean;
    /** 切换自动刷新状态 */
    toggleAutoRefresh: (enabled: boolean) => void;
}

/**
 * 统一自动刷新Hook - 支持依赖项
 */
export const useAutoRefresh = <T>(
    refreshFunction: () => Promise<T>,
    options: AutoRefreshOptions = {},
    storageKey?: string,
    dependencies: any[] = [] // 新增依赖项参数
): AutoRefreshReturn<T> => {
    const {
        interval = 5000,
        enabled = true,
        maxRetries = 5,
        initialRetryInterval = 5000,
        globalKey
    } = options;

    // 所有useState必须在最前面
    const [status, setStatus] = useState<RefreshStatus>('idle');
    const [error, setError] = useState<string | null>(null);
    const [retryCount, setRetryCount] = useState(0);

    // 直接从localStorage读取自动刷新状态，避免闭包问题
    const getAutoRefreshState = () => {
        if (globalKey) {
            const stored = localStorage.getItem(`global_auto_refresh_${globalKey}`);
            return stored !== null ? JSON.parse(stored) : enabled;
        }
        return enabled;
    };

    const [autoRefreshEnabled, setAutoRefreshEnabled] = useState<boolean>(getAutoRefreshState());

    // useRef必须在useState之后
    const isRefreshingRef = useRef(false);
    const intervalRef = useRef<number | null>(null);
    const refreshFunctionRef = useRef(refreshFunction);
    const dependenciesRef = useRef(dependencies);

    // 其他hooks
    const isVisible = usePageVisibility();

    // 更新函数引用和依赖项
    useEffect(() => {
        refreshFunctionRef.current = refreshFunction;
    }, [refreshFunction]);

    useEffect(() => {
        dependenciesRef.current = dependencies;
    }, dependencies);

    // 执行刷新
    const executeRefresh = useCallback(async () => {
        if (isRefreshingRef.current) {
            console.log('刷新正在进行中，跳过重复调用');
            return;
        }

        isRefreshingRef.current = true;
        setStatus('loading');
        setError(null);

        try {
            console.log('开始执行刷新');
            await refreshFunctionRef.current();
            setStatus('success');
            setRetryCount(0);

            setTimeout(() => setStatus('idle'), 1000);
        } catch (err) {
            setStatus('error');
            setError(err instanceof Error ? err.message : '刷新失败');

            // 重试逻辑
            if (getAutoRefreshState() && retryCount < maxRetries) {
                setTimeout(() => {
                    setRetryCount(prev => prev + 1);
                    executeRefresh();
                }, initialRetryInterval);
            }
        } finally {
            isRefreshingRef.current = false;
            console.log('刷新执行完成');
        }
    }, [retryCount, maxRetries, initialRetryInterval]);

    // 手动刷新
    const refresh = useCallback(async () => {
        setRetryCount(0);
        await executeRefresh();
    }, [executeRefresh]);

    // 切换自动刷新
    const toggleAutoRefresh = useCallback((enabled: boolean) => {
        setAutoRefreshEnabled(enabled);
        if (globalKey) {
            localStorage.setItem(`global_auto_refresh_${globalKey}`, JSON.stringify(enabled));
        }
        if (storageKey) {
            localStorage.setItem(storageKey, JSON.stringify(enabled));
        }
    }, [storageKey, globalKey]);

    // 首次加载和依赖项变化时触发刷新
    useEffect(() => {
        // 延迟执行首次刷新，确保状态已完全初始化
        const timer = setTimeout(() => {
            executeRefresh();
        }, 100);

        return () => clearTimeout(timer);
    }, [executeRefresh]);

    // 依赖项变化时触发刷新
    useEffect(() => {
        if (dependenciesRef.current.length > 0) {
            console.log('依赖项变化，触发自动刷新');
            executeRefresh();
        }
    }, [executeRefresh]);

    // 自动刷新定时器 - 使用独立的useEffect避免依赖项问题
    useEffect(() => {
        // 清理现有定时器
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
        }

        // 每次都从最新的localStorage读取状态
        const currentEnabled = getAutoRefreshState();
        console.log('定时器状态检查 - 自动刷新:', currentEnabled, '页面可见:', isVisible);

        if (currentEnabled && isVisible) {
            console.log('启动自动刷新定时器，间隔:', interval);
            intervalRef.current = setInterval(() => {
                if (!isRefreshingRef.current) {
                    console.log('定时器触发自动刷新');
                    refreshFunctionRef.current().catch(err => {
                        console.error('定时器刷新失败:', err);
                    });
                }
            }, interval);
        }

        return () => {
            if (intervalRef.current) {
                console.log('清理自动刷新定时器');
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        };
    }, [interval, isVisible]); // 移除所有依赖项，只保留interval和isVisible

    return {
        status,
        isLoading: status === 'loading',
        refresh,
        error,
        retryCount,
        autoRefreshEnabled: getAutoRefreshState(), // 每次都返回最新状态
        toggleAutoRefresh
    };
};

export default useAutoRefresh;