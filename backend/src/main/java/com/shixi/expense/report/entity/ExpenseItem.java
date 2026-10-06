package com.shixi.expense.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 费用明细实体，对应数据库中的 expense_item。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("expense_item")
public class ExpenseItem {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属报销单 ID，形成一张报销单对应多条明细的关系。 */
    private Long reportId;

    /** 费用实际发生日期。 */
    private LocalDate expenseDate;

    /** 费用类型，例如 TRAVEL、MEAL、TRANSPORT、OFFICE、OTHER。 */
    private String expenseType;

    /** 商户名称，可以为空。 */
    private String merchant;

    /** 费用说明。 */
    private String description;

    /** 本条明细金额，使用 BigDecimal 避免精度问题。 */
    private BigDecimal amount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String overLimitReason;

    /** Submitted limit snapshot, cleared when editing a draft or rejected report. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal appliedLimitAmount;

    /** 税额。 */
    private BigDecimal taxAmount;

    /** 明细展示顺序。 */
    private Integer sortOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：删除后保留历史数据，但默认查询不到。 */
    @TableLogic
    private Integer deleted;
}
