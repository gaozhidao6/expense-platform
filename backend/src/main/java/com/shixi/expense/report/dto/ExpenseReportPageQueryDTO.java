package com.shixi.expense.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.time.LocalDateTime;

/** 报销单分页查询参数。 */
@Data
public class ExpenseReportPageQueryDTO {

    /** 当前页，从 1 开始。 */
    @Min(value = 1, message = "页码必须大于等于 1")
    private long page = 1;

    /** 每页数量，限制最大 100，避免一次查询过多数据。 */
    @Min(value = 1, message = "每页数量必须大于等于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private long size = 10;

    /** 可选关键字，匹配报销编号或标题。 */
    private String keyword;

    /** 可选状态，例如 DRAFT、APPROVING、COMPLETED。 */
    private String status;

    /** 可选创建时间起点。 */
    private LocalDateTime startTime;

    /** 可选创建时间终点。 */
    private LocalDateTime endTime;
}
