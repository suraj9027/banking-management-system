package com.example.banking.repository;

import com.example.banking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select t from Transaction t where t.user.id = :userId and t.createdAt between :start and :end order by t.createdAt desc")
    List<Transaction> findByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

    @Query("select t from Transaction t where (t.user.id = :userId or t.sourceAccount.user.id = :userId or t.targetAccount.user.id = :userId) order by t.createdAt desc")
    List<Transaction> findDashboardTransactions(Long userId);
}
