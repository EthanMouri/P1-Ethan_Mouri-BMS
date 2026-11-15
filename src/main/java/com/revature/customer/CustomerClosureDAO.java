package com.revature.customer;

import com.revature.util.ConnectionProvider;
import com.revature.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Closes a customer only when the database confirms there are no obligations.
 */
public class CustomerClosureDAO {
    private final ConnectionProvider connectionProvider;

    /**
     * Uses the application's PostgreSQL connection.
     */
    public CustomerClosureDAO() {
        this(DatabaseConnection::getConnection);
    }

    /**
     * Accepts a connection provider for isolated unit tests.
     *
     * @param connectionProvider provider of database connections
     */
    public CustomerClosureDAO(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Atomically closes an eligible customer. Returns false if closure is blocked.
     * The eligibility conditions are evaluated by PostgreSQL during the update,
     * rather than relying on an earlier read-only eligibility check.
     *
     * @param customerId ID of the customer to close
     * @return true if one active customer was closed
     * @throws SQLException if the transaction fails
     */
    public boolean closeEligibleCustomer(long customerId) throws SQLException {
        if (customerId <= 0) {
            throw new IllegalArgumentException("Customer ID must be positive.");
        }

        String sql = """
                UPDATE customers AS c
                SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP
                WHERE c.customer_id = ?
                  AND c.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1 FROM accounts AS a
                      WHERE a.customer_id = c.customer_id
                        AND (a.status <> 'CLOSED' OR a.balance <> 0)
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM zelle_payment_requests AS z
                      WHERE z.status = 'PENDING'
                        AND (z.requester_customer_id = c.customer_id
                             OR z.payer_customer_id = c.customer_id)
                  )
                """;

        //Use one database transaction for the guarded status update.
        try (Connection connection = connectionProvider.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                //Lock the customer row before checking related obligations.
                //Concurrent operations that also lock this row must wait.
                String lockSql = "SELECT status FROM customers WHERE customer_id = ? FOR UPDATE";
                boolean active;
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setLong(1, customerId);
                    try (ResultSet result = lock.executeQuery()) {
                        active = result.next() && "ACTIVE".equals(result.getString("status"));
                    }
                }
                if (!active) {
                    connection.commit();
                    return false;
                }
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, customerId);
                    updated = statement.executeUpdate();
                }
                if (updated > 1) {
                    throw new SQLException("Unexpected number of customer records updated.");
                }
                connection.commit();
                return updated == 1;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }
}
