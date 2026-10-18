package com.example.banking.dto;

import java.math.BigDecimal;

public record DashboardSummaryDto(
        BigDecimal totalBalance,
        long activeAccounts,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        long recentTransactionCount,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpenses
) {
}
