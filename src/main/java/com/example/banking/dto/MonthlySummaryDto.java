package com.example.banking.dto;

import java.math.BigDecimal;

public record MonthlySummaryDto(
        String month,
        BigDecimal income,
        BigDecimal expense
) {
}
