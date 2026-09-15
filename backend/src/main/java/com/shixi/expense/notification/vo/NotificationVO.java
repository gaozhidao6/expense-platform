package com.shixi.expense.notification.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 返回给前端的站内通知对象，不直接暴露数据库实体。 */
@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String businessType;
    private Long businessId;
    private Integer isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
