import React, {useMemo, useState} from 'react';
import {Collapse, Empty, Space, Tag, Typography} from 'antd';
import {SegmentCategory, SegmentModel, SegmentPriority} from '../../services/botService';
import {
    BarChartOutlined,
    BulbOutlined,
    CaretRightOutlined,
    DatabaseOutlined,
    ExclamationCircleOutlined,
    InfoCircleOutlined,
    StockOutlined
} from '@ant-design/icons';

const {Text} = Typography;

interface PromptSegmentsProps {
    segments?: SegmentModel[];
    defaultActiveKeys?: string[];
}

/**
 * PromptSegments组件
 * 用于展示结构化的Prompt段落数据
 */
const PromptSegments: React.FC<PromptSegmentsProps> = ({
                                                           segments,
                                                           defaultActiveKeys = []
                                                       }) => {
    const [activeKeys, setActiveKeys] = useState<string[]>(defaultActiveKeys);

    // 排序segments：按priority数值升序排列（数值越小优先级越高）
    const sortedSegments = useMemo(() => {
        if (!segments || segments.length === 0) {
            return [];
        }

        return [...segments].sort((a, b) => {
            // 首先按priority排序
            if (a.priority !== b.priority) {
                return a.priority - b.priority;
            }
            // priority相同时按category排序
            return a.category.localeCompare(b.category);
        });
    }, [segments]);

    // 获取分类图标
    const getCategoryIcon = (category: string) => {
        switch (category) {
            case SegmentCategory.DATA:
                return <DatabaseOutlined/>;
            case SegmentCategory.ANALYSIS:
                return <BarChartOutlined/>;
            case SegmentCategory.INSTRUCTION:
                return <ExclamationCircleOutlined/>;
            case SegmentCategory.THINKING:
                return <BulbOutlined/>;
            case SegmentCategory.MARKET:
                return <StockOutlined/>;
            default:
                return <InfoCircleOutlined/>;
        }
    };

    // 获取分类颜色
    const getCategoryColor = (category: string) => {
        switch (category) {
            case SegmentCategory.DATA:
                return 'blue';
            case SegmentCategory.ANALYSIS:
                return 'green';
            case SegmentCategory.INSTRUCTION:
                return 'orange';
            case SegmentCategory.THINKING:
                return 'purple';
            case SegmentCategory.MARKET:
                return 'cyan';
            default:
                return 'default';
        }
    };

    // 获取分类标签文本
    const getCategoryLabel = (category: string) => {
        switch (category) {
            case SegmentCategory.DATA:
                return '数据';
            case SegmentCategory.ANALYSIS:
                return '分析';
            case SegmentCategory.INSTRUCTION:
                return '指令';
            case SegmentCategory.THINKING:
                return '思考';
            case SegmentCategory.MARKET:
                return '市场';
            default:
                return '其他';
        }
    };

    // 获取优先级标签
    const getPriorityLabel = (priority: number) => {
        if (priority <= SegmentPriority.HIGHEST) return '最高';
        if (priority <= SegmentPriority.HIGH) return '高';
        if (priority <= SegmentPriority.MEDIUM_HIGH) return '中高';
        if (priority <= SegmentPriority.MEDIUM) return '中等';
        if (priority <= SegmentPriority.MEDIUM_LOW) return '中低';
        if (priority <= SegmentPriority.LOW) return '低';
        return '最低';
    };

    // 获取优先级颜色
    const getPriorityColor = (priority: number) => {
        if (priority <= SegmentPriority.HIGH) return 'red';
        if (priority <= SegmentPriority.MEDIUM) return 'orange';
        if (priority <= SegmentPriority.LOW) return 'blue';
        return 'default';
    };

    // 生成Collapse items
    const collapseItems = sortedSegments.map((segment, index) => {
        const key = `segment-${index}`;
        const hasMetadata = segment.metadata && Object.keys(segment.metadata).length > 0;

        return {
            key,
            label: (
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    width: '100%'
                }}>
                    <Space size="small" style={{flex: 1, minWidth: 0}}>
            <span style={{color: '#1890ff', fontSize: '14px'}}>
              {getCategoryIcon(segment.category)}
            </span>
                        <Text strong style={{fontSize: '13px', color: 'rgba(255, 255, 255, 0.95)'}}>
                            {segment.title}
                        </Text>
                    </Space>
                    <Space size="small">
                        <Tag color={getCategoryColor(segment.category)} style={{fontSize: '11px'}}>
                            {getCategoryLabel(segment.category)}
                        </Tag>
                        <Tag color={getPriorityColor(segment.priority)} style={{fontSize: '11px'}}>
                            {getPriorityLabel(segment.priority)}
                        </Tag>
                        {hasMetadata && (
                            <Tag color="default" style={{fontSize: '11px'}}>
                                元数据
                            </Tag>
                        )}
                    </Space>
                </div>
            ),
            children: (
                <div style={{
                    padding: '8px 0',
                    lineHeight: '1.6',
                    fontSize: '12px',
                    color: 'rgba(255, 255, 255, 0.85)',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word'
                }}>
                    {segment.content}

                    {/* 显示元数据信息 */}
                    {hasMetadata && (
                        <div style={{
                            marginTop: '12px',
                            padding: '8px',
                            backgroundColor: 'rgba(0, 0, 0, 0.2)',
                            borderRadius: '4px',
                            fontSize: '11px',
                            color: 'rgba(255, 255, 255, 0.65)'
                        }}>
                            <Text strong style={{color: 'rgba(255, 255, 255, 0.75)', fontSize: '11px'}}>
                                元数据：
                            </Text>
                            <div style={{marginTop: '4px'}}>
                                {Object.entries(segment.metadata!).map(([key, value]) => (
                                    <div key={key} style={{marginBottom: '2px'}}>
                                        <span style={{color: 'rgba(255, 255, 255, 0.55)'}}>{key}:</span>
                                        <span style={{marginLeft: '4px', color: 'rgba(255, 255, 255, 0.75)'}}>
                      {String(value)}
                    </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            ),
            style: {
                backgroundColor: 'rgba(255, 255, 255, 0.02)',
                marginBottom: '4px',
                borderRadius: '6px',
                border: '1px solid rgba(255, 255, 255, 0.06)'
            }
        };
    });

    // 如果没有segments数据，显示空状态
    if (!sortedSegments || sortedSegments.length === 0) {
        return (
            <div style={{
                padding: '40px 20px',
                textAlign: 'center',
                backgroundColor: 'rgba(255, 255, 255, 0.02)',
                borderRadius: '6px',
                border: '1px dashed rgba(255, 255, 255, 0.1)'
            }}>
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                        <Text style={{color: 'rgba(255, 255, 255, 0.45)', fontSize: '12px'}}>
                            暂无结构化的段落数据
                        </Text>
                    }
                />
            </div>
        );
    }

    return (
        <div style={{width: '100%'}}>
            <div style={{
                marginBottom: '12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
            }}>
                <Space>
                    <Text style={{
                        fontSize: '13px',
                        fontWeight: 500,
                        color: 'rgba(255, 255, 255, 0.85)'
                    }}>
                        结构化内容 ({sortedSegments.length} 个段落)
                    </Text>
                </Space>
                <Space size="small">
                    <Tag color="blue" style={{fontSize: '11px'}}>
                        <DatabaseOutlined/> 数据
                    </Tag>
                    <Tag color="green" style={{fontSize: '11px'}}>
                        <BarChartOutlined/> 分析
                    </Tag>
                    <Tag color="orange" style={{fontSize: '11px'}}>
                        <ExclamationCircleOutlined/> 指令
                    </Tag>
                    <Tag color="purple" style={{fontSize: '11px'}}>
                        <BulbOutlined/> 思考
                    </Tag>
                    <Tag color="cyan" style={{fontSize: '11px'}}>
                        <StockOutlined/> 市场
                    </Tag>
                </Space>
            </div>

            <Collapse
                items={collapseItems}
                activeKey={activeKeys}
                onChange={setActiveKeys}
                ghost
                size="small"
                expandIcon={({isActive}) => (
                    <CaretRightOutlined
                        rotate={isActive ? 90 : 0}
                        style={{
                            color: 'rgba(255, 255, 255, 0.55)',
                            fontSize: '12px'
                        }}
                    />
                )}
                style={{
                    backgroundColor: 'transparent',
                    border: 'none'
                }}
            />
        </div>
    );
};

export default PromptSegments;