package com.example.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "Account type is required")
        String accountType,

        @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
        @Digits(integer = 17, fraction = 2, message = "Initial balance must have up to 17 integer and 2 decimal digits")
        BigDecimal balance
) {
}
