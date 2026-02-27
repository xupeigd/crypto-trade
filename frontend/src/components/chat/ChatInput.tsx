import React, {useEffect, useRef, useState} from 'react';
import {Button, Input, message, Typography} from 'antd';
import {ClearOutlined, SendOutlined} from '@ant-design/icons';
import {TextAreaProps} from 'antd/es/input';

const {Text} = Typography;

const {TextArea} = Input;

interface ChatInputProps extends Omit<TextAreaProps, 'onPressEnter'> {
    onSend: (message: string) => Promise<void>;
    loading?: boolean;
    placeholder?: string;
    minHeight?: number;
    maxHeight?: number;
}

const ChatInput: React.FC<ChatInputProps> = ({
                                                 onSend,
                                                 loading = false,
                                                 placeholder = '请输入您的问题...',
                                                 minHeight = 40,
                                                 maxHeight = 120,
                                                 ...props
                                             }) => {
    const [inputValue, setInputValue] = useState('');
    const [isComposing, setIsComposing] = useState(false);
    const textAreaRef = useRef<any>(null);

    // 自动调整文本框高度
    const adjustHeight = () => {
        if (textAreaRef.current) {
            const textArea = textAreaRef.current.resizableTextArea.textArea;
            if (textArea) {
                textArea.style.height = 'auto';
                const scrollHeight = textArea.scrollHeight;
                textArea.style.height = `${Math.min(Math.max(scrollHeight, minHeight), maxHeight)}px`;
            }
        }
    };

    useEffect(() => {
        adjustHeight();
    }, [inputValue, minHeight, maxHeight]);

    const handleSend = async () => {
        const trimmedValue = inputValue.trim();
        if (!trimmedValue || loading || isComposing) {
            return;
        }

        try {
            setInputValue('');
            await onSend(trimmedValue);
        } catch (error) {
            console.error('发送消息失败:', error);
            message.error('发送消息失败，请重试');
            setInputValue(trimmedValue); // 恢复输入内容
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleClear = () => {
        setInputValue('');
        textAreaRef.current?.focus();
    };

    return (
        <div style={{position: 'relative'}}>
            <TextArea
                ref={textAreaRef}
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                onCompositionStart={() => setIsComposing(true)}
                onCompositionEnd={() => setIsComposing(false)}
                placeholder={placeholder}
                disabled={loading}
                autoSize={{minRows: 1, maxRows: 5}}
                style={{
                    paddingRight: 80,
                    borderRadius: 20,
                    backgroundColor: '#ffffff',
                    border: '1px solid #d9d9d9',
                    resize: 'none',
                    color: '#000000',
                    ...props.style
                }}
                {...props}
            />

            {/* 操作按钮组 */}
            <div
                style={{
                    position: 'absolute',
                    right: 8,
                    bottom: 6,
                    display: 'flex',
                    gap: 4,
                    alignItems: 'center'
                }}
            >
                {/* 清空按钮 */}
                {inputValue && !loading && (
                    <Button
                        type="text"
                        size="small"
                        icon={<ClearOutlined/>}
                        onClick={handleClear}
                        style={{
                            width: 28,
                            height: 28,
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}
                    />
                )}

                {/* 发送按钮 */}
                <Button
                    type={inputValue.trim() ? 'primary' : 'default'}
                    size="small"
                    icon={<SendOutlined/>}
                    onClick={handleSend}
                    disabled={!inputValue.trim() || loading}
                    loading={loading}
                    style={{
                        width: 28,
                        height: 28,
                        borderRadius: '50%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center'
                    }}
                />
            </div>

            {/* 快捷提示 */}
            <div style={{marginTop: 4, textAlign: 'center'}}>
                <Text type="secondary" style={{fontSize: 11}}>
                    按 Enter 发送，Shift + Enter 换行
                </Text>
            </div>
        </div>
    );
};

export default ChatInput;