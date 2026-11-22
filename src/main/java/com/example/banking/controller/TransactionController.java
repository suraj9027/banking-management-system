package com.example.banking.controller;

import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.dto.TransferRequest;
import com.example.banking.dto.WithdrawalRequest;
import com.example.banking.entity.User;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.repository.UserRepository;
import com.example.banking.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TransactionController {
    private final TransactionService transactionService;
    private final UserRepository userRepository;

    public TransactionController(TransactionService transactionService, UserRepository userRepository) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/accounts/{id}/deposit")
    public ResponseEntity<TransactionDto> deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(transactionService.deposit(id, request, getCurrentUser()));
    }

    @PostMapping("/api/accounts/{id}/withdraw")
    public ResponseEntity<TransactionDto> withdraw(@PathVariable Long id, @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.ok(transactionService.withdraw(id, request, getCurrentUser()));
    }

    @PostMapping("/api/transfers")
    public ResponseEntity<TransactionDto> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(transactionService.transfer(request, getCurrentUser()));
    }

    @GetMapping("/api/transactions")
    public List<TransactionDto> getTransactions() {
        return transactionService.getTransactionsForUser(getCurrentUser());
    }

    @GetMapping("/api/transactions/{id}")
    public TransactionDto getTransaction(@PathVariable Long id) {
        return transactionService.getTransactionById(id, getCurrentUser());
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
