package com.shixi.expense.report.controller;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.shixi.expense.report.dto.ExpenseItemDTO;
import com.shixi.expense.report.dto.ExpenseReportCreateDTO;
import com.shixi.expense.report.dto.ExpenseReportUpdateDTO;
import com.shixi.expense.report.dto.ExpenseReportPageQueryDTO;
import com.shixi.expense.report.service.ExpenseReportService;
import com.shixi.expense.report.vo.ExpenseReportVO;
import com.shixi.expense.report.vo.ExpenseReportListVO;
import com.shixi.expense.report.vo.ExpenseReportStatisticsVO;
import com.shixi.expense.report.dto.ExpenseReportStatisticsQueryDTO;
import com.shixi.expense.report.dto.ExpenseReportExportQueryDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;


/**
 * 报销单接口。
 *
 * <p>Controller 只负责参数绑定、参数校验和调用 Service，不编写业务规则。</p>
 */
@RestController
@RequestMapping("/api/reports")
public class ExpenseReportController {

    private final ExpenseReportService expenseReportService;

    public ExpenseReportController(ExpenseReportService expenseReportService) {
        this.expenseReportService = expenseReportService;
    }

    @PostMapping
    public ExpenseReportVO createDraft(
            @Valid @RequestBody ExpenseReportCreateDTO request) {
        return expenseReportService.createReport(request);
    }

    @GetMapping("/{id}")
    public ExpenseReportVO getDetail(@PathVariable Long id) {
        return expenseReportService.getDetail(id);
    }

    /** 分页查询当前用户的报销单摘要。 */
    @GetMapping("/page")
    public IPage<ExpenseReportListVO> queryPage(
            @Valid ExpenseReportPageQueryDTO query) {
        return expenseReportService.queryPage(query);
    }

    /** 报销单统计接口框架；统计业务由 Service 负责。 */
    @GetMapping("/statistics")
    public ExpenseReportStatisticsVO statistics(
            @Valid ExpenseReportStatisticsQueryDTO query) {
        return expenseReportService.getStatistics(query);
    }

    /** 将权限范围内的报销单导出为 CSV。 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Valid ExpenseReportExportQueryDTO query) {
        byte[] content = expenseReportService.exportReports(query);

        // 设置下载响应头，让浏览器按照 CSV 文件处理响应内容。
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=expense-reports.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    /** 修改本人草稿的标题和说明。 */
    @PutMapping("/{reportId}")
    public ExpenseReportVO updateReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ExpenseReportUpdateDTO request) {
        return expenseReportService.updateReport(reportId, request);
    }

    /** 提交本人报销单，状态由后端从 DRAFT/REJECTED 转为 APPROVING。 */
    @PostMapping("/{reportId}/submit")
    public ExpenseReportVO submit(
            @PathVariable Long reportId,
            @RequestHeader(value = "Idempotency-Key", required = true)
            String idempotencyKey) {
        // 幂等 key 由客户端放在请求头中，不能放在请求体中伪造用户身份。
        // Service 会结合当前登录用户 ID 和 reportId 构造完整的幂等请求。
        // 请求头为必填；缺少 Idempotency-Key 时由 Spring 直接拒绝请求。
        return expenseReportService.submit(reportId,idempotencyKey);
    }
    @PostMapping("/{reportId}/withdraw")
    public ExpenseReportVO withdraw(@PathVariable Long reportId) {
        return expenseReportService.withdraw(reportId);
    }

    @PostMapping("/{reportId}/items")
    public ExpenseReportVO addItem(
            @PathVariable Long reportId,
            @Valid @RequestBody ExpenseItemDTO request) {

        return expenseReportService.addItem(reportId, request);
    }

    @PutMapping("/{reportId}/items/{itemId}")
    public ExpenseReportVO updateItem(
            @PathVariable Long reportId,
            @PathVariable Long itemId,
            @Valid @RequestBody ExpenseItemDTO request) {

        return expenseReportService.updateItem(reportId, itemId, request);
    }

    @DeleteMapping("/{reportId}/items/{itemId}")
    public ExpenseReportVO deleteItem(
            @PathVariable Long reportId,
            @PathVariable Long itemId) {

        return expenseReportService.deleteItem(reportId, itemId);
    }
}
