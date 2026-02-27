import React, {useEffect, useMemo, useRef, useState} from 'react';
import CandlestickChart from '../../../pages/trading/components/CandlestickChart';
import {candlestickDataFromKLineBars, normalizeKLineBars} from './adapters';
import {KLineBar, KLinePeriod, KLineTheme, KLineTooltipPayload, KLineZoomPayload} from './types';

type Props = {
    symbol?: string;
    period?: KLinePeriod;
    data: KLineBar[];
    theme?: KLineTheme;
    loading?: boolean;
    markPrice?: number | null;
    markPriceColor?: string;
    reverseOrder?: boolean;
    onTooltip?: (payload: KLineTooltipPayload) => void;
    onZoom?: (payload: KLineZoomPayload) => void;
    renderTooltipExtra?: (args: any) => React.ReactNode;

    indicators?: any;
    indicatorConfigs?: any;
    visibleIndicators?: Record<string, boolean>;
    onUpdateIndicatorConfig?: any;
    referenceLines?: any;
    isDebugMode?: boolean; // 调试模式
};

const getThemeMode = (theme?: KLineTheme): 'dark' | 'light' => {
    if (!theme) return 'dark';
    if (theme === 'dark' || theme === 'light') return theme;
    return theme.mode;
};

const useElementSize = (enabled: boolean) => {
    const ref = useRef<HTMLDivElement | null>(null);
    const [size, setSize] = useState({width: 0, height: 0});

    useEffect(() => {
        if (!enabled) return;
        const el = ref.current;
        if (!el) return;

        const update = () => {
            const rect = el.getBoundingClientRect();
            const width = Math.max(0, Math.floor(rect.width));
            const height = Math.max(0, Math.floor(rect.height));
            setSize((prev) => (prev.width === width && prev.height === height ? prev : {width, height}));
        };

        update();

        if (typeof ResizeObserver !== 'undefined') {
            const observer = new ResizeObserver(() => update());
            observer.observe(el);
            return () => observer.disconnect();
        }

        window.addEventListener('resize', update);
        return () => window.removeEventListener('resize', update);
    }, [enabled]);

    return {ref, size};
};

const KLineChart: React.FC<Props> = ({
                                        symbol,
                                        period,
                                        data,
                                        theme,
                                        loading,
                                        markPrice,
                                        markPriceColor,
                                        reverseOrder,
                                        onTooltip,
                                        onZoom,
                                        renderTooltipExtra,
                                        indicators,
                                        indicatorConfigs,
                                        visibleIndicators,
                                        onUpdateIndicatorConfig,
                                        referenceLines,
                                        isDebugMode = false
                                    }) => {
    const mode = getThemeMode(theme);
    const {ref, size} = useElementSize(true);

    const bars = useMemo(() => normalizeKLineBars(data), [data]);
    const candlestickData = useMemo(() => candlestickDataFromKLineBars(bars), [bars]);

    const containerStyle = useMemo(() => {
        const base: React.CSSProperties = {
            width: '100%',
            height: '100%',
            position: 'relative'
        };

        if (mode === 'light') {
            return {
                ...base,
                backgroundColor: typeof theme === 'object' ? theme.background : '#ffffff',
                color: typeof theme === 'object' ? theme.text : '#1f1f1f'
            };
        }

        return {
            ...base,
            backgroundColor: typeof theme === 'object' ? theme.background : undefined,
            color: typeof theme === 'object' ? theme.text : undefined
        };
    }, [mode, theme]);

    return (
        <div
            ref={ref}
            style={containerStyle}
            data-kline-theme={mode}
            onWheel={(e) => {
                if (!onZoom) return;
                onZoom({
                    symbol,
                    period,
                    deltaY: e.deltaY,
                    pointerX: e.clientX,
                    pointerY: e.clientY
                });
            }}
        >
            {size.width > 0 && size.height > 0 ? (
                <CandlestickChart
                    data={candlestickData}
                    width={size.width}
                    height={size.height}
                    loading={loading}
                    markPrice={markPrice}
                    markPriceColor={markPriceColor}
                    timeFrame={period as any}
                    reverseOrder={reverseOrder}
                    indicators={indicators}
                    indicatorConfigs={indicatorConfigs}
                    visibleIndicators={visibleIndicators}
                    onUpdateIndicatorConfig={onUpdateIndicatorConfig}
                    referenceLines={referenceLines}
                    isDebugMode={isDebugMode}
                    renderTooltipExtra={
                        onTooltip
                            ? (args: any) => {
                                const candle = args?.candle;
                                const dataIndex = args?.dataIndex;
                                if (candle && typeof dataIndex === 'number') {
                                    onTooltip({
                                        symbol,
                                        period,
                                        bar: {
                                            time: candle.timestamp,
                                            open: candle.open,
                                            high: candle.high,
                                            low: candle.low,
                                            close: candle.close,
                                            volume: candle.volume,
                                            confirmed: candle.confirm === 1
                                        },
                                        index: dataIndex
                                    });
                                }
                                return renderTooltipExtra ? renderTooltipExtra(args) : null;
                            }
                            : renderTooltipExtra
                    }
                />
            ) : null}
        </div>
    );
};

export default React.memo(KLineChart);
