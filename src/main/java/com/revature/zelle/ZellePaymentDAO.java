package com.revature.zelle;

import com.revature.util.ConnectionProvider;
import com.revature.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Moves money for approved demo Zelle requests.
 * All database changes happen together or not at all.
 */
public class ZellePaymentDAO {

    private final ConnectionProvider connectionProvider;

    /** Creates the DAO with the application's PostgreSQL connection. */
    public ZellePaymentDAO() {
        this(DatabaseConnection::getConnection);
    }

    /**
     * Creates the DAO with a connection provider that can be mocked in tests.
     *
     * @param connectionProvider provides database connections
     */
    public ZellePaymentDAO(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("Connection provider is required.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Approves a request and transfers money from the payer to the requester.
     * The requester's oldest active Checking or Savings Account receives the money.
     *
     * @param requestId the pending request's ID
     * @param payerCustomerId the customer approving the request
     * @param fundingAccountId the payer's chosen Checking or Savings Account ID
     * @return the new transaction ID
     * @throws SQLException if the request cannot be paid or the database fails
     */
    public long approveRequest(long requestId, long payerCustomerId, long fundingAccountId)
            throws SQLException {
        if (requestId <= 0 || payerCustomerId <= 0 || fundingAccountId <= 0) {
            throw new IllegalArgumentException("Valid request, customer, and account IDs are required.");
        }

        try (Connection connection = connectionProvider.getConnection()) {
            //Start one transaction for the request, balances, and history.
            boolean originalAutoCommit = connection.getAutoCommit();
            if (!originalAutoCommit) {
                throw new SQLException("Zelle approval requires a connection in auto-commit mode.");
            }
            connection.setAutoCommit(false);
            try {
                //Read the request before locking; recheck it after customer locks.
                //This keeps customer locks ahead of request and account locks.
                long requesterId;
                BigDecimal amount;
                String requestDescription;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT requester_customer_id, amount, description
                        FROM zelle_payment_requests
                        WHERE request_id = ? AND payer_customer_id = ? AND status = 'PENDING'
                        """)) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, payerCustomerId);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Payment request is missing, already resolved, or not yours.");
                        }
                        requesterId = results.getLong("requester_customer_id");
                        amount = results.getBigDecimal("amount");
                        requestDescription = results.getString("description");
                    }
                }
                if (requesterId <= 0 || requesterId == payerCustomerId || amount == null
                        || amount.signum() <= 0) {
                    throw new SQLException("Payment request has invalid customer or amount data.");
                }

                //Lock both customers in ascending ID order before locking the request.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM customers WHERE customer_id = ? FOR UPDATE")) {
                    long firstId = Math.min(requesterId, payerCustomerId);
                    long secondId = Math.max(requesterId, payerCustomerId);
                    for (long customerId : new long[]{firstId, secondId}) {
                        statement.setLong(1, customerId);
                        try (ResultSet results = statement.executeQuery()) {
                            if (!results.next() || !"ACTIVE".equals(results.getString("status"))) {
                                throw new SQLException("Both Zelle customers must still be active.");
                            }
                        }
                    }
                }

                //Lock and recheck the request after obtaining customer locks.
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT requester_customer_id, amount, description
                        FROM zelle_payment_requests
                        WHERE request_id = ? AND payer_customer_id = ? AND status = 'PENDING'
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, payerCustomerId);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next() || results.getLong("requester_customer_id") != requesterId
                                || amount.compareTo(results.getBigDecimal("amount")) != 0) {
                            throw new SQLException("Payment request changed or was already resolved.");
                        }
                    }
                }

                //Choose one eligible destination account belonging to the requester.
                long destinationId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT account_id FROM accounts
                        WHERE customer_id = ? AND account_type IN ('CHECKING', 'SAVINGS')
                          AND status = 'ACTIVE'
                        ORDER BY account_id LIMIT 1
                        """)) {
                    statement.setLong(1, requesterId);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Requester has no active Checking or Savings Account.");
                        }
                        destinationId = results.getLong("account_id");
                    }
                }
                if (destinationId == fundingAccountId) {
                    throw new SQLException("Funding and destination accounts must be different.");
                }

                //Lock both accounts in ID order to reduce the chance of deadlocks.
                //Recheck ownership and status after obtaining the locks.
                BigDecimal sourceBalance = null;
                BigDecimal destinationBalance = null;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT account_id, customer_id, account_type, status, balance
                        FROM accounts WHERE account_id IN (?, ?)
                        ORDER BY account_id FOR UPDATE
                        """)) {
                    statement.setLong(1, fundingAccountId);
                    statement.setLong(2, destinationId);
                    try (ResultSet results = statement.executeQuery()) {
                        while (results.next()) {
                            long accountId = results.getLong("account_id");
                            long ownerId = results.getLong("customer_id");
                            String type = results.getString("account_type");
                            String status = results.getString("status");
                            if (!("CHECKING".equals(type) || "SAVINGS".equals(type))
                                    || !"ACTIVE".equals(status)) {
                                throw new SQLException("Zelle requires active Checking or Savings Accounts.");
                            }
                            if (accountId == fundingAccountId && ownerId == payerCustomerId) {
                                sourceBalance = results.getBigDecimal("balance");
                            } else if (accountId == destinationId && ownerId == requesterId) {
                                destinationBalance = results.getBigDecimal("balance");
                            } else {
                                throw new SQLException("Account ownership does not match the request.");
                            }
                        }
                    }
                }
                if (sourceBalance == null || destinationBalance == null) {
                    throw new SQLException("Both payment accounts must exist and belong to the correct customers.");
                }
                if (sourceBalance.compareTo(amount) < 0) {
                    throw new SQLException("Insufficient funds for this Zelle payment.");
                }

                BigDecimal newSourceBalance = sourceBalance.subtract(amount);
                BigDecimal newDestinationBalance = destinationBalance.add(amount);

                //Update both balances while the accounts remain locked.
                updateBalance(connection, fundingAccountId, newSourceBalance);
                updateBalance(connection, destinationId, newDestinationBalance);

                //Save the transfer and its audit record in this same transaction.
                long transactionId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO transactions
                            (source_account_id, destination_account_id, transaction_type,
                             amount, status, description, source_balance_after,
                             destination_balance_after)
                        VALUES (?, ?, 'ZELLE_TRANSFER', ?, 'SUCCESS', ?, ?, ?)
                        RETURNING transaction_id
                        """)) {
                    statement.setLong(1, fundingAccountId);
                    statement.setLong(2, destinationId);
                    statement.setBigDecimal(3, amount);
                    statement.setString(4, requestDescription == null || requestDescription.isBlank()
                            ? "Zelle payment request #" + requestId : requestDescription);
                    statement.setBigDecimal(5, newSourceBalance);
                    statement.setBigDecimal(6, newDestinationBalance);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next() || (transactionId = results.getLong("transaction_id")) <= 0) {
                            throw new SQLException("Zelle transaction record could not be saved.");
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO transaction_audit
                            (customer_id, transaction_id, event_type, outcome)
                        VALUES (?, ?, 'ZELLE_TRANSFER', 'SUCCESS')
                        """)) {
                    statement.setLong(1, payerCustomerId);
                    statement.setLong(2, transactionId);
                    requireOneRow(statement.executeUpdate(), "Zelle audit record");
                }

                //Resolve the request only after the transfer is recorded.
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE zelle_payment_requests
                        SET status = 'APPROVED', funding_account_id = ?,
                            payment_transaction_id = ?, resolved_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_id = ? AND payer_customer_id = ? AND status = 'PENDING'
                        """)) {
                    statement.setLong(1, fundingAccountId);
                    statement.setLong(2, transactionId);
                    statement.setLong(3, requestId);
                    statement.setLong(4, payerCustomerId);
                    requireOneRow(statement.executeUpdate(), "Zelle request approval");
                }

                connection.commit();
                return transactionId;
            } catch (SQLException | RuntimeException error) {
                //Undo every change if any part of the payment fails.
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            } finally {
                //Restore the connection's original transaction setting.
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /** Updates one account's balance using the existing transaction. */
    private void updateBalance(Connection connection, long accountId, BigDecimal balance)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE accounts SET balance = ?, updated_at = CURRENT_TIMESTAMP
                WHERE account_id = ? AND status = 'ACTIVE'
                """)) {
            statement.setBigDecimal(1, balance);
            statement.setLong(2, accountId);
            requireOneRow(statement.executeUpdate(), "Account balance update");
        }
    }

    /** Makes sure an update or insert affected exactly one row. */
    private void requireOneRow(int count, String operation) throws SQLException {
        if (count != 1) {
            throw new SQLException(operation + " did not affect exactly one row.");
        }
    }
}
