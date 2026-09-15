package com.shixi.expense.report.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 报销单导出查询参数框架。 */
@Data
public class ExpenseReportExportQueryDTO {

    /** 导出关键字，匹配报销编号或标题。 */
    private String keyword;

    /** 导出状态筛选。 */
    private String status;

    /** 导出创建时间起点。 */
    private LocalDateTime startTime;

    /** 导出创建时间终点。 */
    private LocalDateTime endTime;
}
