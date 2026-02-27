import React, {useState} from 'react';
import {Avatar, Card, Typography, Button, Tooltip} from 'antd';
import {RobotOutlined, UserOutlined, DownOutlined, UpOutlined} from '@ant-design/icons';
import {ChatMessage as ChatMessageType} from '../../types/chat';
import MarkdownRenderer from '../common/MarkdownRenderer';

const {Text} = Typography;

/**
 * 预处理内容函数：将 <thinking> 标签转换为 markdown 引用块格式，处理纯JSON
 * 注意：此函数与 MarkdownRenderer.tsx 中的 preprocessContent 保持一致
 */
const preprocessContent = (content: string): string => {
    // 1. 先提取thinking内容并转换为markdown引用块格式
    let thinkingMarkdown = '';
    const resultWithThinking = content.replace(/<thinking>([\s\S]*?)<\/thinking>/gi, (match, thinkingContent) => {
        const quotedContent = thinkingContent.trim()
            .split('\n')
            .map(line => `> ${line}`)
            .join('\n');
        thinkingMarkdown = `**🤔 思考过程:**\n${quotedContent}`;
        return ''; // 移除thinking标签
    });

    // 2. 检测剩余内容是否为JSON
    let result = resultWithThinking.trim();
    if (!result.includes('```json') && !result.includes('```JSON')) {
        if (result.startsWith('{') || result.startsWith('[')) {
            result = '```json\n' + result + '\n```';
        }
    }

    // 3. 组合thinking和JSON内容
    if (thinkingMarkdown) {
        return thinkingMarkdown + '\n\n' + result;
    }
    return result;
};

interface ChatMessageProps {
    message: ChatMessageType;
}

/**
 * 折叠阈值配置
 */
const COLLAPSE_THRESHOLD = 150; // 字符数超过150时自动折叠

const ChatMessage: React.FC<ChatMessageProps> = ({message}) => {
    const isUser = message.role === 'user';

    // 折叠状态
    const [isExpanded, setIsExpanded] = useState(false);

    // 判断是否需要显示折叠按钮
    const shouldShowCollapseButton = () => {
        let content = message.content;

        // 对用户消息进行标题格式转换：=== {title} === => ## {title}
        if (isUser) {
            content = content.replace(/===\s*([^=]+)\s*===/g, '## $1');
        }

        return content.length > COLLAPSE_THRESHOLD;
    };

    // 获取完整的内容（用于复制）
    const getFullContent = () => {
        let content = message.content;

        // 对用户消息进行标题格式转换：=== {title} === => ## {title}
        if (isUser) {
            content = content.replace(/===\s*([^=]+)\s*===/g, '## $1');
        }

        // 非用户消息，先进行预处理（处理thinking标签和JSON）
        if (!isUser) {
            content = preprocessContent(content);
        }

        return content;
    };

    // 获取显示的内容
    const getDisplayContent = () => {
        let content = message.content;

        // 对用户消息进行标题格式转换：=== {title} === => ## {title}
        if (isUser) {
            content = content.replace(/===\s*([^=]+)\s*===/g, '## $1');
        }

        // 非用户消息，先进行预处理（处理thinking标签和JSON）
        if (!isUser) {
            content = preprocessContent(content);
        }

        // 判断是否需要折叠（基于转换后的内容）
        const shouldCollapseContent = content.length > COLLAPSE_THRESHOLD;

        if (!shouldCollapseContent || isExpanded) {
            return content;
        }
        return content.substring(0, COLLAPSE_THRESHOLD) + '...';
    };

    return (
        <div
            style={{
                display: 'flex',
                justifyContent: isUser ? 'flex-end' : 'flex-start',
                marginBottom: 16,
                padding: '0 8px'
            }}
        >
            <div
                style={{
                    maxWidth: '80%',
                    width: '100%',
                    display: 'flex',
                    flexDirection: isUser ? 'row-reverse' : 'row',
                    alignItems: 'flex-start',
                    gap: 8
                }}
            >
                {/* 头像 */}
                <Avatar
                    size="small"
                    icon={isUser ? <UserOutlined/> : <RobotOutlined/>}
                    style={{
                        backgroundColor: isUser ? '#1890ff' : '#52c41a',
                        flexShrink: 0
                    }}
                />

                {/* 消息内容 */}
                <Card
                    size="small"
                    style={{
                        backgroundColor: isUser ? 'rgba(24, 144, 255, 0.3)' : '#2a2a2a',
                        border: isUser ? 'none' : '1px solid #404040',
                        borderRadius: 18,
                        wordBreak: 'break-word',
                        overflowWrap: 'break-word',
                        position: 'relative',
                        width: 'fit-content',
                        maxWidth: '100%',
                        minWidth: '200px'
                    }}
                    styles={{
                        body: {
                            padding: '12px 16px',
                            overflow: 'hidden',
                            maxWidth: '100%'
                        }
                    }}
                >
                    {/* 消息文本 - 用户和系统响应都使用Markdown渲染 */}
                    <div
                        style={{
                            color: '#ffffff',
                            maxWidth: '100%',
                            overflow: 'hidden',
                            wordBreak: 'break-word',
                            overflowWrap: 'break-word',
                            display: 'inline-block',
                            minWidth: '200px'
                        }}
                    >
                        <MarkdownRenderer content={getDisplayContent()} isUser={isUser} fullContent={getFullContent()} />
                    </div>

                    {/* 展开/折叠按钮 - 右上角绝对定位，在复制按钮右侧 */}
                    {shouldShowCollapseButton() && (
                        <Tooltip title={isExpanded ? '收起' : '展开全文'} placement="top">
                            <Button
                                type="text"
                                size="small"
                                icon={isExpanded ? <UpOutlined /> : <DownOutlined />}
                                onClick={() => setIsExpanded(!isExpanded)}
                                style={{
                                    position: 'absolute',
                                    top: '13px',
                                    right: '17px',
                                    backgroundColor: 'transparent',
                                    borderColor: 'transparent',
                                    color: isExpanded ? '#1890ff' : '#ffffff',
                                    zIndex: 10,
                                    opacity: 0.9
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.opacity = '1';
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.opacity = '0.9';
                                }}
                            />
                        </Tooltip>
                    )}

                    {/* 时间戳 */}
                    <div style={{marginTop: 4}}>
                        <Text
                            style={{fontSize: 11, color: 'rgba(255, 255, 255, 0.45)'}}
                        >
                            {message.createdTime}
                        </Text>
                    </div>
                </Card>
            </div>
        </div>
    );
};

export default ChatMessage;