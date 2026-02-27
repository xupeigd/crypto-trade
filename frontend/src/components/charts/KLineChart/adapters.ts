import {CandlestickData} from '../../../pages/trading/components/CandlestickChart';
import {KLineBar, KLinePeriod} from './types';

type CandleLike = {
    timestamp: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume?: number;
    confirm?: number;
};

const toNumber = (v: unknown, fallback: number): number => {
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : fallback;
};

export const normalizeKLineBars = (bars: KLineBar[]): KLineBar[] => {
    const normalized = bars
        .filter(b => b && Number.isFinite(b.time))
        .map((b) => ({
            time: toNumber(b.time, 0),
            open: toNumber(b.open, 0),
            high: toNumber(b.high, 0),
            low: toNumber(b.low, 0),
            close: toNumber(b.close, 0),
            volume: toNumber(b.volume, 0),
            confirmed: b.confirmed
        }))
        .sort((a, b) => a.time - b.time);

    return normalized;
};

export const klineBarsFromCandles = (candles: CandleLike[]): KLineBar[] => {
    return normalizeKLineBars(
        candles.map((c) => ({
            time: toNumber(c.timestamp, 0),
            open: toNumber(c.open, 0),
            high: toNumber(c.high, 0),
            low: toNumber(c.low, 0),
            close: toNumber(c.close, 0),
            volume: toNumber(c.volume, 0),
            confirmed: c.confirm === undefined ? undefined : c.confirm === 1
        }))
    );
};

export const candlestickDataFromKLineBars = (bars: KLineBar[]): CandlestickData[] => {
    const normalized = normalizeKLineBars(bars);
    return normalized.map((b) => ({
        timestamp: b.time,
        open: b.open,
        high: b.high,
        low: b.low,
        close: b.close,
        volume: b.volume,
        confirm: b.confirmed === undefined ? 1 : (b.confirmed ? 1 : 0)
    }));
};

export const timeFrameFromKLinePeriod = (period?: KLinePeriod): string | undefined => {
    if (!period) return undefined;
    return period;
};

