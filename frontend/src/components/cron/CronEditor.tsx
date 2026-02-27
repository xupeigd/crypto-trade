import React, {useCallback, useEffect, useState} from 'react';
import {Input, Space, Typography} from 'antd';

const {Text} = Typography;

interface CronEditorProps {
    value?: string;
    onChange?: (value: string) => void;
    disabled?: boolean;
    placeholder?: string;
}

const CronEditor: React.FC<CronEditorProps> = ({
                                                   value = '0 0 12 * * ?',
                                                   onChange,
                                                   disabled,
                                                   placeholder
                                               }) => {
    const [cronValue, setCronValue] = useState<string>(value);

    // 同步外部value变化
    useEffect(() => {
        if (value !== cronValue) {
            setCronValue(value || '0 0 12 * * ?');
        }
    }, [value, cronValue]);

    // 处理输入变化
    const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const newValue = e.target.value;
        setCronValue(newValue);
        onChange?.(newValue);
    }, [onChange]);

    return (
        <Space direction="vertical" style={{width: '100%'}}>
            <Input
                value={cronValue}
                onChange={handleChange}
                disabled={disabled}
                placeholder={placeholder || '例如: 0 0/5 * * * ?'}
            />

            {/* 帮助信息 */}
            <div style={{
                fontSize: '12px',
                color: '#666',
                backgroundColor: '#f5f5f5',
                padding: '8px 12px',
                borderRadius: '4px'
            }}>
                <Text style={{color: '#666'}}>
                    格式说明：<br/>
                    • 6位格式：秒 分 时 日 月 周<br/>
                    • 5位格式：分 时 日 月 周<br/>
                    <br/>
                    常用示例：<br/>
                    • 每天中午12点：0 12 * * ?<br/>
                    • 每5分钟：*/5 * * * ?<br/>
                    • 每小时：0 * * * ?<br/>
                    • 每周一上午9点：0 9 ? * 1<br/>
                    • 每月1号凌晨0点：0 0 1 * ?
                </Text>
            </div>
        </Space>
    );
};

export default CronEditor;