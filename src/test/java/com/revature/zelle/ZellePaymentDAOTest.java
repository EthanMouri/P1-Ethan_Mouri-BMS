package com.revature.zelle;

import com.revature.util.ConnectionProvider;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Tests Zelle payment safety without changing the real database. */
public class ZellePaymentDAOTest {

    /** Checks invalid IDs before opening a database connection. */
    @Test
    void rejectsInvalidIds() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        ZellePaymentDAO dao = new ZellePaymentDAO(provider);
        assertThrows(IllegalArgumentException.class, () -> dao.approveRequest(0, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> dao.approveRequest(1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> dao.approveRequest(1, 2, 0));
        verifyNoInteractions(provider);
    }

    /** An already approved or rejected request cannot be paid again. */
    @Test
    void rejectsMissingOrResolvedRequestAndRollsBack() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        ZellePaymentDAO dao = new ZellePaymentDAO(() -> connection);

        assertThrows(SQLException.class, () -> dao.approveRequest(10, 2, 5));
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).setAutoCommit(true);
    }

    /** A database connection failure must reach the caller. */
    @Test
    void propagatesConnectionFailure() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        when(provider.getConnection()).thenThrow(new SQLException("Offline"));
        assertThrows(SQLException.class,
                () -> new ZellePaymentDAO(provider).approveRequest(1, 2, 3));
    }

    /** An insufficient balance must stop the payment before any balance update. */
    @Test
    void insufficientFundsRollBack() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement request = mock(PreparedStatement.class);
        PreparedStatement destination = mock(PreparedStatement.class);
        PreparedStatement customers = mock(PreparedStatement.class);
        PreparedStatement accounts = mock(PreparedStatement.class);
        ResultSet requestRows = mock(ResultSet.class);
        ResultSet destinationRows = mock(ResultSet.class);
        ResultSet accountRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM zelle_payment_requests")) return request;
            if (sql.contains("FROM customers")) return customers;
            if (sql.contains("ORDER BY account_id LIMIT 1")) return destination;
            if (sql.contains("ORDER BY account_id FOR UPDATE")) return accounts;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(request.executeQuery()).thenReturn(requestRows);
        when(destination.executeQuery()).thenReturn(destinationRows);
        ResultSet customerRows = mock(ResultSet.class);
        when(customers.executeQuery()).thenReturn(customerRows);
        when(customerRows.next()).thenReturn(true);
        when(customerRows.getString("status")).thenReturn("ACTIVE");
        when(accounts.executeQuery()).thenReturn(accountRows);
        when(requestRows.next()).thenReturn(true, true);
        when(requestRows.getLong("requester_customer_id")).thenReturn(1L);
        when(requestRows.getBigDecimal("amount")).thenReturn(new BigDecimal("50.00"));
        when(destinationRows.next()).thenReturn(true);
        when(destinationRows.getLong("account_id")).thenReturn(3L);
        when(accountRows.next()).thenReturn(true, true, false);
        when(accountRows.getLong("account_id")).thenReturn(3L, 5L);
        when(accountRows.getLong("customer_id")).thenReturn(1L, 2L);
        when(accountRows.getString("account_type")).thenReturn("CHECKING");
        when(accountRows.getString("status")).thenReturn("ACTIVE");
        when(accountRows.getBigDecimal("balance"))
                .thenReturn(new BigDecimal("100.00"), new BigDecimal("10.00"));

        SQLException error = assertThrows(SQLException.class,
                () -> new ZellePaymentDAO(() -> connection).approveRequest(10, 2, 5));
        assertTrue(error.getMessage().contains("Insufficient funds"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    /** Rejects an account owned by somebody other than the payer. */
    @Test
    void rejectsWrongFundingAccountOwner() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement request = mock(PreparedStatement.class);
        PreparedStatement destination = mock(PreparedStatement.class);
        PreparedStatement customers = mock(PreparedStatement.class);
        PreparedStatement accounts = mock(PreparedStatement.class);
        ResultSet requestRows = mock(ResultSet.class);
        ResultSet destinationRows = mock(ResultSet.class);
        ResultSet accountRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM zelle_payment_requests")) return request;
            if (sql.contains("FROM customers")) return customers;
            if (sql.contains("ORDER BY account_id LIMIT 1")) return destination;
            if (sql.contains("ORDER BY account_id FOR UPDATE")) return accounts;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(request.executeQuery()).thenReturn(requestRows);
        when(destination.executeQuery()).thenReturn(destinationRows);
        ResultSet customerRows = mock(ResultSet.class);
        when(customers.executeQuery()).thenReturn(customerRows);
        when(customerRows.next()).thenReturn(true);
        when(customerRows.getString("status")).thenReturn("ACTIVE");
        when(accounts.executeQuery()).thenReturn(accountRows);
        when(requestRows.next()).thenReturn(true, true);
        when(requestRows.getLong("requester_customer_id")).thenReturn(1L);
        when(requestRows.getBigDecimal("amount")).thenReturn(new BigDecimal("5.00"));
        when(destinationRows.next()).thenReturn(true);
        when(destinationRows.getLong("account_id")).thenReturn(3L);
        when(accountRows.next()).thenReturn(true, true, false);
        when(accountRows.getLong("account_id")).thenReturn(3L, 5L);
        when(accountRows.getLong("customer_id")).thenReturn(1L, 99L);
        when(accountRows.getString("account_type")).thenReturn("CHECKING");
        when(accountRows.getString("status")).thenReturn("ACTIVE");
        when(accountRows.getBigDecimal("balance")).thenReturn(new BigDecimal("20.00"));

        assertThrows(SQLException.class,
                () -> new ZellePaymentDAO(() -> connection).approveRequest(10, 2, 5));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    /** A successful approval commits the balances, transaction, audit, and request. */
    @Test
    void successfulApprovalCommitsOnce() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement request = mock(PreparedStatement.class);
        PreparedStatement destination = mock(PreparedStatement.class);
        PreparedStatement customers = mock(PreparedStatement.class);
        PreparedStatement accounts = mock(PreparedStatement.class);
        PreparedStatement balance = mock(PreparedStatement.class);
        PreparedStatement transaction = mock(PreparedStatement.class);
        PreparedStatement audit = mock(PreparedStatement.class);
        PreparedStatement approval = mock(PreparedStatement.class);
        ResultSet requestRows = mock(ResultSet.class);
        ResultSet destinationRows = mock(ResultSet.class);
        ResultSet accountRows = mock(ResultSet.class);
        ResultSet transactionRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM zelle_payment_requests")) return request;
            if (sql.contains("FROM customers")) return customers;
            if (sql.contains("ORDER BY account_id LIMIT 1")) return destination;
            if (sql.contains("ORDER BY account_id FOR UPDATE")) return accounts;
            if (sql.contains("UPDATE accounts SET balance")) return balance;
            if (sql.contains("INSERT INTO transactions")) return transaction;
            if (sql.contains("INSERT INTO transaction_audit")) return audit;
            if (sql.contains("UPDATE zelle_payment_requests")) return approval;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(request.executeQuery()).thenReturn(requestRows);
        when(destination.executeQuery()).thenReturn(destinationRows);
        ResultSet customerRows = mock(ResultSet.class);
        when(customers.executeQuery()).thenReturn(customerRows);
        when(customerRows.next()).thenReturn(true);
        when(customerRows.getString("status")).thenReturn("ACTIVE");
        when(accounts.executeQuery()).thenReturn(accountRows);
        when(transaction.executeQuery()).thenReturn(transactionRows);
        when(requestRows.next()).thenReturn(true, true);
        when(requestRows.getLong("requester_customer_id")).thenReturn(1L);
        when(requestRows.getBigDecimal("amount")).thenReturn(new BigDecimal("5.00"));
        when(destinationRows.next()).thenReturn(true);
        when(destinationRows.getLong("account_id")).thenReturn(3L);
        when(accountRows.next()).thenReturn(true, true, false);
        when(accountRows.getLong("account_id")).thenReturn(3L, 5L);
        when(accountRows.getLong("customer_id")).thenReturn(1L, 2L);
        when(accountRows.getString("account_type")).thenReturn("CHECKING");
        when(accountRows.getString("status")).thenReturn("ACTIVE");
        when(accountRows.getBigDecimal("balance"))
                .thenReturn(new BigDecimal("100.00"), new BigDecimal("10.00"));
        when(balance.executeUpdate()).thenReturn(1);
        when(audit.executeUpdate()).thenReturn(1);
        when(approval.executeUpdate()).thenReturn(1);
        when(transactionRows.next()).thenReturn(true);
        when(transactionRows.getLong("transaction_id")).thenReturn(77L);

        assertEquals(77L, new ZellePaymentDAO(() -> connection).approveRequest(10, 2, 5));
        verify(balance, times(2)).executeUpdate();
        verify(transaction).setBigDecimal(3, new BigDecimal("5.00"));
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection).setAutoCommit(true);
    }

    /** A closed payer or requester stops approval before any balance changes. */
    @Test
    void closedCustomerPreventsApproval() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement request = mock(PreparedStatement.class);
        PreparedStatement customers = mock(PreparedStatement.class);
        ResultSet requestRows = mock(ResultSet.class);
        ResultSet customerRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM zelle_payment_requests")) return request;
            if (sql.contains("FROM customers")) return customers;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(request.executeQuery()).thenReturn(requestRows);
        when(requestRows.next()).thenReturn(true);
        when(requestRows.getLong("requester_customer_id")).thenReturn(1L);
        when(requestRows.getBigDecimal("amount")).thenReturn(new BigDecimal("5.00"));
        when(customers.executeQuery()).thenReturn(customerRows);
        when(customerRows.next()).thenReturn(true);
        when(customerRows.getString("status")).thenReturn("CLOSED");

        assertThrows(SQLException.class,
                () -> new ZellePaymentDAO(() -> connection).approveRequest(10, 2, 5));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(customers).setLong(1, 1L);
    }

    /** Customer rows are locked in ascending order even when payer ID is lower. */
    @Test
    void customerLockOrderIsAscending() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement request = mock(PreparedStatement.class);
        PreparedStatement customers = mock(PreparedStatement.class);
        ResultSet requestRows = mock(ResultSet.class);
        ResultSet customerRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM zelle_payment_requests")) return request;
            if (sql.contains("FROM customers")) return customers;
            throw new SQLException("Stop after customer locks");
        });
        when(request.executeQuery()).thenReturn(requestRows);
        when(requestRows.next()).thenReturn(true, false);
        when(requestRows.getLong("requester_customer_id")).thenReturn(9L);
        when(requestRows.getBigDecimal("amount")).thenReturn(new BigDecimal("5.00"));
        when(customers.executeQuery()).thenReturn(customerRows);
        when(customerRows.next()).thenReturn(true);
        when(customerRows.getString("status")).thenReturn("ACTIVE");

        assertThrows(SQLException.class,
                () -> new ZellePaymentDAO(() -> connection).approveRequest(10, 2, 5));
        org.mockito.InOrder order = inOrder(customers);
        order.verify(customers).setLong(1, 2L);
        order.verify(customers).setLong(1, 9L);
        verify(connection).rollback();
    }

    /** If the request resolves while waiting for locks, approval cannot pay it. */
    @Test
    void requestResolvedBeforeLockPreventsPayment() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement request = mock(PreparedStatement.class);
        PreparedStatement customers = mock(PreparedStatement.class);
        ResultSet initialRows = mock(ResultSet.class);
        ResultSet lockedRows = mock(ResultSet.class);
        ResultSet customerRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM zelle_payment_requests")) return request;
            if (sql.contains("FROM customers")) return customers;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(request.executeQuery()).thenReturn(initialRows, lockedRows);
        when(initialRows.next()).thenReturn(true);
        when(initialRows.getLong("requester_customer_id")).thenReturn(1L);
        when(initialRows.getBigDecimal("amount")).thenReturn(new BigDecimal("5.00"));
        when(lockedRows.next()).thenReturn(false);
        when(customers.executeQuery()).thenReturn(customerRows);
        when(customerRows.next()).thenReturn(true);
        when(customerRows.getString("status")).thenReturn("ACTIVE");

        assertThrows(SQLException.class,
                () -> new ZellePaymentDAO(() -> connection).approveRequest(10, 2, 5));
        verify(request, times(2)).executeQuery();
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

}
