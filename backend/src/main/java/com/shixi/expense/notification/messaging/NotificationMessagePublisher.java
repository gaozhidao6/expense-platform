package com.shixi.expense.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.common.jobs.DurableJobStore;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import org.springframework.stereotype.Component;

/** Stores a delivery intent in the same transaction as the business state change. */
@Component
public class NotificationMessagePublisher {
    private final DurableJobStore jobs;
    private final ObjectMapper json;

    public NotificationMessagePublisher(DurableJobStore jobs, ObjectMapper json) {
        this.jobs = jobs;
        this.json = json;
    }

    public void publish(NotificationCreateCommand command) {
        try {
            jobs.enqueueNotification(command.messageKey(), command.recipientId(), json.writeValueAsString(command));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化通知命令", exception);
        }
    }
}
