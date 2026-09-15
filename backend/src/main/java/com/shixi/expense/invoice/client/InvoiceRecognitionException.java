package com.shixi.expense.invoice.client;

/** 调用外部 AI、解析响应或校验协议失败时使用的统一异常。 */
public class InvoiceRecognitionException extends RuntimeException {

    public InvoiceRecognitionException(String message) {
        super(message);
    }

    public InvoiceRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
