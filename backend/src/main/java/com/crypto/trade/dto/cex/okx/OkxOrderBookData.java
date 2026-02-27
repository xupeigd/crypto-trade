package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * OkxOrderBookData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxOrderBookData {

    /**
     * 时间戳
     */
    @JsonProperty("ts")
    private String ts;

    /**
     * 卖盘深度数据
     * 格式：[价格, 数量, 数量，订单数]
     */
    @JsonProperty("asks")
    private List<List<String>> asks;

    /**
     * 买盘深度数据
     * 格式：[价格, 数量, 数量，订单数]
     */
    @JsonProperty("bids")
    private List<List<String>> bids;

    /**
     * 检查点
     */
    @JsonProperty("checkSum")
    private String checkSum;

    // === 便利方法 ===

    /**
     * 获取时间戳（Long）
     */
    public Long getTimestamp() {
        try {
            return null != ts && !ts.isEmpty() ? Long.parseLong(ts) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取最优卖价
     */
    public BigDecimal getBestAskPrice() {
        if (null != asks && !asks.isEmpty()) {
            try {
                String price = asks.get(0).get(0);
                return new BigDecimal(price);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取最优卖量
     */
    public BigDecimal getBestAskSize() {
        if (null != asks && !asks.isEmpty()) {
            try {
                String size = asks.get(0).get(1);
                return new BigDecimal(size);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取最优买价
     */
    public BigDecimal getBestBidPrice() {
        if (null != bids && !bids.isEmpty()) {
            try {
                String price = bids.get(0).get(0);
                return new BigDecimal(price);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取最优买量
     */
    public BigDecimal getBestBidSize() {
        if (null != bids && !bids.isEmpty()) {
            try {
                String size = bids.get(0).get(1);
                return new BigDecimal(size);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取价差
     */
    public BigDecimal getSpread() {
        return getBestAskPrice().subtract(getBestBidPrice());
    }

    /**
     * 获取价差百分比
     */
    public BigDecimal getSpreadPercent() {
        BigDecimal bidPrice = getBestBidPrice();
        if (bidPrice.compareTo(BigDecimal.ZERO) > 0) {
            return getSpread()
                    .divide(bidPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取中间价
     */
    public BigDecimal getMidPrice() {
        return getBestAskPrice().add(getBestBidPrice())
                .divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
    }

    /**
     * 获取卖盘深度总和
     */
    public BigDecimal getTotalAskSize() {
        BigDecimal total = BigDecimal.ZERO;
        if (null != asks) {
            for (List<String> ask : asks) {
                try {
                    if (ask.size() > 1) {
                        total = total.add(new BigDecimal(ask.get(1)));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
        return total;
    }

    /**
     * 获取买盘深度总和
     */
    public BigDecimal getTotalBidSize() {
        BigDecimal total = BigDecimal.ZERO;
        if (null != bids) {
            for (List<String> bid : bids) {
                try {
                    if (bid.size() > 1) {
                        total = total.add(new BigDecimal(bid.get(1)));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
        return total;
    }

    /**
     * 价格档位内部类
     */
    @Data
    @NoArgsConstructor
    public static class PriceLevel {
        /**
         * 价格
         */
        private BigDecimal price;

        /**
         * 数量
         */
        private BigDecimal size;

        /**
         * 订单数
         */
        private Integer orderCount;

        public PriceLevel(BigDecimal price, BigDecimal size) {
            this.price = price;
            this.size = size;
        }

        public PriceLevel(BigDecimal price, BigDecimal size, Integer orderCount) {
            this.price = price;
            this.size = size;
            this.orderCount = orderCount;
        }
    }
}