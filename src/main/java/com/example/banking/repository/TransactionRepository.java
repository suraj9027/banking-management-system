package com.example.banking.repository;

import com.example.banking.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("select t from Transaction t left join t.sourceAccount source left join t.targetAccount target where (t.user.id = :userId or source.user.id = :userId or target.user.id = :userId) order by t.createdAt desc, t.id desc")
    List<Transaction> findDashboardTransactions(@Param("userId") Long userId);

    @Query("select t from Transaction t left join t.sourceAccount source left join t.targetAccount target where (t.user.id = :userId or source.user.id = :userId or target.user.id = :userId) order by t.createdAt desc, t.id desc")
    List<Transaction> findRecentDashboardTransactions(@Param("userId") Long userId, Pageable pageable);
}
