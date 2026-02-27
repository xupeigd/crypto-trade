import React from 'react';
import {Badge, FloatButton, Tooltip} from 'antd';
import {DashboardOutlined, SettingOutlined} from '@ant-design/icons';
import {useLocation, useNavigate} from 'react-router-dom';
import {useMenuMode} from '../../contexts/MenuModeContext';

const FloatingToggleButton: React.FC = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const {menuMode, toggleMenuMode} = useMenuMode();

    const handleClick = () => {
        toggleMenuMode();

        // 切换菜单模式后，导航到对应的首页
        if (menuMode === 'trading') {
            // 从交易模式切换到系统管理模式，导航到系统管理首页
            navigate('/system');
        } else {
            // 从系统管理模式切换到交易模式，导航到交易仪表板
            navigate('/');
        }
    };

    const getIcon = () => {
        return menuMode === 'system' ? <DashboardOutlined/> : <SettingOutlined/>;
    };

    const getTooltip = () => {
        return menuMode === 'system' ? '切换到交易系统' : '切换到系统管理';
    };

    const getBadgeColor = () => {
        return menuMode === 'system' ? '#52c41a' : '#1890ff';
    };

    return (
        <Tooltip
            title={getTooltip()}
            placement="left"
            mouseEnterDelay={0.5}
        >
            <Badge
                dot
                color={getBadgeColor()}
                offset={[-8, 8]}
            >
                <FloatButton
                    icon={getIcon()}
                    onClick={handleClick}
                    type="default"
                    style={{
                        right: 24,
                        bottom: 24,
                        width: 56,
                        height: 56,
                        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
                        transition: 'all 0.3s ease-in-out',
                        backgroundColor: 'rgb(31 31 31)',
                        border: '1px solid rgba(255, 255, 255, 0)',
                    }}
                    onMouseEnter={(e) => {
                        e.currentTarget.style.transform = 'scale(1.1)';
                        e.currentTarget.style.backgroundColor = 'rgba(24, 144, 255, 0.8)';
                    }}
                    onMouseLeave={(e) => {
                        e.currentTarget.style.transform = 'scale(1)';
                        e.currentTarget.style.backgroundColor = 'rgb(31 31 31)';
                    }}
                />
            </Badge>
        </Tooltip>
    );
};

export default FloatingToggleButton;