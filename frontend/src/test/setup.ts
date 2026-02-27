import '@testing-library/jest-dom/vitest';

const getMockRect = () => (globalThis as any).__klineMockRect as {width: number; height: number} | undefined;
const setMockRect = (width: number, height: number) => {
    (globalThis as any).__klineMockRect = {width, height};
};

(globalThis as any).__klineMockRect = {width: 600, height: 400};
(globalThis as any).__setKlineMockRect = setMockRect;

Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', {
    value: function () {
        const rect = getMockRect() ?? {width: 0, height: 0};
        return {
            x: 0,
            y: 0,
            top: 0,
            left: 0,
            right: rect.width,
            bottom: rect.height,
            width: rect.width,
            height: rect.height,
            toJSON() {
                return {};
            }
        };
    }
});
