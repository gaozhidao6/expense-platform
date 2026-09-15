package com.shixi.expense.invoice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.shixi.expense.invoice.config.InvoiceRecognitionProperties;
import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 真实 AI 发票识别客户端骨架。
 *
 * <p>配置、输入校验、文件读取和异常类型已经完成；真正调用哪家模型由学习者补充。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.invoice-recognition",
        name = "provider",
        havingValue = "ai")
public class AiInvoiceRecognitionClient implements InvoiceRecognitionClient {

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    private final InvoiceRecognitionProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiInvoiceRecognitionClient(
            InvoiceRecognitionProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("invoiceAiRestClient") RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public InvoiceRecognitionResult recognize(
            InputStream inputStream,
            String contentType) throws IOException {
        validateConfiguration();
        validateInput(inputStream, contentType);
        byte[] fileBytes = readWithinLimit(inputStream, properties.maxFileSize());

        // ==================== AI 核心代码 ====================

        // ---------- 第一阶段：准备附件数据（已完成） ----------
        // 1. 把附件字节转换成 Base64 字符串。
        // 2. 拼接 OpenAI 可以读取的 data URL。
        // 3. 不要把 base64File 或 dataUrl 输出到日志。
        String base64File = Base64.getEncoder().encodeToString(fileBytes);
        String dataUrl = "data:" + contentType + ";base64," + base64File;

        // ---------- 第二阶段：编写识别提示词（已完成） ----------
        // 1. 明确告诉模型需要识别的五个发票字段。
        // 2. 统一金额和日期格式，避免后端解析失败。
        // 3. 要求模型只返回 JSON，不能返回解释或 Markdown。
        // 4. 内容模糊时降低置信度，避免模型随意猜测。
        String prompt = " 你是企业费用报销系统中的发票识别助手。\n" + //
                        "\n" + //
                        "        请识别附件中的发票信息，并严格按照指定的 JSON Schema 返回结果。\n" + //
                        "\n" + //
                        "        要求：\n" + //
                        "        1. invoiceNumber：发票号码，不要把订单号当成发票号码。\n" + //
                        "        2. invoiceDate：开票日期，格式必须为 yyyy-MM-dd。\n" + //
                        "        3. amount：价税合计金额，只返回数字，不要包含人民币符号。\n" + //
                        "        4. merchantName：销售方或商户名称。\n" + //
                        "        5. confidence：整体识别置信度，范围为 0 到 1。\n" + //
                        "        6. 不要输出解释、Markdown 或 JSON 之外的内容。\n" + //
                        "        7. 内容模糊时不要随意猜测，应降低 confidence。";
        // ---------- 第三阶段：限制模型返回结构（已完成） ----------
        // 1. 使用 JSON Schema 定义字段名称和字段类型。
        // 2. additionalProperties=false 表示禁止返回其他字段。
        // 3. required 中的五个字段表示它们都必须返回。
        // 4. confidence 必须在 0 到 1 之间，amount 必须大于 0。
        Map<String, Object> invoiceSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "invoiceNumber", Map.of(
                                "type", "string"
                        ),
                        "invoiceDate", Map.of(
                                "type", "string",
                                "pattern", "^\\d{4}-\\d{2}-\\d{2}$"
                        ),
                        "amount", Map.of(
                                "type", "number",
                                "exclusiveMinimum", 0
                        ),
                        "merchantName", Map.of(
                                "type", "string"
                        ),
                        "confidence", Map.of(
                                "type", "number",
                                "minimum", 0,
                                "maximum", 1
                        )
                ),
                "required", List.of(
                        "invoiceNumber",
                        "invoiceDate",
                        "amount",
                        "merchantName",
                        "confidence"
                )
        );
        // ---------- 第四阶段：根据附件类型构造输入（已完成） ----------
        // 1. PDF 使用 input_file，并提供文件名和 file_data。
        // 2. JPG、PNG 使用 input_image，并把 dataUrl 放入 image_url。
        // 3. detail=high 表示优先识别发票上的小字和数字。
        Map<String, Object> fileContent;

        if ("application/pdf".equalsIgnoreCase(contentType)) {
            fileContent = Map.of(
                    "type", "input_file",
                    "filename", "invoice.pdf",
                    "file_data", dataUrl
            );
        } else {
            fileContent = Map.of(
                    "type", "input_image",
                    "image_url", dataUrl,
                    "detail", "high"
            );
        }
        // ---------- 第五阶段：构造 Responses API 请求体（已完成） ----------
        // 1. model 使用配置中的 gpt-5.6-luna。
        // 2. store=false 表示不主动保存本次识别响应。
        // 3. input 同时放入文字提示和发票附件。
        // 4. text.format 使用严格的 json_schema。
        Map<String, Object> requestBody = Map.of(
                "model", properties.model(),
                "store", false,
                "input", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_text",
                                                "text", prompt
                                        ),
                                        fileContent
                                )
                        )
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "invoice_recognition",
                                "strict", true,
                                "schema", invoiceSchema
                        )
                )
        );
        // ---------- 第六阶段：发送 HTTP 请求（下一步写这里） ----------
        // 1. 请求地址使用 properties.baseUrl() 加 /responses。
        // 2. 请求方法使用 POST，Content-Type 使用 application/json。
        // 3. Authorization 请求头填写 Bearer + properties.apiKey()。
        // 4. 请求体使用上面已经准备好的 requestBody。
        // 5. 连接和读取超时分别使用 properties 中的两个超时配置。
        // 6. 不要把 API Key、dataUrl、发票内容或完整响应写入日志。
        // 7. RestClient 由 Spring Bean 统一创建并复用，这里只负责发送业务请求。
        String responseBody = sendRequest(requestBody);

        // ---------- 第七阶段：处理 HTTP 错误（已完成） ----------
        // 1. 401 或 403：抛出“API Key 无效或没有模型权限”。
        // 2. 429：抛出“AI 请求过于频繁，请稍后重试”。
        // 3. 5xx：抛出“AI 服务暂时不可用”。
        // 4. 连接失败或超时：包装成 InvoiceRecognitionException。

        // ---------- 第八阶段：解析并返回结果（已完成） ----------
        return parseResponse(responseBody);
    }

    /** 发送已经构造好的请求体，并将网络层异常转换为业务异常。 */
    private String sendRequest(Map<String, Object> requestBody) {
        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new InvoiceRecognitionException("AI 没有返回响应内容");
            }
            return responseBody;
        } catch (RestClientResponseException exception) {
            throw translateHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw new InvoiceRecognitionException(
                    "连接 AI 服务失败或请求超时，请稍后重试",
                    exception);
        } catch (RestClientException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                throw new InvoiceRecognitionException(
                        "连接 AI 服务失败或请求超时，请稍后重试",
                        exception);
            }
            throw new InvoiceRecognitionException(
                    "调用 AI 服务失败，请稍后重试",
                    exception);
        }
    }

    /** 解析 Responses API 外层结构和其中的发票 JSON。 */
    private InvoiceRecognitionResult parseResponse(String responseBody) {
        // 1. responseBody 是 Responses API 的外层 JSON，不是最终的发票 JSON。
        // 2. 先用 objectMapper.readTree 将外层 JSON 转换成 JsonNode 树。
        // 3. 从根节点进入 output 数组；数组可能包含多个输出项，不能只写死 output[0]。
        // 4. 对每个输出项继续进入 content 数组，寻找 type=output_text 的内容。
        // 5. 找到后读取 text；text 才是模型按照 JSON Schema 生成的发票 JSON 字符串。
        // 6. 使用标签 break 同时退出两层循环，避免后续内容覆盖已经找到的结果。
        // 7. 即使使用了 JSON Schema，也要检查 outputText 是否存在，不能完全信任外部响应。
        // 8. 再次使用 objectMapper.readTree 解析 outputText，得到真正的发票字段节点。
        // 9. 字符串字段必须存在且非空；金额和置信度字段必须是 JSON 数字。
        // 10. invoiceDate 用 LocalDate.parse 转成日期，要求模型返回 yyyy-MM-dd。
        // 11. amount 和 confidence 用 decimalValue 转成 BigDecimal，避免浮点数精度损失。
        // 12. 最后一次性构造不可变的 InvoiceRecognitionResult 并返回，不在客户端修改数据库。
        // 13. JSON 或日期格式错误统一包装成 InvoiceRecognitionException，避免上层理解底层解析异常。
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String outputText = null;

            findOutputText:
            for (JsonNode outputNode : responseJson.path("output")) {
                for (JsonNode contentNode : outputNode.path("content")) {
                    if ("output_text".equals(contentNode.path("type").asText())) {
                        outputText = contentNode.path("text").asText();
                        break findOutputText;
                    }
                }
            }

            if (outputText == null || outputText.isBlank()) {
                throw new InvoiceRecognitionException("AI 响应中缺少发票识别结果");
            }

            JsonNode invoiceJson = objectMapper.readTree(outputText);
            String invoiceNumber = requireText(invoiceJson, "invoiceNumber");
            LocalDate invoiceDate = LocalDate.parse(
                    requireText(invoiceJson, "invoiceDate"));
            BigDecimal amount = requireDecimal(invoiceJson, "amount");
            String merchantName = requireText(invoiceJson, "merchantName");
            BigDecimal confidence = requireDecimal(invoiceJson, "confidence");

            return new InvoiceRecognitionResult(
                    invoiceNumber,
                    invoiceDate,
                    amount,
                    merchantName,
                    confidence);
        } catch (JsonProcessingException | DateTimeParseException exception) {
            throw new InvoiceRecognitionException(
                    "AI 返回的发票识别结果格式不正确",
                    exception);
        }
    }

    /** 读取必填字符串；字段缺失、为 null 或空白时立即终止解析。 */
    private String requireText(JsonNode invoiceJson, String fieldName) {
        JsonNode field = invoiceJson.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            throw new InvoiceRecognitionException(
                    "AI 返回的发票识别结果缺少有效字段：" + fieldName);
        }
        return field.asText();
    }

    /** 读取必填数字；拒绝把缺失值或字符串悄悄转换成数字零。 */
    private BigDecimal requireDecimal(JsonNode invoiceJson, String fieldName) {
        JsonNode field = invoiceJson.get(fieldName);
        if (field == null || !field.isNumber()) {
            throw new InvoiceRecognitionException(
                    "AI 返回的发票识别结果缺少有效字段：" + fieldName);
        }
        return field.decimalValue();
    }

    /** 沿异常原因链查找指定类型，兼容不同 HTTP 请求工厂的异常包装方式。 */
    private boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        Throwable current = exception;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 将外部 AI 的 HTTP 状态码转换成系统内部可以理解的中文异常。
     * 不拼接响应正文，避免错误信息中泄露发票内容或服务端细节。
     */
    private InvoiceRecognitionException translateHttpException(
            RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();

        if (statusCode == 401 || statusCode == 403) {
            return new InvoiceRecognitionException(
                    "AI API Key 无效或当前账号没有模型权限",
                    exception);
        }
        if (statusCode == 429) {
            return new InvoiceRecognitionException(
                    "AI 请求过于频繁，请稍后重试",
                    exception);
        }
        if (statusCode >= 500) {
            return new InvoiceRecognitionException(
                    "AI 服务暂时不可用，请稍后重试",
                    exception);
        }
        return new InvoiceRecognitionException(
                "AI 请求失败，HTTP 状态码：" + statusCode,
                exception);
    }

    /** 启用真实 AI 时尽早发现遗漏配置，避免发出无效请求。 */
    private void validateConfiguration() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new InvoiceRecognitionException("未配置 INVOICE_AI_API_KEY");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new InvoiceRecognitionException("未配置 AI 服务地址");
        }
        if (properties.model() == null || properties.model().isBlank()) {
            throw new InvoiceRecognitionException("未配置 AI 模型名称");
        }
    }

    private void validateInput(InputStream inputStream, String contentType) {
        if (inputStream == null) {
            throw new IllegalArgumentException("发票文件输入流不能为空");
        }
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("AI 识别仅支持 JPG、PNG 或 PDF 发票");
        }
    }

    /** 限量读取文件，避免外部调用前把超大附件全部加载进内存。 */
    private byte[] readWithinLimit(InputStream inputStream, long maxBytes)
            throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                total += length;
                if (total > maxBytes) {
                    throw new InvoiceRecognitionException("发票文件超过 AI 识别大小限制");
                }
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }
}
