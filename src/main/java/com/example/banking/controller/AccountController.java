package com.example.banking.controller;

import com.example.banking.dto.AccountDto;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.dto.UpdateAccountRequest;
import com.example.banking.entity.User;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.repository.UserRepository;
import com.example.banking.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;
    private final UserRepository userRepository;

    public AccountController(AccountService accountService, UserRepository userRepository) {
        this.accountService = accountService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        User currentUser = getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request, currentUser));
    }

    @GetMapping
    public List<AccountDto> listAccounts() {
        return accountService.getAccountsForUser(getCurrentUser());
    }

    @GetMapping("/{id}")
    public AccountDto getAccount(@PathVariable Long id) {
        return accountService.getAccountById(id, getCurrentUser());
    }

    @PutMapping("/{id}")
    public AccountDto updateAccount(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(id, request, getCurrentUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAccount(@PathVariable Long id) {
        accountService.deleteAccount(id, getCurrentUser());
        return ResponseEntity.ok(Map.of("message", "Account closed successfully"));
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResourceNotFoundException("Authentication is required");
        }
        return userRepository.findByUsernameIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
