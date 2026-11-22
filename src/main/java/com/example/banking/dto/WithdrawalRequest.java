package com.example.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WithdrawalRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Withdrawal amount must be positive")
        @Digits(integer = 17, fraction = 2, message = "Amount must have up to 17 integer and 2 decimal digits")
        BigDecimal amount,
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description
) {
}
