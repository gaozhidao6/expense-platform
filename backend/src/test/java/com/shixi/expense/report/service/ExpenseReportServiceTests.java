package com.shixi.expense.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;

import com.shixi.expense.report.dto.ExpenseItemDTO;
import com.shixi.expense.report.dto.ExpenseReportCreateDTO;
import com.shixi.expense.approval.entity.ApprovalRecord;
import com.shixi.expense.approval.entity.ApprovalTask;
import com.shixi.expense.approval.mapper.ApprovalRecordMapper;
import com.shixi.expense.approval.mapper.ApprovalTaskMapper;
import com.shixi.expense.report.entity.ExpenseItem;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseItemMapper;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.report.vo.ExpenseReportVO;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import com.shixi.expense.department.mapper.DepartmentMapper;
import com.shixi.expense.common.idempotency.IdempotencyAcquireResult;
import com.shixi.expense.common.idempotency.IdempotencyDecision;
import com.shixi.expense.common.idempotency.IdempotencyService;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shixi.expense.department.entity.Department;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ExpenseReportServiceTests {

    @Mock
    private ExpenseReportMapper expenseReportMapper;

    @Mock
    private ExpenseItemMapper expenseItemMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private ApprovalRecordMapper approvalRecordMapper;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private ApprovalTaskMapper approvalTaskMapper;

    @Mock
    private ExpenseReportCsvExporter csvExporter;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    private ExpenseReportService expenseReportService;

    @BeforeEach
    void setUp() throws Exception {
        expenseReportService = new ExpenseReportService(
                expenseReportMapper,
                expenseItemMapper,
                sysUserMapper,
                approvalRecordMapper,
                approvalTaskMapper,
                departmentMapper,
                csvExporter,
                idempotencyService,
                notificationService,
                objectMapper);
        lenient().when(idempotencyService.acquire(any()))
                .thenReturn(new IdempotencyAcquireResult(
                        IdempotencyDecision.ACQUIRED,
                        null,
                        "test-owner-token"));
        lenient().when(objectMapper.writeValueAsString(any(ExpenseReportVO.class)))
                .thenReturn("{}");
        Department department = new Department();
        department.setId(2L);
        department.setStatus(1);
        department.setManagerUserId(2L);
        org.mockito.Mockito.lenient().when(departmentMapper.selectById(anyLong())).thenReturn(department);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "employee", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsDraftAndCalculatesTotalAmount() {
        SysUser user = enabledUser(2L);
        List<ExpenseItem> savedItems = new ArrayList<>();
        when(sysUserMapper.selectOne(any())).thenReturn(user);
        doAnswer(invocation -> {
            ExpenseReport report = invocation.getArgument(0);
            report.setId(10L);
            return 1;
        }).when(expenseReportMapper).insert(any(ExpenseReport.class));
        doAnswer(invocation -> {
            ExpenseItem item = invocation.getArgument(0);
            item.setId((long) savedItems.size() + 1);
            savedItems.add(item);
            return 1;
        }).when(expenseItemMapper).insert(any(ExpenseItem.class));
        when(expenseItemMapper.selectList(any())).thenReturn(savedItems);

        ExpenseReportVO result = expenseReportService.createReport(createRequest());

        assertEquals(new BigDecimal("350.00"), result.getTotalAmount());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(2, result.getItems().size());
        assertNotNull(result.getReportNo());
        assertTrue(result.getReportNo().startsWith("ER"));
        verify(expenseItemMapper, org.mockito.Mockito.times(2))
                .insert(any(ExpenseItem.class));
        verify(expenseReportMapper).updateById(any(ExpenseReport.class));
    }

    @Test
    void allowsApplicantToViewOwnReport() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 2L));
        when(expenseItemMapper.selectList(any())).thenReturn(List.of());

        ExpenseReportVO result = expenseReportService.getDetail(10L);

        assertEquals(10L, result.getId());
        assertEquals(2L, result.getApplicantId());
    }

    @Test
    void rejectsOtherUserViewingReport() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 99L));

        assertThrows(
                AccessDeniedException.class,
                () -> expenseReportService.getDetail(10L));
        verify(expenseItemMapper, never()).selectList(any());
    }

    @Test
    void adminCanViewAnyReportDetail() {
        authenticate("admin", "ADMIN");
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(1L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 99L));
        when(expenseItemMapper.selectList(any())).thenReturn(List.of());

        ExpenseReportVO result = expenseReportService.getDetail(10L);

        assertEquals(10L, result.getId());
        verify(approvalTaskMapper, never()).selectCount(any());
    }

    @Test
    void managerCanViewReportAssignedToTheirApprovalTask() {
        authenticate("manager", "MANAGER");
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(3L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 2L));
        when(approvalTaskMapper.selectCount(any())).thenReturn(1L);
        when(expenseItemMapper.selectList(any())).thenReturn(List.of());

        ExpenseReportVO result = expenseReportService.getDetail(10L);

        assertEquals(10L, result.getId());
        verify(approvalTaskMapper).selectCount(any());
    }

    @Test
    void stopsCreationWhenAnItemInsertFails() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        doAnswer(invocation -> {
            ExpenseReport report = invocation.getArgument(0);
            report.setId(10L);
            return 1;
        }).when(expenseReportMapper).insert(any(ExpenseReport.class));
        doThrow(new RuntimeException("明细保存失败"))
                .when(expenseItemMapper).insert(any(ExpenseItem.class));

        assertThrows(
                RuntimeException.class,
                () -> expenseReportService.createReport(createRequest()));

        // 异常继续抛出，真实 Spring 事务代理会据此回滚主表和已保存的明细。
        verify(expenseReportMapper, never()).updateById(any(ExpenseReport.class));
    }

    @Test
    void addsItemAndRecalculatesTotalAmount() {
        ExpenseReport report = report(10L, 2L);
        List<ExpenseItem> items = new ArrayList<>();
        items.add(savedItem(1L, 10L, "原有明细", "350.00"));
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        doAnswer(invocation -> {
            ExpenseItem item = invocation.getArgument(0);
            item.setId(2L);
            items.add(item);
            return 1;
        }).when(expenseItemMapper).insert(any(ExpenseItem.class));
        when(expenseItemMapper.selectList(any())).thenReturn(items);

        ExpenseReportVO result = expenseReportService.addItem(
                10L, item("MEAL", "新增明细", "100.00", 2));

        assertEquals(new BigDecimal("450.00"), result.getTotalAmount());
        assertEquals(2, result.getItems().size());
    }

    @Test
    void updatesItemAndRecalculatesTotalAmount() {
        ExpenseReport report = report(10L, 2L);
        ExpenseItem target = savedItem(2L, 10L, "修改前", "100.00");
        List<ExpenseItem> items = new ArrayList<>(List.of(
                savedItem(1L, 10L, "原有明细", "350.00"),
                target));
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseItemMapper.selectOne(any())).thenReturn(target);
        when(expenseItemMapper.selectList(any())).thenReturn(items);

        ExpenseReportVO result = expenseReportService.updateItem(
                10L, 2L, item("MEAL", "修改后", "200.00", 2));

        assertEquals(new BigDecimal("550.00"), result.getTotalAmount());
        assertEquals("修改后", target.getDescription());
        verify(expenseItemMapper).updateById(target);
    }

    @Test
    void deletesItemLogicallyAndRecalculatesTotalAmount() {
        ExpenseReport report = report(10L, 2L);
        ExpenseItem target = savedItem(2L, 10L, "待删除明细", "200.00");
        List<ExpenseItem> remainingItems = List.of(
                savedItem(1L, 10L, "保留明细", "350.00"));
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseItemMapper.selectOne(any())).thenReturn(target);
        when(expenseItemMapper.selectList(any())).thenReturn(remainingItems);

        ExpenseReportVO result = expenseReportService.deleteItem(10L, 2L);

        assertEquals(new BigDecimal("350.00"), result.getTotalAmount());
        assertEquals(1, result.getItems().size());
        verify(expenseItemMapper).deleteById(2L);
        verify(expenseItemMapper, times(2)).selectList(any());
    }

    @Test
    void rejectsMissingItemDuringUpdateAndDelete() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 2L));
        when(expenseItemMapper.selectOne(any())).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> expenseReportService.updateItem(
                        10L, 99L, item("OTHER", "不存在", "1.00", 1)));
        assertThrows(
                IllegalStateException.class,
                () -> expenseReportService.deleteItem(10L, 99L));

        verify(expenseItemMapper, never()).updateById(any(ExpenseItem.class));
        verify(expenseItemMapper, never()).deleteById(anyLong());
    }

    @Test
    void rejectsChangingItemsOnNonDraftReport() {
        ExpenseReport report = report(10L, 2L);
        report.setStatus("APPROVING");
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);

        assertThrows(
                IllegalStateException.class,
                () -> expenseReportService.addItem(
                        10L, item("OTHER", "新增", "1.00", 1)));

        verify(expenseItemMapper, never()).insert(any(ExpenseItem.class));
    }

    @Test
    void rejectsOtherUserChangingItems() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 99L));

        assertThrows(
                AccessDeniedException.class,
                () -> expenseReportService.deleteItem(10L, 2L));

        verify(expenseItemMapper, never()).selectOne(any());
        verify(expenseItemMapper, never()).deleteById(anyLong());
    }

    @Test
    void submitsDraftAndWritesApprovalRecord() {
        ExpenseReport report = report(10L, 2L);
        report.setApprovalRound(1);
        List<ExpenseItem> items = List.of(savedItem(1L, 10L, "高铁票", "300.00"));
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseItemMapper.selectList(any())).thenReturn(items);
        when(expenseReportMapper.updateById(report)).thenReturn(1);
        when(approvalTaskMapper.insert(any(ApprovalTask.class))).thenReturn(1);
        when(approvalRecordMapper.insert(any(ApprovalRecord.class))).thenReturn(1);

        ExpenseReportVO result = expenseReportService.submit(10L, "submit-draft-key");

        assertEquals("APPROVING", result.getStatus());
        assertEquals(1, result.getApprovalRound());
        assertNotNull(report.getCurrentApproverId());
        assertEquals("MANAGER_APPROVAL", report.getCurrentNodeCode());
        ArgumentCaptor<ApprovalRecord> captor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordMapper).insert(captor.capture());
        ApprovalRecord record = captor.getValue();
        assertEquals("SUBMIT", record.getAction());
        assertEquals("DRAFT", record.getFromStatus());
        assertEquals("APPROVING", record.getToStatus());
        assertEquals(1, record.getApprovalRound());
        assertEquals(2L, record.getOperatorId());
    }

    @Test
    void resubmitsRejectedReportAndIncrementsApprovalRound() {
        ExpenseReport report = report(10L, 2L);
        report.setStatus("REJECTED");
        report.setApprovalRound(1);
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseItemMapper.selectList(any()))
                .thenReturn(List.of(savedItem(1L, 10L, "明细", "100.00")));
        when(expenseReportMapper.updateById(report)).thenReturn(1);
        when(approvalTaskMapper.insert(any(ApprovalTask.class))).thenReturn(1);

        ExpenseReportVO result = expenseReportService.submit(10L, "submit-rejected-key");

        assertEquals("APPROVING", result.getStatus());
        assertEquals(2, result.getApprovalRound());
        ArgumentCaptor<ApprovalRecord> captor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordMapper).insert(captor.capture());
        assertEquals("REJECTED", captor.getValue().getFromStatus());
        assertEquals(2, captor.getValue().getApprovalRound());
    }

    @Test
    void rejectsSubmitWithoutItems() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 2L));
        when(expenseItemMapper.selectList(any())).thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> expenseReportService.submit(10L, "submit-empty-key"));
        verify(expenseReportMapper, never()).updateById(any(ExpenseReport.class));
        verify(approvalRecordMapper, never()).insert(any(ApprovalRecord.class));
    }

    @Test
    void withdrawsApprovingReportAndWritesApprovalRecord() {
        ExpenseReport report = report(10L, 2L);
        report.setStatus("APPROVING");
        report.setApprovalRound(1);
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseReportMapper.updateById(report)).thenReturn(1);
        when(approvalTaskMapper.cancelPendingTasks(
                eq(10L), eq(1), eq("申请人撤回后取消"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(approvalRecordMapper.insert(any(ApprovalRecord.class))).thenReturn(1);

        ExpenseReportVO result = expenseReportService.withdraw(10L);

        assertEquals("WITHDRAWN", result.getStatus());
        assertEquals(null, report.getCurrentApproverId());
        assertEquals(null, report.getCurrentNodeCode());
        verify(expenseReportMapper).updateById(report);
        verify(approvalTaskMapper).cancelPendingTasks(
                eq(10L), eq(1), eq("申请人撤回后取消"), any(LocalDateTime.class));
        ArgumentCaptor<ApprovalRecord> captor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordMapper).insert(captor.capture());
        assertEquals("WITHDRAW", captor.getValue().getAction());
        assertEquals("APPROVING", captor.getValue().getFromStatus());
        assertEquals("WITHDRAWN", captor.getValue().getToStatus());
        assertEquals(2L, captor.getValue().getOperatorId());
    }

    @Test
    void rejectsWithdrawFromDraft() {
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report(10L, 2L));

        assertThrows(
                IllegalStateException.class,
                () -> expenseReportService.withdraw(10L));
        verify(expenseReportMapper, never()).updateById(any(ExpenseReport.class));
        verify(approvalRecordMapper, never()).insert(any(ApprovalRecord.class));
    }

    @Test
    void rejectsWithdrawWhenPendingTaskCannotBeCancelled() {
        ExpenseReport report = report(10L, 2L);
        report.setStatus("APPROVING");
        report.setApprovalRound(1);
        report.setCurrentApproverId(3L);
        report.setCurrentNodeCode("MANAGER_APPROVAL");
        when(sysUserMapper.selectOne(any())).thenReturn(enabledUser(2L));
        when(expenseReportMapper.selectById(10L)).thenReturn(report);
        when(expenseReportMapper.updateById(report)).thenReturn(1);
        when(approvalTaskMapper.cancelPendingTasks(
                eq(10L), eq(1), eq("申请人撤回后取消"), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThrows(BusinessException.class,
                () -> expenseReportService.withdraw(10L));

        verify(approvalRecordMapper, never()).insert(any(ApprovalRecord.class));
    }

    private ExpenseReportCreateDTO createRequest() {
        ExpenseReportCreateDTO request = new ExpenseReportCreateDTO();
        request.setTitle("8 月出差报销");
        request.setDescription("上海出差费用");
        request.setCurrency("CNY");
        request.setItems(List.of(
                item("TRANSPORT", "高铁票", "300.00", 1),
                item("MEAL", "工作餐", "50.00", 2)));
        return request;
    }

    private ExpenseItemDTO item(
            String type, String description, String amount, int sortOrder) {
        ExpenseItemDTO item = new ExpenseItemDTO();
        item.setExpenseDate(LocalDate.of(2026, 8, 28));
        item.setExpenseType(type);
        item.setDescription(description);
        item.setAmount(new BigDecimal(amount));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setSortOrder(sortOrder);
        return item;
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private SysUser enabledUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("employee");
        user.setDepartmentId(2L);
        user.setStatus(1);
        return user;
    }

    private ExpenseItem savedItem(
            Long id, Long reportId, String description, String amount) {
        ExpenseItem item = new ExpenseItem();
        item.setId(id);
        item.setReportId(reportId);
        item.setExpenseDate(LocalDate.of(2026, 8, 28));
        item.setExpenseType("OTHER");
        item.setDescription(description);
        item.setAmount(new BigDecimal(amount));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setSortOrder(id.intValue());
        return item;
    }

    private ExpenseReport report(Long id, Long applicantId) {
        ExpenseReport report = new ExpenseReport();
        report.setId(id);
        report.setApplicantId(applicantId);
        report.setDepartmentId(2L);
        report.setReportNo("ER-TEST");
        report.setTitle("测试报销单");
        report.setStatus("DRAFT");
        report.setCurrency("CNY");
        report.setTotalAmount(BigDecimal.ZERO);
        return report;
    }
}
