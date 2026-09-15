package com.shixi.expense.invoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/** 发票识别配置。 */
@ConfigurationProperties(prefix = "app.invoice-recognition")
public record InvoiceRecognitionProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        long maxFileSize) {

    public InvoiceRecognitionProperties {
        provider = valueOrDefault(provider, "mock");
        baseUrl = valueOrDefault(baseUrl, "https://api.example.com");
        model = valueOrDefault(model, "gpt-5.6-luna");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        maxFileSize = maxFileSize <= 0 ? 10 * 1024 * 1024L : maxFileSize;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
