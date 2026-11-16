package com.revature.zelle;

import com.revature.util.ConnectionProvider;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Tests simulated Zelle request persistence without connecting to PostgreSQL. */
public class ZellePaymentRequestDAOTest {

    @Test
    void createRequestReturnsGeneratedId() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet status = mock(ResultSet.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock, insert);
        when(lock.executeQuery()).thenReturn(status);
        when(status.next()).thenReturn(true, true);
        when(status.getString("status")).thenReturn("ACTIVE");
        when(insert.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true);
        when(results.getLong("request_id")).thenReturn(42L);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertEquals(42L, dao.createRequest(1L, 2L, new BigDecimal("12.50"), "Lunch"));
        verify(lock).setLong(1, 1L);
        verify(lock).setLong(1, 2L);
        verify(insert).setLong(1, 1L);
        verify(insert).setLong(2, 2L);
        verify(insert).setBigDecimal(3, new BigDecimal("12.50"));
        verify(insert).setString(4, "Lunch");
        verify(connection).commit();
    }

    @Test
    void createRequestRejectsClosedPayerWithoutInsert() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet status = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(status);
        when(status.next()).thenReturn(true, true);
        when(status.getString("status")).thenReturn("ACTIVE", "CLOSED");
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertThrows(SQLException.class,
                () -> dao.createRequest(1L, 2L, new BigDecimal("10.00"), null));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection, times(1)).prepareStatement(anyString());
    }

    @Test
    void createRequestRejectsMissingRequesterWithoutInsert() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet status = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock);
        when(lock.executeQuery()).thenReturn(status);
        when(status.next()).thenReturn(false);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertThrows(SQLException.class,
                () -> dao.createRequest(1L, 2L, new BigDecimal("10.00"), null));
        verify(connection).rollback();
        verify(lock, times(1)).executeQuery();
    }

    @Test
    void createRequestLocksCustomersInAscendingIdOrder() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet status = mock(ResultSet.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock, insert);
        when(lock.executeQuery()).thenReturn(status);
        when(status.next()).thenReturn(true, true);
        when(status.getString("status")).thenReturn("ACTIVE");
        when(insert.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true);
        when(results.getLong("request_id")).thenReturn(19L);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertEquals(19L, dao.createRequest(9L, 3L, new BigDecimal("5.00"), null));
        org.mockito.InOrder order = inOrder(lock);
        order.verify(lock).setLong(1, 3L);
        order.verify(lock).executeQuery();
        order.verify(lock).setLong(1, 9L);
        order.verify(lock).executeQuery();
    }

    @Test
    void createRequestRollsBackWhenInsertFails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet status = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(lock, insert);
        when(lock.executeQuery()).thenReturn(status);
        when(status.next()).thenReturn(true, true);
        when(status.getString("status")).thenReturn("ACTIVE");
        when(insert.executeQuery()).thenThrow(new SQLException("Insert failed"));
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertThrows(SQLException.class,
                () -> dao.createRequest(1L, 2L, new BigDecimal("10.00"), null));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void createRequestRejectsInvalidInputs() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(provider);
        assertThrows(IllegalArgumentException.class,
                () -> dao.createRequest(1L, 1L, new BigDecimal("1.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.createRequest(1L, 2L, new BigDecimal("0.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.createRequest(1L, 2L, new BigDecimal("1.001"), null));
        verifyNoInteractions(provider);
    }

    @Test
    void createRequestPropagatesDatabaseFailure() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        when(provider.getConnection()).thenThrow(new SQLException("Database unavailable"));
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(provider);
        assertThrows(SQLException.class,
                () -> dao.createRequest(1L, 2L, new BigDecimal("1.00"), null));
    }

    @Test
    void findPendingRequestsReturnsEmptyList() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);
        List<ZellePaymentRequest> requests = dao.findPendingRequestsForPayer(2L);
        assertTrue(requests.isEmpty());
        verify(statement).setLong(1, 2L);
    }

    @Test
    void findPendingRequestsMapsRows() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true, false);
        when(results.getLong("request_id")).thenReturn(17L);
        when(results.getLong("requester_customer_id")).thenReturn(1L);
        when(results.getLong("payer_customer_id")).thenReturn(2L);
        when(results.getBigDecimal("amount")).thenReturn(new BigDecimal("25.00"));
        when(results.getString("status")).thenReturn("PENDING");
        when(results.getString("requester_username")).thenReturn("Jane_Doe");
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);
        List<ZellePaymentRequest> requests = dao.findPendingRequestsForPayer(2L);
        assertEquals(1, requests.size());
        assertEquals(17L, requests.get(0).requestId());
        assertEquals(new BigDecimal("25.00"), requests.get(0).amount());
        assertEquals("PENDING", requests.get(0).status());
        assertEquals("Jane_Doe", requests.get(0).requesterUsername());
    }

    @Test
    void findRequestForCustomerReturnsNullWhenMissing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);
        assertNull(dao.findRequestForCustomer(5L, 2L));
        verify(statement).setLong(1, 5L);
        verify(statement).setLong(2, 2L);
        verify(statement).setLong(3, 2L);
    }

    /** Confirms that a pending request can be rejected by its payer. */
    @Test
    void rejectRequestUpdatesOnePendingRequest() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertDoesNotThrow(() -> dao.rejectRequest(17L, 2L));
        verify(statement).setLong(1, 17L);
        verify(statement).setLong(2, 2L);
        verify(statement).executeUpdate();
    }

    /** Prevents rejection of missing, already resolved, or other customers' requests. */
    @Test
    void rejectRequestFailsWhenNoPendingOwnedRowExists() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(() -> connection);

        assertThrows(SQLException.class, () -> dao.rejectRequest(17L, 2L));
    }

    /** Invalid IDs should not open a database connection. */
    @Test
    void rejectRequestRejectsInvalidIds() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(provider);

        assertThrows(IllegalArgumentException.class, () -> dao.rejectRequest(0L, 2L));
        assertThrows(IllegalArgumentException.class, () -> dao.rejectRequest(17L, 0L));
        verifyNoInteractions(provider);
    }

    /** Database failures must reach the caller. */
    @Test
    void rejectRequestPropagatesDatabaseFailure() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        when(provider.getConnection()).thenThrow(new SQLException("Database unavailable"));
        ZellePaymentRequestDAO dao = new ZellePaymentRequestDAO(provider);

        assertThrows(SQLException.class, () -> dao.rejectRequest(17L, 2L));
    }
}
