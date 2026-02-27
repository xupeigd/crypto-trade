import React, {useCallback, useEffect, useState} from 'react';
import {
    Button,
    Card,
    Checkbox,
    Col,
    Empty,
    Input,
    InputNumber,
    message,
    Modal,
    Row,
    Select,
    Spin,
    Tabs,
    Tag,
    Tooltip,
    Typography
} from 'antd';
import {
    CloseCircleOutlined,
    EditOutlined,
    FallOutlined,
    InfoCircleOutlined,
    LineChartOutlined,
    ReloadOutlined,
    RiseOutlined,
    SettingOutlined
} from '@ant-design/icons';
import {PendingOrder, TradingOrder, tradingService} from '../../../services/tradingService';
import {PositionModel} from '../../../types/okxPosition';
import {usePageTimer} from '../../../hooks/usePageTimer';
import ActivePositionOrderChartModal, {type ActiveChartItem} from '../../../components/bot/ActivePositionOrderChartModal';
import './OrderList.css';

// 自定义市价平仓SVG图标 - 使用下降箭头表示平仓，颜色通过父组件控制
const MarketCloseIcon = ({color = '#ff4d4f'}: { color?: string }) => (
    <svg viewBox="0 0 24 24" width="1.2em" height="1.2em" fill="currentColor" style={{color}}>
        <path d="M7 10l5 5 5-5z"/>
        <path
            d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>
    </svg>
);

// 配置message的Dark主题样式
message.config({
    top: 100,
    duration: 3,
    maxCount: 3,
    rtl: false,
});

const {Title, Text} = Typography;

/**
 * 格式化持仓数字，最多显示4位有效小数
 * @param positionValue 持仓值（字符串格式）
 * @returns 格式化后的字符串
 */
const formatPosition = (positionValue: string): string => {
    if (!positionValue || positionValue === '0') {
        return '0';
    }

    const num = parseFloat(positionValue);

    if (isNaN(num)) {
        return positionValue;
    }

    // 对于整数或很小的小数，直接返回
    if (num === Math.floor(num) || Math.abs(num) < 0.0001) {
        return num.toString();
    }

    // 转换为科学计数法来确定有效数字位数
    const scientific = num.toExponential();
    const [, , exponent] = scientific.match(/([-\+]?\d*\.?\d+)e([+-]\d+)/) || ['', '0', '0'];

    // 计算需要的小数位数
    const exponentNum = parseInt(exponent);
    let decimalPlaces = 4 - (exponentNum + 1);

    if (decimalPlaces <= 0) {
        // 如果需要显示整数部分，直接四舍五入到整数
        return Math.round(num).toString();
    }

    // 最多显示8位小数，避免过长
    decimalPlaces = Math.min(decimalPlaces, 8);

    const formatted = num.toFixed(decimalPlaces);

    // 移除末尾的0
    const withoutTrailingZeros = formatted.replace(/\.?0+$/, '');

    return withoutTrailingZeros;
};

// 仓位策略数据接口
interface PositionStrategy {
    algoId: string; // 算法订单ID
    instId: string; // 产品ID
    posSide: string; // 持仓方向：long/short
    sz: string; // 策略数量
    tpTriggerPx?: string; // 止盈触发价格
    slTriggerPx?: string; // 止损触发价格
    tpRatio?: string; // 止盈比例
    slRatio?: string; // 止损比例
    tpProgress?: number | string; // 止盈触发进度百分比
    slProgress?: number | string; // 止损触发进度百分比
}

// 仓位数据接口
interface Position {
    instId: string;
    instType: string;
    posId: string;
    posSide: string; // long/short
    position: string; // 持仓数量
    baseBal: string;
    quoteBal: string;
    avgPx: string; // 开仓均价
    markPx: string; // 标记价格
    upl: string; // 未实现盈亏
    uplRatio: string; // 未实现盈亏率
    uplLastPx: string;
    margin: string; // 保证金
    mgnMode: string;
    lever: string; // 杠杆倍数
    cTime: number;
    ctime?: number; // 兼容API返回的ctime字段
    uTime: string;
    apiKeyId?: number;
    keyName?: string;
    // 新增字段
    ccy: string; // 保证金币种
    posCcy: string; // 持仓币种
    marginRatio: string; // 保证金率
    marginRatioPercent: string; // 保证金维持率百分比
    mmr: string; // 维持保证金
    imr: string; // 初始保证金
    liab: string; // 负债额
    interest: string; // 利息
    tradeId: string; // 最新成交ID
    notionalUsd: string; // 以美元价值计算的持仓数量
    estimatedLiquidationPx: string; // 预计强平价格
    // 止盈止损相关字段
    takeProfitPrice?: string; // 仓位止盈价格
    stopLossPrice?: string; // 仓位止损价格
    totalTakeProfitPrice?: string; // 全仓止盈价格
    totalStopLossPrice?: string; // 全仓止损价格
    tpTriggerPct?: string; // 止盈触发百分比
    slTriggerPct?: string; // 止损触发百分比
    tpTriggerPxType?: string; // 止盈触发类型：1=价格，2=百分比
    slTriggerPxType?: string; // 止损触发类型：1=价格，2=百分比
    tpTriggerPxMode?: string; // 止盈触发模式：1=仓位模式，2=全仓模式
    slTriggerPxMode?: string; // 止损触发模式：1=仓位模式，2=全仓模式
    // 全仓止盈止损算法订单数据
    closeOrderAlgo?: Array<{
        algoId: string;
        closeFraction: string;
        ordType: string;
        slTriggerPx?: string;
        slTriggerPxType?: string;
        tpTriggerPx?: string;
        tpTriggerPxType?: string;
    }>;
    // 资金费用相关字段
    fundingFee?: string; // 累计资金费
    // 仓位止盈止损策略字段
    positionStopLossStrategies?: PositionStrategy[];
}

// 止盈止损配置接口
interface StopLossConfig {
    tpTriggerPx?: string; // 止盈触发价格
    slTriggerPx?: string; // 止损触发价格
    tpTriggerPxType?: '1' | '2'; // 1:价格, 2:百分比
    slTriggerPxType?: '1' | '2';
}

interface OrderListProps {
    apiKeyId: number | null;
    onOrderUpdate: () => void;
}

const OrderList: React.FC<OrderListProps> = ({apiKeyId, onOrderUpdate}) => {
    const [positions, setPositions] = useState<PositionModel[]>([]);
    const [loading, setLoading] = useState(true);
    const [closingPositions, setClosingPositions] = useState<Set<string>>(new Set());

    // K线图弹窗相关状态
    const [chartModalVisible, setChartModalVisible] = useState(false);
    const [selectedPositionForChart, setSelectedPositionForChart] = useState<ActiveChartItem | null>(null);

    // 历史订单相关状态
    const [historyOrders, setHistoryOrders] = useState<TradingOrder[]>([]);
    const [historyTotal, setHistoryTotal] = useState(0);
    const [loadingHistory, setLoadingHistory] = useState(true);
    // 订单详情弹窗状态
    const [orderDetailVisible, setOrderDetailVisible] = useState(false);
    const [selectedOrder, setSelectedOrder] = useState<TradingOrder | null>(null);
    // 瀑布流分页状态
    const [currentPage, setCurrentPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [pendingOrders, setPendingOrders] = useState<PendingOrder[]>([]);
    const [currentTime, setCurrentTime] = useState(Date.now()); // 用于触发持仓时间更新
    const [activeTab, setActiveTab] = useState<'positions' | 'pending' | 'history'>('positions');


    // 止盈止损相关状态
    const [showStopLossModal, setShowStopLossModal] = useState(false);
    const [showTakeProfitModal, setShowTakeProfitModal] = useState(false);
    const [selectedPosition, setSelectedPosition] = useState<PositionModel | null>(null);
    const [stopLossConfig, setStopLossConfig] = useState<StopLossConfig>({});
    const [takeProfitConfig, setTakeProfitConfig] = useState<StopLossConfig>({});
    const [stopLossLoading, setStopLossLoading] = useState(false);
    const [takeProfitLoading, setTakeProfitLoading] = useState(false);

    // 全仓止盈止损相关状态
    const [showTotalStopLossModal, setShowTotalStopLossModal] = useState(false);
    const [totalStopLossConfig, setTotalStopLossConfig] = useState<{
        takeProfitEnabled: boolean;
        stopLossEnabled: boolean;
        takeProfitMode: 'percentage' | 'fixed';
        stopLossMode: 'percentage' | 'fixed';
        takeProfitPercentage?: number;
        stopLossPercentage?: number;
        takeProfitPrice?: number;
        stopLossPrice?: number;
    }>({
        takeProfitEnabled: false,
        stopLossEnabled: false,
        takeProfitMode: 'percentage',
        stopLossMode: 'percentage',
        takeProfitPercentage: undefined,
        stopLossPercentage: undefined,
        takeProfitPrice: undefined,
        stopLossPrice: undefined
    });
    const [totalStopLossLoading, setTotalStopLossLoading] = useState(false);
    const [editingStrategy, setEditingStrategy] = useState<any>(null);

    // 单个策略编辑相关状态
    const [showEditStrategyModal, setShowEditStrategyModal] = useState(false);
    const [editingSingleStrategy, setEditingSingleStrategy] = useState<PositionStrategy | null>(null);
    const [editStrategyConfig, setEditStrategyConfig] = useState<{
        tpTriggerPx: string;
        slTriggerPx: string;
        tpTriggerPxType?: '1' | '2';
        slTriggerPxType?: '1' | '2';
    }>({
        tpTriggerPx: '',
        slTriggerPx: '',
        tpTriggerPxType: '1',
        slTriggerPxType: '1'
    });

    // 设置模式状态
    const [takeProfitSettingMode, setTakeProfitSettingMode] = useState<'profitRate' | 'price'>('profitRate');
    const [stopLossSettingMode, setStopLossSettingMode] = useState<'lossRate' | 'price'>('lossRate');

    // 获取实时仓位数据
    const fetchPositions = async () => {
        try {
            const response = await tradingService.getLivePositions(apiKeyId!);
            setPositions(response.success ? response.data : []);
        } catch (error: any) {
            console.error('获取仓位数据失败:', error);
            setPositions([]);
        }
    };

    // 获取历史订单数据(支持分页瀑布流)
    const fetchHistoryOrders = async (page: number = 0) => {
        try {
            if (page === 0) {
                setLoadingHistory(true);
                setHistoryOrders([]); // 清空旧数据
                setCurrentPage(0);
            } else {
                setLoadingMore(true);
            }

            const response = await tradingService.getHistoryOrders(apiKeyId!, 7, page, 20);

            // 适配ApiResponse结构：response.data.data
            if (response.data && response.data.success && response.data.data) {
                const {data: newOrders, total} = response.data.data;

                if (page === 0) {
                    setHistoryOrders(newOrders);
                    setHistoryTotal(total);
                } else {
                    setHistoryOrders(prev => [...prev, ...newOrders]);
                    setHistoryTotal(total);
                }

                setCurrentPage(page);
                setHasMore((page + 1) * 20 < total);
            } else {
                console.error('获取历史订单数据失败：无效的响应结构', response);
                if (page === 0) {
                    setHistoryOrders([]);
                }
            }
        } catch (error: any) {
            console.error('获取历史订单数据失败:', error);
            if (page === 0) {
                setHistoryOrders([]);
            }
        } finally {
            setLoadingHistory(false);
            setLoadingMore(false);
        }
    };

    // 加载更多历史订单
    const loadMoreHistoryOrders = () => {
        if (!loadingMore && hasMore && !loadingHistory) {
            fetchHistoryOrders(currentPage + 1);
        }
    };

    // 历史订单滚动监听
    const handleHistoryScroll = (e: React.UIEvent<HTMLDivElement>) => {
        // 只在历史订单tab启用滚动加载
        if (activeTab !== 'history') return;

        const container = e.currentTarget;
        const isNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 100;

        if (isNearBottom && hasMore && !loadingMore && !loadingHistory) {
            loadMoreHistoryOrders();
        }
    };

    // 获取当前委托订单数据
    const fetchPendingOrders = async () => {
        try {
            const response = await tradingService.getPendingOrders(apiKeyId!);
            setPendingOrders(response.success ? response.data : []);
        } catch (error: any) {
            console.error('获取当前委托订单数据失败:', error);
            setPendingOrders([]);
        }
    };


    // 初始化数据加载
    const loadData = async () => {
        setLoading(true);
        setLoadingHistory(true);
        try {
            // 并行加载所有数据，提高速度
            await Promise.all([
                fetchPositions(),
                fetchHistoryOrders(),
                fetchPendingOrders()
            ]);
        } finally {
            setLoading(false);
            setLoadingHistory(false);
        }
    };

    // 组件挂载时加载数据
    useEffect(() => {
        if (apiKeyId) {
            // 切换API Key时清空旧数据，避免显示错误数据
            setPositions([]);
            setHistoryOrders([]);
            setPendingOrders([]);
            loadData();
        }
    }, [apiKeyId]);

    // 更新当前时间的定时器（每秒更新一次持仓时间显示）
    useEffect(() => {
        const timer = setInterval(() => {
            setCurrentTime(Date.now());
        }, 1000); // 每秒更新一次

        return () => clearInterval(timer);
    }, []);

    // 使用定时器自动刷新仓位数据
    const {isActive} = usePageTimer(
        async () => {
            await fetchPositions();
            onOrderUpdate();
        },
        {
            interval: 5000, // 5秒刷新一次
            autoStart: true,
            enabled: !!apiKeyId && activeTab === 'positions'
        }
    );

    // 历史订单自动刷新（30秒间隔）
    const {isActive: isHistoryActive} = usePageTimer(
        async () => {
            // 获取历史订单（从数据库查询）
            await fetchHistoryOrders();
        },
        {
            interval: 30000, // 30秒刷新一次
            autoStart: true,
            enabled: !!apiKeyId && activeTab === 'history'
        }
    );

    // 当前委托订单自动刷新（5秒间隔）
    usePageTimer(
        async () => {
            await fetchPendingOrders();
        },
        {
            interval: 5000, // 5秒刷新一次
            autoStart: true,
            enabled: !!apiKeyId && activeTab === 'pending'
        }
    );


    // 止盈止损设置弹窗处理
    const handleOpenStopLossModal = (position: PositionModel) => {
        setSelectedPosition(position);

        // 初始化配置，基于现有仓位数据
        const avgPrice = Number(position.avgPx || 0);
        const initialConfig: StopLossConfig = {
            tpTriggerPx: position.takeProfitPrice?.toString(),
            slTriggerPx: position.stopLossPrice?.toString(),
            tpTriggerPxType: position.tpTriggerPxType as '1' | '2' || '2', // 默认百分比
            slTriggerPxType: position.slTriggerPxType as '1' | '2' || '2'
        };

        // 如果有百分比数据，计算价格
        if (position.tpTriggerPct && !position.takeProfitPrice) {
            const tpPct = position.tpTriggerPct;
            initialConfig.tpTriggerPx = (avgPrice * (1 + tpPct / 100)).toFixed(8);
        }

        if (position.slTriggerPct && !position.stopLossPrice) {
            const slPct = position.slTriggerPct;
            initialConfig.slTriggerPx = (avgPrice * (1 + slPct / 100)).toFixed(8);
        }

        setStopLossConfig(initialConfig);
        setShowStopLossModal(true);
    };

    // 关闭止盈止损弹窗
    const handleCloseStopLossModal = () => {
        setShowStopLossModal(false);
        setSelectedPosition(null);
        setStopLossConfig({});
    };

    // 去除尾部零的辅助函数
    const removeTrailingZeros = (numStr: string): string => {
        // 如果包含小数点，去除尾部的0
        if (numStr.includes('.')) {
            return numStr.replace(/\.?0+$/, '');
        }
        return numStr;
    };

    // 数字格式化函数 - 最多4位小数，去除尾部0（无美元符号）
    const formatPrice = (price: string | number | null | undefined, defaultValue: string = '0'): string => {
        if (!price) return defaultValue;
        const numPrice = typeof price === 'string' ? parseFloat(price) : price;
        if (isNaN(numPrice)) return defaultValue;
        const formatted = removeTrailingZeros(numPrice.toFixed(4));
        return formatted;
    };

    // 格式化全仓止盈止损价格显示
    const formatStopLossPrice = (price: string | number | null | undefined): string => {
        if (price !== null && price !== undefined && price !== '') {
            const numPrice = typeof price === 'string' ? parseFloat(price) : price;
            if (!isNaN(numPrice) && numPrice > 0) {
                const formatted = removeTrailingZeros(numPrice.toFixed(4));
                return `$${formatted}`;
            }
        }
        return '未设置';
    };

    // 格式化策略价格显示（带null检查）
    const formatStrategyPrice = (price: string | null | undefined): string => {
        if (price) {
            const numPrice = parseFloat(price);
            if (!isNaN(numPrice)) {
                const formatted = removeTrailingZeros(numPrice.toFixed(4));
                return `$${formatted}`;
            }
        }
        return '-';
    };

    // 格式化计算价格（用于显示计算结果，不包含$前缀）
    const formatCalculatedPrice = (price: number | undefined): string => {
        if (price === undefined || isNaN(price) || !isFinite(price) || price <= 0) return '未设置';
        return removeTrailingZeros(price.toFixed(4));
    };

    // 格式化百分比，保留2位小数
    const formatPercentage = (percentage: number): number => {
        return Math.round(percentage * 100) / 100;
    };

    // 全仓止盈止损设置弹窗处理
    const handleOpenTotalStopLossModal = async (position: PositionModel) => {
        setSelectedPosition(position);

        const avgPrice = Number(position.avgPx || 0);

        try {
            // 首先检查仓位数据中是否有全仓止盈止损信息
            const hasTakeProfit = !!(position.totalTakeProfitPrice && position.totalTakeProfitPrice > 0);
            const hasStopLoss = !!(position.totalStopLossPrice && position.totalStopLossPrice > 0);

            let currentStrategy = null;

            // 优先使用 closeOrderAlgo 中的策略信息（这是真实存在的策略）
            if (position.closeOrderAlgo && position.closeOrderAlgo.length > 0) {
                const closeOrderAlgo = position.closeOrderAlgo[0];
                currentStrategy = {
                    algoId: closeOrderAlgo.algoId,
                    instId: position.instId,
                    posSide: position.posSide,
                    tpTriggerPx: closeOrderAlgo.tpTriggerPx,
                    slTriggerPx: closeOrderAlgo.slTriggerPx,
                    tpTriggerPxMode: '2',
                    slTriggerPxMode: '2',
                    tpTriggerPxType: '1',
                    slTriggerPxType: '1',
                    sz: position.position,
                    cTime: new Date().toISOString()
                };
            }
            // else {
            //     // 如果没有 closeOrderAlgo，尝试从 API 获取策略信息
            //     const strategiesResponse = await tradingService.getTotalStopLossStrategies({
            //         apiKeyId: apiKeyId!,
            //         instId: position.instId
            //     });
            //
            //     if (strategiesResponse.data && strategiesResponse.data.data && strategiesResponse.data.data.length > 0) {
            //         // 查找对应持仓方向的策略，同时匹配合约ID和持仓方向
            //         currentStrategy = strategiesResponse.data.data.find((strategy: any) =>
            //             strategy.instId === position.instId && strategy.posSide === position.posSide
            //         );
            //     }
            // }

            // 如果仓位数据中有全仓止盈止损但没有找到任何策略，创建一个虚拟策略对象用于显示编辑状态
            if (!currentStrategy && (hasTakeProfit || hasStopLoss)) {
                currentStrategy = {
                    algoId: 'EXISTING_POSITION_STRATEGY',
                    instId: position.instId,
                    posSide: position.posSide,
                    tpTriggerPx: position.totalTakeProfitPrice,
                    slTriggerPx: position.totalStopLossPrice,
                    tpTriggerPxMode: '2',
                    slTriggerPxMode: '2',
                    tpTriggerPxType: '1',
                    slTriggerPxType: '1',
                    sz: position.position,
                    cTime: new Date().toISOString()
                };
            }

            setEditingStrategy(currentStrategy);

            // 初始化全仓止盈止损配置
            const initialConfig: {
                takeProfitEnabled: boolean;
                stopLossEnabled: boolean;
                takeProfitMode: 'percentage' | 'fixed';
                stopLossMode: 'percentage' | 'fixed';
                takeProfitPercentage?: number;
                stopLossPercentage?: number;
                takeProfitPrice?: number;
                stopLossPrice?: number;
            } = {
                takeProfitEnabled: hasTakeProfit,
                stopLossEnabled: hasStopLoss,
                takeProfitMode: 'percentage',
                stopLossMode: 'percentage',
                takeProfitPercentage: undefined,
                stopLossPercentage: undefined,
                takeProfitPrice: undefined,
                stopLossPrice: undefined
            };

            // 使用策略数据或仓位数据初始化配置
            if (currentStrategy) {
                const tpPrice = Number(currentStrategy.tpTriggerPx);
                if (Number.isFinite(tpPrice) && tpPrice > 0) {
                    const tpPercentage = ((tpPrice - avgPrice) / avgPrice) * 100;
                    initialConfig.takeProfitEnabled = true;
                    initialConfig.takeProfitPercentage = formatPercentage(Math.abs(tpPercentage));
                    initialConfig.takeProfitPrice = tpPrice;
                }

                const slPrice = Number(currentStrategy.slTriggerPx);
                if (Number.isFinite(slPrice) && slPrice > 0) {
                    const slPercentage = ((slPrice - avgPrice) / avgPrice) * 100;
                    initialConfig.stopLossEnabled = true;
                    initialConfig.stopLossPercentage = formatPercentage(Math.abs(slPercentage));
                    initialConfig.stopLossPrice = slPrice;
                }
            }

            setTotalStopLossConfig(initialConfig);
            setShowTotalStopLossModal(true);
        } catch (error) {
            console.error('获取全仓止盈止损策略失败:', error);
            const errorMessage = error instanceof Error ? error.message : '获取策略信息失败';
            message.error(errorMessage);
            // 即使获取失败也显示弹窗，使用默认配置
            setEditingStrategy(null);
            setShowTotalStopLossModal(true);
        }
    };

    // 关闭全仓止盈止损弹窗
    const handleCloseTotalStopLossModal = () => {
        setShowTotalStopLossModal(false);
        setSelectedPosition(null);
        setEditingStrategy(null);
        setTotalStopLossConfig({
            takeProfitEnabled: false,
            stopLossEnabled: false,
            takeProfitMode: 'percentage',
            stopLossMode: 'percentage'
        });
    };

    // 撤销全仓止盈止损策略处理
    const handleCancelStopLossStrategies = async (position: PositionModel) => {
        if (!apiKeyId) {
            message.error('未选择API Key');
            return;
        }

        // 只收集全仓止盈止损策略（closeOrderAlgo中的策略）
        const totalStopLossStrategies: Array<{ algoId: string; type: string }> = [];

        // 只收集 closeOrderAlgo 中的全仓策略
        if (position.closeOrderAlgo && position.closeOrderAlgo.length > 0) {
            position.closeOrderAlgo.forEach(algo => {
                if (algo.algoId) {
                    totalStopLossStrategies.push({
                        algoId: algo.algoId,
                        type: 'totalStopLoss'
                    });
                }
            });
        }

        if (totalStopLossStrategies.length === 0) {
            message.warning('未找到需要撤销的全仓止盈止损策略');
            return;
        }

        // 显示确认对话框
        Modal.confirm({
            title: <span style={{color: '#ffffff'}}>确认撤销全仓策略</span>,
            content: (
                <div style={{color: '#a0a0a0', lineHeight: '1.6'}}>
                    <p style={{marginBottom: '16px'}}>确定要撤销该仓位的全仓止盈止损策略吗？</p>
                    <div style={{
                        background: '#2a2a2a',
                        padding: '12px 16px',
                        borderRadius: '6px',
                        marginBottom: '16px',
                        border: '1px solid #404040'
                    }}>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>合约：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>{position.instId}</span>
                            </div>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>方向：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>
                                    {position.posSide === 'long' ? (
                                        <span style={{color: '#52c41a'}}>做多</span>
                                    ) : (
                                        <span style={{color: '#ff4d4f'}}>做空</span>
                                    )}
                                </span>
                            </div>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>全仓策略数量：</span>
                                <span style={{
                                    color: '#ffffff',
                                    fontWeight: '500'
                                }}>{totalStopLossStrategies.length} 个</span>
                            </div>
                        </div>
                    </div>
                    <div style={{
                        background: 'rgba(255, 77, 79, 0.1)',
                        border: '1px solid rgba(255, 77, 79, 0.3)',
                        borderRadius: '6px',
                        padding: '12px 16px',
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: '8px'
                    }}>
                        <span style={{color: '#ff4d4f', fontSize: '16px', marginTop: '2px'}}>⚠</span>
                        <div>
                            <div style={{color: '#ff4d4f', fontWeight: '500', marginBottom: '4px'}}>重要提示</div>
                            <div style={{color: '#ffcccc', fontSize: '14px'}}>
                                此操作仅撤销全仓止盈止损策略，不会影响仓位级别的策略
                            </div>
                        </div>
                    </div>
                </div>
            ),
            okText: '确认撤销',
            cancelText: '取消',
            okType: 'primary',
            okButtonProps: {
                style: {
                    backgroundColor: '#ff4d4f',
                    borderColor: '#ff4d4f',
                    boxShadow: 'none'
                }
            },
            cancelButtonProps: {
                style: {
                    backgroundColor: 'transparent',
                    borderColor: '#434343',
                    color: '#ffffff'
                }
            },
            centered: true,
            width: 480,
            maskClosable: false,
            style: {
                background: '#171717'
            },
            wrapClassName: 'dark-modal-wrapper',
            className: 'dark-modal-content',
            onOk: async () => {
                let successCount = 0;
                let failCount = 0;

                // 逐个撤销全仓策略
                for (const strategy of totalStopLossStrategies) {
                    try {
                        const response = await tradingService.cancelStopLossAlgos({
                            apiKeyId,
                            instId: position.instId || '',
                            algoId: strategy.algoId
                        });

                        if (response.data.success) {
                            successCount++;
                            console.log(`成功撤销全仓策略: ${strategy.algoId}`);
                        } else {
                            failCount++;
                            console.error(`撤销全仓策略失败: ${strategy.algoId} - ${response.data.message}`);
                        }
                    } catch (error: any) {
                        failCount++;
                        console.error(`撤销全仓策略异常: ${strategy.algoId} -`, error);
                    }
                }

                // 显示结果
                if (successCount > 0 && failCount === 0) {
                    message.success(`成功撤销 ${successCount} 个全仓止盈止损策略`);
                } else if (successCount > 0 && failCount > 0) {
                    message.warning(`部分成功：撤销 ${successCount} 个全仓策略，失败 ${failCount} 个策略`);
                } else {
                    message.error(`撤销失败，${failCount} 个全仓策略均撤销失败`);
                }

                // 延迟150ms刷新数据,等待服务器处理完成
                setTimeout(async () => {
                    await fetchPositions();
                    onOrderUpdate();
                }, 150);
            }
        });

        // 添加全局CSS样式来强制覆盖
        const style = document.createElement('style');
        style.textContent = `
            .dark-modal-wrapper .ant-modal-content {
                background: #171717 !important;
                border: 1px solid #404040 !important;
            }
            .dark-modal-wrapper .ant-modal-header {
                background: #171717 !important;
                border-bottom: 1px solid #404040 !important;
            }
            .dark-modal-wrapper .ant-modal-body {
                background: #171717 !important;
            }
            .dark-modal-wrapper .ant-modal-footer {
                background: #171717 !important;
                border-top: 1px solid #404040 !important;
            }
        `;
        document.head.appendChild(style);

        // 清理样式
        setTimeout(() => {
            const existingStyle = document.querySelector('style[data-dark-modal]');
            if (existingStyle) {
                existingStyle.remove();
            }
            style.setAttribute('data-dark-modal', 'true');
        }, 100);
    };

    // 处理单个策略编辑
    const handleEditStrategy = (strategy: PositionStrategy) => {
        if (!apiKeyId) {
            message.error('未选择API Key');
            return;
        }

        // 设置当前编辑的策略数据
        setEditingSingleStrategy(strategy);

        // 初始化编辑配置 - 预填充当前策略数据
        setEditStrategyConfig({
            tpTriggerPx: strategy.tpTriggerPx || '',
            slTriggerPx: strategy.slTriggerPx || '',
            tpTriggerPxType: '1', // 默认为按价格触发
            slTriggerPxType: '1'  // 默认为按价格触发
        });

        // 显示编辑弹窗
        setShowEditStrategyModal(true);
    };

    // 处理单个策略撤销
    const handleCancelSingleStrategy = async (strategy: PositionStrategy) => {
        if (!apiKeyId) {
            message.error('未选择API Key');
            return;
        }

        if (!strategy.algoId) {
            message.error('未找到策略ID，无法撤销');
            return;
        }

        // 显示确认对话框
        Modal.confirm({
            title: <span style={{color: '#ffffff'}}>确认撤销策略</span>,
            content: (
                <div style={{color: '#a0a0a0', lineHeight: '1.6'}}>
                    <p style={{marginBottom: '16px'}}>确定要撤销该策略吗？</p>
                    <div style={{
                        background: '#2a2a2a',
                        padding: '12px 16px',
                        borderRadius: '6px',
                        marginBottom: '16px',
                        border: '1px solid #404040'
                    }}>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>合约：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>{strategy.instId}</span>
                            </div>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>方向：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>
                                    {strategy.posSide === 'long' ? (
                                        <span style={{color: '#52c41a'}}>做多</span>
                                    ) : (
                                        <span style={{color: '#ff4d4f'}}>做空</span>
                                    )}
                                </span>
                            </div>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>止盈价：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>
                                    {strategy.tpTriggerPx || '未设置'}
                                </span>
                            </div>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>止损价：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>
                                    {strategy.slTriggerPx || '未设置'}
                                </span>
                            </div>
                        </div>
                    </div>
                    <div style={{
                        background: 'rgba(255, 77, 79, 0.1)',
                        border: '1px solid rgba(255, 77, 79, 0.3)',
                        borderRadius: '6px',
                        padding: '12px 16px',
                        display: 'flex',
                        alignItems: 'flex-start',
                        gap: '8px'
                    }}>
                        <span style={{color: '#ff4d4f', fontSize: '16px', marginTop: '2px'}}>⚠</span>
                        <div>
                            <div style={{color: '#ff4d4f', fontWeight: '500', marginBottom: '4px'}}>重要提示</div>
                            <div style={{color: '#ffcccc', fontSize: '14px'}}>
                                撤销后该策略将立即失效，请确认操作
                            </div>
                        </div>
                    </div>
                </div>
            ),
            centered: true,
            okText: '确认撤销',
            cancelText: '取消',
            okButtonProps: {danger: true},
            className: 'dark-modal-wrapper',
            onOk: async () => {
                try {
                    const response = await tradingService.cancelStopLossAlgos({
                        apiKeyId,
                        instId: strategy.instId,
                        algoId: strategy.algoId
                    });

                    if (response.data.success) {
                        message.success('策略撤销成功');
                        // 刷新数据
                        await fetchPositions();
                        onOrderUpdate();
                    } else {
                        Modal.error({
                            title: <span style={{color: '#ffffff'}}>撤销失败</span>,
                            content: (
                                <div style={{color: '#a0a0a0'}}>
                                    {response.data.message}
                                </div>
                            ),
                            centered: true,
                            className: 'dark-theme-modal'
                        });
                    }
                } catch (error: any) {
                    console.error('撤销策略失败:', error);
                    Modal.error({
                        title: <span style={{color: '#ffffff'}}>撤销失败</span>,
                        content: (
                            <div style={{color: '#a0a0a0'}}>
                                {error.response?.data?.message || error.message}
                            </div>
                        ),
                        centered: true,
                        className: 'dark-theme-modal'
                    });
                }
            }
        });

        // 添加全局CSS样式来强制覆盖
        const style = document.createElement('style');
        style.textContent = `
            .dark-modal-wrapper .ant-modal-content {
                background: #171717 !important;
                border: 1px solid #404040 !important;
            }
            .dark-modal-wrapper .ant-modal-header {
                background: #171717 !important;
                border-bottom: 1px solid #404040 !important;
            }
            .dark-modal-wrapper .ant-modal-body {
                background: #171717 !important;
            }
            .dark-modal-wrapper .ant-modal-footer {
                background: #171717 !important;
                border-top: 1px solid #404040 !important;
            }
        `;
        document.head.appendChild(style);

        // 清理样式
        setTimeout(() => {
            const existingStyle = document.querySelector('style[data-dark-modal]');
            if (existingStyle) {
                existingStyle.remove();
            }
            style.setAttribute('data-dark-modal', 'true');
        }, 100);
    };

    // 价格校验函数
    const validateStopLossPrice = (triggerPrice: string, currentPrice: number, isLong: boolean): {
        isValid: boolean;
        message?: string
    } => {
        const price = parseFloat(triggerPrice);
        if (isNaN(price) || price <= 0) {
            return {isValid: false, message: '请输入有效的价格'};
        }

        const priceDiffPercent = Math.abs((price - currentPrice) / currentPrice * 100);

        // 对于多头，止损价格应该低于当前价格
        if (isLong && price >= currentPrice) {
            return {isValid: false, message: '多头止损价格必须低于当前价格'};
        }

        // 对于空头，止损价格应该高于当前价格
        if (!isLong && price <= currentPrice) {
            return {isValid: false, message: '空头止损价格必须高于当前价格'};
        }

        // 检查价格差异是否过小（小于0.1%）
        if (priceDiffPercent < 0.1) {
            return {isValid: false, message: '止损价格与当前价格差异过小，建议至少相差0.1%'};
        }

        // 检查价格差异是否过大（大于50%）
        if (priceDiffPercent > 50) {
            return {isValid: false, message: '止损价格与当前价格差异过大，请确认设置是否正确'};
        }

        return {isValid: true};
    };

    // 保存止盈止损配置
    const handleSaveStopLossConfig = async () => {
        if (!selectedPosition || !apiKeyId) {
            return;
        }

        // 价格校验
        if (stopLossConfig.slTriggerPx) {
            const currentPosition = positions.find(p => p.instId === selectedPosition.instId && p.posSide === selectedPosition.posSide);
            const currentPrice = currentPosition ? currentPosition.markPx : selectedPosition.markPx || 0;
            const isLong = selectedPosition.posSide === 'long';

            const validation = validateStopLossPrice(stopLossConfig.slTriggerPx, Number(currentPrice), isLong);
            if (!validation.isValid) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>设置失败</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            {validation.message}
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                return;
            }
        }

        setStopLossLoading(true);
        try {
            // 构建止盈止损配置
            const config = {
                apiKeyId,
                instId: selectedPosition.instId || '',
                side: (selectedPosition.posSide === 'long' ? 'sell' : 'buy') as 'buy' | 'sell', // 平仓方向与持仓方向相反
                posSide: selectedPosition.posSide as 'long' | 'short',
                sz: Number(selectedPosition.position || 0),
                tpTriggerPx: stopLossConfig.tpTriggerPx,
                tpTriggerPxType: stopLossConfig.tpTriggerPxType,
                slTriggerPx: stopLossConfig.slTriggerPx,
                slTriggerPxType: stopLossConfig.slTriggerPxType
            };

            const response = await tradingService.setStopLossOrder(config);

            if (response.data.success) {
                message.success('止盈止损设置成功');
                handleCloseStopLossModal();
                await fetchPositions(); // 刷新仓位数据
                onOrderUpdate();
            } else {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>设置失败</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            {response.data.message}
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
            }
        } catch (error: any) {
            console.error('设置止盈止损失败:', error);
            Modal.error({
                title: <span style={{color: '#ffffff'}}>设置失败</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        {error.response?.data?.message || error.message}
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
        } finally {
            setStopLossLoading(false);
        }
    };

    // 打开止盈设置弹窗
    const handleOpenTakeProfitModal = (position: PositionModel) => {
        setSelectedPosition(position);

        // 初始化配置，基于现有仓位数据
        const avgPrice = Number(position.avgPx || 0);
        const initialConfig: StopLossConfig = {
            tpTriggerPx: position.takeProfitPrice?.toString(),
            slTriggerPx: position.stopLossPrice?.toString(),
            tpTriggerPxType: position.tpTriggerPxType as '1' | '2' || '2', // 默认百分比
            slTriggerPxType: position.slTriggerPxType as '1' | '2' || '2'
        };

        // 如果有百分比数据，计算价格
        if (position.tpTriggerPct && !position.takeProfitPrice) {
            const tpPct = position.tpTriggerPct;
            initialConfig.tpTriggerPx = (avgPrice * (1 + tpPct / 100)).toFixed(8);
        }

        if (position.slTriggerPct && !position.stopLossPrice) {
            const slPct = position.slTriggerPct;
            initialConfig.slTriggerPx = (avgPrice * (1 + slPct / 100)).toFixed(8);
        }

        setTakeProfitConfig(initialConfig);
        setShowTakeProfitModal(true);
    };

    // 关闭止盈弹窗
    const handleCloseTakeProfitModal = () => {
        setShowTakeProfitModal(false);
        setSelectedPosition(null);
        setTakeProfitConfig({});
    };

    // 止盈价格校验函数
    const validateTakeProfitPrice = (triggerPrice: string, currentPrice: number, isLong: boolean): {
        isValid: boolean;
        message?: string
    } => {
        const price = parseFloat(triggerPrice);
        if (isNaN(price) || price <= 0) {
            return {isValid: false, message: '请输入有效的价格'};
        }

        const priceDiffPercent = Math.abs((price - currentPrice) / currentPrice * 100);

        // 对于多头，止盈价格应该高于当前价格
        if (isLong && price <= currentPrice) {
            return {isValid: false, message: '多头止盈价格必须高于当前价格'};
        }

        // 对于空头，止盈价格应该低于当前价格
        if (!isLong && price >= currentPrice) {
            return {isValid: false, message: '空头止盈价格必须低于当前价格'};
        }

        // 检查价格差异是否过小（小于0.1%）
        if (priceDiffPercent < 0.1) {
            return {isValid: false, message: '止盈价格与当前价格差异过小，建议至少相差0.1%'};
        }

        // 检查价格差异是否过大（大于100%）
        if (priceDiffPercent > 100) {
            return {isValid: false, message: '止盈价格与当前价格差异过大，请确认设置是否正确'};
        }

        return {isValid: true};
    };

    // 保存止盈止损配置
    const handleSaveTakeProfitConfig = async () => {
        if (!selectedPosition || !apiKeyId) {
            return;
        }

        // 价格校验
        if (takeProfitConfig.tpTriggerPx) {
            const currentPosition = positions.find(p => p.instId === selectedPosition.instId && p.posSide === selectedPosition.posSide);
            const currentPrice = currentPosition ? currentPosition.markPx : selectedPosition.markPx || 0;
            const isLong = selectedPosition.posSide === 'long';

            const validation = validateTakeProfitPrice(takeProfitConfig.tpTriggerPx, Number(currentPrice), isLong);
            if (!validation.isValid) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>设置失败</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            {validation.message}
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                return;
            }
        }

        setTakeProfitLoading(true);
        try {
            // 构建止盈止损配置
            const config = {
                apiKeyId,
                instId: selectedPosition.instId || '',
                side: (selectedPosition.posSide === 'long' ? 'sell' : 'buy') as 'buy' | 'sell', // 平仓方向与持仓方向相反
                posSide: selectedPosition.posSide as 'long' | 'short',
                sz: Number(selectedPosition.position || 0),
                tpTriggerPx: takeProfitConfig.tpTriggerPx,
                tpTriggerPxType: takeProfitConfig.tpTriggerPxType,
                slTriggerPx: takeProfitConfig.slTriggerPx,
                slTriggerPxType: takeProfitConfig.slTriggerPxType
            };

            const response = await tradingService.setStopLossOrder(config);

            if (response.data.success) {
                message.success('止盈止损设置成功');
                handleCloseTakeProfitModal();
                await fetchPositions(); // 刷新仓位数据
                onOrderUpdate();
            } else {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>设置失败</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            {response.data.message}
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
            }
        } catch (error: any) {
            console.error('设置止盈止损失败:', error);
            Modal.error({
                title: <span style={{color: '#ffffff'}}>设置失败</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        {error.response?.data?.message || error.message}
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
        } finally {
            setTakeProfitLoading(false);
        }
    };

    // 市价平仓
    // 打开仓位K线图弹窗
    const handleOpenPositionChart = (position: PositionModel) => {
        console.log('[OrderList] 打开K线图弹窗 - 合约:', position.instId);
        // 转换为 ActiveChartItem 格式
        const chartItem: ActiveChartItem = {
            type: 'position',
            instId: position.instId || '',
            posSide: position.posSide || 'long',
            ctime: position.cTime ?? Date.now(),
            referencePrice: position.avgPx,
            detail: position
        };
        setSelectedPositionForChart(chartItem);
        setChartModalVisible(true);
    };

    // 关闭K线图弹窗
    const handleCloseChartModal = () => {
        console.log('[OrderList] 关闭K线图弹窗');
        setChartModalVisible(false);
        setSelectedPositionForChart(null);
    };

    // 打开订单详情弹窗
    const handleOpenOrderDetail = (order: TradingOrder) => {
        setSelectedOrder(order);
        setOrderDetailVisible(true);
    };

    // 关闭订单详情弹窗
    const handleCloseOrderDetail = () => {
        setOrderDetailVisible(false);
        setSelectedOrder(null);
    };

    const handleMarketClosePosition = async (position: PositionModel) => {
        if (!apiKeyId) {
            return;
        }

        // 调试信息
        console.log('平仓确认弹窗 - 盈亏信息:', {
            upl: position.upl,
            uplValue: Number(position.upl || 0),
            isProfit: Number(position.upl || 0) >= 0,
            buttonColor: Number(position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f'
        });

        Modal.confirm({
            title: (
                <span style={{color: '#ffffff'}}>
                    确认平仓
                </span>
            ),
            content: (
                <div>
                    <div style={{color: '#a0a0a0', fontSize: '14px', marginBottom: '16px'}}>
                        确定要以市价平仓
                        <span style={{
                            color: position.posSide === 'long' ? '#52c41a' : '#ff4d4f',
                            fontWeight: 'bold',
                            margin: '0 4px'
                        }}>
                            {position.instId}
                        </span>
                        的
                        <span style={{
                            color: position.posSide === 'long' ? '#52c41a' : '#ff4d4f',
                            fontWeight: 'bold',
                            margin: '0 4px'
                        }}>
                            {position.posSide === 'long' ? '多头' : '空头'}
                        </span>
                        仓位吗？
                    </div>

                    {/* 盈亏情况展示 */}
                    <div style={{
                        background: Number(position.upl || 0) >= 0
                            ? 'linear-gradient(135deg, rgba(82, 196, 26, 0.1) 0%, rgba(82, 196, 26, 0.05) 100%)'
                            : 'linear-gradient(135deg, rgba(255, 77, 79, 0.1) 0%, rgba(255, 77, 79, 0.05) 100%)',
                        border: `1px solid ${Number(position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f'}`,
                        borderRadius: '8px',
                        padding: '16px',
                        marginTop: '12px',
                        boxShadow: `0 2px 8px ${Number(position.upl || 0) >= 0
                            ? 'rgba(82, 196, 26, 0.15)'
                            : 'rgba(255, 77, 79, 0.15)'}`
                    }}>
                        {/* 盈亏状态指示器 */}
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            marginBottom: '12px',
                            paddingBottom: '8px',
                            borderBottom: `1px solid ${Number(position.upl || 0) >= 0
                                ? 'rgba(82, 196, 26, 0.2)'
                                : 'rgba(255, 77, 79, 0.2)'}`
                        }}>
                            <div style={{
                                width: '8px',
                                height: '8px',
                                borderRadius: '50%',
                                backgroundColor: Number(position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f',
                                marginRight: '8px',
                                boxShadow: `0 0 4px ${Number(position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f'}`
                            }}></div>
                            <span style={{
                                color: Number(position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f',
                                fontSize: '14px',
                                fontWeight: 'bold'
                            }}>
                                {Number(position.upl || 0) >= 0 ? '盈利状态' : '亏损状态'}
                            </span>
                        </div>

                        {/* 盈亏金额 */}
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginBottom: '10px'
                        }}>
                            <span style={{color: '#888', fontSize: '12px'}}>未实现盈亏</span>
                            <span style={{
                                color: Number(position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f',
                                fontSize: '18px',
                                fontWeight: 'bold',
                                textShadow: `0 1px 2px ${Number(position.upl || 0) >= 0
                                    ? 'rgba(82, 196, 26, 0.3)'
                                    : 'rgba(255, 77, 79, 0.3)'}`
                            }}>
                                {Number(position.upl || 0) >= 0 ? '+' : ''}{Number(position.upl || 0).toFixed(2)}
                            </span>
                        </div>

                        {/* 盈亏率 */}
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center'
                        }}>
                            <span style={{color: '#888', fontSize: '12px'}}>盈亏率</span>
                            <span style={{
                                color: Number(position.uplRatio ?? 0) >= 0 ? '#52c41a' : '#ff4d4f',
                                fontSize: '14px',
                                fontWeight: '600'
                            }}>
                                {Number(position.uplRatio ?? 0) >= 0 ? '+' : ''}{(Number(position.uplRatio ?? 0) * 100).toFixed(2)}%
                            </span>
                        </div>
                    </div>
                </div>
            ),
            okText: '确认平仓',
            cancelText: '取消',
            okButtonProps: {
                className: Number(position.upl ?? 0) >= 0 ? 'profit-confirm-button' : 'loss-confirm-button'
            },
            cancelButtonProps: {
                style: {
                    borderColor: '#434343',
                    color: '#a0a0a0'
                }
            },
            centered: true,
            className: 'dark-theme-modal',
            onOk: async () => {
                const posKey = position.posId ?? `${position.instId ?? 'unknown'}-${position.posSide ?? 'unknown'}`;
                setClosingPositions(prev => new Set(prev).add(posKey));
                try {
                    if (!position.instId) {
                        throw new Error('缺少instId，无法平仓');
                    }
                    const response = await tradingService.closePosition({
                        apiKeyId,
                        instId: position.instId,
                        posSide: position.posSide as 'long' | 'short',
                        sz: Number(position.position ?? 0),
                        mgnMode: position.mgnMode
                    });

                    if (response.data.success) {
                        Modal.success({
                            title: <span style={{color: '#ffffff'}}>平仓成功</span>,
                            content: (
                                <div style={{color: '#a0a0a0'}}>
                                    仓位已成功平仓
                                </div>
                            ),
                            centered: true,
                            className: 'dark-theme-modal'
                        });
                        // 延迟150ms刷新数据,等待服务器处理完成
                        setTimeout(async () => {
                            await fetchPositions();
                            onOrderUpdate();
                        }, 150);
                    } else {
                        Modal.error({
                            title: <span style={{color: '#ffffff'}}>平仓失败</span>,
                            content: (
                                <div style={{color: '#a0a0a0'}}>
                                    {response.data.message}
                                </div>
                            ),
                            centered: true,
                            className: 'dark-theme-modal'
                        });
                    }
                } catch (error: any) {
                    console.error('平仓失败:', error);
                    Modal.error({
                        title: <span style={{color: '#ffffff'}}>平仓失败</span>,
                        content: (
                            <div style={{color: '#a0a0a0'}}>
                                {error.response?.data?.message || error.message}
                            </div>
                        ),
                        centered: true,
                        className: 'dark-theme-modal'
                    });
                } finally {
                    setClosingPositions(prev => {
                        const newSet = new Set(prev);
                        const posKey = position.posId ?? `${position.instId ?? 'unknown'}-${position.posSide ?? 'unknown'}`;
                        newSet.delete(posKey);
                        return newSet;
                    });
                }
            }
        });
    };


    // 计算强平价格的函数
    const calculateLiquidationPrice = (avgPrice: number, lever: number, posSide: 'long' | 'short'): number => {
        // 强平价格计算公式 (简化版)
        // 多头：强平价格 = 开仓价格 * (1 - 1/杠杆倍数)
        // 空头：强平价格 = 开仓价格 * (1 + 1/杠杆倍数)
        if (posSide === 'long') {
            return avgPrice * (1 - 1 / lever);
        } else {
            return avgPrice * (1 + 1 / lever);
        }
    };

    // 获取杠杆对应的最大百分比限制
    const getMaxPercentageByLeverage = (lever: number): number => {
        // 根据用户要求：
        // 3倍杠杆最高32%
        // 5倍杠杆最高19%
        // 其他杠杆按比例计算
        if (lever <= 3) {
            return 32;
        } else if (lever >= 5) {
            return 19;
        } else {
            // 3-5倍杠杆之间线性插值
            const ratio = (lever - 3) / (5 - 3);
            return 32 - (32 - 19) * ratio;
        }
    };

    // 校验止盈止损设置
    const validateStopLossSettings = (price: number | undefined, percentage: number | undefined,
                                      lever: number, avgPrice: number, posSide: 'long' | 'short',
                                      isStopLoss: boolean = false): { isValid: boolean; message?: string } => {
        if (!price && !percentage) {
            return {isValid: false, message: '请设置价格或百分比'};
        }

        const liquidationPrice = calculateLiquidationPrice(avgPrice, lever, posSide);
        const maxPercentage = getMaxPercentageByLeverage(lever);

        // 百分比模式校验
        if (percentage !== undefined) {
            if (percentage < 0.1) {
                return {
                    isValid: false,
                    message: `最小百分比为0.1%，当前杠杆${lever}倍最大允许${maxPercentage.toFixed(1)}%`
                };
            }
            if (percentage > maxPercentage) {
                return {
                    isValid: false,
                    message: `当前杠杆${lever}倍最大允许${maxPercentage.toFixed(1)}%，当前设置${percentage.toFixed(2)}%`
                };
            }
        }

        // 固定价格模式校验
        if (price !== undefined) {
            const calculatedPercentage = Math.abs((price - avgPrice) / avgPrice) * 100;

            if (calculatedPercentage < 0.1) {
                return {isValid: false, message: `价格偏离过小，最小需要0.1% (${(avgPrice * 0.001).toFixed(4)})`};
            }

            if (calculatedPercentage > maxPercentage) {
                return {
                    isValid: false,
                    message: `价格偏离过大，当前杠杆${lever}倍最大允许${maxPercentage.toFixed(1)}% (${(avgPrice * maxPercentage / 100).toFixed(4)})`
                };
            }

            // 检查是否接近强平价格
            if (isStopLoss) {
                if (posSide === 'long' && price <= liquidationPrice * 1.01) {
                    return {isValid: false, message: `止损价格过于接近强平价格 ${liquidationPrice.toFixed(4)}`};
                }
                if (posSide === 'short' && price >= liquidationPrice * 0.99) {
                    return {isValid: false, message: `止损价格过于接近强平价格 ${liquidationPrice.toFixed(4)}`};
                }
            }
        }

        return {isValid: true};
    };

    // 渲染仓位项
    const renderPositionItem = useCallback((position: PositionModel) => {
        const posKey = position.posId ?? `${position.instId ?? 'unknown'}-${position.posSide ?? 'unknown'}`;
        const isClosing = closingPositions.has(posKey);

        // 计算保证金维持率颜色
        const marginRatioPercent = Number(position.marginRatioPercent ?? 0) || 0;
        const marginRatioColor = marginRatioPercent <= 1000 ? '#ff4d4f' : '#52c41a';

        // 计算持仓时间
        const calculateHoldingTime = (createTime: number): { time: string, totalMinutes: number } => {
            // 添加调试日志
            if (!createTime || createTime <= 0) {
                console.log('仓位时间异常 - 合约:', position.instId, 'cTime:', createTime, '当前时间:', currentTime);
                return {time: '0s', totalMinutes: 0};
            }

            const created = createTime;

            const now = currentTime;
            const diff = now - created;

            if (diff < 0) {
                console.log('时间差为负 - 合约:', position.instId, '创建时间:', created, '当前时间:', now);
                return {time: '0s', totalMinutes: 0};
            }

            const days = Math.floor(diff / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
            const seconds = Math.floor((diff % (1000 * 60)) / 1000);

            // 构建时间字符串，支持xdxhxmxs格式
            let time = '';
            if (days > 0) {
                time += `${days}d`;
            }
            if (hours > 0) {
                time += `${hours}h`;
            }
            if (minutes > 0) {
                time += `${minutes}m`;
            }
            if (seconds > 0 || (!days && !hours && !minutes)) {
                time += `${seconds}s`;
            }

            const totalMinutes = days * 24 * 60 + hours * 60 + minutes;

            return {time, totalMinutes};
        };

        // 修复：API返回的是"ctime"，但接口定义的是"cTime"
        const cTime = position.cTime || (position as any).ctime || 0;
        const {time: holdingTime, totalMinutes} = calculateHoldingTime(cTime);

        // 时间颜色判断函数
        const getTimeColor = (totalMinutes: number): string => {
            if (totalMinutes <= 10) return '#52c41a';  // 绿色
            if (totalMinutes <= 30) return '#fa8c16';  // 橙色
            return '#ff4d4f';  // 红色
        };

        // 格式化时间，将数字和单位分开并应用不同颜色
        const formatTimeWithColors = (timeString: string): JSX.Element => {
            const timeColor = getTimeColor(totalMinutes);
            const unitColor = '#ffffff'; // 白色

            // 使用正则表达式匹配数字和单位
            const parts = timeString.split(/(\d+)/);

            return (
                <span>
                    {parts.map((part, index) => {
                        // 如果是数字部分，应用条件颜色
                        if (/\d+/.test(part)) {
                            return (
                                <span key={index} style={{color: timeColor}}>
                                    {part}
                                </span>
                            );
                        }
                        // 如果是单位部分(h/m/s)，应用白色
                        return (
                            <span key={index} style={{color: unitColor}}>
                                {part}
                            </span>
                        );
                    })}
                </span>
            );
        };

        return (
            <div key={position.posId} className="order-item" style={{padding: '16px'}}>
                <div className="order-header order-header-with-actions">
                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-start',
                        gap: '4px',
                        flex: 1
                    }}>
                        <div className={`order-instId ${position.posSide}`}
                             style={{fontSize: '16px', fontWeight: 'bold'}}>
                            {position.instId}
                        </div>
                        <span style={{
                            fontSize: '12px',
                            color: '#ffffff', // 默认白色，由formatTimeWithColors处理具体颜色
                            backgroundColor: 'rgba(255, 255, 255, 0.05)',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            whiteSpace: 'nowrap'
                        }}>
                            {formatTimeWithColors(holdingTime)}
                        </span>
                    </div>
                    <div className="order-actions" style={{display: 'flex', gap: '8px'}}>
                        <Tooltip title="查看K线图">
                            <Button
                                type="text"
                                size="small"
                                icon={<LineChartOutlined style={{fontSize: '1.2em', color: '#1890ff'}}/>}
                                style={{color: '#1890ff'}}
                                onClick={() => handleOpenPositionChart(position)}
                            />
                        </Tooltip>
                        <Tooltip title="市价全平">
                            <Button
                                type="text"
                                size="small"
                                icon={<MarketCloseIcon color={
                                    Number(position.upl ?? 0) > 0 ? '#52c41a' :
                                        Number(position.upl ?? 0) < 0 ? '#ff4d4f' : '#ffffff'
                                }/>}
                                style={{
                                    color: Number(position.upl ?? 0) > 0 ? '#52c41a' :
                                        Number(position.upl ?? 0) < 0 ? '#ff4d4f' : '#ffffff'
                                }}
                                onClick={() => handleMarketClosePosition(position)}
                                loading={isClosing}
                            />
                        </Tooltip>
                    </div>
                </div>

                {/* 第一行：价格信息 - 开仓价格、强平价格、标记价格 */}
                <div className="order-details"
                     style={{display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', marginBottom: '8px'}}>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>开仓: </span>
                        <strong style={{fontSize: '13px'}}>{formatPrice(position.avgPx)}</strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>强平: </span>
                        <strong style={{
                            fontSize: '13px',
                            color: '#ff7875'
                        }}>
                            {formatPrice(position.estimatedLiquidationPx)}
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>标记: </span>
                        <strong style={{fontSize: '13px'}}>{formatPrice(position.markPx)}</strong>
                        {Number(position.markPx ?? 0) > Number(position.avgPx ?? 0) ? (
                            <RiseOutlined style={{color: '#52c41a', fontSize: '12px', marginLeft: '4px'}}/>
                        ) : (
                            <FallOutlined style={{color: '#ff4d4f', fontSize: '12px', marginLeft: '4px'}}/>
                        )}
                    </div>
                </div>

                {/* 第二行：持仓信息 - 持仓数量、保证金、杠杆 */}
                <div className="order-details"
                     style={{display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', marginBottom: '8px'}}>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>持仓: </span>
                        <strong style={{fontSize: '13px'}}>
                            {formatPosition(String(position.position ?? 0))}
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>保证金: </span>
                        <strong style={{fontSize: '13px'}}>
                            {Number(position.margin ?? 0) >= 0 ?
                                Math.floor(Number(position.margin ?? 0) * 100) / 100 :
                                Math.ceil(Number(position.margin ?? 0) * 100) / 100
                            }
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>杠杆: </span>
                        <strong style={{fontSize: '13px'}}>{position.lever}x</strong>
                    </div>
                </div>

                {/* 第三行：资金费用和维持率 - 累计资金费、保证金维持率 */}
                <div className="order-details"
                     style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '8px'}}>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>累计资金费: </span>
                        <strong style={{
                            fontSize: '13px',
                            color: Number(position.fundingFee ?? 0) >= 0 ? '#52c41a' : '#ff4d4f'
                        }}>
                            {position.fundingFee ?
                                (Number(position.fundingFee ?? 0) >= 0 ? '+' : '') + Math.floor(Number(position.fundingFee ?? 0) * 100) / 100
                                : '0.00'
                            }
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>维持率: </span>
                        <strong style={{fontSize: '13px', color: marginRatioColor}}>
                            {Number(marginRatioPercent).toFixed(2)}%
                        </strong>
                    </div>
                </div>

                {/* 第四行：盈亏信息 - 未结盈亏、盈亏率 */}
                <div className="order-details"
                     style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '12px'}}>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>未结盈亏: </span>
                        <strong style={{
                            fontSize: '13px',
                            color: Number(position.upl ?? 0) >= 0 ? '#52c41a' : '#ff4d4f'
                        }}>
                            {Number(position.upl ?? 0) >= 0 ? '+' : ''}{Math.floor(Number(position.upl ?? 0) * 100) / 100}
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>盈亏率: </span>
                        <strong style={{
                            fontSize: '13px',
                            color: Number(position.uplRatio ?? 0) >= 0 ? '#52c41a' : '#ff4d4f'
                        }}>
                            {Number(position.uplRatio ?? 0) >= 0 ? '+' : ''}{(Number(position.uplRatio ?? 0) * 100).toFixed(2)}%
                        </strong>
                    </div>
                </div>

                {/* 止盈止损价格显示 - 只显示全仓止盈和全仓止损 */}
                <div className="order-details stop-loss-profit-section" style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr auto',
                    gap: '8px',
                    marginBottom: '8px',
                    padding: '4px',
                    backgroundColor: '#2a2a2a',
                    borderRadius: '4px',
                    alignItems: 'center'
                }}>
                    <div className="order-detail" style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                        <span style={{fontSize: '11px', color: '#999'}}>全仓止盈:</span>
                        <strong style={{
                            fontSize: '12px',
                            color: '#52c41a'
                        }}>
                            {formatStopLossPrice(position.totalTakeProfitPrice)}
                        </strong>
                    </div>
                    <div className="order-detail" style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                        <span style={{fontSize: '11px', color: '#999'}}>全仓止损:</span>
                        <strong style={{
                            fontSize: '12px',
                            color: '#ff4d4f'
                        }}>
                            {formatStopLossPrice(position.totalStopLossPrice)}
                        </strong>
                    </div>
                    <div className="order-detail" style={{
                        textAlign: 'right',
                        display: 'flex',
                        gap: '4px',
                        alignItems: 'center',
                        justifyContent: 'flex-end'
                    }}>
                        <Tooltip title="设置全仓止盈止损">
                            <Button
                                type="text"
                                size="small"
                                icon={<SettingOutlined style={{fontSize: '1.2em'}}/>}
                                onClick={() => handleOpenTotalStopLossModal(position)}
                                style={{
                                    color: '#1890ff',
                                    padding: '2px 4px',
                                    height: '24px',
                                    fontSize: '12px'
                                }}
                            />
                        </Tooltip>
                        {position.closeOrderAlgo && position.closeOrderAlgo.length > 0 ? (
                            <Tooltip title="撤销全仓止盈止损策略">
                                <Button
                                    type="text"
                                    size="small"
                                    icon={<CloseCircleOutlined style={{fontSize: '1.2em'}}/>}
                                    onClick={() => handleCancelStopLossStrategies(position)}
                                    style={{
                                        color: '#ff4d4f',
                                        padding: '2px 4px',
                                        height: '24px',
                                        fontSize: '12px'
                                    }}
                                />
                            </Tooltip>
                        ) : null}
                    </div>
                </div>

                {/* 仓位止盈止损策略显示 */}
                {position.positionStopLossStrategies && position.positionStopLossStrategies.length > 0 && (
                    <div className="dark-strategy-table" style={{
                        backgroundColor: '#2a2a2a',
                        fontSize: '10px',
                        borderRadius: '4px',
                        border: '1px solid #444',
                        overflowX: 'auto',
                        marginBottom: '8px',
                        position: 'relative'
                    }}>
                        <div style={{
                            minWidth: '740px',
                            position: 'relative'
                        }}>
                            {/* 表格头部 */}
                            <div style={{
                                display: 'grid',
                                gridTemplateColumns: '150px 80px 100px 100px 80px 80px 60px',
                                backgroundColor: '#333',
                                borderBottom: '1px solid #444',
                                padding: '4px 8px',
                                fontSize: '10px',
                                color: '#999',
                                fontWeight: 'bold',
                                position: 'sticky',
                                top: 0,
                                zIndex: 10
                            }}>
                                <div>盈亏进度</div>
                                <div>数量</div>
                                <div>止盈价</div>
                                <div>止损价</div>
                                <div>止盈率</div>
                                <div>止损率</div>
                                <div style={{
                                    position: 'sticky',
                                    right: 0,
                                    backgroundColor: '#333',
                                    paddingLeft: '4px',
                                    boxShadow: '-2px 0 4px rgba(0,0,0,0.1)'
                                }}>操作
                                </div>
                            </div>

                            {/* 表格数据行 */}
                            {position.positionStopLossStrategies.map((strategy, index) => (
                                <div key={strategy.algoId} style={{
                                    display: 'grid',
                                    gridTemplateColumns: '150px 80px 100px 100px 80px 80px 60px',
                                    backgroundColor: index % 2 === 0 ? '#2a2a2a' : '#252525',
                                    borderBottom: '1px solid #3a3a3a',
                                    padding: '4px 8px',
                                    fontSize: '10px',
                                    color: '#ccc',
                                    alignItems: 'center',
                                    position: 'relative'
                                }}>
                                    {/* 触发进度 */}
                                    <div style={{
                                        width: '150px',
                                        overflow: 'hidden',
                                        flexShrink: 0,
                                        fontSize: '8px'
                                    }}>
                                        {(() => {
                                            const tpProgress = strategy.tpProgress ? parseFloat(strategy.tpProgress.toString()) : 0;
                                            const slProgress = strategy.slProgress ? parseFloat(strategy.slProgress.toString()) : 0;

                                            // 如果都没有进度或进度为0，显示"-"
                                            if ((!strategy.tpProgress && !strategy.slProgress) || (tpProgress <= 0 && slProgress <= 0)) {
                                                return <span style={{color: '#666', fontSize: '10px'}}>-</span>;
                                            }

                                            return (
                                                <>
                                                    {tpProgress > 0 && (
                                                        <div style={{marginBottom: '3px'}}>
                                                            <div style={{
                                                                color: '#999',
                                                                marginBottom: '1px',
                                                                whiteSpace: 'nowrap'
                                                            }}>
                                                                止盈 {tpProgress.toFixed(2)}%
                                                            </div>
                                                            <div style={{
                                                                width: '120px',
                                                                height: '3px',
                                                                backgroundColor: '#1a1a1a',
                                                                borderRadius: '1.5px',
                                                                overflow: 'hidden'
                                                            }}>
                                                                <div style={{
                                                                    width: `${Math.min(tpProgress, 100)}%`,
                                                                    height: '100%',
                                                                    backgroundColor: '#52c41a',
                                                                    borderRadius: '1.5px',
                                                                    transition: 'width 0.3s ease'
                                                                }}/>
                                                            </div>
                                                        </div>
                                                    )}
                                                    {slProgress > 0 && (
                                                        <div>
                                                            <div style={{
                                                                color: '#999',
                                                                marginBottom: '1px',
                                                                whiteSpace: 'nowrap'
                                                            }}>
                                                                止损 {slProgress.toFixed(2)}%
                                                            </div>
                                                            <div style={{
                                                                width: '120px',
                                                                height: '3px',
                                                                backgroundColor: '#1a1a1a',
                                                                borderRadius: '1.5px',
                                                                overflow: 'hidden'
                                                            }}>
                                                                <div style={{
                                                                    width: `${Math.min(slProgress, 100)}%`,
                                                                    height: '100%',
                                                                    backgroundColor: '#ff4d4f',
                                                                    borderRadius: '1.5px',
                                                                    transition: 'width 0.3s ease'
                                                                }}/>
                                                            </div>
                                                        </div>
                                                    )}
                                                </>
                                            );
                                        })()}
                                    </div>

                                    {/* 数量 */}
                                    <div style={{fontSize: '11px', color: '#fff', fontWeight: 'bold'}}>
                                        {(() => {
                                            const sz = strategy.sz;
                                            if (!sz || (typeof sz === 'string' && (sz === '0' || sz === '0.0' || parseFloat(sz) === 0)) || (typeof sz === 'number' && sz === 0)) {
                                                return '-';
                                            }
                                            return formatPosition(sz.toString());
                                        })()}
                                    </div>

                                    {/* 止盈价格 */}
                                    <div>
                                    <span style={{fontSize: '11px', color: '#52c41a', fontWeight: 'bold'}}>
                                        {formatStrategyPrice(strategy.tpTriggerPx)}
                                    </span>
                                    </div>

                                    {/* 止损价格 */}
                                    <div>
                                    <span style={{fontSize: '11px', color: '#ff4d4f', fontWeight: 'bold'}}>
                                        {formatStrategyPrice(strategy.slTriggerPx)}
                                    </span>
                                    </div>

                                    {/* 止盈比例 */}
                                    <div>
                                        {strategy.tpRatio ? (
                                            <span style={{fontSize: '10px', color: '#52c41a'}}>
                                            {strategy.tpRatio}%
                                        </span>
                                        ) : <span style={{color: '#666'}}>-</span>}
                                    </div>

                                    {/* 止损比例 */}
                                    <div>
                                        {strategy.slRatio ? (
                                            <span style={{fontSize: '10px', color: '#ff4d4f'}}>
                                            {strategy.slRatio}%
                                        </span>
                                        ) : <span style={{color: '#666'}}>-</span>}
                                    </div>

                                    {/* 操作按钮 */}
                                    <div style={{
                                        display: 'flex',
                                        gap: '2px',
                                        justifyContent: 'center',
                                        alignItems: 'center',
                                        position: 'sticky',
                                        right: 0,
                                        backgroundColor: index % 2 === 0 ? '#2a2a2a' : '#252525',
                                        paddingLeft: '4px',
                                        boxShadow: '-2px 0 4px rgba(0,0,0,0.1)',
                                        zIndex: 5
                                    }}>
                                        <Tooltip title="修改策略">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<EditOutlined style={{fontSize: '10px', color: '#1890ff'}}/>}
                                                onClick={() => handleEditStrategy(strategy)}
                                                style={{
                                                    padding: '1px 2px',
                                                    height: '18px',
                                                    minWidth: '18px',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center'
                                                }}
                                            />
                                        </Tooltip>
                                        <Tooltip title="撤销策略">
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<CloseCircleOutlined
                                                    style={{fontSize: '10px', color: '#ff4d4f'}}/>}
                                                onClick={() => handleCancelSingleStrategy(strategy)}
                                                style={{
                                                    padding: '1px 2px',
                                                    height: '18px',
                                                    minWidth: '18px',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center'
                                                }}
                                            />
                                        </Tooltip>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

            </div>
        );
    }, [currentTime, closingPositions]);


    // 渲染当前委托订单项
    const renderPendingItem = useCallback((order: PendingOrder) => {
        // 添加null检查,防止order为null时崩溃
        if (!order) {
            console.warn('检测到空订单对象,跳过渲染');
            return null;
        }

        // 计算挂单持续时间
        const calculateOrderPendingTime = (createTime: number): { time: string, totalMinutes: number } => {
            if (!createTime || createTime <= 0) return {time: '0s', totalMinutes: 0};

            const created = createTime;

            const now = currentTime;
            const diff = now - created;

            if (diff < 0) return {time: '0s', totalMinutes: 0};

            const days = Math.floor(diff / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
            const seconds = Math.floor((diff % (1000 * 60)) / 1000);

            // 构建时间字符串，支持xdxhxmxs格式
            let time = '';
            if (days > 0) {
                time += `${days}d`;
            }
            if (hours > 0) {
                time += `${hours}h`;
            }
            if (minutes > 0) {
                time += `${minutes}m`;
            }
            if (seconds > 0 || (!days && !hours && !minutes)) {
                time += `${seconds}s`;
            }

            const totalMinutes = days * 24 * 60 + hours * 60 + minutes;

            return {time, totalMinutes};
        };

        // 兼容cTime和ctime两种字段名
        const createTime = typeof order.ctime === 'number' ? order.ctime : (order as any).cTime;
        const {time: orderPendingTime, totalMinutes} = calculateOrderPendingTime(createTime);

        // 时间颜色判断函数
        const getTimeColor = (totalMinutes: number): string => {
            if (totalMinutes <= 10) return '#52c41a';  // 绿色
            if (totalMinutes <= 30) return '#fa8c16';  // 橙色
            return '#ff4d4f';  // 红色
        };

        // 格式化时间，将数字和单位分开并应用不同颜色
        const formatTimeWithColors = (timeString: string): JSX.Element => {
            const timeColor = getTimeColor(totalMinutes);
            const unitColor = '#ffffff'; // 白色

            // 使用正则表达式匹配数字和单位
            const parts = timeString.split(/(\d+)/);

            return (
                <span>
                    {parts.map((part, index) => {
                        // 如果是数字部分，应用条件颜色
                        if (/\d+/.test(part)) {
                            return (
                                <span key={index} style={{color: timeColor}}>
                                    {part}
                                </span>
                            );
                        }
                        // 如果是单位部分(h/m/s)，应用白色
                        return (
                            <span key={index} style={{color: unitColor}}>
                                {part}
                            </span>
                        );
                    })}
                </span>
            );
        };

        // 订单状态
        const getStateText = (state: string) => {
            switch (state) {
                case 'live':
                    return '等待成交';
                case 'partially_filled':
                    return '部分成交';
                case 'filled':
                    return '完全成交';
                case 'cancelling':
                    return '撤单中';
                case 'cancelled':
                    return '已撤单';
                default:
                    return state;
            }
        };


        // 根据开多开空规则格式化数值（开多向上进位，开空向下进位）
        const formatValueWithDirection = (value: number) => {
            if (isNaN(value)) return '--';

            const scaledValue = value * 100;
            let roundedValue: number;

            if (order.posSide === 'long') {
                // 开多：向上进位
                roundedValue = Math.ceil(scaledValue);
            } else {
                // 开空：向下进位
                roundedValue = Math.floor(scaledValue);
            }

            return (roundedValue / 100).toFixed(2);
        };

        return (
            <div key={order.ordId} className="order-item" style={{padding: '16px'}}>
                {/* 第一行：合约名称 + 时间 + 操作按钮 */}
                <div className="order-header order-header-with-actions" style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '8px'
                }}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px'
                    }}>
                        <div className="order-instId" style={{
                            fontSize: '16px',
                            fontWeight: 'bold',
                            color: order.posSide === 'long' ? '#52c41a' : '#ff4d4f'
                        }}>
                            {order.instId}
                        </div>
                        <span style={{
                            fontSize: '12px',
                            color: '#ffffff', // 默认白色，由formatTimeWithColors处理具体颜色
                            backgroundColor: 'rgba(255, 255, 255, 0.05)',
                            padding: '2px 6px',
                            borderRadius: '4px',
                            whiteSpace: 'nowrap'
                        }}>
                            {formatTimeWithColors(orderPendingTime)}
                        </span>
                    </div>
                    <div className="order-actions" style={{display: 'flex', gap: '8px'}}>
                        {/* 撤单按钮 */}
                        {(() => {
                            // 判断订单是否可撤销：活跃状态或状态为空时显示按钮
                            const isActive = !order.state ||
                                             order.state === 'live' ||
                                             order.state === 'partially_filled' ||
                                             order.state === 'LIVE' ||
                                             order.state === 'PARTIALLY_FILLED';

                            // 调试日志：帮助诊断按钮显示问题
                            if (!isActive) {
                                console.log('订单状态不满足取消按钮显示条件:', {
                                    orderId: order.ordId,
                                    state: order.state,
                                    stateType: typeof order.state
                                });
                            }

                            return isActive;
                        })() && (
                            <Tooltip title="撤销订单">
                                <Button
                                    type="text"
                                    size="small"
                                    danger
                                    icon={<CloseCircleOutlined style={{fontSize: '1.2em'}}/>}
                                    onClick={() => handleCancelOrder(order.ordId, order.apiKeyId, order.instId)}
                                    style={{color: '#ff4d4f'}}
                                />
                            </Tooltip>
                        )}
                    </div>
                </div>

                {/* 第二行：委托价格、委托数量、占用资金 */}
                <div className="order-details" style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr',
                    gap: '8px',
                    marginBottom: '8px'
                }}>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>委价: </span>
                        <strong style={{fontSize: '13px'}}>
                            ${formatValueWithDirection(parseFloat(order.px))}
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>数量: </span>
                        <strong style={{fontSize: '13px'}}>{parseFloat(order.sz).toLocaleString()}</strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>资金: </span>
                        <strong style={{fontSize: '13px', color: '#52c41a'}}>
                            ${formatValueWithDirection(parseFloat(order.px) * parseFloat(order.sz) / parseFloat(order.lever))}
                        </strong>
                    </div>
                </div>

                {/* 第三行：名义价值、杠杆、状态 */}
                <div className="order-details" style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr',
                    gap: '8px',
                    marginBottom: '8px'
                }}>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>价值: </span>
                        <strong style={{fontSize: '13px', color: '#1890ff'}}>
                            ${formatValueWithDirection(parseFloat(order.px) * parseFloat(order.sz))}
                        </strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>杠杆: </span>
                        <strong style={{fontSize: '13px'}}>{order.lever}x</strong>
                    </div>
                    <div className="order-detail">
                        <span style={{fontSize: '12px', color: '#666'}}>状态: </span>
                        <strong style={{
                            fontSize: '13px',
                            color: order.state === 'live' ? '#1890ff' :
                                order.state === 'partially_filled' ? '#52c41a' :
                                    order.state === 'filled' ? '#52c41a' :
                                        order.state === 'cancelling' ? '#ff4d4f' :
                                            order.state === 'cancelled' ? '#ff4d4f' : '#666'
                        }}>
                            {getStateText(order.state)}
                        </strong>
                    </div>
                </div>

                {/* 第四行：委托ID */}
                <div className="order-details" style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr',
                    gap: '8px',
                    marginBottom: '12px'
                }}>
                    <div className="order-detail">
                        <span style={{
                            fontSize: '12px',
                            color: '#666',
                            minWidth: '60px',
                            display: 'inline-block'
                        }}>委托ID: </span>
                        <strong style={{fontSize: '13px'}}>{order.ordId}</strong>
                    </div>
                </div>


            </div>
        );
    }, [currentTime]);

    // 恢复handleCancelOrder函数
    const handleCancelOrder = async (orderId: string, orderApiKeyId: number, instId: string) => {
        // 使用订单中的 appKeyId，如果不存在则使用组件的 appKeyId
        const finalApiKeyId = orderApiKeyId || apiKeyId;

        // 添加调试日志
        console.log('handleCancelOrder 调试信息:', {
            orderId,
            orderApiKeyId,
            componentApiKeyId: apiKeyId,
            finalApiKeyId,
            instId
        });

        // 参数验证
        if (!finalApiKeyId) {
            message.error('API Key ID 缺失，无法取消订单');
            return;
        }

        Modal.confirm({
            title: (
                <span style={{color: '#ffffff'}}>
                    确认撤单
                </span>
            ),
            content: (
                <div style={{color: '#a0a0a0', fontSize: '14px'}}>
                    确定要撤销订单 <strong>{orderId}</strong> 吗？
                </div>
            ),
            okText: '确认撤单',
            cancelText: '取消',
            okButtonProps: {
                danger: true
            },
            cancelButtonProps: {
                style: {
                    borderColor: '#434343',
                    color: '#a0a0a0'
                }
            },
            centered: true,
            className: 'dark-theme-modal',
            onOk: async () => {
                try {
                    const response = await tradingService.cancelOrder(orderId, finalApiKeyId, instId);

                    if (response.data.success) {
                        message.success('撤单成功');
                        // 刷新当前委托订单列表
                        await fetchPendingOrders();
                        // 刷新其他数据
                        await fetchPositions();
                        if (activeTab === 'history') {
                            await fetchHistoryOrders();
                        }
                    } else {
                        message.error(response.data.message);
                    }
                } catch (error: any) {
                    console.error('撤单失败:', error);
                    message.error(error.response?.data?.message || error.message || '撤单失败');
                }
            }
        });
    };


    // 渲染历史订单项
    const renderHistoryItem = (order: TradingOrder) => {
        // 格式化交易状态
        // 获取来源图标
        const getSourceIcon = (source: string): string => {
            const iconMap: Record<string, string> = {
                'web': '👤',
                'ai': '🤖',
                'bot': '🤖',
                'api': '🔧'
            };
            return iconMap[source] || '';
        };

        // 格式化订单方向和持仓方向
        // 规则：
        // 开多：buy + long -> 买入开多
        // 开空：sell + short -> 卖出开空
        // 平多：sell + long -> 卖出平多
        // 平空：buy + short -> 买入平空
        const formatSideAndPosSide = (order: TradingOrder): { text: string, color: string } => {
            const side = order.side;
            const posSide = order.posSide;

            let text: string;
            let color: string;

            if ('buy' === side && 'long' === posSide) {
                // 开多
                text = '买入开多';
                color = '#52c41a'; // 绿色
            } else if ('sell' === side && 'short' === posSide) {
                // 开空
                text = '卖出开空';
                color = '#ff4d4f'; // 红色
            } else if ('sell' === side && 'long' === posSide) {
                // 平多
                text = '卖出平多';
                color = '#faad14'; // 橙色
            } else if ('buy' === side && 'short' === posSide) {
                // 平空
                text = '买入平空';
                color = '#1890ff'; // 蓝色
            } else {
                // 未知组合
                text = `${side} ${posSide}`;
                color = '#999';
            }

            return {text, color};
        };

        // 格式化订单状态
        const formatOrderStatus = (order: TradingOrder): { text: string, color: string } => {
            if ('success' === order.orderStatus) {
                return {text: '已成交', color: '#52c41a'};
            } else if ('failed' === order.orderStatus || order.errorMsg) {
                return {text: '失败', color: '#ff4d4f'};
            } else if ('canceled' === order.orderStatus) {
                return {text: '已撤销', color: '#888'};
            } else {
                return {text: '处理中', color: '#faad14'};
            }
        };

        // 格式化订单类型
        const formatOrderType = (orderType: string): string => {
            return orderType === 'market' ? '市价单' : '限价单';
        };

        // 格式化数字为最多4位有效小数(去掉末尾的0)
        const formatDecimal = (num: number, maxDecimals: number = 4): string => {
            if (0 === num) return '0';
            return parseFloat(num.toFixed(maxDecimals)).toString();
        };

        // 格式化价格
        const formatDisplayPrice = (order: TradingOrder): string => {
            if (order.orderType === 'market') {
                // 市价单显示成交价格
                return order.cexOrder?.avgPx ? formatDecimal(order.cexOrder.avgPx) : '市价';
            } else {
                // 限价单显示委托价格
                return order.cexOrder?.px ? formatDecimal(order.cexOrder.px) : '-';
            }
        };

        // 格式化数量
        const formatQuantity = (order: TradingOrder): string => {
            // 优先使用主对象的sz字段（系统订单的委托数量）
            // 如果主对象sz为0或不存在，则使用cexOrder.sz（CEX订单的委托数量）
            const sz = order.sz || order.cexOrder?.sz || 0;

            // 直接返回原始数值，不做格式化处理（用户要求显示接口返回的sz原始值）
            return sz.toString();
        };

        // 计算成本
        const calculateCost = (order: TradingOrder): string => {
            if (order.amt && order.amt > 0) {
                return formatDecimal(order.amt);
            }
            return '0';
        };

        // 格式化时间
        const formatTime = (time: string | number | null | undefined) => {
            if (!time) return '-';
            const ts = typeof time === 'number' ? time : new Date(time).getTime();
            const date = new Date(ts);
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hour = String(date.getHours()).padStart(2, '0');
            const minute = String(date.getMinutes()).padStart(2, '0');
            const second = String(date.getSeconds()).padStart(2, '0');
            return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
        };

        const orderStatus = formatOrderStatus(order);

        // 获取状态样式类名
        const getStatusClassName = (color: string): string => {
            switch (color) {
                case '#52c41a':
                    return 'success';
                case '#ff4d4f':
                    return 'error';
                case '#faad14':
                default:
                    return 'processing';
            }
        };

        // 判断买卖方向的函数
        const getSideDisplay = (order: TradingOrder): { text: string, color: string } => {
            // 优先使用posSide，如果没有则使用side
            const side = order.posSide || order.side;
            if (side === 'long' || side === 'buy') {
                return {text: '买多', color: '#52c41a'};
            } else if (side === 'short' || side === 'sell') {
                return {text: '卖空', color: '#ff4d4f'};
            }
            return {text: order.side || '-', color: '#888'};
        };

        const sideDisplay = getSideDisplay(order);

        return (
            <div key={order.id} className="history-order-item" onClick={() => handleOpenOrderDetail(order)}
                 style={{cursor: 'pointer'}}>
                {/* 第一行：instId + 交易状态 + 订单ID */}
                <div className="history-order-header"
                     style={{display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap'}}>
                    <span style={{
                        fontSize: '12px',
                        fontWeight: 'bold',
                        color: formatSideAndPosSide(order).color,
                        minWidth: '60px'
                    }}>
                        {order.instId}
                    </span>
                    {/* 来源图标 */}
                    {getSourceIcon(order.source) && (
                        <span style={{fontSize: '14px', marginRight: '4px'}}>
                            {getSourceIcon(order.source)}
                        </span>
                    )}
                    <Tooltip
                        title={
                            ('failed' === order.orderStatus || order.errorMsg)
                                ? order.errorMsg || '执行失败'
                                : null
                        }
                        placement="top"
                    >
                        <span className={`order-status-tag ${getStatusClassName(orderStatus.color)}`}
                              style={{cursor: ('failed' === order.orderStatus || order.errorMsg) ? 'help' : 'default'}}
                        >
                            {orderStatus.text}
                        </span>
                    </Tooltip>
                    <div style={{display: 'flex', flex: '1', minWidth: '200px'}}>
                        <div style={{flex: '2', textAlign: 'left', paddingRight: '8px'}}>
                            <span className="order-id-text" style={{fontSize: '11px'}}>
                                订单ID: {order.orderUuid || 'N/A'}
                            </span>
                        </div>
                        <div style={{
                            flex: '1',
                            textAlign: 'left',
                            paddingLeft: '8px',
                            borderLeft: '1px solid #333',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'flex-start'
                        }}>
                            <span style={{
                                color: formatSideAndPosSide(order).color,
                                fontWeight: 'bold',
                                fontSize: '12px'
                            }}>
                                {formatSideAndPosSide(order).text}
                            </span>
                        </div>
                    </div>
                </div>

                {/* 第二行：订单类型 + 价格 + 数量 */}
                <div className="history-order-details three-columns">
                    <div>
                        <span className="history-order-detail-label">类型: </span>
                        <span className="history-order-detail-value">{formatOrderType(order.orderType)}</span>
                    </div>
                    <div>
                        <span className="history-order-detail-label">价格: </span>
                        <span className="history-order-detail-value">{formatDisplayPrice(order)}</span>
                    </div>
                    <div>
                        <span className="history-order-detail-label">数量: </span>
                        <span className="history-order-detail-value">{formatQuantity(order)}</span>
                    </div>
                </div>

                {/* 第三行：成本 + 创建时间 */}
                <div className="history-order-details two-columns">
                    <div>
                        <span className="history-order-detail-label">成本: </span>
                        <span className="history-order-detail-value">{calculateCost(order)}</span>
                    </div>
                    <div>
                        <span className="history-order-detail-label">时间: </span>
                        <span className="history-order-detail-value" style={{color: '#999'}}>
                            {formatTime(order.createdTime)}
                        </span>
                    </div>
                </div>
            </div>
        );
    };

    // 止盈设置弹窗组件
    const renderTakeProfitModal = () => {
        if (!selectedPosition) return null;

        // 从实时positions数组中获取最新数据
        const currentPosition = positions.find(p => p.instId === selectedPosition.instId && p.posSide === selectedPosition.posSide);
        const displayPosition = currentPosition || selectedPosition;

        const avgPrice = Number(displayPosition.avgPx || 0);
        const currentPrice = Number(displayPosition.markPx || 0);
        const marginRatioPercent = Number(displayPosition.marginRatioPercent) || 0;

        // 使用仓位的实际盈亏率
        const profitRate = displayPosition.uplRatio ? Number(displayPosition.uplRatio) * 100 : 0;

        // 计算器函数
        const calculatePriceFromProfitRate = (rate: number, isLong: boolean) => {
            if (isLong) {
                return (avgPrice * (1 + rate / 100)).toFixed(6);
            } else {
                return (avgPrice * (1 - rate / 100)).toFixed(6);
            }
        };

        const calculateProfitRateFromPrice = (targetPrice: number, isLong: boolean) => {
            if (avgPrice === 0) return 0;
            if (isLong) {
                return ((targetPrice - avgPrice) / avgPrice * 100);
            } else {
                return ((avgPrice - targetPrice) / avgPrice * 100);
            }
        };

        const isLong = displayPosition.posSide === 'long';

        return (
            <Modal
                title={
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <RiseOutlined style={{color: '#52c41a'}}/>
                        <span style={{color: '#ffffff'}}>止盈设置 - {displayPosition.instId}</span>
                        <span
                            style={{
                                marginLeft: '12px',
                                padding: '2px 8px',
                                borderRadius: '4px',
                                fontSize: '12px',
                                fontWeight: 'bold',
                                backgroundColor: isLong ? '#162312' : '#2a1218',
                                color: isLong ? '#52c41a' : '#ff4d4f'
                            }}
                        >
                            {isLong ? '多头' : '空头'}
                        </span>
                    </div>
                }
                open={showTakeProfitModal}
                onCancel={handleCloseTakeProfitModal}
                footer={[
                    <Button key="cancel" onClick={handleCloseTakeProfitModal}>
                        取消
                    </Button>,
                    <Button
                        key="submit"
                        type="primary"
                        style={{backgroundColor: '#52c41a', borderColor: '#52c41a'}}
                        onClick={handleSaveTakeProfitConfig}
                        loading={takeProfitLoading}
                    >
                        确认设置
                    </Button>
                ]}
                width={600}
                style={{top: 50}}
            >
                <div style={{display: 'grid', gap: '20px'}}>
                    {/* 仓位信息展示区域 */}
                    <div style={{
                        background: '#1f1f1f',
                        border: '1px solid #434343',
                        borderRadius: '8px',
                        padding: '16px'
                    }}>
                        <div style={{display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '16px'}}>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>开仓价格</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#ffffff'}}>
                                    {formatPrice(avgPrice)}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>当前价格</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#1890ff'}}>
                                    {formatPrice(currentPrice)}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>盈亏率</div>
                                <div style={{
                                    fontSize: '14px',
                                    fontWeight: 'bold',
                                    color: profitRate >= 0 ? '#52c41a' : '#ff4d4f'
                                }}>
                                    {profitRate >= 0 ? '+' : ''}{profitRate.toFixed(2)}%
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>杠杆倍数</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#ffffff'}}>
                                    {displayPosition.lever}x
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>保证金维持率
                                </div>
                                <div style={{
                                    fontSize: '14px',
                                    fontWeight: 'bold',
                                    color: marginRatioPercent <= 1000 ? '#ff4d4f' : '#52c41a'
                                }}>
                                    {Number(marginRatioPercent).toFixed(2)}%
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* 止盈设置功能区域 */}
                    <div style={{
                        border: '1px solid #52c41a',
                        borderRadius: '8px',
                        padding: '20px',
                        background: '#1a2318'
                    }}>
                        <div style={{display: 'flex', alignItems: 'center', marginBottom: '16px'}}>
                            <RiseOutlined style={{color: '#52c41a', marginRight: '8px'}}/>
                            <h4 style={{margin: 0, color: '#52c41a'}}>止盈设置</h4>
                        </div>

                        {takeProfitSettingMode === 'profitRate' ? (
                            // 盈亏率设置模式
                            <div style={{display: 'grid', gap: '16px'}}>
                                <div style={{display: 'flex', alignItems: 'stretch', gap: '16px'}}>
                                    <div style={{flex: '0 0 auto', display: 'flex', flexDirection: 'column'}}>
                                        <div style={{
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            marginBottom: '8px',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>设置方式
                                        </div>
                                        <Select
                                            value={takeProfitSettingMode}
                                            onChange={(value) => setTakeProfitSettingMode(value as 'profitRate' | 'price')}
                                            style={{width: '140px', height: '32px'}}
                                            size="small"
                                        >
                                            <Select.Option value="profitRate">按盈亏率设置</Select.Option>
                                            <Select.Option value="price">按价格设置</Select.Option>
                                        </Select>
                                    </div>
                                    <div style={{flex: '1', display: 'flex', flexDirection: 'column'}}>
                                        <label style={{
                                            display: 'block',
                                            marginBottom: '8px',
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>
                                            止盈盈亏率
                                        </label>
                                        <Input
                                            type="number"
                                            placeholder="输入盈亏率 (如: 5.0 表示5%)"
                                            value={(() => {
                                                if (!takeProfitConfig.tpTriggerPx) return '';
                                                const rate = calculateProfitRateFromPrice(parseFloat(takeProfitConfig.tpTriggerPx), isLong);
                                                return rate.toFixed(2);
                                            })()}
                                            onChange={(e) => {
                                                const rate = parseFloat(e.target.value);
                                                if (!isNaN(rate)) {
                                                    const newPrice = calculatePriceFromProfitRate(rate, isLong);
                                                    setTakeProfitConfig(prev => ({...prev, tpTriggerPx: newPrice}));
                                                } else {
                                                    setTakeProfitConfig(prev => ({...prev, tpTriggerPx: undefined}));
                                                }
                                            }}
                                            suffix="%"
                                            style={{fontSize: '16px', height: '32px'}}
                                        />
                                    </div>
                                </div>
                                <div>
                                    <label style={{
                                        display: 'block',
                                        marginBottom: '8px',
                                        fontSize: '14px',
                                        fontWeight: 'bold'
                                    }}>
                                        计算得出的止盈价格
                                    </label>
                                    <Input
                                        value={takeProfitConfig.tpTriggerPx || ''}
                                        readOnly
                                        style={{
                                            backgroundColor: '#2a2a2a',
                                            color: '#52c41a',
                                            fontWeight: 'bold',
                                            fontSize: '16px',
                                            border: '1px solid #434343'
                                        }}
                                        prefix="$"
                                    />
                                </div>
                            </div>
                        ) : (
                            // 价格设置模式
                            <div style={{display: 'grid', gap: '16px'}}>
                                <div style={{display: 'flex', alignItems: 'stretch', gap: '16px'}}>
                                    <div style={{flex: '0 0 auto', display: 'flex', flexDirection: 'column'}}>
                                        <div style={{
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            marginBottom: '8px',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>设置方式
                                        </div>
                                        <Select
                                            value={takeProfitSettingMode}
                                            onChange={(value) => setTakeProfitSettingMode(value as 'profitRate' | 'price')}
                                            style={{width: '140px', height: '32px'}}
                                            size="small"
                                        >
                                            <Select.Option value="profitRate">按盈亏率设置</Select.Option>
                                            <Select.Option value="price">按价格设置</Select.Option>
                                        </Select>
                                    </div>
                                    <div style={{flex: '1', display: 'flex', flexDirection: 'column'}}>
                                        <label style={{
                                            display: 'block',
                                            marginBottom: '8px',
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>
                                            止盈价格
                                        </label>
                                        <Input
                                            type="number"
                                            placeholder="输入止盈价格"
                                            value={takeProfitConfig.tpTriggerPx || ''}
                                            onChange={(e) => setTakeProfitConfig(prev => ({
                                                ...prev,
                                                tpTriggerPx: e.target.value
                                            }))}
                                            prefix="$"
                                            style={{fontSize: '16px', height: '32px'}}
                                        />
                                    </div>
                                </div>
                                <div>
                                    <label style={{
                                        display: 'block',
                                        marginBottom: '8px',
                                        fontSize: '14px',
                                        fontWeight: 'bold'
                                    }}>
                                        对应盈亏率
                                    </label>
                                    <Input
                                        value={(() => {
                                            if (!takeProfitConfig.tpTriggerPx) return '';
                                            const rate = calculateProfitRateFromPrice(parseFloat(takeProfitConfig.tpTriggerPx), isLong);
                                            return `${rate.toFixed(2)}%`;
                                        })()}
                                        readOnly
                                        style={{
                                            backgroundColor: '#2a2a2a',
                                            color: '#52c41a',
                                            fontWeight: 'bold',
                                            fontSize: '16px',
                                            border: '1px solid #434343'
                                        }}
                                    />
                                </div>
                            </div>
                        )}

                    </div>
                </div>
            </Modal>
        );
    };

    // 止损设置弹窗组件
    const renderStopLossModal = () => {
        if (!selectedPosition) return null;

        // 从实时positions数组中获取最新数据
        const currentPosition = positions.find(p => p.instId === selectedPosition.instId && p.posSide === selectedPosition.posSide);
        const displayPosition = currentPosition || selectedPosition;

        const avgPrice = Number(displayPosition.avgPx) || 0;
        const currentPrice = Number(displayPosition.markPx) || 0;
        const marginRatioPercent = Number(displayPosition.marginRatioPercent) || 0;

        // 使用仓位的实际盈亏率
        const profitRate = displayPosition.uplRatio ? Number(displayPosition.uplRatio) * 100 : 0;

        // 计算器函数
        const calculatePriceFromLossRate = (lossRate: number, isLong: boolean) => {
            if (isLong) {
                return (avgPrice * (1 - lossRate / 100)).toFixed(6);
            } else {
                return (avgPrice * (1 + lossRate / 100)).toFixed(6);
            }
        };

        const calculateLossRateFromPrice = (targetPrice: number, isLong: boolean) => {
            if (avgPrice === 0) return 0;
            if (isLong) {
                return ((avgPrice - targetPrice) / avgPrice * 100);
            } else {
                return ((targetPrice - avgPrice) / avgPrice * 100);
            }
        };

        const isLong = displayPosition.posSide === 'long';

        return (
            <Modal
                title={
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <FallOutlined style={{color: '#ff4d4f'}}/>
                        <span style={{color: '#ffffff'}}>止损设置 - {displayPosition.instId}</span>
                        <span
                            style={{
                                marginLeft: '12px',
                                padding: '2px 8px',
                                borderRadius: '4px',
                                fontSize: '12px',
                                fontWeight: 'bold',
                                backgroundColor: isLong ? '#162312' : '#2a1218',
                                color: isLong ? '#52c41a' : '#ff4d4f'
                            }}
                        >
                            {isLong ? '多头' : '空头'}
                        </span>
                    </div>
                }
                open={showStopLossModal}
                onCancel={handleCloseStopLossModal}
                footer={[
                    <Button key="cancel" onClick={handleCloseStopLossModal}>
                        取消
                    </Button>,
                    <Button
                        key="submit"
                        type="primary"
                        style={{backgroundColor: '#ff4d4f', borderColor: '#ff4d4f'}}
                        onClick={handleSaveStopLossConfig}
                        loading={stopLossLoading}
                    >
                        确认设置
                    </Button>
                ]}
                width={600}
                style={{top: 50}}
            >
                <div style={{display: 'grid', gap: '20px'}}>
                    {/* 仓位信息展示区域 */}
                    <div style={{
                        background: '#1f1f1f',
                        border: '1px solid #434343',
                        borderRadius: '8px',
                        padding: '16px'
                    }}>
                        <div style={{display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '16px'}}>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>开仓价格</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#ffffff'}}>
                                    {formatPrice(avgPrice)}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>当前价格</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#1890ff'}}>
                                    {formatPrice(currentPrice)}
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>盈亏率</div>
                                <div style={{
                                    fontSize: '14px',
                                    fontWeight: 'bold',
                                    color: profitRate >= 0 ? '#52c41a' : '#ff4d4f'
                                }}>
                                    {profitRate >= 0 ? '+' : ''}{profitRate.toFixed(2)}%
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>杠杆倍数</div>
                                <div style={{fontSize: '14px', fontWeight: 'bold', color: '#ffffff'}}>
                                    {displayPosition.lever}x
                                </div>
                            </div>
                            <div>
                                <div style={{fontSize: '12px', color: '#a0a0a0', marginBottom: '4px'}}>保证金维持率
                                </div>
                                <div style={{
                                    fontSize: '14px',
                                    fontWeight: 'bold',
                                    color: marginRatioPercent <= 1000 ? '#ff4d4f' : '#52c41a'
                                }}>
                                    {Number(marginRatioPercent).toFixed(2)}%
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* 止损设置功能区域 */}
                    <div style={{
                        border: '1px solid #ff4d4f',
                        borderRadius: '8px',
                        padding: '20px',
                        background: '#2a1a1a'
                    }}>
                        <div style={{display: 'flex', alignItems: 'center', marginBottom: '16px'}}>
                            <FallOutlined style={{color: '#ff4d4f', marginRight: '8px'}}/>
                            <h4 style={{margin: 0, color: '#ff4d4f'}}>止损设置</h4>
                        </div>

                        {stopLossSettingMode === 'lossRate' ? (
                            // 亏损率设置模式
                            <div style={{display: 'grid', gap: '16px'}}>
                                <div style={{display: 'flex', alignItems: 'stretch', gap: '16px'}}>
                                    <div style={{flex: '0 0 auto', display: 'flex', flexDirection: 'column'}}>
                                        <div style={{
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            marginBottom: '8px',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>设置方式
                                        </div>
                                        <Select
                                            value={stopLossSettingMode}
                                            onChange={(value) => setStopLossSettingMode(value as 'lossRate' | 'price')}
                                            style={{width: '140px', height: '32px'}}
                                            size="small"
                                        >
                                            <Select.Option value="lossRate">按亏损率设置</Select.Option>
                                            <Select.Option value="price">按价格设置</Select.Option>
                                        </Select>
                                    </div>
                                    <div style={{flex: '1', display: 'flex', flexDirection: 'column'}}>
                                        <label style={{
                                            display: 'block',
                                            marginBottom: '8px',
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>
                                            止损亏损率
                                        </label>
                                        <Input
                                            type="number"
                                            placeholder="输入亏损率 (如: 5.0 表示5%)"
                                            step="0.1"
                                            min="0"
                                            max="100"
                                            value={(() => {
                                                if (!stopLossConfig.slTriggerPx) return '00.00';
                                                const rate = calculateLossRateFromPrice(parseFloat(stopLossConfig.slTriggerPx), isLong);
                                                return rate.toFixed(2);
                                            })()}
                                            onChange={(e) => {
                                                const rate = parseFloat(e.target.value);
                                                if (!isNaN(rate)) {
                                                    const newPrice = calculatePriceFromLossRate(rate, isLong);
                                                    setStopLossConfig(prev => ({...prev, slTriggerPx: newPrice}));
                                                } else {
                                                    setStopLossConfig(prev => ({...prev, slTriggerPx: undefined}));
                                                }
                                            }}
                                            suffix="%"
                                            style={{fontSize: '16px', height: '32px'}}
                                        />
                                    </div>
                                </div>
                                <div>
                                    <label style={{
                                        display: 'block',
                                        marginBottom: '8px',
                                        fontSize: '14px',
                                        fontWeight: 'bold'
                                    }}>
                                        计算得出的止损价格
                                    </label>
                                    <Input
                                        value={stopLossConfig.slTriggerPx || ''}
                                        readOnly
                                        style={{
                                            backgroundColor: '#2a2a2a',
                                            color: '#ff4d4f',
                                            fontWeight: 'bold',
                                            fontSize: '16px',
                                            border: '1px solid #434343'
                                        }}
                                        prefix="$"
                                    />
                                </div>
                            </div>
                        ) : (
                            // 价格设置模式
                            <div style={{display: 'grid', gap: '16px'}}>
                                <div style={{display: 'flex', alignItems: 'stretch', gap: '16px'}}>
                                    <div style={{flex: '0 0 auto', display: 'flex', flexDirection: 'column'}}>
                                        <div style={{
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            marginBottom: '8px',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>设置方式
                                        </div>
                                        <Select
                                            value={stopLossSettingMode}
                                            onChange={(value) => setStopLossSettingMode(value as 'lossRate' | 'price')}
                                            style={{width: '140px', height: '32px'}}
                                            size="small"
                                        >
                                            <Select.Option value="lossRate">按亏损率设置</Select.Option>
                                            <Select.Option value="price">按价格设置</Select.Option>
                                        </Select>
                                    </div>
                                    <div style={{flex: '1', display: 'flex', flexDirection: 'column'}}>
                                        <label style={{
                                            display: 'block',
                                            marginBottom: '8px',
                                            fontSize: '14px',
                                            fontWeight: 'bold',
                                            color: '#ffffff',
                                            height: '22px'
                                        }}>
                                            止损价格
                                        </label>
                                        <Input
                                            type="number"
                                            placeholder="输入止损价格"
                                            value={stopLossConfig.slTriggerPx || ''}
                                            onChange={(e) => setStopLossConfig(prev => ({
                                                ...prev,
                                                slTriggerPx: e.target.value
                                            }))}
                                            prefix="$"
                                            style={{fontSize: '16px', height: '32px'}}
                                        />
                                    </div>
                                </div>
                                <div>
                                    <label style={{
                                        display: 'block',
                                        marginBottom: '8px',
                                        fontSize: '14px',
                                        fontWeight: 'bold'
                                    }}>
                                        对应亏损率
                                    </label>
                                    <Input
                                        value={(() => {
                                            if (!stopLossConfig.slTriggerPx) return '';
                                            const rate = calculateLossRateFromPrice(parseFloat(stopLossConfig.slTriggerPx), isLong);
                                            return `${rate.toFixed(2)}%`;
                                        })()}
                                        readOnly
                                        style={{
                                            backgroundColor: '#2a2a2a',
                                            color: '#ff4d4f',
                                            fontWeight: 'bold',
                                            fontSize: '16px',
                                            border: '1px solid #434343'
                                        }}
                                    />
                                </div>
                            </div>
                        )}

                    </div>
                </div>
            </Modal>
        );
    };

    // 全仓止盈止损设置Modal
    const renderTotalStopLossModal = () => {
        if (!selectedPosition) return null;

        // 从最新的positions数据中找到对应的实时仓位数据
        const currentPosition = positions.find(p =>
            p.instId === selectedPosition.instId &&
            p.posSide === selectedPosition.posSide
        ) || selectedPosition;

        const isLong = currentPosition.posSide === 'long';
        const avgPrice = parseFloat(String(currentPosition.avgPx || '0'));
        const markPrice = parseFloat(String(currentPosition.markPx || '0'));
        const lever = parseFloat(String(currentPosition.lever || '3')); // 获取杠杆倍数
        const maxPercentage = getMaxPercentageByLeverage(lever); // 计算最大百分比限制

        // 确保价格数据有效，否则显示错误状态
        if (isNaN(avgPrice) || avgPrice <= 0) {
            console.error('开仓价格数据无效:', currentPosition.avgPx);
            return null;
        }

        // 处理止盈模式切换
        const handleTakeProfitModeChange = (mode: 'percentage' | 'fixed') => {
            setTotalStopLossConfig(prev => {
                const newConfig = {...prev, takeProfitMode: mode};

                if (mode === 'percentage' && prev.takeProfitPrice) {
                    // 切换到百分比模式，计算百分比
                    newConfig.takeProfitPercentage = ((prev.takeProfitPrice - avgPrice) / avgPrice) * 100;
                    newConfig.takeProfitPrice = undefined;
                } else if (mode === 'fixed' && prev.takeProfitPercentage) {
                    // 切换到固定价格模式，计算价格
                    newConfig.takeProfitPrice = isLong
                        ? avgPrice * (1 + prev.takeProfitPercentage / 100)
                        : avgPrice * (1 - prev.takeProfitPercentage / 100);
                    newConfig.takeProfitPercentage = undefined;
                }

                return newConfig;
            });
        };

        // 处理止损模式切换
        const handleStopLossModeChange = (mode: 'percentage' | 'fixed') => {
            setTotalStopLossConfig(prev => {
                const newConfig = {...prev, stopLossMode: mode};

                if (mode === 'percentage' && prev.stopLossPrice) {
                    // 切换到百分比模式，计算百分比
                    newConfig.stopLossPercentage = Math.abs((prev.stopLossPrice - avgPrice) / avgPrice) * 100;
                    newConfig.stopLossPrice = undefined;
                } else if (mode === 'fixed' && prev.stopLossPercentage) {
                    // 切换到固定价格模式，计算价格
                    newConfig.stopLossPrice = isLong
                        ? avgPrice * (1 - prev.stopLossPercentage / 100)
                        : avgPrice * (1 + prev.stopLossPercentage / 100);
                    newConfig.stopLossPercentage = undefined;
                }

                return newConfig;
            });
        };

        // 保存全仓止盈止损设置
        const handleSaveTotalStopLoss = async () => {
            if (!selectedPosition || !apiKeyId) return;

            setTotalStopLossLoading(true);
            try {
                const existingStrategy = editingStrategy;

                // 数据验证
                if (!totalStopLossConfig.takeProfitEnabled && !totalStopLossConfig.stopLossEnabled) {
                    message.warning('请至少启用止盈或止损中的一项');
                    return;
                }

                if (totalStopLossConfig.takeProfitEnabled && !totalStopLossConfig.takeProfitPrice && !totalStopLossConfig.takeProfitPercentage) {
                    message.warning('请设置止盈价格或止盈百分比');
                    return;
                }

                if (totalStopLossConfig.stopLossEnabled && !totalStopLossConfig.stopLossPrice && !totalStopLossConfig.stopLossPercentage) {
                    message.warning('请设置止损价格或止损百分比');
                    return;
                }

                // 在提交前进行验证
                if (totalStopLossConfig.takeProfitEnabled) {
                    const takeProfitValidation = validateStopLossSettings(
                        totalStopLossConfig.takeProfitPrice,
                        totalStopLossConfig.takeProfitPercentage,
                        lever,
                        avgPrice,
                        isLong ? 'long' : 'short',
                        false
                    );
                    if (!takeProfitValidation.isValid) {
                        message.error(takeProfitValidation.message);
                        return;
                    }
                }

                if (totalStopLossConfig.stopLossEnabled) {
                    const stopLossValidation = validateStopLossSettings(
                        totalStopLossConfig.stopLossPrice,
                        totalStopLossConfig.stopLossPercentage,
                        lever,
                        avgPrice,
                        isLong ? 'long' : 'short',
                        true
                    );
                    if (!stopLossValidation.isValid) {
                        message.error(stopLossValidation.message);
                        return;
                    }
                }

                const config: any = {
                    apiKeyId,
                    instId: currentPosition.instId,
                    posSide: currentPosition.posSide as 'long' | 'short',
                    side: (currentPosition.posSide === 'long' ? 'sell' : 'buy') as 'buy' | 'sell', // 平仓方向与持仓方向相反
                    sz: currentPosition.position,
                    tpTriggerPxMode: '2', // 全仓模式
                    slTriggerPxMode: '2', // 全仓模式
                    tpTriggerPxType: (totalStopLossConfig.takeProfitMode === 'percentage' ? '2' : '1') as '1' | '2',
                    slTriggerPxType: (totalStopLossConfig.stopLossMode === 'percentage' ? '2' : '1') as '1' | '2',
                    tpTriggerPx: totalStopLossConfig.takeProfitEnabled
                        ? (totalStopLossConfig.takeProfitMode === 'percentage'
                            ? (() => {
                                const percentage = totalStopLossConfig.takeProfitPercentage || 0;
                                if (percentage <= 0) return "0"; // 百分比小于等于0时不设置止盈，返回0
                                const calculatedPrice = isLong
                                    ? avgPrice * (1 + Math.min(percentage, maxPercentage) / 100)
                                    : avgPrice * (1 - Math.min(percentage, maxPercentage) / 100);
                                return calculatedPrice > 0 ? calculatedPrice.toFixed(4) : "0";
                            })()
                            : totalStopLossConfig.takeProfitPrice?.toFixed(4) || "0")
                        : "0",
                    slTriggerPx: totalStopLossConfig.stopLossEnabled
                        ? (totalStopLossConfig.stopLossMode === 'percentage'
                            ? (() => {
                                const percentage = totalStopLossConfig.stopLossPercentage || 0;
                                if (percentage <= 0) return "0"; // 百分比小于等于0时不设置止损，返回0
                                const calculatedPrice = isLong
                                    ? avgPrice * (1 - Math.min(percentage, maxPercentage) / 100)
                                    : avgPrice * (1 + Math.min(percentage, maxPercentage) / 100);
                                return calculatedPrice > 0 ? calculatedPrice.toFixed(4) : "0";
                            })()
                            : totalStopLossConfig.stopLossPrice?.toFixed(4) || "0")
                        : "0",
                };

                // 如果当前策略存在algoId,添加到config中
                if (existingStrategy && existingStrategy.algoId && existingStrategy.algoId !== 'EXISTING_POSITION_STRATEGY') {
                    config.algoId = existingStrategy.algoId;
                }

                // 添加mgnMode参数
                config.mgnMode = currentPosition.mgnMode;

                let response;
                // 判断是否为真实存在的策略
                if (existingStrategy && existingStrategy.algoId !== 'EXISTING_POSITION_STRATEGY') {
                    // 所有真实策略都使用amend修改
                    response = await tradingService.amendTotalStopLoss({
                        apiKeyId,
                        algoId: existingStrategy.algoId,
                        instId: currentPosition.instId || '',
                        tpTriggerPx: config.tpTriggerPx,
                        slTriggerPx: config.slTriggerPx,
                        tpTriggerPxType: config.tpTriggerPxType,
                        slTriggerPxType: config.slTriggerPxType,
                        mgnMode: currentPosition.mgnMode || '',
                    });
                } else {
                    // 创建新策略(包括虚拟策略对象的情况)
                    response = await tradingService.setTotalStopLoss(config);
                }

                if (response.data.success) {
                    let action = '创建';
                    if (existingStrategy && existingStrategy.algoId !== 'EXISTING_POSITION_STRATEGY') {
                        action = '更新'; // 真实策略都是更新操作
                    }
                    message.success(`全仓止盈止损策略${action}成功`);
                    handleCloseTotalStopLossModal();
                    // 延迟150ms刷新数据,等待服务器处理完成
                    setTimeout(async () => {
                        await fetchPositions();
                        onOrderUpdate();
                    }, 150);
                } else {
                    const errorMsg = response.data.message || '设置失败';
                    message.error(errorMsg);
                    console.error('设置全仓止盈止损失败:', response.data);
                }
            } catch (error) {
                console.error('设置全仓止盈止损失败:', error);
                const errorMessage = error instanceof Error ? error.message : '网络错误，请检查连接后重试';
                message.error(`设置失败: ${errorMessage}`);
            } finally {
                setTotalStopLossLoading(false);
            }
        };

        return (
            <Modal
                title={
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <SettingOutlined/>
                        <span style={{
                            color: isLong ? '#52c41a' : '#ff4d4f',
                            fontSize: '14px',
                            fontWeight: 'bold'
                        }}>
                            {currentPosition.instId}
                        </span>
                        {editingStrategy && (
                            <Tag color="default" style={{
                                fontSize: '12px',
                                backgroundColor: '#262626',
                                color: '#595959',
                                borderColor: '#262626'
                            }}>
                                {editingStrategy.algoId}
                            </Tag>
                        )}
                    </div>
                }
                open={showTotalStopLossModal}
                onCancel={handleCloseTotalStopLossModal}
                width={600}
                centered
                className="dark-theme-modal"
                footer={[
                    <Button key="cancel" onClick={handleCloseTotalStopLossModal}>
                        取消
                    </Button>,
                    <Button
                        key="save"
                        type="primary"
                        loading={totalStopLossLoading}
                        onClick={handleSaveTotalStopLoss}
                        style={{
                            backgroundColor: isLong ? '#52c41a' : '#ff4d4f',
                            borderColor: isLong ? '#52c41a' : '#ff4d4f'
                        }}
                    >
                        保存设置
                    </Button>
                ]}
            >
                <div style={{color: '#a0a0a0'}}>
                    {/* 仓位信息展示 */}
                    <div style={{
                        backgroundColor: '#2a2a2a',
                        padding: '12px',
                        borderRadius: '8px',
                        marginBottom: '12px',
                        border: '1px solid #404040'
                    }}>
                        <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px'}}>
                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                <Text style={{color: '#666', fontSize: '12px'}}>开仓均价:</Text>
                                <div style={{color: '#fff', fontSize: '14px', fontWeight: 'bold'}}>
                                    {formatPrice(avgPrice)}
                                </div>
                            </div>
                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                <Text style={{color: '#666', fontSize: '12px'}}>标记价格:</Text>
                                <div style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                                    <div style={{color: '#fff', fontSize: '14px', fontWeight: 'bold'}}>
                                        {formatPrice(markPrice)}
                                    </div>
                                    {markPrice > avgPrice ? (
                                        <RiseOutlined style={{color: '#52c41a', fontSize: '12px'}}/>
                                    ) : (
                                        <FallOutlined style={{color: '#ff4d4f', fontSize: '12px'}}/>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* 止盈设置 */}
                    <div style={{marginBottom: '24px'}}>
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            marginBottom: '12px',
                            gap: '8px'
                        }}>
                            <Checkbox
                                checked={totalStopLossConfig.takeProfitEnabled}
                                onChange={(e) => setTotalStopLossConfig(prev => ({
                                    ...prev,
                                    takeProfitEnabled: e.target.checked
                                }))}
                            >
                                <Text style={{color: '#fff', fontWeight: 'bold'}}>止盈</Text>
                            </Checkbox>
                            <Tooltip title="当价格上涨到指定价格或百分比时自动卖出获利">
                                <InfoCircleOutlined style={{color: '#888'}}/>
                            </Tooltip>
                        </div>

                        {totalStopLossConfig.takeProfitEnabled && (
                            <div style={{marginLeft: '24px'}}>
                                <div style={{marginBottom: '12px'}}>
                                    <Row gutter={16}>
                                        <Col span={12}>
                                            <Checkbox
                                                checked={totalStopLossConfig.takeProfitMode === 'percentage'}
                                                onChange={() => handleTakeProfitModeChange('percentage')}
                                            >
                                                按百分比
                                            </Checkbox>
                                        </Col>
                                        <Col span={12}>
                                            <Checkbox
                                                checked={totalStopLossConfig.takeProfitMode === 'fixed'}
                                                onChange={() => handleTakeProfitModeChange('fixed')}
                                            >
                                                按固定价格
                                            </Checkbox>
                                        </Col>
                                    </Row>
                                </div>

                                <div>
                                    {totalStopLossConfig.takeProfitMode === 'percentage' ? (
                                        <>
                                            <InputNumber
                                                value={totalStopLossConfig.takeProfitPercentage}
                                                onChange={(value) => {
                                                    setTotalStopLossConfig(prev => ({
                                                        ...prev,
                                                        takeProfitPercentage: value !== null ? value : undefined
                                                    }));
                                                }}
                                                placeholder="止盈百分比"
                                                style={{width: '100%'}}
                                                precision={2}
                                                min={0.1}
                                                max={maxPercentage}
                                                step={0.1}
                                                controls={false}
                                                formatter={value => value ? `${formatPercentage(value)}%` : ''}
                                                parser={value => parseFloat(value!.replace('%', '') || '0')}
                                            />
                                            <div style={{marginTop: '8px', fontSize: '12px', color: '#888'}}>
                                                最小0.1%，最大{maxPercentage.toFixed(1)}% (当前杠杆{lever}倍)
                                            </div>
                                        </>
                                    ) : (
                                        <>
                                            <InputNumber
                                                value={totalStopLossConfig.takeProfitPrice}
                                                onChange={(value) => setTotalStopLossConfig(prev => ({
                                                    ...prev,
                                                    takeProfitPrice: value || undefined
                                                }))}
                                                placeholder="止盈价格"
                                                style={{width: '100%'}}
                                                precision={4}
                                                min={0}
                                                step={0.0001}
                                                formatter={value => `$${value}`}
                                                parser={value => parseFloat(value!.replace('$', '') || '0')}
                                            />
                                            <div style={{marginTop: '8px', fontSize: '12px', color: '#888'}}>
                                                {totalStopLossConfig.takeProfitPrice && (() => {
                                                    const validation = validateStopLossSettings(
                                                        totalStopLossConfig.takeProfitPrice,
                                                        undefined,
                                                        lever,
                                                        avgPrice,
                                                        isLong ? 'long' : 'short',
                                                        false
                                                    );
                                                    return validation.isValid ?
                                                        `偏离开仓价 ${Math.abs((totalStopLossConfig.takeProfitPrice! - avgPrice) / avgPrice * 100).toFixed(2)}%` :
                                                        validation.message;
                                                })()}
                                            </div>
                                        </>
                                    )}
                                </div>

                                {/* 显示对应价格或百分比 */}
                                <div style={{marginTop: '8px', fontSize: '12px', color: '#666'}}>
                                    {totalStopLossConfig.takeProfitMode === 'percentage' && totalStopLossConfig.takeProfitPercentage && totalStopLossConfig.takeProfitPercentage > 0 ? (
                                        <span>对应价格: ${formatCalculatedPrice(
                                            isLong
                                                ? avgPrice * (1 + Math.min(totalStopLossConfig.takeProfitPercentage || 0, maxPercentage) / 100)
                                                : avgPrice * (1 - Math.min(totalStopLossConfig.takeProfitPercentage || 0, maxPercentage) / 100)
                                        )}</span>
                                    ) : totalStopLossConfig.takeProfitMode === 'fixed' && totalStopLossConfig.takeProfitPrice ? (
                                        <span>对应百分比: {(
                                            isLong
                                                ? ((totalStopLossConfig.takeProfitPrice - avgPrice) / avgPrice * 100)
                                                : ((avgPrice - totalStopLossConfig.takeProfitPrice) / avgPrice * 100)
                                        ).toFixed(2)}%</span>
                                    ) : null}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* 分割线 */}
                    <div style={{
                        height: '1px',
                        backgroundColor: '#404040',
                        margin: '16px 0'
                    }}/>

                    {/* 止损设置 */}
                    <div>
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            marginBottom: '12px',
                            gap: '8px'
                        }}>
                            <Checkbox
                                checked={totalStopLossConfig.stopLossEnabled}
                                onChange={(e) => setTotalStopLossConfig(prev => ({
                                    ...prev,
                                    stopLossEnabled: e.target.checked
                                }))}
                            >
                                <Text style={{color: '#fff', fontWeight: 'bold'}}>止损</Text>
                            </Checkbox>
                            <Tooltip title="当价格下跌到指定价格或百分比时自动卖出限制亏损">
                                <InfoCircleOutlined style={{color: '#888'}}/>
                            </Tooltip>
                        </div>

                        {totalStopLossConfig.stopLossEnabled && (
                            <div style={{marginLeft: '24px'}}>
                                <div style={{marginBottom: '12px'}}>
                                    <Row gutter={16}>
                                        <Col span={12}>
                                            <Checkbox
                                                checked={totalStopLossConfig.stopLossMode === 'percentage'}
                                                onChange={() => handleStopLossModeChange('percentage')}
                                            >
                                                按百分比
                                            </Checkbox>
                                        </Col>
                                        <Col span={12}>
                                            <Checkbox
                                                checked={totalStopLossConfig.stopLossMode === 'fixed'}
                                                onChange={() => handleStopLossModeChange('fixed')}
                                            >
                                                按固定价格
                                            </Checkbox>
                                        </Col>
                                    </Row>
                                </div>

                                <div>
                                    {totalStopLossConfig.stopLossMode === 'percentage' ? (
                                        <>
                                            <InputNumber
                                                value={totalStopLossConfig.stopLossPercentage}
                                                onChange={(value) => {
                                                    setTotalStopLossConfig(prev => ({
                                                        ...prev,
                                                        stopLossPercentage: value !== null ? value : undefined
                                                    }));
                                                }}
                                                placeholder="止损百分比"
                                                style={{width: '100%'}}
                                                precision={2}
                                                min={0.1}
                                                max={maxPercentage}
                                                step={0.1}
                                                controls={false}
                                                formatter={value => value ? `${formatPercentage(value)}%` : ''}
                                                parser={value => parseFloat(value!.replace('%', '') || '0')}
                                            />
                                            <div style={{marginTop: '8px', fontSize: '12px', color: '#888'}}>
                                                最小0.1%，最大{maxPercentage.toFixed(1)}% (当前杠杆{lever}倍)
                                            </div>
                                        </>
                                    ) : (
                                        <>
                                            <InputNumber
                                                value={totalStopLossConfig.stopLossPrice}
                                                onChange={(value) => setTotalStopLossConfig(prev => ({
                                                    ...prev,
                                                    stopLossPrice: value || undefined
                                                }))}
                                                placeholder="止损价格"
                                                style={{width: '100%'}}
                                                precision={4}
                                                min={0}
                                                step={0.0001}
                                                formatter={value => `$${value}`}
                                                parser={value => parseFloat(value!.replace('$', '') || '0')}
                                            />
                                            <div style={{marginTop: '8px', fontSize: '12px', color: '#888'}}>
                                                {totalStopLossConfig.stopLossPrice && (() => {
                                                    const validation = validateStopLossSettings(
                                                        totalStopLossConfig.stopLossPrice,
                                                        undefined,
                                                        lever,
                                                        avgPrice,
                                                        isLong ? 'long' : 'short',
                                                        true // 这是止损设置
                                                    );
                                                    return validation.isValid ?
                                                        `偏离开仓价 ${Math.abs((totalStopLossConfig.stopLossPrice! - avgPrice) / avgPrice * 100).toFixed(2)}%` :
                                                        validation.message;
                                                })()}
                                            </div>
                                            <div style={{marginTop: '4px', fontSize: '11px', color: '#666'}}>
                                                强平价格:
                                                ${calculateLiquidationPrice(avgPrice, lever, isLong ? 'long' : 'short').toFixed(4)}
                                            </div>
                                        </>
                                    )}
                                </div>

                                {/* 显示对应价格或百分比 */}
                                <div style={{marginTop: '8px', fontSize: '12px', color: '#666'}}>
                                    {totalStopLossConfig.stopLossMode === 'percentage' && totalStopLossConfig.stopLossPercentage && totalStopLossConfig.stopLossPercentage > 0 ? (
                                        <span>对应价格: ${formatCalculatedPrice(
                                            isLong
                                                ? avgPrice * (1 - Math.min(totalStopLossConfig.stopLossPercentage || 0, 50) / 100)
                                                : avgPrice * (1 + Math.min(totalStopLossConfig.stopLossPercentage || 0, 50) / 100)
                                        )}</span>
                                    ) : totalStopLossConfig.stopLossMode === 'fixed' && totalStopLossConfig.stopLossPrice ? (
                                        <span>对应百分比: {(
                                            isLong
                                                ? ((avgPrice - totalStopLossConfig.stopLossPrice) / avgPrice * 100)
                                                : ((totalStopLossConfig.stopLossPrice - avgPrice) / avgPrice * 100)
                                        ).toFixed(2)}%</span>
                                    ) : null}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </Modal>
        );
    };

    // 策略编辑弹窗
    const renderEditStrategyModal = () => {
        if (!editingSingleStrategy) return null;

        // 找到对应的实时仓位数据以获取当前价格
        const currentPosition = positions.find(p =>
            p.instId === editingSingleStrategy.instId &&
            p.posSide === editingSingleStrategy.posSide
        );

        const isLong = editingSingleStrategy.posSide === 'long';
        const currentPrice = currentPosition ? parseFloat(String(currentPosition.markPx || '0')) : 0;

        const handleSaveEditStrategy = async () => {
            if (!editingSingleStrategy || !apiKeyId) return;

            try {
                const response = await tradingService.amendTotalStopLoss({
                    apiKeyId,
                    algoId: editingSingleStrategy.algoId,
                    instId: editingSingleStrategy.instId,  // 添加instId参数
                    tpTriggerPx: editStrategyConfig.tpTriggerPx || undefined,
                    slTriggerPx: editStrategyConfig.slTriggerPx || undefined,
                    tpTriggerPxType: editStrategyConfig.tpTriggerPxType,
                    slTriggerPxType: editStrategyConfig.slTriggerPxType
                });

                if (response.data.success) {
                    message.success('策略修改成功');
                    setShowEditStrategyModal(false);
                    setEditingSingleStrategy(null);
                    // 刷新数据
                    await fetchPositions();
                    onOrderUpdate();
                } else {
                    Modal.error({
                        title: <span style={{color: '#ffffff'}}>修改失败</span>,
                        content: (
                            <div style={{color: '#a0a0a0'}}>
                                {response.data.message}
                            </div>
                        ),
                        centered: true,
                        className: 'dark-theme-modal'
                    });
                }
            } catch (error: any) {
                console.error('修改策略失败:', error);
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>修改失败</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            {error.response?.data?.message || error.message}
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
            }
        };

        const handleCloseEditStrategyModal = () => {
            setShowEditStrategyModal(false);
            setEditingSingleStrategy(null);
            setEditStrategyConfig({
                tpTriggerPx: '',
                slTriggerPx: '',
                tpTriggerPxType: '1',
                slTriggerPxType: '1'
            });
        };

        return (
            <Modal
                title={
                    <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                        <EditOutlined style={{color: '#1890ff'}}/>
                        <span style={{color: '#ffffff'}}>编辑策略</span>
                    </div>
                }
                open={showEditStrategyModal}
                onCancel={handleCloseEditStrategyModal}
                footer={[
                    <Button key="cancel" onClick={handleCloseEditStrategyModal}>
                        取消
                    </Button>,
                    <Button
                        key="save"
                        type="primary"
                        onClick={handleSaveEditStrategy}
                        disabled={!editStrategyConfig.tpTriggerPx && !editStrategyConfig.slTriggerPx}
                    >
                        保存修改
                    </Button>
                ]}
                width={500}
                centered
                className="dark-theme-modal"
            >
                <div style={{color: '#a0a0a0'}}>
                    {/* 策略信息展示 */}
                    <div style={{
                        background: '#2a2a2a',
                        padding: '12px 16px',
                        borderRadius: '6px',
                        marginBottom: '20px',
                        border: '1px solid #404040'
                    }}>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>合约：</span>
                                <span
                                    style={{color: '#ffffff', fontWeight: '500'}}>{editingSingleStrategy.instId}</span>
                            </div>
                            <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                <span style={{color: '#999999'}}>方向：</span>
                                <span style={{color: '#ffffff', fontWeight: '500'}}>
                                    {isLong ? (
                                        <span style={{color: '#52c41a'}}>做多</span>
                                    ) : (
                                        <span style={{color: '#ff4d4f'}}>做空</span>
                                    )}
                                </span>
                            </div>
                            {currentPrice > 0 && (
                                <div style={{display: 'flex', justifyContent: 'space-between'}}>
                                    <span style={{color: '#999999'}}>当前价格：</span>
                                    <span style={{color: '#ffffff', fontWeight: '500'}}>
                                        {currentPrice.toFixed(6)}
                                    </span>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* 止盈设置 */}
                    <div style={{marginBottom: '16px'}}>
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginBottom: '8px'
                        }}>
                            <Text strong style={{color: '#ffffff'}}>止盈价格</Text>
                        </div>
                        <Input
                            placeholder="输入止盈价格"
                            value={editStrategyConfig.tpTriggerPx}
                            onChange={(e) => setEditStrategyConfig(prev => ({
                                ...prev,
                                tpTriggerPx: e.target.value
                            }))}
                            style={{
                                background: '#1a1a1a',
                                borderColor: '#434343',
                                color: '#ffffff'
                            }}
                        />
                        {editStrategyConfig.tpTriggerPx && currentPrice > 0 && (
                            <div style={{marginTop: '6px', fontSize: '12px', color: '#999'}}>
                                盈利比例: {isLong
                                ? ((parseFloat(editStrategyConfig.tpTriggerPx) - currentPrice) / currentPrice * 100).toFixed(2)
                                : ((currentPrice - parseFloat(editStrategyConfig.tpTriggerPx)) / currentPrice * 100).toFixed(2)
                            }%
                            </div>
                        )}
                    </div>

                    {/* 止损设置 */}
                    <div style={{marginBottom: '16px'}}>
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginBottom: '8px'
                        }}>
                            <Text strong style={{color: '#ffffff'}}>止损价格</Text>
                        </div>
                        <Input
                            placeholder="输入止损价格"
                            value={editStrategyConfig.slTriggerPx}
                            onChange={(e) => setEditStrategyConfig(prev => ({
                                ...prev,
                                slTriggerPx: e.target.value
                            }))}
                            style={{
                                background: '#1a1a1a',
                                borderColor: '#434343',
                                color: '#ffffff'
                            }}
                        />
                        {editStrategyConfig.slTriggerPx && currentPrice > 0 && (
                            <div style={{marginTop: '6px', fontSize: '12px', color: '#999'}}>
                                亏损比例: {isLong
                                ? ((currentPrice - parseFloat(editStrategyConfig.slTriggerPx)) / currentPrice * 100).toFixed(2)
                                : ((parseFloat(editStrategyConfig.slTriggerPx) - currentPrice) / currentPrice * 100).toFixed(2)
                            }%
                            </div>
                        )}
                    </div>

                    {/* 提示信息 */}
                    <div style={{
                        background: 'rgba(24, 144, 255, 0.1)',
                        border: '1px solid rgba(24, 144, 255, 0.3)',
                        borderRadius: '6px',
                        padding: '12px',
                        fontSize: '13px',
                        lineHeight: '1.5'
                    }}>
                        <div style={{color: '#1890ff', marginBottom: '4px'}}>
                            💡 温馨提示
                        </div>
                        <div style={{color: '#a0a0a0'}}>
                            修改后的策略将立即生效，请确保价格设置合理
                        </div>
                    </div>
                </div>
            </Modal>
        );
    };

    // 订单详情弹窗组件
    const renderOrderDetailModal = () => {
        if (!selectedOrder) return null;

        const {cexOrder} = selectedOrder;

        const DetailItem = ({label, value, copyable = false}: {
            label: string,
            value: React.ReactNode,
            copyable?: boolean
        }) => (
            <div style={{marginBottom: '12px', display: 'flex', flexDirection: 'column'}}>
                <Text style={{color: '#8c8c8c', fontSize: '12px', marginBottom: '4px'}}>{label}</Text>
                <Text
                    style={{color: '#d9d9d9', fontSize: '14px'}}
                    copyable={copyable ? {text: String(value)} : false}
                >
                    {value}
                </Text>
            </div>
        );

        return (
            <Modal
                title={<span style={{color: '#fff'}}>订单详情</span>}
                open={orderDetailVisible}
                onCancel={handleCloseOrderDetail}
                footer={null}
                width={600}
                className="dark-theme-modal"
                styles={{body: {padding: '24px'}}}
            >
                <div style={{display: 'flex', flexDirection: 'column', gap: '24px'}}>
                    {/* 基础信息 */}
                    <div>
                        <Title level={5} style={{
                            color: '#fff',
                            marginBottom: '16px',
                            borderLeft: '3px solid #1890ff',
                            paddingLeft: '8px'
                        }}>基础信息</Title>
                        <Row gutter={[16, 16]}>
                            <Col span={12}>
                                <DetailItem label="系统订单ID" value={selectedOrder.orderUuid} copyable/>
                            </Col>
                            <Col span={12}>
                                <DetailItem label="合约代码" value={selectedOrder.instId} copyable/>
                            </Col>
                            <Col span={12}>
                                <DetailItem label="方向" value={
                                    <span style={{color: selectedOrder.side === 'buy' ? '#52c41a' : '#ff4d4f'}}>
                                        {selectedOrder.side === 'buy' ? '买入' : '卖出'}
                                        {selectedOrder.posSide ? ` (${selectedOrder.posSide})` : ''}
                                    </span>
                                }/>
                            </Col>
                            <Col span={12}>
                                <DetailItem label="类型" value={selectedOrder.orderType}/>
                            </Col>
                            <Col span={12}>
                                <DetailItem label="状态" value={
                                    <Tag
                                        color={selectedOrder.orderStatus === 'success' ? 'success' : selectedOrder.orderStatus === 'failed' ? 'error' : 'processing'}>
                                        {selectedOrder.orderStatus === 'success' ? '已成交' :
                                         selectedOrder.orderStatus === 'failed' ? '失败' :
                                         selectedOrder.orderStatus === 'canceled' ? '已撤销' :
                                         selectedOrder.orderStatus}
                                    </Tag>
                                }/>
                            </Col>
                            <Col span={12}>
                                <DetailItem label="创建时间"
                                            value={new Date(selectedOrder.createdTime).toLocaleString()}/>
                            </Col>
                            {selectedOrder.errorMsg && (
                                <Col span={24}>
                                    <DetailItem label="错误信息" value={<span
                                        style={{color: '#ff4d4f'}}>{selectedOrder.errorMsg}</span>}/>
                                </Col>
                            )}
                        </Row>
                    </div>

                    {/* CEX订单信息 */}
                    {cexOrder && (
                        <div>
                            <Title level={5} style={{
                                color: '#fff',
                                marginBottom: '16px',
                                borderLeft: '3px solid #faad14',
                                paddingLeft: '8px'
                            }}>交易所订单详情</Title>
                            <Row gutter={[16, 16]}>
                                <Col span={12}>
                                    <DetailItem label="交易所订单ID" value={cexOrder.orderId} copyable/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="交易所状态" value={cexOrder.orderState}/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="委托价格" value={cexOrder.px}/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="成交均价" value={cexOrder.avgPx || '-'}/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="委托数量" value={cexOrder.sz}/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="成交数量" value={cexOrder.filledSz}/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="手续费" value={`${cexOrder.fee} ${cexOrder.feeCcy || ''}`}/>
                                </Col>
                                <Col span={12}>
                                    <DetailItem label="更新时间"
                                                value={cexOrder.uTime ? new Date(Number(cexOrder.uTime)).toLocaleString() : '-'}/>
                                </Col>
                            </Row>
                        </div>
                    )}
                </div>
            </Modal>
        );
    };

    if (!apiKeyId) {
        return (
            <Card className="order-list-card" title={<Title level={4}>订单列表</Title>}>
                <Empty
                    description="请先选择API Key"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    style={{height: 300}}
                />
            </Card>
        );
    }

    return (
        <>
            <Card className="order-list-card" title={
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                    <Title level={4} style={{margin: 0}}>订单管理</Title>
                    <div style={{display: 'flex', alignItems: 'center', gap: 8}}>
                        <Text type="secondary" style={{fontSize: 12}}>
                            {activeTab === 'positions' ?
                                (isActive ? '自动刷新中' : '已暂停') :
                                (isHistoryActive ? '自动刷新中' : '已暂停')
                            }
                        </Text>
                        <Tooltip title="手动刷新">
                            <Button
                                type="text"
                                size="small"
                                icon={<ReloadOutlined/>}
                                onClick={loadData}
                                loading={activeTab === 'positions' ? loading : loadingHistory}
                                style={{color: '#1890ff'}}
                            />
                        </Tooltip>
                    </div>
                </div>
            }>
                <Tabs
                    className="order-list-tabs"
                    activeKey={activeTab}
                    onChange={(key) => setActiveTab(key as 'positions' | 'pending' | 'history')}
                    defaultActiveKey="positions"
                    items={[
                        {
                            key: 'positions',
                            label: `活跃仓位 (${positions.length})`,
                            children: (
                                <div className="order-list-container">
                                    {positions.length > 0 ? (
                                        positions.map(position => renderPositionItem(position))
                                    ) : (
                                        <Empty
                                            description="暂无活跃仓位"
                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                            style={{height: 200}}
                                        />
                                    )}
                                </div>
                            )
                        },
                        {
                            key: 'pending',
                            label: `当前委托 (${pendingOrders.length})`,
                            children: (
                                <div className="order-list-container">
                                    {pendingOrders.length > 0 ? (
                                        pendingOrders
                                            .filter(order => order != null)  // 过滤掉null元素
                                            .sort((a, b) => {
                                                const bt = typeof b.ctime === 'number' ? b.ctime : (b as any).cTime;
                                                const at = typeof a.ctime === 'number' ? a.ctime : (a as any).cTime;
                                                return (bt || 0) - (at || 0);
                                            })
                                            .map(renderPendingItem)
                                    ) : (
                                        <Empty
                                            description="暂无当前委托"
                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                            style={{height: 200}}
                                        />
                                    )}
                                </div>
                            )
                        },
                        {
                            key: 'history',
                            label: `历史订单 (${historyTotal})`,
                            children: (
                                <div
                                    className="order-list-container"
                                    style={{overflowY: 'auto'}}
                                    onScroll={handleHistoryScroll}
                                >
                                    {historyOrders.length > 0 ? (
                                        <>
                                            {historyOrders.map(renderHistoryItem)}
                                            {loadingMore && (
                                                <div style={{textAlign: 'center', padding: '20px'}}>
                                                    <Spin size="small"/>
                                                    <span style={{marginLeft: '10px', color: '#999'}}>加载中...</span>
                                                </div>
                                            )}
                                            {!hasMore && historyOrders.length > 0 && (
                                                <div style={{textAlign: 'center', padding: '20px', color: '#999'}}>
                                                    没有更多了
                                                </div>
                                            )}
                                        </>
                                    ) : loadingHistory ? (
                                        <div style={{textAlign: 'center', padding: '40px'}}>
                                            <Spin/>
                                        </div>
                                    ) : (
                                        <Empty
                                            description="暂无历史订单"
                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                            style={{height: 200}}
                                        />
                                    )}
                                </div>
                            )
                        }
                    ]}
                />
            </Card>
            {renderTakeProfitModal()}
            {renderStopLossModal()}
            {renderTotalStopLossModal()}
            {renderEditStrategyModal()}
            {renderOrderDetailModal()}
            <ActivePositionOrderChartModal
                visible={chartModalVisible}
                onClose={handleCloseChartModal}
                item={selectedPositionForChart}
                apiKeyId={apiKeyId || 0}
            />
        </>
    );
};

export default React.memo(OrderList);
