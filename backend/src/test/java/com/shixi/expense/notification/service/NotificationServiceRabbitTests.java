package com.shixi.expense.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shixi.expense.notification.config.NotificationRabbitProperties;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.entity.Notification;
import com.shixi.expense.notification.enums.NotificationType;
import com.shixi.expense.notification.mapper.NotificationMapper;
import com.shixi.expense.notification.messaging.NotificationMessagePublisher;
import com.shixi.expense.common.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceRabbitTests {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private NotificationMessagePublisher messagePublisher;

    @Test
    void publishesInsteadOfPersistingWhenRabbitIsEnabled() {
        NotificationService service = serviceWithRabbit(true);
        NotificationCreateCommand command = command();

        service.create(command);

        verify(messagePublisher).publish(command);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void persistsDirectlyWhenRabbitIsDisabled() {
        NotificationService service = serviceWithRabbit(false);
        NotificationCreateCommand command = command();
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        service.create(command);

        verify(notificationMapper).insert(any(Notification.class));
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void consumerEntryPointPersistsWithoutPublishingAgain() {
        NotificationService service = serviceWithRabbit(true);
        NotificationCreateCommand command = command();
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        service.createFromMessage(command);

        verify(notificationMapper).insert(any(Notification.class));
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void treatsDuplicateNotificationAsIdempotentSuccess() {
        NotificationService service = serviceWithRabbit(false);
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("duplicate message key"));
        when(notificationMapper.selectCount(any())).thenReturn(1L);

        assertDoesNotThrow(() -> service.create(command()));

        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void unrelatedUniqueCollisionMustNotBeAcknowledgedAsDelivered() {
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("unrelated collision"));
        when(notificationMapper.selectCount(any())).thenReturn(0L);
        assertThrows(DuplicateKeyException.class, () -> serviceWithRabbit(true).createFromMessage(command()));
    }

    private NotificationService serviceWithRabbit(boolean enabled) {
        return new NotificationService(
                notificationMapper,
                currentUserService,
                messagePublisher,
                new NotificationRabbitProperties(
                        enabled,
                        "test.exchange",
                        "test.queue",
                        "test.create"));
    }

    private NotificationCreateCommand command() {
        return new NotificationCreateCommand(
                10L,
                NotificationType.APPROVAL_TODO,
                "待审核报销单",
                "请处理一张报销单",
                "EXPENSE_REPORT",
                100L,
                "APPROVAL_TODO:100:1");
    }
}
