import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Alert, Card, Skeleton, Space, Tag, Typography} from 'antd';
import {ClockCircleOutlined, LineChartOutlined} from '@ant-design/icons';
import {InstrumentOverviewData, tradingService} from '../../../services/tradingService';
import {useCountdownTimer} from '../../../hooks/useCountdownTimer';
import {formatEffectiveDecimal, truncateToDecimalPlaces} from '../../../utils/numberFormatter';
import ChartContainer from './ChartContainer';
import './InstrumentDetailPanel.css';

const {Text} = Typography;

interface InstrumentDetailPanelProps {
    instId: string | null;
    className?: string;
    onFourHourAvgChange?: (value: number | null) => void;
    apiKeyId?: number | null;
    markPrice?: number | null;
}

// 简化的接口，直接使用 tradingService 中的 InstrumentOverviewData
// 为了向后兼容，保留 success 字段的本地处理
interface InstrumentOverview extends InstrumentOverviewData {
    success?: boolean; // 保持兼容性，可能由本地错误处理逻辑设置
}

const InstrumentDetailPanel: React.FC<InstrumentDetailPanelProps> = ({
                                                                         instId,
                                                                         className = '',
                                                                         onFourHourAvgChange,
                                                                         apiKeyId,
                                                                         markPrice
                                                                     }) => {
    const [overviewData, setOverviewData] = useState<InstrumentOverview | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [showChart, setShowChart] = useState(true);
    const [fourHourAvgChange, setFourHourAvgChange] = useState<number | null>(null);

    // 使用 ref 来跟踪正在进行的请求，避免重复调用
    const isFetchingRef = useRef<string | null>(null);

    // 使用优化的倒计时hook
    const {countdown, isSettled} = useCountdownTimer({
        timestamp: overviewData?.fundingTime,
        enabled: !!overviewData?.fundingTime,
        interval: 1000
    });

    // 格式化资金费率显示 - 使用useCallback优化，最多4位有效小数并截断
    const formatFundingRate = useCallback((rate?: number): string => {
        if (typeof rate !== 'number') return '-';
        // 使用截断函数，最多4位有效小数，不进行四舍五入
        const percentValue = rate * 100;
        const truncatedValue = truncateToDecimalPlaces(percentValue, 4);
        const formattedValue = formatEffectiveDecimal(truncatedValue, 4, 0, 4);
        return `${formattedValue}%`;
    }, []);

    // 格式化涨跌幅显示 - 使用useCallback优化，最多4位有效小数并截断
    const formatChangePercent = useCallback((change?: number | null): string => {
        if (typeof change !== 'number') return '-';
        const sign = change >= 0 ? '+' : '';
        // 使用截断函数，最多4位有效小数，不进行四舍五入
        const truncatedValue = truncateToDecimalPlaces(change, 4);
        const formattedValue = formatEffectiveDecimal(truncatedValue, 4, 0, 4);
        return `${sign}${formattedValue}%`;
    }, []);

    // 获取涨跌幅颜色类名 - 红正绿负 - 使用useCallback优化
    const getChangeClass = useCallback((change?: number | null): string => {
        if (typeof change !== 'number') return 'change-neutral';
        if (change > 0) return 'change-negative';      // 正涨跌幅显示为红色
        if (change < 0) return 'change-positive';      // 负涨跌幅显示为绿色
        return 'change-neutral';
    }, []);

    // 格式化资金费率颜色 - 红正绿负 - 使用useCallback优化
    const getFundingRateClass = useCallback((rate?: number): string => {
        if (typeof rate !== 'number') return 'funding-rate-neutral';
        if (rate > 0.0001) return 'funding-rate-negative';      // 正资金费率显示为红色
        if (rate < -0.0001) return 'funding-rate-positive';    // 负资金费率显示为绿色
        return 'funding-rate-neutral';
    }, []);

    // 获取合约概况信息 - 事件驱动的更新
    const fetchInstrumentOverview = useCallback(async (instrumentId: string) => {
        if (!instrumentId) return;

        // 防止重复请求
        if (isFetchingRef.current === instrumentId) {
            return;
        }

        isFetchingRef.current = instrumentId;
        setLoading(true);
        setError(null);

        try {
            // 验证apiKeyId
            if (!apiKeyId) {
                throw new Error('apiKeyId不能为空');
            }

            // 获取5m、4H和1D周期的数据
            const result = await tradingService.getInstrumentOverview(instrumentId, apiKeyId, ['5m', '4H', '1D']);

            // tradingService 已经处理了 ApiResponse 结构的适配
            // 直接使用返回的数据
            if (result && result.instId) {
                // 添加 success 字段用于本地处理兼容性
                const overviewWithSuccess: InstrumentOverview = {
                    ...result,
                    success: true
                };
                setOverviewData(overviewWithSuccess);

                // 回调通知父组件4H平均涨跌幅
                const fourHourVolatility = result.volatilityData?.['4H']?.averageVolatility;
                setFourHourAvgChange(fourHourVolatility || null);
                if (onFourHourAvgChange) {
                    onFourHourAvgChange(fourHourVolatility || null);
                }
            } else {
                setError('获取合约概况失败：无效的响应数据');
            }
        } catch (err: any) {
            console.error('获取合约概况失败:', err);
            setError(err.message || '获取合约概况失败');
        } finally {
            setLoading(false);
            isFetchingRef.current = null;
        }
    }, [onFourHourAvgChange, apiKeyId]);

    // 监听instId和apiKeyId变化，触发数据更新
    useEffect(() => {
        if (instId) {
            fetchInstrumentOverview(instId);
        } else {
            // 清空状态
            setOverviewData(null);
            setError(null);
            setShowChart(false);
            setFourHourAvgChange(null);

            // 回调通知父组件清空4H平均涨跌幅
            if (onFourHourAvgChange) {
                onFourHourAvgChange(null);
            }
        }
    }, [instId, onFourHourAvgChange, apiKeyId]); // 添加apiKeyId依赖，确保切换apiKey时刷新数据

    // 缓存格式化后的数据，避免每次渲染都重新计算
    const formattedData = useMemo(() => {
        if (!overviewData?.success) return null;

        const fiveMinData = overviewData.volatilityData?.['5m'];
        const fourHourData = overviewData.volatilityData?.['4H'];
        const oneDayData = overviewData.volatilityData?.['1D'];

        return {
            fiveMinAvgChange: formatChangePercent(fiveMinData?.averageVolatility),
            fourHourAvgChange: formatChangePercent(fourHourData?.averageVolatility),
            oneDayAvgChange: formatChangePercent(oneDayData?.averageVolatility),
            fundingRate: formatFundingRate(overviewData.fundingRate),
            fiveMinAvgChangeClass: getChangeClass(fiveMinData?.averageVolatility),
            fourHourAvgChangeClass: getChangeClass(fourHourData?.averageVolatility),
            oneDayAvgChangeClass: getChangeClass(oneDayData?.averageVolatility),
            fundingRateClass: getFundingRateClass(overviewData.fundingRate)
        };
    }, [overviewData, formatChangePercent, formatFundingRate, getChangeClass, getFundingRateClass]);

    // 如果没有选择合约，不显示任何内容
    if (!instId) {
        return null;
    }

    return (
        <Card
            size="small"
            className={`instrument-detail-panel compact no-header ${className}`}
            loading={false} // 我们使用自定义的加载状态
        >
            {error && (
                <Alert
                    message="数据获取失败"
                    description={error}
                    type="error"
                    style={{marginBottom: 8}}
                    closable
                    onClose={() => setError(null)}
                />
            )}

            {/* 立即显示组件框架，数据异步加载 */}
            <div className="compact-content">
                <Space size="small" style={{width: '100%', justifyContent: 'space-between', flexWrap: 'nowrap'}}>
                    {/* 5分钟平均波动 */}
                    <div className="compact-item">
                        <Text type="secondary" className="compact-label">
                            5m平均
                        </Text>
                        {loading ? (
                            <Skeleton.Input active size="small" style={{width: 60, height: 20}}/>
                        ) : (
                            <Text
                                className={`compact-change ${formattedData?.fiveMinAvgChangeClass || 'change-neutral'}`}>
                                {formattedData?.fiveMinAvgChange || '-'}
                            </Text>
                        )}
                    </div>

                    {/* 4小时平均波动 */}
                    <div className="compact-item">
                        <Text type="secondary" className="compact-label">
                            4H平均
                        </Text>
                        {loading ? (
                            <Skeleton.Input active size="small" style={{width: 60, height: 20}}/>
                        ) : (
                            <Text
                                className={`compact-change ${formattedData?.fourHourAvgChangeClass || 'change-neutral'}`}>
                                {formattedData?.fourHourAvgChange || '-'}
                            </Text>
                        )}
                    </div>

                    {/* 24小时平均波动 */}
                    <div className="compact-item">
                        <Text type="secondary" className="compact-label">
                            24H平均
                        </Text>
                        {loading ? (
                            <Skeleton.Input active size="small" style={{width: 60, height: 20}}/>
                        ) : (
                            <Text
                                className={`compact-change ${formattedData?.oneDayAvgChangeClass || 'change-neutral'}`}>
                                {formattedData?.oneDayAvgChange || '-'}
                            </Text>
                        )}
                    </div>

                    {/* 当前资金费率 */}
                    <div className="compact-item">
                        <Text type="secondary" className="compact-label">
                            资金费率
                        </Text>
                        {loading ? (
                            <Skeleton.Button active size="small" style={{width: 70, height: 28, borderRadius: 4}}/>
                        ) : (
                            <Tag
                                className={formattedData?.fundingRateClass || 'funding-rate-neutral'}
                                style={{margin: 0, fontSize: '14px', padding: '4px 12px', fontWeight: 500}}
                            >
                                {formattedData?.fundingRate || '-'}
                            </Tag>
                        )}
                    </div>

                    {/* 距离下次结算 - 实时倒计时（立即显示，无加载状态） */}
                    <div className="compact-item">
                        <Text type="secondary" className="compact-label">
                            <ClockCircleOutlined style={{marginRight: 4}}/>
                            结算倒计时
                        </Text>
                        <Text className={`compact-countdown ${isSettled ? 'settled' : 'active'}`}>
                            {countdown || '-'}
                        </Text>
                    </div>

                    {/* K线图切换 */}
                    <div className="compact-item">
                        <Text type="secondary" className="compact-label">
                            K线图
                        </Text>
                        <LineChartOutlined
                            onClick={() => setShowChart(!showChart)}
                            style={{
                                cursor: 'pointer',
                                fontSize: '16px',
                                color: showChart ? '#1890ff' : '#666',
                                transition: 'color 0.3s ease'
                            }}
                            title={showChart ? '隐藏K线图' : '显示K线图'}
                        />
                    </div>
                </Space>
            </div>

            {/* K线图容器 */}
            {showChart && instId && (
                <div className="chart-container" style={{
                    marginTop: '16px',
                    animation: 'slideIn 0.3s ease-out'
                }}>
                    {/* 使用ChartContainer组件 */}
                    <ChartContainer
                        instId={instId}
                        apiKeyId={apiKeyId || 0}
                        defaultPeriod="1m"
                        height={400}
                        limit={240}
                        markPrice={markPrice}
                        fourHourAvgChange={fourHourAvgChange}
                    />
                </div>
            )}
        </Card>
    );
};

export default React.memo(InstrumentDetailPanel);