import React, {useEffect, useRef} from 'react';
import {createPortal} from 'react-dom';

interface PortalContainerProps {
    children: React.ReactNode;
}

/**
 * Portal容器组件
 * 用于将下拉弹窗等组件渲染到body层级，避免被父容器的overflow限制
 */
const PortalContainer: React.FC<PortalContainerProps> = ({children}) => {
    const portalRootRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        // 创建专用的Portal容器
        if (!portalRootRef.current) {
            portalRootRef.current = document.createElement('div');
            portalRootRef.current.id = 'indicator-dropdown-portal';
            portalRootRef.current.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                z-index: 9999;
                pointer-events: none;
            `;
            document.body.appendChild(portalRootRef.current);
        }

        return () => {
            // 组件卸载时清理Portal容器
            if (portalRootRef.current && portalRootRef.current.parentNode) {
                portalRootRef.current.parentNode.removeChild(portalRootRef.current);
                portalRootRef.current = null;
            }
        };
    }, []);

    if (!portalRootRef.current) {
        return null;
    }

    return createPortal(
        <div style={{pointerEvents: 'auto'}}>
            {children}
        </div>,
        portalRootRef.current
    );
};

export default PortalContainer;