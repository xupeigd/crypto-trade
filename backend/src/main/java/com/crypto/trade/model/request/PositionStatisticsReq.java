package com.crypto.trade.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PositionStatisticsReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PositionStatisticsReq {

    /**
     * 交易所供应商
     */
    @Pattern(regexp = "^(OKX|BINANCE|HUOBI)$", message = "交易所必须是OKX、BINANCE或HUOBI")
    String vendor;

    /**
     * 统计维度
     */
    @Pattern(regexp = "^(instrument_type|position_side|currency|leverage_range)$",
            message = "统计维度必须是instrument_type、position_side、currency或leverage_range")
    String dimension;

    /**
     * 开始时间（包含）
     */
    LocalDateTime startTime;

    /**
     * 结束时间（包含）
     */
    LocalDateTime endTime;

    /**
     * 是否包含已平仓记录
     */
    Boolean includeClosed;

    /**
     * 最小价值筛选（USD）
     */
    @Min(value = 0, message = "最小价值不能小于0")
    Double minNotional;

    /**
     * 只统计有风险的仓位
     */
    Boolean onlyRisky;

    /**
     * 风险阈值（保证金率百分比）
     */
    @Min(value = 0, message = "风险阈值不能小于0")
    Double riskThreshold;

    /**
     * 按币种分组
     */
    Boolean groupByCurrency;

    /**
     * 按合约类型分组
     */
    Boolean groupByInstType;

    /**
     * 按持仓方向分组
     */
    Boolean groupByPosSide;

    /**
     * 计算收益率
     */
    Boolean calculateReturnRate;

    /**
     * 创建请求构建器（设置默认值）
     *
     * @return PositionStatisticsRequestBuilder
     */
    public static PositionStatisticsReqBuilder builder() {
        return new PositionStatisticsReqBuilder()
                .vendor("OKX")
                .dimension("instrument_type")
                .includeClosed(false)
                .onlyRisky(false)
                .groupByCurrency(false)
                .groupByInstType(false)
                .groupByPosSide(false)
                .calculateReturnRate(false)
                .riskThreshold(10.0);
    }

    /**
     * 获取默认供应商
     *
     * @return 供应商，默认为OKX
     */
    public String getVendorOrDefault() {
        return null != vendor ? vendor : "OKX";
    }

    /**
     * 获取默认统计维度
     *
     * @return 统计维度，默认为instrument_type
     */
    public String getDimensionOrDefault() {
        return null != dimension ? dimension : "instrument_type";
    }

    /**
     * 获取默认开始时间
     *
     * @return 开始时间，默认为24小时前
     */
    public LocalDateTime getStartTimeOrDefault() {
        return null != startTime ? startTime : LocalDateTime.now().minusHours(24);
    }

    /**
     * 获取默认结束时间
     *
     * @return 结束时间，默认为当前时间
     */
    public LocalDateTime getEndTimeOrDefault() {
        return null != endTime ? endTime : LocalDateTime.now();
    }

    /**
     * 判断是否包含已平仓记录
     *
     * @return true如果包含已平仓记录
     */
    public boolean isIncludeClosed() {
        return Boolean.TRUE.equals(includeClosed);
    }

    /**
     * 判断是否只统计有风险的仓位
     *
     * @return true如果只统计有风险的仓位
     */
    public boolean isOnlyRisky() {
        return Boolean.TRUE.equals(onlyRisky);
    }

    /**
     * 判断是否按币种分组
     *
     * @return true如果按币种分组
     */
    public boolean isGroupByCurrency() {
        return Boolean.TRUE.equals(groupByCurrency);
    }

    /**
     * 判断是否按合约类型分组
     *
     * @return true如果按合约类型分组
     */
    public boolean isGroupByInstType() {
        return Boolean.TRUE.equals(groupByInstType);
    }

    /**
     * 判断是否按持仓方向分组
     *
     * @return true如果按持仓方向分组
     */
    public boolean isGroupByPosSide() {
        return Boolean.TRUE.equals(groupByPosSide);
    }

    /**
     * 判断是否计算收益率
     *
     * @return true如果计算收益率
     */
    public boolean isCalculateReturnRate() {
        return Boolean.TRUE.equals(calculateReturnRate);
    }

    /**
     * 获取默认风险阈值
     *
     * @return 风险阈值，默认为10%
     */
    public double getRiskThresholdOrDefault() {
        return null != riskThreshold ? riskThreshold : 10.0;
    }

    /**
     * 验证请求参数
     *
     * @return 验证结果
     */
    public boolean isValid() {
        // 验证时间范围
        if (null != startTime && null != endTime && startTime.isAfter(endTime)) {
            return false;
        }

        // 验证时间范围不超过30天
        if (null != startTime && null != endTime) {
            long hours = java.time.Duration.between(startTime, endTime).toHours();
            if (hours > 30 * 24) {
                return false; // 统计时间范围不能超过30天
            }
        }

        // 验证风险阈值
        return null == riskThreshold || (riskThreshold >= 0 && riskThreshold <= 100);
    }

    /**
     * 判断是否有统计条件
     *
     * @return true如果有任何统计条件
     */
    public boolean hasStatisticsConditions() {
        return null != dimension ||
                null != startTime ||
                null != endTime ||
                isIncludeClosed() ||
                null != minNotional ||
                isOnlyRisky() ||
                null != riskThreshold ||
                isGroupByCurrency() ||
                isGroupByInstType() ||
                isGroupByPosSide() ||
                isCalculateReturnRate();
    }

    /**
     * 获取统计类型描述
     *
     * @return 统计类型描述
     */
    public String getStatisticsTypeDescription() {
        if (!hasStatisticsConditions()) {
            return "基础统计";
        }

        StringBuilder description = new StringBuilder();
        if (null != dimension) {
            description.append("按").append(getDimensionDescription()).append("统计");
        }
        if (isOnlyRisky()) {
            if (description.length() > 0) description.append("，");
            description.append("仅高风险");
        }
        if (null != minNotional) {
            if (description.length() > 0) description.append("，");
            description.append("最小价值").append(minNotional).append("USD");
        }

        return description.length() > 0 ? description.toString() : "自定义统计";
    }

    /**
     * 获取维度描述
     *
     * @return 维度描述
     */
    String getDimensionDescription() {
        return switch (getDimensionOrDefault().toLowerCase()) {
            case "instrument_type" -> "合约类型";
            case "position_side" -> "持仓方向";
            case "currency" -> "币种";
            case "leverage_range" -> "杠杆范围";
            default -> "未知维度";
        };
    }
}