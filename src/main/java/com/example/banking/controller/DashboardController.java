package com.example.banking.controller;

import com.example.banking.dto.DashboardSummaryDto;
import com.example.banking.dto.MonthlySummaryDto;
import com.example.banking.dto.TransactionDto;
import com.example.banking.entity.User;
import com.example.banking.exception.ResourceNotFoundException;
import com.example.banking.repository.UserRepository;
import com.example.banking.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;
    private final UserRepository userRepository;

    public DashboardController(DashboardService dashboardService, UserRepository userRepository) {
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
    }

    @GetMapping("/summary")
    public DashboardSummaryDto summary() {
        return dashboardService.getSummary(getCurrentUser());
    }

    @GetMapping("/monthly-summary")
    public List<MonthlySummaryDto> monthlySummary() {
        return dashboardService.getMonthlySummary(getCurrentUser());
    }

    @GetMapping("/recent-transactions")
    public List<TransactionDto> recentTransactions() {
        return dashboardService.getRecentTransactions(getCurrentUser());
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResourceNotFoundException("Authentication is required");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
