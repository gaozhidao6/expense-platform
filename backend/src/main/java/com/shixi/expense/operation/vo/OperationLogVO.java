package com.shixi.expense.operation.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 操作日志列表响应对象，不直接暴露数据库实体。 */
@Data
public class OperationLogVO {
    private Long id;
    private Long operatorId;
    private String operation;
    private String businessType;
    private Long businessId;
    /** 用于把一次请求与审计记录关联起来。 */
    private String requestId;
    private String httpMethod;
    private String requestPath;
    private String ipAddress;
    private String result;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createdAt;
}
