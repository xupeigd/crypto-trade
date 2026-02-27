import React from 'react';
import {act, render, screen, waitFor} from '@testing-library/react';
import {describe, expect, it, vi} from 'vitest';
import KLineChart from './KLineChart';

vi.mock('../../../pages/trading/components/CandlestickChart', () => {
    return {
        default: (props: any) => {
            return (
                <div
                    data-testid="candlestick"
                    data-width={String(props.width)}
                    data-height={String(props.height)}
                    data-data-length={String(props.data?.length ?? 0)}
                    data-reverse={String(!!props.reverseOrder)}
                />
            );
        }
    };
});

describe('KLineChart', () => {
    it('数据为空时仍能渲染占位容器并传递空数组', () => {
        render(
            <div style={{width: 600, height: 400}}>
                <KLineChart data={[]} />
            </div>
        );

        const el = screen.getByTestId('candlestick');
        expect(el).toHaveAttribute('data-data-length', '0');
    });

    it('数据更新后会将新数据传递给底层渲染器', () => {
        const {rerender} = render(
            <div style={{width: 600, height: 400}}>
                <KLineChart data={[]} />
            </div>
        );

        rerender(
            <div style={{width: 600, height: 400}}>
                <KLineChart
                    data={[
                        {
                            time: 1700000000000,
                            open: 1,
                            high: 2,
                            low: 0.5,
                            close: 1.5,
                            volume: 10,
                            confirmed: true
                        }
                    ]}
                />
            </div>
        );

        const el = screen.getByTestId('candlestick');
        expect(el).toHaveAttribute('data-data-length', '1');
    });

    it('主题切换会更新 data-kline-theme', () => {
        const {container} = render(
            <div style={{width: 600, height: 400}}>
                <KLineChart data={[]} theme="light" />
            </div>
        );

        const root = container.querySelector('[data-kline-theme]');
        expect(root).toHaveAttribute('data-kline-theme', 'light');
    });

    it('窗口 resize 触发后会重新计算尺寸并传递给底层渲染器', async () => {
        const originalResizeObserver = (globalThis as any).ResizeObserver;
        (globalThis as any).ResizeObserver = undefined;

        render(
            <div style={{width: 600, height: 400}}>
                <KLineChart data={[]} />
            </div>
        );

        const el = screen.getByTestId('candlestick');
        expect(el).toHaveAttribute('data-width', '600');
        expect(el).toHaveAttribute('data-height', '400');

        act(() => {
            (globalThis as any).__setKlineMockRect?.(800, 500);
            window.dispatchEvent(new Event('resize'));
        });

        await waitFor(() => {
            const updated = screen.getByTestId('candlestick');
            expect(updated).toHaveAttribute('data-width', '800');
            expect(updated).toHaveAttribute('data-height', '500');
        });

        (globalThis as any).ResizeObserver = originalResizeObserver;
    });
});
