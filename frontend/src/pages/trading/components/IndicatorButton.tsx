import React, {forwardRef, useState} from 'react';
import {Button, Space, Typography} from 'antd';
import {DownOutlined, UpOutlined} from '@ant-design/icons';
import {TechnicalIndicator} from '../../../hooks/useChartState';

const {Text} = Typography;

interface IndicatorButtonProps {
    indicator: TechnicalIndicator;
    isActive: boolean;
    color: string;
    name: string;
    description: string;
    onClick: () => void;
    className?: string;
    style?: React.CSSProperties;
}

/**
 * 技术指标按钮组件
 * 支持选中状态、颜色标识和点击展开功能
 */
const IndicatorButton = forwardRef<HTMLButtonElement, IndicatorButtonProps>(({
                                                                                 isActive,
                                                                                 color,
                                                                                 name,
                                                                                 description,
                                                                                 onClick,
                                                                                 className,
                                                                                 style
                                                                             }, ref) => {
    const [isDropdownOpen, setIsDropdownOpen] = useState<boolean>(false);

    const handleClick = () => {
        setIsDropdownOpen(!isDropdownOpen);
        onClick();
    };

    // 获取按钮样式（包括悬停效果）
    const getButtonStyle = () => ({
        display: 'flex',
        alignItems: 'center',
        backgroundColor: isActive ? '#2a2a2a' : '#1a1a1a',
        borderColor: isActive ? color : '#444444',
        borderWidth: '1px',
        borderStyle: 'solid',
        transition: 'all 0.2s ease',
        marginRight: '2px',
        marginBottom: '2px',
        ...style
    });

    // 悬停效果处理
    const handleMouseEnter = (e: React.MouseEvent<HTMLButtonElement>) => {
        const target = e.currentTarget;
        if (isActive) {
            target.style.backgroundColor = '#353535';
        } else {
            target.style.backgroundColor = '#252525';
        }
    };

    const handleMouseLeave = (e: React.MouseEvent<HTMLButtonElement>) => {
        const target = e.currentTarget;
        if (isActive) {
            target.style.backgroundColor = '#2a2a2a';
        } else {
            target.style.backgroundColor = '#1a1a1a';
        }
    };

    return (
        <Button
            ref={ref}
            size="small"
            onClick={handleClick}
            onMouseEnter={handleMouseEnter}
            onMouseLeave={handleMouseLeave}
            className={className}
            style={getButtonStyle()}
            title={`${name}: ${description}`}
        >
            <Space size={6} style={{display: 'flex', alignItems: 'center'}}>
                <span style={{
                    fontSize: '11px',
                    fontWeight: 'normal',
                    color: isActive ? '#ffffff' : '#888888'
                }}>
                    {name}
                </span>
                {isDropdownOpen ? (
                    <UpOutlined style={{fontSize: '10px', color: isActive ? '#ffffff' : '#888888'}}/>
                ) : (
                    <DownOutlined style={{fontSize: '10px', color: isActive ? '#ffffff' : '#888888'}}/>
                )}
            </Space>
        </Button>
    );
});

IndicatorButton.displayName = 'IndicatorButton';

export default IndicatorButton;