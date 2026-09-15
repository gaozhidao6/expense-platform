package com.shixi.expense.notification.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shixi.expense.notification.config.NotificationRabbitProperties;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationMessagePublisherTests {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private NotificationMessagePublisher publisher;
    private NotificationCreateCommand command;

    @BeforeEach
    void setUp() {
        NotificationRabbitProperties properties = new NotificationRabbitProperties(
                true,
                "test.exchange",
                "test.queue",
                "test.create");
        publisher = new NotificationMessagePublisher(rabbitTemplate, properties);
        command = new NotificationCreateCommand(
                10L,
                NotificationType.APPROVAL_TODO,
                "待审核报销单",
                "请处理一张报销单",
                "EXPENSE_REPORT",
                100L,
                "APPROVAL_TODO:100:1");
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void sendsImmediatelyWhenThereIsNoTransaction() {
        publisher.publish(command);

        verify(rabbitTemplate).convertAndSend(
                "test.exchange",
                "test.create",
                command);
    }

    @Test
    void sendsOnlyAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publish(command);

        verifyNoInteractions(rabbitTemplate);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        verify(rabbitTemplate).convertAndSend(
                "test.exchange",
                "test.create",
                command);
    }

    @Test
    void doesNotSendWhenTransactionDoesNotCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publish(command);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(rabbitTemplate);
    }
}
