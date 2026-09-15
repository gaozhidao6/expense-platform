package com.shixi.expense.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 经理审批或财务审核时接收的操作意见。 */
@Data
public class ApprovalActionDTO {

    /** 通过时可以为空，驳回时建议要求填写原因。 */
    @Size(max = 1000, message = "审批意见不能超过1000个字符")
    private String comment;

    // operatorId、approverId、fromStatus 和 toStatus 必须由登录态、数据库任务
    // 与后端状态转换规则确定，不能由客户端提交。
}
