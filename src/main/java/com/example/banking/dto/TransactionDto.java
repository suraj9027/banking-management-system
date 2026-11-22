package com.example.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDto(
        Long id,
        String type,
        String description,
        BigDecimal amount,
        String referenceNumber,
        String status,
        String sourceAccountNumber,
        String targetAccountNumber,
        LocalDateTime createdAt,
        String direction
) {
}
