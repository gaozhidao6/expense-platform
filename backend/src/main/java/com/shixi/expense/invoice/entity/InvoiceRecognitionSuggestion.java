package com.shixi.expense.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 发票识别建议实体。
 *
 * <p>它保存的是“待人工核对的数据”，不是已经生效的费用明细。每次识别都新增一条记录，
 * 因而可以保留历史，不覆盖先前结果。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("invoice_recognition_suggestion")
public class InvoiceRecognitionSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 建议所属报销单。查询和权限校验都需要这个字段。 */
    private Long reportId;

    /** 准备写入的费用明细；刚识别时可以为空，确认时必须指定。 */
    private Long expenseItemId;

    /** 本次识别所读取的原始附件。 */
    private Long attachmentId;

    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private String merchantName;

    /** 取值范围为 0～1。 */
    private BigDecimal confidence;

    /** 保存 InvoiceSuggestionStatus 的枚举名称。 */
    private String status;

    /** 发起识别的用户 ID，必须从登录态取得，不能相信前端传值。 */
    private Long createdBy;

    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime rejectedAt;

    /** 乐观锁版本；更新成功后由 MyBatis-Plus 自动加一。 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
