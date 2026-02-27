package com.crypto.trade.dto.cex.okx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OkxApiResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@NoArgsConstructor
public class OkxApiResponse<T> {

    /**
     * 响应代码
     * "0" 表示成功，其他表示失败
     */
    @JsonProperty("code")
    private String code;

    /**
     * 响应消息
     */
    @JsonProperty("msg")
    private String msg;

    /**
     * 数据数组
     */
    @JsonProperty("data")
    private List<T> data;

    /**
     * 请求开始时间戳
     */
    @JsonProperty("inTime")
    private String inTime;

    /**
     * 响应结束时间戳
     */
    @JsonProperty("outTime")
    private String outTime;

    public static <T> OkxApiResponse<T> fail(String message) {
        OkxApiResponse<T> okxApiResponse = new OkxApiResponse<>();
        okxApiResponse.setCode("1");
        okxApiResponse.setMsg(message);
        return okxApiResponse;
    }

    /**
     * 判断是否为成功响应
     */
    public boolean isSuccess() {
        return "0".equals(code);
    }

    /**
     * 判断是否为失败响应
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 获取错误信息
     * 如果成功则返回null
     */
    public String getErrorMessage() {
        return isSuccess() ? null : msg;
    }

    /**
     * 获取第一个数据项（如果存在）
     */
    public T getFirstData() {
        if (null != data && !data.isEmpty()) {
            return data.get(0);
        }
        return null;
    }

    /**
     * 判断是否有数据
     */
    public boolean hasData() {
        return null != data && !data.isEmpty();
    }

    /**
     * 获取数据数量
     */
    public int getDataCount() {
        return null != data ? data.size() : 0;
    }

}