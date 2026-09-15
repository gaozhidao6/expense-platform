package com.shixi.expense.notification.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.service.NotificationService;

/** RabbitMQ 通知消息消费者。 */
@Component
@ConditionalOnProperty(
        prefix = "app.notification.rabbitmq",
        name = "enabled",
        havingValue = "true")
public class NotificationMessageConsumer {

    private final NotificationService notificationService;

    public NotificationMessageConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 消费消息：Spring 会把队列中的 JSON 转换成 command。 */
    @RabbitListener(queues = "${app.notification.rabbitmq.queue}")
    public void consume(NotificationCreateCommand command) {
        // 使用消费者专用入口校验并保存通知。
        notificationService.createFromMessage(command);
        // 不调用 create(command)，避免形成“消费后又生产”的消息循环。
    }
}
