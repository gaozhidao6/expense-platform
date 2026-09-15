package com.shixi.expense.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 当前登录用户的通知分页查询参数。 */
@Data
public class NotificationPageQueryDTO {

    @Min(value = 1, message = "页码必须大于等于 1")
    private long page = 1;

    @Min(value = 1, message = "每页数量必须大于等于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private long size = 10;

    /** 可选通知类型，例如 APPROVAL_TODO、APPROVED、REJECTED、PAID。 */
    private String type;

    /** 可选已读状态：0 表示未读，1 表示已读。 */
    @Min(value = 0, message = "已读状态只能是 0 或 1")
    @Max(value = 1, message = "已读状态只能是 0 或 1")
    private Integer isRead;
}
