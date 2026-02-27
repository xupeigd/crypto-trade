import React, {useEffect, useRef, useState} from 'react';
import {Alert, App, Button, Card, Input, Space, Spin, Tooltip, Typography} from 'antd';
import {
    AppstoreOutlined,
    CopyOutlined,
    DownOutlined,
    EditOutlined,
    FileTextOutlined,
    InfoCircleOutlined,
    LoadingOutlined,
    SaveOutlined,
    UpOutlined
} from '@ant-design/icons';
import {botService, PromptGenerateResponse, SegmentModel, parsePromptToSegments} from '../../services/botService';
import {AIInfoModel} from '../../types/aiInfoModel';
import SegmentEditor from './SegmentEditor';
import MarkdownRenderer from '../common/MarkdownRenderer';

const {TextArea} = Input;
const {Text} = Typography;

interface PromptEditorProps {
    apiKeyId: number | null;
    selectedModel: AIInfoModel | null;
    onPromptGenerated?: (promptData: PromptGenerateResponse) => void;
    triggerGenerate?: number; // 触发生成prompt的信号
    onGeneratingChange?: (generating: boolean) => void;
}

const PromptEditor: React.FC<PromptEditorProps> = ({
                                                       apiKeyId,
                                                       selectedModel,
                                                       onPromptGenerated,
                                                       triggerGenerate,
                                                       onGeneratingChange
                                                   }) => {
    const [promptData, setPromptData] = useState<PromptGenerateResponse | null>(null);
    const [editedPrompt, setEditedPrompt] = useState<string>('');
    const [editedSegments, setEditedSegments] = useState<SegmentModel[]>([]);
    const [editingPrompt, setEditingPrompt] = useState<string>(''); // 实时编辑缓存，类似SegmentEditor的editingContent
    const [isEditing, setIsEditing] = useState<boolean>(false);
    const [editMode, setEditMode] = useState<'unified' | 'segmented'>('segmented'); // 编辑模式
    const [loading, setLoading] = useState<boolean>(false);
    const [callingAI, setCallingAI] = useState<boolean>(false);
    const [hasChanges, setHasChanges] = useState<boolean>(false);
    const [isSegmentsValid, setIsSegmentsValid] = useState<boolean>(true);

    // AI任务提交状态管理
    const [submitStatus, setSubmitStatus] = useState<'idle' | 'submitting' | 'submitted' | 'error'>('idle');
    // 面板折叠状态，默认为true（折叠状态）
    const [collapsed, setCollapsed] = useState(true);

    // 防重复提交的引用
    const submitRef = useRef<number>(0);

    // 使用App.useApp()获取message
    const {message} = App.useApp();

    // 获取按钮文本
    const getButtonText = (): string => {
        switch (submitStatus) {
            case 'submitting':
                return '提交中...';
            case 'submitted':
                return '已提交';
            case 'error':
                return '重新提交';
            default:
                return '提交AI任务';
        }
    };

    // 获取按钮禁用状态
    const isButtonDisabled = (): boolean => {
        switch (submitStatus) {
            case 'submitting':
                return true; // 提交中禁用
            case 'submitted':
                return true; // 已提交禁用
            default:
                return !editedPrompt.trim(); // 空内容时禁用
        }
    };

    // 获取按钮loading状态
    const isButtonLoading = (): boolean => {
        return submitStatus === 'submitting' || callingAI;
    };

    // 计算编辑器是否应该为只读状态
    const isEditorReadOnly = (): boolean => {
        return !isEditing || submitStatus === 'submitted';
    };

    /**
     * 切换面板折叠/展开状态
     */
    const handleToggleCollapse = () => {
        setCollapsed(!collapsed);
    };

    // 数据转换函数：将segments转换为prompt文本
    const segmentsToPrompt = (segments: SegmentModel[]): string => {
        try {
            if (!segments || segments.length === 0) {
                console.warn('segmentsToPrompt: 段落数据为空');
                return '';
            }

            // 数据验证
            if (!Array.isArray(segments)) {
                console.error('segmentsToPrompt: 段落数据格式错误，期望数组');
                return '';
            }

            // ✨ 修复: 不再按priority排序,保持数组顺序
            // 按数组顺序重新分配priority,确保用户编辑的顺序被保留
            const result = segments
                .filter(segment => segment != null) // 过滤掉null/undefined
                .map((segment, index) => {
                    // ✅ 重新分配priority为连续序号(10, 20, 30, ...)
                    const newPriority = (index + 1) * 10;

                    // 更新segment的priority
                    if (segment.priority !== newPriority) {
                        segment.priority = newPriority;
                    }

                    // 验证segment结构
                    if (!segment.content && !segment.title) {
                        console.warn(`segmentsToPrompt: 段落 ${index} 缺少内容`);
                        return '';
                    }

                    if (segment.showTitle && segment.title) {
                        const title = String(segment.title).trim();
                        const content = String(segment.content || '').trim();
                        return title ? `=== ${title} ===\n${content}` : content;
                    }
                    return String(segment.content || '').trim();
                })
                .filter(content => content.length > 0) // 过滤掉空内容
                .join('\n\n');

            console.log('segmentsToPrompt 转换成功:', {
                segmentsCount: segments.length,
                resultLength: result.length
            });

            return result;
        } catch (error) {
            console.error('segmentsToPrompt 转换失败:', error);
            return '';
        }
    };

    // 数据转换函数：将prompt文本解析为segments
    const promptToSegments = (prompt: string, originalSegments?: SegmentModel[]): SegmentModel[] => {
        try {
            // 输入验证
            if (typeof prompt !== 'string') {
                console.error('promptToSegments: 输入必须是字符串');
                return [];
            }

            if (!prompt.trim()) {
                console.warn('promptToSegments: prompt内容为空');
                return [];
            }

            console.log('promptToSegments 开始解析:', {promptLength: prompt.length, hasOriginal: !!originalSegments});

            // 如果有原始段落数据，优先基于原始结构解析
            if (originalSegments && originalSegments.length > 0) {
                const contentLines = prompt.split('\n');
                let segmentIndex = 0;
                const parsedSegments: SegmentModel[] = [];

                for (let i = 0; i < contentLines.length && segmentIndex < originalSegments.length; i++) {
                    const line = contentLines[i];
                    const originalSegment = originalSegments[segmentIndex];

                    // 检查是否是标题行
                    if (originalSegment.showTitle && line.startsWith('=== ') && line.endsWith(' ===')) {
                        const title = line.replace(/^=== (.*?) ===$/, '$1');
                        let content = '';
                        i++; // 移到下一行开始读取内容

                        // 收集段落内容，直到遇到下一个标题或文件结尾
                        while (i < contentLines.length) {
                            const nextLine = contentLines[i];
                            if (nextLine.startsWith('=== ') && nextLine.endsWith(' ===')) {
                                i--; // 回退一行，让外层循环处理下一个标题
                                break;
                            }
                            content += (content ? '\n' : '') + nextLine;
                            i++;
                        }

                        parsedSegments.push({
                            ...originalSegment,
                            title,
                            content: content.trim()
                        });
                    } else {
                        // 没有标题的段落，直接作为内容
                        let content = line;
                        i++;

                        while (i < contentLines.length) {
                            const nextLine = contentLines[i];
                            if (nextLine.startsWith('=== ') && nextLine.endsWith(' ===')) {
                                i--; // 回退一行
                                break;
                            }
                            content += '\n' + nextLine;
                            i++;
                        }

                        parsedSegments.push({
                            ...originalSegment,
                            content: content.trim()
                        });
                    }

                    segmentIndex++;
                }

                return parsedSegments;
            }

            // 如果没有原始数据，简单按空行分割
            return prompt
                .split('\n\n')
                .filter(section => section.trim())
                .map((section, index) => ({
                    segmentId: `segment_${Date.now()}_${index}`,
                    title: '',
                    content: section.trim(),
                    category: 'DATA' as const,
                    priority: index + 1,
                    showTitle: false
                }));

        } catch (error) {
            console.error('promptToSegments 解析失败:', error);
            // 发生错误时返回基于原始数据的安全版本
            return originalSegments || [];
        }
    };

    // 检查两个segments数组是否内容相同（忽略segmentId的差异）
    const areSegmentsEqual = (segments1: SegmentModel[], segments2: SegmentModel[]): boolean => {
        if (segments1.length !== segments2.length) {
            return false;
        }

        return segments1.every((seg1, index) => {
            const seg2 = segments2[index];
            return (
                seg1.title === seg2.title &&
                seg1.content === seg2.content &&
                seg1.category === seg2.category &&
                seg1.priority === seg2.priority &&
                seg1.showTitle === seg2.showTitle
            );
        });
    };


    // 触发生成prompt
    const generatePrompt = async () => {
        if (!apiKeyId) {
            message.warning('请先选择API Key');
            return;
        }

        try {
            // 1. 立即设置加载状态
            setLoading(true);
            // 通知父组件开始生成
            if (onGeneratingChange) {
                onGeneratingChange(true);
            }

            // 2. 立即清理所有旧内容(在API调用前)
            setPromptData(null);
            setEditedPrompt('');
            setEditingPrompt('');
            setEditedSegments([]); // 确保清理段落数据
            setIsEditing(false);
            setHasChanges(false);

            // 3. 重置提交状态，生成新prompt时允许重新提交
            setSubmitStatus('idle');
            submitRef.current = 0;

            // 4. 显示加载提示
            const loadingMessage = message.loading('正在生成Prompt...', 0);

            // 5. 调用后端API
            const response = await botService.generatePrompt(apiKeyId);

            // 6. 隐藏加载提示
            loadingMessage();

            if (response.success && response.data) {
                setPromptData(response.data);
                setEditedPrompt(response.data.promptContent);
                setEditingPrompt(response.data.promptContent); // 初始化编辑缓存

                // 【优化】不再使用response.data.segments，而是从promptContent动态解析
                // 这样可以统一数据源，减少数据传输
                const parsedSegments = parsePromptToSegments(response.data.promptContent);

                // 确保segments有segmentId
                const segmentsWithIds = parsedSegments.map((segment, index) => ({
                    ...segment,
                    segmentId: segment.segmentId || `segment_${Date.now()}_${index}`
                }));
                setEditedSegments(segmentsWithIds);
                setIsEditing(true);
                setHasChanges(false);

                if (onPromptGenerated) {
                    onPromptGenerated(response.data);
                }

                message.success('Prompt生成成功！');
            } else {
                message.error(response.message || '生成prompt失败');
            }
        } catch (error) {
            console.error('生成prompt失败:', error);
            message.error('生成prompt失败');
        } finally {
            setLoading(false);
            // 通知父组件生成结束
            if (onGeneratingChange) {
                onGeneratingChange(false);
            }
        }
    };

    // 当triggerGenerate变化时触发生成
    useEffect(() => {
        if (triggerGenerate && triggerGenerate > 0) {
            generatePrompt();
        }
    }, [triggerGenerate, apiKeyId]);

    /**
     * 当prompt生成成功后，自动展开面板
     */
    useEffect(() => {
        if (promptData && collapsed) {
            setCollapsed(false);
        }
    }, [promptData]);

    // 数据同步机制：分块编辑 → 统一编辑
    // 【修复】优化：移除editMode依赖，避免模式切换时触发不必要的同步
    useEffect(() => {
        // 【优化】修复：只在分段模式下将Segments同步到Prompt
        // 同时确保原始segments数据是从promptContent解析得到的
        // 在统一模式下（无论是否编辑中），Prompt是主源，不应被Segments覆盖，否则会导致保存后内容丢失
        if (editMode === 'segmented') {
            const newPrompt = segmentsToPrompt(editedSegments);
            if (newPrompt !== editedPrompt) {
                console.log('分块编辑内容同步到统一编辑:', {
                    segmentsCount: editedSegments.length,
                    promptLength: newPrompt.length
                });
                setEditedPrompt(newPrompt);
                setEditingPrompt(newPrompt);
            }
        }
    }, [editedSegments]); // 【修复】移除editMode依赖，避免模式切换时触发

    // 数据同步机制：统一编辑 → 分块编辑（使用防抖优化性能）
    // 【修复】优化：只在统一模式下执行反向同步，避免双向更新循环
    useEffect(() => {
        // 【优化】避免在编辑状态下自动同步，防止中断用户编辑
        // 从promptData.promptContent解析得到原始segments（如果存在）
        const originalSegments = promptData ? parsePromptToSegments(promptData.promptContent) : [];

        // 【修复】只在统一模式下才执行反向同步，避免双向循环
        if (editMode === 'unified') {
            const newSegments = promptToSegments(editedPrompt, originalSegments);

            // 检查内容是否真的有变化
            if (!areSegmentsEqual(newSegments, editedSegments)) {
                console.log('统一编辑内容同步到分块编辑:', {
                    promptLength: editedPrompt.length,
                    segmentsCount: newSegments.length
                });
                setEditedSegments(newSegments);
            }
        }
    }, [editedPrompt, editMode, promptData?.promptContent]); // 【修复】移除isEditing依赖，简化逻辑

    // 检测prompt内容变化（优化：添加实时缓存机制，类似SegmentEditor）
    const handlePromptChange = (value: string) => {
        // 立即更新编辑缓存
        setEditingPrompt(value);
        setEditedPrompt(value);

        // 添加调试日志
        console.log('统一编辑内容变更:', value);

        setHasChanges(value !== (promptData?.promptContent || ''));
    };

    // 处理segments变更
    const handleSegmentsChange = (segments: SegmentModel[]) => {
        // 确保segments有segmentId
        const segmentsWithIds = segments.map((segment, index) => ({
            ...segment,
            segmentId: segment.segmentId || `segment_${Date.now()}_${index}`
        }));
        setEditedSegments(segmentsWithIds);

        // 【优化】检查是否有变更 - 从promptContent解析原始segments
        const originalSegments = promptData ? parsePromptToSegments(promptData.promptContent) : [];
        const hasSegmentChanges = segments.length !== originalSegments.length ||
            segments.some((seg, index) => {
                const original = originalSegments[index];
                return !original || seg.content !== original.content || seg.title !== original.title;
            });

        setHasChanges(hasSegmentChanges);
    };

    // 处理editMode切换，简化逻辑（数据同步已由useEffect处理）
    const handleEditModeChange = (newMode: 'unified' | 'segmented') => {
        console.log(`切换编辑模式: ${editMode} -> ${newMode}`);

        // 【修复】根据目标模式调整isEditing状态，优化用户体验
        if (newMode === 'unified') {
            // 切换到统一模式：显示预览状态（isEditing=false）
            // 用户需要点击编辑图标才能进入编辑状态
            setIsEditing(false);
            console.log('切换到统一模式，重置为预览状态');
        } else {
            // 切换到分段模式：设置为编辑状态（isEditing=true）
            // 与SegmentEditor的编辑状态保持一致
            setIsEditing(true);
            console.log('切换到分段模式，设置为编辑状态');
        }

        // 由于useEffect已经处理了数据同步，这里只需要切换模式
        // 同步是实时的，不需要等待切换完成
        setEditMode(newMode);

        // 提供用户反馈
        message.info(`已切换到${newMode === 'unified' ? '统一编辑' : '分块编辑'}模式`);
    };

    // 开始编辑
    const startEditing = () => {
        if (!promptData) {
            message.warning('请先生成Prompt');
            return;
        }
        setIsEditing(true);
        // 如果已经提交过，重置状态允许重新提交
        if (submitStatus === 'submitted') {
            setSubmitStatus('idle');
        }
        // 添加焦点
        setTimeout(() => {
            const textArea = document.getElementById('prompt-textarea') as HTMLTextAreaElement;
            if (textArea) {
                textArea.focus();
                // textArea.select(); // 不全选，避免误操作
            }
        }, 100);
    };

    // 取消编辑
    const cancelEditing = () => {
        if (promptData) {
            setEditedPrompt(promptData.promptContent);
            setIsEditing(false);
            setHasChanges(false);
        }
    };

    // 保存编辑
    const saveEditing = () => {
        if (hasChanges) {
            // 重新计算最新的数据，防止useEffect异步更新导致的滞后
            let finalPrompt = editedPrompt;
            let finalSegments = editedSegments;

            // 【优化】根据当前模式重新生成对应的衍生数据
            if (editMode === 'unified') {
                // 统一模式下，确保segments是从当前prompt解析出来的最新版本
                console.log('保存前重新解析segments...');
                const originalSegments = promptData ? parsePromptToSegments(promptData.promptContent) : [];
                finalSegments = promptToSegments(editedPrompt, originalSegments);

                // ✨ 修复: 统一模式编辑后,按序号重新分配priority
                finalSegments = finalSegments.map((segment, index) => ({
                    ...segment,
                    priority: (index + 1) * 10  // 重新分配priority: 10, 20, 30, ...
                }));
            } else {
                // 分段模式下，确保prompt是从当前segments生成的最新版本
                console.log('保存前重新生成prompt...');
                finalPrompt = segmentsToPrompt(editedSegments);
            }

            // 更新promptData中的内容
            setPromptData(prev => {
                if (!prev) return null;

                return {
                    ...prev,
                    promptContent: finalPrompt,
                    segments: finalSegments
                };
            });

            // 同时更新本地状态，确保一致性
            setEditedPrompt(finalPrompt);
            setEditedSegments(finalSegments);

            // 保持hasChanges为false，表示已保存
            setHasChanges(false);
            message.success('编辑内容已保存');
        }

        // 只有在统一模式下，保存后退出编辑模式
        if (editMode === 'unified') {
            setIsEditing(false);
        }
    };

    // 复制prompt内容
    const copyToClipboard = async () => {
        try {
            await navigator.clipboard.writeText(editedPrompt);
            message.success('Prompt内容已复制到剪贴板');
        } catch (error) {
            message.error('复制失败');
        }
    };

    // 调用AI模型（异步版本）
    const callAI = async () => {
        // 防重复提交检查
        if (submitStatus === 'submitting' || submitStatus === 'submitted') {
            console.log('任务正在处理或已提交，跳过重复提交');
            return;
        }

        // 基础验证
        if (!apiKeyId || !selectedModel) {
            message.warning('请确保API Key和AI模型都不为空');
            setSubmitStatus('error');
            return;
        }

        // 设置提交状态
        setSubmitStatus('submitting');
        const currentSubmitId = ++submitRef.current;

        try {
            // 使用React状态数据，无需DOM操作
            let latestPrompt = editedPrompt;
            let latestSegments = editedSegments;

            // 优先使用编辑缓存中的最新数据
            if (editMode === 'unified' && editingPrompt !== editedPrompt) {
                console.log('使用编辑缓存的统一编辑内容:', editingPrompt);
                latestPrompt = editingPrompt;
            }

            console.log('准备提交的数据:', {
                editMode: editMode,
                promptLength: latestPrompt?.length || 0,
                segmentsCount: latestSegments?.length || 0,
                submitId: currentSubmitId
            });

            // 内容验证
            if (editMode === 'unified' && !latestPrompt.trim()) {
                message.warning('请确保prompt内容不为空');
                setSubmitStatus('error');
                return;
            }

            if (editMode === 'segmented' && !isSegmentsValid) {
                message.warning('请确保所有段落内容都不为空');
                setSubmitStatus('error');
                return;
            }

            // 添加数据验证和用户提示
            if (editMode === 'segmented' && latestSegments) {
                const emptySegments = latestSegments.filter(seg => !seg.content.trim());
                if (emptySegments.length > 0) {
                    message.warning(`发现 ${emptySegments.length} 个空段落，请填写内容后再提交`);
                    setSubmitStatus('error');
                    return;
                }
            }

            // 【优化】检查是否有未保存的编辑 - 从promptContent解析原始segments
            const originalSegments = promptData ? parsePromptToSegments(promptData.promptContent) : [];
            const hasUnsavedChanges = editMode === 'segmented'
                ? !areSegmentsEqual(latestSegments, originalSegments)
                : latestPrompt !== promptData?.promptContent;

            if (hasUnsavedChanges) {
                console.log('检测到未保存的编辑内容，将提交最新版本');
            }

            // 设置loading状态
            setCallingAI(true);

            // 确保使用最新的同步数据：总是使用最新的prompt内容，并在分段编辑模式下同时提供segments
            const finalPromptContent = editMode === 'segmented'
                ? segmentsToPrompt(latestSegments)
                : latestPrompt;

            const request = {
                apiKeyId: apiKeyId,
                promptContent: finalPromptContent,
                taskId: `ASYNC_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`,
                saveToHistory: true,
                modelName: selectedModel.modelId,        // 修改：使用modelName匹配后端
                balanceSnapshotId: promptData?.balanceSnapshotId  // 传递balanceSnapshotId，用于关联TradeBalanceSnapshot记录
            };

            const response = await botService.callModel(request);

            // 检查是否是当前提交的响应，防止旧的响应覆盖新的状态
            if (currentSubmitId !== submitRef.current) {
                console.log('收到过期的响应，忽略处理');
                return;
            }

            if (response.success && response.data) {
                message.success('AI模型调用任务已提交！正在后台处理...');
                setSubmitStatus('submitted');
                // 提交成功后自动折叠Prompt编辑器
                setCollapsed(true);

                // 触发父组件立即刷新历史记录，显示新提交的任务
                if (onPromptGenerated) {
                    onPromptGenerated({
                        taskId: response.data.taskId,
                        apiKeyId: response.data.apiKeyId,
                        status: response.data.status,
                        message: response.data.message,
                        promptContent: latestPrompt,
                        accountData: promptData?.accountData || '',
                        positionDetails: promptData?.positionDetails || '',
                        generateTime: response.data.callTime,
                        estimatedTokens: 0,
                        success: response.data.success,
                        errorMessage: response.data.errorMessage
                    });
                }

                // 重置按钮状态
                setHasChanges(false);
            } else {
                message.error(response.message || '提交AI模型调用任务失败');
                setSubmitStatus('error');
            }
        } catch (error) {
            console.error('调用AI模型失败:', error);
            message.error('调用AI模型失败');

            // 检查是否是当前提交的错误，防止旧的错误覆盖新的状态
            if (currentSubmitId === submitRef.current) {
                setSubmitStatus('error');
            }
        } finally {
            // 立即重置loading状态，不等待任务完成
            setCallingAI(false);
        }
    };

    // 键盘快捷键支持
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 's' && isEditing) {
                e.preventDefault();
                saveEditing();
            }
            if (e.key === 'Escape' && isEditing) {
                e.preventDefault();
                cancelEditing();
            }
        };

        if (isEditing) {
            document.addEventListener('keydown', handleKeyDown);
        }

        return () => {
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isEditing, hasChanges, editedPrompt]);

    // 格式化时间
    const formatTime = (timestamp: number) => {
        return new Date(timestamp).toLocaleString('zh-CN');
    };

    return (
        <Card
            title={
                <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%'}}>
                    {/* 左侧：标题和状态 */}
                    <Space>
                        <EditOutlined style={{color: isEditing ? '#52c41a' : '#1677ff'}}/>
                        <span style={{color: isEditing ? '#52c41a' : 'rgba(255, 255, 255, 0.95)'}}>
                            Prompt
                            {/*Prompt {isEditing ? '(编辑中)' : '(预览)'}*/}
                        </span>
                        {/*{hasChanges && (*/}
                        {/*    <Tag color="orange">已修改</Tag>*/}
                        {/*)}*/}
                        {/*{isEditing && (*/}
                        {/*    <Tag color="green">可编辑</Tag>*/}
                        {/*)}*/}
                    </Space>

                    {/* 右侧：信息概览 */}
                    {promptData && (
                        <Space size="small">
                            <Text style={{color: 'rgba(255, 255, 255, 0.6)', fontSize: '11px'}}>
                                Tk:{promptData.estimatedTokens}
                            </Text>
                            {promptData.processingTimeMs && (
                                <Text style={{
                                    color: promptData.processingTimeMs < 1000 ? '#52c41a' : '#faad14',
                                    fontSize: '11px'
                                }}>
                                    {promptData.processingTimeMs < 1000
                                    ? `${promptData.processingTimeMs}ms`
                                    : `${(promptData.processingTimeMs / 1000).toFixed(2)}s`}
                                </Text>
                            )}
                            <Text style={{color: 'rgba(255, 255, 255, 0.6)', fontSize: '11px'}}>
                                {formatTime(promptData.generateTime)}
                            </Text>
                        </Space>
                    )}
                </div>
            }
            extra={
                <Space>
                    {editedPrompt && (
                        <Button
                            type="text"
                            icon={<CopyOutlined/>}
                            onClick={copyToClipboard}
                            size="small"
                            title="复制内容"
                        />
                    )}
                    <Tooltip title={collapsed ? "展开面板" : "折叠面板"}>
                        <Button
                            type="text"
                            icon={collapsed ? <DownOutlined/> : <UpOutlined/>}
                            onClick={handleToggleCollapse}
                            style={{color: collapsed ? 'rgba(255, 255, 255, 0.6)' : '#1677ff'}}
                        />
                    </Tooltip>
                </Space>
            }
            style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                backgroundColor: '#1f1f1f',
                border: '1px solid #434343',
                borderRadius: '8px',
                height: '100%' // 确保高度撑满
            }}
            styles={{
                header: {
                    backgroundColor: '#1a1a1a',
                    borderBottom: '1px solid #434343',
                    color: 'rgba(255, 255, 255, 0.95)'
                },
                body: collapsed ? {
                    height: '56px',
                    minHeight: '56px',
                    padding: 0,
                    overflow: 'hidden'
                } : {
                    padding: '12px 12px 64px 12px',
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'auto'  // 修改:从visible改为auto,允许滚动但不溢出
                }
            }}
        >
            {/* 面板内容：折叠时隐藏，展开时显示 */}
            {!collapsed && (
                <>
                    {loading ? (
                        <div style={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%'
                        }}>
                            <Spin
                                indicator={<LoadingOutlined style={{fontSize: 24}} spin/>}
                            >
                                <div style={{marginTop: 8, color: '#999'}}>生成prompt中...</div>
                            </Spin>
                        </div>
                    ) : !promptData ? (
                        <div style={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: '100%',
                            marginTop: '80px'

                        }}>
                            <Alert
                                message="等待生成prompt"
                                description="请在上侧点击'生成Prompt'按钮来生成AI决策prompt"
                                type="info"
                                showIcon
                                icon={<InfoCircleOutlined/>}
                                style={{
                                    maxWidth: '300px',
                                    border: '1px solid #434343',
                                    background: '#2a2a2a',
                                }}
                            />
                        </div>
                    ) : (
                        <Space direction="vertical" size="middle" style={{width: '100%', height: '100%'}}>

                            {/* Prompt编辑区域 */}
                            <div style={{flex: 1, display: 'flex', flexDirection: 'column'}}>
                                {/* 编辑模式切换 */}
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    marginBottom: '12px'
                                }}>
                                    <Space>
                                        <Text strong style={{color: 'rgba(255, 255, 255, 0.95)'}}>
                                            Segment ：
                                        </Text>
                                        <Text type="secondary" style={{fontSize: '12px'}}>
                                            {editMode === 'segmented'
                                                ? `${editedSegments.length} (blocks)`
                                                : `${editedPrompt.length} (chars)`}
                                        </Text>
                                    </Space>
                                    {editedSegments && editedSegments.length > 0 && (
                                        <Space size="small">
                                            {/* 模式切换图标 */}
                                            <Tooltip title={editMode === 'unified' ? '切换为分段模式' : '切换为统一模式'}>
                                                <Button
                                                    type="text"
                                                    size="small"
                                                    icon={editMode === 'unified' ? <AppstoreOutlined/> :
                                                        <FileTextOutlined/>}
                                                    onClick={() => handleEditModeChange(editMode === 'unified' ? 'segmented' : 'unified')}
                                                    style={{color: 'rgba(255, 255, 255, 0.6)'}}
                                                />
                                            </Tooltip>

                                            {/* 编辑/保存按钮 - 仅在统一模式下显示 */}
                                            {editMode === 'unified' && (
                                                isEditing ? (
                                                    <Tooltip title="保存修改">
                                                        <Button
                                                            type="text"
                                                            size="small"
                                                            icon={<SaveOutlined/>}
                                                            onClick={saveEditing}
                                                            style={{color: '#52c41a'}}
                                                        />
                                                    </Tooltip>
                                                ) : (
                                                    <Tooltip title="修改内容">
                                                        <Button
                                                            type="text"
                                                            size="small"
                                                            icon={<EditOutlined/>}
                                                            onClick={startEditing}
                                                            disabled={!editedPrompt}
                                                            style={{color: '#1677ff'}}
                                                        />
                                                    </Tooltip>
                                                )
                                            )}
                                        </Space>
                                    )}
                                </div>

                                {/* 编辑内容区域 */}
                                {editMode === 'segmented' && editedSegments.length > 0 ? (
                                    <SegmentEditor
                                        segments={editedSegments}
                                        originalSegments={promptData ? parsePromptToSegments(promptData.promptContent) : []}
                                        onChange={handleSegmentsChange}
                                        onValidationChange={setIsSegmentsValid}
                                        readOnly={isEditorReadOnly()}
                                        apiKeyId={apiKeyId}
                                    />
                                ) : (
                                    // 【优化】统一模式：根据isEditing状态条件渲染
                                    isEditing ? (
                                        // 编辑状态：显示可编辑的TextArea
                                        <TextArea
                                            id="prompt-textarea"
                                            value={editedPrompt}
                                            onChange={(e) => handlePromptChange(e.target.value)}
                                            placeholder="正在编辑prompt内容..."
                                            autoSize={{minRows: 15}}
                                            style={{
                                                flex: 1,
                                                minHeight: '300px',
                                                maxHeight: 'none',
                                                fontFamily: 'Monaco, Consolas, monospace',
                                                fontSize: '12px',
                                                backgroundColor: '#2a2a2a',
                                                border: '2px solid #52c41a',
                                                color: 'rgba(255, 255, 255, 0.9)',
                                                resize: 'none',
                                                boxShadow: '0 0 8px rgba(82, 196, 26, 0.2)',
                                                cursor: 'text'
                                            }}
                                        />
                                    ) : (
                                        // 非编辑状态：显示Markdown渲染后的预览
                                        <MarkdownRenderer
                                            content={editedPrompt.replace(/===\s*(.+?)\s*===/g, '## $1')}
                                            style={{
                                                flex: 1,
                                                minHeight: '300px',
                                                fontSize: '12px',
                                                fontFamily: 'Monaco, Consolas, monospace',
                                                border: '1px solid #434343',
                                                padding: '12px',
                                                borderRadius: '6px',
                                                backgroundColor: '#1f1f1f'
                                            }}
                                        />
                                    )
                                )}
                            </div>
                        </Space>
                    )}
                </>
            )}

            {/* 固定在Card右下角的按钮 - 折叠和展开状态下都显示 */}
            {promptData && (
                <div style={{
                    position: 'absolute',
                    bottom: '16px',
                    right: '16px',
                    zIndex: 1000
                }}>
                    <Button
                        type="primary"
                        size="middle"
                        loading={isButtonLoading()}
                        disabled={isButtonDisabled()}
                        onClick={callAI}
                        style={{
                            fontSize: '12px',
                            padding: '4px 8px',
                            background: submitStatus === 'submitted'
                                ? 'linear-gradient(135deg, #52c41a 0%, #95a973 100%)'
                                : 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
                            border: 'none',
                            boxShadow: '0 4px 12px rgba(82, 196, 26, 0.3)',
                            opacity: submitStatus === 'submitted' ? 0.8 : 1
                        }}
                    >
                        {getButtonText()}
                    </Button>
                </div>
            )}
        </Card>
    );
};

export default PromptEditor;