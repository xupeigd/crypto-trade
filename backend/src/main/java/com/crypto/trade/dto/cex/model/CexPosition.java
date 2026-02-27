package com.crypto.trade.dto.cex.model;

import com.crypto.trade.dto.cex.common.MarginMode;
import com.crypto.trade.dto.cex.common.PositionSide;

import java.math.BigDecimal;
import java.util.List;

/**
 * CEX持仓抽象模型
 * <p>
 * 统一不同交易所的持仓数据结构,提供通用的持仓字段访问接口。
 * </p>
 *
 * @author Page
 * @since 2025-01-21
 */
public abstract class CexPosition {

    /**
     * 获取交易对/合约代码
     *
     * @return 交易对标识
     */
    public abstract String getSymbol();

    /**
     * 获取持仓方向
     *
     * @return LONG、SHORT或NET
     */
    public abstract PositionSide getSide();

    /**
     * 获取持仓数量
     * <p>
     * 统一使用字段,对应OKX的pos。
     * </p>
     *
     * @return 持仓数量
     */
    public abstract BigDecimal getQuantity();

    /**
     * 获取可平仓数量
     *
     * @return 可平仓数量
     */
    public abstract BigDecimal getAvailableQuantity();

    /**
     * 获取开仓均价
     *
     * @return 开仓平均价格
     */
    public abstract BigDecimal getAvgPrice();

    /**
     * 获取标记价格
     *
     * @return 当前标记价格
     */
    public abstract BigDecimal getMarkPrice();

    /**
     * 获取未实现盈亏
     *
     * @return 未实现盈亏金额
     */
    public abstract BigDecimal getUnrealizedPnl();

    /**
     * 获取未实现盈亏比率
     * <p>
     * 交易所API返回的盈亏率,通常是相对于保证金的百分比
     * </p>
     *
     * @return 盈亏比率(百分比形式, 如 - 5.23 表示 - 5.23 %), 如果不可用返回null
     */
    public abstract BigDecimal getUnrealizedPnlRatio();

    /**
     * 获取已实现盈亏
     *
     * @return 已实现盈亏金额
     */
    public abstract BigDecimal getRealizedPnl();

    /**
     * 获取杠杆倍数
     *
     * @return 杠杆倍数
     */
    public abstract BigDecimal getLeverage();

    /**
     * 获取保证金模式
     *
     * @return 逐仓或全仓
     */
    public abstract MarginMode getMarginMode();

    /**
     * 获取保证金
     *
     * @return 保证金金额
     */
    public abstract BigDecimal getMargin();

    /**
     * 获取预估强平价格
     *
     * @return 强平价格
     */
    public abstract BigDecimal getLiquidationPrice();

    /**
     * 获取持仓价值(USD)
     *
     * @return 持仓名义价值
     */
    public abstract BigDecimal getNotionalValue();

    /**
     * 获取持仓创建时间(毫秒时间戳)
     * <p>
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 创建时间戳, 如果不可用返回null
     */
    public abstract Long getCreateTime();

    /**
     * 获取合约类型
     * <p>
     * 可能值: SWAP, FUTURES, OPTION等
     * </p>
     *
     * @return 合约类型, 如果不可用返回null
     */
    public abstract String getInstrumentType();

    /**
     * 获取保证金币种
     * <p>
     * 用于保证金的币种,如USDT
     * </p>
     *
     * @return 保证金币种, 如果不可用返回null
     */
    public abstract String getCurrency();

    /**
     * 获取保证金率
     * <p>
     * 保证金维持率,用于判断是否接近强平
     * </p>
     *
     * @return 保证金率, 如果不可用返回null
     */
    public abstract BigDecimal getMarginRatio();

    /**
     * 获取原始数据对象
     * <p>
     * 用于保留交易所特定的原始数据,如OKX的closeOrderAlgo字段。
     * 在反向适配时可以恢复这些数据。
     * </p>
     *
     * @return 原始数据对象, 如果不可用返回null
     */
//    public abstract Object getOriginalData();

    /**
     * 判断是否为多头持仓
     *
     * @return true=多头, false=非多头
     */
    public boolean isLong() {
        return PositionSide.LONG == getSide();
    }

    /**
     * 判断是否为空头持仓
     *
     * @return true=空头, false=非空头
     */
    public boolean isShort() {
        return PositionSide.SHORT == getSide();
    }

    /**
     * 判断是否为逐仓模式
     *
     * @return true=逐仓, false=全仓
     */
    public boolean isIsolated() {
        return MarginMode.ISOLATED == getMarginMode();
    }

    /**
     * 判断是否为全仓模式
     *
     * @return true=全仓, false=逐仓
     */
    public boolean isCross() {
        return MarginMode.CROSS == getMarginMode();
    }

    /**
     * 获取持仓Id
     *
     * @return String
     */
    public abstract String getPosId();

    /**
     * 累计资金费
     *
     * @return String
     */
    public abstract BigDecimal getFundingFee();

    /**
     * 手续费
     *
     * @return BigDecimal
     */
    public abstract BigDecimal getFee();

    /**
     * 止盈止损算法单
     *
     * @return list of CexAlgoOrder
     */
    public abstract List<CexAlgoOrder> getCloseOrderAlgo();

    public abstract String getMgnMode();

    /**
     * 获取最近一次平仓的类型
     * <p>
     * 可能值: 1=部分平仓, 2=完全平仓, 3=强平, 4=强减, 5=ADL自动减仓
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 平仓类型, 如果不可用返回null
     */
    public abstract String getType();

    /**
     * 获取最新成交价格
     * <p>
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 最新价格, 如果不可用返回null
     */
    public abstract BigDecimal getLast();

    /**
     * 获取初始保证金
     * <p>
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 初始保证金, 如果不可用返回null
     */
    public abstract BigDecimal getImr();

    /**
     * 获取维持保证金
     * <p>
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 维持保证金, 如果不可用返回null
     */
    public abstract BigDecimal getMmr();

    /**
     * 获取持仓更新时间(毫秒时间戳)
     * <p>
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 更新时间戳, 如果不可用返回null
     */
    public abstract Long getUTime();

    /**
     * 获取未实现盈亏(按最新价)
     * <p>
     * OKX特有字段,其他交易所可能没有此字段
     * </p>
     *
     * @return 未实现盈亏, 如果不可用返回null
     */
    public abstract BigDecimal getUplLastPx();

    /**
     * 获取数据摄入时间(毫秒时间戳)
     * <p>
     * 系统记录的数据采集时间,OKX特有字段
     * </p>
     *
     * @return 数据摄入时间, 如果不可用返回null
     */
    public abstract Long getDataIngestionTime();

    /**
     * 获取开仓时最大持仓数量
     * <p>
     * 历史仓位特有字段,记录该仓位开仓时的最大持仓量
     * </p>
     *
     * @return 开仓时最大持仓数量, 如果不可用返回null
     */
    public abstract BigDecimal getOpenMaxPos();

    /**
     * 获取平仓时的总持仓数量
     * <p>
     * 历史仓位特有字段,记录平仓时的总持仓量
     * </p>
     *
     * @return 平仓时的总持仓数量, 如果不可用返回null
     */
    public abstract BigDecimal getCloseTotalPos();

    /**
     * 获取已结算盈亏
     * <p>
     * 历史仓位特有字段,记录已结算的盈亏金额
     * </p>
     *
     * @return 已结算盈亏, 如果不可用返回null
     */
    public abstract BigDecimal getSettledPnl();

    /**
     * 获取盈亏比率
     * <p>
     * 历史仓位特有字段,记录盈亏比率
     * </p>
     *
     * @return 盈亏比率, 如果不可用返回null
     */
    public abstract BigDecimal getPnlRatio();

    public abstract BigDecimal getOpenAvgPx();

    public abstract BigDecimal getCloseAvgPx();

}
