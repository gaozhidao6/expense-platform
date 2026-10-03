package com.shixi.expense.notification.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.common.jobs.DurableJobStore;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationMessagePublisherTests {
    @Test
    void persistsSerializableDeliveryIntentWithoutCallingRabbit() throws Exception {
        DurableJobStore jobs = mock(DurableJobStore.class);
        ObjectMapper json = new ObjectMapper();
        var command = new NotificationCreateCommand(10L, NotificationType.APPROVAL_TODO,
                "待办", "处理报销单", "EXPENSE_REPORT", 100L, "event:100");
        new NotificationMessagePublisher(jobs, json).publish(command);
        verify(jobs).enqueueNotification("event:100", 10L, json.writeValueAsString(command));
    }

    @Test
    void propagatesStorageFailureSoBusinessCannotCommitWithoutIntent() {
        DurableJobStore jobs = mock(DurableJobStore.class);
        doThrow(new IllegalStateException("database unavailable")).when(jobs)
                .enqueueNotification(anyString(), anyLong(), anyString());
        var command = new NotificationCreateCommand(10L, NotificationType.APPROVAL_TODO,
                "待办", "处理报销单", "EXPENSE_REPORT", 100L, "event:100");
        assertThrows(IllegalStateException.class,
                () -> new NotificationMessagePublisher(jobs, new ObjectMapper()).publish(command));
    }
}
