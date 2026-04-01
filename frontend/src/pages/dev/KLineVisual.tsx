import React, {useMemo} from 'react';
import LightweightCandlestickChart, {KLineBar} from '../../components/charts/LightweightCandlestickChart';

const buildBars = (): KLineBar[] => {
    const start = 1700000000000;
    const bars: KLineBar[] = [];
    let lastClose = 100;

    for (let i = 0; i < 80; i++) {
        const time = start + i * 60_000;
        const drift = Math.sin(i / 7) * 1.2;
        const open = lastClose;
        const close = open + drift;
        const high = Math.max(open, close) + 0.8;
        const low = Math.min(open, close) - 0.8;
        const volume = 1000 + i * 3;
        bars.push({time, open, high, low, close, volume, confirmed: true});
        lastClose = close;
    }

    return bars;
};

const KLineVisual: React.FC = () => {
    const bars = useMemo(() => buildBars(), []);

    return (
        <div data-testid="kline-visual-root" style={{padding: 16}}>
            <div style={{display: 'flex', gap: 16, flexWrap: 'wrap'}}>
                <div style={{width: 920, height: 520, background: '#1f1f1f', border: '1px solid #333'}}>
                    <LightweightCandlestickChart
                        data={bars}
                        height={520}
                        timeFrame="1m"
                    />
                </div>
                {/* 注：LightweightCandlestickChart 目前仅支持暗色主题 */}
            </div>
        </div>
    );
};

export default KLineVisual;