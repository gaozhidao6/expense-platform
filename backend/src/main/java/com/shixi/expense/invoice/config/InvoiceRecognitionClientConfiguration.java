package com.shixi.expense.invoice.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 统一创建并配置真实 AI 使用的 HTTP 客户端。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.invoice-recognition",
        name = "provider",
        havingValue = "ai")
public class InvoiceRecognitionClientConfiguration {

    @Bean
    @Qualifier("invoiceAiRestClient")
    public RestClient invoiceAiRestClient(InvoiceRecognitionProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(
                        "Authorization",
                        "Bearer " + properties.apiKey())
                .build();
    }
}
