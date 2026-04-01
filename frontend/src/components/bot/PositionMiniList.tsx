import React, {useEffect, useRef, useState} from 'react';
import {Badge, Button, Card, Empty, Modal, Spin, Table, Tooltip, Typography, message} from 'antd';
import {CloseCircleOutlined} from '@ant-design/icons';
import {OrderModel, tradingService} from '../../services/tradingService';
import ActivePositionOrderChartModal from './ActivePositionOrderChartModal';
import HistoryPositionChartModal from './HistoryPositionChartModal';
import './flat-radius-table.css';

// 自定义市价平仓SVG图标 - 使用下降箭头表示平仓，颜色通过父组件控制
const MarketCloseIcon = ({color = '#ff4d4f'}: { color?: string }) => (
    <svg viewBox="0 0 24 24" width="1.2em" height="1.2em" fill="currentColor" style={{color}}>
        <path d="M7 10l5 5 5-5z"/>
        <path
            d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>
    </svg>
);

const {Text} = Typography;

interface PositionMiniListProps {
    apiKeyId: number;
    onDataLoad?: () => void;
    activeTabKey?: 'positions' | 'orders' | 'history';
}

// 简化的持仓类型
interface MiniPosition {
    posId: string;
    instId: string;
    posSide: string;
    pos: number | null;
    upl: number | null;
    ctime: number | null;
    avgPx: number | null;
    detail: any;
}

/**
 * 持仓迷你列表组件
 * 显示当前持仓的简化信息
 */
const PositionMiniList: React.FC<PositionMiniListProps> = ({apiKeyId, onDataLoad, activeTabKey}) => {
    const [activeTab, setActiveTab] = useState<string>('positions');
    const [positions, setPositions] = useState<MiniPosition[]>([]);
    const [orders, setOrders] = useState<OrderModel[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');

    // 历史仓位状态
    const [historyPositions, setHistoryPositions] = useState<any[]>([]);
    const [historyLoading, setHistoryLoading] = useState<boolean>(false);
    const [historyHasMore, setHistoryHasMore] = useState<boolean>(true);
    const [historyBefore, setHistoryBefore] = useState<string>('');
    // 使用 ref 保存最新的 historyBefore 值，解决闭包问题
    const historyBeforeRef = useRef<string>('');
    // 同步 historyBefore 和 ref
    useEffect(() => {
        historyBeforeRef.current = historyBefore;
    }, [historyBefore]);

    // 新增：数量状态
    const [positionsCount, setPositionsCount] = useState<number>(0);
    const [ordersCount, setOrdersCount] = useState<number>(0);

    const refreshSeqRef = useRef(0);
    const loadingRef = useRef<boolean>(false);
    const historyLoadingRef = useRef<boolean>(false);

    useEffect(() => {
        if (!activeTabKey) return;
        setActiveTab((prev) => (prev === activeTabKey ? prev : activeTabKey));
    }, [activeTabKey]);

    useEffect(() => {
        loadingRef.current = loading;
    }, [loading]);

    useEffect(() => {
        historyLoadingRef.current = historyLoading;
    }, [historyLoading]);

    // K线图弹窗状态
    const [chartModalVisible, setChartModalVisible] = useState<boolean>(false);
    const [selectedPosition, setSelectedPosition] = useState<any>(null);
    const [activeChartModalVisible, setActiveChartModalVisible] = useState<boolean>(false);
    const [selectedActiveItem, setSelectedActiveItem] = useState<any>(null);

    // 悬浮状态管理
    const [hoveredPosId, setHoveredPosId] = useState<string | null>(null);
    const [showOverlay, setShowOverlay] = useState<boolean>(false);
    const hoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const hoveredPosIdRef = useRef<string | null>(null);
    const hoveredRowRef = useRef<HTMLTableRowElement | null>(null);
    const tableAreaRef = useRef<HTMLDivElement | null>(null);
    const overlayActionRef = useRef<HTMLDivElement | null>(null);
    const [overlayRect, setOverlayRect] = useState<{ top: number; height: number } | null>(null);
    const [overlayRecord, setOverlayRecord] = useState<MiniPosition | null>(null);

    const [hoveredOrdId, setHoveredOrdId] = useState<string | null>(null);
    const [showOrderOverlay, setShowOrderOverlay] = useState<boolean>(false);
    const orderHoverTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const hoveredOrdIdRef = useRef<string | null>(null);
    const hoveredOrderRowRef = useRef<HTMLTableRowElement | null>(null);
    const orderTableAreaRef = useRef<HTMLDivElement | null>(null);
    const orderOverlayActionRef = useRef<HTMLDivElement | null>(null);
    const [orderOverlayRect, setOrderOverlayRect] = useState<{ top: number; height: number } | null>(null);
    const [orderOverlayRecord, setOrderOverlayRecord] = useState<OrderModel | null>(null);

    // 平仓状态管理
    const [closingPositions, setClosingPositions] = useState<Set<string>>(new Set());

    useEffect(() => {
        hoveredPosIdRef.current = hoveredPosId;
    }, [hoveredPosId]);

    useEffect(() => {
        hoveredOrdIdRef.current = hoveredOrdId;
    }, [hoveredOrdId]);

    useEffect(() => {
        if (activeTab !== 'positions') {
            setShowOverlay(false);
            setOverlayRect(null);
            setOverlayRecord(null);
            hoveredRowRef.current = null;
            return;
        }

        const tableArea = tableAreaRef.current;
        const scrollContainer = tableArea?.closest('.ant-card-body') as HTMLElement | null;
        if (!scrollContainer) return;

        const onScroll = () => {
            setShowOverlay(false);
            setOverlayRect(null);
            hoveredRowRef.current = null;
        };

        scrollContainer.addEventListener('scroll', onScroll, {passive: true});
        return () => {
            scrollContainer.removeEventListener('scroll', onScroll as any);
        };
    }, [activeTab]);
    useEffect(() => {
        if (activeTab !== 'orders') {
            if (orderHoverTimerRef.current) {
                clearTimeout(orderHoverTimerRef.current);
                orderHoverTimerRef.current = null;
            }
            setHoveredOrdId(null);
            setShowOrderOverlay(false);
            setOrderOverlayRect(null);
            setOrderOverlayRecord(null);
            hoveredOrderRowRef.current = null;
            return;
        }

        const tableArea = orderTableAreaRef.current;
        const scrollContainer = tableArea?.closest('.ant-card-body') as HTMLElement | null;
        if (!scrollContainer) return;

        const onScroll = () => {
            setShowOrderOverlay(false);
            setOrderOverlayRect(null);
            hoveredOrderRowRef.current = null;
        };

        scrollContainer.addEventListener('scroll', onScroll, {passive: true});
        return () => {
            scrollContainer.removeEventListener('scroll', onScroll as any);
        };
    }, [activeTab]);


    /**
     * 处理行鼠标移入
     */
    const handleRowMouseEnter = (record: MiniPosition, e: React.MouseEvent<HTMLTableRowElement>) => {

        if (hoverTimerRef.current) {
            clearTimeout(hoverTimerRef.current);
            hoverTimerRef.current = null;
        }

        setHoveredPosId(record.posId);
        setShowOverlay(false);
        setOverlayRect(null);
        setOverlayRecord(record);
        hoveredRowRef.current = e.currentTarget;

        hoverTimerRef.current = setTimeout(() => {
            if (hoveredPosIdRef.current !== record.posId) return;
            const tableArea = tableAreaRef.current;
            const rowEl = hoveredRowRef.current;
            if (!tableArea || !rowEl) return;
            const areaRect = tableArea.getBoundingClientRect();
            const rowRect = rowEl.getBoundingClientRect();
            setOverlayRect({
                top: rowRect.top - areaRect.top,
                height: rowRect.height
            });
            setShowOverlay(true);
        }, 3000);
    };

    /**
     * 处理行鼠标移出
     */
    const handleRowMouseLeave = (e?: React.MouseEvent) => {
        const relatedTarget = (e?.relatedTarget as Node | null) ?? null;
        if (relatedTarget && overlayActionRef.current?.contains(relatedTarget)) {
            return;
        }

        if (hoverTimerRef.current) {
            clearTimeout(hoverTimerRef.current);
            hoverTimerRef.current = null;
        }
        setHoveredPosId(null);
        setShowOverlay(false);
        setOverlayRect(null);
        setOverlayRecord(null);
        hoveredRowRef.current = null;
    };

    const handleOrderRowMouseEnter = (record: OrderModel, e: React.MouseEvent<HTMLTableRowElement>) => {
        if (!record.ordId) return;

        if (orderHoverTimerRef.current) {
            clearTimeout(orderHoverTimerRef.current);
            orderHoverTimerRef.current = null;
        }

        setHoveredOrdId(record.ordId);
        setShowOrderOverlay(false);
        setOrderOverlayRect(null);
        setOrderOverlayRecord(record);
        hoveredOrderRowRef.current = e.currentTarget;

        orderHoverTimerRef.current = setTimeout(() => {
            if (hoveredOrdIdRef.current !== record.ordId) return;
            const tableArea = orderTableAreaRef.current;
            const rowEl = hoveredOrderRowRef.current;
            if (!tableArea || !rowEl) return;
            const areaRect = tableArea.getBoundingClientRect();
            const rowRect = rowEl.getBoundingClientRect();
            setOrderOverlayRect({
                top: rowRect.top - areaRect.top,
                height: rowRect.height
            });
            setShowOrderOverlay(true);
        }, 3000);
    };

    const handleOrderRowMouseLeave = (e?: React.MouseEvent) => {
        const relatedTarget = (e?.relatedTarget as Node | null) ?? null;
        if (relatedTarget && orderOverlayActionRef.current?.contains(relatedTarget)) {
            return;
        }

        if (orderHoverTimerRef.current) {
            clearTimeout(orderHoverTimerRef.current);
            orderHoverTimerRef.current = null;
        }
        setHoveredOrdId(null);
        setShowOrderOverlay(false);
        setOrderOverlayRect(null);
        setOrderOverlayRecord(null);
        hoveredOrderRowRef.current = null;
    };

    const handleCancelOrder = (order: OrderModel) => {
        const finalApiKeyId = order.apiKeyId || apiKeyId;
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
                    确定要撤销订单 <strong>{order.ordId}</strong> 吗？
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
                const instId = order.instId || undefined;
                try {
                    const response = await tradingService.cancelOrder(order.ordId, finalApiKeyId, instId);
                    if (response.data.success) {
                        message.success('撤单成功');
                        setShowOrderOverlay(false);
                        setOrderOverlayRect(null);
                        setOrderOverlayRecord(null);
                        hoveredOrderRowRef.current = null;
                        await fetchData('orders');
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

    /**
     * 市价全平
     */
    const handleMarketClosePosition = (position: MiniPosition) => {
        if (!position.instId || !position.posId) return;
        
        // 阻止冒泡已经由Overlay中的onClick处理了，但为了安全起见，这里不需要做额外处理，
        // 因为调用者应该处理好冒泡。

        Modal.confirm({
            title: `确认市价平仓 ${position.instId}?`,
            content: (
                <div style={{display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '12px'}}>
                    <div className="confirm-modal-row" style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}>
                        <span style={{color: '#888', fontSize: '12px'}}>方向</span>
                        <span style={{
                            color: position.posSide === 'long' ? '#52c41a' : '#ff4d4f',
                            fontSize: '14px',
                            fontWeight: '600'
                        }}>
                            {position.posSide === 'long' ? '做多' : (position.posSide === 'short' ? '做空' : '净持仓')}
                        </span>
                    </div>
                    
                    <div className="confirm-modal-row" style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}>
                        <span style={{color: '#888', fontSize: '12px'}}>持仓数量</span>
                        <span style={{color: '#fff', fontSize: '14px', fontWeight: '500'}}>
                            {position.pos}
                        </span>
                    </div>

                    <div className="confirm-modal-row" style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}>
                        <span style={{color: '#888', fontSize: '12px'}}>未结盈亏</span>
                        <span style={{
                            color: (position.upl || 0) >= 0 ? '#52c41a' : '#ff4d4f',
                            fontSize: '14px',
                            fontWeight: '600'
                        }}>
                            {(position.upl || 0) >= 0 ? '+' : ''}{Number(position.upl).toFixed(4)}
                        </span>
                    </div>
                </div>
            ),
            okText: '确认平仓',
            cancelText: '取消',
            okButtonProps: {
                danger: (position.upl || 0) < 0,
                style: (position.upl || 0) >= 0 ? {
                    backgroundColor: '#52c41a',
                    borderColor: '#52c41a' 
                } : undefined
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
                setClosingPositions(prev => new Set(prev).add(position.posId));
                try {
                    const response = await tradingService.closePosition({
                        apiKeyId,
                        instId: position.instId,
                        posSide: position.posSide as 'long' | 'short',
                        sz: Math.abs(position.pos ?? 0),
                        mgnMode: position.detail?.mgnMode || 'cross'
                    });

                    if (response.data.success) {
                        message.success('市价平仓指令已发送');
                        // 延迟刷新数据
                        setTimeout(() => fetchData(), 1000);
                    } else {
                        message.error(response.data.message || '平仓失败');
                    }
                } catch (error) {
                    console.error('平仓异常:', error);
                    message.error('平仓请求异常');
                } finally {
                    setClosingPositions(prev => {
                        const next = new Set(prev);
                        next.delete(position.posId);
                        return next;
                    });
                }
            }
        });
    };

    /**
     * 获取历史仓位数据
     */
    const fetchHistory = async (reset = false) => {
        if (!apiKeyId || (historyLoading && !reset) || (!historyHasMore && !reset)) return;

        setHistoryLoading(true);
        try {
            const before = reset ? undefined : historyBeforeRef.current;
            // 获取历史持仓 (limit=10)
            const res = await tradingService.getPositionsHistory(apiKeyId, {
                limit: 10,
                before: before
            });

            // tradingService.getPositionsHistory 返回的是 ApiResponse<any[]>，也就是 {success, data, ...}
            // 但如果 service 层直接返回了 response.data，那么 res 实际上就是 ApiResponse 对象
            if (res && res.success && Array.isArray(res.data)) {
                const newItems = res.data;
                if (reset) {
                    setHistoryPositions(newItems);
                } else {
                    // 直接追加后端返回的数据
                    setHistoryPositions(prev => [...prev, ...newItems]);
                }

                // 更新分页游标 (使用第一条记录的utime作为before参数)
                if (newItems.length > 0) {
                    const firstUtime = newItems[0].utime || '';
                    setHistoryBefore(firstUtime.toString() || '');
                }

                // 如果返回少于10条，说明没有更多了
                if (newItems.length < 10) {
                    setHistoryHasMore(false);
                } else {
                    setHistoryHasMore(true);
                }
            } else {
                console.warn("历史仓位数据格式错误或请求失败", res);
                if (reset) setHistoryPositions([]);
                setHistoryHasMore(false);
            }
        } catch (e) {
            console.error('获取历史仓位失败', e);
        } finally {
            setHistoryLoading(false);
        }
    };

    /**
     * 处理历史仓位点击事件
     */
    const handleHistoryPositionClick = (record: any) => {
        setSelectedPosition(record);
        setChartModalVisible(true);
    };

    /**
     * 获取数据
     */
    const buildMiniPositions = (raw: any[]): MiniPosition[] => {
        return raw.slice(0, 10).map((pos: any) => ({
            posId: pos.posId || '',
            instId: pos.instId || '',
            posSide: pos.posSide || 'net',
            pos: pos.pos ?? null,
            upl: pos.upl ?? null,
            ctime: (pos.cTime ?? pos.ctime ?? null),
            avgPx: (pos.avgPx ?? null),
            detail: pos
        }));
    };

    const refreshBadgesAndCache = async (): Promise<{ superseded: boolean; positionsOk: boolean; ordersOk: boolean }> => {
        if (!apiKeyId) return {superseded: false, positionsOk: false, ordersOk: false};

        const seq = ++refreshSeqRef.current;

        const [positionsRes, ordersRes] = await Promise.allSettled([
            tradingService.getLivePositions(apiKeyId),
            tradingService.getPendingOrders(apiKeyId),
        ]);

        if (seq !== refreshSeqRef.current) {
            return {superseded: true, positionsOk: false, ordersOk: false};
        }

        let positionsOk = false;
        let ordersOk = false;

        if (positionsRes.status === 'fulfilled' && positionsRes.value?.success && Array.isArray(positionsRes.value.data)) {
            const rawPositions = positionsRes.value.data;
            setPositionsCount(rawPositions.length);
            setPositions(buildMiniPositions(rawPositions));
            positionsOk = true;
        }

        if (ordersRes.status === 'fulfilled' && ordersRes.value?.success && Array.isArray(ordersRes.value.data)) {
            const rawOrders = ordersRes.value.data;
            setOrdersCount(rawOrders.length);
            setOrders(rawOrders.slice(0, 10));
            ordersOk = true;
        }

        return {superseded: false, positionsOk, ordersOk};
    };

    const fetchData = async (targetTab?: string) => {
        if (!apiKeyId) return;

        // 使用传入的targetTab或当前的activeTab
        const currentTab = targetTab || activeTab;

        // 如果是历史tab，且不是初次加载（已有数据），则不自动刷新，除非手动触发
        if (currentTab === 'history') {
            if (historyPositions.length === 0) {
                fetchHistory(true);
            }
            return;
        }

        setLoading(true);
        setError('');

        try {
            const result = await refreshBadgesAndCache();
            if (result.superseded) {
                return;
            }
            if (currentTab === 'positions' && !result.positionsOk) {
                setError('获取持仓数据失败');
            }
            if (currentTab === 'orders' && !result.ordersOk) {
                setError('获取委托订单失败');
            }
            if (currentTab === 'positions' || currentTab === 'orders') {
                onDataLoad?.();
            }
        } catch (err) {
            console.error('获取数据失败:', err);
            setError('获取数据失败');
        } finally {
            setLoading(false);
        }
    };

    // 定时拉取：每10秒刷新一次
    useEffect(() => {
        if (!apiKeyId) return;

        // 切换Tab时立即获取一次当前Tab的数据
        if (activeTab === 'history') {
            refreshBadgesAndCache();
            if (historyPositions.length === 0) {
                fetchHistory(true);
            }
        } else {
            fetchData();
        }

        // 设置定时器（仅在页面可见时执行）
        const timer = setInterval(() => {
            if (document.hidden) return;
            if (loadingRef.current || historyLoadingRef.current) return;

            refreshBadgesAndCache();
        }, 10000);  // 10秒刷新

        // 清理定时器
        return () => clearInterval(timer);
    }, [apiKeyId, activeTab]);

    // 持仓表格列定义
    const positionColumns = [
        {
            title: '合约',
            dataIndex: 'instId',
            key: 'instId',
            width: 140,
            render: (instId: string, record: MiniPosition) => {
                const isLong = record.posSide === 'long';
                return (
                    <Text
                        style={{
                            color: isLong ? '#52c41a' : '#ff4d4f',
                            fontSize: 12,
                            fontWeight: 500
                        }}
                        ellipsis={{tooltip: instId}}
                    >
                        {instId}
                    </Text>
                );
            }
        },
        {
            title: '数量',
            dataIndex: 'pos',
            key: 'pos',
            width: 80,
            render: (pos: number | null) => (
                <Text style={{color: '#d9d9d9', fontSize: 12}}>
                    {pos !== null && pos !== undefined ? pos.toFixed(2) : '-'}
                </Text>
            )
        },
        {
            title: '盈亏',
            dataIndex: 'upl',
            key: 'upl',
            width: 100,
            render: (upl: number | null) => {
                const value = upl ?? 0;
                const isPositive = value >= 0;
                return (
                    <Text
                        style={{
                            color: isPositive ? '#52c41a' : '#ff4d4f',
                            fontSize: 12,
                            fontWeight: 500
                        }}
                    >
                        {value !== null && value !== undefined ? (isPositive ? '+' : '') + value.toFixed(2) : '-'}
                    </Text>
                );
            }
        }
    ];

    // 格式化订单方向和持仓方向
    const formatSideAndPosSide = (side: string, posSide: string): { text: string, color: string } => {
        // 推断posSide（当posSide为空时，根据side推断）
        const inferredPosSide = posSide || (side === 'buy' ? 'long' : (side === 'sell' ? 'short' : 'net'));

        if ('buy' === side && 'long' === inferredPosSide) {
            // 买入开多 - 绿色
            return { text: '买入开多', color: '#52c41a' };
        } else if ('sell' === side && 'short' === inferredPosSide) {
            // 卖出开空 - 红色
            return { text: '卖出开空', color: '#ff4d4f' };
        } else if ('sell' === side && 'long' === inferredPosSide) {
            // 卖出平多 - 橙色
            return { text: '卖出平多', color: '#faad14' };
        } else if ('buy' === side && 'short' === inferredPosSide) {
            // 买入平空 - 蓝色
            return { text: '买入平空', color: '#1890ff' };
        }
        return { text: `${side} ${inferredPosSide}`, color: '#999' };
    };

    // 委托订单表格列定义
    const orderColumns = [
        {
            title: '合约',
            dataIndex: 'instId',
            key: 'instId',
            width: 110,
            render: (instId: string, record: OrderModel) => {
                const isBuy = record.side === 'buy';
                return (
                    <Text
                        style={{
                            color: isBuy ? '#52c41a' : '#ff4d4f',
                            fontSize: 12,
                            fontWeight: 500
                        }}
                        ellipsis={{tooltip: instId}}
                    >
                        {instId}
                    </Text>
                );
            }
        },
        {
            title: '方向',
            dataIndex: 'side',
            key: 'direction',
            width: 80,
            render: (_: string, record: OrderModel) => {
                const { text, color } = formatSideAndPosSide(record.side, record.posSide);
                return (
                    <Text style={{ color, fontSize: 12, fontWeight: 500 }}>
                        {text}
                    </Text>
                );
            }
        },
        {
            title: '数量',
            dataIndex: 'sz',
            key: 'sz',
            width: 70,
            render: (sz: string) => (
                <Text style={{color: '#d9d9d9', fontSize: 12}}>
                    {sz}
                </Text>
            )
        },
        {
            title: '价格',
            dataIndex: 'px',
            key: 'px',
            width: 90,
            render: (px: string) => (
                <Text style={{color: '#d9d9d9', fontSize: 12}}>
                    {px}
                </Text>
            )
        }
    ];

    // 历史持仓表格列定义
    const historyColumns = [
        {
            title: '合约',
            dataIndex: 'instId',
            key: 'instId',
            width: 140,
            render: (instId: string, record: any) => {
                // posSide might be 'long' or 'short' or 'net'
                const isLong = record.posSide === 'long';
                const cTimeStr = (record.cTime || record.ctime) ? new Date(Number(record.cTime || record.ctime)).toLocaleString() : '-';
                const uTimeStr = (record.uTime || record.utime) ? new Date(Number(record.uTime || record.utime)).toLocaleString() : '-';
                return (
                    <div style={{display: 'flex', flexDirection: 'column'}}>
                        <Text
                            style={{
                                color: isLong ? '#52c41a' : '#ff4d4f',
                                fontSize: 12,
                                fontWeight: 500
                            }}
                            ellipsis={{tooltip: instId}}
                        >
                            {instId}
                        </Text>
                        <Text style={{color: '#8c8c8c', fontSize: 10}}>
                            开: {cTimeStr}
                        </Text>
                        <Text style={{color: '#8c8c8c', fontSize: 10}}>
                            关: {uTimeStr}
                        </Text>
                    </div>
                );
            }
        },
        {
            title: '时长',
            key: 'duration',
            width: 80,
            render: (_: any, record: any) => {
                const cTime = Number(record.cTime || record.ctime || 0);
                const uTime = Number(record.uTime || record.utime || 0);

                if (cTime > 0 && uTime > 0 && uTime >= cTime) {
                    const diffMs = uTime - cTime;
                    const diffMins = Math.floor(diffMs / (1000 * 60));

                    const days = Math.floor(diffMins / (60 * 24));
                    const hours = Math.floor((diffMins % (60 * 24)) / 60);
                    const minutes = diffMins % 60;

                    let durationStr = '';
                    if (days > 0) durationStr += `${days}d`;
                    if (hours > 0) durationStr += `${hours}h`;
                    durationStr += `${minutes}m`;

                    return (
                        <Text style={{color: '#d9d9d9', fontSize: 12}}>
                            {durationStr}
                        </Text>
                    );
                }
                return <Text style={{color: '#595959', fontSize: 12}}>-</Text>;
            }
        },
        {
            title: '盈亏',
            dataIndex: 'realizedPnl',
            key: 'realizedPnl',
            width: 100,
            render: (pnl: any) => {
                const val = parseFloat(pnl || '0');
                const isPositive = val >= 0;
                return (
                    <Text
                        style={{
                            color: isPositive ? '#52c41a' : '#ff4d4f',
                            fontSize: 12,
                            fontWeight: 500
                        }}
                    >
                        {isPositive ? '+' : ''}{val.toFixed(4)}
                    </Text>
                );
            }
        }
    ];

    // 定义Badge颜色：0为绿色，非0为橙色
    const getBadgeColor = (count: number) => {
        return count === 0 ? '#52c41a' : '#fa8c16';
    };

    const tabList = [
        {
            key: 'positions',
            tab: (
                <Badge
                    count={positionsCount}
                    showZero
                    offset={[10, 0]}
                    size="small"
                    color={getBadgeColor(positionsCount)}
                >
                    <span style={{
                        color: activeTab === 'positions' ? '#1890ff' : '#d9d9d9',
                        fontWeight: activeTab === 'positions' ? 500 : 400
                    }}>
                        当前持仓
                    </span>
                </Badge>
            ),
        },
        {
            key: 'orders',
            tab: (
                <Badge
                    count={ordersCount}
                    showZero
                    offset={[10, 0]}
                    size="small"
                    color={getBadgeColor(ordersCount)}
                >
                    <span style={{
                        color: activeTab === 'orders' ? '#1890ff' : '#d9d9d9',
                        fontWeight: activeTab === 'orders' ? 500 : 400
                    }}>
                        当前委托
                    </span>
                </Badge>
            ),
        },
        {
            key: 'history',
            tab: (
                <span style={{
                    color: activeTab === 'history' ? '#1890ff' : '#d9d9d9',
                    fontWeight: activeTab === 'history' ? 500 : 400
                }}>
                    历史仓位
                </span>
            ),
        },
    ];

    return (
        <>
            <Card
                tabList={tabList}
                activeTabKey={activeTab}
                onTabChange={(key) => setActiveTab(key)}
                size="small"
                style={{
                    height: 300,
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343'
                }}
                styles={{
                    header: {
                        borderBottom: 'none',
                        minHeight: '40px',
                        backgroundColor: '#1f1f1f'
                    },
                    body: {
                        padding: 0,
                        height: 240,
                        overflow: 'auto'
                    }
                }}
                tabProps={{
                    size: 'small',
                    className: 'custom-dark-tabs',
                    tabBarStyle: {
                        marginBottom: 0,
                        color: '#d9d9d9',
                        backgroundColor: '#1f1f1f'
                    }
                }}
            >
                {activeTab === 'history' ? (
                    <>
                        {historyPositions.length === 0 && !historyLoading ? (
                            <Empty
                                description={<Text style={{color: '#d9d9d9'}}>暂无历史仓位</Text>}
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                            />
                        ) : (
                            <>
                                <Table
                                    dataSource={historyPositions}
                                    columns={historyColumns}
                                    rowKey={(record) => record.posId && record.utime ? `${record.posId}_${record.utime}` : `history_${record.posId || Math.random()}`}
                                    pagination={false}
                                    size="small"
                                    style={{fontSize: 12, borderRadius: 0}}
                                    className="mini-position-table flat-radius-table"
                                    loading={historyLoading && historyPositions.length === 0}
                                    onRow={(record) => ({
                                        onClick: () => handleHistoryPositionClick(record),
                                        style: {cursor: 'pointer'}
                                    })}
                                />
                                {historyPositions.length > 0 && (
                                    <div style={{textAlign: 'center', padding: '8px 0'}}>
                                        {historyHasMore ? (
                                            <Button
                                                type="text"
                                                size="small"
                                                onClick={() => fetchHistory(false)}
                                                loading={historyLoading}
                                                style={{color: '#1890ff', fontSize: '12px'}}
                                            >
                                                {historyLoading ? '加载中...' : '加载更多'}
                                            </Button>
                                        ) : (
                                            <Text style={{color: '#595959', fontSize: 10}}>没有更多了</Text>
                                        )}
                                    </div>
                                )}
                            </>
                        )}
                    </>
                ) : loading ? (
                    <div style={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%'}}>
                        <Spin size="large"/>
                    </div>
                ) : error ? (
                    <Empty
                        description={<Text style={{color: '#d9d9d9'}}>{error}</Text>}
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                ) : activeTab === 'positions' ? (
                    positions.length === 0 ? (
                        <Empty
                            description={<Text style={{color: '#d9d9d9'}}>暂无持仓</Text>}
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                    ) : (
                        <div ref={tableAreaRef} style={{position: 'relative'}}>
                            <Table
                                dataSource={positions}
                                columns={positionColumns}
                                rowKey={(record) => record.posId ? `${record.posId}_${record.instId}_${record.posSide}` : `position_${record.instId || Math.random()}`}
                                pagination={false}
                                size="small"
                                style={{fontSize: 12, borderRadius: 0}}
                                className="mini-position-table flat-radius-table"
                                onRow={(record) => ({
                                    onMouseEnter: (e) => handleRowMouseEnter(record, e),
                                    onMouseLeave: (e) => handleRowMouseLeave(e),
                                    onClick: () => {
                                        if (!record.instId) return;
                                        const ctime = record.ctime ?? Date.now() - 60 * 60 * 1000;
                                        setSelectedActiveItem({
                                            type: 'position',
                                            instId: record.instId,
                                            posSide: record.posSide,
                                            ctime,
                                            referencePrice: record.avgPx ?? undefined,
                                            detail: record.detail
                                        });
                                        setActiveChartModalVisible(true);
                                    },
                                    style: {cursor: 'pointer'}
                                })}
                            />
                            {showOverlay && overlayRect && overlayRecord && (
                                <>
                                    <div style={{
                                        position: 'absolute',
                                        left: 0,
                                        right: 0,
                                        top: overlayRect.top,
                                        height: overlayRect.height,
                                        backgroundColor: 'rgba(31, 31, 31, 0.15)',
                                        backdropFilter: 'blur(2px)',
                                        pointerEvents: 'none',
                                        zIndex: 2
                                    }}/>
                                    <div
                                        ref={overlayActionRef}
                                        style={{
                                            position: 'absolute',
                                            right: 16,
                                            top: overlayRect.top,
                                            height: overlayRect.height,
                                            display: 'flex',
                                            alignItems: 'center',
                                            pointerEvents: 'auto',
                                            zIndex: 3
                                        }}
                                        onMouseLeave={(e) => {
                                            const relatedTarget = (e.relatedTarget as Node | null) ?? null;
                                            if (relatedTarget && hoveredRowRef.current?.contains(relatedTarget)) {
                                                return;
                                            }
                                            handleRowMouseLeave();
                                        }}
                                    >
                                        <Tooltip title="市价全平">
                                            <Button
                                                type="text"
                                                shape="circle"
                                                size="large"
                                                icon={
                                                    closingPositions.has(overlayRecord.posId)
                                                        ? <Spin size="small"/>
                                                        : <MarketCloseIcon color={
                                                            (overlayRecord.upl ?? 0) > 0 ? '#52c41a' : (overlayRecord.upl ?? 0) < 0 ? '#ff4d4f' : '#ffffff'
                                                        }/>
                                                }
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleMarketClosePosition(overlayRecord);
                                                }}
                                                loading={closingPositions.has(overlayRecord.posId)}
                                                style={{
                                                    color: (overlayRecord.upl ?? 0) > 0 ? '#52c41a' : (overlayRecord.upl ?? 0) < 0 ? '#ff4d4f' : '#ffffff',
                                                    display: 'flex',
                                                    justifyContent: 'center',
                                                    alignItems: 'center'
                                                }}
                                            />
                                        </Tooltip>
                                    </div>
                                </>
                            )}
                        </div>
                    )
                ) : (
                    orders.length === 0 ? (
                        <Empty
                            description={<Text style={{color: '#d9d9d9'}}>暂无委托</Text>}
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                        />
                    ) : (
                        <div ref={orderTableAreaRef} style={{position: 'relative'}}>
                            <Table
                                dataSource={orders}
                                columns={orderColumns}
                                rowKey="ordId"
                                pagination={false}
                                size="small"
                                style={{fontSize: 12, borderRadius: 0}}
                                className="mini-position-table flat-radius-table"
                                onRow={(record) => ({
                                    onMouseEnter: (e) => handleOrderRowMouseEnter(record, e),
                                    onMouseLeave: (e) => handleOrderRowMouseLeave(e),
                                    onClick: () => {
                                        const ctime = (record as any).ctime;
                                        if (!record.instId || !ctime) return;
                                        const inferredPosSide = record.posSide || (record.side === 'buy' ? 'long' : (record.side === 'sell' ? 'short' : 'net'));
                                        const priceNum = Number(record.px);
                                        setSelectedActiveItem({
                                            type: 'order',
                                            instId: record.instId,
                                            posSide: inferredPosSide,
                                            ctime,
                                            referencePrice: Number.isFinite(priceNum) ? priceNum : undefined,
                                            detail: record
                                        });
                                        setActiveChartModalVisible(true);
                                    },
                                    style: {cursor: 'pointer'}
                                })}
                            />
                            {showOrderOverlay && orderOverlayRect && orderOverlayRecord && (
                                <>
                                    <div style={{
                                        position: 'absolute',
                                        left: 0,
                                        right: 0,
                                        top: orderOverlayRect.top,
                                        height: orderOverlayRect.height,
                                        backgroundColor: 'rgba(31, 31, 31, 0.15)',
                                        backdropFilter: 'blur(2px)',
                                        pointerEvents: 'none',
                                        zIndex: 2
                                    }}/>
                                    <div
                                        ref={orderOverlayActionRef}
                                        className="bot-order-overlay-action"
                                        style={{
                                            position: 'absolute',
                                            right: 16,
                                            top: orderOverlayRect.top,
                                            height: orderOverlayRect.height,
                                            display: 'flex',
                                            alignItems: 'center',
                                            pointerEvents: 'auto',
                                            zIndex: 3
                                        }}
                                        onMouseLeave={(e) => {
                                            const relatedTarget = (e.relatedTarget as Node | null) ?? null;
                                            if (relatedTarget && hoveredOrderRowRef.current?.contains(relatedTarget)) {
                                                return;
                                            }
                                            handleOrderRowMouseLeave();
                                        }}
                                    >
                                        <Tooltip title="撤销订单">
                                            <Button
                                                type="text"
                                                shape="circle"
                                                size="large"
                                                icon={<CloseCircleOutlined style={{fontSize: '1.2em'}}/>}
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleCancelOrder(orderOverlayRecord);
                                                }}
                                                style={{
                                                    border: 'none',
                                                    background: 'transparent',
                                                    boxShadow: 'none',
                                                    display: 'flex',
                                                    justifyContent: 'center',
                                                    alignItems: 'center'
                                                }}
                                            />
                                        </Tooltip>
                                    </div>
                                </>
                            )}
                        </div>
                    )
                )}
            </Card>

            {/* 历史仓位K线图弹窗 */}
            <HistoryPositionChartModal
                visible={chartModalVisible}
                onClose={() => {
                    setChartModalVisible(false);
                    setSelectedPosition(null);
                }}
                position={selectedPosition}
                apiKeyId={apiKeyId}
            />

            <ActivePositionOrderChartModal
                visible={activeChartModalVisible}
                onClose={() => {
                    setActiveChartModalVisible(false);
                    setSelectedActiveItem(null);
                }}
                item={selectedActiveItem}
                apiKeyId={apiKeyId}
            />
        </>
    );
};

export default PositionMiniList;
