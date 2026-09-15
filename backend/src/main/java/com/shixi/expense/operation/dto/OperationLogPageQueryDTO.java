package com.shixi.expense.operation.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 操作日志分页查询参数框架。 */
@Data
public class OperationLogPageQueryDTO {
    @Min(value = 1, message = "页码必须大于等于 1")
    private long page = 1;
    @Min(value = 1, message = "每页数量必须大于等于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private long size = 10;
    /** 操作人 ID，可选。 */
    private Long operatorId;
    /** 操作类型，可选。 */
    private String operation;
    /** 业务类型，可选。 */
    private String businessType;
    /** 操作结果：SUCCESS 或 FAILURE。 */
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
