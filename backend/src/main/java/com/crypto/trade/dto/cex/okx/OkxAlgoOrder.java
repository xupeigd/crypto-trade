package com.crypto.trade.dto.cex.okx;

import com.crypto.trade.dto.cex.utils.StringToBigDecimalDeserializer;
import com.crypto.trade.dto.cex.utils.StringToBooleanDeserializer;
import com.crypto.trade.dto.cex.utils.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * OkxAlgoOrder
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxAlgoOrder {

    /**
     * 算法订单ID
     */
    String algoId;

    /**
     * 客户自定义订单ID（下单时传入）
     */
    String clOrdId;

    /**
     * 算法订单类型：conditional、oco、trigger、move_order_stop、iceberg、twap
     */
    String ordType;

    /**
     * 订单状态：live、effective、partially_filled、filled、canceled
     */
    String state;

    /**
     * 合约代码
     */
    String instId;

    /**
     * 合约类型：SWAP、FUTURES、MARGIN、SPOT 等
     */
    String instType;

    /**
     * 持仓方向：long / short / net
     */
    String posSide;

    /**
     * 买卖方向
     */
    String side;

    /**
     * 保证金模式：isolated / cross
     */
    String tdMode;

    /**
     * 币种（如 USDT）
     */
    String ccy;

    /**
     * 是否只减仓
     */
    @JsonDeserialize(using = StringToBooleanDeserializer.class)
    boolean reduceOnly;

    /**
     * 杠杆倍数
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal lever;

    /**
     * 最新成交价（部分成交时有值）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal last;

    /**
     * 最新成交价格（备用字段）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal actualPx;

    /**
     * 实际成交方向
     */
    String actualSide;

    /**
     * 实际成交数量
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal actualSz;

    /**
     * 止盈触发价
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal tpTriggerPx;

    /**
     * 止盈触发价类型：last、index、mark
     */
    String tpTriggerPxType;

    /**
     * 止盈挂单价（-1 表示市价）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal tpOrdPx;

    /**
     * 止损触发价
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal slTriggerPx;

    /**
     * 止损触发价类型
     */
    String slTriggerPxType;

    /**
     * 止损挂单价（-1 表示市价）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal slOrdPx;

    /**
     * 触发价（通用触发单用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal triggerPx;

    /**
     * 触发价类型
     */
    String triggerPxType;

    /**
     * 挂单价（普通限价单用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal ordPx;

    /**
     * 数量（张或币）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal sz;

    /**
     * 数量限制（冰山单用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal szLimit;

    /**
     * 价格偏差（TWAP/冰山单用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pxVar;

    /**
     * 价格幅度
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pxSpread;

    /**
     * 价格限制
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal pxLimit;

    /**
     * 时间间隔（秒，TWAP用）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long timeInterval;

    /**
     * 回调幅度（移动止损用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal callbackSpread;

    /**
     * 回调率
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal callbackRatio;

    /**
     * 追单类型
     */
    String chaseType;

    /**
     * 追单值
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal chaseVal;

    /**
     * 最大追单类型
     */
    String maxChaseType;

    /**
     * 最大追单值
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal maxChaseVal;

    /**
     * 移动止损触发价
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal moveTriggerPx;

    /**
     * 快速保证金类型
     */
    String quickMgnType;

    /**
     * 平仓比例（OCO单用）
     */
    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal closeFraction;

    /**
     * 标签
     */
    String tag;

    /**
     * 高级订单类型
     */
    String advanceOrdType;

    /**
     * 附加算法订单
     */
    List<Object> attachAlgoOrds;

    /**
     * 关联订单ID（OCO中另一条）
     */
    LinkedOrd linkedOrd;

    /**
     * 创建时间
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long cTime;

    /**
     * 更新时间
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long uTime;

    /**
     * 触发时间（已触发时有值）
     */
    @JsonDeserialize(using = StringToLongDeserializer.class)
    Long triggerTime;

    /**
     * 失败代码
     */
    String failCode;

    /**
     * 是否交易借币模式
     */
    String isTradeBorrowMode;

    /**
     * 业务参考ID
     */
    String bizRefId;

    /**
     * 业务参考类型
     */
    String bizRefType;

    /**
     * 改正触发价类型
     */
    String amendPxOnTriggerType;

    /**
     * 订单ID列表（历史接口可能返回多个）
     */
    List<String> ordIdList;

    @JsonDeserialize(using = StringToBigDecimalDeserializer.class)
    BigDecimal activePx;

    String algoClOrdId;

    String ordId;

    String tgtCcy;

    String tradeQuoteCcy;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LinkedOrd {
        String ordId;
    }

}
