import React from 'react';
import {Button, Tooltip} from 'antd';
import {CheckCircleOutlined, ExclamationCircleOutlined, LoadingOutlined, ReloadOutlined} from '@ant-design/icons';
import {RefreshStatus} from '../types/common';

interface RefreshIndicatorProps {
    /** 当前刷新状态 */
    status: RefreshStatus;
    /** 手动刷新函数 */
    onRefresh?: () => void;
    /** 是否显示手动刷新按钮，默认true */
    showButton?: boolean;
    /** 重试次数 */
    retryCount?: number;
    /** 最大重试次数 */
    maxRetries?: number;
    /** 自定义样式 */
    style?: React.CSSProperties;
}

/**
 * 刷新状态指示器组件
 * 显示当前刷新状态，支持手动刷新操作
 */
const RefreshIndicator: React.FC<RefreshIndicatorProps> = ({
                                                               status,
                                                               onRefresh,
                                                               showButton = true,
                                                               retryCount = 0,
                                                               maxRetries = 5,
                                                               style
                                                           }) => {
    // 根据状态获取图标和颜色
    const getIconAndColor = () => {
        switch (status) {
            case 'loading':
                return {
                    icon: <LoadingOutlined spin/>,
                    color: '#1890ff',
                    tooltip: '正在刷新...'
                };
            case 'success':
                return {
                    icon: <CheckCircleOutlined/>,
                    color: '#52c41a',
                    tooltip: '刷新成功'
                };
            case 'error':
                return {
                    icon: <ExclamationCircleOutlined/>,
                    color: '#ff4d4f',
                    tooltip: retryCount >= maxRetries
                        ? `刷新失败，已达到最大重试次数 (${maxRetries})`
                        : `刷新失败，正在重试... (${retryCount}/${maxRetries})`
                };
            case 'active':
                return {
                    icon: <CheckCircleOutlined/>,
                    color: '#52c41a',
                    tooltip: '自动刷新中'
                };
            case 'paused':
                return {
                    icon: <ReloadOutlined/>,
                    color: '#faad14',
                    tooltip: '自动刷新已暂停'
                };
            case 'idle':
            default:
                return {
                    icon: <ReloadOutlined/>,
                    color: '#666',
                    tooltip: '点击手动刷新'
                };
        }
    };

    const {icon, color, tooltip} = getIconAndColor();

    // 按钮版本（支持点击刷新）
    if (showButton && onRefresh) {
        return (
            <Tooltip title={tooltip}>
                <Button
                    type="text"
                    icon={icon}
                    onClick={onRefresh}
                    loading={status === 'loading'}
                    style={{
                        color,
                        border: 'none',
                        boxShadow: 'none',
                        ...style
                    }}
                />
            </Tooltip>
        );
    }

    // 纯图标版本（仅显示状态）
    return (
        <Tooltip title={tooltip}>
      <span
          style={{
              color,
              display: 'inline-flex',
              alignItems: 'center',
              ...style
          }}
      >
        {icon}
      </span>
        </Tooltip>
    );
};

export default RefreshIndicator;