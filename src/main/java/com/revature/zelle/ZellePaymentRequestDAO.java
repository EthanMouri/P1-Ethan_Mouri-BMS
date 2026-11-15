package com.revature.zelle;

import com.revature.util.ConnectionProvider;
import com.revature.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Database operations for simulated Zelle payment requests.
 * Does not move funds or approve requests.
 */
public class ZellePaymentRequestDAO {

    private final ConnectionProvider connectionProvider;

    /** Creates a DAO using PostgreSQL. */
    public ZellePaymentRequestDAO() {
        this(DatabaseConnection::getConnection);
    }

    /** Creates a DAO using a testable connection provider. */
    public ZellePaymentRequestDAO(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Creates a pending request without transferring money.
     *
     * @return the generated request ID
     */
    public long createRequest(long requesterCustomerId, long payerCustomerId,
                              BigDecimal amount, String description) throws SQLException {
        if (requesterCustomerId <= 0 || payerCustomerId <= 0 ||
                requesterCustomerId == payerCustomerId) {
            throw new IllegalArgumentException("Two different valid customers are required.");
        }
        if (amount == null || amount.signum() <= 0 ||
                amount.stripTrailingZeros().scale() > 2 ||
                amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("A positive amount with at most two decimal places is required.");
        }
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("Description cannot exceed 255 characters.");
        }

        //Lock both customer rows in ID order to avoid opposite-order deadlocks.
        String lockSql = "SELECT status FROM customers WHERE customer_id = ? FOR UPDATE";
        String insertSql = """
                INSERT INTO zelle_payment_requests
                    (requester_customer_id, payer_customer_id, amount, description, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                RETURNING request_id
                """;
        try (Connection connection = connectionProvider.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                long firstId = Math.min(requesterCustomerId, payerCustomerId);
                long secondId = Math.max(requesterCustomerId, payerCustomerId);
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    for (long customerId : new long[]{firstId, secondId}) {
                        lock.setLong(1, customerId);
                        try (ResultSet result = lock.executeQuery()) {
                            if (!result.next() || !"ACTIVE".equals(result.getString("status"))) {
                                throw new SQLException("Both customers must be active to create a payment request.");
                            }
                        }
                    }
                }
                long requestId;
                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    statement.setLong(1, requesterCustomerId);
                    statement.setLong(2, payerCustomerId);
                    statement.setBigDecimal(3, amount);
                    statement.setString(4, description);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Payment request insertion returned no ID.");
                        }
                        requestId = results.getLong("request_id");
                        if (requestId <= 0) {
                            throw new SQLException("Payment request insertion returned an invalid ID.");
                        }
                    }
                }
                connection.commit();
                return requestId;
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

    /** Returns pending requests received by a customer, regardless of bank account. */
    public List<ZellePaymentRequest> findPendingRequestsForPayer(long payerCustomerId)
            throws SQLException {
        if (payerCustomerId <= 0) {
            throw new IllegalArgumentException("A valid customer ID is required.");
        }
        String sqlQuery = """
                SELECT request_id, requester_customer_id, payer_customer_id,
                       amount, description, status, funding_account_id,
                       payment_transaction_id, created_at, updated_at, resolved_at
                FROM zelle_payment_requests
                WHERE payer_customer_id = ? AND status = 'PENDING'
                ORDER BY created_at, request_id
                """;
        List<ZellePaymentRequest> requests = new ArrayList<>();
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setLong(1, payerCustomerId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    requests.add(mapRequest(results));
                }
            }
        }
        return requests;
    }

    /** Returns one request only when the customer is its requester or payer. */
    public ZellePaymentRequest findRequestForCustomer(long requestId, long customerId)
            throws SQLException {
        if (requestId <= 0 || customerId <= 0) {
            throw new IllegalArgumentException("Valid request and customer IDs are required.");
        }
        String sqlQuery = """
                SELECT request_id, requester_customer_id, payer_customer_id,
                       amount, description, status, funding_account_id,
                       payment_transaction_id, created_at, updated_at, resolved_at
                FROM zelle_payment_requests
                WHERE request_id = ?
                  AND (requester_customer_id = ? OR payer_customer_id = ?)
                """;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setLong(1, requestId);
            statement.setLong(2, customerId);
            statement.setLong(3, customerId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? mapRequest(results) : null;
            }
        }
    }

    /**
     * Rejects a pending request only when it belongs to the payer.
     *
     * @param requestId the request being rejected
     * @param payerCustomerId the customer who was asked to pay
     * @throws SQLException if the request is missing, already resolved, or the database fails
     */
    public void rejectRequest(long requestId, long payerCustomerId) throws SQLException {
        //Check IDs before opening a database connection.
        if (requestId <= 0 || payerCustomerId <= 0) {
            throw new IllegalArgumentException("Valid request and payer IDs are required.");
        }

        //The status check prevents a second rejection or rejection after approval.
        //The payer check prevents one customer from rejecting another customer's request.
        String sqlQuery = """
                UPDATE zelle_payment_requests
                SET status = 'REJECTED',
                    resolved_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE request_id = ? AND payer_customer_id = ? AND status = 'PENDING'
                """;
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
            statement.setLong(1, requestId);
            statement.setLong(2, payerCustomerId);

            //Exactly one row must change. No money or account balances are touched.
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Request is not pending or does not belong to this payer.");
            }
        }
    }

    private ZellePaymentRequest mapRequest(ResultSet results) throws SQLException {
        return new ZellePaymentRequest(
                results.getLong("request_id"),
                results.getLong("requester_customer_id"),
                results.getLong("payer_customer_id"),
                results.getBigDecimal("amount"),
                results.getString("description"),
                results.getString("status"),
                (Long) results.getObject("funding_account_id", Long.class),
                (Long) results.getObject("payment_transaction_id", Long.class),
                results.getObject("created_at", java.time.OffsetDateTime.class),
                results.getObject("updated_at", java.time.OffsetDateTime.class),
                results.getObject("resolved_at", java.time.OffsetDateTime.class));
    }
}
