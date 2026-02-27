import React, {useCallback, useEffect, useState} from 'react';
import {Button, Empty, Input, message, Space, Tabs, Tag, Typography} from 'antd';
import './SegmentEditor.css';
import {SegmentCategory, SegmentModel} from '../../services/botService';
import MarkdownRenderer from '../common/MarkdownRenderer';
import TableChartIcon from './TableChartIcon';
import PositionChartModal from './PositionChartModal';
import IndicatorChartModal from './IndicatorChartModal';
import {
    BarChartOutlined,
    BulbOutlined,
    CopyOutlined,
    DatabaseOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    EyeOutlined,
    LineChartOutlined,
    RightOutlined,
    SaveOutlined,
    StockOutlined,
    UndoOutlined
} from '@ant-design/icons';

const {Text} = Typography;
const {TextArea} = Input;

interface SegmentEditorProps {
    segments?: SegmentModel[];
    originalSegments?: SegmentModel[];
    onChange?: (segments: SegmentModel[]) => void;
    onValidationChange?: (isValid: boolean) => void;
    readOnly?: boolean;
    apiKeyId?: number | null; // API Key ID，用于获取K线数据
}

/**
 * SegmentEditor组件
 * 支持分块编辑prompt内容，title不可编辑，只编辑content
 */
const SegmentEditor: React.FC<SegmentEditorProps> = ({
                                                         segments: initialSegments = [],
                                                         originalSegments = [],
                                                         onChange,
                                                         onValidationChange,
                                                         readOnly = false,
                                                         apiKeyId = null
                                                     }) => {
    const [segments, setSegments] = useState<SegmentModel[]>(initialSegments);
    const [editingContent, setEditingContent] = useState<Record<number, string>>({});
    const [editingSegments, setEditingSegments] = useState<Record<number, boolean>>({});
    const [activeTabKey, setActiveTabKey] = useState<string>('0');
    // const [hasChanges, setHasChanges] = useState(false); // 暂时未使用

    // 图表弹窗状态管理
    const [positionModalVisible, setPositionModalVisible] = useState<boolean>(false);
    const [indicatorModalVisible, setIndicatorModalVisible] = useState<boolean>(false);
    const [selectedSegmentData, setSelectedSegmentData] = useState<string>('');
    const [selectedInstId, setSelectedInstId] = useState<string | null>(null); // 选中的合约ID

    /**
     * 深度比较两个segments数组是否相等
     * 比较segmentId、title、content、category等关键字段
     * 避免使用JSON.stringify带来的性能问题和引用误判
     */
    const areSegmentsDeepEqual = (seg1: SegmentModel[], seg2: SegmentModel[]): boolean => {
        // 长度不同直接返回false
        if (seg1.length !== seg2.length) {
            return false;
        }

        // 逐个比较每个segment的关键字段
        return seg1.every((s1, index) => {
            const s2 = seg2[index];
            return (
                s1.segmentId === s2.segmentId &&
                s1.title === s2.title &&
                s1.content === s2.content &&
                s1.category === s2.category &&
                s1.priority === s2.priority &&
                s1.showTitle === s2.showTitle
            );
        });
    };

    // 当传入的segments变化时，更新本地状态（避免重置编辑状态）
    useEffect(() => {
        // 【修复】使用深度比较替代JSON.stringify，避免引用变化导致的误判
        const segmentsChanged = !areSegmentsDeepEqual(segments, initialSegments);

        if (segmentsChanged) {
            // 【修复】保存当前tab索引，避免强制跳回0导致闪烁
            const currentTabKey = activeTabKey;
            const currentIndex = parseInt(currentTabKey);

            // 更新segments和相关状态
            setSegments(initialSegments);
            setEditingContent({});
            setEditingSegments({});

            // 【修复】智能恢复：如果当前索引仍然有效，则保持当前tab
            // 只有当索引超出范围时才重置为0
            if (currentIndex >= 0 && currentIndex < initialSegments.length) {
                // 保持当前tab，不闪烁
                // setActiveTabKey(currentTabKey); // 可选：如果tab索引没变，不需要设置
            } else {
                // 索引失效，重置为0
                setActiveTabKey('0');
            }

            // setHasChanges(false); // 暂时注释
            // 不重置expandedSegments，保持用户的展开状态
        }
    }, [initialSegments]);

    // 检查是否有变更（暂时简化，移除hasChanges）
    useEffect(() => {
        // const changed = segments.some((segment, index) => {
        //     const originalContent = editingContent[index] !== undefined ? editingContent[index] : segment.content;
        //     return originalContent !== segment.content;
        // });
        // setHasChanges(changed);

        // 验证内容是否有效（不能为空）
        const isValid = segments.every(segment => {
            const content = editingContent[segments.indexOf(segment)] !== undefined
                ? editingContent[segments.indexOf(segment)]
                : segment.content;
            return content && content.trim().length > 0;
        });

        if (onValidationChange) {
            onValidationChange(isValid);
        }
    }, [segments, editingContent, onValidationChange]);

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
                return <DatabaseOutlined/>;
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

    // 判断是否应该显示图表按钮
    const shouldShowChartIcon = (segment: SegmentModel): boolean => {
        // 更灵活的标题匹配逻辑，支持多种可能的标题格式
        const title = segment.title.toLowerCase();

        // 多种持仓相关的关键词
        const positionKeywords = [
            '持仓', 'position', '价格', 'price', '仓位', 'portfolio'
        ];

        // 多种技术指标相关的关键词
        const indicatorKeywords = [
            '技术指标', '技术分析', 'indicator', 'ema', 'sma', 'rsi', 'boll', 'bollinger',
            '均线', '移动平均', 'macd', 'kdj', 'cci'
        ];

        const hasPositionKeyword = positionKeywords.some(keyword => title.includes(keyword));
        const hasIndicatorKeyword = indicatorKeywords.some(keyword => title.includes(keyword));
        const isSegmentEditing = editingSegments[segments.indexOf(segment)] || false;

        // 优化显示逻辑：只要段落不在编辑状态，且包含相关关键词，就显示图表按钮
        const shouldShow = !isSegmentEditing && (hasPositionKeyword || hasIndicatorKeyword);

        return shouldShow;
    };

    // 获取图表类型
    const getChartType = (segmentTitle: string): 'position' | 'indicator' | null => {
        const title = segmentTitle.toLowerCase();

        // 持仓相关关键词优先判断为position类型
        const positionKeywords = ['持仓', 'position', '价格', 'price', '仓位', 'portfolio'];
        const indicatorKeywords = ['技术指标', '技术分析', 'indicator', 'ema', 'sma', 'rsi', 'boll', 'bollinger', '均线', '移动平均', 'macd', 'kdj', 'cci'];

        const hasPositionKeyword = positionKeywords.some(keyword => title.includes(keyword));
        const hasIndicatorKeyword = indicatorKeywords.some(keyword => title.includes(keyword));

        // 如果同时包含两种关键词，优先显示为持仓数据图表
        if (hasPositionKeyword) {
            return 'position';
        }
        if (hasIndicatorKeyword) {
            return 'indicator';
        }
        return null;
    };

    // 处理图表按钮点击
    const handleChartClick = (segment: SegmentModel, tableData?: string) => {
        const chartType = getChartType(segment.title);
        const dataToUse = tableData || segment.content; // 优先使用表格数据

        console.log('=== handleChartClick 调用 ===');
        console.log('chartType:', chartType);
        console.log('segment title:', segment.title);
        console.log('当前 apiKeyId:', apiKeyId);

        if (chartType === 'position') {
            setSelectedSegmentData(dataToUse);
            setPositionModalVisible(true);
        } else if (chartType === 'indicator') {
            setSelectedSegmentData(dataToUse);
            // 提取合约ID - 传递完整的segment对象
            const instId = extractInstIdFromTable(segment.content, segment);
            setSelectedInstId(instId);

            console.log('📊 IndicatorChartModal 参数汇总:');
            console.log('  - apiKeyId:', apiKeyId);
            console.log('  - instId:', instId);
            console.log('  - indicatorData length:', dataToUse.length);

            if (!apiKeyId) {
                console.error('❌ apiKeyId 为空！无法获取实时数据');
            }
            if (!instId) {
                console.error('❌ instId 为空！无法获取实时数据');
            }

            setIndicatorModalVisible(true);
        }
    };

    // 从表格数据中提取合约ID(instId)
    const extractInstIdFromTable = (tableData: string, segment?: SegmentModel): string | null => {
        try {
            console.log('=== 尝试从表格数据提取合约ID ===');
            console.log('表格数据长度:', tableData.length);

            // 【优先级最高】从原始segment.content提取(Markdown格式)
            if (segment && segment.content) {
                console.log('尝试从segment原始content提取...');
                const originalLines = segment.content.split('\n');

                console.log('检查segment.content前10行:');
                originalLines.slice(0, 10).forEach((line, index) => {
                    console.log(`  行${index + 1}:`, line);
                });

                for (const line of originalLines) {
                    const trimmedLine = line.trim();
                    if (trimmedLine.startsWith('#####')) {
                        console.log('✅ 找到#####行:', trimmedLine);

                        // 提取 #####后面的内容
                        const hashMatch = trimmedLine.match(/#{5,}\s*([A-Z]+-USDT-[A-Z]+)/i);
                        if (hashMatch && hashMatch[1]) {
                            const extractedInstId = hashMatch[1].toUpperCase();
                            console.log('✅ 从segment.content的#####行提取到合约ID:', extractedInstId);
                            return extractedInstId;
                        }

                        // 宽松匹配:#####后面跟任意包含-USDT的内容
                        const looseMatch = trimmedLine.match(/#{5,}\s*(\S+)/i);
                        if (looseMatch && looseMatch[1]) {
                            const potentialInstId = looseMatch[1].trim().toUpperCase();
                            if (potentialInstId.includes('-USDT')) {
                                console.log('✅ 从segment.content的#####行提取到合约ID(宽松):', potentialInstId);
                                return potentialInstId;
                            }
                        }
                    }
                }
                console.log('⚠️ segment.content中未找到#####行');
            } else {
                console.log('⚠️ segment或segment.content为空，无法从原始内容提取');
            }

            // 优先处理：从表格前两行查找以#####开头的内容
            const lines = tableData.split('\n');
            const firstTwoLines = lines.slice(0, 2);

            console.log('检查表格前两行:');
            firstTwoLines.forEach((line, index) => {
                console.log(`  行${index + 1}:`, line.substring(0, 100));
            });

            for (const line of firstTwoLines) {
                // 查找以#####开头的内容
                const hashMatch = line.match(/#{3,}\s*([A-Z]+-USDT-[A-Z]+)/i);
                if (hashMatch && hashMatch[1]) {
                    const extractedInstId = hashMatch[1].toUpperCase();
                    console.log('✅ 从#####行提取到合约ID:', extractedInstId);
                    return extractedInstId;
                }

                // 查找以#####开头，后面跟任意格式的内容
                const looseHashMatch = line.match(/#{3,}\s*(.*?)(?:\s|$)/);
                if (looseHashMatch && looseHashMatch[1]) {
                    const potentialInstId = looseHashMatch[1].trim().toUpperCase();
                    // 检查是否包含合约ID模式
                    if (potentialInstId.includes('-USDT-') || potentialInstId.includes('-USDT')) {
                        console.log('✅ 从#####行提取到可能合约ID:', potentialInstId);
                        return potentialInstId;
                    }
                }
            }

            // 如果前两行没找到，尝试从整个表格数据中提取
            console.log('前两行未找到，尝试全局匹配...');

            // 格式1: 标准永续合约格式 "BTC-USDT-SWAP"
            let instIdPattern = /([A-Z]+)-USDT-(SWAP|PERP|FUTURES)/i;
            let match = tableData.match(instIdPattern);

            if (match && match[0]) {
                const extractedInstId = match[0].toUpperCase();
                console.log('✅ 成功提取合约ID（标准格式）:', extractedInstId);
                return extractedInstId;
            }

            // 格式2: 简化的永续合约格式 "BTC-USDT-SWAP"
            instIdPattern = /([A-Z]{2,10})-USDT-SWAP/i;
            match = tableData.match(instIdPattern);

            if (match && match[0]) {
                const extractedInstId = match[0].toUpperCase();
                console.log('✅ 成功提取合约ID（简化格式）:', extractedInstId);
                return extractedInstId;
            }

            // 格式3: 可能包含其他后缀
            instIdPattern = /([A-Z]+)-USDT-([A-Z]+)/i;
            match = tableData.match(instIdPattern);

            if (match && match[0]) {
                const extractedInstId = match[0].toUpperCase();
                console.log('✅ 成功提取合约ID（通用格式）:', extractedInstId);
                return extractedInstId;
            }

            // 格式4: 仅币种和USDT，添加-SWAP后缀
            instIdPattern = /([A-Z]+)-USDT/i;
            match = tableData.match(instIdPattern);

            if (match && match[0]) {
                const extractedInstId = (match[0] + '-SWAP').toUpperCase();
                console.log('✅ 使用宽松格式提取合约ID:', extractedInstId);
                return extractedInstId;
            }

            // 格式5: 尝试从"Top15合约概况"等标题中提取
            const titlePattern = /Top\d+.*?合约/i;
            const titleMatch = tableData.match(titlePattern);

            if (titleMatch) {
                // 如果发现是Top15合约概况，尝试从表格内容中查找合约代码
                console.log('检测到Top15合约概况表格');
                // 查找表格中的第一列数据
                for (const line of lines) {
                    const coinMatch = line.match(/([A-Z]+)-USDT/i);
                    if (coinMatch && coinMatch[0]) {
                        const extractedInstId = (coinMatch[0] + '-SWAP').toUpperCase();
                        console.log('✅ 从表格内容提取到第一个合约ID:', extractedInstId);
                        return extractedInstId;
                    }
                }
            }

            console.error('❌ 无法从表格数据中提取合约ID');
            console.error('尝试的模式:');
            console.error('  1. #####行提取');
            console.error('  2. ([A-Z]+)-USDT-(SWAP|PERP|FUTURES)');
            console.error('  3. ([A-Z]{2,10})-USDT-SWAP');
            console.error('  4. ([A-Z]+)-USDT-([A-Z]+)');
            console.error('  5. ([A-Z]+)-USDT');
            console.error('表格数据前500字符:', tableData.substring(0, 500));

            return null;
        } catch (error) {
            console.error('❌ 提取合约ID时出错:', error);
            return null;
        }
    };


    // 处理内容变更（优化：避免函数重新创建和频繁状态更新）
    const handleContentChange = useCallback((index: number, content: string) => {
        // 更新编辑内容缓存
        setEditingContent(prev => ({...prev, [index]: content}));

        // 添加调试日志
        console.log(`段落 ${segments[index]?.segmentId} 内容变更:`, content);
    }, []);

    // 失去焦点时触发onChange回调
    const handleBlur = useCallback((index: number) => {
        // 获取当前编辑内容
        const currentContent = editingContent[index];

        // 只有内容确实有变化时才触发onChange
        if (currentContent !== undefined && currentContent !== segments[index]?.content) {
            const updatedSegments = [...segments];
            if (updatedSegments[index]) {
                updatedSegments[index] = {...updatedSegments[index], content: currentContent};
            }

            console.log(`段落 ${segments[index]?.segmentId} 失去焦点，更新内容:`, currentContent);

            if (onChange) {
                onChange(updatedSegments);
            }

            // 实际更新segments状态
            setSegments(updatedSegments);
        }
    }, [editingContent, segments, onChange]);


    // 重置为原始内容
    const handleReset = (index: number) => {
        const originalSegment = originalSegments[index];
        if (originalSegment) {
            handleContentChange(index, originalSegment.content);
            const newEditingContent = {...editingContent};
            delete newEditingContent[index];
            setEditingContent(newEditingContent);
        }
    };

    // 重置所有内容（暂时未使用）
    // const handleResetAll = () => {
    //     setSegments([...originalSegments]);
    //     setEditingContent({});
    //     if (onChange) {
    //         onChange([...originalSegments]);
    //     }
    // };

    // 复制内容
    const handleCopy = (content: string) => {
        navigator.clipboard.writeText(content).then(() => {
            message.success('内容已复制到剪贴板');
        }).catch(() => {
            message.error('复制失败');
        });
    };

    // 复制所有内容（暂时未使用）
    // const handleCopyAll = () => {
    //     const fullContent = segments.map(segment => `=== ${segment.title} ===\n${segment.content}`).join('\n\n');
    //     handleCopy(fullContent);
    // };


    // 切换段落展开/收起 (不再需要，由Tab控制)
    // const toggleExpandSegment = (index: number) => { ... };

    // 开始编辑段落
    const startEditSegment = (index: number) => {
        setEditingSegments(prev => ({
            ...prev,
            [index]: true
        }));
    };

    // 停止编辑段落
    const stopEditSegment = (index: number) => {
        setEditingSegments(prev => ({
            ...prev,
            [index]: false
        }));
    };


    // 统一编辑所有段落（暂时未使用）
    // const editAllSegments = () => {
    //     const allEditing: Record<number, boolean> = {};
    //     segments.forEach((_, index) => {
    //         allEditing[index] = true;
    //     });
    //     setEditingSegments(allEditing);

    //     // 展开所有段落
    //     const allExpanded: Record<number, boolean> = {};
    //     segments.forEach((_, index) => {
    //         allExpanded[index] = true;
    //     });
    //     setExpandedSegments(allExpanded);
    // };

    // 停止编辑所有段落（暂时未使用）
    // const stopEditAllSegments = () => {
    //     setEditingSegments({});
    // };

    // 保存单个段落
    const saveSegment = useCallback((index: number) => {
        // 将编辑内容保存到segments中
        const currentContent = editingContent[index];
        if (currentContent !== undefined) {
            const updatedSegments = [...segments];
            updatedSegments[index] = {...updatedSegments[index], content: currentContent};
            setSegments(updatedSegments);

            // 清除编辑内容
            const newEditingContent = {...editingContent};
            delete newEditingContent[index];
            setEditingContent(newEditingContent);
        }

        stopEditSegment(index);
        message.success(`段落 "${segments[index].title}" 已保存`);
    }, [segments, editingContent, stopEditSegment]);

    // 如果没有segments，显示空状态
    if (!segments || segments.length === 0) {
        return (
            <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                    <Text type="secondary">
                        暂无可编辑的段落内容
                    </Text>
                }
            />
        );
    }

    // 渲染Tab右侧的操作按钮
    const renderTabBarExtraContent = () => {
        const index = parseInt(activeTabKey);
        if (isNaN(index) || !segments[index]) return null;

        const segment = segments[index];
        const currentContent = editingContent[index] !== undefined ? editingContent[index] : segment.content;
        const isModified = currentContent !== segment.content;
        const isEditing = editingSegments[index] || false;

        return (
            <Space size="small">
                <Tag color={getCategoryColor(segment.category)} style={{marginRight: 8}}>
                    {segment.category}
                </Tag>
                {isModified && (
                    <Tag color="orange">已修改</Tag>
                )}
                {isEditing && (
                    <Tag color="blue">编辑中</Tag>
                )}

                {!readOnly && !isEditing && (
                    <Button
                        type="text"
                        size="small"
                        icon={<EditOutlined/>}
                        onClick={(e) => { e.stopPropagation(); startEditSegment(index); }}
                        title="编辑此段落"
                    />
                )}
                {!readOnly && isEditing && (
                    <>
                        <Button
                            type="text"
                            size="small"
                            icon={<SaveOutlined/>}
                            onClick={(e) => { e.stopPropagation(); saveSegment(index); }}
                            style={{color: '#52c41a'}}
                            title="保存此段落"
                        />
                        <Button
                            type="text"
                            size="small"
                            icon={<EyeOutlined/>}
                            onClick={(e) => { e.stopPropagation(); stopEditSegment(index); }}
                            title="退出编辑"
                        />
                    </>
                )}
                {!readOnly && isModified && (
                    <Button
                        type="text"
                        size="small"
                        icon={<UndoOutlined/>}
                        onClick={(e) => { e.stopPropagation(); handleReset(index); }}
                        title="重置为原始内容"
                    />
                )}
                <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined/>}
                    onClick={(e) => { e.stopPropagation(); handleCopy(currentContent); }}
                    title="复制内容"
                />
            </Space>
        );
    };

    // 生成Tab items
    const tabItems = segments.map((segment, index) => {
        const currentContent = editingContent[index] !== undefined ? editingContent[index] : segment.content;
        const isEditing = editingSegments[index] || false;

        return {
            key: index.toString(),
            label: (
                <Space size="small">
                    {getCategoryIcon(segment.category)}
                    <Text
                        style={{maxWidth: 100, minWidth: 30, margin: 0, color: 'inherit', fontSize: '13px'}}
                        ellipsis={{tooltip: segment.title}}
                    >
                        {segment.title}
                    </Text>
                </Space>
            ),
            children: (
                <div style={{
                    backgroundColor: '#1f1f1f',
                    borderRadius: '0 0 6px 6px',
                    border: '1px solid #303030', // 稍微亮一点的边框
                    borderTop: 'none',
                    padding: '16px',
                    minHeight: '200px'
                }}>
                    {isEditing ? (
                        <TextArea
                            id={`segment-content-${segments[index]?.segmentId || index}`}
                            value={currentContent}
                            onChange={(e) => handleContentChange(index, e.target.value)}
                            onBlur={() => isEditing && handleBlur(index)}
                            placeholder="请输入段落内容..."
                            autoSize={{minRows: 6}}
                            readOnly={readOnly}
                            style={{
                                fontSize: '13px',
                                fontFamily: 'Monaco, Consolas, monospace',
                                backgroundColor: '#2a2a2a',
                                border: '1px solid #52c41a',
                                cursor: 'text'
                            }}
                        />
                    ) : (
                        <MarkdownRenderer
                            content={currentContent}
                            showChartButtons={shouldShowChartIcon(segment)}
                            segmentTitle={segment.title}
                            onTableChartClick={(tableData, tableIndex) => {
                                handleChartClick(segment, tableData);
                            }}
                            style={{
                                fontSize: '13px',
                                fontFamily: 'Monaco, Consolas, monospace',
                                border: 'none',
                                padding: '0px',
                                minHeight: '100px',
                                borderRadius: '6px',
                                backgroundColor: 'rgb(31, 31, 31)'
                            }}
                        />
                    )}
                </div>
            )
        };
    });

    return (
        <div style={{width: '100%'}}>
            {/* 段落Tab页 */}
            <Tabs
                type="card"
                activeKey={activeTabKey}
                onChange={setActiveTabKey}
                items={tabItems}
                tabBarExtraContent={renderTabBarExtraContent()}
                className="segment-editor-tabs"
                size="small"
            />

            {/* 图表弹窗组件 */}
            <PositionChartModal
                visible={positionModalVisible}
                onClose={() => {
                    setPositionModalVisible(false);
                    setSelectedSegmentData('');
                }}
                positionData={selectedSegmentData}
            />

            <IndicatorChartModal
                visible={indicatorModalVisible}
                onClose={() => {
                    setIndicatorModalVisible(false);
                    setSelectedSegmentData('');
                    setSelectedInstId(null);
                }}
                indicatorData={selectedSegmentData}
                apiKeyId={apiKeyId}
                instId={selectedInstId}
            />

        </div>
    );
};

export default SegmentEditor;