package com.crypto.trade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FundingRateModel
 * 数据模型
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FundingRateModel {

    /**
     * 合约品种
     */
    String instId;

    /**
     * 当前资金费率
     */
    String fundingRate;

    /**
     * 下一期资金费率
     */
    String nextFundingRate;

    /**
     * 资金费率结算时间
     */
    LocalDateTime fundingTime;

    /**
     * 下一期资金费率结算时间
     */
    LocalDateTime nextFundingTime;

    /**
     * 资金费率状态
     */
    String status;

    /**
     * 资金费率更新时间
     */
    LocalDateTime updateTime;

    /**
     * 从OKX API响应创建FundingRateModel
     *
     * @param instId   合约品种
     * @param response OKX API响应
     * @return FundingRateModel模型
     */
    public static FundingRateModel fromOkxResponse(String instId, String response) {
        if (null == response || !response.contains("\"code\":\"0\"")) {
            return null;
        }

        FundingRateModelBuilder builder = FundingRateModel.builder()
                .instId(instId);

        // 提取fundingRate
        String fundingRate = extractJsonValue(response, "fundingRate");
        if (null != fundingRate) {
            builder.fundingRate(fundingRate);
        }

        // 提取nextFundingRate
        String nextFundingRate = extractJsonValue(response, "nextFundingRate");
        if (null != nextFundingRate) {
            builder.nextFundingRate(nextFundingRate);
        }

        // 提取fundingTime
        String fundingTime = extractJsonValue(response, "fundingTime");
        if (null != fundingTime) {
            try {
                long timestamp = Long.parseLong(fundingTime);
                builder.fundingTime(LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        // 提取nextFundingTime
        String nextFundingTime = extractJsonValue(response, "nextFundingTime");
        if (null != nextFundingTime) {
            try {
                long timestamp = Long.parseLong(nextFundingTime);
                builder.nextFundingTime(LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        builder.updateTime(LocalDateTime.now());
        return builder.build();
    }

    /**
     * 从JSON字符串中提取指定字段的值
     *
     * @param json JSON字符串
     * @param key  字段名
     * @return 字段值
     */
    static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int startIndex = json.indexOf(pattern);
        if (startIndex == -1) {
            pattern = "\"" + key + "\":";
            startIndex = json.indexOf(pattern);
            if (startIndex == -1) {
                return null;
            }
            startIndex += pattern.length();
        } else {
            startIndex += pattern.length();
        }

        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = json.indexOf("}", startIndex);
                if (endIndex == -1) {
                    return null;
                }
            }
        }

        return json.substring(startIndex, endIndex).trim();
    }

    /**
     * 获取资金费率数值
     *
     * @return 资金费率BigDecimal值
     */
    public BigDecimal getFundingRateValue() {
        if (null == fundingRate || fundingRate.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(fundingRate);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 获取下一期资金费率数值
     *
     * @return 下一期资金费率BigDecimal值
     */
    public BigDecimal getNextFundingRateValue() {
        if (null == nextFundingRate || nextFundingRate.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(nextFundingRate);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 判断当前资金费率是否为正数（多头支付空头）
     *
     * @return true如果资金费率为正数
     */
    public boolean isPositiveFundingRate() {
        return getFundingRateValue().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 判断当前资金费率是否为负数（空头支付多头）
     *
     * @return true如果资金费率为负数
     */
    public boolean isNegativeFundingRate() {
        return getFundingRateValue().compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * 获取资金费率显示文本
     *
     * @return 格式化的资金费率显示文本
     */
    public String getFundingRateDisplay() {
        BigDecimal rate = getFundingRateValue();
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return "0.0000%";
        }
        return rate.multiply(new BigDecimal("100")) + "%";
    }

    /**
     * 获取资金费率说明
     *
     * @return 资金费率说明文本
     */
    public String getFundingRateDescription() {
        if (isPositiveFundingRate()) {
            return "多头支付给空头 " + getFundingRateDisplay();
        } else if (isNegativeFundingRate()) {
            return "空头支付给多头 " + getFundingRateDisplay();
        } else {
            return "无资金费率";
        }
    }
}