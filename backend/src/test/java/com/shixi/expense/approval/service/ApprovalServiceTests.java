package com.shixi.expense.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.approval.entity.ApprovalRecord;
import com.shixi.expense.approval.entity.ApprovalTask;
import com.shixi.expense.approval.mapper.ApprovalRecordMapper;
import com.shixi.expense.approval.mapper.ApprovalTaskMapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.notification.service.NotificationService;
import com.shixi.expense.operation.service.OperationLogService;
import com.shixi.expense.operation.service.OperationLogWriteService;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTests {

    @Mock private ApprovalTaskMapper approvalTaskMapper;
    @Mock private ApprovalRecordMapper approvalRecordMapper;
    @Mock private ExpenseReportMapper expenseReportMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private OperationLogWriteService operationLogWriteService;
    @Mock private NotificationService notificationService;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(
                approvalTaskMapper, approvalRecordMapper, expenseReportMapper,
                sysUserMapper, operationLogService, operationLogWriteService,
                notificationService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingListUsesValidatedJoinQuery() {
        authenticate("manager", "MANAGER");
        when(sysUserMapper.selectOne(any())).thenReturn(user(3L, "manager"));
        when(approvalTaskMapper.selectValidPendingByApproverId(3L)).thenReturn(List.of());

        assertEquals(List.of(), service.getMyPendingTasks());

        verify(approvalTaskMapper).selectValidPendingByApproverId(3L);
        verify(approvalTaskMapper, never()).selectList(any());
    }

    @Test
    void managerCannotProcessTaskAssignedToAnotherUser() {
        authenticate("manager", "MANAGER");
        when(sysUserMapper.selectOne(any())).thenReturn(user(3L, "manager"));
        ExpenseReport report = report("APPROVING", 99L, "MANAGER_APPROVAL");
        when(expenseReportMapper.selectById(10L)).thenReturn(report);

        assertThrows(AccessDeniedException.class,
                () -> service.managerApprove(10L, null));

        verify(approvalTaskMapper, never()).selectOne(any());
        verify(operationLogWriteService).recordFailure(
                eq("MANAGER_APPROVE"), eq("EXPENSE_REPORT"), eq(10L),
                eq("权限校验失败"));
    }

    @Test
    void repeatedApprovalReturnsConflictWhenPendingTaskNoLongerExists() {
        authenticate("manager", "MANAGER");
        when(sysUserMapper.selectOne(any())).thenReturn(user(3L, "manager"));
        when(expenseReportMapper.selectById(10L))
                .thenReturn(report("APPROVING", 3L, "MANAGER_APPROVAL"));
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.managerApprove(10L, null));

        verify(expenseReportMapper, never()).updateById(any(ExpenseReport.class));
        verify(operationLogWriteService).recordFailure(
                eq("MANAGER_APPROVE"), eq("EXPENSE_REPORT"), eq(10L),
                eq("业务校验或状态冲突"));
    }

    @Test
    void optimisticLockConflictStopsManagerApproval() {
        authenticate("manager", "MANAGER");
        when(sysUserMapper.selectOne(any())).thenReturn(user(3L, "manager"));
        when(expenseReportMapper.selectById(10L))
                .thenReturn(report("APPROVING", 3L, "MANAGER_APPROVAL"));
        when(approvalTaskMapper.selectOne(any())).thenReturn(task());
        when(approvalTaskMapper.updateById(any(ApprovalTask.class))).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.managerApprove(10L, null));

        verify(expenseReportMapper, never()).updateById(any(ExpenseReport.class));
    }

    @Test
    void paymentClearsCurrentHandlerAndNode() {
        authenticate("finance", "FINANCE");
        when(sysUserMapper.selectOne(any())).thenReturn(user(4L, "finance"));
        ExpenseReport report = report("PENDING_PAYMENT", 4L, "PAYMENT");
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseReportMapper.updateById(report)).thenReturn(1);
        when(approvalRecordMapper.insert(any(ApprovalRecord.class))).thenReturn(1);

        service.pay(10L, null);

        assertEquals("COMPLETED", report.getStatus());
        assertNull(report.getCurrentApproverId());
        assertNull(report.getCurrentNodeCode());
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(1);
        return user;
    }

    private ExpenseReport report(String status, Long approverId, String nodeCode) {
        ExpenseReport report = new ExpenseReport();
        report.setId(10L);
        report.setApplicantId(2L);
        report.setApprovalRound(1);
        report.setStatus(status);
        report.setCurrentApproverId(approverId);
        report.setCurrentNodeCode(nodeCode);
        return report;
    }

    private ApprovalTask task() {
        ApprovalTask task = new ApprovalTask();
        task.setId(20L);
        task.setReportId(10L);
        task.setApprovalRound(1);
        task.setApproverId(3L);
        task.setNodeCode("MANAGER_APPROVAL");
        task.setStatus("PENDING");
        task.setVersion(0);
        return task;
    }
}
