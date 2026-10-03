package com.shixi.expense.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 基础设施配置，仅在 enabled=true 时生效。 */
@Configuration
@EnableConfigurationProperties(NotificationRabbitProperties.class)
@ConditionalOnProperty(
        prefix = "app.notification.rabbitmq",
        name = "enabled",
        havingValue = "true")
public class NotificationRabbitConfig {

    /** 创建交换机：生产者先把消息发到这里。 */
    @Bean
    public DirectExchange notificationExchange(NotificationRabbitProperties properties) {
        // 使用配置名称创建持久化、非自动删除的直连交换机。
        return new DirectExchange(properties.exchange(), true, false);
    }

    /** 创建队列：消费者从这里获取消息。 */
    @Bean
    public Queue notificationQueue(NotificationRabbitProperties properties) {
        // 持久化队列在 RabbitMQ 重启后仍然存在。
        return QueueBuilder.durable(properties.queue())
                .deadLetterExchange(properties.exchange() + ".dead")
                .deadLetterRoutingKey(properties.routingKey() + ".dead").build();
    }

    @Bean
    public DirectExchange notificationDeadExchange(NotificationRabbitProperties properties) {
        return new DirectExchange(properties.exchange() + ".dead", true, false);
    }

    @Bean
    public Queue notificationDeadQueue(NotificationRabbitProperties properties) {
        return QueueBuilder.durable(properties.queue() + ".dead").build();
    }

    @Bean
    public Binding notificationDeadBinding(NotificationRabbitProperties properties) {
        return BindingBuilder.bind(notificationDeadQueue(properties))
                .to(notificationDeadExchange(properties)).with(properties.routingKey() + ".dead");
    }

    /** 绑定交换机和队列：routing key 匹配时，消息才会进入该队列。 */
    @Bean
    public Binding notificationBinding(
            Queue notificationQueue,
            DirectExchange notificationExchange,
            NotificationRabbitProperties properties) {
        // routing key 完全匹配时，交换机才把消息路由到通知队列。
        return BindingBuilder.bind(notificationQueue)
                .to(notificationExchange)
                .with(properties.routingKey());
    }

    /** 配置消息转换器：发送时转成 JSON，接收时再转回 Java 对象。 */
    @Bean
    public MessageConverter notificationMessageConverter() {
        // Java 通知命令以 JSON 格式发送和接收。
        return new Jackson2JsonMessageConverter();
    }
}
