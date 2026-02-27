# AI 交易机器人指南

本指南介绍如何使用系统的 AI 交易机器人进行智能对话式交易。

## 访问入口

**页面路径**: `/bot`

![AI交易机器人页面](../pics/user-gudie-bot-01.png)

## 页面布局

系统采用对话式交易界面，分为上侧，下侧两部分。上侧为状态栏，下左侧为BOT控制面板和下右侧历史记录：

### 上侧 - 状态栏

- 显示大模型调用概况(调用次数和上次调用时间)
- 上次交易api key所属的交易所
- 风控模式，交易风格，AI交易运行模式
- 当前持仓，当前委托，权益详情 角标

点击展开折叠，可展开完整的状态栏。
![](../pics/user-gudie-bot-02.png)

### 下左侧 - BOT控制面板和Prompt面板
BOT控制面板用于控制手动触发大模型交易的控制。
Prompt面板用于展示/修改 Prompt（⚠️修改仅对当次生效，仅用于调试prompt）
![](../pics/user-gudie-bot-18.png)

### 下右侧 - Prompt历史记录
- 展示大模型交易的历史(每个Item为一次调用)
- 展示大模型交易的action信息及状态
![](../pics/user-gudie-bot-05.png)

## BOT交易

BOT交易有三种模式，分别为自动触发，手动触发，Prompt触发三种模式
> 不论哪种模式，视乎大模型的响应，每次调用都有可能触发单轮/多轮对话。
- 自动触发：由AI_TRADING_AUTOMATIC_TRADE_ENABLED参数控制，每15分钟执行一次。自动调用大模型生成决策，并解析执行交易。
- 手动触发：用户点击「直接触发BOT」从而触发大模型决策
- Prompt触发：用户点击「生成Prompt」，再点击「提交AI任务」触发大模型决策（Prompt内容可以被用户修改）。

### 自动触发

AI自动交易受启动参数控制(AI_TRADING_AUTOMATIC_TRADE_ENABLED)，系统启动后将按照每15分钟执行一次的频率自动触发大模型交易。所有的模型交互均可在「Prompt历史记录查看」

### 手动触发

用户点击「直接触发BOT」从而触发大模型决策，手动触发与AI自动交易互不影响，所有的模型交互均可在「Prompt历史记录查看」。
![](../pics/user-gudie-bot-19.png)

### Prompt触发

- 点击「生成Prompt」，生成的Prompt会显示在Prompt面板中
![](../pics/user-gudie-bot-15.png)
![](../pics/user-gudie-bot-16.png)
- (可选)点击「编辑」图标，Prompt面板进入编辑模式，用户可以在在此编辑prompt的内容。编辑完成后，点击「保存」使prompt生效
![](../pics/user-gudie-bot-17.png)
- 点击「提交AI任务」，系统将使用修改过的prompt进行大模型交互

## Prompt历史记录

### 查看会话链路和追问

每个Prompt历史的Item均支持查看会话链路和追问。
- 会话链路会将多轮会话的每一轮聚合在弹窗中。
![](../pics/user-gudie-bot-07.png)
![](../pics/user-gudie-bot-08.png)
- 会话转移到AI助手后，用户可以继续进行追问
![](../pics/user-gudie-bot-10.png)
![](../pics/user-gudie-bot-11.png)
![](../pics/user-gudie-bot-13.png)
![](../pics/user-gudie-bot-14.png)

## 介入交易

AI交易所产生的持仓/委托，用户可以通过「合约交易」页面或BOT页面的状态栏进行操作。「合约交易」页的操作参考[合约交易](03-futures-trading.md)
「当前持仓」/「当前委托」均支持如下操作
- 鼠标悬浮在Item上超过3s，操作按钮会呈现
![](../pics/user-gudie-bot-02.png)
![](../pics/user-gudie-bot-20.png)
- 点击「市价全平」，弹出交易确认窗口
![](../pics/user-gudie-bot-21.png)
- 点击「确认平仓」，执行交易

## 交易Review/回顾

- 「当前持仓」/「当前委托」的Item点击弹出K线图
![](../pics/user-gudie-bot-22.png)
- 「历史仓位」的Item点击弹出回顾K线图
![](../pics/user-gudie-bot-23.png)


