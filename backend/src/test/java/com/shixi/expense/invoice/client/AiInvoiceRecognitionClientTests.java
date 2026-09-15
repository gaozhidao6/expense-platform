package com.shixi.expense.invoice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.invoice.config.InvoiceRecognitionClientConfiguration;
import com.shixi.expense.invoice.config.InvoiceRecognitionProperties;
import com.shixi.expense.invoice.dto.InvoiceRecognitionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

class AiInvoiceRecognitionClientTests {

    @Test
    void rejectsMissingApiKeyBeforeCallingExternalService() {
        AiInvoiceRecognitionClient client = client("", 1024);

        assertThrows(
                InvoiceRecognitionException.class,
                () -> client.recognize(
                        new ByteArrayInputStream(new byte[] {1}),
                        "image/png"));
    }

    @Test
    void rejectsUnsupportedFileType() {
        AiInvoiceRecognitionClient client = client("test-key", 1024);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.recognize(
                        new ByteArrayInputStream(new byte[] {1}),
                        "text/plain"));
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        AiInvoiceRecognitionClient client = client("test-key", 2);

        assertThrows(
                InvoiceRecognitionException.class,
                () -> client.recognize(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "application/pdf"));
    }

    @Test
    void convertsConnectionFailureToRecognitionException() {
        AiInvoiceRecognitionClient client = client(
                "http://127.0.0.1:1",
                "test-key",
                1024);

        InvoiceRecognitionException exception = assertThrows(
                InvoiceRecognitionException.class,
                () -> client.recognize(
                        new ByteArrayInputStream(new byte[] {1, 2, 3}),
                        "image/jpeg"));

        assertEquals(
                "连接 AI 服务失败或请求超时，请稍后重试",
                exception.getMessage());
    }

    @Test
    void convertsReadTimeoutToRecognitionException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, "{\"output\":[]}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();

        try {
            AiInvoiceRecognitionClient client = client(
                    serverBaseUrl(server),
                    "test-key",
                    Duration.ofSeconds(1),
                    Duration.ofMillis(50),
                    1024);

            InvoiceRecognitionException exception = assertThrows(
                    InvoiceRecognitionException.class,
                    () -> client.recognize(
                            new ByteArrayInputStream(new byte[] {1}),
                            "image/jpeg"));

            assertEquals(
                    "连接 AI 服务失败或请求超时，请稍后重试",
                    exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parsesSuccessfulResponsesApiResult() throws Exception {
        String invoiceText = """
                {"invoiceNumber":"INV-2026-001","invoiceDate":"2026-09-15","amount":88.50,"merchantName":"测试商户","confidence":0.96}
                """;
        String responseBody = responsesBody(invoiceText);
        HttpServer server = startServer(200, responseBody);

        try {
            AiInvoiceRecognitionClient client = client(serverBaseUrl(server), "test-key", 1024);

            InvoiceRecognitionResult result = client.recognize(
                    new ByteArrayInputStream(new byte[] {1, 2, 3}),
                    "image/jpeg");

            assertEquals("INV-2026-001", result.invoiceNumber());
            assertEquals(LocalDate.of(2026, 9, 15), result.invoiceDate());
            assertEquals(0, new BigDecimal("88.50").compareTo(result.amount()));
            assertEquals("测试商户", result.merchantName());
            assertEquals(new BigDecimal("0.96"), result.confidence());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsResponseWithoutOutputText() throws Exception {
        HttpServer server = startServer(200, "{\"output\":[]}");

        try {
            AiInvoiceRecognitionClient client = client(serverBaseUrl(server), "test-key", 1024);

            InvoiceRecognitionException exception = assertThrows(
                    InvoiceRecognitionException.class,
                    () -> client.recognize(
                            new ByteArrayInputStream(new byte[] {1}),
                            "image/png"));

            assertEquals("AI 响应中缺少发票识别结果", exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMalformedInvoiceDate() throws Exception {
        String invoiceText = """
                {"invoiceNumber":"INV-1","invoiceDate":"错误日期","amount":10,"merchantName":"测试商户","confidence":0.9}
                """;
        String responseBody = responsesBody(invoiceText);
        HttpServer server = startServer(200, responseBody);

        try {
            AiInvoiceRecognitionClient client = client(serverBaseUrl(server), "test-key", 1024);

            InvoiceRecognitionException exception = assertThrows(
                    InvoiceRecognitionException.class,
                    () -> client.recognize(
                            new ByteArrayInputStream(new byte[] {1}),
                            "application/pdf"));

            assertEquals("AI 返回的发票识别结果格式不正确", exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "401, AI API Key 无效或当前账号没有模型权限",
        "403, AI API Key 无效或当前账号没有模型权限",
        "429, AI 请求过于频繁，请稍后重试",
        "503, AI 服务暂时不可用，请稍后重试",
        "400, AI 请求失败，HTTP 状态码：400"
    })
    void convertsHttpErrorsToReadableMessages(int status, String expectedMessage)
            throws Exception {
        HttpServer server = startServer(status, "{\"error\":\"test error\"}");

        try {
            AiInvoiceRecognitionClient client = client(serverBaseUrl(server), "test-key", 1024);

            InvoiceRecognitionException exception = assertThrows(
                    InvoiceRecognitionException.class,
                    () -> client.recognize(
                            new ByteArrayInputStream(new byte[] {1}),
                            "image/jpeg"));

            assertEquals(expectedMessage, exception.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(int status, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> respond(exchange, status, responseBody));
        server.start();
        return server;
    }

    private String responsesBody(String invoiceText) throws IOException {
        return new ObjectMapper().writeValueAsString(Map.of(
                "output", List.of(Map.of(
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", invoiceText))))));
    }

    private void respond(HttpExchange exchange, int status, String responseBody)
            throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private String serverBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private AiInvoiceRecognitionClient client(String apiKey, long maxFileSize) {
        return client("https://api.example.com", apiKey, maxFileSize);
    }

    private AiInvoiceRecognitionClient client(
            String baseUrl,
            String apiKey,
            long maxFileSize) {
        return client(
                baseUrl,
                apiKey,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                maxFileSize);
    }

    private AiInvoiceRecognitionClient client(
            String baseUrl,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout,
            long maxFileSize) {
        InvoiceRecognitionProperties properties = new InvoiceRecognitionProperties(
                "ai",
                baseUrl,
                apiKey,
                "gpt-5.6-luna",
                connectTimeout,
                readTimeout,
                maxFileSize);
        RestClient restClient = new InvoiceRecognitionClientConfiguration()
                .invoiceAiRestClient(properties);
        return new AiInvoiceRecognitionClient(
                properties,
                new ObjectMapper(),
                restClient);
    }
}
