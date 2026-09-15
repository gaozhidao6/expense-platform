package com.shixi.expense.operation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 操作日志实体，对应 operation_log 表。 */
@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String operation;
    private String businessType;
    private Long businessId;
    private String requestId;
    private String httpMethod;
    private String requestPath;
    private String ipAddress;
    private String result;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createdAt;
}
