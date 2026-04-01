/**
 * @file IndicatorChartModal.tsx
 * @description 技术指标K线图弹窗组件，提供实时数据的可视化展示与技术指标分析功能。
 * 该组件支持多种技术指标（EMA, RSI, BOLL等）的动态切换与多周期展示。
 *
 * 核心功能包括：
 * 1. 实时K线数据获取与解析（支持 JSON 及 Markdown 表格格式）。
 * 2. 技术指标的自动化计算（本地计算与 API 结合）。
 * 3. 响应式图表布局，支持全屏模式及各种时间帧切换。
 * 4. 灵活的 UI 控制，支持图标化指标切换按钮及详细的悬浮提示。
 *
 * @author Page
 * @date 2026-02-11
 */

import React, {useEffect, useMemo, useState} from 'react';
import {Alert, Button, Checkbox, Drawer, Empty, message, Modal, Segmented, Space, Spin, Tooltip} from 'antd';
import {
    AreaChartOutlined,
    BarChartOutlined,
    CloseOutlined,
    CompressOutlined,
    ExpandOutlined,
    LineChartOutlined,
    SettingOutlined
} from '@ant-design/icons';

/**
 * 导入必要的图表组件及类型定义
 * CandlestickData: 基础K线数据结构（开高低收成交量）
 * COLORS: 图表颜色配置系统（用于右侧指标控制栏颜色）
 * LightweightCandlestickChart: 基于 lightweight-charts 的 K 线图组件
 * tradingService: 与后端进行交易数据交互的服务层
 */
import {CandlestickData, COLORS} from '../../pages/trading/components/CandlestickChart';
import LightweightCandlestickChart, {KLineBar} from '../charts/LightweightCandlestickChart';
import {IndicatorDataPoint, TechnicalIndicatorData, tradingService} from '../../services/tradingService';
import {getIndicatorValues} from '../../utils/indicatorValues';

/**
 * 将 CandlestickData[] 转换为 KLineBar[] 格式
 * 用于 LightweightCandlestickChart 组件
 */
const candlestickDataToKLineBars = (candles: CandlestickData[]): KLineBar[] => {
    return candles
        .filter(c => c && Number.isFinite(c.timestamp))
        .map(c => ({
            time: c.timestamp,
            open: c.open,
            high: c.high,
            low: c.low,
            close: c.close,
            volume: c.volume || 0,
            confirmed: c.confirm === 1
        }))
        .sort((a, b) => a.time - b.time);
};

/**
 * 将后端指标数据格式转换为 LightweightCandlestickChart 所需的扁平化格式
 * 后端格式: {EMA: {data: {values: [{timestamp, multiPeriodValues: {ema_12: value}}]}}}
 * 表格解析格式: {BOLL: {data: {values: [{timestamp, upperBand, middleBand, lowerBand}]}}}
 * 目标格式: {EMA_ema_12: [{time, value}]} 或 {BOLL_boll_20: [{time, upper, middle, lower}]}
 */
const transformIndicatorsForChart = (rawIndicators: Record<string, TechnicalIndicatorData> | undefined) => {
    if (!rawIndicators) return undefined;

    const result: Record<string, Array<{
        time: number;
        value?: number;
        upper?: number;
        middle?: number;
        lower?: number;
        diff?: number;
        signal?: number;
        histogram?: number;
        k?: number;
        d?: number;
        j?: number;
    }>> = {};

    Object.entries(rawIndicators).forEach(([key, indicator]) => {
        const values = indicator?.data?.values || indicator?.values;
        if (!values || !Array.isArray(values)) return;

        // 特殊处理 BOLL：检查是否有直接的 upperBand/middleBand/lowerBand 字段（表格解析格式）
        if (key === 'BOLL') {
            const hasDirectBollFields = values.some((v: any) =>
                v.upperBand !== undefined || v.middleBand !== undefined || v.lowerBand !== undefined
            );

            if (hasDirectBollFields) {
                const period = indicator?.data?.period || indicator?.period || 20;
                const seriesKey = `BOLL_boll_${period}`;
                result[seriesKey] = values.map((v: any) => ({
                    time: v.timestamp,
                    upper: v.upperBand ?? undefined,
                    middle: v.middleBand ?? undefined,
                    lower: v.lowerBand ?? undefined,
                }));
                return; // 跳过后面的 multiPeriodValues 处理
            }
        }

        values.forEach((v: IndicatorDataPoint) => {
            const multiPeriod = v.multiPeriodValues;
            if (!multiPeriod) return;

            Object.entries(multiPeriod).forEach(([periodKey, periodData]: [string, any]) => {
                const seriesKey = `${key}_${periodKey}`;

                if (key === 'MACD') {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        diff: periodData.diff,
                        signal: periodData.dea,
                        histogram: periodData.macd,
                    });
                } else if (key === 'BOLL') {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        upper: periodData.upper,
                        middle: periodData.middle,
                        lower: periodData.lower,
                    });
                } else if (key === 'KDJ') {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        k: periodData.k,
                        d: periodData.d,
                        j: periodData.j,
                    });
                } else {
                    if (!result[seriesKey]) result[seriesKey] = [];
                    result[seriesKey].push({
                        time: v.timestamp,
                        value: periodData,
                    });
                }
            });
        });
    });

    // 按时间排序
    Object.values(result).forEach(arr => arr.sort((a, b) => a.time - b.time));
    return result;
};

/**
 * 组件 Props 接口定义
 * @property visible 控制弹窗是否显示
 * @property onClose 弹窗关闭时的回调函数
 * @property indicatorData 初始加载的技术指标文本数据（Markdown表格或JSON）
 * @property apiKeyId 用于 API 调用的密钥 ID
 * @property instId 交易对标识（如 BTC-USDT-SWAP）
 */
interface IndicatorChartModalProps {
    visible: boolean;
    onClose: () => void;
    indicatorData: string;
    apiKeyId?: number | null;
    instId?: string | null;
}

/**
 * 技术指标K线图弹窗核心组件实现
 * 采用 React 函数式组件及 Hooks 架构。
 */
const IndicatorChartModal: React.FC<IndicatorChartModalProps> = ({
                                                                     visible,
                                                                     onClose,
                                                                     indicatorData,
                                                                     apiKeyId = null,
                                                                     instId = null
                                                                 }) => {
    /**
     * 时间帧常量定义：支持从 1 分钟到 1 天的多种周期
     * 采样率常量定义：控制图表展示的数据点数量，平衡性能与细节
     */
    const TIMEFRAME_OPTIONS = ['1m', '5m', '1h', '4h', '1d'] as const;
    const LIMIT_OPTIONS = [10, 30, 60, 90, 120, 150] as const;

    /**
     * 规范化时间帧输入，确保输入值在预定义的合法范围内
     * @param value 原始时间帧字符串
     * @returns 规范化后的合法时间帧字符串
     */
    const normalizeTimeFrame = (value: unknown): typeof TIMEFRAME_OPTIONS[number] => {
        const v = String(value ?? '').trim().toLowerCase();
        if (v === '1m') return '1m';
        if (v === '5m') return '5m';
        if (v === '1h') return '1h';
        if (v === '4h') return '4h';
        if (v === '1d') return '1d';
        /** 默认返回 4 小时周期，这是加密货币交易中常用的趋势分析周期 */
        return '4h';
    };

    /**
     * 规范化采样率限制，确保数值在 LIMIT_OPTIONS 预定义的范围内
     * @param value 原始数值
     * @returns 规范化后的采样率数值
     */
    const normalizeLimit = (value: unknown): typeof LIMIT_OPTIONS[number] => {
        const n = typeof value === 'number' ? value : Number(value);
        if (Number.isFinite(n) && (LIMIT_OPTIONS as readonly number[]).includes(n)) {
            return n as typeof LIMIT_OPTIONS[number];
        }
        /** 默认 120 个数据点，能够覆盖大多数指标的计算窗口 */
        return 120;
    };

    /**
     * 组件生命周期管理：当弹窗显示时，输出当前状态日志以便于调试
     * 记录关键参数如 API Key、合约 ID 以及数据长度
     */
    useEffect(() => {
        if (visible) {
            console.log('=== IndicatorChartModal 状态快照 ===');
            console.log(' - 是否显示:', visible);
            console.log(' - 密钥 ID:', apiKeyId);
            console.log(' - 合约 ID:', instId);
            console.log(' - 数据源长度:', indicatorData?.length);
        }
    }, [visible, apiKeyId, instId]);

    /**
     * 状态管理定义
     * chartData: 原始 K 线数据数组
     * bars: 经过转换的图表专用 Bar 数据（使用 useMemo 优化计算性能）
     * indicators: 存储各类型技术指标的计算结果
     * loading: 异步加载/解析数据时的加载状态
     * error: 存储过程中可能发生的错误信息
     * timeFrame: 当前选中的时间周期
     * isFullscreen: 是否处于全屏沉浸式模式
     * useApiData: 是否启用从后端 API 获取实时数据（全屏模式下默认开启）
     */
    const [chartData, setChartData] = useState<CandlestickData[]>([]);
    const bars = useMemo(() => candlestickDataToKLineBars(chartData), [chartData]);
    const [indicators, setIndicators] = useState<{
        EMA?: TechnicalIndicatorData;
        SMA?: TechnicalIndicatorData;
        WMA?: TechnicalIndicatorData;
        BOLL?: TechnicalIndicatorData;
        RSI?: TechnicalIndicatorData;
        MACD?: TechnicalIndicatorData;
        KDJ?: TechnicalIndicatorData;
        CCI?: TechnicalIndicatorData;
        ATR?: TechnicalIndicatorData;
        OBV?: TechnicalIndicatorData;
        ADX?: TechnicalIndicatorData;
    }>({});
    const transformedIndicators = useMemo(() => transformIndicatorsForChart(indicators), [indicators]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [timeFrame, setTimeFrame] = useState<string>('4H');
    const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
    const [useApiData, setUseApiData] = useState<boolean>(false);

    /**
     * UI 交互与过滤配置状态
     * limit: 当前 K 线采样数量
     * enabledIndicators: 当前开启计算并展示的指标列表，初始默认开启 EMA, RSI, BOLL
     * visibleIndicators: 详细控制每个指标（含多周期）在图表中的显示/隐藏状态
     * indicatorDrawerVisible: 技术指标选择与配置侧边抽屉的开关状态
     */
    const [limit, setLimit] = useState<number>(120);
    const [enabledIndicators, setEnabledIndicators] = useState<string[]>(['EMA', 'RSI', 'BOLL']);
    const [visibleIndicators, setVisibleIndicators] = useState<Record<string, boolean>>({});
    const [indicatorDrawerVisible, setIndicatorDrawerVisible] = useState<boolean>(false);

    /**
     * 技术指标元数据映射表
     * 该对象定义了每个指标的：
     * 1. 简短名称 (name)
     * 2. 语义化图标 (icon)：帮助用户快速识别指标类型
     * 3. 功能描述 (desc)：通过 Tooltip 展示，方便非专业用户理解指标用途
     */
    const INDICATOR_METADATA: Record<string, { name: string, icon: React.ReactNode, desc: string }> = {
        EMA: {name: 'EMA', icon: <LineChartOutlined/>, desc: '指数移动平均线 - 用于识别趋势方向，对近期价格更敏感'},
        SMA: {name: 'SMA', icon: <LineChartOutlined/>, desc: '简单移动平均线 - 基础趋势指标，计算一段时期内的平均价格'},
        WMA: {name: 'WMA', icon: <LineChartOutlined/>, desc: '加权移动平均线 - 赋予近期价格更高的权重，反应更快'},
        BOLL: {name: 'BOLL', icon: <LineChartOutlined/>, desc: '布林带 - 通过标准差衡量价格波动范围及潜在压力/支撑位'},
        RSI: {
            name: 'RSI',
            icon: <AreaChartOutlined/>,
            desc: '相对强弱指标 - 衡量价格变动的速度和变化，识别超买超卖状态'
        },
        MACD: {
            name: 'MACD',
            icon: <BarChartOutlined/>,
            desc: '平滑异同移动平均线 - 趋势动量指标，显示两条均线之间的关系'
        },
        KDJ: {name: 'KDJ', icon: <AreaChartOutlined/>, desc: '随机指标 - 通过价格波动的强弱来寻找潜在的趋势反转点'},
        CCI: {name: 'CCI', icon: <AreaChartOutlined/>, desc: '顺势指标 - 衡量价格是否偏离统计范围，适用于超买超卖判断'},
        ATR: {name: 'ATR', icon: <BarChartOutlined/>, desc: '平均真实波幅 - 纯粹衡量市场波动率的指标，不代表方向'},
        OBV: {name: 'OBV', icon: <BarChartOutlined/>, desc: '能量潮指标 - 将成交量与价格变化相结合，判断趋势强度'},
        ADX: {name: 'ADX', icon: <AreaChartOutlined/>, desc: '平均趋向指数 - 衡量当前趋势的强度，无论趋势向上还是向下'}
    };

    /**
     * 核心交互逻辑：切换一类指标的整体可见性
     * @param type 指标类型标识（如 'EMA'）
     *
     * 该逻辑实现了“一键开关”：
     * 1. 查找所有以该类型开头的子指标（如 EMA_5, EMA_20, EMA_60）。
     * 2. 判断当前是否有任何一个子指标是可见的。
     * 3. 如果有可见的，则将该类型下所有指标全部隐藏；反之则全部显示。
     * 4. 这种方式极大简化了用户在多周期指标共存时的操作成本。
     */
    const toggleIndicatorTypeVisibility = (type: string) => {
        /** 筛选出所有相关的键名 */
        const relatedKeys = Object.keys(visibleIndicators).filter(k => k.startsWith(type + '_'));
        if (relatedKeys.length === 0) return;

        /** 确定当前整体状态：只要有一个是亮的，下次点击就全部熄灭 */
        const isAnyVisible = relatedKeys.some(k => visibleIndicators[k]);

        /** 批量生成新的状态快照 */
        const nextVisible = {...visibleIndicators};
        relatedKeys.forEach(k => {
            nextVisible[k] = !isAnyVisible;
        });

        /** 同步状态并触发组件重绘 */
        setVisibleIndicators(nextVisible);
    };

    /**
     * 数据解析辅助：解析 OHLC（开盘、最高、最低、收盘）数值
     * @param value 原始字符串值
     * @param field 字段名称，用于错误日志记录
     *
     * 该函数会：
     * 1. 剔除数值中的非数字干扰字符（如千分位逗号）。
     * 2. 验证解析结果的有效性（非 NaN 且为有限数）。
     * 3. 强制转换并限制数值精度，确保图表渲染的一致性。
     */
    const parseOHLCValue = (value: string, field: string): number => {
        /** 空值检查：如果数据缺失，则记录警告并返回 0 */
        if (!value || value.trim() === '') {
            console.warn(`[数据校验] ${field} 值为空`);
            return 0;
        }

        /** 清理字符串：只保留数字、小数点和负号 */
        const cleanValue = value.replace(/[^\d.-]/g, '');
        const num = parseFloat(cleanValue);

        /** 有效性检查：防止非法数值进入计算逻辑 */
        if (isNaN(num) || !isFinite(num)) {
            console.warn(`[数据校验] ${field} 解析失败: ${value} -> ${cleanValue}`);
            return 0;
        }

        /** 逻辑校验：价格数据原则上不应为负数或零 */
        if (num <= 0) {
            console.warn(`[数据校验] ${field} 值无效（小于等于0）: ${num}`);
            return 0;
        }

        /** 精度控制：统一保留两位小数 */
        return Number(num.toFixed(2));
    };


    /**
     * 技术指标数值解析
     * @param value 原始指标值字符串
     *
     * 与价格数据不同，指标数据可能包含 NaN（如移动平均线在初期数据不足时）
     * 因此该函数在解析失败时返回 NaN，而不是 0，以避免在图表上产生错误的连线
     */
    const parseIndicatorValue = (value: string): number => {
        if (!value || value.trim() === '') {
            return NaN;
        }
        const cleanValue = value.replace(/[^\d.-]/g, '');
        const num = parseFloat(cleanValue);
        return isNaN(num) || !isFinite(num) ? NaN : num;
    };

    /**
     * 时间解析与标准化：将各种格式的时间字符串转换为统一的时间戳（毫秒）
     * @param timeStr 时间字符串，支持 "MM/DD HH:mm", "HH:mm", "YYYY-MM-DD HH:mm"
     *
     * 该函数是图表对齐的核心，通过正则表达式匹配多种常见时间格式：
     * 1. 尝试匹配月/日 格式，并自动补全当前年份。
     * 2. 尝试匹配纯时间格式，并自动补全当前日期。
     * 3. 尝试匹配标准完整日期时间格式。
     */
    const parseTimeToTimestamp = (timeStr: string): number | null => {
        if (!timeStr || timeStr.trim() === '') {
            return null;
        }

        let match: RegExpMatchArray | null = null;
        let date: Date;

        /** 格式匹配1: "12/18 20:00" - 这种格式常出现在简洁版行情表格中 */
        match = timeStr.match(/^(\d{1,2})\/(\d{1,2})\s+(\d{1,2}):(\d{2})$/);
        if (match) {
            const month = parseInt(match[1]) - 1;
            const day = parseInt(match[2]);
            const hours = parseInt(match[3]);
            const minutes = parseInt(match[4]);

            date = new Date();
            date.setFullYear(date.getFullYear(), month, day);
            date.setHours(hours, minutes, 0, 0);
            return date.getTime();
        }

        /** 格式匹配2: "20:00" - 仅包含时间，假设为当天数据 */
        match = timeStr.match(/^(\d{1,2}):(\d{2})$/);
        if (match) {
            const hours = parseInt(match[1]);
            const minutes = parseInt(match[2]);

            date = new Date();
            date.setHours(hours, minutes, 0, 0);
            return date.getTime();
        }

        /** 格式匹配3: 标准 ISO 或 类似格式 "2024-12-18 20:00" */
        match = timeStr.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{2})$/);
        if (match) {
            const year = parseInt(match[1]);
            const month = parseInt(match[2]) - 1;
            const day = parseInt(match[3]);
            const hours = parseInt(match[4]);
            const minutes = parseInt(match[5]);

            date = new Date(year, month, day, hours, minutes, 0, 0);
            return date.getTime();
        }

        /** 如果所有格式都无法匹配，则放弃解析并输出警告 */
        console.warn(`[时间解析] 无法识别的日期格式: ${timeStr}`);
        return null;
    };

    /**
     * 智能时间顺序检测
     * @param timeColumns 时间列数组
     *
     * 不同的数据源（如不同的交易所或分析工具）提供的数据顺序可能不同：
     * - 正序 (Normal): [最旧, ..., 最新]
     * - 倒序 (Reversed): [最新, ..., 最旧]
     *
     * 该函数通过抽样对比时间戳，自动识别数据方向，确保 K 线图的 X 轴渲染正确。
     */
    const detectTimeOrder = (timeColumns: string[]): 'reversed' | 'normal' | 'unknown' => {
        if (timeColumns.length < 2) return 'unknown';

        /** 解析前几个时间点的时间戳 */
        const timestamps = timeColumns
            .map(timeStr => parseTimeToTimestamp(timeStr))
            .filter((ts): ts is number => ts !== null);

        if (timestamps.length < 2) return 'unknown';

        /** 检查时间戳是否呈递减趋势（即倒序） */
        const isReversed = timestamps[0] > timestamps[timestamps.length - 1];

        console.log(`[顺序检测] 检测结果: ${isReversed ? '倒序 (最新在前)' : '正序 (最旧在前)'}`);
        return isReversed ? 'reversed' : 'normal';
    };

    /**
     * 核心业务逻辑：解析并转换技术指标表格数据
     * @param data 原始字符串数据（Markdown 或 JSON）
     *
     * 该函数是一个复杂的管道流，负责：
     * 1. 格式探测：自动识别是标准 JSON 还是 Markdown 表格。
     * 2. 结构提取：从非结构化文本中提取出表头、行数据以及时间帧信息。
     * 3. 数据清洗：识别 OHLC 各行，并将文本转换为可绘图的数值对象。
     * 4. 排序修正：无论原始数据如何，输出统一的时间正序数组。
     */
    const parseIndicatorData = (data: string) => {
        let currentInfoTimeFrame = timeFrame;

        try {
            let tableData;

            /** 阶段1: 格式解析 */
            try {
                /** 尝试作为 JSON 解析 */
                tableData = JSON.parse(data);
            } catch (e) {
                /** 解析失败则尝试作为 Markdown 表格处理 */
                console.log('[数据解析] 尝试解析 Markdown 表格格式');
                const lines = data.split('\n').filter(line => line.trim() !== '');
                if (lines.length < 3) {
                    throw new Error('Markdown表格行数不足，无法构成有效数据');
                }

                /** 解析表头：使用 "|" 作为分隔符，并清理多余空格 */
                const headers = lines[0].split('|')
                    .map(h => h.trim())
                    .filter((h, i, arr) => {
                        if (i === 0 && h === '') return false;
                        if (i === arr.length - 1 && h === '') return false;
                        return true;
                    });

                /** 解析数据行：跳过表头和分隔行 (|---|) */
                const rows = lines.slice(2).map(line => {
                    return line.split('|')
                        .map(cell => cell.trim())
                        .filter((cell, i, arr) => {
                            if (i === 0 && cell === '') return false;
                            if (i === arr.length - 1 && cell === '') return false;
                            return true;
                        });
                });

                tableData = {headers, rows};
            }

            const result: CandlestickData[] = [];
            const indicatorData: any = {};

            /** 阶段2: 基础验证 */
            if (!tableData.headers || !tableData.rows || tableData.rows.length === 0) {
                console.warn('[数据解析] 数据结构不完整');
                return {candlestickData: [], indicators: {}, timeFrame: '5m'};
            }

            /** 阶段3: 提取元信息 */
            if (tableData.headers && tableData.headers.length > 0) {
                const firstHeader = tableData.headers[0];
                if (firstHeader && firstHeader.trim() !== '') {
                    currentInfoTimeFrame = firstHeader.trim();
                }
            }

            /** 阶段4: 数据处理循环 */
            const timeColumns = tableData.headers.slice(1);
            const timeOrder = detectTimeOrder(timeColumns);
            const isTimeReversed = timeOrder !== 'normal';

            /** 定位核心数据行：不区分大小写查找 Open/High/Low/Close/Volume */
            const openRow = tableData.rows.find((row: string[]) => row[0] && row[0].toLowerCase().includes('open'));
            const highRow = tableData.rows.find((row: string[]) => row[0] && row[0].toLowerCase().includes('high'));
            const lowRow = tableData.rows.find((row: string[]) => row[0] && row[0].toLowerCase().includes('low'));
            const closeRow = tableData.rows.find((row: string[]) => row[0] && row[0].toLowerCase().includes('close'));
            const volumeRow = tableData.rows.find((row: string[]) => row[0] && (row[0].toLowerCase().includes('volume') || row[0].toLowerCase() === 'vol'));

            if (!openRow || !highRow || !lowRow || !closeRow) {
                console.warn('[数据解析] 缺少必要的 OHLC 数据行');
                return {candlestickData: [], indicators: {}, timeFrame: '5m'};
            }

            /** 迭代解析每一列数据 */
            for (let i = 0; i < timeColumns.length; i++) {
                const timeStr = timeColumns[i];
                let timestamp = parseTimeToTimestamp(timeStr);

                /** 如果时间解析失败，则根据索引和预设间隔生成一个伪时间戳，保证图表不崩溃 */
                if (timestamp === null) {
                    const interval = 5 * 60 * 1000;
                    /** 默认5分钟间隔 */
                    timestamp = Date.now() - (isTimeReversed ? i : (timeColumns.length - 1 - i)) * interval;
                }

                /** 解析并验证单根 K 线数据 */
                const open = parseOHLCValue(openRow[i + 1], 'open');
                const high = parseOHLCValue(highRow[i + 1], 'high');
                const low = parseOHLCValue(lowRow[i + 1], 'low');
                const close = parseOHLCValue(closeRow[i + 1], 'close');
                const volume = volumeRow ? parseOHLCValue(volumeRow[i + 1], 'volume') : 0;

                /** 只有满足 OHLC 逻辑关系的数据才会被采纳 */
                if (open > 0 && high >= low && high >= open && high >= close) {
                    result.push({
                        timestamp,
                        open,
                        high,
                        low,
                        close,
                        volume,
                        confirm: (isTimeReversed ? i === 0 : i === timeColumns.length - 1) ? 0 : 1
                    });
                }
            }

            /** 最终输出必须按时间升序，这是所有主流图表库的通用要求 */
            result.sort((a, b) => a.timestamp - b.timestamp);
            console.log(`IndicatorChart K线数据已按时间正序排序 (${result.length}条):`);
            result.forEach((item, index) => {
                const isFirst = index === 0;
                const isLast = index === result.length - 1;
                console.log(`${index + 1}. ${new Date(item.timestamp).toLocaleString('zh-CN')} -> ${item.open}/${item.high}/${item.low}/${item.close}${isFirst ? ' [最旧K线]' : ''}${isLast ? ' [当前K线]' : ''}`);
            });

            // 改进的技术指标行识别逻辑
            const isValidIndicatorRow = (row: string[]): boolean => {
                const name = row[0]?.toLowerCase().trim();
                if (!name || name.length < 2) return false;

                // 检查是否为OHLC或Volume行
                const ohlcKeywords = ['open', 'high', 'low', 'close', 'volume'];
                if (ohlcKeywords.some(keyword => name.includes(keyword))) return false;

                // 检查是否为有效的技术指标格式 - 支持 x(y) 和 x(y)-z 格式
                const indicatorPatterns = [
                    /^ema\(\d+\)(?:-value)?$/i,           // EMA(20) 或 EMA(20)-VALUE
                    /^sma\(\d+\)(?:-value)?$/i,           // SMA(50) 或 SMA(50)-VALUE
                    /^wma\(\d+\)(?:-value)?$/i,           // WMA(30) 或 WMA(30)-VALUE
                    /^rsi\(\d+\)(?:-value)?$/i,           // RSI(14) 或 RSI(14)-VALUE
                    /^boll\(\d+\)(?:-(ub|mb|lb))?$/i,    // BOLL(20), BOLL(20)-UB, BOLL(20)-MB, BOLL(20)-LB
                    /^macd\(\d+(?:\s*,\s*\d+){0,2}\)(?:-(dif|diff|dea|bar|macd|hist|value))?$/i,
                    /^kdj\(\d+\)(?:-value)?$/i,           // KDJ(9) 或 KDJ(9)-VALUE
                    /^cci\(\d+\)(?:-value)?$/i,           // CCI(14) 或 CCI(14)-VALUE
                    /^atr\(\d+\)(?:-value)?$/i,           // ATR(14) 或 ATR(14)-VALUE
                    /^obv\(\d+\)(?:-value)?$/i,           // OBV(20) 或 OBV(20)-VALUE
                    /^adx\(\d+\)(?:-value)?$/i            // ADX(14) 或 ADX(14)-VALUE
                ];

                return indicatorPatterns.some(pattern => pattern.test(name));
            };

            const indicatorRows = tableData.rows.filter(isValidIndicatorRow);

            console.log('找到的技术指标行:', indicatorRows);
            console.log('开始处理技术指标数据...');

            const macdRowRegex = /^macd\(([^)]+)\)(?:-(dif|diff|dea|bar|macd|hist|value))?$/i;
            const macdBuckets: Record<string, {
                fastPeriod: number;
                valuesByTs: Record<number, { diff?: number; dea?: number; macd?: number }>
            }> = {};

            // 简化的技术指标数据处理逻辑
            for (const indicatorRow of indicatorRows) {
                const indicatorName = indicatorRow[0];
                if (!indicatorName) continue;

                console.log(`处理技术指标: ${indicatorName}`);

                const macdMatch = indicatorName.toLowerCase().trim().match(macdRowRegex);
                if (macdMatch) {
                    const periodsRaw = macdMatch[1].replace(/\s+/g, '');
                    const component = (macdMatch[2] || 'value').toLowerCase();
                    const macdKey = `macd_${periodsRaw}`;
                    const fastPeriod = Number(periodsRaw.split(',')[0] || 0) || 12;

                    if (!macdBuckets[macdKey]) {
                        macdBuckets[macdKey] = {fastPeriod, valuesByTs: {}};
                    }

                    result.forEach((candlestick, index) => {
                        const tableIndex = isTimeReversed ? (result.length - 1 - index) : index;
                        const valueStr = indicatorRow[tableIndex + 1];
                        const value = parseIndicatorValue(valueStr);
                        if (isNaN(value) || !isFinite(value)) return;

                        if (!macdBuckets[macdKey].valuesByTs[candlestick.timestamp]) {
                            macdBuckets[macdKey].valuesByTs[candlestick.timestamp] = {};
                        }

                        const entry = macdBuckets[macdKey].valuesByTs[candlestick.timestamp];
                        if (component === 'dif' || component === 'diff') entry.diff = value;
                        else if (component === 'dea') entry.dea = value;
                        else if (component === 'bar' || component === 'macd' || component === 'hist' || component === 'value') entry.macd = value;
                    });

                    continue;
                }

                // 修复技术指标数据映射 - 确保与排序后的K线数据正确对应
                const indicatorValues: { [timestamp: number]: number } = {};

                // 根据时间顺序调整映射关系
                result.forEach((candlestick, index) => {
                    let tableIndex;

                    if (isTimeReversed) {
                        // 如果表格是倒序（最新在左，最旧在右）
                        // result是正序（最旧在左，最新在右）
                        // result[0] (最旧) -> table[last]
                        // result[last] (最新) -> table[0]
                        tableIndex = result.length - 1 - index;
                    } else {
                        // 如果表格是正序（最旧在左，最新在右）
                        // result[0] (最旧) -> table[0]
                        tableIndex = index;
                    }

                    const valueStr = indicatorRow[tableIndex + 1];
                    const value = parseIndicatorValue(valueStr);

                    if (!isNaN(value) && isFinite(value)) {
                        indicatorValues[candlestick.timestamp] = value;
                        console.log(`指标映射: ${indicatorName}[${index}] @ K线位置${index}(${new Date(candlestick.timestamp).toLocaleString('zh-CN')}) <- 表格列${tableIndex}(${timeColumns[tableIndex]}) = ${value}`);
                    } else {
                        console.warn(`跳过无效指标值: ${indicatorName}[${index}] @ 表格列${tableIndex} = "${valueStr}"`);
                    }
                });

                // 改进的技术指标识别逻辑 - 支持 x(y) 和 x(y)-z 格式
                const getIndicatorTypeAndPeriod = (name: string): {
                    type: string;
                    period: number;
                    element?: string
                } | null => {
                    const patterns = [
                        // EMA, SMA, WMA, RSI 等单一值指标
                        {regex: /^ema\(?(\d+)\)?(?:-value)?$/i, type: 'EMA', element: 'value'},
                        {regex: /^sma\(?(\d+)\)?(?:-value)?$/i, type: 'SMA', element: 'value'},
                        {regex: /^wma\(?(\d+)\)?(?:-value)?$/i, type: 'WMA', element: 'value'},
                        {regex: /^rsi\(?(\d+)\)?(?:-value)?$/i, type: 'RSI', element: 'value'},

                        // 布林带指标
                        {regex: /^boll\(?(\d+)\)?$/i, type: 'BOLL', element: 'value'}, // BOLL(20)
                        {regex: /^boll\(?(\d+)\)?-(ub|upper)$/i, type: 'BOLL', element: 'upperBand'},
                        {regex: /^boll\(?(\d+)\)?-(mb|mid|middle)$/i, type: 'BOLL', element: 'middleBand'},
                        {regex: /^boll\(?(\d+)\)?-(lb|lower)$/i, type: 'BOLL', element: 'lowerBand'},

                        // 其他指标
                        {regex: /^macd\(?(\d+)\)?(?:-value)?$/i, type: 'MACD', element: 'value'},
                        {regex: /^kdj\(?(\d+)\)?(?:-value)?$/i, type: 'KDJ', element: 'value'},
                        {regex: /^cci\(?(\d+)\)?(?:-value)?$/i, type: 'CCI', element: 'value'},
                        {regex: /^atr\(?(\d+)\)?(?:-value)?$/i, type: 'ATR', element: 'value'},
                        {regex: /^obv\(?(\d+)\)?(?:-value)?$/i, type: 'OBV', element: 'value'}
                        ,
                        {regex: /^adx\(?(\d+)\)?(?:-value)?$/i, type: 'ADX', element: 'value'}
                    ];

                    for (const pattern of patterns) {
                        const match = name.toLowerCase().trim().match(pattern.regex);
                        if (match) {
                            return {
                                type: pattern.type,
                                period: parseInt(match[1]),
                                element: pattern.element
                            };
                        }
                    }
                    return null;
                };

                const indicatorInfo = getIndicatorTypeAndPeriod(indicatorName);
                if (!indicatorInfo) {
                    console.warn(`无法识别的技术指标: ${indicatorName}`);
                    continue;
                }

                console.log(`识别到技术指标: ${indicatorInfo.type}, 周期: ${indicatorInfo.period}, 元素: ${indicatorInfo.element || 'value'}`);

                // 直接使用K线数据的时间戳，简化数据结构
                const values = result.map((candlestick) => {
                    const value = indicatorValues[candlestick.timestamp] !== undefined ?
                        Number(indicatorValues[candlestick.timestamp].toFixed(2)) : null;

                    const dataPoint: IndicatorDataPoint = {
                        timestamp: candlestick.timestamp,
                        value: value || undefined
                    };

                    // 添加动态属性以便CandlestickChart组件识别
                    const indicatorKey = indicatorInfo.type.toLowerCase() + '_' + indicatorInfo.period;
                    dataPoint[indicatorKey] = value;

                    // 为BOLL指标添加特殊的多元素支持
                    if (indicatorInfo.type === 'BOLL' && indicatorInfo.element) {
                        if (indicatorInfo.element === 'upperBand') {
                            dataPoint.upperBand = value || undefined;
                        } else if (indicatorInfo.element === 'middleBand') {
                            dataPoint.middleBand = value || undefined;
                        } else if (indicatorInfo.element === 'lowerBand') {
                            dataPoint.lowerBand = value || undefined;
                        }
                    }

                    // 为多周期指标添加multiPeriodValues
                    dataPoint.multiPeriodValues = {
                        [indicatorKey]: value !== null ? value : 0 // 确保不为null，虽然类型定义允许undefined但不允许null赋值给number
                    };

                    return dataPoint;
                });

                // 创建符合TechnicalIndicatorData接口的数据结构
                const updateMultiPeriodIndicator = (type: string, dataKey: keyof typeof indicatorData) => {
                    if (!indicatorData[dataKey]) {
                        indicatorData[dataKey] = {
                            success: true,
                            message: 'success',
                            data: {
                                metricName: type,
                                period: indicatorInfo.period, // 默认周期
                                isMultiPeriod: true,
                                periods: [indicatorInfo.period],
                                values: values,
                                createdAt: new Date().toISOString()
                            }
                        };
                    } else {
                        // 合并多周期数据
                        const existingData = indicatorData[dataKey].data;
                        if (!existingData.periods.includes(indicatorInfo.period)) {
                            existingData.periods.push(indicatorInfo.period);
                            existingData.periods.sort((a: number, b: number) => a - b);
                        }

                        // 合并values
                        existingData.values = existingData.values.map((v: IndicatorDataPoint, i: number) => {
                            const newValue = values[i];
                            return {
                                ...v,
                                ...newValue,
                                multiPeriodValues: {
                                    ...v.multiPeriodValues,
                                    ...newValue.multiPeriodValues
                                }
                            };
                        });
                    }
                };

                switch (indicatorInfo.type) {
                    case 'EMA':
                        updateMultiPeriodIndicator('EMA', 'EMA');
                        break;

                    case 'SMA':
                        updateMultiPeriodIndicator('SMA', 'SMA');
                        break;

                    case 'WMA':
                        updateMultiPeriodIndicator('WMA', 'WMA');
                        break;

                    case 'RSI':
                        updateMultiPeriodIndicator('RSI', 'RSI');
                        break;

                    case 'BOLL':
                        // 布林带特殊处理，需要三条线的数据
                        if (!indicatorData.BOLL) {
                            indicatorData.BOLL = {
                                success: true,
                                message: 'success',
                                data: {
                                    metricName: 'BOLL',
                                    period: indicatorInfo.period,
                                    standardDeviation: 2,
                                    values: result.map((candlestick) => ({
                                        timestamp: candlestick.timestamp,
                                        upperBand: null,
                                        middleBand: null,
                                        lowerBand: null
                                    })),
                                    createdAt: new Date().toISOString()
                                }
                            };
                        }

                        // 使用indicatorInfo.element来确定是上轨、中轨还是下轨
                        const bollValues = indicatorData.BOLL.data.values;
                        console.log(`处理布林带元素: ${indicatorInfo.element}, 指标名: ${indicatorName}`);

                        values.forEach((value, index) => {
                            if (index < bollValues.length && value.value !== null) {
                                // 根据解析出的元素类型设置对应的布林带值
                                if (indicatorInfo.element === 'upperBand') {
                                    bollValues[index].upperBand = value.value;
                                } else if (indicatorInfo.element === 'middleBand') {
                                    bollValues[index].middleBand = value.value;
                                } else if (indicatorInfo.element === 'lowerBand') {
                                    bollValues[index].lowerBand = value.value;
                                } else if (indicatorInfo.element === 'value') {
                                    // 如果是BOLL(20)格式，默认设为中轨
                                    bollValues[index].middleBand = value.value;
                                }
                            }
                        });
                        break;

                    default:
                        // 其他指标（MACD, KDJ, CCI, ATR, OBV等）
                        // 使用通用处理逻辑
                        if (['KDJ', 'CCI', 'ATR', 'OBV', 'ADX'].includes(indicatorInfo.type)) {
                            updateMultiPeriodIndicator(indicatorInfo.type, indicatorInfo.type as any);
                        }
                        break;
                }
            }

            if (Object.keys(macdBuckets).length > 0) {
                const firstKey = Object.keys(macdBuckets)[0];
                const period = macdBuckets[firstKey].fastPeriod || 12;
                const values: any[] = result.map((candlestick) => {
                    const multiPeriodValues: Record<string, any> = {};
                    Object.entries(macdBuckets).forEach(([key, bucket]) => {
                        const v = bucket.valuesByTs[candlestick.timestamp];
                        if (!v) return;
                        multiPeriodValues[key] = {
                            diff: v.diff !== undefined ? Number(v.diff) : undefined,
                            dea: v.dea !== undefined ? Number(v.dea) : undefined,
                            macd: v.macd !== undefined ? Number(v.macd) : undefined
                        };
                    });
                    return {timestamp: candlestick.timestamp, multiPeriodValues};
                });

                indicatorData.MACD = {
                    success: true,
                    message: 'success',
                    data: {
                        metricName: 'MACD',
                        period,
                        isMultiPeriod: true,
                        periods: Object.values(macdBuckets).map(b => b.fastPeriod).filter(Boolean),
                        values,
                        createdAt: new Date().toISOString()
                    }
                };
            }

            // 增强的解析结果统计和验证
            const parseStats = {
                totalRows: tableData.rows.length,
                indicatorRowsFound: indicatorRows.length,
                ohlcRowsFound: (openRow && highRow && lowRow && closeRow) ? 4 : 0,
                candlestickDataCount: result.length,
                indicatorsGenerated: Object.keys(indicatorData).length,
                isTimeReversed: isTimeReversed,
                timeColumns: timeColumns,
                indicatorDataKeys: Object.keys(indicatorData)
            };

            console.log('=== 技术指标解析详细统计 ===');
            console.log('原始表格数据:', {
                总行数: parseStats.totalRows,
                时间列: parseStats.timeColumns,
                时间顺序: parseStats.isTimeReversed ? '倒序' : '正序'
            });
            console.log('技术指标识别:', {
                找到的指标行数: parseStats.indicatorRowsFound,
                成功生成的指标类型: parseStats.indicatorsGenerated,
                指标详情: parseStats.indicatorDataKeys
            });
            console.log('K线数据生成:', {
                K线数据条数: parseStats.candlestickDataCount,
                OHLC数据完整性: parseStats.ohlcRowsFound === 4 ? '完整' : '不完整'
            });

            // 验证每个技术指标的数据完整性
            Object.entries(indicatorData).forEach(([indicatorType, indicatorInfo]) => {
                const dataPoints = (indicatorInfo as TechnicalIndicatorData).data.values?.length || 0;
                const validDataPoints = (indicatorInfo as TechnicalIndicatorData).data.values?.filter((v: IndicatorDataPoint) =>
                    v.value !== null || v.upperBand !== null || v.middleBand !== null || v.lowerBand !== null
                ).length || 0;

                console.log(`指标 ${indicatorType}:`, {
                    数据点总数: dataPoints,
                    有效数据点: validDataPoints,
                    完整性: `${validDataPoints}/${dataPoints} (${((validDataPoints / dataPoints) * 100).toFixed(1)}%)`
                });
            });

            console.log('生成的技术指标数据:', indicatorData);
            console.log('=== 表格转K线图数据转换完成 ===');

            // 增强调试：生成综合数据摘要
            const dataSummary = {
                k线数据: {
                    总数量: result.length,
                    时间范围: result.length > 0 ? {
                        开始: new Date(result[0].timestamp).toLocaleString('zh-CN'),
                        结束: new Date(result[result.length - 1].timestamp).toLocaleString('zh-CN'),
                        当前K线: new Date(result[result.length - 1].timestamp).toLocaleString('zh-CN')
                    } : null,
                    价格范围: result.length > 0 ? {
                        最高: Math.max(...result.map(k => k.high)),
                        最低: Math.min(...result.map(k => k.low))
                    } : null
                },
                技术指标: {
                    总数量: Object.keys(indicatorData).length,
                    指标类型: Object.keys(indicatorData),
                    详细信息: Object.entries(indicatorData).map(([indicatorType, data]) => ({
                        类型: indicatorType,
                        周期: (data as any).data?.period,
                        数据点数: (data as any).data?.values?.length,
                        有效性: (data as any).data?.values?.filter((v: any) => v.value !== null).length
                    }))
                },
                数据对应关系: {
                    表格列数: timeColumns.length,
                    K线数据点数: result.length,
                    一致性检查: timeColumns.length === result.length ? '✅ 一致' : '❌ 不一致'
                }
            };

            console.log('📊 数据解析摘要:', dataSummary);

            // 验证生成的数据
            if (result.length === 0) {
                console.error('❌ 错误：未能生成有效的K线数据');
            } else {
                console.log('✅ K线数据生成成功，数据点数:', result.length);
                console.log(`📈 价格范围: ${dataSummary.k线数据.价格范围?.最低} - ${dataSummary.k线数据.价格范围?.最高}`);
            }

            if (Object.keys(indicatorData).length === 0) {
                console.error('❌ 错误：未能生成任何技术指标数据');
                console.warn('可能的原因:');
                console.warn('1. 表格中没有符合格式的技术指标数据');
                console.warn('2. 技术指标格式不符合预期的 x(y) 或 x(y)-z 格式');
                console.warn('3. 技术指标数据解析失败');
            } else {
                console.log('✅ 技术指标数据生成成功，指标类型:', Object.keys(indicatorData));
                dataSummary.技术指标.详细信息.forEach(info => {
                    console.log(`  📊 ${info.类型}(${info.周期}): ${info.数据点数}个数据点, ${info.有效性}个有效值`);
                });
            }

            // 数据一致性警告
            if (dataSummary.数据对应关系.一致性检查 === '❌ 不一致') {
                console.warn('⚠️  警告：表格列数与K线数据点数不一致，可能影响技术指标对齐');
            }

            return {candlestickData: result, indicators: indicatorData, timeFrame: currentInfoTimeFrame};
        } catch (error) {
            console.error('解析技术指标数据失败:', error);
            console.error('错误详情:', {
                errorMessage: error instanceof Error ? error.message : '未知错误',
                errorStack: error instanceof Error ? error.stack : undefined,
                inputDataLength: indicatorData?.length || 0
            });
            return {candlestickData: [], indicators: {}, timeFrame: '5m'};
        }
    };

    const DEFAULT_MULTI_PERIODS = {
        KDJ: [9, 14, 21],
        CCI: [14, 20, 30],
        ATR: [14, 20, 30],
        OBV: [20, 60],
        ADX: [14, 20, 30],
    } as const;

    /**
     * 计算简单移动平均线 (SMA) 数组
     * @param values 原始数值序列（如收盘价）
     * @param period 计算周期（如 20）
     * @returns 与输入序列等长的 SMA 数值序列，不足周期处为 undefined
     *
     * SMA 计算公式：(P1 + P2 + ... + Pn) / n
     * 它是衡量价格趋势最基础的指标，通过平滑价格波动来展示中长期的方向。
     */
    const computeSMAArray = (values: number[], period: number): Array<number | undefined> => {
        /** 初始化结果数组，默认填充 undefined */
        const result: Array<number | undefined> = new Array(values.length).fill(undefined);
        /** 基础验证：数据不足或周期无效时直接返回 */
        if (period <= 0) return result;
        let sum = 0;
        /** 使用滑动窗口优化计算性能 */
        for (let i = 0; i < values.length; i++) {
            /** 累加当前数值 */
            sum += values[i];
            /** 当窗口超过指定周期时，减去窗口最左侧的旧值 */
            if (i >= period) {
                sum -= values[i - period];
            }
            /** 当窗口达到指定周期时开始输出平均值 */
            if (i >= period - 1) {
                result[i] = sum / period;
            }
        }
        return result;
    };

    /**
     * 计算能量潮指标 (OBV) 的原始序列
     * @param candles 蜡烛数据数组
     * @returns 累积的 OBV 数值序列
     *
     * OBV 理论认为：成交量是价格变动的先兆。
     * - 如果今日收盘价 > 昨日收盘价，成交量计为正。
     * - 如果今日收盘价 < 昨日收盘价，成交量计为负。
     */
    const computeOBVRaw = (candles: CandlestickData[]): number[] => {
        const result: number[] = new Array(candles.length).fill(0);
        let obv = 0;
        for (let i = 1; i < candles.length; i++) {
            const prevClose = candles[i - 1].close;
            const close = candles[i].close;
            const vol = candles[i].volume || 0;

            /** 价格上涨则累加成交量，下跌则扣除 */
            if (close > prevClose) obv += vol;
            else if (close < prevClose) obv -= vol;

            result[i] = obv;
        }
        return result;
    };

    /**
     * 计算多周期 OBV 指标
     * @param candlesAsc 时间正序的蜡烛数据
     * @param periods 周期数组（如 [20, 60]）
     * @returns 符合 TechnicalIndicatorData 结构的指标对象
     */
    const computeOBVMulti = (candlesAsc: CandlestickData[], periods: number[]): TechnicalIndicatorData => {
        /** 1. 首先计算基础 OBV 序列 */
        const raw = computeOBVRaw(candlesAsc);
        /** 2. 对 OBV 序列进行平滑处理（计算 SMA） */
        const perPeriod = periods.map(p => ({period: p, series: computeSMAArray(raw, p)}));

        /** 3. 将计算结果映射到每一个时间点 */
        const values = candlesAsc.map((candle, i) => {
            const multiPeriodValues: Record<string, number> = {};
            perPeriod.forEach(({period, series}) => {
                const v = series[i];
                if (v !== undefined && Number.isFinite(v)) {
                    multiPeriodValues[`obv_${period}`] = Number(v.toFixed(2));
                }
            });
            return {timestamp: candle.timestamp, multiPeriodValues};
        });

        return {
            success: true,
            message: 'success',
            data: {
                metricName: 'OBV',
                period: periods[0] ?? 20,
                periods: [...periods],
                values,
                createdAt: new Date().toISOString(),
            } as any,
        };
    };

    /**
     * 计算平均真实波幅 (ATR) 序列
     * @param candlesAsc 时间正序的蜡烛数据
     * @param period 计算周期（通常为 14）
     * @returns ATR 数值序列
     *
     * ATR 衡量的是市场的绝对波动率。
     * 1. 首先计算“真实波幅”(TR)，它是以下三者中的最大值：
     *    - 今日最高价与今日最低价之差。
     *    - 今日最高价与昨日收盘价之差的绝对值。
     *    - 今日最低价与昨日收盘价之差的绝对值。
     * 2. 然后对 TR 进行平滑移动平均。
     */
    const computeATRSeries = (candlesAsc: CandlestickData[], period: number): Array<number | undefined> => {
        const result: Array<number | undefined> = new Array(candlesAsc.length).fill(undefined);
        if (period <= 0 || candlesAsc.length === 0) return result;

        /** 第一步：计算 TR 序列 */
        const tr: number[] = new Array(candlesAsc.length).fill(0);
        for (let i = 0; i < candlesAsc.length; i++) {
            const cur = candlesAsc[i];
            const prevClose = i > 0 ? candlesAsc[i - 1].close : cur.close;
            const range1 = cur.high - cur.low;
            const range2 = Math.abs(cur.high - prevClose);
            const range3 = Math.abs(cur.low - prevClose);
            tr[i] = Math.max(range1, range2, range3);
        }

        /** 第二步：计算 TR 的 Wilder 平滑移动平均 */
        let sum = 0;
        for (let i = 0; i < tr.length; i++) {
            sum += tr[i];
            if (i === period - 1) {
                /** 第一个 ATR 是前 period 个 TR 的简单平均值 */
                const first = sum / period;
                result[i] = first;
            } else if (i >= period) {
                /** 后续 ATR 使用递归公式：(前一周期 ATR * (n-1) + 今日 TR) / n */
                const prev = result[i - 1];
                if (prev !== undefined) {
                    result[i] = (prev * (period - 1) + tr[i]) / period;
                }
            }
        }

        return result;
    };

    /**
     * 计算多周期 ATR 指标
     * @param candlesAsc 蜡烛数据
     * @param periods 周期数组
     * @returns 指标数据对象
     */
    const computeATRMulti = (candlesAsc: CandlestickData[], periods: number[]): TechnicalIndicatorData => {
        /** 批量计算不同周期的 ATR */
        const perPeriod = periods.map(p => ({period: p, series: computeATRSeries(candlesAsc, p)}));
        const values = candlesAsc.map((candle, i) => {
            const multiPeriodValues: Record<string, number> = {};
            perPeriod.forEach(({period, series}) => {
                const v = series[i];
                if (v !== undefined && Number.isFinite(v)) {
                    /** 精度控制：ATR 通常需要更高的精度 */
                    multiPeriodValues[`atr_${period}`] = Number(v.toFixed(4));
                }
            });
            return {timestamp: candle.timestamp, multiPeriodValues};
        });

        return {
            success: true,
            message: 'success',
            data: {
                metricName: 'ATR',
                period: periods[0] ?? 14,
                periods: [...periods],
                values,
                createdAt: new Date().toISOString(),
            } as any,
        };
    };

    /**
     * 计算顺势指标 (CCI) 序列
     * @param candlesAsc 蜡烛数据
     * @param period 计算周期（通常为 14 或 20）
     * @returns CCI 数值序列
     *
     * CCI 用于衡量价格是否偏离其统计平均范围。
     * 计算步骤：
     * 1. 计算典型价格 (TP) = (High + Low + Close) / 3
     * 2. 计算 TP 的 n 周期简单移动平均 (SMA_TP)
     * 3. 计算平均偏差 (MD)：TP 与 SMA_TP 差值的绝对值的平均值
     * 4. CCI = (TP - SMA_TP) / (0.015 * MD)
     */
    const computeCCISeries = (candlesAsc: CandlestickData[], period: number): Array<number | undefined> => {
        const result: Array<number | undefined> = new Array(candlesAsc.length).fill(undefined);
        if (period <= 0 || candlesAsc.length === 0) return result;

        /** 1. 计算典型价格 TP 序列 */
        const tp = candlesAsc.map(c => (c.high + c.low + c.close) / 3);
        /** 2. 计算 TP 的 SMA */
        const smaTp = computeSMAArray(tp, period);

        for (let i = period - 1; i < candlesAsc.length; i++) {
            const sma = smaTp[i];
            if (sma === undefined) continue;

            /** 3. 计算平均偏差 MD */
            let mdSum = 0;
            for (let j = i - period + 1; j <= i; j++) {
                mdSum += Math.abs(tp[j] - sma);
            }
            const md = mdSum / period;

            /** 4. 计算最终 CCI 值 */
            if (md === 0) {
                result[i] = 0;
            } else {
                /** 0.015 是常数系数，旨在使 70% 到 80% 的 CCI 值落在 -100 到 +100 之间 */
                result[i] = (tp[i] - sma) / (0.015 * md);
            }
        }

        return result;
    };

    /**
     * 计算多周期 CCI 指标
     * @param candlesAsc 蜡烛数据
     * @param periods 周期数组
     * @returns 指标数据对象
     */
    const computeCCIMulti = (candlesAsc: CandlestickData[], periods: number[]): TechnicalIndicatorData => {
        const perPeriod = periods.map(p => ({period: p, series: computeCCISeries(candlesAsc, p)}));
        const values = candlesAsc.map((candle, i) => {
            const multiPeriodValues: Record<string, number> = {};
            perPeriod.forEach(({period, series}) => {
                const v = series[i];
                if (v !== undefined && Number.isFinite(v)) {
                    multiPeriodValues[`cci_${period}`] = Number(v.toFixed(2));
                }
            });
            return {timestamp: candle.timestamp, multiPeriodValues};
        });

        return {
            success: true,
            message: 'success',
            data: {
                metricName: 'CCI',
                period: periods[0] ?? 14,
                periods: [...periods],
                values,
                createdAt: new Date().toISOString(),
            } as any,
        };
    };

    /**
     * 计算随机指标 (KDJ) 序列
     * @param candlesAsc 蜡烛数据
     * @param period 计算周期（通常为 9）
     * @returns KDJ 结果序列（包含 K, D, J 三条线）
     *
     * KDJ 是通过价格波动的强弱来寻找趋势反转点。
     * 1. 计算未成熟随机值 RSV = (今日收盘价 - n周期最低价) / (n周期最高价 - n周期最低价) * 100
     * 2. 计算 K 值 = 2/3 * 前一日 K + 1/3 * 今日 RSV
     * 3. 计算 D 值 = 2/3 * 前一日 D + 1/3 * 今日 K
     * 4. 计算 J 值 = 3 * K - 2 * D
     */
    const computeKDJSeries = (
        candlesAsc: CandlestickData[],
        period: number
    ): Array<{ k: number; d: number; j: number } | null> => {
        const result: Array<{ k: number; d: number; j: number } | null> = new Array(candlesAsc.length).fill(null);
        if (period <= 0 || candlesAsc.length === 0) return result;

        /** 初始值通常设定为 50 */
        let k = 50;
        let d = 50;

        for (let i = 0; i < candlesAsc.length; i++) {
            /** 只有在数据点达到周期数后才开始计算 */
            if (i < period - 1) continue;

            /** 获取过去 n 周期内的最高价和最低价 */
            let highest = -Infinity;
            let lowest = Infinity;
            for (let j = i - period + 1; j <= i; j++) {
                highest = Math.max(highest, candlesAsc[j].high);
                lowest = Math.min(lowest, candlesAsc[j].low);
            }

            const denom = highest - lowest;
            /** 计算 RSV，防止分母为 0 */
            const rsv = denom === 0 ? 50 : ((candlesAsc[i].close - lowest) / denom) * 100;

            /** 迭代计算 K 和 D */
            k = (2 / 3) * k + (1 / 3) * rsv;
            d = (2 / 3) * d + (1 / 3) * k;
            /** 计算 J */
            const j = 3 * k - 2 * d;

            result[i] = {
                k: Number(k.toFixed(4)),
                d: Number(d.toFixed(4)),
                j: Number(j.toFixed(4)),
            };
        }

        return result;
    };

    /**
     * 计算多周期 KDJ 指标
     * @param candlesAsc 蜡烛数据
     * @param periods 周期数组
     * @returns 指标数据对象
     */
    const computeKDJMulti = (candlesAsc: CandlestickData[], periods: number[]): TechnicalIndicatorData => {
        const perPeriod = periods.map(p => ({period: p, series: computeKDJSeries(candlesAsc, p)}));
        const values = candlesAsc.map((candle, i) => {
            const multiPeriodValues: Record<string, any> = {};
            perPeriod.forEach(({period, series}) => {
                /** KDJ 的值是一个包含 K, D, J 的对象 */
                multiPeriodValues[`kdj_${period}`] = series[i];
            });
            return {timestamp: candle.timestamp, multiPeriodValues};
        });

        return {
            success: true,
            message: 'success',
            data: {
                metricName: 'KDJ',
                period: periods[0] ?? 9,
                periods: [...periods],
                values,
                createdAt: new Date().toISOString(),
            } as any,
        };
    };

    // 从后端API获取K线数据
    const fetchKlineDataFromApi = async (overrideTimeFrame?: string, overrideLimit?: number) => {
        const nextTimeFrame = normalizeTimeFrame(overrideTimeFrame ?? timeFrame);
        const nextLimit = normalizeLimit(overrideLimit ?? limit);
        console.log('fetchKlineDataFromApi 调用，参数:', {apiKeyId, instId, timeFrame: nextTimeFrame, limit: nextLimit});

        if (!apiKeyId) {
            console.error('缺少apiKeyId，无法从API获取数据');
            setError('缺少API Key ID，无法获取实时数据');
            return;
        }

        if (!instId) {
            console.error('缺少instId，无法从API获取数据');
            setError('缺少合约ID，无法获取实时数据（请确保表格数据中包含合约ID，格式如: BTC-USDT-SWAP）');
            return;
        }

        try {
            setLoading(true);
            setError('');

            console.log('开始从API获取K线数据:', {
                instId,
                apiKeyId,
                timeFrame: nextTimeFrame,
                limit: nextLimit,
                enabledIndicators
            });

            const response = await tradingService.getCompleteChartData({
                instId,
                apiKeyId,
                timeframe: nextTimeFrame,
                limit: nextLimit,
                indicators: enabledIndicators,
                emaPeriods: [5, 20, 30],
                rsiPeriods: [5, 20, 30],
                kdjPeriods: [9, 14, 21],
                cciPeriods: [14, 20, 30],
                atrPeriods: [14, 20, 30],
                obvPeriods: [20, 60],
                adxPeriods: [14, 20, 30]
            });

            console.log('API响应:', response);

            // response 已经是 CompleteChartData 对象（tradingService已解包ApiResponse）
            if (response && response.candles) {
                // 转换API数据格式为CandlestickData格式
                const candles = response.candles || [];
                console.log('API返回的K线数据条数:', candles.length);

                const apiChartData: CandlestickData[] = candles.map((candle: any) => ({
                    timestamp: Number(candle.ts ?? candle.timestamp),
                    open: parseFloat(candle.open || candle.o),
                    high: parseFloat(candle.high || candle.h),
                    low: parseFloat(candle.low || candle.l),
                    close: parseFloat(candle.close || candle.c),
                    volume: parseFloat(candle.volume || candle.vol || candle.v || 0),
                    confirm: candle.confirm ?? 1
                }));

                setChartData(apiChartData);

                // 详细日志：检查indicators数据结构
                console.log('📊 API返回的indicators数据详情:', {
                    hasIndicators: !!response.indicators,
                    indicatorKeys: response.indicators ? Object.keys(response.indicators) : [],
                    indicatorKeys详细: response.indicators ? JSON.stringify(Object.keys(response.indicators)) : 'N/A',
                    indicatorsType: typeof response.indicators,
                    indicatorsRaw: response.indicators,
                    // 详细检查每个指标
                    EMA: response.indicators?.EMA,
                    RSI: response.indicators?.RSI,
                    BOLL: response.indicators?.BOLL,
                    SMA: response.indicators?.SMA,
                    WMA: response.indicators?.WMA,
                    OBV: response.indicators?.OBV,
                    KDJ: response.indicators?.KDJ,
                    CCI: response.indicators?.CCI,
                    ATR: response.indicators?.ATR,
                    ADX: response.indicators?.ADX
                });

                const candlesAsc = [...apiChartData].sort((a, b) => a.timestamp - b.timestamp);
                const mergedIndicators: any = {...(response.indicators || {})};

                if (enabledIndicators.includes('KDJ')) {
                    const apiKdj = mergedIndicators.KDJ;
                    const apiKdjValues = getIndicatorValues(apiKdj);
                    const apiKdjSample = Array.isArray(apiKdjValues)
                        ? apiKdjValues.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).some((k: string) => /^kdj_\d+$/i.test(k)))
                        : undefined;
                    const apiKdjPeriodKey = apiKdjSample?.multiPeriodValues
                        ? Object.keys(apiKdjSample.multiPeriodValues).find((k: string) => /^kdj_\d+$/i.test(k))
                        : undefined;
                    const apiKdjRaw = apiKdjPeriodKey ? apiKdjSample?.multiPeriodValues?.[apiKdjPeriodKey] : undefined;
                    const apiKdjHasDAndJ = apiKdjRaw && typeof apiKdjRaw === 'object' && 'd' in apiKdjRaw && 'j' in apiKdjRaw;

                    if (!apiKdjHasDAndJ) {
                        mergedIndicators.KDJ = computeKDJMulti(candlesAsc, [...DEFAULT_MULTI_PERIODS.KDJ]);
                    }
                }
                if (enabledIndicators.includes('CCI')) {
                    // 只有当后端未返回有效CCI数据时才本地计算
                    const apiCci = mergedIndicators.CCI;
                    const apiCciValues = getIndicatorValues(apiCci);
                    const apiCciValid = apiCci && apiCciValues && apiCciValues.length > 0;
                    if (!apiCciValid) {
                        mergedIndicators.CCI = computeCCIMulti(candlesAsc, [...DEFAULT_MULTI_PERIODS.CCI]);
                    }
                }
                if (enabledIndicators.includes('ATR')) {
                    // 只有当后端未返回有效ATR数据时才本地计算
                    const apiAtr = mergedIndicators.ATR;
                    const apiAtrValues = getIndicatorValues(apiAtr);
                    const apiAtrValid = apiAtr && apiAtrValues && apiAtrValues.length > 0;
                    if (!apiAtrValid) {
                        mergedIndicators.ATR = computeATRMulti(candlesAsc, [...DEFAULT_MULTI_PERIODS.ATR]);
                    }
                }
                if (enabledIndicators.includes('OBV')) {
                    // 只有当后端未返回有效OBV数据时才本地计算
                    const apiObv = mergedIndicators.OBV;
                    const apiObvValues = getIndicatorValues(apiObv);
                    const apiObvValid = apiObv && apiObvValues && apiObvValues.length > 0;
                    if (!apiObvValid) {
                        mergedIndicators.OBV = computeOBVMulti(candlesAsc, [...DEFAULT_MULTI_PERIODS.OBV]);
                    }
                }

                setIndicators(mergedIndicators);
                console.log('✅ 成功从API获取并设置K线数据:', apiChartData.length, '条');
            } else {
                throw new Error('获取K线数据失败：响应数据格式不正确');
            }
        } catch (error) {
            console.error('❌ 从API获取K线数据失败:', error);
            setError('从API获取数据失败: ' + (error instanceof Error ? error.message : '未知错误'));
            // API失败时，回退到表格数据
            setUseApiData(false);
        } finally {
            setLoading(false);
        }
    };

    // 重新解析数据
    const refreshData = () => {
        if (useApiData && apiKeyId && instId) {
            // 全屏模式：从API获取数据
            fetchKlineDataFromApi();
        } else if (visible && indicatorData) {
            // 非全屏模式：解析表格数据
            setLoading(true);
            setError('');
            setTimeout(() => {
                const result = parseIndicatorData(indicatorData);
                setChartData(result.candlestickData);
                setIndicators(result.indicators);
                if (result.timeFrame) {
                    setTimeFrame(normalizeTimeFrame(result.timeFrame));
                }
                setLoading(false);
            }, 100);
        }
    };

    // 时间帧切换处理
    const handleTimeFrameChange = (value: string) => {
        const next = normalizeTimeFrame(value);
        console.log('切换时间帧:', next);
        setTimeFrame(next);

        // 如果是全屏模式且有API参数，重新从API获取数据
        if (useApiData && apiKeyId && instId) {
            fetchKlineDataFromApi(next, limit);
        } else {
            // 否则重新解析表格数据
            refreshData();
        }
    };

    // 当弹窗打开或数据变化时，解析数据
    useEffect(() => {
        if (visible && indicatorData) {
            refreshData();
        }
    }, [visible, indicatorData]);

    // 辅助函数：获取统一格式的指标数据
    const getIndicatorData = (indicator: any) => {
        if (!indicator) return null;
        // 旧格式（带包装层）：{success: true, data: {metricName: "EMA", values: [...]}}
        if (indicator.success && indicator.data) return indicator.data;
        // 新格式（直接返回）：{metricName: "EMA", values: [...]}
        if (indicator.metricName || indicator.values) return indicator;
        return null;
    };

    // 初始化visibleIndicators - 保留用户已有的选择状态，只对新指标设置默认值
    useEffect(() => {
        if (!indicators || Object.keys(indicators).length === 0) return;

        setVisibleIndicators(prev => {
            // 保留现有选择，只添加新出现的指标
            const nextVisible: Record<string, boolean> = {...prev};

            Object.keys(indicators).forEach(key => {
                const indicator = indicators[key as keyof typeof indicators];
                const data = getIndicatorData(indicator);
                if (!data) return;

                // 检查多周期
                const samplePoint = Array.isArray(data.values)
                    ? data.values.find((p: any) => p?.multiPeriodValues && Object.keys(p.multiPeriodValues).length > 0)
                    : undefined;
                const isMultiPeriod = !!samplePoint?.multiPeriodValues;

                if (isMultiPeriod && samplePoint?.multiPeriodValues) {
                    const prefixMap: { [key: string]: string } = {
                        EMA: 'ema_', SMA: 'sma_', WMA: 'wma_', RSI: 'rsi_'
                    };
                    const prefix = prefixMap[key] || key.toLowerCase() + '_';

                    Object.keys(samplePoint.multiPeriodValues).forEach(k => {
                        if (key === 'BOLL' && k.startsWith('boll_')) {
                            const match = k.match(/boll_(\d+)/);
                            if (match) {
                                const indicatorKey = `BOLL_${match[1]}`;
                                // 只设置还没有在 prev 中存在的指标
                                if (!prev.hasOwnProperty(indicatorKey)) {
                                    nextVisible[indicatorKey] = true;
                                }
                            }
                        } else if (k.startsWith(prefix)) {
                            const period = parseInt(k.replace(prefix, ''));
                            if (!isNaN(period)) {
                                const indicatorKey = `${key}_${period}`;
                                // 只设置还没有在 prev 中存在的指标
                                if (!prev.hasOwnProperty(indicatorKey)) {
                                    nextVisible[indicatorKey] = true;
                                }
                            }
                        }
                    });
                } else {
                    // 单周期兼容
                    const period = data.period;
                    if (period) {
                        const indicatorKey = `${key}_${period}`;
                        // 只设置还没有在 prev 中存在的指标
                        if (!prev.hasOwnProperty(indicatorKey)) {
                            nextVisible[indicatorKey] = true;
                        }
                    }
                }
            });

            return nextVisible;
        });
    }, [indicators]);


    /**
     * 关闭弹窗并清理资源
     *
     * 在关闭弹窗时，我们需要：
     * 1. 清空 K 线图表数据，防止下次打开时显示旧数据。
     * 2. 清空已计算的指标数据。
     * 3. 重置错误信息状态。
     * 4. 退出全屏模式，将 UI 状态恢复到初始表格预览状态。
     * 5. 禁用 API 实时数据获取。
     * 6. 执行父组件传入的关闭回调。
     */
    const handleClose = () => {
        /** 清理核心状态数据 */
        setChartData([]);
        setIndicators({});
        setError('');

        /** 重置 UI 模式标志 */
        setIsFullscreen(false);
        setUseApiData(false);

        /** 执行外部关闭逻辑 */
        onClose();
    };

    // 切换全屏状态
    const toggleFullscreen = () => {
        const newFullscreenState = !isFullscreen;
        console.log('=== 切换全屏状态 ===');
        console.log('当前全屏状态:', isFullscreen, '-> 新状态:', newFullscreenState);
        console.log('参数检查:', {apiKeyId, instId, useApiData});

        setIsFullscreen(newFullscreenState);

        // 切换到全屏模式时，使用API数据
        if (newFullscreenState) {
            if (apiKeyId && instId) {
                const nextTimeFrame = normalizeTimeFrame(timeFrame);
                const nextLimit = normalizeLimit(limit);
                setTimeFrame(nextTimeFrame);
                setLimit(nextLimit);
                setUseApiData(true);
                console.log('✅ 切换到全屏模式，将从API获取K线数据');
                console.log('调用参数:', {apiKeyId, instId, timeFrame: nextTimeFrame, limit: nextLimit});
                // 全屏模式下立即获取API数据
                fetchKlineDataFromApi(nextTimeFrame, nextLimit);
            } else {
                console.warn('⚠️ 切换到全屏模式，但缺少必要参数:');
                console.warn('  - apiKeyId:', apiKeyId);
                console.warn('  - instId:', instId);
                console.warn('将继续使用表格数据');
                message.warning('缺少API Key或合约ID，无法获取实时数据，将使用表格数据');
            }
        } else {
            // 切换回非全屏模式，使用表格数据
            setUseApiData(false);
            console.log('⬅️ 切换回非全屏模式，将解析表格数据');
            // 重新解析表格数据
            if (indicatorData) {
                setTimeout(() => {
                    const result = parseIndicatorData(indicatorData);
                    setChartData(result.candlestickData);
                    setIndicators(result.indicators);
                    if (result.timeFrame) {
                        setTimeFrame(normalizeTimeFrame(result.timeFrame));
                    }
                }, 100);
            }
        }
    };

    return (
        /**
         * 渲染核心弹窗组件
         * 使用 Ant Design 的 Modal 组件作为容器，自定义样式以匹配暗黑交易主题。
         */
        <Modal
            /** 弹窗标题区域：包含合约信息、时间帧、数据源类型以及窗口控制按钮 */
            title={
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        width: '100%',
                        cursor: 'default',
                        userSelect: 'none',
                        minHeight: '22px'
                    }}
                >
                    <Space size={8}>
                        {/* 动态标题：展示合约名称、当前周期及数据获取模式 */}
                        <span>{instId ? `${instId}-` : ''}技术指标K线图-{timeFrame}{useApiData ? '(实时数据)' : '(表格数据)'}</span>
                    </Space>

                    <Space size={0}>
                        {/* 全屏切换按钮：支持全屏/退出全屏的双向切换 */}
                        <Button
                            type="text"
                            icon={isFullscreen ? <CompressOutlined/> : <ExpandOutlined/>}
                            onClick={(e) => {
                                e.stopPropagation(); /* 阻止事件冒泡，避免干扰潜在的拖拽逻辑 */
                                toggleFullscreen();
                            }}
                            style={{
                                color: 'rgba(255, 255, 255, 0.65)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                height: '22px',
                                minWidth: '32px',
                                padding: '0'
                            }}
                            title={isFullscreen ? "退出全屏" : "全屏显示"}
                        />

                        {/* 弹窗关闭按钮 */}
                        <Button
                            type="text"
                            icon={<CloseOutlined/>}
                            onClick={(e) => {
                                e.stopPropagation();
                                handleClose();
                            }}
                            style={{
                                color: 'rgba(255, 255, 255, 0.45)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                height: '22px',
                                minWidth: '32px',
                                padding: '0',
                                marginLeft: '4px'
                            }}
                            title="关闭"
                        />
                    </Space>
                </div>
            }
            closable={false} /* 禁用原生关闭图标，改用自定义 header 中的关闭按钮 */
            open={visible}
            onCancel={handleClose}
            footer={null} /** 隐藏默认页脚，所有交互均在 body 或 header 中完成 */
            width={isFullscreen ? 'calc(100vw - 50px)' : 900}
            height={600}
            styles={{
                mask: {backgroundColor: 'rgba(0, 0, 0, 0.7)'}, /** 半透明遮罩层 */
                content: {
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343',
                    borderRadius: '8px',
                },
                body: {
                    padding: isFullscreen ? '0' : '16px',
                    backgroundColor: '#1f1f1f',
                    height: isFullscreen ? 'calc(92vh - 100px)' : '500px',
                    display: 'flex',
                    flexDirection: 'column',
                    cursor: 'default'
                }
            }}
        >
            {/* -----------------------------------------------------------------
             * 全屏控制面板
             * 该面板仅在全屏模式下显示，提供了更丰富的图表配置与指标切换功能。
             * ----------------------------------------------------------------- */}
            {isFullscreen && (
                <div style={{
                    height: '60px',
                    borderBottom: '1px solid #434343',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0 16px',
                    backgroundColor: '#1a1a1a',
                    flexShrink: 0
                }}>
                    {/* 左侧区域: 提供时间帧切换、采样率调整及数据顺序翻转 */}
                    <Space size="middle">
                        <span style={{color: 'rgba(255,255,255,0.65)', fontSize: '12px'}}>
                            时间帧:
                        </span>
                        <Segmented
                            size="small"
                            value={normalizeTimeFrame(timeFrame)}
                            options={TIMEFRAME_OPTIONS.map(tf => ({
                                label: tf,
                                value: tf,
                                disabled: loading
                            }))}
                            onChange={(value) => {
                                if (loading) return;
                                handleTimeFrameChange(value as string);
                            }}
                        />

                        <span style={{color: 'rgba(255,255,255,0.65)', fontSize: '12px'}}>
                            采样率:
                        </span>
                        <Segmented
                            size="small"
                            value={normalizeLimit(limit)}
                            options={LIMIT_OPTIONS.map(v => ({
                                label: String(v),
                                value: v,
                                disabled: loading
                            }))}
                            onChange={(value) => {
                                if (loading) return;
                                const next = value as number;
                                setLimit(next);
                                if (useApiData && apiKeyId && instId) {
                                    fetchKlineDataFromApi(timeFrame, next);
                                }
                            }}
                        />
                    </Space>

                    {/* 右侧区域: 核心技术指标的快速切换与配置入口 */}
                    <Space size="small">
                        {/* 映射已启用的指标列表
                         * 根据用户需求，此处将按钮优化为"显示技术指标名称"模式
                         * 既保持了界面的简洁，又不失专业指标的易读性
                         */}
                        {enabledIndicators.map(type => {
                            const metadata = INDICATOR_METADATA[type];
                            if (!metadata) return null;

                            /** 智能检测该指标分类下的可见性状态 */
                            const isVisible = Object.keys(visibleIndicators)
                                .filter(k => k.startsWith(type + '_'))
                                .some(k => visibleIndicators[k]);

                            return (
                                <Tooltip
                                    key={type}
                                    title={`${metadata.name}: ${metadata.desc}`}
                                    placement="bottom"
                                >
                                    <Button
                                        size="small"
                                        type={isVisible ? "primary" : "default"}
                                        onClick={() => toggleIndicatorTypeVisibility(type)}
                                        style={{
                                            height: '24px',
                                            padding: '0 8px',
                                            fontSize: '12px',
                                            fontWeight: 500
                                        }}
                                    >
                                        {metadata.name}
                                    </Button>
                                </Tooltip>
                            );
                        })}

                        {/* 视觉分隔 */}
                        <div style={{width: '8px'}}/>

                        {/* 打开技术指标高级配置抽屉 */}
                        <Tooltip title="技术指标配置">
                            <Button
                                size="small"
                                icon={<SettingOutlined/>}
                                onClick={() => setIndicatorDrawerVisible(true)}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center'
                                }}
                            />
                        </Tooltip>
                    </Space>
                </div>
            )}

            {/* -----------------------------------------------------------------
             * 主图表渲染区域
             * 负责处理加载中、错误及无数据等异常状态，并渲染核心 K 线图表。
             * ----------------------------------------------------------------- */}
            <div style={{
                flex: 1,
                position: 'relative',
                overflow: 'hidden',
                width: '100%'
            }}>
                {loading ? (
                    /** 加载状态反馈 */
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%'
                    }}>
                        <Spin size="large">
                            <div style={{marginTop: 8, color: '#999'}}>正在解析技术指标数据...</div>
                        </Spin>
                    </div>
                ) : error ? (
                    /** 错误状态反馈 */
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%'
                    }}>
                        <Alert
                            message="数据解析失败"
                            description={error}
                            type="error"
                            showIcon
                        />
                    </div>
                ) : chartData.length === 0 ? (
                    /** 空数据反馈 */
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%'
                    }}>
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="暂无有效的技术指标数据"
                            style={{color: '#999'}}
                        />
                    </div>
                ) : (
                    /** 正常数据渲染：包含 K 线主图及右侧指标细分控制侧边栏 */
                    <div style={{display: 'flex', height: '100%', width: '100%'}}>
                        <div style={{flex: 1, position: 'relative', overflow: 'hidden'}}>
                            <div
                                style={{
                                    position: 'absolute',
                                    top: 0,
                                    left: 0,
                                    right: 0,
                                    bottom: 0,
                                    minWidth: isFullscreen ? '400px' : '720px',
                                    minHeight: isFullscreen ? '300px' : '450px'
                                }}
                            >
                                {/* 调用 lightweight-charts K 线图组件 */}
                                <LightweightCandlestickChart
                                    data={bars}
                                    timeFrame={normalizeTimeFrame(timeFrame)}
                                    markPrice={chartData.length > 0 ? chartData[chartData.length - 1].close : null}
                                    markPriceColor={chartData.length > 0 ? (() => {
                                        const currentCandle = chartData[chartData.length - 1];
                                        return currentCandle.close >= currentCandle.open ? '#52c41a' : '#ff4d4f';
                                    })() : undefined}
                                    indicators={transformedIndicators}
                                    visibleIndicators={visibleIndicators}
                                    maxVisibleBars={useApiData ? limit : 30}
                                />
                            </div>
                        </div>

                        {/* -------------------------------------------------------------
                         * 右侧指标控制侧边栏
                         * 提供了对具体周期指标（如 EMA 20, SMA 60 等）的细粒度开关控制。
                         * ------------------------------------------------------------- */}
                        <div style={{
                            width: '140px',
                            borderLeft: '1px solid #434343',
                            padding: '12px 8px',
                            overflowY: 'auto',
                            backgroundColor: '#1a1a1a',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '12px',
                            flexShrink: 0
                        }}>
                            {Object.keys(indicators).map(type => {
                                const indicator = indicators[type as keyof typeof indicators];
                                const data = getIndicatorData(indicator);
                                if (!data) return null;

                                // 获取该类型的所有周期key
                                const keys = Object.keys(visibleIndicators).filter(k => k.startsWith(type + '_'));
                                if (keys.length === 0) return null;

                                // 排序
                                keys.sort((a, b) => {
                                    const pa = parseInt(a.split('_')[1]);
                                    const pb = parseInt(b.split('_')[1]);
                                    return pa - pb;
                                });

                                // 获取所有周期用于计算颜色索引
                                let allPeriods: number[] = [];
                                if (data.values && data.values.length > 0 && data.values[0].multiPeriodValues) {
                                    const prefixMap: { [key: string]: string } = {
                                        EMA: 'ema_', SMA: 'sma_', WMA: 'wma_', RSI: 'rsi_'
                                    };
                                    const prefix = prefixMap[type] || type.toLowerCase() + '_';
                                    if (type === 'BOLL') {
                                        allPeriods = Object.keys(data.values[0].multiPeriodValues)
                                            .filter(k => k.startsWith('boll_'))
                                            .map(k => {
                                                const m = k.match(/boll_(\d+)/);
                                                return m ? parseInt(m[1]) : 0;
                                            })
                                            .sort((a, b) => a - b);
                                    } else {
                                        allPeriods = Object.keys(data.values[0].multiPeriodValues)
                                            .filter(k => k.startsWith(prefix))
                                            .map(k => parseInt(k.replace(prefix, '')))
                                            .sort((a, b) => a - b);
                                    }
                                } else {
                                    allPeriods = [data.period || 0];
                                }

                                return (
                                    <div key={type}>
                                        <div style={{
                                            color: 'rgba(255,255,255,0.85)',
                                            fontSize: '12px',
                                            fontWeight: 'bold',
                                            marginBottom: '6px',
                                            paddingLeft: '2px'
                                        }}>{type}</div>
                                        <div style={{display: 'flex', flexDirection: 'column', gap: '6px'}}>
                                            {keys.map(key => {
                                                const period = parseInt(key.split('_')[1]);
                                                const index = allPeriods.indexOf(period);
                                                const colors = (COLORS.indicatorColors as any)[type] || (type === 'BOLL' ? [COLORS.bollUpper] : ['#fff']);
                                                const color = colors[index % colors.length] || colors[0];

                                                return (
                                                    <div key={key} style={{
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        cursor: 'pointer',
                                                        paddingLeft: '4px'
                                                    }}
                                                         onClick={() => setVisibleIndicators(prev => ({
                                                             ...prev,
                                                             [key]: !prev[key]
                                                         }))}
                                                    >
                                                        <Checkbox
                                                            checked={visibleIndicators[key]}
                                                            style={{marginRight: '6px', transform: 'scale(0.85)'}}
                                                            onClick={(e) => e.stopPropagation()}
                                                            onChange={() => setVisibleIndicators(prev => ({
                                                                ...prev,
                                                                [key]: !prev[key]
                                                            }))}
                                                        />
                                                        <span style={{
                                                            color: color,
                                                            fontSize: '11px',
                                                            backgroundColor: 'rgba(255,255,255,0.05)',
                                                            padding: '2px 6px',
                                                            borderRadius: '4px',
                                                            flex: 1,
                                                            whiteSpace: 'nowrap',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis'
                                                        }}>
                                                            {type}({period})
                                                        </span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}
            </div>

            {/* 技术指标配置抽屉 */}
            <Drawer
                title="技术指标配置"
                placement="right"
                width={350}
                open={indicatorDrawerVisible}
                onClose={() => setIndicatorDrawerVisible(false)}
                styles={{
                    body: {padding: '16px'},
                    header: {backgroundColor: '#1a1a1a', color: 'rgba(255,255,255,0.95)'}
                }}
                extra={
                    <Button
                        type="primary"
                        size="small"
                        onClick={() => {
                            setIndicatorDrawerVisible(false);
                            // 应用配置后刷新数据
                            if (useApiData && apiKeyId && instId) {
                                fetchKlineDataFromApi();
                            }
                        }}
                    >
                        应用
                    </Button>
                }
            >
                <div style={{color: 'rgba(255,255,255,0.85)'}}>
                    <div style={{marginBottom: '16px'}}>
                        <h4 style={{color: 'rgba(255,255,255,0.95)', marginBottom: '12px'}}>
                            选择要显示的技术指标
                        </h4>
                        <Checkbox.Group
                            value={enabledIndicators}
                            onChange={(values) => setEnabledIndicators(values as string[])}
                            style={{width: '100%'}}
                        >
                            <Space direction="vertical" style={{width: '100%'}}>
                                <Checkbox value="EMA">EMA - 指数移动平均线</Checkbox>
                                <Checkbox value="SMA">SMA - 简单移动平均线</Checkbox>
                                <Checkbox value="WMA">WMA - 加权移动平均线</Checkbox>
                                <Checkbox value="BOLL">BOLL - 布林带</Checkbox>
                                <Checkbox value="RSI">RSI - 相对强弱指标</Checkbox>
                                <Checkbox value="MACD">MACD - 平滑异同移动平均线</Checkbox>
                                <Checkbox value="KDJ">KDJ - 随机指标</Checkbox>
                                <Checkbox value="CCI">CCI - 顺势指标</Checkbox>
                                <Checkbox value="ATR">ATR - 平均真实波幅</Checkbox>
                                <Checkbox value="OBV">OBV - 能量潮指标</Checkbox>
                                <Checkbox value="ADX">ADX - 平均趋向指数</Checkbox>
                            </Space>
                        </Checkbox.Group>
                    </div>

                    <div style={{
                        marginTop: '24px',
                        padding: '12px',
                        backgroundColor: 'rgba(255,255,255,0.05)',
                        borderRadius: '4px',
                        fontSize: '12px',
                        color: 'rgba(255,255,255,0.6)'
                    }}>
                        <div>当前启用的指标: {enabledIndicators.length} 个</div>
                        <div style={{marginTop: '8px'}}>
                            已选择: {enabledIndicators.join(', ') || '无'}
                        </div>
                    </div>
                </div>
            </Drawer>
        </Modal>
    );
};

export default IndicatorChartModal;
