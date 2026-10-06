package com.shixi.expense.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shixi.expense.common.exception.BusinessException;
import com.shixi.expense.common.exception.ErrorCode;
import com.shixi.expense.common.security.CurrentUserService;
import com.shixi.expense.report.entity.ExpenseItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpensePolicyService {
    private final ExpensePolicyMapper mapper;
    private final CurrentUserService currentUser;

    public ExpensePolicyService(ExpensePolicyMapper mapper, CurrentUserService currentUser) {
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    public List<ExpensePolicy> list() {
        return mapper.selectList(new LambdaQueryWrapper<ExpensePolicy>().orderByAsc(ExpensePolicy::getExpenseType));
    }

    public Map<String, BigDecimal> activeLimits() {
        Map<String, BigDecimal> limits = new LinkedHashMap<>();
        for (ExpensePolicy policy : list()) {
            if (Boolean.TRUE.equals(policy.getEnabled())) limits.put(policy.getExpenseType(), policy.getLimitAmount());
        }
        return limits;
    }

    @Transactional
    public ExpensePolicy update(String expenseType, ExpensePolicyUpdateDTO request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("只有管理员可以修改费用限额");
        }
        ExpenseType.require(expenseType);
        ExpensePolicy policy = mapper.selectById(expenseType);
        if (policy == null) throw new BusinessException(ErrorCode.NOT_FOUND, "费用规则不存在");
        policy.setLimitAmount(request.limitAmount());
        policy.setEnabled(request.enabled());
        policy.setUpdatedBy(currentUser.requireCurrentUserId());
        policy.setUpdatedAt(LocalDateTime.now());
        if (mapper.updateById(policy) != 1) throw new BusinessException(ErrorCode.CONFLICT, "规则修改失败，请刷新后重试");
        return policy;
    }

    /** The backend checks persisted values; a browser warning cannot replace this guard. */
    public void validateSubmission(List<ExpenseItem> items, String currency, Map<String, BigDecimal> limits) {
        if (!"CNY".equals(currency)) throw new BusinessException(ErrorCode.BAD_REQUEST, "当前费用限额仅支持人民币 CNY");
        for (int index = 0; index < items.size(); index++) {
            ExpenseItem item = items.get(index);
            ExpenseType.require(item.getExpenseType());
            BigDecimal limit = limits.get(item.getExpenseType());
            if (isOverLimit(item.getAmount(), limit) && (item.getOverLimitReason() == null || item.getOverLimitReason().isBlank())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第 " + (index + 1) + " 条费用明细超过单笔限额 "
                        + limit.toPlainString() + " 元，请填写超限原因后再提交");
            }
        }
    }

    public static boolean isOverLimit(BigDecimal amount, BigDecimal limit) {
        return amount != null && limit != null && amount.compareTo(limit) > 0;
    }
}
