package com.shixi.expense.notification.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 站内通知实体，对应 notification 表。 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收通知的用户 ID。 */
    private Long recipientId;

    /** 通知类型，例如 APPROVAL_TODO、APPROVED、REJECTED、PAID。 */
    private String type;

    private String title;
    private String content;

    /** 关联业务类型，当前主要为 EXPENSE_REPORT。 */
    private String businessType;

    /** 关联业务主键，当前为报销单 ID。 */
    private Long businessId;

    /** 同一接收人的消息幂等键，用于防止重复创建通知。 */
    private String messageKey;

    /** 0 表示未读，1 表示已读。 */
    private Integer isRead;

    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
