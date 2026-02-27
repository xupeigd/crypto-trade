# 前端上线检查清单

## 构建与测试

- `npm run build:check` 通过（TypeScript + Vite build）
- `npm test` 通过（Vitest）
- `npm run e2e` 通过（Playwright 视觉回归）

## 功能回归（关键路径）

- Trading 页面 K 线可正常显示，缩放/浮窗交互正常
- Positions/Bot 等引用 KLineChart 的页面可正常显示（无白屏、无报错）
- 指标面板开关、可见性切换与图例显示正常（如使用）
- 参考线（均价/止盈止损/强平等）在对应场景展示正确（如使用）

## 运行时约束

- dev 工具页仅在开发环境可访问：`/__dev__/kline`
- K 线调试日志默认关闭，仅在开发环境且显式打开 `window.__DEBUG_KLINE__ = true` 时输出

