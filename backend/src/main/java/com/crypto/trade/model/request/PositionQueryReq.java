package com.crypto.trade.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PositionQueryReq
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PositionQueryReq {

    /**
     * 交易所供应商
     */
    @Pattern(regexp = "^(OKX|BINANCE|HUOBI)$", message = "交易所必须是OKX、BINANCE或HUOBI")
    String vendor;

    /**
     * 合约类型列表
     */
    List<String> instTypes;

    /**
     * 持仓方向列表
     */
    List<String> posSides;

    /**
     * 合约代码列表
     */
    List<String> instIds;

    /**
     * 保证金币种列表
     */
    List<String> ccys;

    /**
     * 最小持仓数量（包含）
     */
    @Min(value = 0, message = "最小持仓数量不能小于0")
    Double minPos;

    /**
     * 最大持仓数量（包含）
     */
    @Min(value = 0, message = "最大持仓数量不能小于0")
    Double maxPos;

    /**
     * 最小杠杆倍数（包含）
     */
    @Min(value = 1, message = "最小杠杆倍数不能小于1")
    Double minLever;

    /**
     * 最大杠杆倍数（包含）
     */
    @Min(value = 1, message = "最大杠杆倍数不能小于1")
    Double maxLever;

    /**
     * 只显示有持仓的记录
     */
    Boolean onlyWithPositions;

    /**
     * 只显示有盈亏的记录
     */
    Boolean onlyWithPnl;

    /**
     * 排序字段
     */
    @Pattern(regexp = "^(instId|pos|notionalUsd|pnl|upl|lever|mgnRatio|uTime)$",
            message = "排序字段必须是instId、pos、notionalUsd、pnl、upl、lever、mgnRatio或uTime")
    String sortBy;

    /**
     * 排序方向
     */
    @Pattern(regexp = "^(asc|desc)$", message = "排序方向必须是asc或desc")
    String sortOrder;

    /**
     * 页码（从0开始）
     */
    @Min(value = 0, message = "页码不能小于0")
    Integer page;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小不能小于1")
    Integer size;

    /**
     * 搜索关键词（搜索合约代码）
     */
    String keyword;

    /**
     * 创建请求构建器（设置默认值）
     *
     * @return PositionQueryRequestBuilder
     */
    public static PositionQueryReqBuilder builder() {
        return new PositionQueryReqBuilder()
                .vendor("OKX")
                .sortBy("uTime")
                .sortOrder("desc")
                .page(0)
                .size(50);
    }

    /**
     * 获取默认排序方向
     *
     * @return 排序方向，默认为desc
     */
    public String getSortOrderOrDefault() {
        return null != sortOrder ? sortOrder.toLowerCase() : "desc";
    }

    /**
     * 获取默认排序字段
     *
     * @return 排序字段，默认为uTime
     */
    public String getSortByOrDefault() {
        return null != sortBy ? sortBy : "uTime";
    }

    /**
     * 获取默认页码
     *
     * @return 页码，默认为0
     */
    public int getPageOrDefault() {
        return null != page ? page : 0;
    }

    /**
     * 获取默认每页大小
     *
     * @return 每页大小，默认为50
     */
    public int getSizeOrDefault() {
        return null != size ? size : 50;
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
     * 判断是否只显示有持仓的记录
     *
     * @return true如果只显示有持仓的记录
     */
    public boolean isOnlyWithPositions() {
        return Boolean.TRUE.equals(onlyWithPositions);
    }

    /**
     * 判断是否只显示有盈亏的记录
     *
     * @return true如果只显示有盈亏的记录
     */
    public boolean isOnlyWithPnl() {
        return Boolean.TRUE.equals(onlyWithPnl);
    }

    /**
     * 验证请求参数
     *
     * @return 验证结果
     */
    public boolean isValid() {
        // 验证数值范围
        if (null != minPos && null != maxPos && minPos > maxPos) {
            return false;
        }
        if (null != minLever && null != maxLever && minLever > maxLever) {
            return false;
        }
        return getSizeOrDefault() <= 200; // 每页大小不能超过200
    }

    /**
     * 判断是否有查询条件
     *
     * @return true如果有任何查询条件
     */
    public boolean hasQueryConditions() {
        return (null != instTypes && !instTypes.isEmpty()) ||
                (null != posSides && !posSides.isEmpty()) ||
                (null != instIds && !instIds.isEmpty()) ||
                (null != ccys && !ccys.isEmpty()) ||
                null != minPos ||
                null != maxPos ||
                null != minLever ||
                null != maxLever ||
                isOnlyWithPositions() ||
                isOnlyWithPnl() ||
                (null != keyword && !keyword.trim().isEmpty());
    }
}