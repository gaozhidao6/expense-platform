package com.shixi.expense.notification.dto;

import com.shixi.expense.notification.enums.NotificationType;

/**
 * 业务层创建通知所需的数据。
 *
 * <p>它不是前端请求 DTO，只在后端业务模块之间传递，接收人和 messageKey
 * 必须由可信的后端业务数据生成。</p>
 */
public record NotificationCreateCommand(
        Long recipientId,
        NotificationType type,
        String title,
        String content,
        String businessType,
        Long businessId,
        String messageKey) {
}
