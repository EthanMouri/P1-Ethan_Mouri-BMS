package com.revature.customer;

import com.revature.util.ConnectionProvider;
import com.revature.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads the database requirements for closing a customer relationship.
 * Does not change customer, account, or payment records.
 */
public class CustomerClosureEligibilityDAO {

    private final ConnectionProvider connectionProvider;

    /**
     * Creates a DAO using the application's PostgreSQL connection.
     */
    public CustomerClosureEligibilityDAO() {
        this(DatabaseConnection::getConnection);
    }

    /**
     * Creates a DAO with a supplied connection provider for tests.
     *
     * @param connectionProvider source of database connections
     */
    public CustomerClosureEligibilityDAO(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Counts a customer's accounts that have not been closed.
     *
     * @param customerId customer to inspect
     * @return number of accounts still open
     * @throws SQLException if the database query fails
     */
    public long countOpenAccounts(long customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM accounts WHERE customer_id = ? AND status <> 'CLOSED'";
        return count(sql, customerId);
    }

    /**
     * Counts accounts with money remaining, including closed accounts if inconsistent.
     *
     * @param customerId customer to inspect
     * @return number of accounts with a nonzero balance
     * @throws SQLException if the database query fails
     */
    public long countNonzeroBalances(long customerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM accounts WHERE customer_id = ? AND balance <> 0";
        return count(sql, customerId);
    }

    /**
     * Counts unresolved requests involving the customer as payer or requester.
     *
     * @param customerId customer to inspect
     * @return number of pending Zelle requests
     * @throws SQLException if the database query fails
     */
    public long countPendingZelleRequests(long customerId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM zelle_payment_requests
                WHERE status = 'PENDING'
                  AND (requester_customer_id = ? OR payer_customer_id = ?)
                """;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            statement.setLong(2, customerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Count query returned no result.");
                }
                return result.getLong(1);
            }
        }
    }

    /**
     * Reads a single count for a customer.
     */
    private long count(String sql, long customerId) throws SQLException {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Count query returned no result.");
                }
                return result.getLong(1);
            }
        }
    }
}
