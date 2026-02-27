package com.crypto.trade.dto.cex.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CexContractInfo
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CexContractInfo {

    /**
     * 合约品种ID (如 BTC-USDT-SWAP)
     */
    private String instId;

    /**
     * 合约类型 (SPOT/SWAP/FUTURES/OPTION)
     */
    private String instType;

    /**
     * 基础货币 (如 BTC)
     */
    private String baseCcy;

    /**
     * 计价货币 (如 USDT)
     */
    private String quoteCcy;

    /**
     * 合约面值 (张/contract)
     * 例如: 0.01 表示1张合约代表0.01个BTC
     */
    private BigDecimal ctVal;

    /**
     * 合约乘数
     * 用于计算合约价值和保证金
     */
    private BigDecimal ctMult;

    /**
     * 下单数量精度 (最小单位)
     * 例如: 1 表示最小下单1张
     */
    private BigDecimal lotSz;

    /**
     * 下单价格精度 (tick size)
     * 例如: 0.1 表示价格最小变动0.1 USDT
     */
    private BigDecimal tickSz;

    /**
     * 最小下单数量
     */
    private BigDecimal minSz;

    /**
     * 最大杠杆倍数
     */
    private BigDecimal lever;

    /**
     * 合约状态 (live/suspend/expire)
     */
    private String state;

    /**
     * 结算货币
     */
    private String settleCcy;

    /**
     * 合约列表上限
     */
    private BigDecimal maxLmtSz;

    /**
     * 合约列表下限
     */
    private BigDecimal minLmtSz;

    /**
     * 创建时间戳
     */
    private Long timestamp;

    // ==================== 便捷计算方法 ====================

    /**
     * 计算合约价值
     *
     * @param quantity 合约数量(张)
     * @return 合约价值(计价货币)
     */
    public BigDecimal calculateContractValue(BigDecimal quantity) {
        if (null == ctVal || null == quantity) {
            return BigDecimal.ZERO;
        }
        return ctVal.multiply(quantity);
    }

    /**
     * 检查数量是否符合精度要求
     *
     * @param quantity 下单数量
     * @return true-符合要求, false-不符合
     */
    public boolean isValidQuantity(BigDecimal quantity) {
        if (null == quantity || null == lotSz) {
            return false;
        }
        // 检查是否为lotSz的整数倍
        BigDecimal[] divideAndRemainder = quantity.divideAndRemainder(lotSz);
        return divideAndRemainder[1].compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 检查价格是否符合精度要求
     *
     * @param price 下单价格
     * @return true-符合要求, false-不符合
     */
    public boolean isValidPrice(BigDecimal price) {
        if (null == price || null == tickSz) {
            return false;
        }
        // 检查是否为tickSz的整数倍
        BigDecimal[] divideAndRemainder = price.divideAndRemainder(tickSz);
        return divideAndRemainder[1].compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 调整数量为合法精度
     *
     * @param quantity 原始数量
     * @return 调整后的数量
     */
    public BigDecimal adjustQuantity(BigDecimal quantity) {
        if (null == quantity || null == lotSz) {
            return quantity;
        }
        // 向下取整到lotSz的整数倍
        BigDecimal lots = quantity.divide(lotSz, 0, java.math.RoundingMode.DOWN);
        return lots.multiply(lotSz);
    }

    /**
     * 调整价格为合法精度
     *
     * @param price 原始价格
     * @return 调整后的价格
     */
    public BigDecimal adjustPrice(BigDecimal price) {
        if (null == price || null == tickSz) {
            return price;
        }
        // 向下取整到tickSz的整数倍
        BigDecimal ticks = price.divide(tickSz, 0, java.math.RoundingMode.DOWN);
        return ticks.multiply(tickSz);
    }

    /**
     * 判断合约是否可用
     *
     * @return true-可用, false-不可用
     */
    public boolean isAvailable() {
        return "live".equalsIgnoreCase(state) || "normal".equalsIgnoreCase(state);
    }

    /**
     * 判断是否为永续合约
     *
     * @return true-永续合约, false-其他类型
     */
    public boolean isPerpetual() {
        return "SWAP".equalsIgnoreCase(instType);
    }

    /**
     * 判断是否为现货
     *
     * @return true-现货, false-其他类型
     */
    public boolean isSpot() {
        return "SPOT".equalsIgnoreCase(instType);
    }
}
