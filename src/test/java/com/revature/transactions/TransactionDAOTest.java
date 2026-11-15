package com.revature.transactions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests database operations for financial transaction records.
 */
public class TransactionDAOTest {

    /**
     * Tests that a successful deposit transaction is recorded using
     * an existing connection without committing or closing it.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertDepositTransactionRecordsDepositUsingExistingConnection() throws SQLException {
        //Create mocked database objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        //Configure the prepared statement to insert one transaction record.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        ResultSet resultSet = mock(ResultSet.class);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(100000L);

        //Record a $50.00 deposit with a resulting balance of $150.00.
        TransactionDAO transactionDAO = new TransactionDAO();
        long transactionId = transactionDAO.insertDepositTransaction(
                connection, 1L, new BigDecimal("50.00"), new BigDecimal("150.00"));

        //Verify that a valid UUID reference number was generated.
        //PostgreSQL now returns a numeric transaction ID instead of a UUID.
        assertEquals(100000L, transactionId);

        //Verify that the correct deposit information was supplied.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setBigDecimal(2, new BigDecimal("50.00"));
        verify(preparedStatement).setString(3, "Cash deposit");
        verify(preparedStatement).setBigDecimal(4, new BigDecimal("150.00"));
        verify(preparedStatement).executeQuery();
        verify(resultSet).close();

        //Verify that the DAO closes its prepared statement.
        verify(preparedStatement).close();

        //Verify that the caller retains control of the database transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();

        //Verify that no separate database connection was requested.
        //TransactionDAO uses only the supplied connection.
    }


    /**
     * Tests that a successful deposit audit is saved using the existing
     * database connection without committing or closing it.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertDepositAuditUsesExistingConnection() throws SQLException {
        //Create mocked database objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        //Simulate PostgreSQL successfully inserting one audit record.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        //Create the DAO and save the audit.
        TransactionDAO transactionDAO = new TransactionDAO();
        transactionDAO.insertDepositAudit(connection, 1L, 100001L);

        //Verify that the correct IDs were sent to PostgreSQL.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 100001L);
        verify(preparedStatement).executeUpdate();

        //Verify that the DAO does not manage the caller's transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();

        //Verify that the prepared statement is closed.
        verify(preparedStatement).close();
    }


    /**
     * Tests that a successful withdrawal transaction is recorded
     * without committing or closing the existing database connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertWithdrawalTransactionUsesExistingConnection() throws SQLException {
        //Create mocked database objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Simulate PostgreSQL returning the generated transaction ID.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(200001L);

        //Record a $100.00 withdrawal with a remaining balance of $3400.00.
        TransactionDAO transactionDAO = new TransactionDAO();
        long transactionId = transactionDAO.insertWithdrawalTransaction(
                connection, 1L, new BigDecimal("100.00"), new BigDecimal("3400.00"));

        //Verify the transaction ID returned by PostgreSQL.
        assertEquals(200001L, transactionId);

        //Verify that the transaction information was supplied.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setBigDecimal(2, new BigDecimal("100.00"));
        verify(preparedStatement).setBigDecimal(4, new BigDecimal("3400.00"));
        verify(preparedStatement).executeQuery();

        //Verify that JDBC resources were closed.
        verify(resultSet).close();
        verify(preparedStatement).close();

        //Verify that the DAO does not control the transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    /**
     * Tests that a successful withdrawal audit is saved
     * using the existing database connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertWithdrawalAuditUsesExistingConnection() throws SQLException {
        //Create mocked database objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        //Simulate PostgreSQL inserting one audit record.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        //Save the withdrawal audit.
        TransactionDAO transactionDAO = new TransactionDAO();
        transactionDAO.insertWithdrawalAudit(connection, 1L, 200001L);

        //Verify the customer and transaction IDs.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 200001L);
        verify(preparedStatement).executeUpdate();

        //Verify that the statement was closed.
        verify(preparedStatement).close();

        //Verify that the caller controls the database transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }


    /**
     * Tests that a transfer transaction records both account IDs
     * and both updated balances.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void insertTransferTransactionRecordsBothAccounts() throws SQLException {
        //Prepare the PostgreSQL-generated transaction ID.
        TransactionDAO transactionDAO = new TransactionDAO();
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(3L);

        long transactionId = transactionDAO.insertTransferTransaction(
                connection, 1L, 2L, new BigDecimal("200.00"),
                new BigDecimal("3200.00"), new BigDecimal("200.00"));

        assertEquals(3L, transactionId);
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 2L);
        verify(preparedStatement).setBigDecimal(3, new BigDecimal("200.00"));
        verify(preparedStatement).setString(4, "Transfer between own accounts");
        verify(preparedStatement).setBigDecimal(5, new BigDecimal("3200.00"));
        verify(preparedStatement).setBigDecimal(6, new BigDecimal("200.00"));
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that transfer transaction records cannot use the same
     * account as both the source and destination.
     */
    @Test
    void insertTransferTransactionRejectsMatchingAccountIds() {
        //A transfer must involve two different accounts.
        TransactionDAO transactionDAO = new TransactionDAO();
        Connection connection = mock(Connection.class);

        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertTransferTransaction(
                        connection, 1L, 1L, new BigDecimal("200.00"),
                        new BigDecimal("3200.00"), new BigDecimal("200.00")));

        verifyNoInteractions(connection);
    }

    /**
     * Tests that a successful transfer creates its audit record.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void insertTransferAuditRecordsSuccess() throws SQLException {
        //Prepare a successful audit insertion.
        TransactionDAO transactionDAO = new TransactionDAO();
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        transactionDAO.insertTransferAudit(connection, 1L, 3L);

        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 3L);
        verify(preparedStatement).executeUpdate();
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }


    /**
     * Tests that transaction history includes incoming deposits
     * and outgoing transfers with the correct balances.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getTransactionHistoryReturnsIncomingAndOutgoingTransactions() throws SQLException {
        //Prepare mocked database objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);

        OffsetDateTime transactionDate = OffsetDateTime.parse("2026-11-13T10:30:00Z");
        when(resultSet.getObject("created_at", OffsetDateTime.class))
                .thenReturn(transactionDate);

        when(resultSet.getLong("transaction_id")).thenReturn(3L, 1L);
        when(resultSet.getString("transaction_type")).thenReturn("TRANSFER", "DEPOSIT");
        when(resultSet.getBigDecimal("amount"))
                .thenReturn(new BigDecimal("200.00"), new BigDecimal("3500.00"));
        when(resultSet.getString("direction")).thenReturn("OUT", "IN");
        when(resultSet.getString("status")).thenReturn("SUCCESS");
        when(resultSet.getString("description"))
                .thenReturn("Transfer between own accounts", "Cash deposit");
        when(resultSet.getBigDecimal("balance_after"))
                .thenReturn(new BigDecimal("3200.00"), new BigDecimal("3500.00"));

        TransactionDAO transactionDAO = new TransactionDAO();

        //Retrieve the selected account's history.
        List<TransactionHistoryEntry> history =
                transactionDAO.getTransactionHistory(connection, 1L, 1L);

        assertEquals(2, history.size());

        //Verify the outgoing transfer.
        assertEquals(3L, history.get(0).transactionId());
        assertEquals("TRANSFER", history.get(0).transactionType());
        assertEquals("OUT", history.get(0).direction());
        assertEquals(new BigDecimal("3200.00"), history.get(0).balanceAfter());

        //Verify the incoming deposit.
        assertEquals(1L, history.get(1).transactionId());
        assertEquals("DEPOSIT", history.get(1).transactionType());
        assertEquals("IN", history.get(1).direction());
        assertEquals(new BigDecimal("3500.00"), history.get(1).balanceAfter());

        //Verify that the customer and account IDs were supplied.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).setLong(4, 1L);

        //Verify that this read-only operation does not manage the connection.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
        verify(resultSet).close();
        verify(preparedStatement).close();
    }

    /**
     * Tests that transaction history returns an empty list when
     * the selected account has no transactions.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getTransactionHistoryReturnsEmptyListWhenNoTransactionsExist() throws SQLException {
        //Simulate a query returning no rows.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        TransactionDAO transactionDAO = new TransactionDAO();

        List<TransactionHistoryEntry> history =
                transactionDAO.getTransactionHistory(connection, 1L, 2L);

        assertTrue(history.isEmpty());
        verify(preparedStatement).setLong(3, 2L);
        verify(preparedStatement).setLong(4, 1L);
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that invalid customer or account IDs are rejected
     * before executing a history query.
     */
    @Test
    void getTransactionHistoryRejectsInvalidIds() {
        //Invalid IDs must never reach the database.
        Connection connection = mock(Connection.class);
        TransactionDAO transactionDAO = new TransactionDAO();

        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.getTransactionHistory(connection, 0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.getTransactionHistory(connection, 1L, -1L));

        verifyNoInteractions(connection);
    }

    /**
     * Tests that the history SQL enforces customer ownership,
     * includes both sides of transfers, and sorts newest first.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getTransactionHistoryUsesAccountOwnershipAndChronologicalOrdering() throws SQLException {
        //Capture the SQL sent to PostgreSQL.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        TransactionDAO transactionDAO = new TransactionDAO();
        transactionDAO.getTransactionHistory(connection, 1L, 2L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();

        //Require customer ownership and both transfer directions.
        assertTrue(sql.contains("a.customer_id = ?"));
        assertTrue(sql.contains("t.source_account_id = a.account_id"));
        assertTrue(sql.contains("t.destination_account_id = a.account_id"));
        assertTrue(sql.contains("ORDER BY t.created_at DESC"));
    }


    /**
     * Tests that a loan repayment records both accounts and their updated balances.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertLoanRepaymentTransactionRecordsBothAccounts() throws SQLException {
        //Create mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(300001L);

        //Record a $100 repayment from Checking to a Loan Account.
        TransactionDAO transactionDAO = new TransactionDAO();
        long transactionId = transactionDAO.insertLoanRepaymentTransaction(
                connection,
                1L,
                4L,
                new BigDecimal("100.00"),
                new BigDecimal("3100.00"),
                new BigDecimal("450.00"));

        //Verify the generated transaction ID.
        assertEquals(300001L, transactionId);

        //Verify both account IDs, the payment amount, and remaining balances.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 4L);
        verify(preparedStatement).setBigDecimal(3, new BigDecimal("100.00"));
        verify(preparedStatement).setString(4, "Loan repayment");
        verify(preparedStatement).setBigDecimal(5, new BigDecimal("3100.00"));
        verify(preparedStatement).setBigDecimal(6, new BigDecimal("450.00"));

        //Verify that the SQL records the correct transaction type.
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("'LOAN_REPAYMENT'"));
        assertTrue(sqlCaptor.getValue().contains("'SUCCESS'"));

        //The DAO must not commit or close the caller's connection.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    /**
     * Tests that a fully repaid loan can be recorded with zero remaining debt.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertLoanRepaymentTransactionAcceptsZeroLoanBalance() throws SQLException {
        //Create mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(300002L);

        //Record the final $550 payment.
        TransactionDAO transactionDAO = new TransactionDAO();
        long transactionId = transactionDAO.insertLoanRepaymentTransaction(
                connection,
                1L,
                4L,
                new BigDecimal("550.00"),
                new BigDecimal("2650.00"),
                new BigDecimal("0.00"));

        //Verify that zero outstanding debt is accepted.
        assertEquals(300002L, transactionId);
        verify(preparedStatement).setBigDecimal(6, new BigDecimal("0.00"));
        verify(connection, never()).commit();
    }

    /**
     * Tests that invalid loan repayment details are rejected before database access.
     */
    @Test
    void insertLoanRepaymentTransactionRejectsInvalidValues() {
        //Create a DAO and mocked connection.
        TransactionDAO transactionDAO = new TransactionDAO();
        Connection connection = mock(Connection.class);

        //Reject identical source and destination accounts.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertLoanRepaymentTransaction(
                        connection, 1L, 1L,
                        new BigDecimal("100.00"),
                        new BigDecimal("900.00"),
                        new BigDecimal("450.00")));

        //Reject negative repayment amounts.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertLoanRepaymentTransaction(
                        connection, 1L, 4L,
                        new BigDecimal("-100.00"),
                        new BigDecimal("900.00"),
                        new BigDecimal("450.00")));

        //Reject negative remaining loan debt.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertLoanRepaymentTransaction(
                        connection, 1L, 4L,
                        new BigDecimal("100.00"),
                        new BigDecimal("900.00"),
                        new BigDecimal("-1.00")));

        //Invalid inputs must never reach PostgreSQL.
        verifyNoInteractions(connection);
    }

    /**
     * Tests that a successful loan repayment audit is saved without committing.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertLoanRepaymentAuditRecordsSuccess() throws SQLException {
        //Create mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        //Record the audit for a successful loan repayment.
        TransactionDAO transactionDAO = new TransactionDAO();
        transactionDAO.insertLoanRepaymentAudit(connection, 1L, 300001L);

        //Verify that the customer and transaction IDs were supplied.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 300001L);

        //Verify the audit event and outcome.
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("'LOAN_REPAYMENT'"));
        assertTrue(sqlCaptor.getValue().contains("'SUCCESS'"));

        //Verify that the audit was inserted exactly once.
        verify(preparedStatement).executeUpdate();

        //The service layer remains responsible for committing.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }


    /**
     * Tests that an Investment Account contribution transaction is saved
     * with its source, destination, amount, and resulting balances.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentContributionTransactionReturnsId() throws SQLException {
        //Prepare mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        TransactionDAO transactionDAO = new TransactionDAO();

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(6L);

        //Record a contribution from Checking Account 1 to Investment Account 3.
        BigDecimal amount = new BigDecimal("500.00");
        BigDecimal sourceBalanceAfter = new BigDecimal("2150.00");
        BigDecimal investmentBalanceAfter = new BigDecimal("500.00");

        long transactionId = transactionDAO.insertInvestmentContributionTransaction(
                connection, 1L, 3L, amount,
                sourceBalanceAfter, investmentBalanceAfter);

        assertEquals(6L, transactionId);

        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 3L);
        verify(preparedStatement).setBigDecimal(3, amount);
        verify(preparedStatement).setString(4, "Investment contribution");
        verify(preparedStatement).setBigDecimal(5, sourceBalanceAfter);
        verify(preparedStatement).setBigDecimal(6, investmentBalanceAfter);

        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that a missing or invalid generated transaction ID
     * causes an Investment Account contribution record to fail.
     *
     * @throws SQLException if the mocked database insert fails
     */
    @Test
    void insertInvestmentContributionTransactionRejectsInvalidResult() throws SQLException {
        //Prepare mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        TransactionDAO transactionDAO = new TransactionDAO();

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Reject a missing transaction record.
        when(resultSet.next()).thenReturn(false);

        assertThrows(SQLException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 1L, 3L, new BigDecimal("500.00"),
                        new BigDecimal("2150.00"), new BigDecimal("500.00")));

        //Reject a generated transaction ID of zero.
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(0L);

        assertThrows(SQLException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 1L, 3L, new BigDecimal("500.00"),
                        new BigDecimal("2150.00"), new BigDecimal("500.00")));

        verify(connection, never()).commit();
    }

    /**
     * Tests that an Investment Account contribution audit event
     * is inserted using the provided customer and transaction IDs.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentContributionAuditSavesRecord() throws SQLException {
        //Prepare mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        TransactionDAO transactionDAO = new TransactionDAO();

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        //Record a successful audit for transaction 6.
        transactionDAO.insertInvestmentContributionAudit(connection, 1L, 6L);

        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 6L);
        verify(preparedStatement).executeUpdate();

        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that invalid Investment Account contribution inputs
     * are rejected before preparing SQL statements.
     */
    @Test
    void investmentContributionRecordsRejectInvalidInputs() {
        //Create mocked database dependencies.
        Connection connection = mock(Connection.class);
        TransactionDAO transactionDAO = new TransactionDAO();

        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal balance = new BigDecimal("500.00");

        //Reject invalid account IDs and identical source/destination accounts.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 0L, 3L, amount, balance, balance));

        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 1L, 1L, amount, balance, balance));

        //Reject invalid amounts.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 1L, 3L, BigDecimal.ZERO, balance, balance));

        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 1L, 3L, new BigDecimal("10.123"), balance, balance));

        //Reject invalid resulting balances.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionTransaction(
                        connection, 1L, 3L, amount,
                        new BigDecimal("-1.00"), balance));

        //Reject invalid audit identifiers.
        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionAudit(
                        connection, 0L, 6L));

        assertThrows(IllegalArgumentException.class,
                () -> transactionDAO.insertInvestmentContributionAudit(
                        connection, 1L, 0L));

        verifyNoInteractions(connection);
    }




    /**
     * Tests that a maturity payout records the closed source and credited destination.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentMaturityTransactionRecordsPayout() throws SQLException {
        //Prepare a successful PostgreSQL transaction insert.
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(7L);

        TransactionDAO dao = new TransactionDAO();
        long id = dao.insertInvestmentMaturityTransaction(connection, 3L, 1L,
                new BigDecimal("680.00"), new BigDecimal("2680.00"));

        assertEquals(7L, id);
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 1L);
        verify(statement).setBigDecimal(3, new BigDecimal("680.00"));
        verify(statement).setString(4, "Investment maturity payout");
        verify(statement).setBigDecimal(5, new BigDecimal("0.00"));
        verify(statement).setBigDecimal(6, new BigDecimal("2680.00"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("'INVESTMENT_MATURITY_PAYOUT'"));
        assertTrue(sql.getValue().contains("'SUCCESS'"));
        verify(resultSet).close();
        verify(statement).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    /**
     * Tests that a maturity transaction requires a valid generated database ID.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentMaturityTransactionRejectsInvalidResult() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        TransactionDAO dao = new TransactionDAO();

        //Reject an insert that returns no transaction row.
        when(resultSet.next()).thenReturn(false);
        assertThrows(SQLException.class, () -> dao.insertInvestmentMaturityTransaction(
                connection, 3L, 1L, new BigDecimal("680.00"), new BigDecimal("2680.00")));

        //Reject an insert that returns an invalid generated ID.
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("transaction_id")).thenReturn(0L);
        assertThrows(SQLException.class, () -> dao.insertInvestmentMaturityTransaction(
                connection, 3L, 1L, new BigDecimal("680.00"), new BigDecimal("2680.00")));
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests successful maturity audit insertion and rejection of an incomplete insert.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentMaturityAuditRecordsSuccess() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1, 0);
        TransactionDAO dao = new TransactionDAO();

        dao.insertInvestmentMaturityAudit(connection, 1L, 7L);
        verify(statement).setLong(1, 1L);
        verify(statement).setLong(2, 7L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1)).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("'INVESTMENT_MATURITY_PAYOUT'"));
        assertTrue(sql.getValue().contains("'SUCCESS'"));

        //A missing audit row must fail so the service can roll back.
        assertThrows(SQLException.class, () -> dao.insertInvestmentMaturityAudit(connection, 1L, 7L));
        verify(statement, times(2)).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    /**
     * Tests that invalid payout values and audit identifiers never reach JDBC.
     */
    @Test
    void investmentMaturityRecordsRejectInvalidInputs() {
        Connection connection = mock(Connection.class);
        TransactionDAO dao = new TransactionDAO();
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal balance = new BigDecimal("200.00");

        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityTransaction(connection, 0L, 1L, amount, balance));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityTransaction(connection, 3L, 3L, amount, balance));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityTransaction(connection, 3L, 1L, BigDecimal.ZERO, balance));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityTransaction(connection, 3L, 1L,
                        new BigDecimal("10.123"), balance));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityTransaction(connection, 3L, 1L,
                        new BigDecimal("100000000000000000.00"), balance));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityTransaction(connection, 3L, 1L, amount,
                        new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityAudit(connection, 0L, 7L));
        assertThrows(IllegalArgumentException.class, () ->
                dao.insertInvestmentMaturityAudit(connection, 1L, 0L));
        verifyNoInteractions(connection);
    }

}
