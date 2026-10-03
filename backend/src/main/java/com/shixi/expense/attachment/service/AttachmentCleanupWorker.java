package com.shixi.expense.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.attachment.entity.Attachment;
import com.shixi.expense.attachment.mapper.AttachmentMapper;
import com.shixi.expense.attachment.storage.AttachmentStorage;
import com.shixi.expense.common.jobs.DurableJobStore;
import com.shixi.expense.common.jobs.DurableJobStore.Queue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AttachmentCleanupWorker {
    private final DurableJobStore jobs;
    private final AttachmentMapper attachments;
    private final AttachmentStorage storage;

    public AttachmentCleanupWorker(DurableJobStore jobs, AttachmentMapper attachments, AttachmentStorage storage) {
        this.jobs = jobs;
        this.attachments = attachments;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${app.jobs.poll-interval-ms:3000}")
    public void clean() {
        for (var job : jobs.claim(Queue.CLEANUP, 1)) {
            try {
                // Recovery after a crash must never delete an object referenced by committed metadata.
                long references = attachments.selectCount(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getStoragePath, job.payload()));
                if (references == 0) storage.delete(job.payload());
                jobs.complete(Queue.CLEANUP, job);
            } catch (Exception exception) {
                jobs.retry(Queue.CLEANUP, job, "Attachment cleanup failed; awaiting retry");
                log.warn("Attachment cleanup retry scheduled, jobId={}", job.id());
            }
        }
    }
}
