package com.example.banking.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotNull(message = "Account type is required")
        String accountType,

        BigDecimal balance
) {
}
