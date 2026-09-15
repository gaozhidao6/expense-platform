package com.shixi.expense.notification.enums;

/**
 * 站内通知类型。
 *
 * <p>枚举名称直接作为数据库和接口中的类型值，避免业务代码散落易拼错的字符串。</p>
 */
public enum NotificationType {
    APPROVAL_TODO,
    APPROVED,
    REJECTED,
    PAID;

    /** 将接口传入的字符串转换为受控枚举，并统一处理空格和大小写。 */
    public static NotificationType from(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("通知类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的通知类型：" + value, exception);
        }
    }
}
