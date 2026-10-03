package com.shixi.expense.reliability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.attachment.mapper.AttachmentMapper;
import com.shixi.expense.attachment.service.AttachmentCleanupWorker;
import com.shixi.expense.attachment.service.AttachmentService;
import com.shixi.expense.attachment.storage.AttachmentStorage;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.idempotency.*;
import com.shixi.expense.common.jobs.DurableJobStore;
import com.shixi.expense.common.jobs.DurableJobStore.Queue;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import com.shixi.expense.notification.messaging.*;
import com.shixi.expense.report.dto.ExpenseItemDTO;
import com.shixi.expense.report.service.ExpenseReportService;
import com.shixi.expense.report.vo.ExpenseReportVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL commits, rollbacks and races; Redis outages and object storage failures are injected. */
@SpringBootTest
class ReliabilityIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ExpenseReportService reports;
    @Autowired IdempotencyService idempotency;
    @Autowired IdempotentOperationExecutor executor;
    @Autowired DurableJobStore jobs;
    @Autowired NotificationMessagePublisher publisher;
    @Autowired ObjectMapper json;
    @Autowired AttachmentService attachments;
    @Autowired AttachmentMapper attachmentMapper;
    @Autowired PlatformTransactionManager manager;
    @MockBean StringRedisTemplate redis;
    @MockBean AttachmentStorage storage;
    private long reportId;
    private long employeeId;
    private final String prefix = "TEST-HARDEN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    private TransactionTemplate transaction;

    @BeforeEach void setUp() throws Exception {
        transaction = new TransactionTemplate(manager);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("employee", null, List.of()));
        employeeId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='employee'", Long.class);
        long department = jdbc.queryForObject("SELECT department_id FROM sys_user WHERE id=?", Long.class, employeeId);
        jdbc.update("INSERT INTO expense_report (report_no, applicant_id, department_id, title, status) VALUES (?, ?, ?, ?, 'DRAFT')",
                prefix, employeeId, department, "可靠性回归测试");
        reportId = jdbc.queryForObject("SELECT id FROM expense_report WHERE report_no=?", Long.class, prefix);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("simulated offline"));
        when(storage.save(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @AfterEach void cleanUp() {
        jdbc.update("DELETE FROM attachment_cleanup_job WHERE storage_path LIKE ?", "reports/" + reportId + "/%");
        jdbc.update("DELETE FROM notification_outbox WHERE message_key LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM notification_outbox WHERE message_key LIKE ?", "SUBMIT_REPORT:" + reportId + ":%");
        jdbc.update("DELETE FROM notification WHERE business_id=? AND business_type='EXPENSE_REPORT'", reportId);
        jdbc.update("DELETE FROM operation_log WHERE business_id=? AND business_type='EXPENSE_REPORT'", reportId);
        jdbc.update("DELETE FROM idempotency_record WHERE business_id=? AND business_type='SUBMIT_REPORT'", reportId);
        jdbc.update("DELETE FROM approval_record WHERE report_id=?", reportId);
        jdbc.update("DELETE FROM approval_task WHERE report_id=?", reportId);
        jdbc.update("DELETE FROM attachment WHERE report_id=?", reportId);
        jdbc.update("DELETE FROM expense_item WHERE report_id=?", reportId);
        jdbc.update("DELETE FROM expense_report WHERE id=?", reportId);
        SecurityContextHolder.clearContext();
    }

    private IdempotencyRequest request(String suffix) {
        return new IdempotencyRequest(prefix + suffix, employeeId, "SUBMIT_REPORT", reportId);
    }
    private String state(String suffix) {
        return jdbc.queryForObject("SELECT status FROM idempotency_record WHERE user_id=? AND idempotency_key=?",
                String.class, employeeId, prefix + suffix);
    }
    private NotificationCreateCommand notification() {
        return new NotificationCreateCommand(employeeId, NotificationType.APPROVAL_TODO, "测试待办", "测试通知",
                "EXPENSE_REPORT", reportId, prefix + ":event");
    }
    private MockMultipartFile file() {
        return new MockMultipartFile("file", "invoice.pdf", "application/pdf", "%PDF-1.7 sample".getBytes());
    }

    @Test void failedBusinessRollsBackButFailureStateSurvivesAndCanRetry() {
        var request = request(":rollback");
        assertThrows(IllegalStateException.class, () -> executor.execute(request, () -> {
            jdbc.update("UPDATE expense_report SET status='APPROVING' WHERE id=?", reportId);
            throw new IllegalStateException("injected late failure");
        }, String.class));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM expense_report WHERE id=?", String.class, reportId));
        assertEquals("FAILED", state(":rollback"));
        assertEquals("done", executor.execute(request, () -> "done", String.class));
        assertEquals("SUCCEEDED", state(":rollback"));
    }

    @Test void redisOfflineSubmissionReplaysWithoutDuplicatingTasks() {
        ExpenseItemDTO item = new ExpenseItemDTO();
        item.setExpenseDate(LocalDate.now()); item.setExpenseType("OTHER");
        item.setDescription("可靠性测试明细"); item.setAmount(new BigDecimal("12.00"));
        item.setTaxAmount(BigDecimal.ZERO); item.setSortOrder(0);
        reports.addItem(reportId, item);
        ExpenseReportVO first = reports.submit(reportId, prefix + ":submit");
        ExpenseReportVO replay = reports.submit(reportId, prefix + ":submit");
        assertEquals(first.getId(), replay.getId());
        assertEquals("APPROVING", replay.getStatus());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM approval_task WHERE report_id=?", Long.class, reportId));
    }

    @Test void twentyConcurrentRequestsOnlyAcquireOneExpiredLease() throws Exception {
        var request = request(":race");
        var stale = idempotency.acquire(request);
        jdbc.update("UPDATE idempotency_record SET expires_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE idempotency_key=?",
                request.key());
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotencyAcquireResult>> futures = new ArrayList<>();
        try {
            for (int i=0;i<20;i++) futures.add(pool.submit(() -> { start.await(); return idempotency.acquire(request); }));
            start.countDown();
            List<IdempotencyAcquireResult> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(r -> r.decision()==IdempotencyDecision.ACQUIRED).count());
            String winner = results.stream().filter(r -> r.decision()==IdempotencyDecision.ACQUIRED).findFirst().orElseThrow().ownerToken();
            idempotency.markFailed(request, stale.ownerToken(), "stale worker must not overwrite winner");
            assertEquals("PROCESSING", state(":race"));
            assertEquals(winner, jdbc.queryForObject("SELECT owner_token FROM idempotency_record WHERE idempotency_key=?", String.class, request.key()));
            assertThrows(BusinessException.class, () -> transaction.execute(status -> {
                idempotency.lockOwnership(request, stale.ownerToken()); return null;
            }));
            assertThrows(BusinessException.class, () -> idempotency.markSucceeded(request, stale.ownerToken(), "{}"));
        } finally { pool.shutdownNow(); }
    }

    @Test void sameKeyCannotReplayAnotherReport() {
        var request = request(":binding");
        executor.execute(request, () -> "first", String.class);
        assertThrows(BusinessException.class, () -> idempotency.acquire(
                new IdempotencyRequest(request.key(), employeeId, "SUBMIT_REPORT", reportId + 1)));
    }

    @Test void outboxRollsBackWithBusinessAndDeduplicatesCommittedIntent() {
        var command = notification();
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            publisher.publish(command); throw new IllegalStateException("rollback");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE message_key=?", Long.class, command.messageKey()));
        transaction.executeWithoutResult(status -> { publisher.publish(command); publisher.publish(command); });
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE message_key=?", Long.class, command.messageKey()));
    }

    @Test void failedNotificationDeliveryRemainsDurableAndThenRetries() throws Exception {
        publisher.publish(notification());
        RabbitNotificationSender sender = mock(RabbitNotificationSender.class);
        doThrow(new IOException("offline")).when(sender).send(any());
        var dispatcher = new NotificationOutboxDispatcher(jobs, json, sender);
        dispatcher.dispatch();
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM notification_outbox WHERE message_key=?", String.class, notification().messageKey()));
        jdbc.update("UPDATE notification_outbox SET available_at=NOW(3) WHERE message_key=?", notification().messageKey());
        doNothing().when(sender).send(any());
        dispatcher.dispatch();
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM notification_outbox WHERE message_key=?", String.class, notification().messageKey()));
        verify(sender, times(2)).send(any());
    }

    @Test void recoveredJobRejectsStaleCompletion() {
        publisher.publish(notification());
        var old = jobs.claim(Queue.NOTIFICATION, 1).get(0);
        jdbc.update("UPDATE notification_outbox SET lease_until=DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE id=?", old.id());
        var current = jobs.claim(Queue.NOTIFICATION, 1).get(0);
        jobs.complete(Queue.NOTIFICATION, old);
        assertEquals("PROCESSING", jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?", String.class, old.id()));
        jobs.complete(Queue.NOTIFICATION, current);
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id=?", String.class, old.id()));
    }

    @Test void lateRollbackRemovesUploadedFileAndMetadata() throws Exception {
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            attachments.upload(reportId, file()); throw new IllegalStateException("late transaction failure");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE report_id=?", Long.class, reportId));
        verify(storage).delete(anyString());
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM attachment_cleanup_job WHERE storage_path LIKE ?", String.class, "reports/"+reportId+"/%"));
    }

    @Test void failedRollbackCleanupIsRetriedFromDatabase() throws Exception {
        doThrow(new IOException("object storage offline")).when(storage).delete(anyString());
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            attachments.upload(reportId, file()); throw new IllegalStateException("rollback");
        }));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM attachment_cleanup_job WHERE storage_path LIKE ?", String.class, "reports/"+reportId+"/%"));
        jdbc.update("UPDATE attachment_cleanup_job SET available_at=NOW(3) WHERE storage_path LIKE ?", "reports/"+reportId+"/%");
        doNothing().when(storage).delete(anyString());
        new AttachmentCleanupWorker(jobs, attachmentMapper, storage).clean();
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM attachment_cleanup_job WHERE storage_path LIKE ?", String.class, "reports/"+reportId+"/%"));
    }

    @Test void committedAttachmentIsNeverRemovedByCrashRecovery() throws Exception {
        attachments.upload(reportId, file());
        jdbc.update("UPDATE attachment_cleanup_job SET status='PENDING', available_at=NOW(3) WHERE storage_path LIKE ?", "reports/"+reportId+"/%");
        new AttachmentCleanupWorker(jobs, attachmentMapper, storage).clean();
        verify(storage, never()).delete(anyString());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM attachment WHERE report_id=?", Long.class, reportId));
    }

    @Test void deletionRollbackKeepsMetadataAndDoesNotDeleteFile() throws Exception {
        var attachment = attachments.upload(reportId, file());
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            attachments.delete(reportId, attachment.getId()); throw new IllegalStateException("rollback");
        }));
        verify(storage, never()).delete(anyString());
        assertNotNull(attachmentMapper.selectById(attachment.getId()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM attachment_cleanup_job WHERE status='PENDING' AND storage_path LIKE ?", Long.class, "reports/"+reportId+"/%"));
    }

    @Test void committedDeletionRetriesFileFailureWithoutRestoringMetadata() throws Exception {
        var attachment = attachments.upload(reportId, file());
        attachments.delete(reportId, attachment.getId());
        doThrow(new IOException("offline")).when(storage).delete(anyString());
        new AttachmentCleanupWorker(jobs, attachmentMapper, storage).clean();
        assertNull(attachmentMapper.selectById(attachment.getId()));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM attachment_cleanup_job WHERE status='PENDING' AND storage_path LIKE ?", Long.class, "reports/"+reportId+"/%"));
    }
}
