package com.shixi.expense.invoice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户确认后的正式发票数据，对应已有 invoice 表。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("invoice")
public class Invoice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long expenseItemId;
    private String invoiceCode;
    private String invoiceNumber;
    private String sellerName;
    private LocalDate issuedDate;
    private BigDecimal amount;
    private String fileUrl;
    private String fileHash;
    private String recognitionStatus;
    private String aiRawResult;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
