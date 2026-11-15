package com.revature.transactions;

import com.revature.account.AccountDAO;
import com.revature.account.CheckingAccount;
import com.revature.customer.Customer;
import com.revature.util.ConnectionProvider;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import com.revature.account.investment.InvestmentAccount;
import com.revature.account.investment.InvestmentContribution;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests financial transaction business rules and database transaction handling.
 */
public class TransactionServiceTest {

    /**
     * Tests that a successful deposit updates the account balance,
     * records the transaction and audit, and commits all three operations together.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void successfulDepositCommitsBalanceUpdateAndTransactionRecord() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create an active Checking Account belonging to the customer.
        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);
        account.setAccountNumber("100004");

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Return the same connection for the entire deposit.
        when(connectionProvider.getConnection()).thenReturn(connection);

        //Simulate the account balance increasing after a $50.00 deposit.
        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal updatedBalance = new BigDecimal("150.00");

        when(accountDAO.deposit(connection, 5L, 1L, amount))
                .thenReturn(updatedBalance);

        //Simulate PostgreSQL generating the transaction ID.
        when(transactionDAO.insertDepositTransaction(connection, 5L, amount, updatedBalance))
                .thenReturn(100001L);

        //Create the service using mocked dependencies.
        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Make the deposit.
        long transactionId = transactionService.deposit(customer, account, amount);

        //Verify that the generated transaction ID is returned.
        assertEquals(100001L, transactionId);

        //Verify that all three database operations used the same connection.
        verify(accountDAO).deposit(connection, 5L, 1L, amount);
        verify(transactionDAO).insertDepositTransaction(
                connection, 5L, amount, updatedBalance);
        verify(transactionDAO).insertDepositAudit(connection, 1L, 100001L);

        //Verify that the database transaction was committed.
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection, never()).rollback();

        //Verify that the connection was closed afterward.
        verify(connection).close();

        //Verify that the balance update, transaction record, and audit happen before committing.
        InOrder operationOrder = inOrder(accountDAO, transactionDAO, connection);
        operationOrder.verify(accountDAO).deposit(connection, 5L, 1L, amount);
        operationOrder.verify(transactionDAO).insertDepositTransaction(
                connection, 5L, amount, updatedBalance);
        operationOrder.verify(transactionDAO).insertDepositAudit(
                connection, 1L, 100001L);
        operationOrder.verify(connection).commit();
    }

    /**
     * Tests that a deposit rolls back its balance update when saving
     * the transaction record fails.
     *
     * @throws SQLException if the expected mocked database failure occurs
     */
    @Test
    void depositRollsBackWhenTransactionRecordFails() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create an active Checking Account belonging to the customer.
        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);
        account.setAccountNumber("100004");

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Return one connection for both database operations.
        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal updatedBalance = new BigDecimal("150.00");

        //Simulate the account balance update succeeding.
        when(accountDAO.deposit(connection, 5L, 1L, amount))
                .thenReturn(updatedBalance);

        //Simulate a database error when recording the deposit.
        when(transactionDAO.insertDepositTransaction(connection, 5L, amount, updatedBalance))
                .thenThrow(new SQLException("Simulated transaction insert failure"));

        //Create the service with mocked dependencies.
        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Verify that the database error is passed back to the caller.
        SQLException exception = assertThrows(SQLException.class,
                () -> transactionService.deposit(customer, account, amount));

        assertEquals("Simulated transaction insert failure", exception.getMessage());

        //Verify that the balance update and transaction insert were attempted.
        verify(accountDAO).deposit(connection, 5L, 1L, amount);
        verify(transactionDAO).insertDepositTransaction(
                connection, 5L, amount, updatedBalance);

        //Verify that no successful audit is recorded when the transaction insert fails.
        verify(transactionDAO, never()).insertDepositAudit(
                any(Connection.class), anyLong(), anyLong());

        //Verify that the transaction is rolled back instead of committed.
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();

        //Verify that the connection was closed afterward.
        verify(connection).close();

        //Verify that rollback happens after the failed transaction insert.
        InOrder operationOrder = inOrder(accountDAO, transactionDAO, connection);
        operationOrder.verify(accountDAO).deposit(connection, 5L, 1L, amount);
        operationOrder.verify(transactionDAO).insertDepositTransaction(
                connection, 5L, amount, updatedBalance);
        operationOrder.verify(connection).rollback();
    }

    /**
     * Tests that a failed deposit audit rolls back both the balance update
     * and the transaction record instead of committing incomplete data.
     *
     * @throws SQLException if the expected mocked database failure occurs
     */
    @Test
    void depositRollsBackWhenAuditInsertFails() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create an active Checking Account belonging to the customer.
        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);
        account.setAccountNumber("100004");

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Return one connection for all three database operations.
        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal updatedBalance = new BigDecimal("150.00");

        //Simulate the balance update and transaction insert succeeding.
        when(accountDAO.deposit(connection, 5L, 1L, amount))
                .thenReturn(updatedBalance);
        when(transactionDAO.insertDepositTransaction(
                connection, 5L, amount, updatedBalance))
                .thenReturn(100001L);

        //Simulate a database error while recording the audit.
        doThrow(new SQLException("Simulated audit insert failure"))
                .when(transactionDAO)
                .insertDepositAudit(connection, 1L, 100001L);

        //Create the service with mocked dependencies.
        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Verify that the audit failure is passed back to the caller.
        SQLException exception = assertThrows(SQLException.class,
                () -> transactionService.deposit(customer, account, amount));

        assertEquals("Simulated audit insert failure", exception.getMessage());

        //Verify that all three database operations were attempted.
        verify(accountDAO).deposit(connection, 5L, 1L, amount);
        verify(transactionDAO).insertDepositTransaction(
                connection, 5L, amount, updatedBalance);
        verify(transactionDAO).insertDepositAudit(connection, 1L, 100001L);

        //Verify that the entire deposit is rolled back.
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();

        //Verify that the connection is closed.
        verify(connection).close();

        //Verify that the audit failure occurs before rollback.
        InOrder operationOrder = inOrder(accountDAO, transactionDAO, connection);
        operationOrder.verify(accountDAO).deposit(connection, 5L, 1L, amount);
        operationOrder.verify(transactionDAO).insertDepositTransaction(
                connection, 5L, amount, updatedBalance);
        operationOrder.verify(transactionDAO).insertDepositAudit(
                connection, 1L, 100001L);
        operationOrder.verify(connection).rollback();
    }


    /**
     * Tests that a successful withdrawal updates the balance,
     * records the transaction and audit, and commits all operations.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void successfulWithdrawalCommitsAllOperations() throws SQLException {
        //Create an active customer and Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);
        account.setAccountNumber("100004");

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal updatedBalance = new BigDecimal("3400.00");

        //Simulate a successful balance deduction and transaction insert.
        when(accountDAO.withdraw(connection, 5L, 1L, amount))
                .thenReturn(updatedBalance);
        when(transactionDAO.insertWithdrawalTransaction(
                connection, 5L, amount, updatedBalance))
                .thenReturn(200001L);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Perform the withdrawal.
        long transactionId = transactionService.withdraw(customer, account, amount);

        assertEquals(200001L, transactionId);

        //Verify that all operations use the same connection.
        InOrder operationOrder = inOrder(accountDAO, transactionDAO, connection);
        operationOrder.verify(accountDAO).withdraw(connection, 5L, 1L, amount);
        operationOrder.verify(transactionDAO).insertWithdrawalTransaction(
                connection, 5L, amount, updatedBalance);
        operationOrder.verify(transactionDAO).insertWithdrawalAudit(
                connection, 1L, 200001L);
        operationOrder.verify(connection).commit();

        //Verify the connection lifecycle.
        verify(connection).setAutoCommit(false);
        verify(connection, never()).rollback();
        verify(connection).close();
    }

    /**
     * Tests that a failed withdrawal transaction insert causes
     * the entire database transaction to roll back.
     *
     * @throws SQLException if the expected database failure occurs
     */
    @Test
    void withdrawalRollsBackWhenTransactionInsertFails() throws SQLException {
        //Create an active customer and Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal updatedBalance = new BigDecimal("3400.00");

        //Simulate the balance update succeeding.
        when(accountDAO.withdraw(connection, 5L, 1L, amount))
                .thenReturn(updatedBalance);

        //Simulate the transaction record failing to save.
        when(transactionDAO.insertWithdrawalTransaction(
                connection, 5L, amount, updatedBalance))
                .thenThrow(new SQLException("Simulated withdrawal insert failure"));

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Verify that the error is propagated.
        assertThrows(SQLException.class, () ->
                transactionService.withdraw(customer, account, amount));

        //Verify that the withdrawal is rolled back.
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();

        //Verify that no success audit is inserted.
        verify(transactionDAO, never()).insertWithdrawalAudit(
                any(Connection.class), anyLong(), anyLong());
    }

    /**
     * Tests that a failed withdrawal audit rolls back the balance
     * deduction and transaction record.
     *
     * @throws SQLException if the expected database failure occurs
     */
    @Test
    void withdrawalRollsBackWhenAuditInsertFails() throws SQLException {
        //Create an active customer and Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal updatedBalance = new BigDecimal("3400.00");

        //Simulate the balance update and transaction insert succeeding.
        when(accountDAO.withdraw(connection, 5L, 1L, amount))
                .thenReturn(updatedBalance);
        when(transactionDAO.insertWithdrawalTransaction(
                connection, 5L, amount, updatedBalance))
                .thenReturn(200001L);

        //Simulate the audit insert failing.
        doThrow(new SQLException("Simulated withdrawal audit failure"))
                .when(transactionDAO)
                .insertWithdrawalAudit(connection, 1L, 200001L);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Verify that the failure is propagated.
        assertThrows(SQLException.class, () ->
                transactionService.withdraw(customer, account, amount));

        //Verify that the entire transaction is rolled back.
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
    }

    /**
     * Tests that zero and negative withdrawal amounts are rejected
     * before a database connection is requested.
     */
    @Test
    void withdrawalRejectsNonPositiveAmounts() {
        //Create an active customer and Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);

        //Create mocked dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Reject zero and negative amounts.
        assertThrows(IllegalArgumentException.class, () ->
                transactionService.withdraw(customer, account, BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class, () ->
                transactionService.withdraw(customer, account, new BigDecimal("-10.00")));

        //Verify that invalid withdrawals never reach PostgreSQL.
        verifyNoInteractions(connectionProvider, accountDAO, transactionDAO);
    }


    /**
     * Tests that a successful transfer commits both balance updates,
     * the transaction record, and the audit.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void successfulTransferCommitsAllOperations() throws SQLException {
        //Prepare an active customer with two saved accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount sourceAccount = new CheckingAccount(customer);
        sourceAccount.setId(1L);

        CheckingAccount destinationAccount = new CheckingAccount(customer);
        destinationAccount.setId(2L);

        //Prepare mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
        when(accountDAO.debitTransfer(connection, 1L, 1L, new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("3200.00"));
        when(accountDAO.creditTransfer(connection, 2L, 1L, new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("200.00"));
        when(transactionDAO.insertTransferTransaction(
                connection, 1L, 2L, new BigDecimal("200.00"),
                new BigDecimal("3200.00"), new BigDecimal("200.00")))
                .thenReturn(3L);

        TransactionService transactionService =
                new TransactionService(accountDAO, transactionDAO, connectionProvider);

        //Execute the transfer and verify the transaction ID.
        long transactionId = transactionService.transfer(
                customer, sourceAccount, destinationAccount, new BigDecimal("200.00"));

        assertEquals(3L, transactionId);

        //Verify the order of operations before the database commit.
        InOrder inOrder = inOrder(connection, accountDAO, transactionDAO);
        inOrder.verify(connection).setAutoCommit(false);
        inOrder.verify(accountDAO).debitTransfer(
                connection, 1L, 1L, new BigDecimal("200.00"));
        inOrder.verify(accountDAO).creditTransfer(
                connection, 2L, 1L, new BigDecimal("200.00"));
        inOrder.verify(transactionDAO).insertTransferTransaction(
                connection, 1L, 2L, new BigDecimal("200.00"),
                new BigDecimal("3200.00"), new BigDecimal("200.00"));
        inOrder.verify(transactionDAO).insertTransferAudit(connection, 1L, 3L);
        inOrder.verify(connection).commit();

        verify(connection, never()).rollback();
        verify(connection).close();
    }

    /**
     * Tests that a transfer rolls back when the destination account
     * cannot receive the money.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void transferRollsBackWhenDestinationCreditFails() throws SQLException {
        //Prepare the customer and two saved accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount sourceAccount = new CheckingAccount(customer);
        sourceAccount.setId(1L);

        CheckingAccount destinationAccount = new CheckingAccount(customer);
        destinationAccount.setId(2L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
        when(accountDAO.debitTransfer(connection, 1L, 1L, new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("3200.00"));
        when(accountDAO.creditTransfer(connection, 2L, 1L, new BigDecimal("200.00")))
                .thenThrow(new SQLException("Destination account unavailable."));

        TransactionService transactionService =
                new TransactionService(accountDAO, transactionDAO, connectionProvider);

        //A failed credit must undo the source debit.
        assertThrows(SQLException.class, () -> transactionService.transfer(
                customer, sourceAccount, destinationAccount, new BigDecimal("200.00")));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verifyNoInteractions(transactionDAO);
        verify(connection).close();
    }

    /**
     * Tests that a transfer rolls back when the audit record cannot
     * be saved after both balances have been updated.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void transferRollsBackWhenAuditFails() throws SQLException {
        //Prepare the customer and two saved accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount sourceAccount = new CheckingAccount(customer);
        sourceAccount.setId(1L);

        CheckingAccount destinationAccount = new CheckingAccount(customer);
        destinationAccount.setId(2L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
        when(accountDAO.debitTransfer(connection, 1L, 1L, new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("3200.00"));
        when(accountDAO.creditTransfer(connection, 2L, 1L, new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("200.00"));
        when(transactionDAO.insertTransferTransaction(
                connection, 1L, 2L, new BigDecimal("200.00"),
                new BigDecimal("3200.00"), new BigDecimal("200.00")))
                .thenReturn(3L);

        doThrow(new SQLException("Audit insertion failed."))
                .when(transactionDAO).insertTransferAudit(connection, 1L, 3L);

        TransactionService transactionService =
                new TransactionService(accountDAO, transactionDAO, connectionProvider);

        //The audit failure must undo both balance changes and the transaction record.
        assertThrows(SQLException.class, () -> transactionService.transfer(
                customer, sourceAccount, destinationAccount, new BigDecimal("200.00")));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
    }

    /**
     * Tests that transfers reject attempts to use the same account
     * as both the source and destination.
     */
    @Test
    void transferRejectsSameSourceAndDestination() {
        //Prepare a valid customer and one saved account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(1L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);

        TransactionService transactionService =
                new TransactionService(accountDAO, transactionDAO, connectionProvider);

        //Reject the transfer before opening a database connection.
        assertThrows(IllegalArgumentException.class, () -> transactionService.transfer(
                customer, account, account, new BigDecimal("200.00")));

        verifyNoInteractions(connectionProvider, accountDAO, transactionDAO);
    }


    /**
     * Tests that the service retrieves account history using the
     * authenticated customer's ID and closes the connection afterward.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getTransactionHistoryReturnsAccountTransactions() throws SQLException {
        //Prepare an active customer and saved Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        //Prepare one transaction history entry.
        TransactionHistoryEntry entry = new TransactionHistoryEntry(
                3L, "TRANSFER", new BigDecimal("200.00"),
                "OUT", "SUCCESS", "Transfer between own accounts",
                new BigDecimal("3200.00"),
                OffsetDateTime.parse("2026-11-13T10:30:00Z"));

        List<TransactionHistoryEntry> expectedHistory = List.of(entry);

        when(transactionDAO.getTransactionHistory(connection, 1L, 5L))
                .thenReturn(expectedHistory);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        List<TransactionHistoryEntry> history =
                transactionService.getTransactionHistory(customer, account);

        assertEquals(expectedHistory, history);
        verify(transactionDAO).getTransactionHistory(connection, 1L, 5L);
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(accountDAO, never()).deposit(any(), anyLong(), anyLong(), any());
    }

    /**
     * Tests that the service rejects invalid customers or accounts
     * before requesting a database connection.
     */
    @Test
    void getTransactionHistoryRejectsInvalidCustomerOrAccount() {
        //Prepare an active customer and a saved account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Reject missing customers and unsaved accounts.
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.getTransactionHistory(null, account));

        CheckingAccount unsavedAccount = new CheckingAccount(customer);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.getTransactionHistory(customer, unsavedAccount));

        verifyNoInteractions(connectionProvider, accountDAO, transactionDAO);
    }

    /**
     * Tests that database failures during history retrieval are
     * propagated and the connection is closed.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getTransactionHistoryClosesConnectionOnFailure() throws SQLException {
        //Prepare an active customer and saved Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount account = new CheckingAccount(customer);
        account.setId(5L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
        when(transactionDAO.getTransactionHistory(connection, 1L, 5L))
                .thenThrow(new SQLException("Simulated history query failure"));

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        assertThrows(SQLException.class,
                () -> transactionService.getTransactionHistory(customer, account));

        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }


    /**
     * Tests that a partial loan repayment debits the funding account,
     * reduces the loan balance, records the transaction and audit,
     * and commits all operations together.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void repayLoanCommitsSuccessfulPartialRepayment() throws SQLException {
        //Create an active customer.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create an active Checking Account and Loan Account.
        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        com.revature.account.LoanAccount loanAccount =
                new com.revature.account.LoanAccount(
                        customer, new BigDecimal("550.00"),
                        new BigDecimal("5.00"), 12,
                        java.time.LocalDate.now());
        loanAccount.setId(4L);

        //Mock the database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        //Simulate the two updated balances.
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal checkingBalanceAfter = new BigDecimal("3100.00");
        BigDecimal loanBalanceAfter = new BigDecimal("450.00");

        when(accountDAO.debitTransfer(connection, 1L, 1L, amount))
                .thenReturn(checkingBalanceAfter);
        when(accountDAO.repayLoan(connection, 4L, 1L, amount))
                .thenReturn(loanBalanceAfter);

        //Simulate PostgreSQL generating a transaction ID.
        when(transactionDAO.insertLoanRepaymentTransaction(
                connection, 1L, 4L, amount,
                checkingBalanceAfter, loanBalanceAfter))
                .thenReturn(300001L);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Make the repayment.
        long transactionId = transactionService.repayLoan(
                customer, checkingAccount, loanAccount, amount);

        assertEquals(300001L, transactionId);

        //Verify that every operation uses the same connection.
        InOrder operationOrder = inOrder(accountDAO, transactionDAO, connection);

        operationOrder.verify(connection).setAutoCommit(false);
        operationOrder.verify(accountDAO).debitTransfer(
                connection, 1L, 1L, amount);
        operationOrder.verify(accountDAO).repayLoan(
                connection, 4L, 1L, amount);
        operationOrder.verify(transactionDAO).insertLoanRepaymentTransaction(
                connection, 1L, 4L, amount,
                checkingBalanceAfter, loanBalanceAfter);
        operationOrder.verify(transactionDAO).insertLoanRepaymentAudit(
                connection, 1L, 300001L);
        operationOrder.verify(connection).commit();

        verify(connection, never()).rollback();
        verify(connection).close();
    }

    /**
     * Tests that a full loan repayment records zero remaining debt
     * and commits the transaction.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void repayLoanAcceptsFullRepayment() throws SQLException {
        //Create an active customer and their accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        com.revature.account.LoanAccount loanAccount =
                new com.revature.account.LoanAccount(
                        customer, new BigDecimal("550.00"),
                        new BigDecimal("5.00"), 12,
                        java.time.LocalDate.now());
        loanAccount.setId(4L);

        //Mock a full repayment of $550.00.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("550.00");
        BigDecimal checkingBalanceAfter = new BigDecimal("2650.00");
        BigDecimal loanBalanceAfter = new BigDecimal("0.00");

        when(accountDAO.debitTransfer(connection, 1L, 1L, amount))
                .thenReturn(checkingBalanceAfter);
        when(accountDAO.repayLoan(connection, 4L, 1L, amount))
                .thenReturn(loanBalanceAfter);

        when(transactionDAO.insertLoanRepaymentTransaction(
                connection, 1L, 4L, amount,
                checkingBalanceAfter, loanBalanceAfter))
                .thenReturn(300002L);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Repay the entire outstanding loan.
        long transactionId = transactionService.repayLoan(
                customer, checkingAccount, loanAccount, amount);

        assertEquals(300002L, transactionId);

        //Verify that the zero loan balance is recorded.
        verify(transactionDAO).insertLoanRepaymentTransaction(
                connection, 1L, 4L, amount,
                checkingBalanceAfter, new BigDecimal("0.00"));

        verify(transactionDAO).insertLoanRepaymentAudit(
                connection, 1L, 300002L);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    /**
     * Tests that a rejected loan repayment rolls back the funding account debit.
     *
     * @throws SQLException if the mocked loan update fails
     */
    @Test
    void repayLoanRollsBackWhenLoanUpdateFails() throws SQLException {
        //Create an active customer and their accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        com.revature.account.LoanAccount loanAccount =
                new com.revature.account.LoanAccount(
                        customer, new BigDecimal("550.00"),
                        new BigDecimal("5.00"), 12,
                        java.time.LocalDate.now());
        loanAccount.setId(4L);

        //Mock the dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("600.00");

        //Simulate a successful debit followed by a rejected loan overpayment.
        when(accountDAO.debitTransfer(connection, 1L, 1L, amount))
                .thenReturn(new BigDecimal("2600.00"));

        when(accountDAO.repayLoan(connection, 4L, 1L, amount))
                .thenThrow(new SQLException("Loan repayment rejected"));

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Verify that the failure is reported.
        SQLException exception = assertThrows(SQLException.class,
                () -> transactionService.repayLoan(
                        customer, checkingAccount, loanAccount, amount));

        assertEquals("Loan repayment rejected", exception.getMessage());

        //Verify that the debit was attempted but the transaction was rolled back.
        verify(accountDAO).debitTransfer(connection, 1L, 1L, amount);
        verify(accountDAO).repayLoan(connection, 4L, 1L, amount);

        verify(connection).rollback();
        verify(connection, never()).commit();

        //No successful transaction or audit should be recorded.
        verifyNoInteractions(transactionDAO);
    }

    /**
     * Tests that a Loan Account cannot fund its own repayment
     * and that Checking Accounts cannot receive loan repayments.
     */
    @Test
    void repayLoanRejectsInvalidAccountTypes() {
        //Create an active customer and their accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        com.revature.account.LoanAccount loanAccount =
                new com.revature.account.LoanAccount(
                        customer, new BigDecimal("550.00"),
                        new BigDecimal("5.00"), 12,
                        java.time.LocalDate.now());
        loanAccount.setId(4L);

        //Create the service with mocked dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        BigDecimal amount = new BigDecimal("100.00");

        //A Loan Account cannot be used as the funding account.
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.repayLoan(
                        customer, loanAccount, checkingAccount, amount));

        //A Checking Account cannot be the loan repayment destination.
        CheckingAccount secondCheckingAccount = new CheckingAccount(customer);
        secondCheckingAccount.setId(2L);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.repayLoan(
                        customer, checkingAccount, secondCheckingAccount, amount));

        //Invalid account types must be rejected before database access.
        verifyNoInteractions(accountDAO, transactionDAO, connectionProvider);
    }


    /**
     * Tests that an Investment Account contribution debits the funding
     * account, credits the investment, records its history and audit,
     * and commits all operations together.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void contributeToInvestmentCommitsAllOperations() throws SQLException {
        //Create an active customer and Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        //Mock the Investment Account without assuming its constructor.
        InvestmentAccount investmentAccount = mock(InvestmentAccount.class);
        when(investmentAccount.getId()).thenReturn(3L);
        when(investmentAccount.getAccountType()).thenReturn("INVESTMENT");
        when(investmentAccount.getStatus()).thenReturn("ACTIVE");
        when(investmentAccount.getAccountOwnerName()).thenReturn(customer);

        //Prepare mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("500.00");
        BigDecimal checkingBalanceAfter = new BigDecimal("2150.00");
        BigDecimal investmentBalanceAfter = new BigDecimal("500.00");

        when(accountDAO.debitTransfer(connection, 1L, 1L, amount))
                .thenReturn(checkingBalanceAfter);

        when(accountDAO.creditInvestmentContribution(connection, 3L, 1L, amount))
                .thenReturn(investmentBalanceAfter);

        when(accountDAO.insertInvestmentContribution(connection, 3L, amount))
                .thenReturn(1L);

        when(transactionDAO.insertInvestmentContributionTransaction(
                connection, 1L, 3L, amount,
                checkingBalanceAfter, investmentBalanceAfter))
                .thenReturn(6L);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //Contribute $500 from Checking to the Investment Account.
        long transactionId = transactionService.contributeToInvestment(
                customer, checkingAccount, investmentAccount, amount);

        assertEquals(6L, transactionId);

        //Verify the exact order of all operations.
        InOrder operationOrder = inOrder(connection, accountDAO, transactionDAO);

        operationOrder.verify(connection).setAutoCommit(false);
        operationOrder.verify(accountDAO).debitTransfer(
                connection, 1L, 1L, amount);
        operationOrder.verify(accountDAO).creditInvestmentContribution(
                connection, 3L, 1L, amount);
        operationOrder.verify(accountDAO).insertInvestmentContribution(
                connection, 3L, amount);
        operationOrder.verify(transactionDAO).insertInvestmentContributionTransaction(
                connection, 1L, 3L, amount,
                checkingBalanceAfter, investmentBalanceAfter);
        operationOrder.verify(transactionDAO).insertInvestmentContributionAudit(
                connection, 1L, 6L);
        operationOrder.verify(connection).commit();

        verify(connection, never()).rollback();
        verify(connection).close();
    }

    /**
     * Tests that a rejected Investment Account credit rolls back the
     * funding debit and prevents contribution records from being saved.
     *
     * @throws SQLException if the mocked investment credit fails
     */
    @Test
    void contributeToInvestmentRollsBackWhenCreditFails() throws SQLException {
        //Create an active customer and funding account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        InvestmentAccount investmentAccount = mock(InvestmentAccount.class);
        when(investmentAccount.getId()).thenReturn(3L);
        when(investmentAccount.getAccountType()).thenReturn("INVESTMENT");
        when(investmentAccount.getStatus()).thenReturn("ACTIVE");
        when(investmentAccount.getAccountOwnerName()).thenReturn(customer);

        //Mock a successful debit followed by a rejected investment credit.
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("500.00");

        when(accountDAO.debitTransfer(connection, 1L, 1L, amount))
                .thenReturn(new BigDecimal("2150.00"));

        when(accountDAO.creditInvestmentContribution(connection, 3L, 1L, amount))
                .thenThrow(new SQLException("Investment Account is not eligible."));

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //A rejected investment credit must undo the funding debit.
        SQLException exception = assertThrows(SQLException.class,
                () -> transactionService.contributeToInvestment(
                        customer, checkingAccount, investmentAccount, amount));

        assertEquals("Investment Account is not eligible.", exception.getMessage());

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();

        //No contribution history, transaction, or audit should be saved.
        verify(accountDAO, never()).insertInvestmentContribution(
                any(Connection.class), anyLong(), any(BigDecimal.class));
        verifyNoInteractions(transactionDAO);
    }

    /**
     * Tests that an audit insertion failure rolls back both account
     * balances, the contribution history, and the transaction record.
     *
     * @throws SQLException if the mocked audit insertion fails
     */
    @Test
    void contributeToInvestmentRollsBackWhenAuditFails() throws SQLException {
        //Create an active customer and both accounts.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        InvestmentAccount investmentAccount = mock(InvestmentAccount.class);
        when(investmentAccount.getId()).thenReturn(3L);
        when(investmentAccount.getAccountType()).thenReturn("INVESTMENT");
        when(investmentAccount.getStatus()).thenReturn("ACTIVE");
        when(investmentAccount.getAccountOwnerName()).thenReturn(customer);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        when(connectionProvider.getConnection()).thenReturn(connection);

        BigDecimal amount = new BigDecimal("500.00");
        BigDecimal checkingBalanceAfter = new BigDecimal("2150.00");
        BigDecimal investmentBalanceAfter = new BigDecimal("500.00");

        when(accountDAO.debitTransfer(connection, 1L, 1L, amount))
                .thenReturn(checkingBalanceAfter);

        when(accountDAO.creditInvestmentContribution(connection, 3L, 1L, amount))
                .thenReturn(investmentBalanceAfter);

        when(accountDAO.insertInvestmentContribution(connection, 3L, amount))
                .thenReturn(1L);

        when(transactionDAO.insertInvestmentContributionTransaction(
                connection, 1L, 3L, amount,
                checkingBalanceAfter, investmentBalanceAfter))
                .thenReturn(6L);

        doThrow(new SQLException("Simulated investment audit failure"))
                .when(transactionDAO)
                .insertInvestmentContributionAudit(connection, 1L, 6L);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        //An audit failure must roll back the complete contribution.
        assertThrows(SQLException.class,
                () -> transactionService.contributeToInvestment(
                        customer, checkingAccount, investmentAccount, amount));

        verify(accountDAO).insertInvestmentContribution(connection, 3L, amount);
        verify(transactionDAO).insertInvestmentContributionTransaction(
                connection, 1L, 3L, amount,
                checkingBalanceAfter, investmentBalanceAfter);
        verify(transactionDAO).insertInvestmentContributionAudit(connection, 1L, 6L);

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
    }

    /**
     * Tests that invalid account types, ownership, and amounts are
     * rejected before requesting a database connection.
     */
    @Test
    void contributeToInvestmentRejectsInvalidInputs() {
        //Create an active customer and saved Checking Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);

        InvestmentAccount investmentAccount = mock(InvestmentAccount.class);
        when(investmentAccount.getId()).thenReturn(3L);
        when(investmentAccount.getAccountType()).thenReturn("INVESTMENT");
        when(investmentAccount.getStatus()).thenReturn("ACTIVE");
        when(investmentAccount.getAccountOwnerName()).thenReturn(customer);

        //Create a second customer for ownership validation.
        Customer otherCustomer = new Customer("Other", "Customer", "other@example.com",
                "1234567891", "othercustomer", "unused-test-hash");
        otherCustomer.setId(2L);

        CheckingAccount otherCheckingAccount = new CheckingAccount(otherCustomer);
        otherCheckingAccount.setId(2L);

        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);

        TransactionService transactionService = new TransactionService(
                accountDAO, transactionDAO, connectionProvider);

        BigDecimal amount = new BigDecimal("100.00");

        //Reject using an Investment Account as the funding source.
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.contributeToInvestment(
                        customer, investmentAccount, checkingAccount, amount));

        //Reject using a Checking Account as the destination.
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.contributeToInvestment(
                        customer, checkingAccount, otherCheckingAccount, amount));

        //Reject accounts belonging to another customer.
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.contributeToInvestment(
                        customer, otherCheckingAccount, investmentAccount, amount));

        //Reject zero, negative, and fractional-cent amounts.
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.contributeToInvestment(
                        customer, checkingAccount, investmentAccount, BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.contributeToInvestment(
                        customer, checkingAccount, investmentAccount,
                        new BigDecimal("-10.00")));

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.contributeToInvestment(
                        customer, checkingAccount, investmentAccount,
                        new BigDecimal("10.123")));

        //Invalid requests must never reach the database.
        verifyNoInteractions(connectionProvider, accountDAO, transactionDAO);
    }



    /**
     * Creates a matured Investment Account for atomic payout tests.
     *
     * @param customer the saved account owner
     * @return an eligible Investment Account
     */
    private InvestmentAccount createMaturedInvestment(Customer customer) {
        InvestmentAccount account = new InvestmentAccount(customer, new BigDecimal("650.00"),
                12, new BigDecimal("5.00"), LocalDate.now().minusDays(1));
        account.setId(3L);
        return account;
    }

    /**
     * Tests that release, interest, credit, transaction, audit, and commit share one connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void payMaturedInvestmentCommitsAllOperations() throws SQLException {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        InvestmentAccount investment = createMaturedInvestment(customer);
        CheckingAccount destination = new CheckingAccount(customer);
        destination.setId(5L);
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        when(provider.getConnection()).thenReturn(connection);
        when(accountDAO.releaseMaturedInvestment(connection, 3L, 1L))
                .thenReturn(new BigDecimal("650.00"));
        when(accountDAO.findInvestmentContributions(connection, 3L, 1L)).thenReturn(List.of(
                new InvestmentContribution(new BigDecimal("650.00"),
                        OffsetDateTime.now().minusDays(2))));
        BigDecimal payout = new BigDecimal("650.09");
        when(accountDAO.creditInvestmentMaturityPayout(connection, 5L, 1L, payout))
                .thenReturn(new BigDecimal("1650.09"));
        when(transactionDAO.insertInvestmentMaturityTransaction(
                connection, 3L, 5L, payout, new BigDecimal("1650.09"))).thenReturn(7L);

        long id = new TransactionService(accountDAO, transactionDAO, provider)
                .payMaturedInvestment(customer, investment, destination);
        assertEquals(7L, id);
        InOrder order = inOrder(connection, accountDAO, transactionDAO);
        order.verify(connection).setAutoCommit(false);
        order.verify(accountDAO).releaseMaturedInvestment(connection, 3L, 1L);
        order.verify(accountDAO).findInvestmentContributions(connection, 3L, 1L);
        order.verify(accountDAO).creditInvestmentMaturityPayout(connection, 5L, 1L, payout);
        order.verify(transactionDAO).insertInvestmentMaturityTransaction(
                connection, 3L, 5L, payout, new BigDecimal("1650.09"));
        order.verify(transactionDAO).insertInvestmentMaturityAudit(connection, 1L, 7L);
        order.verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection).close();
    }

    /**
     * Tests that a failed destination credit rolls back the released investment.
     *
     * @throws SQLException if the mocked credit fails
     */
    @Test
    void payMaturedInvestmentRollsBackWhenCreditFails() throws SQLException {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        InvestmentAccount investment = createMaturedInvestment(customer);
        CheckingAccount destination = new CheckingAccount(customer);
        destination.setId(5L);
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        when(provider.getConnection()).thenReturn(connection);
        when(accountDAO.releaseMaturedInvestment(connection, 3L, 1L))
                .thenReturn(new BigDecimal("650.00"));
        when(accountDAO.findInvestmentContributions(connection, 3L, 1L)).thenReturn(List.of());
        when(accountDAO.creditInvestmentMaturityPayout(
                connection, 5L, 1L, new BigDecimal("650.00")))
                .thenThrow(new SQLException("Credit failed"));

        assertThrows(SQLException.class, () -> new TransactionService(accountDAO, transactionDAO, provider)
                .payMaturedInvestment(customer, investment, destination));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).close();
        verifyNoInteractions(transactionDAO);
    }

    /**
     * Tests that a premature investment is rejected before database access.
     */
    @Test
    void payMaturedInvestmentRejectsPrematureAccount() {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        InvestmentAccount investment = new InvestmentAccount(customer,
                new BigDecimal("650.00"), 12, new BigDecimal("5.00"),
                LocalDate.now().plusDays(30));
        investment.setId(3L);
        CheckingAccount destination = new CheckingAccount(customer);
        destination.setId(5L);
        AccountDAO accountDAO = mock(AccountDAO.class);
        TransactionDAO transactionDAO = mock(TransactionDAO.class);
        ConnectionProvider provider = mock(ConnectionProvider.class);

        assertThrows(IllegalStateException.class,
                () -> new TransactionService(accountDAO, transactionDAO, provider)
                        .payMaturedInvestment(customer, investment, destination));
        verifyNoInteractions(provider, accountDAO, transactionDAO);
    }

}