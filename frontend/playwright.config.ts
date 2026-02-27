import {defineConfig} from '@playwright/test';

export default defineConfig({
    testDir: './tests',
    retries: 0,
    reporter: [['list']],
    projects: [
        {
            name: 'chrome',
            use: {
                browserName: 'chromium',
                channel: 'chrome'
            }
        }
    ],
    use: {
        baseURL: 'http://127.0.0.1:5176',
        viewport: {width: 1280, height: 720},
        timezoneId: 'UTC',
    },
    expect: {
        toHaveScreenshot: {
            maxDiffPixelRatio: 0.01
        }
    },
    webServer: {
        command: 'npm run dev -- --host 127.0.0.1 --port 5176',
        url: 'http://127.0.0.1:5176',
        reuseExistingServer: true,
        timeout: 120000
    }
});
