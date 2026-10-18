package com.example.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDto(
        Long id,
        String accountNumber,
        String accountType,
        String status,
        BigDecimal balance,
        Long userId,
        String username,
        LocalDateTime createdAt
) {
}
