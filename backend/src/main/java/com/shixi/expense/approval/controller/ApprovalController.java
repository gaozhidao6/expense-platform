package com.shixi.expense.approval.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shixi.expense.approval.dto.ApprovalActionDTO;
import com.shixi.expense.approval.service.ApprovalService;
import com.shixi.expense.approval.vo.ApprovalTaskVO;
import com.shixi.expense.approval.vo.ApprovalRecordVO;

import jakarta.validation.Valid;

/** 审批接口控制器：只负责参数绑定并调用 Service。 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** 查询当前登录用户的待审批任务。 */
    @GetMapping("/my-pending")
    public List<ApprovalTaskVO> getMyPendingTasks() {
        return approvalService.getMyPendingTasks();
    }

    /** 查询指定报销单的审批历史；具体权限和查询逻辑由 Service 负责。 */
    @GetMapping("/{reportId}/records")
    public List<ApprovalRecordVO> getApprovalRecords(
            @PathVariable Long reportId) {
        return approvalService.getApprovalRecords(reportId);
    }

    /** 经理审批通过：APPROVING -> FINANCE_REVIEW。 */
    @PostMapping("/{reportId}/manager/approve")
    public void managerApprove(@PathVariable Long reportId,
            @Valid @RequestBody(required = false) ApprovalActionDTO request) {
        approvalService.managerApprove(reportId, request);
    }

    /** 经理驳回：APPROVING -> REJECTED。 */
    @PostMapping("/{reportId}/manager/reject")
    public void managerReject(@PathVariable Long reportId,
            @Valid @RequestBody ApprovalActionDTO request) {
        approvalService.managerReject(reportId, request);
    }

    /** 财务审核通过：FINANCE_REVIEW -> PENDING_PAYMENT。 */
    @PostMapping("/{reportId}/finance/approve")
    public void financeApprove(@PathVariable Long reportId,
            @Valid @RequestBody(required = false) ApprovalActionDTO request) {
        approvalService.financeApprove(reportId, request);
    }

    /** 财务审核驳回：FINANCE_REVIEW -> REJECTED。 */
    @PostMapping("/{reportId}/finance/reject")
    public void financeReject(@PathVariable Long reportId,
            @Valid @RequestBody ApprovalActionDTO request) {
        approvalService.financeReject(reportId, request);
    }

    /** 财务付款：PENDING_PAYMENT -> COMPLETED。 */
    @PostMapping("/{reportId}/pay")
    public void pay(@PathVariable Long reportId,
            @Valid @RequestBody(required = false) ApprovalActionDTO request) {
        approvalService.pay(reportId, request);
    }
}
