package com.example.banking.dto;

import java.math.BigDecimal;

public record UpdateAccountRequest(
        String accountType,
        String status,
        BigDecimal balance
) {
}
