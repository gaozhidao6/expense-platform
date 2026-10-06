package com.shixi.expense.policy;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expense-policies")
public class ExpensePolicyController {
    private final ExpensePolicyService service;
    public ExpensePolicyController(ExpensePolicyService service) { this.service = service; }

    @GetMapping
    public List<ExpensePolicy> list() { return service.list(); }

    @PutMapping("/{expenseType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExpensePolicy update(@PathVariable String expenseType, @Valid @RequestBody ExpensePolicyUpdateDTO request) {
        return service.update(expenseType, request);
    }
}
