import React, {forwardRef, useCallback, useEffect, useImperativeHandle, useState} from 'react';
import {Alert, Card, Col, Row, Select, Spin, Statistic} from 'antd';
import {BankOutlined, DollarOutlined, LockOutlined, WalletOutlined} from '@ant-design/icons';
import CexBalanceService, {BalanceSummary, CexBalance} from '../../services/cexBalanceService';

const {Option} = Select;

interface BalanceCardsProps {
    className?: string;
    selectedCex: string;
    onCexChange: (cex: string) => void;
}

export interface BalanceCardsRef {
    refreshBalance: () => Promise<void>;
}

const BalanceCards = forwardRef<BalanceCardsRef, BalanceCardsProps>(({className, selectedCex, onCexChange}, ref) => {

    // 使用函数形式的useState确保初始值正确设置
    const [cexOptions, setCexOptions] = useState<string[]>(() => {
        const initialOptions = ['ALL', 'OKX'];
        console.log('useState初始化函数执行，返回:', initialOptions);
        return initialOptions;
    });

    console.log('组件初始化，初始cexOptions:', ['ALL', 'OKX']);
    console.log('实际设置的cexOptions:', cexOptions);
    const [balanceSummary, setBalanceSummary] = useState<BalanceSummary | null>(null);
    const [balances, setBalances] = useState<CexBalance[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>('');
    const [cexOptionsError, setCexOptionsError] = useState<string>('');
    const [shouldShowSelector, setShouldShowSelector] = useState(true); // ✅ 新增：控制选择器显示

    // 获取CEX选项
    const fetchCexOptions = async () => {
        try {
            console.log('开始获取CEX选项...');
            const options = await CexBalanceService.getActiveCexNames();
            console.log('API返回的CEX选项:', options);
            // 确保options是一个数组
            const optionsArray = Array.isArray(options) ? options : [];
            console.log('设置CEX选项为:', optionsArray);

            // ✅ 新增：判断实际交易所数量(不含"ALL")
            const actualExchangeCount = optionsArray.length - 1;
            const hasMultipleExchanges = actualExchangeCount > 1;

            // ✅ 新增：设置是否显示选择器
            setShouldShowSelector(hasMultipleExchanges);

            // ✅ 新增：如果只有一个交易所，自动选择它
            if (!hasMultipleExchanges && optionsArray.length === 2) {
                const uniqueCex = optionsArray.find(cex => cex !== 'ALL');
                if (uniqueCex && onCexChange) {
                    console.log('只有一个交易所，自动选择:', uniqueCex);
                    onCexChange(uniqueCex);
                }
            }

            setCexOptions(optionsArray);
        } catch (error) {
            console.error('获取CEX选项失败:', error);
            // 即使获取失败，也保留默认的ALL和OKX选项
            console.log('使用默认CEX选项: ["ALL", "OKX"]');
            setCexOptions(['ALL', 'OKX']);
            setShouldShowSelector(true);
            setCexOptionsError('获取交易所选项失败，使用默认选项');
        }
    };

    // 获取余额数据
    const fetchBalanceData = useCallback(async () => {
        if (!selectedCex) return;

        setLoading(true);
        setError('');

        try {
            // console.log('开始获取余额数据，selectedCex:', selectedCex);
            // 并行获取余额汇总和详细数据
            const [summaryData, balancesData] = await Promise.all([
                CexBalanceService.getBalanceSummary(selectedCex),
                CexBalanceService.getLatestBalances(selectedCex)
            ]);

            console.log('获取到的余额汇总数据:', summaryData);
            console.log('获取到的余额详细数据:', balancesData);

            console.log('设置balanceSummary:', summaryData);
            console.log('设置balances:', balancesData);

            setBalanceSummary(summaryData);
            setBalances(balancesData);
        } catch (error) {
            console.error('获取余额数据失败:', error);
            setError('获取余额数据失败');
        } finally {
            setLoading(false);
        }
    }, [selectedCex]);

    // 移除useAutoRefresh，避免复杂性问题
    // 由父组件统一管理刷新逻辑

    // 暴露刷新方法给父组件
    useImperativeHandle(ref, () => ({
        refreshBalance: async () => {
            console.log('BalanceCards 暴露的refreshBalance方法被调用');
            await fetchBalanceData();
        }
    }), [fetchBalanceData]);

    // 初始化数据
    useEffect(() => {
        console.log('useEffect执行，当前cexOptions:', cexOptions);
        console.log('组件挂载，开始初始化数据');
        // 强制从API获取最新的CEX选项，即使已经有初始值
        fetchCexOptions();
    }, []);

    // 监听 selectedCex 变化，自动刷新余额数据
    useEffect(() => {
        if (selectedCex && cexOptions.length > 1) {
            console.log('selectedCex 变化，刷新余额数据:', selectedCex);
            fetchBalanceData();
        }
    }, [selectedCex, fetchBalanceData]);

    // 获取余额颜色
    const getBalanceColor = (amount: number): string => {
        if (amount > 0) return '#52c41a';
        if (amount < 0) return '#ff4d4f';
        return '#8c8c8c';
    };

    // 计算余额分布
    const getTopCurrencies = () => {
        if (!balanceSummary?.currencyDistribution) return [];

        return Object.entries(balanceSummary.currencyDistribution)
            .sort(([, a], [, b]) => b - a)
            .slice(0, 5);
    };

    // 不再显示阻塞的错误，而是在底部显示警告

    if (error && !balanceSummary) {
        return (
            <Alert
                message={error}
                type="error"
                showIcon
                action={
                    <span className="cursor-pointer text-blue-600" onClick={fetchBalanceData}>
            重试
          </span>
                }
                className={className}
            />
        );
    }

    console.log('组件渲染，当前cexOptions:', cexOptions);
    console.log('当前selectedCex:', selectedCex);
    console.log('当前balanceSummary:', balanceSummary);
    console.log('当前balances:', balances);

    return (
        <div className={className}>
            {/* ✅ 修改：CEX选择器 - 只在多交易所时显示 */}
            {shouldShowSelector && (
                <Row gutter={16} style={{marginBottom: 24}}>
                    <Col span={24}>
                        <div style={{display: 'flex', alignItems: 'center', gap: 16}}>
                            <span style={{fontWeight: 500}}>交易所：</span>
                            <Select
                                value={selectedCex}
                                onChange={onCexChange}
                                style={{width: 200}}
                                loading={loading}
                            >
                                {cexOptions.map(cex => (
                                    <Option key={cex} value={cex}>
                                        {cex === 'ALL' ? '全部交易所' : cex}
                                    </Option>
                                ))}
                            </Select>
                        </div>
                    </Col>
                </Row>
            )}

            {/* 主要余额卡片 */}
            <Row gutter={16} style={{marginBottom: 24}}>
                <Col span={6}>
                    <Card styles={{ body: { padding: '8px' } }}>
                        <Spin spinning={loading}>
                            <Statistic
                                title="总资产估值"
                                value={balanceSummary?.totalUsdValue || 0}
                                prefix={<DollarOutlined/>}
                                precision={2}
                                valueStyle={{color: '#1890ff', fontSize: '28px'}}
                                formatter={(value) => CexBalanceService.formatUsdAmount(Number(value))}
                            />
                            <div style={{fontSize: '12px', color: '#666', marginTop: '8px'}}>
                                {selectedCex === 'ALL' ? '所有交易所' : selectedCex}
                            </div>
                        </Spin>
                    </Card>
                </Col>
                <Col span={6}>
                    <Card styles={{ body: { padding: '8px' } }}>
                        <Spin spinning={loading}>
                            <Statistic
                                title="可用余额"
                                value={balanceSummary?.totalAvailableBalance || 0}
                                prefix={<WalletOutlined/>}
                                precision={2}
                                valueStyle={{color: '#52c41a', fontSize: '28px'}}
                                formatter={(value) => CexBalanceService.formatAmount(Number(value))}
                            />
                            <div style={{fontSize: '12px', color: '#666', marginTop: '8px'}}>
                                可用于交易
                            </div>
                        </Spin>
                    </Card>
                </Col>
                <Col span={6}>
                    <Card styles={{ body: { padding: '8px' } }}>
                        <Spin spinning={loading}>
                            <Statistic
                                title="冻结资产"
                                value={balanceSummary?.totalLockedBalance || 0}
                                prefix={<LockOutlined/>}
                                precision={2}
                                valueStyle={{color: '#fa8c16', fontSize: '28px'}}
                                formatter={(value) => CexBalanceService.formatAmount(Number(value))}
                            />
                            <div style={{fontSize: '12px', color: '#666', marginTop: '8px'}}>
                                质押或冻结中
                            </div>
                        </Spin>
                    </Card>
                </Col>
                <Col span={6}>
                    <Card styles={{ body: { padding: '8px' } }}>
                        <Spin spinning={loading}>
                            <Statistic
                                title="持有币种"
                                value={balanceSummary?.currencyCount || 0}
                                prefix={<BankOutlined/>}
                                valueStyle={{color: '#722ed1', fontSize: '28px'}}
                            />
                            <div style={{fontSize: '12px', color: '#666', marginTop: '8px'}}>
                                不同币种数量
                            </div>
                        </Spin>
                    </Card>
                </Col>
            </Row>


            {/* 详细余额表格 - 仅在选择了具体交易所时显示 */}
            {selectedCex !== 'ALL' && (
                <Card
                    title="账户明细"
                    extra={
                        <span style={{fontSize: '12px', color: '#666'}}>
              {balances && balances.length > 0 ? `共 ${balances.length} 个币种` : '暂无数据'}
            </span>
                    }
                >
                    {balances && balances.length > 0 ? (
                        <Row gutter={16}>
                            {balances.map((balance) => (
                                <Col span={6} key={`${balance.cexName}-${balance.currency}`} style={{marginBottom: 12}}>
                                    <div style={{
                                        padding: '8px',
                                        fontSize: '12px',
                                        backgroundColor: '#262626',
                                        borderRadius: '6px',
                                        border: '1px solid #434343'
                                    }}>
                                        <div style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            marginBottom: '4px'
                                        }}>
                                            <span>{CexBalanceService.getCurrencyDisplayName(balance.currency)}</span>
                                            <span style={{color: getBalanceColor(balance.usdValue || 0)}}>
                        {CexBalanceService.formatUsdAmount(balance.usdValue || 0)}
                      </span>
                                        </div>
                                        <div style={{color: '#666'}}>
                                            可用: {CexBalanceService.formatAmount(balance.availableBalance)} /
                                            冻结: {CexBalanceService.formatAmount(balance.lockedBalance)}
                                        </div>
                                    </div>
                                </Col>
                            ))}
                        </Row>
                    ) : (
                        <div style={{textAlign: 'center', padding: '20px', color: '#999'}}>
                            暂无账户明细数据
                        </div>
                    )}
                </Card>
            )}

            {cexOptionsError && (
                <Alert
                    message={cexOptionsError}
                    type="warning"
                    showIcon
                    closable
                    action={
                        <span className="cursor-pointer text-blue-600" onClick={fetchCexOptions}>
              重试
            </span>
                    }
                    style={{marginTop: 16}}
                />
            )}
        </div>
    );
});

BalanceCards.displayName = 'BalanceCards';

export default BalanceCards;