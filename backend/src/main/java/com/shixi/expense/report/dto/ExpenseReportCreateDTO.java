package com.shixi.expense.report.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 创建报销单草稿时接收的参数。 */
@Data
public class ExpenseReportCreateDTO {

    @NotBlank(message = "title must not be blank")
    @Size(max = 150, message = "title must not exceed 150 characters")
    private String title;

    @Size(max = 1000, message = "description must not exceed 1000 characters")
    private String description;

    /** 币种暂时只作为请求字段保留，Service 中还要校验允许值。 */
    @Size(max = 3, message = "currency must not exceed 3 characters")
    private String currency = "CNY";

    /** @Valid 让每条费用明细上的校验注解生效。 */
    @Valid
    private List<ExpenseItemDTO> items = new ArrayList<>();

    // 注意：这里故意不接收 applicantId、status 和 totalAmount。
    // applicantId 应从当前登录用户中取得，status 由后端设置，totalAmount 由后端汇总。
}
