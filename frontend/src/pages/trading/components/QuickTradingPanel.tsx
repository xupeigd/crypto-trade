import React, {useEffect, useRef, useState} from 'react';
import {Button, Checkbox, message, Modal, Select} from 'antd';
import {tradingService} from '../../../services/tradingService';
import {formatEffectiveDecimal} from '../../../utils/numberFormatter';
import './ChartModalEnhanced.css';

interface QuickTradingPanelProps {
    currentPrice?: number;
    priceChange?: number;
    instId?: string;
    fourHourAvgChange?: number; // 4小时平均涨跌幅
    onTradeSuccess?: () => void;
}

interface TradeCalculation {
    contract: string;
    direction: 'long' | 'short' | null;
    amount: number;
    leverage: number;
    orderType: 'market' | 'limit';
    limitPrice?: number;
    estimatedQuantity?: number;
    estimatedNotional?: number;
    estimatedFee?: number;
    liquidationPrice?: number;
    riskWarning?: string;
}

const QuickTradingPanel: React.FC<QuickTradingPanelProps> = ({
                                                                 currentPrice,
                                                                 priceChange,
                                                                 instId = 'ETH-USDT-SWAP',
                                                                 fourHourAvgChange,
                                                                 onTradeSuccess
                                                             }) => {
    // 交易状态
    const [orderType, setOrderType] = useState<'market' | 'limit'>('market');
    const [selectedAmount, setSelectedAmount] = useState<number>(20);
    const [direction, setDirection] = useState<'long' | 'short' | null>(null);
    const [limitPrice, setLimitPrice] = useState<number>(currentPrice || 0);
    const [leverage, setLeverage] = useState<number>(3); // 固定3倍杠杆

    // 价格编辑状态管理
    const [hasManuallyEditedPrice, setHasManuallyEditedPrice] = useState(false);  // 用户是否手动编辑过价格
    const [isUserEditing, setIsUserEditing] = useState(false);  // 用户是否正在编辑
    const [priceFlashClass, setPriceFlashClass] = useState('');  // 价格更新时的闪烁动画class

    // 止盈止损状态（简化版，只支持百分比）
    const [takeProfitEnabled, setTakeProfitEnabled] = useState(true);
    const [stopLossEnabled, setStopLossEnabled] = useState(true);
    const [takeProfitPct, setTakeProfitPct] = useState<number | null>(null);
    const [stopLossPct, setStopLossPct] = useState<number | null>(null);

    // 实时计算的止盈止损价格
    const [calculatedPrices, setCalculatedPrices] = useState({
        longTakeProfit: null as number | null,
        shortTakeProfit: null as number | null,
        longStopLoss: null as number | null,
        shortStopLoss: null as number | null
    });

    // 预期盈亏金额
    const [expectedAmounts, setExpectedAmounts] = useState({
        longTakeProfitAmount: null as number | null,
        shortTakeProfitAmount: null as number | null,
        longStopLossAmount: null as number | null,
        shortStopLossAmount: null as number | null
    });

    // 交易加载状态
    const [isTrading, setIsTrading] = useState(false);

    // 使用ref来跟踪当前值，避免循环依赖
    const takeProfitPctRef = useRef(takeProfitPct);
    const stopLossPctRef = useRef(stopLossPct);

    // 更新ref值
    useEffect(() => {
        takeProfitPctRef.current = takeProfitPct;
    }, [takeProfitPct]);

    useEffect(() => {
        stopLossPctRef.current = stopLossPct;
    }, [stopLossPct]);

    // 计算结果
    const [calculation, setCalculation] = useState<TradeCalculation>({
        contract: instId,
        direction: null,
        amount: selectedAmount,
        leverage: leverage,
        orderType: 'market',
        riskWarning: '实盘风险较高，请谨慎操作'
    });

    // 成本金额选项
    const amountOptions = [20, 30, 50];

    // 杠杆选项
    const leverageOptions = [
        {value: 2, label: '2x'},
        {value: 3, label: '3x'},
        {value: 5, label: '5x'}
    ];

    // 更新价格变化（仅在用户未手动编辑时）
    useEffect(() => {
        if (currentPrice && orderType === 'limit' && !hasManuallyEditedPrice && !isUserEditing) {
            const previousPrice = limitPrice;
            setLimitPrice(currentPrice);

            // 添加价格更新视觉反馈（闪烁效果）
            if (previousPrice && previousPrice !== currentPrice) {
                setPriceFlashClass(currentPrice > previousPrice ? 'price-flash-up' : 'price-flash-down');
                setTimeout(() => setPriceFlashClass(''), 300);
            }
        }
    }, [currentPrice, orderType, hasManuallyEditedPrice, isUserEditing]);

    // 智能默认值计算：基于4小时平均涨跌幅自动设置止盈止损百分比
    useEffect(() => {
        if (fourHourAvgChange && fourHourAvgChange !== 0) {
            const absChange = Math.abs(fourHourAvgChange);
            const takeProfitDefault = parseFloat((absChange * 1.2).toFixed(2));
            const stopLossDefault = parseFloat((absChange * 1.5).toFixed(2));

            // 当启用止盈且没有设置值时，自动设置默认值
            if (takeProfitEnabled && (!takeProfitPctRef.current || takeProfitPctRef.current === 0)) {
                setTakeProfitPct(takeProfitDefault);
                console.log(`智能设置止盈百分比: ${takeProfitDefault}% (基于4H平均: ${absChange.toFixed(2)}%)`);
            }

            // 当启用止损且没有设置值时，自动设置默认值
            if (stopLossEnabled && (!stopLossPctRef.current || stopLossPctRef.current === 0)) {
                setStopLossPct(stopLossDefault);
                console.log(`智能设置止损百分比: ${stopLossDefault}% (基于4H平均: ${absChange.toFixed(2)}%)`);
            }
        }
    }, [fourHourAvgChange, takeProfitEnabled, stopLossEnabled]);

    // 实时计算止盈止损价格和预期金额
    useEffect(() => {
        if (!currentPrice) return;

        const newPrices = {
            longTakeProfit: takeProfitEnabled && takeProfitPct && takeProfitPct > 0
                ? currentPrice * (1 + takeProfitPct / 100)
                : null,
            shortTakeProfit: takeProfitEnabled && takeProfitPct && takeProfitPct > 0
                ? currentPrice * (1 - takeProfitPct / 100)
                : null,
            longStopLoss: stopLossEnabled && stopLossPct && stopLossPct > 0
                ? currentPrice * (1 - stopLossPct / 100)
                : null,
            shortStopLoss: stopLossEnabled && stopLossPct && stopLossPct > 0
                ? currentPrice * (1 + stopLossPct / 100)
                : null
        };

        // 计算预期盈亏金额（基于保证金×杠杆×百分比）
        const totalValue = selectedAmount * leverage;
        const newAmounts = {
            longTakeProfitAmount: takeProfitEnabled && takeProfitPct ?
                totalValue * (takeProfitPct / 100) : null,
            shortTakeProfitAmount: takeProfitEnabled && takeProfitPct ?
                totalValue * (takeProfitPct / 100) : null,
            longStopLossAmount: stopLossEnabled && stopLossPct ?
                totalValue * (stopLossPct / 100) : null,
            shortStopLossAmount: stopLossEnabled && stopLossPct ?
                totalValue * (stopLossPct / 100) : null
        };

        setCalculatedPrices(newPrices);
        setExpectedAmounts(newAmounts);
    }, [currentPrice, takeProfitEnabled, stopLossEnabled, takeProfitPct, stopLossPct, selectedAmount, leverage]);

    // 实时计算交易信息
    useEffect(() => {
        calculateTradeInfo();
    }, [direction, selectedAmount, orderType, limitPrice, currentPrice, takeProfitEnabled, stopLossEnabled, takeProfitPct, stopLossPct, leverage]);

    // 计算交易信息
    const calculateTradeInfo = () => {
        if (!direction || !currentPrice) return;

        const price = orderType === 'market' ? currentPrice : limitPrice;
        const estimatedQuantity = (selectedAmount * leverage) / price; // 动态杠杆
        const estimatedNotional = selectedAmount;
        const estimatedFee = estimatedNotional * 0.0005; // 0.05% 手续费
        const liquidationPrice = calculateLiquidationPrice(price, direction, leverage);

        setCalculation({
            contract: instId,
            direction,
            amount: selectedAmount,
            leverage: leverage,
            orderType,
            limitPrice: orderType === 'limit' ? limitPrice : undefined,
            estimatedQuantity,
            estimatedNotional,
            estimatedFee,
            liquidationPrice,
            riskWarning: selectedAmount >= 50 ? '大额交易风险高，请谨慎操作' : '实盘交易风险高，请谨慎操作'
        });
    };

    // 计算强平价格
    const calculateLiquidationPrice = (price: number, dir: 'long' | 'short', leverage: number): number => {
        const maintenanceMarginRate = 0.005; // 维持保证金率
        if (dir === 'long') {
            return price * (1 - 1 / leverage + maintenanceMarginRate);
        } else {
            return price * (1 + 1 / leverage - maintenanceMarginRate);
        }
    };


    // 处理真实交易
    const handleTrade = async (tradeDirection: 'long' | 'short') => {
        if (!currentPrice || !instId) {
            message.error('交易参数不完整');
            return;
        }

        setIsTrading(true);
        setDirection(tradeDirection);

        try {
            // 构建交易请求
            const takeProfitPx = takeProfitEnabled
                ? calculatedPrices[tradeDirection === 'long' ? 'longTakeProfit' : 'shortTakeProfit']
                : undefined;
            const stopLossPx = stopLossEnabled
                ? calculatedPrices[tradeDirection === 'long' ? 'longStopLoss' : 'shortStopLoss']
                : undefined;

            const orderRequest = {
                apiKeyId: 1, // TODO: 从用户配置或上下文获取API Key ID
                instId,
                side: (tradeDirection === 'long' ? 'buy' : 'sell') as 'buy' | 'sell',
                orderType,
                amount: selectedAmount,
                lever: leverage,
                px: orderType === 'limit' ? limitPrice : undefined,
                takeProfitPrice: takeProfitPx === null ? undefined : takeProfitPx,
                stopLossPrice: stopLossPx === null ? undefined : stopLossPx,
                posSide: tradeDirection,
                source: 'web' as const // 用户手动下单
            };

            console.log('执行交易:', orderRequest);

            // 调用真实交易接口
            const response = await tradingService.placeOrder(orderRequest);
            console.log('交易响应:', response);

            const result = response.data;
            console.log('交易结果:', result);

            if (result.success) {
                // 检查风控模式
                const isManualRiskMode = result.data?.isManualRiskMode || false;
                const orderId = result.data?.orderId || (result as any).orderId;

                if (isManualRiskMode) {
                    // 风控审核模式 - 使用Modal显示
                    Modal.success({
                        title: <span style={{color: '#ffffff'}}>
                            进入风控审核
                        </span>,
                        content: (
                            <div style={{color: '#a0a0a0'}}>
                                <div>订单已进入风控审核流程，请等待风控审核</div>
                                {orderId && (
                                    <div style={{marginTop: '8px'}}>订单ID: {orderId}</div>
                                )}
                            </div>
                        ),
                        centered: true,
                        className: 'dark-theme-modal'
                    });
                } else {
                    // 自动通过模式 - 使用Message显示
                    message.success({
                        content: `${tradeDirection === 'long' ? '开多' : '开空'}订单提交成功！${orderId ? `订单ID: ${orderId}` : ''}`,
                        duration: 5,
                        style: {
                            zIndex: 9999
                        }
                    });
                }

                onTradeSuccess?.();
            } else {
                message.error({
                    content: `订单提交失败: ${result.message || '未知错误'}`,
                    duration: 5,
                    style: {
                        zIndex: 9999
                    }
                });
            }
        } catch (error: any) {
            console.error('交易执行失败:', error);
            console.error('错误详情:', {
                message: error.message,
                status: error.response?.status,
                data: error.response?.data
            });

            // 根据错误类型显示不同的提示
            if (error.response?.status === 401) {
                message.error({
                    content: '认证失败，请检查API密钥配置',
                    duration: 5,
                    style: {
                        zIndex: 9999
                    }
                });
            } else if (error.response?.status === 403) {
                message.error({
                    content: '权限不足，请检查API密钥权限',
                    duration: 5,
                    style: {
                        zIndex: 9999
                    }
                });
            } else {
                message.error({
                    content: `网络错误: ${error.message || '请稍后重试'}`,
                    duration: 5,
                    style: {
                        zIndex: 9999
                    }
                });
            }
        } finally {
            setIsTrading(false);
        }
    };

    return (
        <div className="quick-trading-area">
            {/* 6行信息展示面板 - 纯信息显示 */}
            <div className="info-display-section">
                {/* 第1行：订单类型 */}
                <div className="info-row">
                    <div className="info-label">订单类型</div>
                    <div className="info-value">
                        {orderType === 'market' ? '市价单' : '限价单'}
                    </div>
                </div>

                {/* 第2行：委托价格 */}
                <div className="info-row">
                    <div className="info-label">委托价格</div>
                    <div className="info-value">
                        {orderType === 'limit' ? `${limitPrice.toFixed(2)}` : '市价'}
                    </div>
                </div>

                {/* 第3行：保证金数量 */}
                <div className="info-row">
                    <div className="info-label">保证金数量</div>
                    <div className="info-value">
                        {selectedAmount}
                    </div>
                </div>

                {/* 第4行：杠杆 */}
                <div className="info-row">
                    <div className="info-label">杠杆</div>
                    <div className="info-value">
                        {leverage}x
                    </div>
                </div>

                {/* 第5行：止盈价格（左右两列） */}
                <div className="info-row two-columns">
                    <div className="info-column">
                        <div className="info-label">开多止盈</div>
                        <div className="info-value positive">
                            {calculatedPrices.longTakeProfit && expectedAmounts.longTakeProfitAmount
                                ? (
                                    <div>
                                        <div className="price-line">${calculatedPrices.longTakeProfit.toFixed(2)}</div>
                                        <div
                                            className="amount-line">+${expectedAmounts.longTakeProfitAmount.toFixed(2)}</div>
                                    </div>
                                )
                                : '--'
                            }
                        </div>
                    </div>
                    <div className="info-column">
                        <div className="info-label">开空止盈</div>
                        <div className="info-value positive">
                            {calculatedPrices.shortTakeProfit && expectedAmounts.shortTakeProfitAmount
                                ? (
                                    <div>
                                        <div className="price-line">${calculatedPrices.shortTakeProfit.toFixed(2)}</div>
                                        <div
                                            className="amount-line">+${expectedAmounts.shortTakeProfitAmount.toFixed(2)}</div>
                                    </div>
                                )
                                : '--'
                            }
                        </div>
                    </div>
                </div>

                {/* 第6行：止损价格（左右两列） */}
                <div className="info-row two-columns">
                    <div className="info-column">
                        <div className="info-label">开多止损</div>
                        <div className="info-value negative">
                            {calculatedPrices.longStopLoss && expectedAmounts.longStopLossAmount
                                ? (
                                    <div>
                                        <div className="price-line">${calculatedPrices.longStopLoss.toFixed(2)}</div>
                                        <div
                                            className="amount-line">-${expectedAmounts.longStopLossAmount.toFixed(2)}</div>
                                    </div>
                                )
                                : '--'
                            }
                        </div>
                    </div>
                    <div className="info-column">
                        <div className="info-label">开空止损</div>
                        <div className="info-value negative">
                            {calculatedPrices.shortStopLoss && expectedAmounts.shortStopLossAmount
                                ? (
                                    <div>
                                        <div className="price-line">${calculatedPrices.shortStopLoss.toFixed(2)}</div>
                                        <div
                                            className="amount-line">-${expectedAmounts.shortStopLossAmount.toFixed(2)}</div>
                                    </div>
                                )
                                : '--'
                            }
                        </div>
                    </div>
                </div>
            </div>

            {/* 交易控制面板 - 保持原有结构 */}
            <div className="trading-controls-section">
                {/* 第1行：订单类型选择 */}
                <div className="control-row order-type-row">
                    <label className="control-label">类型</label>
                    <div className="control-content">
                        <div className="order-type-selector">
                            <button
                                className={orderType === 'market' ? 'active' : ''}
                                onClick={() => {
                                    setOrderType('market');
                                    setHasManuallyEditedPrice(false);  // 重置编辑状态
                                    setIsUserEditing(false);
                                }}
                            >
                                市价单
                            </button>
                            <button
                                className={orderType === 'limit' ? 'active' : ''}
                                onClick={() => {
                                    setOrderType('limit');
                                    setHasManuallyEditedPrice(false);  // 重置编辑状态
                                    setIsUserEditing(false);
                                }}
                            >
                                限价单
                            </button>
                        </div>
                    </div>
                </div>

                {/* 第2行：委托价格 (仅限价单显示) */}
                {orderType === 'limit' && (
                    <div className="control-row limit-price-row">
                        <label className="control-label">委托价</label>
                        <div className="control-content" style={{position: 'relative'}}>
                            <input
                                type="number"
                                value={limitPrice}
                                onChange={(e) => {
                                    setLimitPrice(Number(e.target.value));
                                    setHasManuallyEditedPrice(true);  // 标记用户已手动编辑
                                }}
                                onFocus={() => setIsUserEditing(true)}
                                onBlur={(e) => {
                                    setIsUserEditing(false);
                                    // 失焦时格式化显示
                                    const numValue = Number(e.target.value);
                                    if (!isNaN(numValue) && numValue > 0) {
                                        const formatted = formatEffectiveDecimal(numValue, 2, 0, 4);
                                        setLimitPrice(parseFloat(formatted));
                                    }
                                }}
                                className={`limit-price-input ${priceFlashClass}`}
                                placeholder="输入限价"
                                step="0.0001"
                                style={{textAlign: 'right'}}
                            />
                            {hasManuallyEditedPrice && currentPrice && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        setLimitPrice(currentPrice);
                                        setHasManuallyEditedPrice(false);
                                    }}
                                    style={{
                                        position: 'absolute',
                                        right: 8,
                                        top: '50%',
                                        transform: 'translateY(-50%)',
                                        background: 'transparent',
                                        border: 'none',
                                        color: '#1890ff',
                                        cursor: 'pointer',
                                        fontSize: '12px',
                                        padding: '0 4px'
                                    }}
                                >
                                    恢复市价
                                </button>
                            )}
                        </div>
                    </div>
                )}

                {/* 第3行：成本金额选择 */}
                <div className="control-row amount-row">
                    <label className="control-label">成本</label>
                    <div className="control-content">
                        <div className="amount-selector">
                            {amountOptions.map((amount) => (
                                <button
                                    key={amount}
                                    className={selectedAmount === amount ? 'active' : ''}
                                    onClick={() => setSelectedAmount(amount)}
                                >
                                    {amount}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>

                {/* 第4行：杠杆选择器 */}
                <div className="control-row leverage-row">
                    <label className="control-label">杠杆</label>
                    <div className="control-content">
                        <Select
                            value={leverage}
                            onChange={(value) => setLeverage(value)}
                            size="small"
                            style={{width: '100%'}}
                            options={leverageOptions}
                            disabled
                        />
                    </div>
                </div>

                {/* 第5行：止盈设置 */}
                <div className="control-row take-profit-row">
                    <label className="control-label">止盈</label>
                    <div className="control-content">
                        <div className="profit-loss-control" data-enabled={takeProfitEnabled}>
                            <div className="profit-loss-header">
                                <Checkbox
                                    checked={takeProfitEnabled}
                                    onChange={(e) => setTakeProfitEnabled(e.target.checked)}
                                >
                                </Checkbox>
                            </div>
                            {takeProfitEnabled && (
                                <div className="profit-loss-input">
                                    <div className="percentage-input-wrapper">
                                        <input
                                            type="number"
                                            value={takeProfitPct || ''}
                                            onChange={(e) => {
                                                const value = Number(e.target.value);
                                                setTakeProfitPct(value > 0 ? value : null);
                                            }}
                                            placeholder="止盈百分比"
                                            step="0.01"
                                            min="0.01"
                                        />
                                        <span className="percentage-sign">%</span>
                                    </div>
                                    {/*{fourHourAvgChange && fourHourAvgChange !== 0 && (*/}
                                    {/*    <div style={{marginTop: '4px', fontSize: '12px', color: 'rgb(102, 102, 102)'}}>*/}
                                    {/*        💡 建议值: {parseFloat((Math.abs(fourHourAvgChange) * 1.2).toFixed(2))}% (基于4H平均: {Math.abs(fourHourAvgChange).toFixed(2)}%)*/}
                                    {/*    </div>*/}
                                    {/*)}*/}
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* 第6行：止损设置 */}
                <div className="control-row stop-loss-row">
                    <label className="control-label">止损</label>
                    <div className="control-content">
                        <div className="profit-loss-control" data-enabled={stopLossEnabled}>
                            <div className="profit-loss-header">
                                <Checkbox
                                    checked={stopLossEnabled}
                                    onChange={(e) => setStopLossEnabled(e.target.checked)}
                                >
                                </Checkbox>
                            </div>
                            {stopLossEnabled && (
                                <div className="profit-loss-input">
                                    <div className="percentage-input-wrapper">
                                        <input
                                            type="number"
                                            value={stopLossPct || ''}
                                            onChange={(e) => {
                                                const value = Number(e.target.value);
                                                setStopLossPct(value > 0 ? value : null);
                                            }}
                                            placeholder="止损百分比"
                                            step="0.01"
                                            min="0.01"
                                        />
                                        <span className="percentage-sign">%</span>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* 交易按钮 */}
                <div className="trading-buttons">
                    <Button
                        type="primary"
                        className="buy-button"
                        loading={isTrading && direction === 'long'}
                        onClick={() => handleTrade('long')}
                        style={{
                            opacity: direction === 'long' ? 1 : 0.7,
                            transform: direction === 'long' ? 'scale(1.02)' : 'scale(1)',
                            backgroundColor: '#52c41a',
                            borderColor: '#52c41a'
                        }}
                    >
                        {isTrading && direction === 'long' ? '交易中...' : '开多'}
                    </Button>
                    <Button
                        type="primary"
                        className="sell-button"
                        loading={isTrading && direction === 'short'}
                        onClick={() => handleTrade('short')}
                        danger
                        style={{
                            opacity: direction === 'short' ? 1 : 0.7,
                            transform: direction === 'short' ? 'scale(1.02)' : 'scale(1)',
                            backgroundColor: '#ff4d4f',
                            borderColor: '#ff4d4f'
                        }}
                    >
                        {isTrading && direction === 'short' ? '交易中...' : '开空'}
                    </Button>
                </div>

                {/* 风险提示 */}
                <div className="risk-warning">
                    ⚠️ {calculation.riskWarning}
                </div>
            </div>
        </div>
    );
};

export default QuickTradingPanel;
