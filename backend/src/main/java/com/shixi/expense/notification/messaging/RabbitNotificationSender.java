package com.shixi.expense.notification.messaging;

import java.util.concurrent.TimeUnit;
import com.shixi.expense.notification.config.NotificationRabbitProperties;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitNotificationSender {
    private final RabbitTemplate rabbit;
    private final NotificationRabbitProperties properties;

    public RabbitNotificationSender(RabbitTemplate rabbit, NotificationRabbitProperties properties) {
        this.rabbit = rabbit;
        this.properties = properties;
    }

    public void send(NotificationCreateCommand command) throws Exception {
        CorrelationData correlation = new CorrelationData();
        rabbit.convertAndSend(properties.exchange(), properties.routingKey(), command, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        }, correlation);
        CorrelationData.Confirm confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirmation.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ did not confirm a routable message");
        }
    }
}
