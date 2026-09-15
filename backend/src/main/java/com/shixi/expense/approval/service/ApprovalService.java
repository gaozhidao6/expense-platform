package com.shixi.expense.approval.service;

import com.shixi.expense.operation.service.OperationLogService;
import com.shixi.expense.operation.service.OperationLogWriteService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.approval.dto.ApprovalActionDTO;
import com.shixi.expense.approval.entity.ApprovalRecord;
import com.shixi.expense.approval.entity.ApprovalTask;
import com.shixi.expense.approval.mapper.ApprovalRecordMapper;
import com.shixi.expense.approval.mapper.ApprovalTaskMapper;
import com.shixi.expense.approval.vo.ApprovalTaskVO;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.notification.dto.NotificationCreateCommand;
import com.shixi.expense.notification.enums.NotificationType;
import com.shixi.expense.notification.service.NotificationService;
import com.shixi.expense.approval.vo.ApprovalRecordVO;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.enums.ExpenseReportStatus;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;


/**
 * 审批与付款业务服务。
 */
@Service
@Slf4j
public class ApprovalService {

    private final OperationLogWriteService operationLogWriteService;
    private final OperationLogService operationLogService;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ExpenseReportMapper expenseReportMapper;
    private final SysUserMapper sysUserMapper;
    private final NotificationService notificationService;

    public ApprovalService(
            ApprovalTaskMapper approvalTaskMapper,
            ApprovalRecordMapper approvalRecordMapper,
            ExpenseReportMapper expenseReportMapper,
            SysUserMapper sysUserMapper,
            OperationLogService operationLogService,
            OperationLogWriteService operationLogWriteService,
            NotificationService notificationService) {
        this.approvalRecordMapper = approvalRecordMapper;
        this.approvalTaskMapper = approvalTaskMapper;
        this.expenseReportMapper = expenseReportMapper;
        this.sysUserMapper = sysUserMapper;
        this.operationLogService = operationLogService;
        this.operationLogWriteService = operationLogWriteService;
        this.notificationService = notificationService;
    }
    
    /**
     * 公共查询辅助方法：根据报销单、处理人和节点查询待审批任务。
     * 只查询和校验，不修改状态，也不写审批记录；具体审批方法负责后续业务。
     */
    private ApprovalTask getCurrentPendingTask(
        ExpenseReport report,
        Long approverId,
        String nodeCode) {
        if (!Objects.equals(report.getCurrentApproverId(), approverId)) {
            throw new AccessDeniedException("当前用户不是该报销单的审批人");
        }
        if (!Objects.equals(report.getCurrentNodeCode(), nodeCode)) {
            throw new BusinessException(ErrorCode.CONFLICT, "报销单当前审批节点已经变化，请刷新后重试");
        }
        ApprovalTask task = approvalTaskMapper.selectOne(
                new LambdaQueryWrapper<ApprovalTask>()
                        .eq(ApprovalTask::getReportId, report.getId())
                        .eq(ApprovalTask::getApprovalRound, report.getApprovalRound())
                        .eq(ApprovalTask::getApproverId, approverId)
                        .eq(ApprovalTask::getNodeCode, nodeCode)
                        .eq(ApprovalTask::getStatus, "PENDING")
        );
        if (task == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "审批任务已处理或已失效，请刷新后重试");
        }
        return task;    
    }

    //查询用户的公共方法
    private SysUser getCurrentUser() {
        // 从 SecurityContextHolder 获取当前登录用户名
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        // 根据用户名查询用户
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getStatus, 1)
        );

        if (user == null) {
            throw new IllegalStateException("当前登录用户不存在");
        }

        return user;
    }

    private ApprovalTaskVO convertToTaskVO(ApprovalTask task) {
        // 根据任务中的 reportId 查询报销单
        ExpenseReport report = expenseReportMapper.selectById(task.getReportId());

        // 创建返回对象
        ApprovalTaskVO vo = new ApprovalTaskVO();

        // 复制审批任务字段
        vo.setId(task.getId());
        vo.setReportId(task.getReportId());
        vo.setApprovalRound(task.getApprovalRound());
        vo.setNodeCode(task.getNodeCode());
        vo.setNodeName(task.getNodeName());
        vo.setApproverId(task.getApproverId());
        vo.setStatus(task.getStatus());
        vo.setCreatedAt(task.getCreatedAt());

        // 复制报销单摘要字段
        if (report != null) {
            vo.setReportNo(report.getReportNo());
            vo.setReportTitle(report.getTitle());
            vo.setApplicantId(report.getApplicantId());
            vo.setTotalAmount(report.getTotalAmount());
        }

        return vo;
    }
    private void checkRole(String roleCode) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        // 没有认证信息，说明用户未登录
        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("用户未登录");
        }

        // Spring Security 的角色权限格式为 ROLE_ + 角色编码
        String requiredAuthority = "ROLE_" + roleCode;

        // 判断当前用户是否拥有指定角色
        boolean hasRole = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        requiredAuthority.equals(authority.getAuthority()));

        if (!hasRole) {
            throw new AccessDeniedException("没有 " + roleCode + " 角色权限");
        }
    }

    /**
     * 根据报销单 ID 查询报销单。
     * 查询不到时立即抛出异常，避免后续代码反复判断 null。
     */
    private ExpenseReport getReportOrThrow(Long reportId) {
        ExpenseReport report = expenseReportMapper.selectById(reportId);
        if (report == null) {
            throw new IllegalStateException("报销单不存在");
        }
        return report;
    }
    /**
     * 判断当前用户是否有权查看审批历史。
     */
    private boolean canViewApprovalHistory(
            SysUser user,
            ExpenseReport report) {

        // 申请人本人可以查看自己的报销单
        if (Objects.equals(user.getId(), report.getApplicantId())) {
            return true;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        // 管理员、经理、财务可以查看审批历史
        return authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority)
                                || "ROLE_MANAGER".equals(authority)
                                || "ROLE_FINANCE".equals(authority));
    }
    /**
     * 校验报销单是否处于指定状态。
     * 状态不符合时不允许继续执行当前业务动作。
     */
    private void checkReportStatus(
            ExpenseReport report,
            ExpenseReportStatus expectedStatus) {
        if (!Objects.equals(report.getStatus(), expectedStatus.name())) {
            throw new IllegalStateException(
                    "报销单当前状态不允许执行此操作");
        }
    }

    /**
     * 检查数据库更新结果。
     * 返回 0 通常表示版本号已变化，即发生了乐观锁冲突。
     */
    private void checkUpdateResult(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "数据已被其他人修改，请刷新后重试");
        }
    }

    /**
     * 新增审批历史记录。
     * 审批记录只新增，不修改历史数据。
     */
    private void createApprovalRecord(
            ExpenseReport report,
            ApprovalTask task,
            String action,
            String fromStatus,
            String toStatus,
            String comment) {
        SysUser operator = getCurrentUser();

        ApprovalRecord record = new ApprovalRecord();
        record.setReportId(report.getId());
        record.setApprovalRound(report.getApprovalRound());
        record.setTaskId(task == null ? null : task.getId());
        record.setAction(action);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setOperatorId(operator.getId());
        record.setComment(comment);
        record.setOperatedAt(LocalDateTime.now());

        int rows = approvalRecordMapper.insert(record);
        if (rows != 1) {
            throw new IllegalStateException("保存审批记录失败");
        }
    }
    /**
     * 查询当前用户的待审批任务列表
     */
    public List<ApprovalTaskVO> getMyPendingTasks(){
        SysUser user = getCurrentUser();

        List<ApprovalTask> tasks =
                approvalTaskMapper.selectValidPendingByApproverId(user.getId());
            return tasks.stream().map(this::convertToTaskVO).toList();

        }

    /**
     * 经理审批通过
     * APPROVING -> FINANCE_REVIEW
     */
    @Transactional
    public void managerApprove(Long reportId,ApprovalActionDTO request){
        log.info("经理审批通过，reportId={}", reportId);
        try {
        //获取当前用户
        SysUser user = getCurrentUser();
        //检验角色是否为经理
        checkRole("MANAGER");
        //获取报销单
        ExpenseReport report = getReportOrThrow(reportId);
        checkReportStatus(report, ExpenseReportStatus.APPROVING);
        //查询当前经理任务
        ApprovalTask task = getCurrentPendingTask(report, user.getId(), "MANAGER_APPROVAL");
        String formStatus = report.getStatus();
        //更新经理任务
        task.setStatus("APPROVED");
        task.setComment(request == null ? null : request.getComment());
        task.setActedAt(LocalDateTime.now());
        checkUpdateResult(approvalTaskMapper.updateById(task));
        Long financeUserId = sysUserMapper.selectFinanceUserId();
        if (financeUserId == null) {
            throw new IllegalStateException("没有可用的财务审核人员");
        }
        // 更新报销单状态及当前处理节点，三者必须始终同步。
        report.setStatus(ExpenseReportStatus.FINANCE_REVIEW.name());
        report.setCurrentApproverId(financeUserId);
        report.setCurrentNodeCode("FINANCE_REVIEW");
        checkUpdateResult(expenseReportMapper.updateById(report));
        operationLogWriteService.recordSuccess(
                "MANAGER_APPROVE",
                "EXPENSE_REPORT",
                reportId
        );
        //创建财务待审核状态
        ApprovalTask financeTask = new ApprovalTask();
        financeTask.setReportId(report.getId());
        financeTask.setApprovalRound(report.getApprovalRound());
        financeTask.setNodeCode("FINANCE_REVIEW");
        financeTask.setNodeName("财务审核");
        financeTask.setSequenceNo(2);
        financeTask.setApproverId(financeUserId);
        financeTask.setStatus("PENDING");

        int financeTaskRows = approvalTaskMapper.insert(financeTask);

        if (financeTaskRows != 1) {
            throw new IllegalStateException("创建财务审核任务失败");
        }
        //写入审批记录
        ApprovalRecord record = new ApprovalRecord();
        record.setReportId(reportId);
        record.setTaskId(task.getId());
        record.setApprovalRound(report.getApprovalRound());
        record.setAction("APPROVE");
        record.setFromStatus(formStatus);
        record.setToStatus("FINANCE_REVIEW");
        record.setOperatorId(user.getId());
        record.setComment(request == null ? null : request.getComment());
        record.setOperatedAt(LocalDateTime.now());
        int recordRows = approvalRecordMapper.insert(record);
        if (recordRows != 1) {
            throw new IllegalStateException("保存审批记录失败");
        }
        //给财务的任务
        NotificationCreateCommand command = new NotificationCreateCommand(
                financeUserId,
                NotificationType.APPROVAL_TODO,
                "新的财务审核任务",
                "您有一张报销单需要进行财务审核",
                "EXPENSE_REPORT",
                report.getId(),
                "FINANCE_REVIEW:"
                        + report.getId()
                        + ":"
                        + report.getApprovalRound());

        notificationService.create(command);
        //跟申请人的通知
        NotificationCreateCommand applicantNotification =
        new NotificationCreateCommand(
                report.getApplicantId(),
                NotificationType.APPROVED,
                "报销单经理审批通过",
                "您的报销单已经通过经理审批，正在等待财务审核",
                "EXPENSE_REPORT",
                report.getId(),
                "MANAGER_APPROVED:"
                        + report.getId()
                        + ":"
                        + report.getApprovalRound());

        notificationService.create(applicantNotification);
        } catch (RuntimeException exception) {
            recordFailure("MANAGER_APPROVE", reportId, exception);
            throw exception;
        }
    }
    @Transactional 
    public void managerReject(Long reportId,ApprovalActionDTO request){
        log.info("经理驳回报销单，reportId={}", reportId);
        try {
        if (request == null
            || request.getComment() == null
            || request.getComment().trim().isEmpty()) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回意见不能为空");
        }
         //获取当前用户
        SysUser user = getCurrentUser();
        //检验角色是否为经理
        checkRole("MANAGER");
        //获取报销单
        ExpenseReport report = getReportOrThrow(reportId);
        checkReportStatus(report, ExpenseReportStatus.APPROVING);
        //查询当前经理任务
        ApprovalTask task = getCurrentPendingTask(report, user.getId(), "MANAGER_APPROVAL");
        String formStatus = report.getStatus();
        String toStatus = ExpenseReportStatus.REJECTED.name();
        //更新经理任务
        task.setStatus("REJECTED");
        task.setComment(request == null ? null : request.getComment());
        task.setActedAt(LocalDateTime.now());
        checkUpdateResult(approvalTaskMapper.updateById(task));
        //更新报销单状态
        report.setStatus(toStatus);
        report.setCurrentApproverId(null);
        report.setCurrentNodeCode(null);
        checkUpdateResult(expenseReportMapper.updateById(report));
        //保存审批历史和驳回意见
        operationLogWriteService.recordSuccess(
                "MANAGER_REJECT",
                "EXPENSE_REPORT",
                reportId
        );
        createApprovalRecord(report, task, "REJECT", formStatus, toStatus, request.getComment().trim());
        //报销申请人驳回通知
        NotificationCreateCommand rejectedNotification =
                new NotificationCreateCommand(
                        report.getApplicantId(),
                        NotificationType.REJECTED,
                        "报销单被经理驳回",
                        "您的报销单未通过经理审批，请修改后重新提交",
                        "EXPENSE_REPORT",
                        report.getId(),
                        "MANAGER_REJECTED:"
                                + report.getId()
                                + ":"
                                + report.getApprovalRound());

        notificationService.create(rejectedNotification);
        } catch (RuntimeException exception) {
            // 驳回意见属于用户输入，失败日志只保存受控摘要，绝不写入完整意见。
            recordFailure("MANAGER_REJECT", reportId, exception);
            throw exception;
        }
    }
    /**
     * 财务审核通过
     * FINANCE_REVIEW -> PENDING_PAYMENT
     */
    @Transactional
    public void financeApprove(Long reportId,ApprovalActionDTO request){
        log.info("财务审核通过，reportId={}", reportId);
        try {
         //获取当前用户
        SysUser user = getCurrentUser();
        //检验角色是否为财务
        checkRole("FINANCE");
        //获取报销单
        ExpenseReport report = getReportOrThrow(reportId);
        checkReportStatus(report, ExpenseReportStatus.FINANCE_REVIEW);
        String fromStatus = report.getStatus();
        String toStatus = ExpenseReportStatus.PENDING_PAYMENT.name();
        //查询当前财务任务
        ApprovalTask task = getCurrentPendingTask(report, user.getId(), "FINANCE_REVIEW");
        //更新财务任务
        task.setStatus("APPROVED");
        task.setComment(request == null ? null : request.getComment());
        task.setActedAt(LocalDateTime.now());
        checkUpdateResult(approvalTaskMapper.updateById(task));
        //更新报销单状态
        report.setStatus(toStatus);
        // 财务审核通过后由同一财务人员执行付款。
        report.setCurrentApproverId(user.getId());
        report.setCurrentNodeCode("PAYMENT");
        checkUpdateResult(expenseReportMapper.updateById(report));
        //保存审批历史和驳回意见
        createApprovalRecord(report, task, "APPROVE", fromStatus, toStatus,
                request == null || request.getComment() == null
                        ? null : request.getComment().trim());
        operationLogWriteService.recordSuccess(
                "FINANCE_APPROVE",
                "EXPENSE_REPORT",
                reportId
        );

        // 通知申请人：财务审核已经通过，报销单进入等待付款状态。
        NotificationCreateCommand financeApprovedNotification =
                new NotificationCreateCommand(
                        report.getApplicantId(),
                        NotificationType.APPROVED,
                        "报销单财务审核通过",
                        "您的报销单已经通过财务审核，正在等待付款",
                        "EXPENSE_REPORT",
                        report.getId(),
                        "FINANCE_APPROVED:"
                                + report.getId()
                                + ":"
                                + report.getApprovalRound());

        notificationService.create(financeApprovedNotification);
        } catch (RuntimeException exception) {
            recordFailure("FINANCE_APPROVE", reportId, exception);
            throw exception;
        }
    }
    /**
     * 财务审核驳回
     * FINANCE_REVIEW -> REJECTED
     */
    @Transactional
    public void financeReject(Long reportId,ApprovalActionDTO request){
        log.info("财务驳回报销单，reportId={}", reportId);
        try {
        if (request == null
            || request.getComment() == null
            || request.getComment().trim().isEmpty()) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回意见不能为空");
        }
        SysUser user = getCurrentUser();
        checkRole("FINANCE");
        ExpenseReport report = getReportOrThrow(reportId);
        checkReportStatus(report, ExpenseReportStatus.FINANCE_REVIEW);
        String fromStatus = report.getStatus();
        String toStatus = ExpenseReportStatus.REJECTED.name();
        ApprovalTask task = getCurrentPendingTask(report, user.getId(), "FINANCE_REVIEW");
        task.setStatus("REJECTED");
        task.setComment(request == null ? null : request.getComment());
        task.setActedAt(LocalDateTime.now());
        checkUpdateResult(approvalTaskMapper.updateById(task));
        report.setStatus(toStatus);
        report.setCurrentApproverId(null);
        report.setCurrentNodeCode(null);
        checkUpdateResult(expenseReportMapper.updateById(report));
        createApprovalRecord(report, task, "REJECT", fromStatus, toStatus, request.getComment().trim());
        operationLogWriteService.recordSuccess(
                "FINANCE_REJECT",
                "EXPENSE_REPORT",
                reportId
        );

        // 通知申请人：财务驳回后可以修改报销单并重新提交。
        NotificationCreateCommand financeRejectedNotification =
                new NotificationCreateCommand(
                        report.getApplicantId(),
                        NotificationType.REJECTED,
                        "报销单被财务驳回",
                        "您的报销单未通过财务审核，请修改后重新提交",
                        "EXPENSE_REPORT",
                        report.getId(),
                        "FINANCE_REJECTED:"
                                + report.getId()
                                + ":"
                                + report.getApprovalRound());

        notificationService.create(financeRejectedNotification);
        } catch (RuntimeException exception) {
            recordFailure("FINANCE_REJECT", reportId, exception);
            throw exception;
        }
    }
    /**
     * 财务付款
     * PENDING_PAYMENT -> COMPLETED
     */
    @Transactional
    public void pay(Long reportId,ApprovalActionDTO request){
        log.info("财务付款，reportId={}", reportId);
        try {
        SysUser user = getCurrentUser();
        checkRole("FINANCE");
        ExpenseReport report = getReportOrThrow(reportId);
        checkReportStatus(report, ExpenseReportStatus.PENDING_PAYMENT);
        if (!Objects.equals(report.getCurrentApproverId(), user.getId())) {
            throw new AccessDeniedException("当前用户不是该报销单的付款处理人");
        }
        if (!Objects.equals(report.getCurrentNodeCode(), "PAYMENT")) {
            throw new BusinessException(ErrorCode.CONFLICT, "报销单当前节点不允许付款，请刷新后重试");
        }
        String fromStatus = report.getStatus();
        String toStatus = ExpenseReportStatus.COMPLETED.name();
        report.setStatus(toStatus);
        report.setCompletedAt(LocalDateTime.now());
        report.setCurrentApproverId(null);
        report.setCurrentNodeCode(null);
        checkUpdateResult(expenseReportMapper.updateById(report));
        createApprovalRecord(report, null, "PAY", fromStatus, toStatus,
                request == null || request.getComment() == null
                        ? null : request.getComment().trim());
        operationLogWriteService.recordSuccess(
                "FINANCE_PAY", "EXPENSE_REPORT", reportId);

        // 通知申请人：报销单付款完成，本轮流程结束。
        NotificationCreateCommand paidNotification =
                new NotificationCreateCommand(
                        report.getApplicantId(),
                        NotificationType.PAID,
                        "报销单付款完成",
                        "您的报销单已经完成付款",
                        "EXPENSE_REPORT",
                        report.getId(),
                        "REPORT_PAID:"
                                + report.getId()
                                + ":"
                                + report.getApprovalRound());

        notificationService.create(paidNotification);
        } catch (RuntimeException exception) {
            recordFailure("FINANCE_PAY", reportId, exception);
            throw exception;
        }
    }

    /**
     * 将审批失败写入独立事务；审计写入失败不能覆盖原始业务异常。
     * 错误摘要按异常类别固定，避免把审批意见或付款敏感信息写入日志。
     */
    private void recordFailure(String operation, Long reportId, RuntimeException exception) {
        try {
            operationLogWriteService.recordFailure(
                    operation, "EXPENSE_REPORT", reportId, failureSummary(exception));
        } catch (RuntimeException auditException) {
            log.warn("记录审批失败审计日志失败，operation={}, reportId={}",
                    operation, reportId, auditException);
        }
    }

    private String failureSummary(RuntimeException exception) {
        if (exception instanceof AccessDeniedException) {
            return "权限校验失败";
        }
        if (exception instanceof BusinessException) {
            return "业务校验或状态冲突";
        }
        return "审批或付款业务执行失败";
    }

    /**
     * 查询指定报销单的审批历史。
     */
    public List<ApprovalRecordVO> getApprovalRecords(Long reportId) {
        SysUser user = getCurrentUser();
        ExpenseReport report = getReportOrThrow(reportId);
        if (!canViewApprovalHistory(user, report)) {
            throw new AccessDeniedException("无权查看该报销单审批历史");
        }
        List<ApprovalRecord> records = approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                                    .eq(ApprovalRecord::getReportId, reportId)
                                    .orderByDesc(ApprovalRecord::getOperatedAt));

        return records.stream()
                .map(this::convertToRecordVO)
                .toList();
    }

    /** 将审批记录实体转换为只读的审批记录 VO。 */
    private ApprovalRecordVO convertToRecordVO(ApprovalRecord record) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setId(record.getId());
        vo.setReportId(record.getReportId());
        vo.setApprovalRound(record.getApprovalRound());
        vo.setTaskId(record.getTaskId());
        vo.setAction(record.getAction());
        vo.setFromStatus(record.getFromStatus());
        vo.setToStatus(record.getToStatus());
        vo.setOperatorId(record.getOperatorId());
        vo.setComment(record.getComment());
        vo.setOperatedAt(record.getOperatedAt());
        return vo;
    }

}
