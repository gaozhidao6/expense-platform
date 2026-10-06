package com.shixi.expense.policy;

import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;

public enum ExpenseType {
    TRAVEL, MEAL, TRANSPORT, OFFICE, OTHER;

    public static ExpenseType require(String value) {
        try {
            return valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效费用类型，不能通过自定义类型绕过限额");
        }
    }
}
