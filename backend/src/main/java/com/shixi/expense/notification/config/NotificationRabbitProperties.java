package com.shixi.expense.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** RabbitMQ 通知配置：Spring 会把 application.yml 中对应的值自动填入这个对象。 */
@ConfigurationProperties(prefix = "app.notification.rabbitmq")
public record NotificationRabbitProperties(
        boolean enabled,
        String exchange,
        String queue,
        String routingKey) {
}
