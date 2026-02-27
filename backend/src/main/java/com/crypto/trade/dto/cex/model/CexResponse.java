package com.crypto.trade.dto.cex.model;

import java.util.List;

/**
 * CexResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
public abstract class CexResponse<T> {

    /**
     * 获取响应代码
     * <p>
     * "0"表示成功,其他值表示失败。
     * </p>
     *
     * @return 响应代码
     */
    public abstract String getCode();

    /**
     * 获取响应消息
     *
     * @return 响应消息
     */
    public abstract String getMessage();

    /**
     * 获取响应数据
     *
     * @return 数据列表
     */
    public abstract List<T> getData();

    /**
     * 获取请求时间戳
     *
     * @return 请求时间戳
     */
    public abstract Long getRequestTime();

    /**
     * 获取响应时间戳
     *
     * @return 响应时间戳
     */
    public abstract Long getResponseTime();

    // ==================== 便捷判断方法 ====================

    /**
     * 判断响应是否成功
     *
     * @return true=成功, false=失败
     */
    public boolean isSuccess() {
        String code = getCode();
        return ("0".equals(code) || "200".equals(code));
    }

    /**
     * 判断响应是否失败
     *
     * @return true=失败, false=成功
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 判断是否有数据
     *
     * @return true=有数据, false=无数据
     */
    public boolean hasData() {
        List<T> data = getData();
        return null != data && !data.isEmpty();
    }

    /**
     * 获取第一条数据
     *
     * @return 第一条数据, 无数据返回null
     */
    public T getFirstData() {
        List<T> data = getData();
        if (null == data || data.isEmpty()) {
            return null;
        }
        return data.get(0);
    }

    /**
     * 获取数据数量
     *
     * @return 数据条数
     */
    public int getDataCount() {
        List<T> data = getData();
        return null == data ? 0 : data.size();
    }

    /**
     * 获取请求耗时(毫秒)
     *
     * @return 请求耗时
     */
    public Long getDuration() {
        Long reqTime = getRequestTime();
        Long resTime = getResponseTime();
        if (null == reqTime || null == resTime) {
            return null;
        }
        return resTime - reqTime;
    }
}
