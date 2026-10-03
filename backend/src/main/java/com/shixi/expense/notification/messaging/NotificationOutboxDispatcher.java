package com.shixi.expense.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.common.jobs.DurableJobStore;
import com.shixi.expense.common.jobs.DurableJobStore.Queue;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.notification.rabbitmq", name = "enabled", havingValue = "true")
public class NotificationOutboxDispatcher {
    private final DurableJobStore jobs;
    private final ObjectMapper json;
    private final RabbitNotificationSender sender;

    public NotificationOutboxDispatcher(DurableJobStore jobs, ObjectMapper json, RabbitNotificationSender sender) {
        this.jobs = jobs;
        this.json = json;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-interval-ms:3000}")
    public void dispatch() {
        for (var job : jobs.claim(Queue.NOTIFICATION, 1)) {
            try {
                sender.send(json.readValue(job.payload(), NotificationCreateCommand.class));
                jobs.complete(Queue.NOTIFICATION, job);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                jobs.retry(Queue.NOTIFICATION, job, "Notification delivery failed; awaiting retry");
                log.warn("Notification outbox retry scheduled, jobId={}", job.id());
            }
        }
    }
}
