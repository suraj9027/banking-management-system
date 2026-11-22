package com.example.banking.controller;

import com.example.banking.dto.AccountDto;
import com.example.banking.dto.UserDto;
import com.example.banking.entity.Role;
import com.example.banking.entity.User;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.exception.UnauthorizedAccessException;
import com.example.banking.repository.UserRepository;
import com.example.banking.service.AccountService;
import com.example.banking.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final UserService userService;
    private final AccountService accountService;

    public AdminController(UserRepository userRepository, UserService userService, AccountService accountService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.accountService = accountService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        User currentUser = getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedAccessException("Admin access required");
        }

        List<UserDto> users = userService.getAllUsers();
        List<AccountDto> accounts = accountService.getAccountsForUser(currentUser);

        Map<String, Object> result = new HashMap<>();
        result.put("userCount", users.size());
        result.put("accountCount", accounts.size());
        result.put("users", users);
        result.put("accounts", accounts);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        ensureAdmin();
        return userService.getAllUsers();
    }

    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        ensureAdmin();
        return accountService.getAccountsForUser(getCurrentUser());
    }

    private void ensureAdmin() {
        User currentUser = getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedAccessException("Admin access required");
        }
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
