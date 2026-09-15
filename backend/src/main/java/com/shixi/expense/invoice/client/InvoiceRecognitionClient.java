package com.shixi.expense.invoice.client;

import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import java.io.IOException;
import java.io.InputStream;

/** 发票识别客户端抽象，业务层不依赖具体 AI 服务商。 */
public interface InvoiceRecognitionClient {

    InvoiceRecognitionResult recognize(
            InputStream inputStream,
            String contentType) throws IOException;
}
