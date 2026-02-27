import React from 'react';
import {Button, message} from 'antd';
import {MessageOutlined} from '@ant-design/icons';

const TestChat: React.FC = () => {
    const handleTest = () => {
        message.info('聊天组件测试按钮点击！');
        console.log('Test chat button clicked');
    };

    return (
        <div
            style={{
                position: 'fixed',
                bottom: 24,
                right: 24,
                zIndex: 1000
            }}
        >
            <Button
                type="primary"
                shape="circle"
                size="large"
                icon={<MessageOutlined/>}
                onClick={handleTest}
                style={{
                    width: 56,
                    height: 56,
                    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
                    border: 'none'
                }}
            />
        </div>
    );
};

export default TestChat;