package com.example.banking.service;

import com.example.banking.dto.DashboardSummaryDto;
import com.example.banking.dto.MonthlySummaryDto;
import com.example.banking.dto.TransactionDto;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionType;
import com.example.banking.entity.User;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public DashboardService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(User currentUser) {
        List<Account> accounts = accountRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeAccounts = accounts.stream()
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .count();

        List<Transaction> transactions = transactionRepository.findDashboardTransactions(currentUser.getId());
        BigDecimal totalDeposits = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.DEPOSIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWithdrawals = transactions.stream()
                .filter(tx -> tx.getType() == TransactionType.WITHDRAWAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyIncome = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.DEPOSIT)
                .filter(transaction -> transaction.getCreatedAt().isAfter(LocalDateTime.now().minusMonths(1)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpenses = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.WITHDRAWAL)
                .filter(transaction -> transaction.getCreatedAt().isAfter(LocalDateTime.now().minusMonths(1)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardSummaryDto(
                totalBalance,
                activeAccounts,
                totalDeposits,
                totalWithdrawals,
                Math.min(transactions.size(), 10L),
                monthlyIncome,
                monthlyExpenses
        );
    }

    @Transactional(readOnly = true)
    public List<MonthlySummaryDto> getMonthlySummary(User currentUser) {
        List<Transaction> transactions = transactionRepository.findDashboardTransactions(currentUser.getId());
        Map<String, BigDecimal> incomeByMonth = new java.util.HashMap<>();
        Map<String, BigDecimal> expenseByMonth = new java.util.HashMap<>();

        for (Transaction transaction : transactions) {
            String monthKey = transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            if (transaction.getType() == TransactionType.DEPOSIT) {
                incomeByMonth.merge(monthKey, transaction.getAmount(), BigDecimal::add);
            } else if (transaction.getType() == TransactionType.WITHDRAWAL) {
                expenseByMonth.merge(monthKey, transaction.getAmount(), BigDecimal::add);
            }
        }

        List<MonthlySummaryDto> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthDate = now.minusMonths(i);
            String key = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            result.add(new MonthlySummaryDto(
                    monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                    incomeByMonth.getOrDefault(key, BigDecimal.ZERO),
                    expenseByMonth.getOrDefault(key, BigDecimal.ZERO)
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getRecentTransactions(User currentUser) {
        return transactionRepository.findDashboardTransactions(currentUser.getId()).stream()
                .limit(10)
                .map(transaction -> new TransactionDto(
                        transaction.getId(),
                        transaction.getType().name(),
                        transaction.getDescription(),
                        transaction.getAmount(),
                        transaction.getReferenceNumber(),
                        transaction.getStatus().name(),
                        transaction.getSourceAccount() == null ? null : transaction.getSourceAccount().getAccountNumber(),
                        transaction.getTargetAccount() == null ? null : transaction.getTargetAccount().getAccountNumber(),
                        transaction.getCreatedAt()))
                .toList();
    }
}
