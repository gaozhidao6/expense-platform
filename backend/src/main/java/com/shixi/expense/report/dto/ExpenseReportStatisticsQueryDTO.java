package com.shixi.expense.report.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 报销单统计查询参数框架。 */
@Data
public class ExpenseReportStatisticsQueryDTO {

    /** 统计时间范围起点。 */
    private LocalDateTime startTime;

    /** 统计时间范围终点。 */
    private LocalDateTime endTime;
}
