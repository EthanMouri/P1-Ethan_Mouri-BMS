package com.revature.customer;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests the guarded customer closure database transaction.
 */
class CustomerClosureDAOTest {
    @Test
    void rejectsInvalidCustomerId() {
        CustomerClosureDAO dao = new CustomerClosureDAO(() -> mock(Connection.class));
        assertThrows(IllegalArgumentException.class, () -> dao.closeEligibleCustomer(0));
    }

    @Test
    void rejectsNullConnectionProvider() {
        assertThrows(IllegalArgumentException.class, () -> new CustomerClosureDAO(null));
    }

    @Test
    void commitsSuccessfulClosure() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet lockResult = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FOR UPDATE") ? lock : statement);
        when(lock.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getString("status")).thenReturn("ACTIVE");
        when(statement.executeUpdate()).thenReturn(1);
        CustomerClosureDAO dao = new CustomerClosureDAO(() -> connection);
        assertTrue(dao.closeEligibleCustomer(3L));
        verify(statement).setLong(1, 3L);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void returnsFalseWhenNoCustomerCanBeClosed() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet lockResult = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FOR UPDATE") ? lock : statement);
        when(lock.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getString("status")).thenReturn("ACTIVE");
        CustomerClosureDAO dao = new CustomerClosureDAO(() -> connection);
        assertFalse(dao.closeEligibleCustomer(3L));
        verify(connection).commit();
    }

    @Test
    void rollsBackFailedUpdate() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet lockResult = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FOR UPDATE") ? lock : statement);
        when(lock.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getString("status")).thenReturn("ACTIVE");
        when(statement.executeUpdate()).thenThrow(new SQLException("Database failure"));
        CustomerClosureDAO dao = new CustomerClosureDAO(() -> connection);
        assertThrows(SQLException.class, () -> dao.closeEligibleCustomer(3L));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
    @Test
    void rejectsClosedCustomerWithoutUpdating() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString("status")).thenReturn("CLOSED");
        assertFalse(new CustomerClosureDAO(() -> connection).closeEligibleCustomer(4L));
        verify(lock).setLong(1, 4L);
        verify(connection).commit();
        verify(lock, never()).executeUpdate();
    }

    @Test
    void rejectsMissingCustomerWithoutUpdating() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        assertFalse(new CustomerClosureDAO(() -> connection).closeEligibleCustomer(99L));
        verify(connection).commit();
        verify(lock, never()).executeUpdate();
    }

    @Test
    void obtainsRowLockBeforeGuardedUpdate() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).contains("FOR UPDATE") ? lock : update);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString("status")).thenReturn("ACTIVE");
        when(update.executeUpdate()).thenReturn(1);
        assertTrue(new CustomerClosureDAO(() -> connection).closeEligibleCustomer(4L));
        var order = inOrder(connection, lock, update);
        order.verify(connection).setAutoCommit(false);
        order.verify(lock).executeQuery();
        order.verify(update).executeUpdate();
        order.verify(connection).commit();
    }
}
