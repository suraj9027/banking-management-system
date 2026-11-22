package com.example.banking.service;

import com.example.banking.dto.AccountDto;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.dto.UpdateAccountRequest;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.entity.Role;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionStatus;
import com.example.banking.entity.TransactionType;
import com.example.banking.entity.User;
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
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request, User currentUser) {
        AccountType type = parseAccountType(request.accountType());
        BigDecimal initialBalance = request.balance() == null
                ? BigDecimal.ZERO.setScale(2)
                : normalizeMoney(request.balance());
        if (initialBalance.signum() < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setAccountType(type);
        account.setBalance(initialBalance.setScale(2, RoundingMode.HALF_UP));
        account.setStatus(AccountStatus.ACTIVE);
        account.setUser(currentUser);

        Account saved = accountRepository.save(account);
        if (initialBalance.signum() > 0) {
            Transaction openingDeposit = new Transaction();
            openingDeposit.setUser(currentUser);
            openingDeposit.setType(TransactionType.DEPOSIT);
            openingDeposit.setAmount(initialBalance);
            openingDeposit.setDescription("Initial deposit");
            openingDeposit.setReferenceNumber(generateReference());
            openingDeposit.setStatus(TransactionStatus.SUCCESS);
            openingDeposit.setTargetAccount(saved);
            transactionRepository.save(openingDeposit);
        }
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountDto> getAccountsForUser(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return accountRepository.findAll().stream().map(this::toDto).toList();
        }
        return accountRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(Long id, User currentUser) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (currentUser.getRole() != Role.ADMIN && !account.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You are not allowed to access this account");
        }
        return toDto(account);
    }

    @Transactional
    public AccountDto updateAccount(Long id, UpdateAccountRequest request, User currentUser) {
        Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (currentUser.getRole() != Role.ADMIN && !account.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You are not allowed to update this account");
        }

        if (request.balance() != null && request.balance().compareTo(account.getBalance()) != 0) {
            throw new IllegalArgumentException("Balance cannot be modified directly through the account update endpoint");
        }

        if (request.accountType() != null && !request.accountType().isBlank()) {
            account.setAccountType(parseAccountType(request.accountType()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            AccountStatus requestedStatus = parseStatus(request.status());
            if (requestedStatus == AccountStatus.CLOSED && account.getBalance().signum() > 0) {
                throw new IllegalArgumentException("Account must have zero balance before closing");
            }
            if (account.getStatus() == AccountStatus.CLOSED && requestedStatus != AccountStatus.CLOSED) {
                throw new IllegalArgumentException("A closed account cannot be reactivated");
            }
            account.setStatus(requestedStatus);
        }

        return toDto(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id, User currentUser) {
        Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (currentUser.getRole() != Role.ADMIN && !account.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You are not allowed to delete this account");
        }
        if (account.getBalance().signum() > 0) {
            throw new IllegalArgumentException("Account must have zero balance before closing");
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    private String generateAccountNumber() {
        return "ACCT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String generateReference() {
        return "DEP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Money values cannot have more than two decimal places");
        }
        if (value.precision() - value.scale() > 17) {
            throw new IllegalArgumentException("Money value exceeds the supported account balance limit");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private AccountType parseAccountType(String value) {
        if (value == null || value.isBlank()) {
            return AccountType.SAVINGS;
        }
        return AccountType.valueOf(value.trim().toUpperCase());
    }

    private AccountStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return AccountStatus.ACTIVE;
        }
        return AccountStatus.valueOf(value.trim().toUpperCase());
    }

    private AccountDto toDto(Account account) {
        return new AccountDto(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getStatus().name(),
                account.getBalance(),
                account.getUser().getId(),
                account.getUser().getUsername(),
                account.getCreatedAt()
        );
    }
}
