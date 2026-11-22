package com.example.banking.service;

import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.WithdrawalRequest;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.Role;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Account account = getAccessibleAccountForUpdate(accountId, currentUser);
        BigDecimal amount = normalizeAmount(request.amount());
        validateActiveAccount(account);

        BigDecimal updatedBalance = normalizeBalance(account.getBalance().add(amount));
        account.setBalance(updatedBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(amount);
        transaction.setDescription(normalizeDescription(request.description(), "Deposit"));
        transaction.setReferenceNumber(generateReference("DEP"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setTargetAccount(account);
        return toDto(transactionRepository.save(transaction), currentUser);
    }

    @Transactional
    public TransactionDto withdraw(Long accountId, WithdrawalRequest request, User currentUser) {
        Account account = getAccessibleAccountForUpdate(accountId, currentUser);
        BigDecimal amount = normalizeAmount(request.amount());
        validateActiveAccount(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for withdrawal");
        }

        account.setBalance(normalizeBalance(account.getBalance().subtract(amount)));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setType(TransactionType.WITHDRAWAL);
        transaction.setAmount(amount);
        transaction.setDescription(normalizeDescription(request.description(), "Withdrawal"));
        transaction.setReferenceNumber(generateReference("WDR"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setSourceAccount(account);
        return toDto(transactionRepository.save(transaction), currentUser);
    }

    @Transactional
    public TransactionDto transfer(TransferRequest request, User currentUser) {
        if (request.sourceAccountId() == null || request.targetAccountId() == null) {
            throw new IllegalArgumentException("Source and target accounts are required");
        }
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new IllegalArgumentException("Transfer to the same account is not allowed");
        }

        List<Long> orderedIds = List.of(request.sourceAccountId(), request.targetAccountId()).stream()
                .sorted()
                .toList();
        Map<Long, Account> lockedAccounts = new LinkedHashMap<>();
        for (Long accountId : orderedIds) {
            accountRepository.findByIdForUpdate(accountId)
                    .ifPresent(account -> lockedAccounts.put(accountId, account));
        }
        Account source = lockedAccounts.get(request.sourceAccountId());
        if (source == null) {
            throw new ResourceNotFoundException("Source account not found");
        }
        Account target = lockedAccounts.get(request.targetAccountId());
        if (target == null) {
            throw new ResourceNotFoundException("Target account not found");
        }
        BigDecimal amount = normalizeAmount(request.amount());

        if (currentUser.getRole() != Role.ADMIN && !source.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You can only transfer from your own account");
        }
        validateActiveAccount(source);
        validateActiveAccount(target);

        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient funds for transfer");
        }

        source.setBalance(normalizeBalance(source.getBalance().subtract(amount)));
        target.setBalance(normalizeBalance(target.getBalance().add(amount)));
        accountRepository.save(source);
        accountRepository.save(target);

        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setAmount(amount);
        transaction.setDescription(normalizeDescription(request.description(), "Fund transfer"));
        transaction.setReferenceNumber(generateReference("TRF"));
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setSourceAccount(source);
        transaction.setTargetAccount(target);

        return toDto(transactionRepository.save(transaction), currentUser);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsForUser(User currentUser) {
        return transactionRepository.findDashboardTransactions(currentUser.getId()).stream()
                .map(transaction -> toDto(transaction, currentUser))
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(Long id, User currentUser) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        if (currentUser.getRole() == Role.ADMIN) {
            return toDto(transaction, currentUser);
        }
        boolean canView = transaction.getUser().getId().equals(currentUser.getId())
                || (transaction.getSourceAccount() != null
                && transaction.getSourceAccount().getUser().getId().equals(currentUser.getId()))
                || (transaction.getTargetAccount() != null
                && transaction.getTargetAccount().getUser().getId().equals(currentUser.getId()));
        if (!canView) {
            throw new UnauthorizedAccessException("You are not allowed to view this transaction");
        }
        return toDto(transaction, currentUser);
    }

    private Account getAccessibleAccountForUpdate(Long accountId, User currentUser) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (currentUser.getRole() == Role.ADMIN || account.getUser().getId().equals(currentUser.getId())) {
            return account;
        }
        throw new UnauthorizedAccessException("You are not allowed to access this account");
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than two decimal places");
        }
        if (amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Amount exceeds the supported limit");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal normalizeBalance(BigDecimal balance) {
        BigDecimal normalized = balance.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.precision() - normalized.scale() > 17) {
            throw new IllegalArgumentException("Account balance exceeds the supported limit");
        }
        return normalized;
    }

    private String normalizeDescription(String description, String fallback) {
        if (description == null || description.isBlank()) {
            return fallback;
        }
        String normalized = description.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("Description must be at most 255 characters");
        }
        return normalized;
    }

    private void validateActiveAccount(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }
    }

    private String generateReference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private TransactionDto toDto(Transaction transaction, User currentUser) {
        String direction;
        if (transaction.getType() == TransactionType.DEPOSIT) {
            direction = "INCOMING";
        } else if (transaction.getType() == TransactionType.WITHDRAWAL) {
            direction = "OUTGOING";
        } else if (transaction.getSourceAccount() != null
                && transaction.getSourceAccount().getUser().getId().equals(currentUser.getId())) {
            direction = "OUTGOING";
        } else {
            direction = "INCOMING";
        }
        return new TransactionDto(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getReferenceNumber(),
                transaction.getStatus().name(),
                transaction.getSourceAccount() != null ? transaction.getSourceAccount().getAccountNumber() : null,
                transaction.getTargetAccount() != null ? transaction.getTargetAccount().getAccountNumber() : null,
                transaction.getCreatedAt(),
                direction
        );
    }
}
