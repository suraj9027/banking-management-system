package com.example.banking.service;

import com.example.banking.dto.DepositRequest;
import com.example.banking.dto.TransactionDto;
import com.example.banking.dto.TransferRequest;
import com.example.banking.entity.Account;
import com.example.banking.entity.AccountStatus;
import com.example.banking.entity.AccountType;
import com.example.banking.entity.Role;
import com.example.banking.entity.Transaction;
import com.example.banking.entity.TransactionType;
import com.example.banking.entity.User;
import com.example.banking.exception.InsufficientBalanceException;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService transactionService;
    private User customer;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(accountRepository, transactionRepository);
        customer = user(10L);
    }

    @Test
    void depositLocksTheAccountAndRecordsTheLedgerEntry() {
        Account account = account(1L, customer, "20.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionDto result = transactionService.deposit(1L, new DepositRequest(
                new BigDecimal("15.25"), "Paycheck"), customer);

        assertEquals(new BigDecimal("35.25"), account.getBalance());
        assertEquals("INCOMING", result.direction());
        verify(accountRepository).findByIdForUpdate(1L);
        verify(accountRepository).save(account);
    }

    @Test
    void withdrawalCannotSpendMoreThanTheLockedBalance() {
        Account account = account(1L, customer, "10.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThrows(InsufficientBalanceException.class, () -> transactionService.withdraw(
                1L, new com.example.banking.dto.WithdrawalRequest(new BigDecimal("10.01"), null), customer));

        assertEquals(new BigDecimal("10.00"), account.getBalance());
    }

    @Test
    void transferLocksAccountsInIdOrderAndIsVisibleToTheRecipient() {
        User recipient = user(20L);
        Account target = account(1L, recipient, "2.00");
        Account source = account(2L, customer, "20.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(source));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionDto result = transactionService.transfer(
                new TransferRequest(2L, 1L, new BigDecimal("5.00"), null), customer);

        assertEquals(new BigDecimal("15.00"), source.getBalance());
        assertEquals(new BigDecimal("7.00"), target.getBalance());
        assertEquals("OUTGOING", result.direction());

        InOrder locks = inOrder(accountRepository);
        locks.verify(accountRepository).findByIdForUpdate(1L);
        locks.verify(accountRepository).findByIdForUpdate(2L);

        Transaction transfer = new Transaction();
        transfer.setId(100L);
        transfer.setUser(customer);
        transfer.setType(TransactionType.TRANSFER);
        transfer.setAmount(new BigDecimal("5.00"));
        transfer.setSourceAccount(source);
        transfer.setTargetAccount(target);
        when(transactionRepository.findById(100L)).thenReturn(Optional.of(transfer));

        TransactionDto recipientView = transactionService.getTransactionById(100L, recipient);
        assertEquals("INCOMING", recipientView.direction());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.CUSTOMER);
        user.setUsername("customer-" + id);
        return user;
    }

    private Account account(Long id, User owner, String balance) {
        Account account = new Account();
        account.setId(id);
        account.setUser(owner);
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal(balance));
        account.setAccountNumber("ACCT-" + id);
        return account;
    }
}
