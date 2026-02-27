import React, {useEffect, useMemo, useState} from 'react';
import {Card, Spin} from 'antd';
import {tradingService} from '../../../services/tradingService';
import {KLineBar, KLineChart} from '../../../components/charts/KLineChart';

interface ChartContainerSimpleProps {
    instId: string;
    apiKeyId: number;
    markPrice?: number | null;
}

const ChartContainerSimple: React.FC<ChartContainerSimpleProps> = ({instId, apiKeyId, markPrice}) => {
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState<any[]>([]);
    const [error, setError] = useState<string | null>(null);
    const bars = useMemo<KLineBar[]>(() => {
        return data.map((item) => ({
            time: Number(item.timestamp),
            open: Number(item.open),
            high: Number(item.high),
            low: Number(item.low),
            close: Number(item.close),
            volume: Number(item.volume ?? 0),
            confirmed: item.confirm === undefined ? undefined : item.confirm === 1
        }));
    }, [data]);

    useEffect(() => {
        const fetchData = async () => {
            // 使用固定的合约ID进行测试
            const testInstId = instId || 'BTC-USDT-SWAP';

            setLoading(true);
            setError(null);

            try {
                console.log('Simple ChartContainer: 开始获取K线数据:', testInstId);
                const candles = await tradingService.getMarkPriceCandles(testInstId, apiKeyId, '1m', 240);
                console.log('Simple ChartContainer: 获取到K线数据:', candles);

                if (candles && candles.length > 0) {
                    // 按时间戳升序排列，确保最新的数据在右边
                    const sortedCandles = [...candles].sort((a, b) => {
                        const timeA = a.timestamp;
                        const timeB = b.timestamp;
                        return timeA - timeB; // 升序排列，从早到晚
                    });
                    setData(sortedCandles);
                    console.log('Simple ChartContainer: 数据设置成功，数据长度:', sortedCandles.length);
                    console.log('Simple ChartContainer: 时间范围:', {
                        earliest: new Date(sortedCandles[0].timestamp).toLocaleString(),
                        latest: new Date(sortedCandles[sortedCandles.length - 1].timestamp).toLocaleString()
                    });
                } else {
                    console.log('Simple ChartContainer: K线数据为空:', candles);
                    setError('未获取到数据');
                }
            } catch (err) {
                console.error('Simple ChartContainer: 获取K线数据失败:', err);
                setError('获取K线数据失败: ' + (err as Error).message);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [instId]);

    return (
        <Card title={`${instId} K线图`} style={{backgroundColor: '#1f1f1f', border: 'none'}}
              styles={{
                  header: {padding: '8px 16px'},
                  body: {padding: '8px'}
              }}>
            <Spin spinning={loading}>
                {error && (
                    <div style={{color: '#ff4d4f', textAlign: 'center', padding: '20px'}}>
                        错误: {error}
                    </div>
                )}
                {data.length > 0 && (
                    <div style={{width: '100%', height: 420}}>
                        <KLineChart
                            symbol={instId}
                            period="1m"
                            data={bars}
                            loading={loading}
                            markPrice={markPrice}
                        />
                    </div>
                )}
                {!error && data.length === 0 && (
                    <div style={{color: '#999', textAlign: 'center', padding: '20px'}}>
                        暂无K线数据
                    </div>
                )}
            </Spin>
        </Card>
    );
};

export default ChartContainerSimple;
