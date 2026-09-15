package com.shixi.expense.invoice.service;

import com.shixi.expense.attachment.service.AttachmentService;
import com.shixi.expense.attachment.vo.AttachmentDownload;
import com.shixi.expense.invoice.client.InvoiceRecognitionClient;
import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import com.shixi.expense.invoice.vo.InvoiceSuggestionVO;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 发票识别业务：读取有权限访问的附件，并返回 AI 识别建议。 */
@Service
public class InvoiceRecognitionService {

    private static final int MAX_INVOICE_NUMBER_LENGTH = 80;
    private static final int MAX_MERCHANT_NAME_LENGTH = 200;

    private final AttachmentService attachmentService;
    private final InvoiceRecognitionClient recognitionClient;
    private final InvoiceSuggestionService suggestionService;
    private final Set<String> recognizingAttachments = ConcurrentHashMap.newKeySet();

    public InvoiceRecognitionService(
            AttachmentService attachmentService,
            InvoiceRecognitionClient recognitionClient,
            InvoiceSuggestionService suggestionService) {
        this.attachmentService = attachmentService;
        this.recognitionClient = recognitionClient;
        this.suggestionService = suggestionService;
    }

    public InvoiceSuggestionVO recognize(
            Long reportId,
            Long attachmentId) {
        // 报销单 ID 和附件 ID 必须是有效正数。
        if (reportId == null
                || reportId <= 0
                || attachmentId == null
                || attachmentId <= 0) {
            throw new IllegalArgumentException("报销单 ID 或附件 ID 不合法");
        }
        String recognitionKey = reportId + ":" + attachmentId;
        if (!recognizingAttachments.add(recognitionKey)) {
            throw new IllegalStateException("该附件正在识别中，请勿重复提交");
        }

        try {
            // 复用附件下载服务完成用户权限、报销单和附件归属校验。
            AttachmentDownload attachmentDownload =
                    attachmentService.download(reportId, attachmentId);
            // try-with-resources 保证识别完成或异常时输入流都会关闭。
            try (InputStream inputStream =
                    attachmentDownload.resource().getInputStream()) {
                // 把文件内容和类型交给当前配置的识别客户端。
                InvoiceRecognitionResult result = recognitionClient.recognize(
                        inputStream,
                        attachmentDownload.contentType());
                // 只返回经过校验的建议，不直接修改报销单或明细。
                InvoiceRecognitionResult validatedResult = validateResult(result);
                // 每次成功识别都新增一条待确认建议，保留识别历史。
                return suggestionService.createSuggestion(
                        reportId,
                        attachmentId,
                        validatedResult);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "读取或识别发票附件失败",
                        exception);
            }
        } finally {
            // 无论成功还是失败都释放标记，允许用户稍后重试。
            recognizingAttachments.remove(recognitionKey);
        }
    }

    private InvoiceRecognitionResult validateResult(
            InvoiceRecognitionResult result) {
        // 识别服务必须返回结构化结果。
        if (result == null) {
            throw new IllegalStateException("AI 未返回发票识别结果");
        }
        // 发票号码和商户名称是展示及人工核对所需的基本字段。
        if (result.invoiceNumber() == null
                || result.invoiceNumber().isBlank()
                || result.invoiceNumber().length() > MAX_INVOICE_NUMBER_LENGTH) {
            throw new IllegalStateException("AI 返回的发票号码不合法");
        }
        if (result.merchantName() == null
                || result.merchantName().isBlank()
                || result.merchantName().length() > MAX_MERCHANT_NAME_LENGTH) {
            throw new IllegalStateException("AI 返回的商户名称不合法");
        }
        // 发票日期不能为空且不能晚于当前日期。
        LocalDate invoiceDate = result.invoiceDate();
        if (invoiceDate == null || invoiceDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException("AI 返回的发票日期不合法");
        }
        // 发票金额必须大于 0。
        BigDecimal amount = result.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("AI 返回的发票金额不合法");
        }
        // 置信度使用闭区间 0 到 1。
        BigDecimal confidence = result.confidence();
        if (confidence == null
                || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("AI 返回的置信度不合法");
        }
        // 校验通过后原样返回识别建议。
        return result;
    }
}
