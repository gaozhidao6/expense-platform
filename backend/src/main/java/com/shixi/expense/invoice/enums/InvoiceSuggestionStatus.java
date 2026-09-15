package com.shixi.expense.invoice.enums;

/**
 * 发票识别建议状态。
 *
 * <p>数据库保存枚举名称。业务代码使用枚举，可以避免到处手写字符串造成拼写错误。</p>
 */
public enum InvoiceSuggestionStatus {

    /** AI 已给出建议，等待用户检查、修改并确认。 */
    PENDING_CONFIRMATION,

    /** 用户已确认，建议内容已经写入正式费用明细。 */
    CONFIRMED,

    /** 用户明确拒绝了本次建议，正式费用明细不会被修改。 */
    REJECTED
}
