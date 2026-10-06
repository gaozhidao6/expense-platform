package com.shixi.expense.policy;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ExpensePolicyUpdateDTO(
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal limitAmount,
        @NotNull Boolean enabled) { }
