export type KLinePeriod = '1m' | '5m' | '1h' | '5h' | '1d';

export type KLineTheme =
    | 'dark'
    | 'light'
    | {
    mode: 'dark' | 'light';
    background?: string;
    text?: string;
    grid?: string;
    up?: string;
    down?: string;
};

export type KLineBar = {
    time: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
    confirmed?: boolean;
};

export type KLineTooltipPayload = {
    symbol?: string;
    period?: KLinePeriod;
    bar: KLineBar;
    index: number;
};

export type KLineZoomPayload = {
    symbol?: string;
    period?: KLinePeriod;
    deltaY: number;
    pointerX?: number;
    pointerY?: number;
};

