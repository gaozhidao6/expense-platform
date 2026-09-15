package com.shixi.expense.notification.messaging;

import static org.mockito.Mockito.verify;

import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import com.shixi.expense.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationMessageConsumerTests {

    @Mock
    private NotificationService notificationService;

    private NotificationMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationMessageConsumer(notificationService);
    }

    @Test
    void delegatesMessageToNotificationService() {
        NotificationCreateCommand command = new NotificationCreateCommand(
                10L,
                NotificationType.APPROVAL_TODO,
                "待审核报销单",
                "请处理一张报销单",
                "EXPENSE_REPORT",
                100L,
                "APPROVAL_TODO:100:1");

        consumer.consume(command);

        verify(notificationService).createFromMessage(command);
    }
}
