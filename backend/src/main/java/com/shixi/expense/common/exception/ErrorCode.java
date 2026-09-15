package com.shixi.expense.common.exception;

/** 系统统一错误码定义框架。 */
public enum ErrorCode {

    /** 请求参数错误。 */
    BAD_REQUEST(400, "请求参数错误"),
    /** 未登录或认证失效。 */
    UNAUTHORIZED(401, "未登录或认证已失效"),
    /** 没有访问权限。 */
    FORBIDDEN(403, "没有操作权限"),
    /** 数据不存在。 */
    NOT_FOUND(404, "数据不存在"),
    /** 状态冲突或乐观锁冲突。 */
    CONFLICT(409, "数据状态发生冲突"),
    /** 未知系统错误。 */
    INTERNAL_ERROR(500, "系统内部异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
