package com.example.banking.service;

import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.WithdrawalRequest;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionStatus;
import com.example.banking.entity.TransactionType;
import com.example.banking.entity.User;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.UnauthorizedAccessException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionDto deposit(Long accountId, DepositRequest request, User currentUser) {
        Account account = getAccessibleAccount(accountId, currentUser);
        validatePositiveAmount(request.amount());
        validateActiveAccount(account);

        BigDecimal updatedBalance = account.getBalance().add(request.amount()).setScale(2, RoundingMode.HALF_UP);
        account.setBalance(updatedBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description() == null || request.description().isBlank() ? "Deposit" : request.description());
        transaction.setReferenceNumber(generateReference("DEP"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setTargetAccount(account);
        return toDto(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionDto withdraw(Long accountId, WithdrawalRequest request, User currentUser) {
        Account account = getAccessibleAccount(accountId, currentUser);
        validatePositiveAmount(request.amount());
        validateActiveAccount(account);

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for withdrawal");
        }

        account.setBalance(account.getBalance().subtract(request.amount()).setScale(2, RoundingMode.HALF_UP));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setType(TransactionType.WITHDRAWAL);
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description() == null || request.description().isBlank() ? "Withdrawal" : request.description());
        transaction.setReferenceNumber(generateReference("WDR"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setSourceAccount(account);
        return toDto(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionDto transfer(TransferRequest request, User currentUser) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new IllegalArgumentException("Transfer to the same account is not allowed");
        }

        Account source = accountRepository.findByIdForUpdate(request.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        Account target = accountRepository.findByIdForUpdate(request.targetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));

        if (!currentUser.getRole().name().equals("ADMIN") && !source.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You can only transfer from your own account");
        }
        validateActiveAccount(source);
        validateActiveAccount(target);
        validatePositiveAmount(request.amount());

        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient funds for transfer");
        }

        source.setBalance(source.getBalance().subtract(request.amount()).setScale(2, RoundingMode.HALF_UP));
        target.setBalance(target.getBalance().add(request.amount()).setScale(2, RoundingMode.HALF_UP));
        accountRepository.save(source);
        accountRepository.save(target);

        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description() == null || request.description().isBlank() ? "Fund transfer" : request.description());
        transaction.setReferenceNumber(generateReference("TRF"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setSourceAccount(source);
        transaction.setTargetAccount(target);

        return toDto(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsForUser(User currentUser) {
        return transactionRepository.findDashboardTransactions(currentUser.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public TransactionDto getTransactionById(Long id, User currentUser) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (currentUser.getRole().name().equals("ADMIN")) {
            return toDto(transaction);
        }
        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You are not allowed to view this transaction");
        }
        return toDto(transaction);
    }

    private Account getAccessibleAccount(Long accountId, User currentUser) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (currentUser.getRole().name().equals("ADMIN") || account.getUser().getId().equals(currentUser.getId())) {
            return account;
        }
        throw new UnauthorizedAccessException("You are not allowed to access this account");
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }
    }

    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private TransactionDto toDto(Transaction transaction) {
        return new TransactionDto(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getReferenceNumber(),
                transaction.getStatus().name(),
                transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountNumber() : null,
                transaction.getTargetAccount() != null ? transaction.getTargetAccount().getAccountNumber() : null,
                transaction.getCreatedAt()
        );
    }
}
