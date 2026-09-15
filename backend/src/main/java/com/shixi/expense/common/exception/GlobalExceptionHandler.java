package com.shixi.expense.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.shixi.expense.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/** 全局异常处理器：把后端异常统一转换成 JSON 响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 登录凭据错误统一返回 401，而不是被兜底异常处理成 500。 */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailure(
            RuntimeException exception) {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getCode()).body(response);
    }

    /** 处理业务代码主动抛出的自定义异常。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode == null) {
            errorCode = ErrorCode.INTERNAL_ERROR;
        }
        ApiResponse<Void> response = ApiResponse.failure(
                errorCode.getCode(), exception.getMessage());
        return ResponseEntity.status(errorCode.getCode()).body(response);
    }

    /** 处理 Service 层的无权访问异常。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception) {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.FORBIDDEN.getCode(), exception.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getCode())
                .body(response);
    }

    /** 处理参数值错误或非法业务状态。 */
    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = ErrorCode.BAD_REQUEST.getMessage();
        }
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.BAD_REQUEST.getCode(), message);
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getCode())
                .body(response);
    }

    /** 处理 DTO 的 @Valid 校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.BAD_REQUEST.getCode(), message);
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getCode())
                .body(response);
    }

    /** 处理请求参数类型转换失败，例如 page=abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.BAD_REQUEST.getCode(),
                "请求参数类型错误：" + exception.getName());
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getCode())
                .body(response);
    }

    /** 兜底处理未知异常，不向前端暴露内部堆栈或异常消息。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(
            Exception exception) {
        log.error("未处理的系统异常", exception);
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getCode())
                .body(response);
    }
}
