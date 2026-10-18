package com.example.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "Source account id is required")
        Long sourceAccountId,

        @NotNull(message = "Target account id is required")
        Long targetAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be positive")
        BigDecimal amount,

        String description
) {
}
