package com.example.banking.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateAccountRequest(
        @Size(max = 30, message = "Account type must be at most 30 characters")
        String accountType,
        @Size(max = 20, message = "Account status must be at most 20 characters")
        String status,
        BigDecimal balance
) {
}
