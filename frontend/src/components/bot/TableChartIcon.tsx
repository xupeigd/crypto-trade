import React from 'react';
import {Button, Tooltip} from 'antd';
import {LineChartOutlined} from '@ant-design/icons';

interface TableChartIconProps {
    type: 'position' | 'indicator';
    onClick: () => void;
    className?: string;
    style?: React.CSSProperties;
}

/**
 * 表格图表图标按钮组件
 * 用于在表格右上角显示图表切换按钮
 */
const TableChartIcon: React.FC<TableChartIconProps> = ({
                                                         type,
                                                         onClick,
                                                         className,
                                                         style
                                                     }) => {
    const getTooltipText = () => {
        switch (type) {
            case 'position':
                return '查看持仓价格K线图';
            case 'indicator':
                return '查看技术指标K线图';
            default:
                return '查看图表';
        }
    };

    return (
        <Tooltip title={getTooltipText()} placement="top">
            <Button
                type="text"
                size="small"
                icon={<LineChartOutlined />}
                onClick={onClick}
                className={className}
                style={{
                    color: '#1890ff',
                    fontSize: '14px',
                    width: '24px',
                    height: '24px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: '4px',
                    border: '1px solid transparent',
                    transition: 'all 0.3s ease',
                    ...style
                }}
                onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = 'rgba(24, 144, 255, 0.1)';
                    e.currentTarget.style.borderColor = '#1890ff';
                }}
                onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                    e.currentTarget.style.borderColor = 'transparent';
                }}
            />
        </Tooltip>
    );
};

export default TableChartIcon;