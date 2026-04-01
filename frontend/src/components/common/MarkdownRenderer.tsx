import React, {useState} from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {Button} from 'antd';
import {LineChartOutlined} from '@ant-design/icons';
import FundamentalAnalysisChart, {FundamentalAnalysisData} from '../chat/FundamentalAnalysisChart';

// 模块级别的图表模式存储，避免组件重渲染时状态丢失
const globalChartModes = new Map<string, 'json' | 'chart'>();

// 简单的字符串 hash 函数
const simpleHash = (str: string): string => {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash;
    }
    return Math.abs(hash).toString(36);
};

// JSON格式化函数（移到组件外部）
const formatJson = (content: string): string => {
    try {
        const parsed = JSON.parse(content);
        return JSON.stringify(parsed, null, 2);
    } catch {
        return content;
    }
};

// JSON语法高亮函数（移到组件外部）
const highlightJson = (json: string): string => {
    return json
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"([^"]+)":/g, '<span style="color: #9cdcfe;">"$1":</span>')
        .replace(/: "([^"]*)"/g, ': <span style="color: #ce9178;">"$1"</span>')
        .replace(/: (\d+\.?\d*)/g, ': <span style="color: #b5cea8;">$1</span>')
        .replace(/: (true|false)/g, ': <span style="color: #569cd6;">$1</span>')
        .replace(/: (null)/g, ': <span style="color: #569cd6;">$1</span>');
};

// 检测内容是否为JSON（移到组件外部）
const isJsonContent = (content: string): boolean => {
    const trimmed = content.trim();
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) return true;
    if (trimmed.startsWith('[') && trimmed.endsWith(']')) return true;
    return false;
};

// 检测 JSON 是否为 FundamentalAnalysis 类型（移到组件外部）
const isFundamentalAnalysis = (content: string): { isFA: boolean; data?: FundamentalAnalysisData } => {
    try {
        const parsed = JSON.parse(content);
        if (parsed && parsed.type === 'FundamentalAnalysis' && parsed.instId && parsed.timeframe) {
            return { isFA: true, data: parsed as FundamentalAnalysisData };
        }
    } catch {
        // 解析失败，不是有效 JSON
    }
    return { isFA: false };
};

// 独立的代码块组件（移到 MarkdownRenderer 外部）
interface CodeBlockComponentProps {
    children: any;
    inline?: boolean;
    className?: string;
    isUser: boolean;
    chartMode?: 'json' | 'chart';
    onToggleChartMode?: () => void;
    onChartModeChange?: (isChart: boolean) => void;
}

const CodeBlockComponent: React.FC<CodeBlockComponentProps> = ({children, inline, className, isUser, chartMode = 'json', onToggleChartMode, onChartModeChange}) => {
    // 获取代码内容
    let codeContent = '';
    if (typeof children === 'string') {
        codeContent = children;
    } else if (Array.isArray(children)) {
        codeContent = children.join('');
    } else if (children && typeof children === 'object') {
        codeContent = String(children);
    }

    // 检查是否为JSON代码块
    const isJsonBlock = className?.includes('json') || isJsonContent(codeContent);

    // 检测是否为 FundamentalAnalysis 类型
    const faResult = isJsonBlock ? isFundamentalAnalysis(codeContent) : { isFA: false };

    // 获取代码块背景色
    const codeBlockBackgroundColor = isUser ? 'rgba(24, 144, 255, 0.3)' : 'rgba(0, 0, 0, 0.3)';

    // 如果是图表模式，渲染 K 线图
    if (chartMode === 'chart' && faResult.isFA && faResult.data) {
        return (
            <FundamentalAnalysisChart
                data={faResult.data}
                height={350}
                onBack={() => {
                    onToggleChartMode?.();
                    onChartModeChange?.(false);
                }}
            />
        );
    }

    // 如果是JSON，进行格式化和语法高亮
    if (isJsonBlock && !inline) {
        const formattedJson = formatJson(codeContent);
        const highlightedJson = highlightJson(formattedJson);

        return (
            <div style={{ position: 'relative' }}>
                {/* 可视化按钮 - 仅在 FundamentalAnalysis 类型时显示 */}
                {faResult.isFA && (
                    <Button
                        type="text"
                        size="small"
                        icon={<LineChartOutlined />}
                        onClick={() => {
                            onToggleChartMode?.();
                            onChartModeChange?.(true);
                        }}
                        style={{
                            position: 'absolute',
                            top: 8,
                            left: 8,
                            zIndex: 10,
                            color: '#1890ff',
                            backgroundColor: 'rgba(0, 0, 0, 0.5)',
                            borderRadius: '4px'
                        }}
                        title="K线图可视化"
                    >
                        可视化
                    </Button>
                )}
                <pre
                    className="json-code-block"
                    style={{
                        backgroundColor: codeBlockBackgroundColor,
                        border: 'none',
                        borderRadius: '6px',
                        padding: '16px',
                        margin: '16px 0',
                        overflowX: 'auto',
                        fontSize: '13px',
                        fontFamily: 'Consolas, Monaco, monospace',
                        lineHeight: '1.5',
                        color: '#d4d4d4',
                        paddingTop: faResult.isFA ? '44px' : '16px'
                    }}
                    dangerouslySetInnerHTML={{__html: `<code>${highlightedJson}</code>`}}
                />
            </div>
        );
    }

    // 非JSON代码块，使用原有逻辑
    if (inline) {
        return (
            <code
                style={{
                    backgroundColor: codeBlockBackgroundColor,
                    color: '#e6e6e6',
                    padding: '2px 6px',
                    borderRadius: '4px',
                    fontSize: '12px',
                    fontFamily: 'Monaco, Consolas, monospace'
                }}
            >
                {children}
            </code>
        );
    }

    return (
        <pre
            style={{
                backgroundColor: codeBlockBackgroundColor,
                border: 'none',
                borderRadius: '6px',
                padding: '16px',
                margin: '16px 0',
                overflow: 'auto',
                fontSize: '12px',
                fontFamily: 'Monaco, Consolas, monospace',
                lineHeight: '1.5'
            }}
        >
            <code>{children}</code>
        </pre>
    );
};

interface MarkdownRendererProps {
    content: string;
    style?: React.CSSProperties;
    showChartButtons?: boolean;
    onTableChartClick?: (tableData: string, tableIndex: number) => void;
    segmentTitle?: string;
    showCopyButton?: boolean;
    isUser?: boolean;
    fullContent?: string;
    onChartModeChange?: (isChart: boolean) => void;
}

/**
 * MarkdownRenderer组件
 * 用于渲染markdown内容，支持表格、代码块等元素
 * 适配项目暗色主题
 */
const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({
                                                               content,
                                                               style,
                                                               showChartButtons = false,
                                                               onTableChartClick,
                                                               segmentTitle,
                                                               showCopyButton = true,
                                                               isUser = false,
                                                               fullContent,
                                                               onChartModeChange
                                                           }) => {
    // 使用计数器触发重渲染
    const [, forceUpdate] = useState(0);

    // 切换代码块的图表模式
    const toggleChartMode = (codeHash: string) => {
        const currentMode = globalChartModes.get(codeHash) || 'json';
        globalChartModes.set(codeHash, currentMode === 'json' ? 'chart' : 'json');
        forceUpdate(n => n + 1);
    };

    // 获取图表模式
    const getChartMode = (codeHash: string): 'json' | 'chart' => {
        return globalChartModes.get(codeHash) || 'json';
    };

    // 移除 isHovered 和 copied 状态，因为 CollapsibleMessageContent 已经有复制功能

    // 预处理内容函数：将 <thinking> 标签转换为 markdown 引用块格式，处理纯JSON
    const preprocessContent = (content: string, segmentTitle?: string): string => {
        // 1. 先提取thinking内容并转换为markdown引用块格式
        let thinkingMarkdown = '';
        const resultWithThinking = content.replace(/<thinking>([\s\S]*?)<\/thinking>/gi, (match, thinkingContent) => {
            const quotedContent = thinkingContent.trim()
                .split('\n')
                .map((line: string) => `> ${line}`)
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

    // 静态计数器，用于为每个表格生成唯一索引，避免状态重渲染
    let staticTableCounter = 0;

    // 获取背景色，根据isUser参数返回不同的背景色
    const getBackgroundColor = () => {
        if (isUser) {
            return 'transparent';
        }
        return '#2a2a2a';
    };

    // 获取代码块背景色
    const getCodeBlockBackgroundColor = () => {
        if (isUser) {
            return 'rgba(24, 144, 255, 0.3)';
        }
        return 'rgba(0, 0, 0, 0.3)';
    };

    // 获取表格背景色
    const getTableBackgroundColor = () => {
        if (isUser) {
            return 'rgba(24, 144, 255, 0.3)';
        }
        return 'rgba(255, 255, 255, 0.1)';
    };

    // 获取表头背景色
    const getTableHeaderBackgroundColor = () => {
        if (isUser) {
            return 'rgba(24, 144, 255, 0.3)';
        }
        return 'rgba(255, 255, 255, 0.15)';
    };

    // 获取表格行背景色
    const getTableRowBackgroundColor = (index: number) => {
        if (isUser) {
            return 'rgba(24, 144, 255, 0.3)';
        }
        return index % 2 === 0 ? 'rgba(255, 255, 255, 0.08)' : 'rgba(255, 255, 255, 0.12)';
    };

    // 判断段落是否应该显示图表按钮
    const shouldShowChartForSegment = (): boolean => {
        if (!showChartButtons || !segmentTitle) return false;

        const title = segmentTitle.toLowerCase();
        const positionKeywords = ['持仓', 'position', '价格', 'price', '仓位', 'portfolio'];
        const indicatorKeywords = ['技术指标', '技术分析', 'indicator', 'ema', 'sma', 'rsi', 'boll', 'bollinger', '均线', '移动平均', 'macd', 'kdj', 'cci'];

        return positionKeywords.some(keyword => title.includes(keyword)) ||
            indicatorKeywords.some(keyword => title.includes(keyword));
    };

    // 获取表格数据类型
    const getTableType = (): 'position' | 'indicator' | null => {
        if (!segmentTitle) return null;

        const title = segmentTitle.toLowerCase();
        const positionKeywords = ['持仓', 'position', '价格', 'price', '仓位', 'portfolio'];
        const indicatorKeywords = ['技术指标', '技术分析', 'indicator', 'ema', 'sma', 'rsi', 'boll', 'bollinger', '均线', '移动平均', 'macd', 'kdj', 'cci'];

        if (positionKeywords.some(keyword => title.includes(keyword))) return 'position';
        if (indicatorKeywords.some(keyword => title.includes(keyword))) return 'indicator';
        return null;
    };

    // 表格数据结构定义
    interface TableData {
        headers: string[];
        rows: string[][];
    }


    // 处理表格数据，返回结构化的JSON数据
    const extractTableData = (element: React.ReactNode): string => {
        try {
            if (!element) {
                console.warn('表格元素为空');
                return JSON.stringify({headers: [], rows: []});
            }

            const tableData: TableData = {headers: [], rows: []};

            // 处理数组类型的元素（ReactMarkdown可能传入数组）
            const processNode = (node: React.ReactNode): void => {
                // 如果是数组，递归处理每个元素
                if (Array.isArray(node)) {
                    node.forEach(processNode);
                    return;
                }

                // 如果是React元素，处理其结构
                if (React.isValidElement(node)) {
                    const elementType = node.type;
                    const props = node.props;

                    // 获取组件名称（支持函数组件和字符串标签）
                    let componentName = '';
                    if (typeof elementType === 'string') {
                        componentName = elementType.toLowerCase();
                    } else {
                        // 对于函数组件，尝试获取displayName或函数名
                        componentName = (elementType as any)?.displayName?.toLowerCase() ||
                            (elementType as any)?.name?.toLowerCase() ||
                            '';
                    }

                    // 检查是否是表格相关元素
                    switch (componentName) {
                        case 'table':
                        case 'tablecomponent':
                            // 直接处理表格的children
                            if (props.children) {
                                processNode(props.children);
                            }
                            break;

                        case 'thead':
                        case 'theadcomponent':
                            // 处理表头
                            if (props.children) {
                                extractHeaders(props.children);
                            }
                            break;

                        case 'tbody':
                        case 'tbodycomponent':
                            // 处理表体
                            if (props.children) {
                                extractRows(props.children);
                            }
                            break;

                        case 'tr':
                        case 'rowcomponent':
                            // 处理表格行
                            if (props.children) {
                                extractRowCells(props.children);
                            }
                            break;

                        case 'th':
                        case 'thcomponent':
                            // 处理表头单元格
                            const thText = extractTextFromNode(node);
                            if (thText && thText.trim()) {
                                tableData.headers.push(thText.trim());
                            }
                            break;

                        case 'td':
                        case 'tdcomponent':
                            // 处理表格单元格
                            const tdText = extractTextFromNode(node);
                            if (tdText && tdText.trim()) {
                                currentRowCells.push(tdText.trim());
                            }
                            break;

                        default:
                            // 对于其他元素，继续递归处理
                            if (props.children) {
                                processNode(props.children);
                            }
                            break;
                    }
                }
            };

            let currentRowCells: string[] = [];

            const extractHeaders = (node: React.ReactNode): void => {
                const headers: string[] = [];
                let inHeaderRow = false;

                const traverseHeaders = (n: React.ReactNode): void => {
                    if (Array.isArray(n)) {
                        n.forEach(traverseHeaders);
                        return;
                    }

                    if (React.isValidElement(n)) {
                        const elementType = n.type;
                        let componentName = '';

                        if (typeof elementType === 'string') {
                            componentName = elementType.toLowerCase();
                        } else {
                            componentName = (elementType as any)?.displayName?.toLowerCase() ||
                                (elementType as any)?.name?.toLowerCase() || '';
                        }

                        // 检查是否是表头行
                        if (componentName === 'tr' || componentName === 'rowcomponent') {
                            inHeaderRow = true;
                            if (n.props.children) {
                                traverseHeaders(n.props.children);
                            }
                            inHeaderRow = false;
                        } else if ((componentName === 'th' || componentName === 'thcomponent') && inHeaderRow) {
                            const text = extractTextFromNode(n);
                            if (text && text.trim()) {
                                headers.push(text.trim());
                            }
                        } else if (n.props.children) {
                            traverseHeaders(n.props.children);
                        }
                    }
                };

                traverseHeaders(node);
                tableData.headers = headers;
            };

            const extractRows = (node: React.ReactNode): void => {
                const rows: string[][] = [];

                const traverseRows = (n: React.ReactNode): void => {
                    if (Array.isArray(n)) {
                        n.forEach(traverseRows);
                        return;
                    }

                    if (React.isValidElement(n)) {
                        const elementType = n.type;
                        let componentName = '';

                        if (typeof elementType === 'string') {
                            componentName = elementType.toLowerCase();
                        } else {
                            componentName = (elementType as any)?.displayName?.toLowerCase() ||
                                (elementType as any)?.name?.toLowerCase() || '';
                        }

                        if (componentName === 'tr' || componentName === 'rowcomponent') {
                            currentRowCells = [];
                            if (n.props.children) {
                                extractRowCells(n.props.children);
                            }
                            if (currentRowCells.length > 0) {
                                rows.push([...currentRowCells]);
                            }
                            currentRowCells = [];
                        } else if (n.props.children) {
                            traverseRows(n.props.children);
                        }
                    }
                };

                traverseRows(node);
                tableData.rows = rows;
            };

            const extractRowCells = (trChildren: React.ReactNode): void => {
                if (Array.isArray(trChildren)) {
                    trChildren.forEach((child) => {
                        if (React.isValidElement(child)) {
                            const elementType = child.type;
                            let componentName = '';

                            if (typeof elementType === 'string') {
                                componentName = elementType.toLowerCase();
                            } else {
                                componentName = (elementType as any)?.displayName?.toLowerCase() ||
                                    (elementType as any)?.name?.toLowerCase() || '';
                            }

                            if (componentName === 'td' || componentName === 'tdcomponent') {
                                const text = extractTextFromNode(child);
                                if (text && text.trim()) {
                                    currentRowCells.push(text.trim());
                                }
                            } else if ((child.props as any)?.children) {
                                extractRowCells((child.props as any).children);
                            }
                        }
                    });
                }
            };

            const extractTextFromNode = (node: React.ReactNode): string => {
                if (node === null || node === undefined) return '';
                if (typeof node === 'string') return node;
                if (typeof node === 'number') return node.toString();
                if (typeof node === 'boolean') return node.toString();

                if (Array.isArray(node)) {
                    return node.map(extractTextFromNode).join('');
                }

                if (React.isValidElement(node) && node.props) {
                    if (node.props.children) {
                        return extractTextFromNode(node.props.children);
                    }
                }

                return '';
            };

            // 开始处理传入的元素
            processNode(element);

            // 如果结构化解析失败，尝试从文本解析
            if (tableData.headers.length === 0 && tableData.rows.length === 0) {
                console.warn('结构化解析失败，尝试从纯文本解析表格');
                const textContent = extractTextFromNode(element);
                console.log('表格文本内容:', textContent);

                const parsedData = parseTableFromText(textContent);
                tableData.headers = parsedData.headers;
                tableData.rows = parsedData.rows;
            }

            console.log('解析到的表格数据:', tableData);
            return JSON.stringify(tableData);

        } catch (error) {
            console.error('表格数据提取失败:', error);
            return JSON.stringify({headers: [], rows: []});
        }
    };

    // 从纯文本解析表格数据的备用方案
    const parseTableFromText = (text: string): TableData => {
        const result: TableData = {headers: [], rows: []};

        try {
            if (!text || text.trim().length === 0) {
                return result;
            }

            // 清理文本，移除多余的空格和换行
            const cleanText = text.trim().replace(/\s+/g, ' ');

            // 第一步：识别表头
            // 表头通常以"指标"开头，后面跟着时间列
            const timePattern = /(\d{1,2}:\d{2})/g;
            const timeMatches = cleanText.match(timePattern) || [];

            // 查找指标类型（如"指标(5分钟)"）
            const indicatorMatch = cleanText.match(/指标\([^)]*\)/);
            const indicatorType = indicatorMatch ? indicatorMatch[0] : '指标';

            if (timeMatches.length > 0) {
                // 构建表头
                result.headers = [indicatorType, ...timeMatches];

                // 第二步：提取数据行
                // 使用正则表达式识别数据行
                const rowPatterns = [
                    {name: 'open', regex: /open([\d,.-]+)/g},
                    {name: 'high', regex: /high([\d,.-]+)/g},
                    {name: 'low', regex: /low([\d,.-]+)/g},
                    {name: 'close', regex: /close([\d,.-]+)/g},
                    {name: 'volume', regex: /volume([\d,.-]+)/g}
                ];

                // 添加技术指标模式
                const technicalIndicators = [
                    {name: 'SMA', regex: /SMA\([^)]*\)([\d,.-]+)/g},
                    {name: 'EMA', regex: /EMA\([^)]*\)([\d,.-]+)/g},
                    {name: 'RSI', regex: /RSI\([^)]*\)([\d,.-]+)/g},
                    {name: 'BOLL(20)-UB', regex: /BOLL\([^)]*\)-UB([\d,.-]+)/g},
                    {name: 'BOLL(20)-MB', regex: /BOLL\([^)]*\)-MB([\d,.-]+)/g},
                    {name: 'BOLL(20)-LB', regex: /BOLL\([^)]*\)-LB([\d,.-]+)/g}
                ];

                rowPatterns.push(...technicalIndicators);

                for (const pattern of rowPatterns) {
                    const matches = [...text.matchAll(pattern.regex)];
                    if (matches.length > 0) {
                        // 取最后一个匹配，因为可能包含多个匹配
                        const lastMatch = matches[matches.length - 1];
                        const values = lastMatch[1];

                        // 分割数值（基于时间匹配的数量）
                        const valueArray = splitValuesByTimeCount(values, timeMatches.length);

                        if (valueArray.length === timeMatches.length) {
                            result.rows.push([pattern.name, ...valueArray]);
                        }
                    }
                }
            }

            console.log('从文本解析的表格数据:', result);
            return result;

        } catch (error) {
            console.error('从文本解析表格失败:', error);
            return result;
        }
    };

    // 根据时间数量分割数值
    const splitValuesByTimeCount = (values: string, timeCount: number): string[] => {
        const result: string[] = [];

        // 移除逗号，保留小数点和负号
        const cleanValues = values.replace(/,/g, ' ');

        // 使用正则表达式分割数字
        const numberPattern = /-?\d+\.?\d*/g;
        const numberMatches = cleanValues.match(numberPattern) || [];

        // 如果匹配的数量正好等于时间数量，直接返回
        if (numberMatches.length === timeCount) {
            return numberMatches;
        }

        // 如果数量不匹配，尝试按比例分割
        if (numberMatches.length > timeCount) {
            const ratio = Math.floor(numberMatches.length / timeCount);
            for (let i = 0; i < timeCount; i++) {
                const startIndex = i * ratio;
                const endIndex = Math.min((i + 1) * ratio, numberMatches.length);
                const segment = numberMatches.slice(startIndex, endIndex);
                result.push(segment[0]); // 取每个段落的第一个值
            }
        } else if (numberMatches.length > 0) {
            // 如果数值不够，使用现有的数值
            result.push(...numberMatches);
            // 补齐空值
            while (result.length < timeCount) {
                result.push('');
            }
        }

        return result.slice(0, timeCount);
    };
    // 自定义表格样式组件
    const TableComponent: React.FC<any> = React.memo(({children}) => {
        // 使用useRef存储表格索引，避免重渲染
        const tableIndexRef = React.useRef<number>();

        // 只在第一次挂载时分配索引
        if (tableIndexRef.current === undefined) {
            tableIndexRef.current = staticTableCounter++;
        }

        const showChartButton = shouldShowChartForSegment();

        // 提取表格数据
        const tableData = extractTableData(children);
        const tableType = getTableType();

        const handleChartClick = () => {
            if (onTableChartClick) {
                onTableChartClick(tableData, tableIndexRef.current || 0);
            }
        };

        return (
            <div style={{position: 'relative', marginBottom: '16px'}}>
                <div style={{overflowX: 'auto', backgroundColor: getTableBackgroundColor()}}>
                    <table
                        style={{
                            borderCollapse: 'collapse',
                            width: '100%',
                            backgroundColor: getTableBackgroundColor(),
                            borderRadius: '6px',
                            overflow: 'hidden',
                            border: '1px solid #434343',
                            minWidth: '600px'
                        }}
                    >
                        {children}
                    </table>
                </div>
                {showChartButton && (
                    <Button
                        type="text"
                        size="small"
                        icon={<LineChartOutlined/>}
                        onClick={handleChartClick}
                        style={{
                            position: 'absolute',
                            top: '8px',
                            right: '8px',
                            backgroundColor: 'rgba(26, 26, 26, 0.9)',
                            borderColor: '#434343',
                            color: '#1890ff',
                            fontSize: '12px',
                            height: '24px',
                            padding: '0 6px',
                            zIndex: 10,
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
                            backdropFilter: 'blur(4px)'
                        }}
                        title={`${tableType === 'position' ? '持仓价格' : '技术指标'}K线图`}
                    />
                )}
            </div>
        );
    });

    // 设置displayName以帮助调试
    TableComponent.displayName = 'TableComponent';

    // 自定义表头样式组件
    const TheadComponent: React.FC<any> = ({children}) => (
        <thead
            style={{
                backgroundColor: getTableHeaderBackgroundColor(),
                borderBottom: '2px solid #434343'
            }}
        >
        {children}
        </thead>
    );

    // 自定义表格行样式组件
    const RowComponent: React.FC<any> = ({children, index}) => (
        <tr
            style={{
                backgroundColor: getTableRowBackgroundColor(index),
                borderBottom: '1px solid #434343'
            }}
        >
            {children}
        </tr>
    );

    // 自定义表头单元格样式组件
    const ThComponent: React.FC<any> = ({children}) => (
        <th
            style={{
                padding: '12px',
                textAlign: 'left',
                borderRight: '1px solid #434343',
                color: '#ffffff',
                fontWeight: '600',
                fontSize: '13px',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                minWidth: '100px'
            }}
        >
            {children}
        </th>
    );

    // 自定义表格单元格样式组件
    const TdComponent: React.FC<any> = ({children}) => (
        <td
            style={{
                padding: '12px',
                borderRight: '1px solid #434343',
                color: 'rgba(255, 255, 255, 0.9)',
                fontSize: '13px',
                lineHeight: '1.5',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                minWidth: '100px'
            }}
        >
            {children}
        </td>
    );

    // 自定义链接样式组件
    const LinkComponent: React.FC<any> = ({href, children}) => (
        <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            style={{
                color: '#1677ff',
                textDecoration: 'none'
            }}
            onMouseOver={(e) => {
                e.currentTarget.style.textDecoration = 'underline';
            }}
            onMouseOut={(e) => {
                e.currentTarget.style.textDecoration = 'none';
            }}
        >
            {children}
        </a>
    );

    return (
        <div
            style={{
                position: 'relative',
                backgroundColor: getBackgroundColor(),
                color: 'rgba(255, 255, 255, 0.9)',
                padding: '8px',
                borderRadius: '6px',
                fontSize: '13px',
                lineHeight: '1.2',
                fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
                ...style
            }}
        >
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                    table: TableComponent,
                    thead: TheadComponent,
                    tbody: ({children}) => <tbody>{children}</tbody>,
                    tr: RowComponent,
                    th: ThComponent,
                    td: TdComponent,
                    code: (props: any) => {
                        // 获取代码内容生成 hash
                        let codeContent = '';
                        if (typeof props.children === 'string') {
                            codeContent = props.children;
                        } else if (Array.isArray(props.children)) {
                            codeContent = props.children.join('');
                        } else if (props.children && typeof props.children === 'object') {
                            codeContent = String(props.children);
                        }
                        const codeHash = simpleHash(codeContent);
                        return (
                            <CodeBlockComponent
                                {...props}
                                isUser={isUser}
                                chartMode={getChartMode(codeHash)}
                                onToggleChartMode={() => toggleChartMode(codeHash)}
                                onChartModeChange={onChartModeChange}
                            />
                        );
                    },
                    a: LinkComponent,
                    // 其他markdown元素的样式
                    h1: ({children}) => (
                        <h1
                            style={{
                                color: 'rgba(255, 255, 255, 0.95)',
                                fontSize: '24px',
                                fontWeight: '600',
                                margin: '24px 0 16px 0',
                                borderBottom: '1px solid #434343',
                                paddingBottom: '8px'
                            }}
                        >
                            {children}
                        </h1>
                    ),
                    h2: ({children}) => (
                        <h2
                            style={{
                                color: 'rgba(255, 255, 255, 0.95)',
                                fontSize: '20px',
                                fontWeight: '600',
                                margin: '20px 0 12px 0',
                                borderBottom: '1px solid #434343',
                                paddingBottom: '6px'
                            }}
                        >
                            {children}
                        </h2>
                    ),
                    h3: ({children}) => (
                        <h3
                            style={{
                                color: 'rgba(255, 255, 255, 0.95)',
                                fontSize: '18px',
                                fontWeight: '600',
                                margin: '16px 0 10px 0'
                            }}
                        >
                            {children}
                        </h3>
                    ),
                    h4: ({children}) => (
                        <h4
                            style={{
                                color: 'rgba(255, 255, 255, 0.95)',
                                fontSize: '16px',
                                fontWeight: '600',
                                margin: '14px 0 8px 0'
                            }}
                        >
                            {children}
                        </h4>
                    ),
                    p: ({children}) => (
                        <p
                            style={{
                                margin: '12px 0',
                                lineHeight: '1.6'
                            }}
                        >
                            {children}
                        </p>
                    ),
                    ul: ({children}) => (
                        <ul
                            style={{
                                margin: '12px 0',
                                paddingLeft: '20px'
                            }}
                        >
                            {children}
                        </ul>
                    ),
                    ol: ({children}) => (
                        <ol
                            style={{
                                margin: '12px 0',
                                paddingLeft: '20px'
                            }}
                        >
                            {children}
                        </ol>
                    ),
                    li: ({children}) => (
                        <li
                            style={{
                                margin: '6px 0',
                                lineHeight: '1.6'
                            }}
                        >
                            {children}
                        </li>
                    ),
                    blockquote: ({children}) => (
                        <blockquote
                            style={{
                                borderLeft: '4px solid #1677ff',
                                padding: '12px 16px',
                                margin: '16px 0',
                                backgroundColor: getTableBackgroundColor(),
                                borderRadius: '0 6px 6px 0',
                                fontStyle: 'italic',
                                color: 'rgba(255, 255, 255, 0.8)'
                            }}
                        >
                            {children}
                        </blockquote>
                    ),
                    strong: ({children}) => (
                        <strong
                            style={{
                                color: 'rgba(255, 255, 255, 0.95)',
                                fontWeight: '600'
                            }}
                        >
                            {children}
                        </strong>
                    ),
                    em: ({children}) => (
                        <em
                            style={{
                                color: 'rgba(255, 255, 255, 0.85)',
                                fontStyle: 'italic'
                            }}
                        >
                            {children}
                        </em>
                    ),
                    hr: () => (
                        <hr
                            style={{
                                border: 'none',
                                borderTop: '1px solid #434343',
                                margin: '24px 0'
                            }}
                        />
                    )
                }}
            >
                {preprocessContent(content, segmentTitle)}
            </ReactMarkdown>
        </div>
    );
};

export default MarkdownRenderer;