import {useEffect, useState} from 'react';

/**
 * 页面可见性检测Hook
 * 基于Page Visibility API检测页面是否可见，用于优化后台页面的资源消耗
 */
export const usePageVisibility = () => {
    const [isVisible, setIsVisible] = useState(true);

    useEffect(() => {
        // 定义事件处理函数
        const handleVisibilityChange = () => {
            setIsVisible(document.visibilityState === 'visible');
        };

        // 定义页面获取/失去焦点的事件处理
        const handleFocus = () => setIsVisible(true);
        const handleBlur = () => setIsVisible(false);

        // 初始设置可见性状态
        setIsVisible(document.visibilityState === 'visible');

        // 添加事件监听器
        document.addEventListener('visibilitychange', handleVisibilityChange);
        window.addEventListener('focus', handleFocus);
        window.addEventListener('blur', handleBlur);

        // 清理函数：移除事件监听器
        return () => {
            document.removeEventListener('visibilitychange', handleVisibilityChange);
            window.removeEventListener('focus', handleFocus);
            window.removeEventListener('blur', handleBlur);
        };
    }, []);

    return isVisible;
};

export default usePageVisibility;