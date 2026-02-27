import React, {useEffect, useState} from 'react';
import {
    Alert,
    Button,
    Card,
    Checkbox,
    Col,
    Form,
    InputNumber,
    Modal,
    notification,
    Row,
    Select,
    Tooltip,
    Typography
} from 'antd';
import {DollarOutlined, InfoCircleOutlined, WarningOutlined} from '@ant-design/icons';
import {ApiKey, tradingService} from '../../../services/tradingService';
import {useThrottle} from '../../../hooks/useThrottle';
import {formatEffectiveDecimal} from '../../../utils/numberFormatter';
import AmountSelector from './AmountSelector';
import Top30InstrumentSelector from './Top30InstrumentSelector';
import InstrumentDetailPanel from './InstrumentDetailPanel';
import './TradingForm.css';

const {Text} = Typography;

interface TradingFormProps {
    apiKeys: ApiKey[];
    selectedApiKey: number | null;
    onTradeSuccess: () => void;
}

const TradingForm: React.FC<TradingFormProps> = ({
                                                     apiKeys,
                                                     selectedApiKey,
                                                     onTradeSuccess
                                                 }) => {
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const [selectedInstrument, setSelectedInstrument] = useState<string>('');
    const [selectedAmount, setSelectedAmount] = useState<number>(30);
    const [orderType, setOrderType] = useState<'market' | 'limit'>('market');
    const [limitPrice, setLimitPrice] = useState<number | null>(null);
    const [takeProfitEnabled, setTakeProfitEnabled] = useState(false);
    const [stopLossEnabled, setStopLossEnabled] = useState(false);
    const [takeProfitPrice, setTakeProfitPrice] = useState<number | null>(null);
    const [stopLossPrice, setStopLossPrice] = useState<number | null>(null);
    const [takeProfitPct, setTakeProfitPct] = useState<number | null>(null);
    const [stopLossPct, setStopLossPct] = useState<number | null>(null);
    const [useTakeProfitPct, setUseTakeProfitPct] = useState(true);  // 默认使用百分比模式
    const [useStopLossPct, setUseStopLossPct] = useState(true);      // 默认使用百分比模式
    const [currentPrice, setCurrentPrice] = useState<number | null>(null);  // 当前合约的实际价格
    const [fourHourAvgChange, setFourHourAvgChange] = useState<number | null>(null);  // 4小时平均涨跌幅
    const [estimatedQuantity, setEstimatedQuantity] = useState<number>(0);  // 预估数量
    const [estimatedNotional, setEstimatedNotional] = useState<number>(0);  // 预估名义价值
    const [currentMarkPrice, setCurrentMarkPrice] = useState<number | null>(null);  // 当前标记价格

    // 价格编辑状态管理
    const [hasManuallyEditedPrice, setHasManuallyEditedPrice] = useState(false);  // 用户是否手动编辑过价格
    const [isUserEditing, setIsUserEditing] = useState(false);  // 用户是否正在编辑
    const [priceFlashClass, setPriceFlashClass] = useState('');  // 价格更新时的闪烁动画class

    // 止盈止损加载状态
    const [isCalculatingProfitLoss, setIsCalculatingProfitLoss] = useState(false);

    // 处理合约变化时清除价格和预估信息，并重置止盈止损状态
    useEffect(() => {
        if (selectedInstrument) {
            setCurrentPrice(null); // 切换合约时清除之前的价格
            setFourHourAvgChange(null); // 清除4小时平均涨跌幅数据
            setEstimatedQuantity(0); // 清除预估数量
            setEstimatedNotional(0); // 清除预估名义价值

            // 重置止盈止损为默认状态（启用）
            setTakeProfitEnabled(true);
            setStopLossEnabled(true);
            // 清除之前的价格和百分比设置
            setTakeProfitPrice(null);
            setStopLossPrice(null);
            setTakeProfitPct(null);
            setStopLossPct(null);
            // 重置为百分比模式
            setUseTakeProfitPct(true);
            setUseStopLossPct(true);

            // 重置价格编辑状态标识
            setHasManuallyEditedPrice(false);
            setIsUserEditing(false);

            // 移除直接清除限价的逻辑，让价格变化监听来处理更新
        } else {
            // 合约清空时，止盈止损也设为未选中状态
            setTakeProfitEnabled(false);
            setStopLossEnabled(false);
            // 清除所有设置
            setTakeProfitPrice(null);
            setStopLossPrice(null);
            setTakeProfitPct(null);
            setStopLossPct(null);
            // 清除限价
            setLimitPrice(null);
        }
    }, [selectedInstrument, orderType]);

    // 自动计算止盈止损百分比（止盈1.2倍，止损1.5倍）
    useEffect(() => {
        // 当合约被选中时,开始计算
        if (selectedInstrument) {
            setIsCalculatingProfitLoss(true);
        }

        if (fourHourAvgChange !== null && fourHourAvgChange !== 0) {
            const takeProfitPctCalculated = parseFloat((Math.abs(fourHourAvgChange) * 1.2).toFixed(2));
            const stopLossPctCalculated = parseFloat((Math.abs(fourHourAvgChange) * 1.5).toFixed(2));

            // 移除localStorage限制,只要百分比为null或0就自动设置
            if (takeProfitPct === null || takeProfitPct === 0) {
                setTakeProfitPct(takeProfitPctCalculated);
            }
            if (stopLossPct === null || stopLossPct === 0) {
                setStopLossPct(stopLossPctCalculated);
            }

            setIsCalculatingProfitLoss(false);
        }
    }, [fourHourAvgChange, selectedInstrument, takeProfitPct, stopLossPct]);

    // 计算预估信息
    useEffect(() => {
        // 如果没有当前价格或没有选择金额，返回默认值
        if (!currentPrice || !selectedAmount || selectedAmount <= 0) {
            setEstimatedQuantity(0);
            setEstimatedNotional(0);
            return;
        }

        // 计算预估数量（考虑3倍杠杆）
        // 市价单：数量 = (成本金额 * 杠杆倍数) / 当前价格
        // 限价单：数量 = (成本金额 * 杠杆倍数) / 委托价格（如果有）
        const lever = 3;
        const priceForCalculation = orderType === 'limit' && limitPrice ? limitPrice : currentPrice;
        const quantity = (selectedAmount * lever) / priceForCalculation;

        // 计算预估名义价值（保证金 * 杠杆倍数）
        const notional = selectedAmount * lever;

        setEstimatedQuantity(quantity || 0);
        setEstimatedNotional(notional || 0);
    }, [currentPrice, selectedAmount, orderType, limitPrice]);

    // 错误处理函数
    const handleTradingError = (error: any) => {
        console.error('下单失败:', error);

        const errorMessage = error.response?.data?.message || error.message || '下单失败，请重试';

        // 根据错误类型进行分类处理
        if (errorMessage.includes('API Key') || errorMessage.includes('50111') || errorMessage.includes('50112') || errorMessage.includes('50113')) {
            // API Key相关错误
            notification.error({
                message: 'API Key配置错误',
                description: (
                    <div>
                        <p>{errorMessage}</p>
                        <p style={{marginTop: 8, fontSize: '12px', color: '#666'}}>
                            请检查以下配置是否正确：
                        </p>
                        <ul style={{marginTop: 4, fontSize: '12px', color: '#666'}}>
                            <li>Access Key（API Key）</li>
                            <li>Secret Key（密钥）</li>
                            <li>Pass Phrase（密码）</li>
                            <li>API Key权限和IP白名单设置</li>
                        </ul>
                    </div>
                ),
                duration: 8,
                placement: 'topRight'
            });
        } else if (errorMessage.includes('Invalid signature') || errorMessage.includes('签名')) {
            // 签名错误
            notification.error({
                message: '签名验证失败',
                description: (
                    <div>
                        <p>{errorMessage}</p>
                        <p style={{marginTop: 8, fontSize: '12px', color: '#666'}}>
                            可能的原因：Secret Key不正确或时间同步问题
                        </p>
                    </div>
                ),
                duration: 6,
                placement: 'topRight'
            });
        } else if (errorMessage.includes('Insufficient') || errorMessage.includes('余额') || errorMessage.includes('保证金')) {
            // 余额不足错误
            notification.error({
                message: '账户余额不足',
                description: errorMessage,
                duration: 5,
                placement: 'topRight'
            });
        } else if (errorMessage.includes('参数') || errorMessage.includes('parameter')) {
            // 参数错误
            Modal.error({
                title: <span style={{color: '#ffffff'}}>参数错误</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        {errorMessage}
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
        } else {
            // 通用错误
            Modal.error({
                title: <span style={{color: '#ffffff'}}>错误</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        {errorMessage}
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
        }
    };

    // 显示交易确认对话框
    const showTradingConfirmDialog = async (direction: 'long' | 'short', side: 'buy' | 'sell', apiKeyData: ApiKey) => {
        // 计算委托价格
        const orderPrice = orderType === 'limit' && limitPrice ? limitPrice : currentPrice;

        // 计算名义价值
        const lever = 3;
        const notional = selectedAmount * lever;

        // 计算预估数量
        const quantity = orderPrice ? (selectedAmount * lever) / orderPrice : 0;

        // 计算止盈止损价格
        let takeProfitPriceCalculated: number | null = null;
        let stopLossPriceCalculated: number | null = null;

        if (takeProfitEnabled && takeProfitPct && orderPrice) {
            if (direction === 'long') {
                takeProfitPriceCalculated = orderPrice * (1 + takeProfitPct / 100);
            } else {
                takeProfitPriceCalculated = orderPrice * (1 - takeProfitPct / 100);
            }
        }

        if (stopLossEnabled && stopLossPct && orderPrice) {
            if (direction === 'long') {
                stopLossPriceCalculated = orderPrice * (1 - stopLossPct / 100);
            } else {
                stopLossPriceCalculated = orderPrice * (1 + stopLossPct / 100);
            }
        }

        const isLiveTrading = apiKeyData.isLiveTrading;
        const title = isLiveTrading ? '交易确认' : '交易确认';
        const riskColor = isLiveTrading ? '#ff4d4f' : '#1890ff';

        Modal.confirm({
            title: (
                <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                    {isLiveTrading && <WarningOutlined style={{color: riskColor}}/>}
                    <span style={{color: '#ffffff'}}>{title}</span>
                </div>
            ),
            icon: null,
            width: 480,
            centered: true,
            className: 'dark-theme-modal',
            content: (
                <div style={{fontSize: '14px', lineHeight: '1.6'}}>
                    {isLiveTrading && (
                        <p style={{color: riskColor, fontWeight: 'bold', marginBottom: '16px'}}>
                            ⚠️ 您正在进行实盘交易，资金将真实投入市场
                        </p>
                    )}

                    <div style={{
                        background: '#2a2a2a',
                        padding: '16px',
                        borderRadius: '8px',
                        marginBottom: '16px',
                        border: '1px solid #404040'
                    }}>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>合约品种：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>{selectedInstrument}</Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>交易方向：</Text></Col>
                            <Col><Text style={{color: direction === 'long' ? '#52c41a' : '#ff4d4f'}}>
                                {direction === 'long' ? '开多' : '开空'}
                            </Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>订单类型：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>{orderType === 'market' ? '市价单' : '限价单'}</Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>委托价格：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>
                                {orderType === 'limit' && limitPrice
                                    ? `$${limitPrice.toFixed(4)}`
                                    : orderPrice
                                        ? `$${orderPrice.toFixed(4)} (市价)`
                                        : '获取中...'
                                }
                            </Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>保证金：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>${selectedAmount}</Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>杠杆倍数：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>{lever}x</Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>预估数量：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>{quantity.toFixed(6)}</Text></Col>
                        </Row>
                        <Row justify="space-between" style={{marginBottom: '8px'}}>
                            <Col><Text style={{color: '#a0a0a0'}} strong>预估名义价值：</Text></Col>
                            <Col><Text style={{color: '#ffffff'}}>${notional}</Text></Col>
                        </Row>

                        {(takeProfitEnabled && (takeProfitPct || takeProfitPrice)) && (
                            <Row justify="space-between" style={{marginBottom: '8px'}}>
                                <Col><Text style={{color: '#a0a0a0'}} strong>止盈价格：</Text></Col>
                                <Col><Text style={{color: '#52c41a'}}>
                                    {takeProfitPrice
                                        ? `$${takeProfitPrice.toFixed(4)}`
                                        : takeProfitPriceCalculated
                                            ? `$${takeProfitPriceCalculated.toFixed(4)}`
                                            : '计算中...'
                                    }
                                    {(() => {
                                        if (takeProfitPrice && orderPrice) {
                                            const percentage = ((takeProfitPrice - orderPrice) / orderPrice) * 100;
                                            return ` (${percentage > 0 ? '+' : ''}${percentage.toFixed(2)}%)`;
                                        }
                                        return takeProfitPct && !takeProfitPrice ? ` (${takeProfitPct}%)` : '';
                                    })()}
                                </Text></Col>
                            </Row>
                        )}

                        {(stopLossEnabled && (stopLossPct || stopLossPrice)) && (
                            <Row justify="space-between">
                                <Col><Text style={{color: '#a0a0a0'}} strong>止损价格：</Text></Col>
                                <Col><Text style={{color: '#ff4d4f'}}>
                                    {stopLossPrice
                                        ? `$${stopLossPrice.toFixed(4)}`
                                        : stopLossPriceCalculated
                                            ? `$${stopLossPriceCalculated.toFixed(4)}`
                                            : '计算中...'
                                    }
                                    {(() => {
                                        if (stopLossPrice && orderPrice) {
                                            const percentage = ((stopLossPrice - orderPrice) / orderPrice) * 100;
                                            return ` (${percentage > 0 ? '+' : ''}${percentage.toFixed(2)}%)`;
                                        }
                                        return stopLossPct && !stopLossPrice ? ` (${stopLossPct}%)` : '';
                                    })()}
                                </Text></Col>
                            </Row>
                        )}
                    </div>

                    <p style={{
                        color: isLiveTrading ? riskColor : '#a0a0a0',
                        fontWeight: 'bold',
                        textAlign: 'center',
                        margin: 0
                    }}>
                        确认执行{isLiveTrading ? '合约' : '合约'}交易吗？
                    </p>
                </div>
            ),
            okText: '确认交易',
            cancelText: '取消',
            okButtonProps: {
                danger: false, // 禁用danger样式，完全使用自定义样式
                type: 'primary',
                size: 'middle',
                style: {
                    backgroundColor: direction === 'long' ? '#52c41a' : '#ff4d4f',
                    borderColor: direction === 'long' ? '#52c41a' : '#ff4d4f',
                    boxShadow: 'none',
                    color: '#ffffff'
                },
                className: direction === 'long' ? 'buy-confirm-button' : 'sell-confirm-button'
            },
            cancelButtonProps: {
                size: 'middle'
            },
            onOk: () => executeOrder(side, direction),
        });
    };

    // 处理下单
    const handleSubmit = async (direction: 'long' | 'short') => {
        // 开多使用side="buy"，开空使用side="sell"
        const side = direction === 'long' ? 'buy' : 'sell';
        if (!selectedApiKey) {
            Modal.error({
                title: <span style={{color: '#ffffff'}}>请选择API Key</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        请先选择一个API Key再进行交易
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
            return;
        }

        if (!selectedInstrument) {
            Modal.error({
                title: <span style={{color: '#ffffff'}}>请选择合约品种</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        请先选择一个合约品种再进行交易
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
            return;
        }

        const selectedApiKeyData = apiKeys.find(key => key.keyId === selectedApiKey);
        if (!selectedApiKeyData) {
            Modal.error({
                title: <span style={{color: '#ffffff'}}>API Key不存在</span>,
                content: (
                    <div style={{color: '#a0a0a0'}}>
                        所选择的API Key不存在，请重新选择
                    </div>
                ),
                centered: true,
                className: 'dark-theme-modal'
            });
            return;
        }

        // 显示交易确认对话框
        showTradingConfirmDialog(direction, side, selectedApiKeyData);
    };

    // 处理价格更新 - 使用节流优化
    const throttledPriceUpdate = useThrottle((instrumentId: string, price: number) => {
        if (instrumentId === selectedInstrument) {
            console.log('收到价格更新:', instrumentId, price);
            setCurrentPrice(price);
            setCurrentMarkPrice(price); // 同时更新标记价格
        }
    }, 1000); // 1000ms节流间隔，优化性能

    // 保持原有的函数签名以便组件使用
    const handlePriceUpdate = throttledPriceUpdate;

    // 执行下单
    const executeOrder = async (side: 'buy' | 'sell', direction: 'long' | 'short') => {
        setLoading(true);
        try {
            // 验证限价单必须设置价格
            if (orderType === 'limit' && (!limitPrice || limitPrice <= 0)) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>限价单价格无效</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            限价单必须设置有效价格
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                setLoading(false);
                return;
            }

            // 验证止盈止损设置
            if (takeProfitEnabled && !useTakeProfitPct && (!takeProfitPrice || takeProfitPrice <= 0)) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>止盈价格无效</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            请设置有效的止盈价格
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                setLoading(false);
                return;
            }
            if (stopLossEnabled && !useStopLossPct && (!stopLossPrice || stopLossPrice <= 0)) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>止损价格无效</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            请设置有效的止损价格
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                setLoading(false);
                return;
            }
            if (takeProfitEnabled && useTakeProfitPct && (!takeProfitPct || takeProfitPct <= 0)) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>止盈百分比无效</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            请设置有效的止盈百分比
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                setLoading(false);
                return;
            }
            if (stopLossEnabled && useStopLossPct && (!stopLossPct || stopLossPct <= 0)) {
                Modal.error({
                    title: <span style={{color: '#ffffff'}}>止损百分比无效</span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            请设置有效的止损百分比
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                setLoading(false);
                return;
            }

            // 计算止盈价格（如果使用百分比）
            const calculatedTakeProfitPrice = await calculateTakeProfitPrice(
                takeProfitEnabled,
                useTakeProfitPct,
                takeProfitPct,
                takeProfitPrice,
                side,
                direction
            );

            // 计算止损价格（如果使用百分比）
            const calculatedStopLossPrice = await calculateStopLossPrice(
                stopLossEnabled,
                useStopLossPct,
                stopLossPct,
                stopLossPrice,
                side,
                direction
            );

            // 调试信息：显示最终传递给API的参数
            console.log('=== 下单参数调试信息 ===');
            console.log('合约:', selectedInstrument);
            console.log('方向:', side, '(', direction, ')');
            console.log('订单类型:', orderType);
            console.log('数量:', selectedAmount);
            console.log('杠杆:', 3);
            console.log('限价:', orderType === 'limit' && limitPrice !== null ? limitPrice : '市价');
            console.log('原始止盈设置:', {
                enabled: takeProfitEnabled,
                usePercentage: useTakeProfitPct,
                percentage: takeProfitPct,
                fixedPrice: takeProfitPrice
            });
            console.log('原始止损设置:', {
                enabled: stopLossEnabled,
                usePercentage: useStopLossPct,
                percentage: stopLossPct,
                fixedPrice: stopLossPrice
            });

            // 强制调试：如果用户输入了百分比但useTakeProfitPct为false，强制修复
            let finalTakeProfitPrice = calculatedTakeProfitPrice;
            let finalStopLossPrice = calculatedStopLossPrice;

            if (takeProfitEnabled && takeProfitPct && takeProfitPct > 0 && !useTakeProfitPct) {
                console.warn('检测到止盈百分比输入但模式未切换，强制修复');
                setUseTakeProfitPct(true);
                // 重新计算
                finalTakeProfitPrice = await calculateTakeProfitPrice(
                    takeProfitEnabled, true, takeProfitPct, takeProfitPrice, side, direction
                );
            }

            if (stopLossEnabled && stopLossPct && stopLossPct > 0 && !useStopLossPct) {
                console.warn('检测到止损百分比输入但模式未切换，强制修复');
                setUseStopLossPct(true);
                // 重新计算
                finalStopLossPrice = await calculateStopLossPrice(
                    stopLossEnabled, true, stopLossPct, stopLossPrice, side, direction
                );
            }

            console.log('计算后止盈价格:', finalTakeProfitPrice);
            console.log('计算后止损价格:', finalStopLossPrice);
            console.log('==========================');

            const response = await tradingService.placeOrder({
                apiKeyId: selectedApiKey!,
                instId: selectedInstrument,
                side,
                orderType,
                amount: selectedAmount,  // 添加成本金额字段
                px: orderType === 'limit' && limitPrice !== null ? limitPrice : undefined,
                posSide: direction,
                lever: 3,
                takeProfitPrice: finalTakeProfitPrice !== null ? finalTakeProfitPrice : undefined,
                stopLossPrice: finalStopLossPrice !== null ? finalStopLossPrice : undefined,
                source: 'web' as const // 用户手动下单
            });

            if (response.data.success) {
                // 根据风控模式显示不同的成功提示
                const isManualRiskMode = response.data.data?.isManualRiskMode || false;

                Modal.success({
                    title: <span style={{color: '#ffffff'}}>
                        {isManualRiskMode ? '进入风控审核' : '下单成功'}
                    </span>,
                    content: (
                        <div style={{color: '#a0a0a0'}}>
                            {isManualRiskMode ? (
                                <>
                                    <div>订单已进入风控审核流程，请等待风控审核</div>
                                    {response.data.data?.orderId && (
                                        <div style={{marginTop: '8px'}}>订单ID: {response.data.data.orderId}</div>
                                    )}
                                </>
                            ) : (
                                <>
                                    订单ID: {response.data.data?.orderId}
                                </>
                            )}
                        </div>
                    ),
                    centered: true,
                    className: 'dark-theme-modal'
                });
                onTradeSuccess();
            } else {
                handleTradingError({response: {data: {message: response.data.message}}});
            }
        } catch (error: any) {
            handleTradingError(error);
        } finally {
            setLoading(false);
        }
    };

    // 计算止盈价格的辅助函数
    const calculateTakeProfitPrice = async (
        enabled: boolean,
        usePercentage: boolean,
        percentage: number | null,
        fixedPrice: number | null,
        side: 'buy' | 'sell',
        direction: 'long' | 'short'
    ): Promise<number | null> => {
        console.log(`止盈价格计算开始: enabled=${enabled}, usePercentage=${usePercentage}, percentage=${percentage}, fixedPrice=${fixedPrice}, direction=${direction}`);

        if (!enabled) {
            console.log('止盈未启用，返回null');
            return null;
        }

        if (!usePercentage) {
            console.log('使用固定止盈价格:', fixedPrice);
            return fixedPrice;
        }

        if (!percentage || percentage <= 0) {
            console.log('百分比无效，返回固定价格:', fixedPrice);
            return fixedPrice;
        }

        try {
            // 检查是否有实时价格
            if (currentPrice === null || currentPrice === 0) {
                throw new Error('未获取到实时价格，无法计算止盈价格。请等待价格数据加载完成后再试。');
            }

            console.log('使用组件实时价格:', currentPrice, '合约:', selectedInstrument);

            // 计算基准价格（限价单使用限价，市价单使用当前价格）
            const basePrice = orderType === 'limit' && limitPrice ? limitPrice : currentPrice;
            console.log(`基准价格: ${basePrice} (订单类型: ${orderType}, 限价: ${limitPrice})`);

            // 止盈：开多时价格上涨，开空时价格下跌
            let multiplier;
            console.log(`DEBUG: 计算前 - direction=${direction}, percentage=${percentage}, percentage/100=${percentage / 100}`);
            if (direction === 'long') {
                // 开多：止盈价格 = 基准价格 * (1 + 百分比)
                multiplier = 1 + (percentage / 100);
                console.log(`DEBUG: 开多止盈 - 基准价格=${basePrice}, multiplier=${multiplier}, 结果=${basePrice * multiplier}`);
            } else {
                // 开空：止盈价格 = 基准价格 * (1 - 百分比)
                multiplier = 1 - (percentage / 100);
                console.log(`DEBUG: 开空止盈 - 基准价格=${basePrice}, multiplier=${multiplier}, 结果=${basePrice * multiplier}`);
            }

            const calculatedPrice = basePrice * multiplier;
            console.log(`最终止盈价格计算: 方向=${direction}, 基准价格=${basePrice}, 百分比=${percentage}%, 计算价格=${calculatedPrice}`);

            return calculatedPrice;
        } catch (error) {
            console.error('计算止盈价格失败:', error);
            return fixedPrice;
        }
    };

    // 计算止损价格的辅助函数
    const calculateStopLossPrice = async (
        enabled: boolean,
        usePercentage: boolean,
        percentage: number | null,
        fixedPrice: number | null,
        side: 'buy' | 'sell',
        direction: 'long' | 'short'
    ): Promise<number | null> => {
        console.log(`止损价格计算开始: enabled=${enabled}, usePercentage=${usePercentage}, percentage=${percentage}, fixedPrice=${fixedPrice}, direction=${direction}`);

        if (!enabled) {
            console.log('止损未启用，返回null');
            return null;
        }

        if (!usePercentage) {
            console.log('使用固定止损价格:', fixedPrice);
            return fixedPrice;
        }

        if (!percentage || percentage <= 0) {
            console.log('百分比无效，返回固定价格:', fixedPrice);
            return fixedPrice;
        }

        try {
            // 检查是否有实时价格
            if (currentPrice === null || currentPrice === 0) {
                throw new Error('未获取到实时价格，无法计算止损价格。请等待价格数据加载完成后再试。');
            }

            console.log('使用组件实时价格:', currentPrice, '合约:', selectedInstrument);

            // 计算基准价格（限价单使用限价，市价单使用当前价格）
            const basePrice = orderType === 'limit' && limitPrice ? limitPrice : currentPrice;
            console.log(`基准价格: ${basePrice} (订单类型: ${orderType}, 限价: ${limitPrice})`);

            // 止损：开多时价格下跌，开空时价格上涨
            let multiplier;
            if (direction === 'long') {
                // 开多：止损价格 = 基准价格 * (1 - 百分比)
                multiplier = 1 - (percentage / 100);
                console.log(`开多止损: 基准价格=${basePrice} * (1 - ${percentage / 100}) = ${basePrice * multiplier}`);
            } else {
                // 开空：止损价格 = 基准价格 * (1 + 百分比)
                multiplier = 1 + (percentage / 100);
                console.log(`开空止损: 基准价格=${basePrice} * (1 + ${percentage / 100}) = ${basePrice * multiplier}`);
            }

            const calculatedPrice = basePrice * multiplier;
            console.log(`最终止损价格计算: 方向=${direction}, 基准价格=${basePrice}, 百分比=${percentage}%, 计算价格=${calculatedPrice}`);

            return calculatedPrice;
        } catch (error) {
            console.error('计算止损价格失败:', error);
            return fixedPrice;
        }
    };

    // 获取预估数量和名义价值
    const {quantity, notional} = {
        quantity: estimatedQuantity,
        notional: estimatedNotional
    };

    // 当切换到限价单时自动填入当前价格（仅当限价为空时）
    useEffect(() => {
        if (orderType === 'limit' && (!limitPrice || limitPrice === 0) && currentPrice && selectedInstrument) {
            setLimitPrice(currentPrice);
            // 移除notification，只保留自动填入功能
        }
    }, [orderType]);

    // 限价单模式下，价格变化时自动更新委托价格（仅在用户未手动编辑时）
    useEffect(() => {
        if (orderType === 'limit' && currentPrice && selectedInstrument &&
            !hasManuallyEditedPrice && !isUserEditing) {
            const previousPrice = limitPrice;
            setLimitPrice(currentPrice);

            // 添加价格更新视觉反馈（闪烁效果）
            if (previousPrice && previousPrice !== currentPrice) {
                setPriceFlashClass(currentPrice > previousPrice ? 'price-flash-up' : 'price-flash-down');
                setTimeout(() => setPriceFlashClass(''), 300);
            }
        }
    }, [currentPrice, selectedInstrument, orderType, hasManuallyEditedPrice, isUserEditing]);

    const selectedApiKeyData = apiKeys.find(key => key.keyId === selectedApiKey);

    return (
        <Card className="trading-form-card" title={<><DollarOutlined/> 合约交易</>}>
            <div className="trading-form">
                <Form form={form} layout="vertical">

                    {/* 实盘风险提示 */}
                    {selectedApiKeyData?.isLiveTrading && (
                        <Alert
                            message="实盘交易风险提示"
                            description="当前选择的是实盘交易模式，所有交易将使用真实资金，请谨慎操作。"
                            type="warning"
                            showIcon
                            style={{marginBottom: 16}}
                        />
                    )}

                    {/* 合约品种选择 */}
                    <Form.Item label={<><Text strong>合约品种</Text></>}>
                        <div className="instrument-selector-container">
                            {selectedApiKey ? (
                                <Top30InstrumentSelector
                                    selectedInstrument={selectedInstrument}
                                    onInstrumentChange={setSelectedInstrument}
                                    onPriceUpdate={handlePriceUpdate}
                                    apiKeyId={selectedApiKey}
                                />
                            ) : (
                                <div style={{color: 'rgba(255, 255, 255, 0.45)'}}>
                                    请先选择 API Key
                                </div>
                            )}
                        </div>
                    </Form.Item>

                    {/* 合约详细信息面板 */}
                    <InstrumentDetailPanel
                        instId={selectedInstrument}
                        onFourHourAvgChange={setFourHourAvgChange}
                        apiKeyId={selectedApiKey}
                        markPrice={currentMarkPrice}
                    />

                    {/* 订单类型 */}
                    <Form.Item label={<><Text strong>订单类型</Text></>}>
                        <Select
                            value={orderType}
                            onChange={setOrderType}
                            size="large"
                            style={{width: '100%'}}
                        >
                            <Select.Option value="market">市价单</Select.Option>
                            <Select.Option value="limit">限价单</Select.Option>
                        </Select>
                    </Form.Item>

                    {/* 限价单价格输入框 */}
                    {orderType === 'limit' && (
                        <Form.Item
                            label={
                                <>
                                    <><Text strong>委托价格</Text></>
                                    <Tooltip title="限价单必须设置的成交价格">
                                        <InfoCircleOutlined style={{marginLeft: 8, color: '#888'}}/>
                                    </Tooltip>
                                </>
                            }
                        >
                            <div style={{position: 'relative'}}>
                                <InputNumber
                                    value={limitPrice}
                                    onChange={(value) => {
                                        setLimitPrice(value);
                                        setHasManuallyEditedPrice(true);  // 标记用户已手动编辑
                                    }}
                                    onFocus={() => setIsUserEditing(true)}
                                    onBlur={() => setIsUserEditing(false)}
                                    placeholder="输入限价单价格"
                                    size="large"
                                    style={{width: '100%'}}
                                    precision={4}
                                    min={0}
                                    step={0.0001}
                                    className={priceFlashClass}
                                    formatter={(value) => value ? formatEffectiveDecimal(value, 2, 0, 4) : ''}
                                    parser={(value) => parseFloat(value!.replace(/[^\d.-]/g, '') || '0')}
                                />
                                {hasManuallyEditedPrice && currentPrice && (
                                    <Button
                                        type="link"
                                        size="small"
                                        onClick={() => {
                                            setLimitPrice(currentPrice);
                                            setHasManuallyEditedPrice(false);
                                        }}
                                        style={{
                                            position: 'absolute',
                                            right: 8,
                                            top: '50%',
                                            transform: 'translateY(-50%)',
                                            padding: '0 4px',
                                            height: 'auto',
                                            fontSize: '12px'
                                        }}
                                    >
                                        恢复市价
                                    </Button>
                                )}
                            </div>
                        </Form.Item>
                    )}

                    {/* 成本金额选择 */}
                    <Form.Item label={<><Text strong>成本金额 (USDT)</Text></>}>
                        <AmountSelector
                            selectedAmount={selectedAmount}
                            onAmountChange={setSelectedAmount}
                        />
                    </Form.Item>

                    {/* 杠杆倍数 */}
                    <Form.Item label={<><Text strong>杠杆倍数</Text></>}>
                        <div className="lever-display">
                            <Text strong style={{fontSize: '16px'}}>3x</Text>
                            <Text type="secondary" style={{marginLeft: 8}}>固定杠杆</Text>
                        </div>
                    </Form.Item>

                    {/* 止盈止损设置 */}
                    <Form.Item label={<><Text strong>止盈止损设置</Text></>}>
                        <div className="profit-loss-settings">
                            {/* 止盈设置 */}
                            <div className="profit-loss-item">
                                <div className="profit-loss-header">
                                    <Checkbox
                                        checked={takeProfitEnabled}
                                        onChange={(e) => setTakeProfitEnabled(e.target.checked)}
                                    >
                                        <Text>止盈</Text>
                                    </Checkbox>
                                    <Tooltip title="当价格上涨到指定价格或百分比时自动卖出获利">
                                        <InfoCircleOutlined style={{marginLeft: 4, color: '#888'}}/>
                                    </Tooltip>
                                </div>

                                {takeProfitEnabled && (
                                    <div className="profit-loss-controls" style={{marginTop: 12}}>
                                        <Row gutter={8}>
                                            <Col span={12}>
                                                <Checkbox
                                                    checked={useTakeProfitPct}
                                                    onChange={(e) => {
                                                        setUseTakeProfitPct(e.target.checked);
                                                        // 选择百分比时，清理固定价格
                                                        if (e.target.checked) {
                                                            setTakeProfitPrice(null);
                                                        }
                                                    }}
                                                >
                                                    百分比
                                                </Checkbox>
                                            </Col>
                                            <Col span={12}>
                                                <Checkbox
                                                    checked={!useTakeProfitPct}
                                                    onChange={(e) => {
                                                        setUseTakeProfitPct(!e.target.checked);
                                                        // 选择固定价格时，清理百分比
                                                        if (!e.target.checked) {
                                                            setTakeProfitPct(null);
                                                        }
                                                    }}
                                                >
                                                    固定价格
                                                </Checkbox>
                                            </Col>
                                        </Row>

                                        <div style={{marginTop: 8}}>
                                            {useTakeProfitPct ? (
                                                <>
                                                    <InputNumber
                                                        value={takeProfitPct}
                                                        onChange={(value) => {
                                                            setTakeProfitPct(value);
                                                            // 如果用户输入了百分比，自动切换到百分比模式并清理固定价格
                                                            if (value && value > 0) {
                                                                setUseTakeProfitPct(true);
                                                                setTakeProfitPrice(null);
                                                            }
                                                        }}
                                                        placeholder={isCalculatingProfitLoss ? "计算中..." : "止盈百分比"}
                                                        size="middle"
                                                        style={{width: '100%'}}
                                                        precision={2}
                                                        min={0.01}
                                                        step={0.01}
                                                        disabled={isCalculatingProfitLoss}
                                                        formatter={value => `${value}%`}
                                                        parser={value => parseFloat(value!.replace('%', '') || '0')}
                                                    />
                                                    {fourHourAvgChange !== null && (
                                                        <div style={{marginTop: 4, fontSize: '12px', color: '#666'}}>
                                                            💡
                                                            建议值: {parseFloat((Math.abs(fourHourAvgChange) * 1.2).toFixed(2))}%
                                                            (基于4H平均: {parseFloat(Math.abs(fourHourAvgChange).toFixed(2))}%)
                                                        </div>
                                                    )}
                                                </>
                                            ) : (
                                                <InputNumber
                                                    value={takeProfitPrice}
                                                    onChange={(value) => {
                                                        setTakeProfitPrice(value);
                                                        // 如果用户输入了固定价格，自动切换到固定价格模式并清理百分比
                                                        if (value && value > 0) {
                                                            setUseTakeProfitPct(false);
                                                            setTakeProfitPct(null);
                                                        }
                                                    }}
                                                    placeholder="止盈价格"
                                                    size="middle"
                                                    style={{width: '100%'}}
                                                    precision={4}
                                                    min={0}
                                                    step={0.0001}
                                                    formatter={(value) => value ? formatEffectiveDecimal(value, 2, 0, 4) : ''}
                                                    parser={(value) => parseFloat(value!.replace(/[^\d.-]/g, '') || '0')}
                                                />
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* 分割线 */}
                            <div style={{height: 1, backgroundColor: '#f0f0f0', margin: '16px 0'}}/>

                            {/* 止损设置 */}
                            <div className="profit-loss-item">
                                <div className="profit-loss-header">
                                    <Checkbox
                                        checked={stopLossEnabled}
                                        onChange={(e) => setStopLossEnabled(e.target.checked)}
                                    >
                                        <Text>止损</Text>
                                    </Checkbox>
                                    <Tooltip title="当价格下跌到指定价格或百分比时自动卖出限制亏损">
                                        <InfoCircleOutlined style={{marginLeft: 4, color: '#888'}}/>
                                    </Tooltip>
                                </div>

                                {stopLossEnabled && (
                                    <div className="profit-loss-controls" style={{marginTop: 12}}>
                                        <Row gutter={8}>
                                            <Col span={12}>
                                                <Checkbox
                                                    checked={useStopLossPct}
                                                    onChange={(e) => {
                                                        setUseStopLossPct(e.target.checked);
                                                        // 选择百分比时，清理固定价格
                                                        if (e.target.checked) {
                                                            setStopLossPrice(null);
                                                        }
                                                    }}
                                                >
                                                    百分比
                                                </Checkbox>
                                            </Col>
                                            <Col span={12}>
                                                <Checkbox
                                                    checked={!useStopLossPct}
                                                    onChange={(e) => {
                                                        setUseStopLossPct(!e.target.checked);
                                                        // 选择固定价格时，清理百分比
                                                        if (!e.target.checked) {
                                                            setStopLossPct(null);
                                                        }
                                                    }}
                                                >
                                                    固定价格
                                                </Checkbox>
                                            </Col>
                                        </Row>

                                        <div style={{marginTop: 8}}>
                                            {useStopLossPct ? (
                                                <>
                                                    <InputNumber
                                                        value={stopLossPct}
                                                        onChange={(value) => {
                                                            setStopLossPct(value);
                                                            // 如果用户输入了百分比，自动切换到百分比模式并清理固定价格
                                                            if (value && value > 0) {
                                                                setUseStopLossPct(true);
                                                                setStopLossPrice(null);
                                                            }
                                                        }}
                                                        placeholder={isCalculatingProfitLoss ? "计算中..." : "止损百分比"}
                                                        size="middle"
                                                        style={{width: '100%'}}
                                                        precision={2}
                                                        min={0.01}
                                                        step={0.01}
                                                        disabled={isCalculatingProfitLoss}
                                                        formatter={value => `${value}%`}
                                                        parser={value => parseFloat(value!.replace('%', '') || '0')}
                                                    />
                                                    {fourHourAvgChange !== null && (
                                                        <div style={{marginTop: 4, fontSize: '12px', color: '#666'}}>
                                                            💡
                                                            建议值: {parseFloat((Math.abs(fourHourAvgChange) * 1.5).toFixed(2))}%
                                                            (基于4H平均: {parseFloat(Math.abs(fourHourAvgChange).toFixed(2))}%)
                                                        </div>
                                                    )}
                                                </>
                                            ) : (
                                                <InputNumber
                                                    value={stopLossPrice}
                                                    onChange={(value) => {
                                                        setStopLossPrice(value);
                                                        // 如果用户输入了固定价格，自动切换到固定价格模式并清理百分比
                                                        if (value && value > 0) {
                                                            setUseStopLossPct(false);
                                                            setStopLossPct(null);
                                                        }
                                                    }}
                                                    placeholder="止损价格"
                                                    size="middle"
                                                    style={{width: '100%'}}
                                                    precision={4}
                                                    min={0}
                                                    step={0.0001}
                                                    formatter={(value) => value ? formatEffectiveDecimal(value, 2, 0, 4) : ''}
                                                    parser={(value) => parseFloat(value!.replace(/[^\d.-]/g, '') || '0')}
                                                />
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    </Form.Item>

                    {/* 预估信息 */}
                    {selectedInstrument && (
                        <Form.Item label={<><Text strong>预估信息</Text></>}>
                            <div className="estimated-info">
                                <Row justify="space-between">
                                    <Col>
                                        <Text type="secondary">预估数量：</Text>
                                    </Col>
                                    <Col>
                                        <Text strong>
                                            {quantity > 0
                                                ? quantity.toLocaleString('en-US', {
                                                    minimumFractionDigits: 2,
                                                    maximumFractionDigits: 6
                                                })
                                                : '0'
                                            }
                                        </Text>
                                    </Col>
                                </Row>
                                <Row justify="space-between" style={{marginTop: 4}}>
                                    <Col>
                                        <Text type="secondary">预估名义价值：</Text>
                                    </Col>
                                    <Col>
                                        <Text strong>
                                            ${notional > 0
                                            ? notional.toLocaleString('en-US', {
                                                minimumFractionDigits: 2,
                                                maximumFractionDigits: 2
                                            })
                                            : '0'
                                        }
                                        </Text>
                                    </Col>
                                </Row>
                                <Row justify="space-between" style={{marginTop: 4}}>
                                    <Col>
                                        <Text type="secondary" style={{fontSize: '12px'}}>
                                            保证金: ${selectedAmount.toLocaleString('en-US', {
                                            minimumFractionDigits: 2,
                                            maximumFractionDigits: 2
                                        })}
                                        </Text>
                                    </Col>
                                    <Col>
                                        <Text type="secondary" style={{fontSize: '12px'}}>
                                            杠杆: 3x
                                        </Text>
                                    </Col>
                                </Row>
                                {currentPrice && (
                                    <Row justify="space-between" style={{marginTop: 4}}>
                                        <Col>
                                            <Text type="secondary" style={{fontSize: '12px'}}>
                                                {orderType === 'limit' && limitPrice
                                                    ? `委托价格: $${limitPrice.toLocaleString('en-US', {
                                                        minimumFractionDigits: 2,
                                                        maximumFractionDigits: 4
                                                    })}`
                                                    : `当前价格: $${currentPrice.toLocaleString('en-US', {
                                                        minimumFractionDigits: 2,
                                                        maximumFractionDigits: 4
                                                    })}`
                                                }
                                            </Text>
                                        </Col>
                                        <Col>
                                            <Text type="secondary" style={{fontSize: '12px'}}>
                                                {orderType === 'limit' ? '限价单' : '市价单'}
                                            </Text>
                                        </Col>
                                    </Row>
                                )}
                            </div>
                        </Form.Item>
                    )}

                    {/* 交易按钮 */}
                    <Form.Item style={{marginBottom: 0}}>
                        <Row gutter={16}>
                            <Col span={12}>
                                <Button
                                    type="primary"
                                    size="large"
                                    block
                                    className="trading-button buy-button"
                                    loading={loading}
                                    onClick={() => handleSubmit('long')}
                                    disabled={!selectedApiKey || !selectedInstrument}
                                >
                                    开多
                                </Button>
                            </Col>
                            <Col span={12}>
                                <Button
                                    type="primary"
                                    size="large"
                                    block
                                    className="trading-button sell-button"
                                    loading={loading}
                                    onClick={() => handleSubmit('short')}
                                    disabled={!selectedApiKey || !selectedInstrument}
                                >
                                    开空
                                </Button>
                            </Col>
                        </Row>
                    </Form.Item>
                </Form>
            </div>
        </Card>
    );
};

export default React.memo(TradingForm);