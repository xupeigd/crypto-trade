import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    globals: true,
    clearMocks: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['tests/**', 'node_modules/**', 'dist/**']
  },
  server: {
    port: 3000,
    // 添加SPA路由支持,刷新页面时返回index.html
    // 只代理API请求,不代理前端路由
    proxy: {
      // trading路径不代理，让SPA路由处理
      // 注意：/trading/api/* 等API请求需要单独处理，这里只匹配trading路由本身
      // 代理所有后端API请求到8080端口，不代理前端路由
      // bot/.+ 匹配所有/bot/开头的API路径（如/bot/status、/bot/trigger等）
      // 使用 (/.+)? 支持精确路径和子路径,例如: /ai-model-configs 和 /ai-model-configs/active
      '^/(bot/.+|trading/.+|cex-exchanges(/.+)?|cex-balances(/.+)?|risk-control(/.+)?|chat(/.+)?|ai-model-configs(/.+)?|cex-keys(/.+)?|data-fetch-configs(/.+)?|okx-positions(/.+)?|tasks(/.+)?|executions(/.+)?|proxy-service-configs(/.+)?|dashboard(/.+)?)': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true
  }
})
