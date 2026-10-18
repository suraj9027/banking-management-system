package com.example.banking.service;

import com.example.banking.dto.AccountDto;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.dto.UpdateAccountRequest;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.entity.Role;
import com.example.banking.entity.User;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.UnauthorizedAccessException;
import com.example.banking.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountDto createAccount(CreateAccountRequest request, User currentUser) {
        AccountType type = parseAccountType(request.accountType());
        BigDecimal initialBalance = request.balance() == null ? BigDecimal.ZERO : request.balance();

        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setAccountType(type);
        account.setBalance(initialBalance.setScale(2, RoundingMode.HALF_UP));
        account.setStatus(AccountStatus.ACTIVE);
        account.setUser(currentUser);

        Account saved = accountRepository.save(account);
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
        Account account = accountRepository.findById(id)
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
            account.setStatus(parseStatus(request.status()));
        }

        return toDto(accountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long id, User currentUser) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (currentUser.getRole() != Role.ADMIN && !account.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("You are not allowed to delete this account");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Account must have zero balance before closing");
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    public boolean canAccessAccount(User currentUser, Account account) {
        return currentUser.getRole() == Role.ADMIN || account.getUser().getId().equals(currentUser.getId());
    }

    private String generateAccountNumber() {
        return "ACCT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
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
