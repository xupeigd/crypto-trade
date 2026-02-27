import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Button, Card, Space, Typography} from 'antd';
import {CloseOutlined} from '@ant-design/icons';
import PortalContainer from '../../../components/common/PortalContainer';
import {CATEGORY_CONFIG, IndicatorPreset, PRESETS_BY_CATEGORY} from '../../../config/technicalIndicatorsPresets';

const {Text} = Typography;

interface PresetStrategyPanelProps {
    isVisible: boolean;
    onClose: () => void;
    onApplyPreset: (preset: IndicatorPreset) => void;
    triggerElement?: HTMLElement | null;
    className?: string;
    style?: React.CSSProperties;
}

/**
 * 预设策略弹出面板
 * 使用Portal渲染，避免层级遮挡问题
 */
const PresetStrategyPanel: React.FC<PresetStrategyPanelProps> = ({
                                                                     isVisible,
                                                                     onClose,
                                                                     onApplyPreset,
                                                                     triggerElement,
                                                                     className,
                                                                     style
                                                                 }) => {
    const [position, setPosition] = useState<{ top: number; left: number }>({top: 0, left: 0});
    const panelRef = useRef<HTMLDivElement>(null);

    // 计算弹窗位置
    const calculatePosition = useCallback(() => {
        if (!triggerElement || !isVisible) return;

        const triggerRect = triggerElement.getBoundingClientRect();
        const panelWidth = 320; // 预估的面板宽度
        const panelHeight = 200; // 预估的面板高度（调整为更合理的值）
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        let left = triggerRect.left;
        let top = triggerRect.bottom + 4; // 在触发器下方4px

        // 水平边界检查 - 确保面板不超出视窗
        if (left + panelWidth > viewportWidth) {
            left = viewportWidth - panelWidth - 8; // 右边留8px边距
        }
        if (left < 8) {
            left = 8; // 左边留8px边距
        }

        // 垂直边界检查
        if (top + panelHeight > viewportHeight) {
            // 如果下方空间不足，显示在触发器上方
            top = triggerRect.top - panelHeight - 4;
            // 如果上方空间也不足，则显示在视窗顶部
            if (top < 8) {
                top = 8;
            }
        }

        setPosition({left, top});
    }, [triggerElement, isVisible]);

    // 监听窗口大小变化和滚动
    useEffect(() => {
        if (isVisible) {
            calculatePosition();

            // 监听窗口大小变化
            const handleResize = () => calculatePosition();
            window.addEventListener('resize', handleResize);

            // 监听滚动事件
            const handleScroll = () => calculatePosition();
            window.addEventListener('scroll', handleScroll, true);

            return () => {
                window.removeEventListener('resize', handleResize);
                window.removeEventListener('scroll', handleScroll, true);
            };
        }
    }, [isVisible, calculatePosition]);

    // 处理预设策略应用
    const handleApplyPreset = useCallback((preset: IndicatorPreset) => {
        try {
            onApplyPreset(preset);
            onClose();
        } catch (error) {
            console.error('[PresetStrategyPanel] 应用预设策略失败:', error);
        }
    }, [onApplyPreset, onClose]);

    // 处理点击外部区域关闭
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (!isVisible || !panelRef.current) return;

            const target = event.target as Node;
            if (!panelRef.current.contains(target)) {
                onClose();
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isVisible, onClose]);

    // 处理ESC键关闭
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (isVisible && event.key === 'Escape') {
                onClose();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isVisible, onClose]);

    if (!isVisible) {
        return null;
    }

    return (
        <PortalContainer>
            <Card
                ref={panelRef}
                className={className}
                style={{
                    position: 'fixed',
                    top: position.top,
                    left: position.left,
                    zIndex: 1001, // 低于下拉面板(9999)但高于主界面
                    width: '320px',
                    backgroundColor: '#262626',
                    border: '1px solid #404040',
                    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)',
                    borderRadius: '8px',
                    ...style
                }}
                size="small"
            >
                <div style={{padding: '8px'}}>
                    {/* 标题和关闭按钮 */}
                    <div style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: '12px'
                    }}>
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px'
                        }}>
                            <Text strong style={{color: '#fff', fontSize: '13px'}}>
                                预设策略
                            </Text>
                        </div>
                        <Button
                            type="text"
                            icon={<CloseOutlined style={{fontSize: '12px', color: '#999'}}/>}
                            onClick={onClose}
                            size="small"
                            style={{
                                border: 'none',
                                background: 'transparent',
                                padding: '2px',
                                minWidth: 'auto',
                                height: 'auto'
                            }}
                        />
                    </div>

                    {/* 预设策略内容 */}
                    <div>
                        {Object.entries(CATEGORY_CONFIG).map(([categoryKey, categoryConfig]) => {
                            const categoryPresets = PRESETS_BY_CATEGORY[categoryKey as keyof typeof PRESETS_BY_CATEGORY];
                            if (!categoryPresets || categoryPresets.length === 0) return null;

                            return (
                                <div key={categoryKey} style={{marginBottom: '16px'}}>
                                    <div style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '6px',
                                        marginBottom: '8px',
                                        color: '#ffffff',
                                        fontSize: '12px',
                                        fontWeight: 'bold'
                                    }}>
                                        <span>{categoryConfig.icon}</span>
                                        <span>{categoryConfig.name}</span>
                                    </div>
                                    <Space wrap size={4}>
                                        {categoryPresets.map(preset => (
                                            <Button
                                                key={preset.id}
                                                size="small"
                                                onClick={() => handleApplyPreset(preset)}
                                                title={preset.description}
                                                style={{
                                                    fontSize: '11px',
                                                    height: '28px',
                                                    backgroundColor: 'rgba(42, 42, 42, 0.8)',
                                                    borderColor: 'rgba(255, 255, 255, 0.08)',
                                                    color: '#ffffff',
                                                    transition: 'all 0.2s ease'
                                                }}
                                                onMouseEnter={(e) => {
                                                    e.currentTarget.style.backgroundColor = 'rgba(64, 64, 64, 0.9)';
                                                    e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.15)';
                                                }}
                                                onMouseLeave={(e) => {
                                                    e.currentTarget.style.backgroundColor = 'rgba(42, 42, 42, 0.8)';
                                                    e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.08)';
                                                }}
                                            >
                                                {preset.name}
                                            </Button>
                                        ))}
                                    </Space>
                                </div>
                            );
                        })}
                    </div>
                </div>
            </Card>
        </PortalContainer>
    );
};

export default PresetStrategyPanel;