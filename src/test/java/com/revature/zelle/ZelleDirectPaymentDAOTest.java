package com.revature.zelle;

import com.revature.util.ConnectionProvider;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Tests direct Zelle payment safeguards without using the live database. */
public class ZelleDirectPaymentDAOTest {

    /** Invalid IDs and amounts are rejected before opening a connection. */
    @Test
    void rejectsInvalidInputs() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        ZelleDirectPaymentDAO dao = new ZelleDirectPaymentDAO(provider);
        assertThrows(IllegalArgumentException.class,
                () -> dao.sendMoney(1, 2, 1, new BigDecimal("1.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.sendMoney(1, 0, 2, new BigDecimal("1.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.sendMoney(1, 3, 2, new BigDecimal("1.001"), null));
        verifyNoInteractions(provider);
    }

    /** If the recipient has no eligible account, the transaction rolls back. */
    @Test
    void missingDestinationRollsBack() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        ZelleDirectPaymentDAO dao = new ZelleDirectPaymentDAO(() -> connection);
        assertThrows(SQLException.class,
                () -> dao.sendMoney(1, 3, 2, new BigDecimal("5.00"), null));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection).setAutoCommit(true);
    }

    /** Database connection failures must be reported to the caller. */
    @Test
    void propagatesConnectionFailure() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        when(provider.getConnection()).thenThrow(new SQLException("Offline"));
        assertThrows(SQLException.class,
                () -> new ZelleDirectPaymentDAO(provider)
                        .sendMoney(1, 3, 2, new BigDecimal("5.00"), null));
    }
    /** Closed customers cannot participate in direct Zelle payments. */
    @Test
    void closedCustomerRollsBackBeforeAccountLookup() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement customerStatement = mock(PreparedStatement.class);
        ResultSet firstCustomer = mock(ResultSet.class);
        ResultSet secondCustomer = mock(ResultSet.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(contains("FROM customers"))).thenReturn(customerStatement);
        when(customerStatement.executeQuery()).thenReturn(firstCustomer, secondCustomer);
        when(firstCustomer.next()).thenReturn(true);
        when(firstCustomer.getString("status")).thenReturn("ACTIVE");
        when(secondCustomer.next()).thenReturn(true);
        when(secondCustomer.getString("status")).thenReturn("CLOSED");

        ZelleDirectPaymentDAO dao = new ZelleDirectPaymentDAO(() -> connection);
        assertThrows(SQLException.class,
                () -> dao.sendMoney(9, 30, 2, new BigDecimal("5.00"), null));
        verify(customerStatement).setLong(1, 2);
        verify(customerStatement).setLong(1, 9);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection, never()).prepareStatement(contains("FROM accounts"));
    }

    /** A missing customer must fail without selecting or updating accounts. */
    @Test
    void missingCustomerRollsBackBeforeAccountLookup() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement customerStatement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(contains("FROM customers"))).thenReturn(customerStatement);
        when(customerStatement.executeQuery()).thenReturn(results);
        ZelleDirectPaymentDAO dao = new ZelleDirectPaymentDAO(() -> connection);
        assertThrows(SQLException.class,
                () -> dao.sendMoney(1, 30, 2, new BigDecimal("5.00"), null));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(contains("FROM accounts"));
    }

    /** Customer locks must be acquired in ascending order regardless of sender. */
    @Test
    void locksCustomersInAscendingIdOrder() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement customerStatement = mock(PreparedStatement.class);
        PreparedStatement destinationStatement = mock(PreparedStatement.class);
        ResultSet customerResults = mock(ResultSet.class);
        ResultSet destinationResults = mock(ResultSet.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(contains("FROM customers"))).thenReturn(customerStatement);
        when(connection.prepareStatement(contains("SELECT account_id FROM accounts")))
                .thenReturn(destinationStatement);
        when(customerStatement.executeQuery()).thenReturn(customerResults);
        when(customerResults.next()).thenReturn(true, true);
        when(customerResults.getString("status")).thenReturn("ACTIVE");
        when(destinationStatement.executeQuery()).thenReturn(destinationResults);
        ZelleDirectPaymentDAO dao = new ZelleDirectPaymentDAO(() -> connection);
        assertThrows(SQLException.class,
                () -> dao.sendMoney(10, 30, 3, new BigDecimal("5.00"), null));
        org.mockito.InOrder order = inOrder(customerStatement);
        order.verify(customerStatement).setLong(1, 3);
        order.verify(customerStatement).executeQuery();
        order.verify(customerStatement).setLong(1, 10);
        order.verify(customerStatement).executeQuery();
        verify(connection).rollback();
    }

}
