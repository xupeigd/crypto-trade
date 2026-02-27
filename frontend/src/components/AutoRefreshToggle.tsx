import React from 'react';
import {Space, Switch, Tooltip, Typography} from 'antd';
import {SyncOutlined} from '@ant-design/icons';

const {Text} = Typography;

interface AutoRefreshToggleProps {
    /** 是否启用自动刷新 */
    enabled: boolean;
    /** 切换回调函数 */
    onChange: (enabled: boolean) => void;
    /** 是否显示手动刷新按钮，默认true */
    showManualRefresh?: boolean;
    /** 手动刷新函数 */
    onManualRefresh?: () => void;
    /** 手动刷新加载状态 */
    loading?: boolean;
    /** 自定义样式 */
    style?: React.CSSProperties;
    /** 刷新间隔描述，默认'5秒' */
    interval?: string;
}

/**
 * 自动刷新开关组件
 * 提供自动刷新切换开关和手动刷新按钮
 *
 * 特性：
 * - 自动/手动状态严格互斥
 * - 自动状态：定时器运行，手动刷新禁用
 * - 手动状态：定时器停止，只能手动刷新
 */
const AutoRefreshToggle: React.FC<AutoRefreshToggleProps> = ({
                                                                 enabled,
                                                                 onChange,
                                                                 showManualRefresh = true,
                                                                 onManualRefresh,
                                                                 loading = false,
                                                                 style,
                                                                 interval = '5秒'
                                                             }) => {
    return (
        <Space style={style}>
            <Tooltip title={`开启后将每${interval}自动刷新数据`}>
                <Space>
                    <SyncOutlined
                        style={{
                            color: enabled ? '#1890ff' : '#999',
                            transition: 'color 0.3s'
                        }}
                    />
                    <Text>自动刷新</Text>
                    <Switch
                        checked={enabled}
                        onChange={onChange}
                        size="small"
                    />
                </Space>
            </Tooltip>

            {showManualRefresh && onManualRefresh && (
                <>
                    <Text type="secondary">|</Text>
                    <Tooltip title={enabled ? "自动刷新开启时，手动刷新不可用" : "点击手动刷新数据"}>
            <span
                onClick={() => {
                    // 只有在自动刷新未开启时才允许手动刷新
                    if (!enabled) {
                        onManualRefresh();
                    }
                }}
                style={{
                    cursor: enabled ? 'not-allowed' : 'pointer',
                    color: enabled ? '#d9d9d9' : '#1890ff',
                    fontSize: '14px',
                    transition: 'color 0.3s',
                    pointerEvents: enabled ? 'none' : 'auto' // 确保自动刷新开启时完全禁用点击事件
                }}
            >
              手动刷新
            </span>
                    </Tooltip>
                </>
            )}
        </Space>
    );
};

export default AutoRefreshToggle;