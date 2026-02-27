import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Col, Row, Select, Typography} from 'antd';
import {
    getRealtimePrice,
    getTopNInstruments,
    InstrumentBasicInfo,
    RealtimePrice
} from '../../../services/tradingService';
import {ApiResponse} from '../../../types/common';
import {usePageTimer} from '../../../hooks/usePageTimer';
import {formatEffectiveDecimal, truncateToDecimalPlaces} from '../../../utils/numberFormatter';
import './Top30InstrumentSelector.css';

const {Text} = Typography;

interface Top30Instrument {
    instId: string;
    displayName: string;
}

interface Top30InstrumentSelectorProps {
    selectedInstrument: string;
    onInstrumentChange: (instrumentId: string) => void;
    onPriceUpdate?: (instrumentId: string, price: number) => void;
    apiKeyId: number; // 必传：用于获取指定供应商的TopN合约
    count?: number; // 可选：获取合约数量，默认30
}

const Top30InstrumentSelector: React.FC<Top30InstrumentSelectorProps> = ({
                                                                             selectedInstrument,
                                                                             onInstrumentChange,
                                                                             onPriceUpdate,
                                                                             apiKeyId,
                                                                             count = 15 // 默认30
                                                                         }) => {
    const [top30Instruments, setTop30Instruments] = useState<Top30Instrument[]>([]);
    const [realtimePrice, setRealtimePrice] = useState<RealtimePrice | null>(null);
    const [loading, setLoading] = useState(false);
    const [searchValue, setSearchValue] = useState('');
    const selectRef = useRef<any>(null);
    const [dropdownOpen, setDropdownOpen] = useState(false);

    // 创建防抖搜索值状态
    const [debouncedSearchValue, setDebouncedSearchValue] = useState('');

    // 监听搜索值变化，应用防抖
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearchValue(searchValue);
        }, 300);

        return () => clearTimeout(timer);
    }, [searchValue]);

    // 获取TopN合约列表 - 使用useCallback优化
    const fetchTopNInstruments = useCallback(async () => {
        try {
            setLoading(true);
            // 传递count和apiKeyId参数，获取指定供应商的TopN合约
            const response = await getTopNInstruments(count, apiKeyId);
            if (response && Array.isArray(response)) {
                const instruments = response.map((item: InstrumentBasicInfo) => ({
                    instId: item.instId,
                    displayName: item.displayName || item.instId // 使用后端格式化的名称
                }));
                setTop30Instruments(instruments);
            }
        } catch (error) {
            console.error('获取TopN合约失败:', error);
        } finally {
            setLoading(false);
        }
    }, [count, apiKeyId]); // 添加count和apiKeyId依赖，当参数变化时重新获取数据

    // 防抖缓存变量
    let lastFetchTime = 0;
    let lastFetchInstId = '';
    let cachedResponse: ApiResponse<RealtimePrice> | null = null;

    // 获取选中合约的实时价格 - 添加缓存和防抖机制
    const fetchRealtimePrice = useCallback(async () => {
        if (!selectedInstrument) {
            setRealtimePrice(null);
            return;
        }

        const now = Date.now();
        const cacheDuration = 3000; // 3秒缓存，与全局缓存策略保持一致

        // 防抖检查：如果是同一个合约且在缓存期内，直接返回缓存结果
        if (selectedInstrument === lastFetchInstId &&
            now - lastFetchTime < cacheDuration &&
            cachedResponse) {
            console.log('💰 使用缓存的价格数据:', selectedInstrument);
            if (cachedResponse.success && cachedResponse.data) {
                const priceData = cachedResponse.data;
                setRealtimePrice({
                    instId: priceData.instId,
                    lastPrice: priceData.lastPrice,
                    changePercent: priceData.changePercent24H || priceData.changePercent,
                    changePercent24H: priceData.changePercent24H,
                    changePercent4H: priceData.changePercent4H,
                    changePercent1H: priceData.changePercent1H,
                    updateTime: priceData.updateTime
                });

                // 通知父组件价格更新
                if (onPriceUpdate && priceData.lastPrice) {
                    onPriceUpdate(priceData.instId, priceData.lastPrice);
                }
            }
            return;
        }

        try {
            console.log('🔄 获取实时价格数据:', selectedInstrument);
            const response = await getRealtimePrice(selectedInstrument, apiKeyId);

            // 更新缓存
            lastFetchTime = now;
            lastFetchInstId = selectedInstrument;
            cachedResponse = response;

            if (response.success && response.data) {
                const priceData = response.data;
                const newPrice = {
                    instId: priceData.instId,
                    lastPrice: priceData.lastPrice,
                    changePercent: priceData.changePercent24H || priceData.changePercent,
                    changePercent24H: priceData.changePercent24H,
                    changePercent4H: priceData.changePercent4H,
                    changePercent1H: priceData.changePercent1H,
                    updateTime: priceData.updateTime
                };
                setRealtimePrice(newPrice);

                // 通知父组件价格更新
                if (onPriceUpdate && priceData.lastPrice) {
                    onPriceUpdate(priceData.instId, priceData.lastPrice);
                }
            }
        } catch (error) {
            console.error('❌ 获取实时价格失败:', error);
            // 出错时清除缓存，下次可以重试
            lastFetchTime = 0;
            cachedResponse = null;
        }
    }, [selectedInstrument, apiKeyId, onPriceUpdate]);

    // 初始化加载TopN合约
    useEffect(() => {
        fetchTopNInstruments();
    }, [fetchTopNInstruments]);

    // 使用定时器刷新选中合约的价格数据（3秒一次）
    // 错开启动时间，延迟0-1秒，避免与其他请求同时调用
    const delayedStartTime = Math.floor(Math.random() * 1000);

    const {start, isActive} = usePageTimer(
        fetchRealtimePrice,
        {
            interval: 3000, // 3秒刷新一次
            autoStart: false, // 手动控制启动时间
            enabled: !!selectedInstrument
        }
    );

    // 延迟启动定时器，错开请求时间
    useEffect(() => {
        if (selectedInstrument && !isActive) {
            const timeout = setTimeout(() => {
                console.log('🕐 延迟启动Top30InstrumentSelector定时器，延迟:', delayedStartTime, 'ms');
                start();
            }, delayedStartTime);

            return () => clearTimeout(timeout);
        }
    }, [selectedInstrument, isActive, start, delayedStartTime]);

    // 当选中的合约改变时，立即获取价格数据
    useEffect(() => {
        if (selectedInstrument) {
            fetchRealtimePrice();
        }
    }, [selectedInstrument, fetchRealtimePrice]);

    // 格式化价格显示 - 使用useCallback优化，最多4位有效小数并截断
    const formatPrice = useCallback((price: number): string => {
        // 使用我们的格式化工具函数，最多4位有效小数并截断
        return formatEffectiveDecimal(price, 4, 0, 4);
    }, []);

    // 格式化涨跌幅显示 - 使用useCallback优化，最多4位有效小数并截断
    const formatChangePercent = useCallback((changePercent: number): string => {
        // 如果changePercent为null、undefined或NaN，显示"数据不足"
        if (changePercent === null || changePercent === undefined || isNaN(changePercent)) {
            return '数据不足';
        }
        const sign = changePercent >= 0 ? '+' : '';
        // 使用截断函数，最多4位有效小数，不进行四舍五入
        const truncatedValue = truncateToDecimalPlaces(changePercent, 4);
        const formattedValue = formatEffectiveDecimal(truncatedValue, 4, 0, 4);
        return `${sign}${formattedValue}%`;
    }, []);

    // 过滤合约品种 - 使用useMemo优化，配合防抖搜索
    const filteredInstruments = useMemo(() => {
        return top30Instruments.filter(instrument =>
            instrument.displayName.toLowerCase().includes(debouncedSearchValue.toLowerCase()) ||
            instrument.instId.toLowerCase().includes(debouncedSearchValue.toLowerCase())
        );
    }, [top30Instruments, debouncedSearchValue]);

    // 自定义下拉框内容
    const dropdownRender = () => (
        <div className="instrument-dropdown">
            <div className="instrument-search">
                <input
                    type="text"
                    placeholder="搜索TopN合约品种..."
                    value={searchValue}
                    onChange={(e) => setSearchValue(e.target.value)}
                    className="search-input"
                    // 防止输入框点击关闭下拉框
                    onClick={(e) => e.stopPropagation()}
                    onMouseDown={(e) => e.stopPropagation()}
                />
            </div>
            <div className="instrument-list">
                {loading ? (
                    <div className="loading-text">加载中...</div>
                ) : filteredInstruments.length > 0 ? (
                    filteredInstruments.map((instrument) => (
                        <div
                            key={instrument.instId}
                            className="instrument-item"
                            onClick={(e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                // 调用原有的选择回调
                                onInstrumentChange(instrument.instId);
                                // 关键：通过ref直接控制下拉框关闭
                                setTimeout(() => {
                                    if (selectRef.current) {
                                        selectRef.current.blur();
                                        setDropdownOpen(false);
                                    }
                                }, 0);
                            }}
                            onMouseDown={(e) => e.stopPropagation()}
                        >
                            <Row align="middle">
                                <Col span={24}>
                                    <div className="instrument-name">
                                        {instrument.displayName}
                                    </div>
                                    <div className="instrument-id">
                                        {instrument.instId}
                                    </div>
                                </Col>
                            </Row>
                        </div>
                    ))
                ) : (
                    <div className="no-results">
                        <Text type="secondary">未找到匹配的TopN合约品种</Text>
                    </div>
                )}
            </div>
        </div>
    );


    // 缓存选中品种的基础信息，避免重复计算
    const selectedInstrumentInfo = useMemo(() => {
        if (!selectedInstrument) return null;

        const instrument = top30Instruments.find(inst => inst.instId === selectedInstrument);
        return instrument ? {
            instId: instrument.instId,
            displayName: instrument.displayName || instrument.instId
        } : null;
    }, [selectedInstrument, top30Instruments]);

    // 缓存选择器的显示内容
    const getDisplayText = useCallback(() => {
        if (!selectedInstrumentInfo) {
            return '选择TopN交易额合约';
        }

        const displayName = selectedInstrumentInfo.displayName;

        // 如果没有价格数据，仅显示合约名称
        if (!realtimePrice) {
            return displayName;
        }

        // 直接显示完整文本，通过CSS进行4部分布局
        const price = `$${formatPrice(realtimePrice.lastPrice || 0)}`;
        const change = formatChangePercent(realtimePrice.changePercent || 0);
        const realtime = isActive ? '实时' : '';

        return `${displayName} ${price} ${change} ${realtime}`;
    }, [selectedInstrumentInfo, realtimePrice, isActive, formatPrice, formatChangePercent]);

    // 获取4部分的详细数据
    const getDisplayParts = useCallback(() => {
        if (!selectedInstrumentInfo || !realtimePrice) {
            return {
                instrument: '选择TopN交易额合约',
                price: '',
                change1H: '',
                change4H: '',
                change24H: '',
                change1HClass: '',
                change4HClass: '',
                change24HClass: '',
                realtime: ''
            };
        }

        const displayName = selectedInstrumentInfo.displayName;
        const price = `$${formatPrice(realtimePrice.lastPrice)}`;

        // 辅助函数：格式化涨跌幅
        const getChangeParts = (val: number | undefined) => {
            const str = formatChangePercent(val ?? 0);
            const cls = (val ?? 0) >= 0 ? 'positive' : 'negative';
            return {str, cls};
        };

        const change1H = getChangeParts(realtimePrice.changePercent1H);
        const change4H = getChangeParts(realtimePrice.changePercent4H);
        const change24H = getChangeParts(realtimePrice.changePercent24H || realtimePrice.changePercent);

        return {
            instrument: displayName,
            price,
            change1H: change1H.str,
            change1HClass: change1H.cls,
            change4H: change4H.str,
            change4HClass: change4H.cls,
            change24H: change24H.str,
            change24HClass: change24H.cls,
            realtime: isActive ? '实时' : ''
        };
    }, [selectedInstrumentInfo, realtimePrice, isActive, formatPrice, formatChangePercent]);

    // 获取4部分的详细数据
    const displayParts = getDisplayParts();

    return (
        <div className="top30-instrument-selector">
            {/* 合约选择器 - 价格信息内嵌显示 */}
            <Select
                ref={selectRef}
                value={selectedInstrument}
                onChange={onInstrumentChange}
                placeholder="选择TopN交易额合约"
                style={{width: '100%'}}
                size="large"
                showSearch={false}
                popupRender={dropdownRender}
                className="instrument-select"
                loading={loading}
                open={dropdownOpen}
                onOpenChange={(open) => {
                    setDropdownOpen(open);
                    console.log('下拉框状态:', open ? '打开' : '关闭');
                }}
                optionLabelProp="label"
                // 修复下拉框关闭问题的关键配置
                popupMatchSelectWidth={false}
            >
                {top30Instruments.map(instrument => {
                    // 如果是选中的合约且有价格数据，显示完整信息
                    const isSelected = instrument.instId === selectedInstrument;
                    let displayLabel = instrument.displayName || instrument.instId;

                    if (isSelected && realtimePrice && selectedInstrumentInfo) {
                        displayLabel = `${instrument.displayName} $${formatPrice(realtimePrice.lastPrice)} ${formatChangePercent(realtimePrice.changePercent24H || realtimePrice.changePercent || 0)}${isActive ? ' 实时' : ''}`;
                    }

                    return (
                        <Select.Option
                            key={instrument.instId}
                            value={instrument.instId}
                            label={displayLabel}
                        >
                            <div className="instrument-option-content">
                                <div className="instrument-name">{instrument.displayName}</div>
                                <div className="instrument-id">{instrument.instId}</div>
                            </div>
                        </Select.Option>
                    );
                })}
            </Select>

            {/* 4部分布局覆盖层 - 当有价格数据时显示 */}
            {selectedInstrumentInfo && realtimePrice && (
                <div className="instrument-selector-4parts">
                    <div className="part-instrument" title={displayParts.instrument}>
                        {displayParts.instrument}
                    </div>
                    <div className="part-price">
                        {displayParts.price}
                    </div>

                    <div className={`part-change ${displayParts.change1HClass}`}>
                        <span className="time-label">1H</span>
                        <span className="change-value">{displayParts.change1H}</span>
                    </div>

                    <div className={`part-change ${displayParts.change4HClass}`}>
                        <span className="time-label">4H</span>
                        <span className="change-value">{displayParts.change4H}</span>
                    </div>

                    <div className={`part-change ${displayParts.change24HClass}`}>
                        <span className="time-label">24H</span>
                        <span className="change-value">{displayParts.change24H}</span>
                    </div>
                </div>
            )}
        </div>
    );
};

export default React.memo(Top30InstrumentSelector);
