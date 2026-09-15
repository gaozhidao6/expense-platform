package com.shixi.expense.invoice.client;

import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 本地学习用识别客户端，不调用任何外部 AI 服务。 */
@Component
@ConditionalOnProperty(
        prefix = "app.invoice-recognition",
        name = "provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockInvoiceRecognitionClient implements InvoiceRecognitionClient {

    @Override
    public InvoiceRecognitionResult recognize(
            InputStream inputStream,
            String contentType) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("发票文件输入流不能为空");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("发票文件类型不能为空");
        }

        return new InvoiceRecognitionResult(
                "MOCK-20260914-001",
                LocalDate.of(2026, 9, 14),
                new BigDecimal("100.00"),
                "示例商户",
                new BigDecimal("0.95"));
    }
}
