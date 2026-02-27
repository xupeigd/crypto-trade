package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * OkxAlgoOrderData
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OkxAlgoOrderData extends OkxOrderData {

    /**
     * 算法订单ID
     */
    @JsonProperty("algoId")
    private String algoId;

    /**
     * 触发价格
     */
    @JsonProperty("triggerPx")
    private String triggerPx;

    /**
     * 触发价格类型
     * last：最新价格，mark：标记价格，index：指数价格
     */
    @JsonProperty("triggerPxType")
    private String triggerPxType;

    /**
     * 订单类型
     * move_order_stop：止盈止损，conditional：计划委托，iceberg： iceberg订单，twap：TWAP订单，adl：ADL自动减仓
     */
    @JsonProperty("algoType")
    private String algoType;

    /**
     * 下单量
     * 按订单金额下单时生效
     */
    @JsonProperty("sz")
    private String sz;

    /**
     * 最后更新时间
     */
    @JsonProperty("cTime")
    private String cTime;

    // === 便利方法 ===

    /**
     * 获取触发价格（BigDecimal）
     */
    public java.math.BigDecimal getTriggerPriceAsBigDecimal() {
        try {
            return null != triggerPx && !triggerPx.isEmpty() ? new java.math.BigDecimal(triggerPx) : java.math.BigDecimal.ZERO;
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    /**
     * 判断是否为止盈止损订单
     */
    public boolean isStopOrder() {
        return "move_order_stop".equals(algoType);
    }

    /**
     * 判断是否为计划委托订单
     */
    public boolean isConditionalOrder() {
        return "conditional".equals(algoType);
    }

    /**
     * 判断是否为冰山订单
     */
    public boolean isIcebergOrder() {
        return "iceberg".equals(algoType);
    }

    /**
     * 判断是否为TWAP订单
     */
    public boolean isTwapOrder() {
        return "twap".equals(algoType);
    }

    /**
     * 判断是否为最新价格触发
     */
    public boolean isLastPriceTrigger() {
        return "last".equals(triggerPxType);
    }

    /**
     * 判断是否为标记价格触发
     */
    public boolean isMarkPriceTrigger() {
        return "mark".equals(triggerPxType);
    }

    /**
     * 判断是否为指数价格触发
     */
    public boolean isIndexPriceTrigger() {
        return "index".equals(triggerPxType);
    }
}