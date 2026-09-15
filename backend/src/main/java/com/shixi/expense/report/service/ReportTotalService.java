package com.shixi.expense.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.report.entity.ExpenseItem;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.mapper.ExpenseItemMapper;
import com.shixi.expense.report.mapper.ExpenseReportMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** 统一重新计算并保存报销单总金额。 */
@Service
public class ReportTotalService {

    private final ExpenseItemMapper itemMapper;
    private final ExpenseReportMapper reportMapper;

    public ReportTotalService(
            ExpenseItemMapper itemMapper,
            ExpenseReportMapper reportMapper) {
        this.itemMapper = itemMapper;
        this.reportMapper = reportMapper;
    }

    /** 总金额等于当前报销单所有未逻辑删除明细的 amount 之和。 */
    public void recalculateAndSave(ExpenseReport report) {
        List<ExpenseItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ExpenseItem>()
                        .eq(ExpenseItem::getReportId, report.getId()));

        BigDecimal totalAmount = items.stream()
                .map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalAmount(totalAmount);
        if (reportMapper.updateById(report) != 1) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "更新报销单总金额失败");
        }
    }
}
