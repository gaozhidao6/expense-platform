package com.shixi.expense.notification.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.shixi.expense.notification.config.NotificationRabbitProperties;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitNotificationSenderTests {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final RabbitNotificationSender sender = new RabbitNotificationSender(rabbit,
            new NotificationRabbitProperties(true, "exchange", "queue", "key"));
    private final NotificationCreateCommand command = new NotificationCreateCommand(10L,
            NotificationType.APPROVAL_TODO, "待办", "处理报销单", "EXPENSE_REPORT", 100L, "event:100");

    private void acknowledge(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            MessagePostProcessor postProcessor = invocation.getArgument(3);
            Message message = postProcessor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            CorrelationData data = invocation.getArgument(4);
            if (returned) data.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", "exchange", "key"));
            data.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq("exchange"), eq("key"), eq(command),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test void acceptsConfirmedRoutablePersistentMessage() throws Exception {
        acknowledge(true, false);
        assertDoesNotThrow(() -> sender.send(command));
    }
    @Test void rejectsBrokerNack() {
        acknowledge(false, false);
        assertThrows(IllegalStateException.class, () -> sender.send(command));
    }
    @Test void rejectsUnroutableMessageEvenWhenBrokerAcknowledges() {
        acknowledge(true, true);
        assertThrows(IllegalStateException.class, () -> sender.send(command));
    }
    @Test void propagatesConnectionFailureToOutboxRetry() {
        doThrow(new IllegalStateException("offline")).when(rabbit).convertAndSend(
                eq("exchange"), eq("key"), eq(command), any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThrows(IllegalStateException.class, () -> sender.send(command));
    }
}
