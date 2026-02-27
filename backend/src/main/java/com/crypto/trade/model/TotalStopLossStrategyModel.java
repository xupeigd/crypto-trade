package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * TotalStopLossStrategyModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TotalStopLossStrategyModel {

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * API密钥ID
     */
    Long apiKeyId;

    /**
     * 合约品种
     */
    String instId;

    /**
     * 持仓方向 (long/short/net)
     */
    String posSide;

    /**
     * 订单方向 (buy/sell)
     */
    String side;

    /**
     * 订单数量
     */
    String sz;

    /**
     * 止盈触发价格
     */
    String tpTriggerPx;

    /**
     * 止损触发价格
     */
    String slTriggerPx;

    /**
     * 止盈触发价格类型
     */
    String tpTriggerPxType;

    /**
     * 止损触发价格类型
     */
    String slTriggerPxType;

    /**
     * 策略状态
     */
    String state;

    /**
     * 创建时间
     */
    LocalDateTime cTime;

    /**
     * 更新时间
     */
    LocalDateTime uTime;

    /**
     * 策略类型
     */
    String strategyType;

    /**
     * 触发条件
     */
    String triggerCondition;

    /**
     * 从OKX API响应数据创建TotalStopLossStrategyModel
     *
     * @param algoId   算法订单ID
     * @param apiKeyId API密钥ID
     * @param instId   合约品种
     * @param rawData  原始数据Map
     * @return TotalStopLossStrategyModel模型
     */
    public static TotalStopLossStrategyModel fromRawData(String algoId, Long apiKeyId, String instId, Map<String, Object> rawData) {
        if (null == rawData) {
            return null;
        }

        TotalStopLossStrategyModelBuilder builder = TotalStopLossStrategyModel.builder()
                .algoId(algoId)
                .apiKeyId(apiKeyId)
                .instId(instId);

        // 从rawData中提取字段
        if (rawData.containsKey("posSide")) {
            builder.posSide((String) rawData.get("posSide"));
        }
        if (rawData.containsKey("side")) {
            builder.side((String) rawData.get("side"));
        }
        if (rawData.containsKey("sz")) {
            builder.sz(String.valueOf(rawData.get("sz")));
        }
        if (rawData.containsKey("tpTriggerPx")) {
            builder.tpTriggerPx(String.valueOf(rawData.get("tpTriggerPx")));
        }
        if (rawData.containsKey("slTriggerPx")) {
            builder.slTriggerPx(String.valueOf(rawData.get("slTriggerPx")));
        }
        if (rawData.containsKey("tpTriggerPxType")) {
            builder.tpTriggerPxType((String) rawData.get("tpTriggerPxType"));
        }
        if (rawData.containsKey("slTriggerPxType")) {
            builder.slTriggerPxType((String) rawData.get("slTriggerPxType"));
        }
        if (rawData.containsKey("state")) {
            builder.state((String) rawData.get("state"));
        }
        if (rawData.containsKey("cTime")) {
            Object cTime = rawData.get("cTime");
            if (cTime instanceof String) {
                try {
                    long timestamp = Long.parseLong((String) cTime);
                    builder.cTime(LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC));
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }
        }
        if (rawData.containsKey("uTime")) {
            Object uTime = rawData.get("uTime");
            if (uTime instanceof String) {
                try {
                    long timestamp = Long.parseLong((String) uTime);
                    builder.uTime(LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC));
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }
        }

        // 设置策略类型
        builder.strategyType("TOTAL_STOP_LOSS");

        // 设置触发条件
        String triggerCondition = buildTriggerCondition(
                (String) rawData.get("tpTriggerPx"),
                (String) rawData.get("slTriggerPx"),
                (String) rawData.get("posSide")
        );
        builder.triggerCondition(triggerCondition);

        return builder.build();
    }

    /**
     * 构建触发条件描述
     *
     * @param tpTriggerPx 止盈触发价格
     * @param slTriggerPx 止损触发价格
     * @param posSide     持仓方向
     * @return 触发条件描述
     */
    static String buildTriggerCondition(String tpTriggerPx, String slTriggerPx, String posSide) {
        StringBuilder condition = new StringBuilder();

        if (null != tpTriggerPx && !tpTriggerPx.isEmpty()) {
            if ("long".equals(posSide)) {
                condition.append("价格涨至 ").append(tpTriggerPx).append(" 时止盈");
            } else if ("short".equals(posSide)) {
                condition.append("价格跌至 ").append(tpTriggerPx).append(" 时止盈");
            }
        }

        if (null != slTriggerPx && !slTriggerPx.isEmpty()) {
            if (condition.length() > 0) {
                condition.append("，");
            }
            if ("long".equals(posSide)) {
                condition.append("价格跌至 ").append(slTriggerPx).append(" 时止损");
            } else if ("short".equals(posSide)) {
                condition.append("价格涨至 ").append(slTriggerPx).append(" 时止损");
            }
        }

        return condition.length() > 0 ? condition.toString() : "无触发条件";
    }

    /**
     * 获取止盈触发价格的BigDecimal值
     *
     * @return 止盈触发价格BigDecimal值
     */
    public BigDecimal getTpTriggerPxValue() {
        if (null == tpTriggerPx || tpTriggerPx.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(tpTriggerPx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取止损触发价格的BigDecimal值
     *
     * @return 止损触发价格BigDecimal值
     */
    public BigDecimal getSlTriggerPxValue() {
        if (null == slTriggerPx || slTriggerPx.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(slTriggerPx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否设置了止盈
     *
     * @return true如果设置了止盈
     */
    public boolean hasTakeProfit() {
        return null != tpTriggerPx && !tpTriggerPx.trim().isEmpty();
    }

    /**
     * 判断是否设置了止损
     *
     * @return true如果设置了止损
     */
    public boolean hasStopLoss() {
        return null != slTriggerPx && !slTriggerPx.trim().isEmpty();
    }

    /**
     * 判断策略是否有效
     *
     * @return true如果策略有效
     */
    public boolean isValid() {
        return null != algoId && !algoId.trim().isEmpty() &&
                null != apiKeyId &&
                null != instId && !instId.trim().isEmpty() &&
                (hasTakeProfit() || hasStopLoss());
    }

    /**
     * 获取策略状态显示名称
     *
     * @return 状态的中文描述
     */
    public String getStateDisplayName() {
        if (null == state) {
            return "未知";
        }
        switch (state) {
            case "live":
                return "生效中";
            case "canceled":
                return "已取消";
            case "triggered":
                return "已触发";
            case "failed":
                return "失败";
            default:
                return state;
        }
    }

    /**
     * 获取持仓方向显示名称
     *
     * @return 持仓方向的中文描述
     */
    public String getPosSideDisplayName() {
        if (null == posSide) {
            return "未知";
        }
        switch (posSide) {
            case "long":
                return "做多";
            case "short":
                return "做空";
            case "net":
                return "净持仓";
            default:
                return posSide;
        }
    }
}