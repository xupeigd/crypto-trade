package com.crypto.trade.model.ctm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ApiResponse
 * 响应对象
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String code;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean success;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer total;

    public static <T> ApiResponse<T> ok(T t) {
        return ok(t, null);
    }

    public static <T> ApiResponse<T> ok(T t, Integer total) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = "0";
        response.success = true;
        response.message = "操作成功";
        response.data = t;
        response.total = total;
        return response;
    }

    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.message = message;
        response.code = "1";
        response.success = false;
        return response;
    }
}
