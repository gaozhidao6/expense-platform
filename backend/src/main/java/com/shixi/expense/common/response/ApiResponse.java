package com.shixi.expense.common.response;

import com.shixi.expense.common.exception.ErrorCode;

import lombok.Data;

/** 统一 JSON 响应结构，文件下载接口不使用此对象。 */
@Data
public class ApiResponse<T> {

    /** 业务状态码。 */
    private int code;

    /** 面向前端的提示信息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /** 成功响应工厂方法框架。 */
    public static <T> ApiResponse<T> success(T data) {
        //设置成功状态码、提示信息和 data。
        ApiResponse<T> response = new ApiResponse<>();

        response.setCode(200);
        response.setMessage("操作成功");
        response.setData(data);
        return response;
    }
    public static ApiResponse<Void> success() {
        return success(null);
    }

    /** 失败响应工厂方法框架。 */
    public static <T> ApiResponse<T> failure(int code, String message) {
        //设置失败状态码、提示信息，data 默认为空。
        ApiResponse<T> response = new ApiResponse<>();

        response.setCode(code);
        response.setMessage(message);
        response.setData(null);
        return response;
    }

    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode) {

        return failure(
                errorCode.getCode(),
                errorCode.getMessage()
        );
    }
}
