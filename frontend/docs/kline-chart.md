# KLineChart（统一K线组件）

## 目标

- 用一个统一的 React 组件对外提供 K 线能力，内部复用现有 CandlestickChart 渲染逻辑
- 降低页面之间重复实现与后续维护成本

## 代码位置

- 组件入口：`src/components/charts/KLineChart/index.ts`
- 组件实现：`src/components/charts/KLineChart/KLineChart.tsx`
- 类型定义：`src/components/charts/KLineChart/types.ts`
- 数据适配：`src/components/charts/KLineChart/adapters.ts`
- 颜色导出：`src/components/charts/KLineChart/colors.ts`

## 使用方式

```tsx
import React from 'react';
import {KLineChart, KLineBar} from '@/components/charts/KLineChart';

const bars: KLineBar[] = [
  {time: 1700000000000, open: 1, high: 2, low: 0.5, close: 1.5, volume: 10, confirmed: true},
];

export default function Demo() {
  return (
    <div style={{width: 900, height: 520}}>
      <KLineChart symbol="BTC-USDT" period="1m" data={bars} />
    </div>
  );
}
```

## Props（摘要）

- data：`KLineBar[]`（必须）
- symbol：交易对（如 "BTC-USDT"），用于事件回调
- period：周期类型，值为 `'1m' | '5m' | '1h' | '5h' | '1d'`
- theme：`dark | light | {mode, background?, text?}`
- loading/markPrice/markPriceColor/reverseOrder：传递给底层图表
- indicators/indicatorConfigs/visibleIndicators/onUpdateIndicatorConfig/referenceLines：兼容现有指标与参考线能力
- onTooltip/onZoom：对外暴露浮窗与滚轮缩放事件（可选）
- renderTooltipExtra：自定义渲染 tooltip 额外内容
- isDebugMode：调试模式开关，开启后输出详细日志

## 调试

- K 线相关调试日志默认关闭；可通过以下方式开启：
  - 在浏览器控制台执行：`window.__DEBUG_KLINE__ = true`
  - 或通过组件属性 `isDebugMode={true}` 开启

## 测试

- 单元测试：`npm test`
- 视觉回归（需要本机可用 Chrome 渠道）：`npm run e2e` / 更新快照：`npm run e2e:update`
- 稳定对照页（仅 dev）：`/__dev__/kline`

