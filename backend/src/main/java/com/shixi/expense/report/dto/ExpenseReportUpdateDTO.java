package com.shixi.expense.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改报销单草稿基本信息时接收的参数。 */
@Data
public class ExpenseReportUpdateDTO {

    /** 只允许修改标题。 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 150, message = "标题不能超过150个字符")
    private String title;

    /** 只允许修改说明。 */
    @Size(max = 1000, message = "说明不能超过1000个字符")
    private String description;

    // 注意：这里不接收 applicantId、reportNo、status、totalAmount 和 items。
}
