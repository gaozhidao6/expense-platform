package com.shixi.expense.policy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("expense_policy")
public class ExpensePolicy {
    @TableId(type = IdType.INPUT)
    private String expenseType;
    private BigDecimal limitAmount;
    private Boolean enabled;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
