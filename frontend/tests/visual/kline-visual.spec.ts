import {expect, test} from '@playwright/test';

test('KLineChart 视觉回归：基础页面', async ({page}) => {
    await page.goto('/__dev__/kline');
    await page.waitForSelector('[data-testid="kline-visual-root"]');
    await page.addStyleTag({content: '*{transition:none!important;animation:none!important;}'});

    const root = page.locator('[data-testid="kline-visual-root"]');
    await expect(root).toHaveScreenshot('kline-visual.png');
});

