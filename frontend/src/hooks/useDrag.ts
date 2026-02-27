import { useState, useEffect, useRef, useCallback } from 'react';

interface DragState {
    isDragging: boolean;
    dragStart: { x: number; y: number };
    modalPosition: { x: number; y: number };
    longPressTimer: number | null;
}

interface UseDragOptions {
    longPressDelay?: number;
    storageKey?: string;
    boundaryPadding?: number;
    onDragStart?: () => void;
    onDragEnd?: () => void;
}

interface UseDragReturn {
    dragState: DragState;
    handleMouseDown: (e: React.MouseEvent<HTMLDivElement>) => void;
    resetPosition: () => void;
    modalStyle: React.CSSProperties;
}

/**
 * 高性能拖拽Hook
 * 使用requestAnimationFrame优化节流，启用GPU硬件加速
 */
export const useDrag = (options: UseDragOptions = {}): UseDragReturn => {
    const {
        longPressDelay = 500,
        storageKey,
        boundaryPadding = 50,
        onDragStart,
        onDragEnd
    } = options;

    const [dragState, setDragState] = useState<DragState>({
        isDragging: false,
        dragStart: { x: 0, y: 0 },
        modalPosition: { x: 0, y: 0 },
        longPressTimer: null
    });

    const animationFrameRef = useRef<number | null>(null);
    const isDraggingRef = useRef(false);

    // 加载保存的位置
    useEffect(() => {
        if (storageKey) {
            try {
                const savedPosition = localStorage.getItem(storageKey);
                if (savedPosition) {
                    const position = JSON.parse(savedPosition);
                    setDragState(prev => ({
                        ...prev,
                        modalPosition: position
                    }));
                }
            } catch (error) {
                console.warn('加载位置失败:', error);
            }
        }
    }, [storageKey]);

    // 保存位置到本地存储
    useEffect(() => {
        if (storageKey && (dragState.modalPosition.x !== 0 || dragState.modalPosition.y !== 0)) {
            try {
                localStorage.setItem(storageKey, JSON.stringify(dragState.modalPosition));
            } catch (error) {
                console.warn('保存位置失败:', error);
            }
        }
    }, [dragState.modalPosition, storageKey]);

    // 清理定时器和动画帧
    useEffect(() => {
        return () => {
            if (dragState.longPressTimer) {
                clearTimeout(dragState.longPressTimer);
            }
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
            }
        };
    }, [dragState.longPressTimer]);

    // 高性能鼠标移动处理
    const handleMouseMove = useCallback((e: MouseEvent) => {
        if (!isDraggingRef.current) return;

        // 使用requestAnimationFrame优化性能
        if (animationFrameRef.current) {
            cancelAnimationFrame(animationFrameRef.current);
        }

        animationFrameRef.current = requestAnimationFrame(() => {
            const newX = e.clientX - dragState.dragStart.x;
            const newY = e.clientY - dragState.dragStart.y;

            // 边界检测
            const maxX = window.innerWidth - boundaryPadding;
            const maxY = window.innerHeight - boundaryPadding;
            const boundedX = Math.max(boundaryPadding, Math.min(newX, maxX));
            const boundedY = Math.max(boundaryPadding, Math.min(newY, maxY));

            setDragState(prev => ({
                ...prev,
                modalPosition: { x: boundedX, y: boundedY }
            }));
        });
    }, [dragState.dragStart, boundaryPadding]);

    const handleMouseUp = useCallback(() => {
        if (isDraggingRef.current) {
            isDraggingRef.current = false;

            setDragState(prev => {
                // 清除长按定时器
                if (prev.longPressTimer) {
                    clearTimeout(prev.longPressTimer);
                }

                return {
                    ...prev,
                    isDragging: false,
                    longPressTimer: null
                };
            });

            // 清理动画帧
            if (animationFrameRef.current) {
                cancelAnimationFrame(animationFrameRef.current);
                animationFrameRef.current = null;
            }

            // 移除事件监听器
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);

            onDragEnd?.();
        }
    }, [handleMouseMove, onDragEnd]);

    const handleMouseDown = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
        // 清除可能存在的长按定时器
        if (dragState.longPressTimer) {
            clearTimeout(dragState.longPressTimer);
        }

        const timer = setTimeout(() => {
            // 长按触发，开始拖动
            isDraggingRef.current = true;

            setDragState(prev => ({
                ...prev,
                isDragging: true,
                dragStart: {
                    x: e.clientX - prev.modalPosition.x,
                    y: e.clientY - prev.modalPosition.y
                },
                longPressTimer: null
            }));

            onDragStart?.();

            // 添加事件监听器
            document.addEventListener('mousemove', handleMouseMove);
            document.addEventListener('mouseup', handleMouseUp);
        }, longPressDelay);

        setDragState(prev => ({
            ...prev,
            longPressTimer: timer
        }));
    }, [dragState.longPressTimer, longPressDelay, handleMouseMove, handleMouseUp, onDragStart]);

    const resetPosition = useCallback(() => {
        setDragState({
            isDragging: false,
            dragStart: { x: 0, y: 0 },
            modalPosition: { x: 0, y: 0 },
            longPressTimer: null
        });

        if (storageKey) {
            try {
                localStorage.removeItem(storageKey);
            } catch (error) {
                console.warn('清除位置失败:', error);
            }
        }
    }, [storageKey]);

    // GPU加速的样式
    const modalStyle: React.CSSProperties = {
        // 使用translate3d启用GPU硬件加速
        transform: `translate3d(${dragState.modalPosition.x}px, ${dragState.modalPosition.y}px, 0)`,
        // 告诉浏览器该元素会发生变化，提前优化
        willChange: 'transform',
        // 禁用过渡动画以确保拖拽时的即时响应
        transition: dragState.isDragging ? 'none' : 'transform 0.2s ease-out',
        cursor: dragState.isDragging ? 'grabbing' : 'default'
    };

    return {
        dragState,
        handleMouseDown,
        resetPosition,
        modalStyle
    };
};