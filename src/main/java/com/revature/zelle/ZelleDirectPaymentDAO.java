package com.revature.zelle;

import com.revature.util.ConnectionProvider;
import com.revature.util.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Sends demo Zelle money directly between two customers.
 * The balances and transaction history are saved together.
 */
public class ZelleDirectPaymentDAO {

    private final ConnectionProvider connectionProvider;

    /** Creates a DAO using the application's database connection. */
    public ZelleDirectPaymentDAO() {
        this(DatabaseConnection::getConnection);
    }

    /**
     * Creates a DAO with a testable database connection provider.
     *
     * @param connectionProvider provides database connections
     */
    public ZelleDirectPaymentDAO(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("Connection provider is required.");
        }
        this.connectionProvider = connectionProvider;
    }

    /**
     * Transfers money from the sender's account to the recipient's oldest active account.
     * Both accounts must be active Checking or Savings Accounts.
     *
     * @param senderCustomerId the customer sending money
     * @param fundingAccountId the sender's chosen account
     * @param recipientCustomerId the customer receiving money
     * @param amount the amount to transfer
     * @param description an optional payment description
     * @return the new transaction ID
     * @throws SQLException if the transfer cannot be completed
     */
    public long sendMoney(long senderCustomerId, long fundingAccountId,
                          long recipientCustomerId, BigDecimal amount, String description)
            throws SQLException {
        if (senderCustomerId <= 0 || fundingAccountId <= 0 || recipientCustomerId <= 0
                || senderCustomerId == recipientCustomerId) {
            throw new IllegalArgumentException("Two different valid customers and a funding account are required.");
        }
        if (amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Enter a positive amount with at most two decimal places.");
        }
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("Description cannot exceed 255 characters.");
        }

        try (Connection connection = connectionProvider.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            if (!originalAutoCommit) {
                throw new SQLException("Zelle sending requires an auto-commit connection.");
            }
            //Start one transaction so a failed payment cannot move only some money.
            connection.setAutoCommit(false);
            try {
                //Lock both customers in ID order before touching their accounts.
                //This coordinates direct payments with customer relationship closure.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM customers WHERE customer_id = ? FOR UPDATE")) {
                    long firstId = Math.min(senderCustomerId, recipientCustomerId);
                    long secondId = Math.max(senderCustomerId, recipientCustomerId);
                    for (long customerId : new long[]{firstId, secondId}) {
                        statement.setLong(1, customerId);
                        try (ResultSet results = statement.executeQuery()) {
                            if (!results.next() || !"ACTIVE".equals(results.getString("status"))) {
                                throw new SQLException("Both Zelle customers must still be active.");
                            }
                        }
                    }
                }

                //Choose an active destination account belonging to the recipient.
                long destinationId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT account_id FROM accounts
                        WHERE customer_id = ? AND account_type IN ('CHECKING', 'SAVINGS')
                          AND status = 'ACTIVE'
                        ORDER BY account_id LIMIT 1
                        """)) {
                    statement.setLong(1, recipientCustomerId);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Recipient has no active Checking or Savings Account.");
                        }
                        destinationId = results.getLong("account_id");
                    }
                }
                if (destinationId == fundingAccountId) {
                    throw new SQLException("Payment accounts must be different.");
                }

                //Lock accounts in ID order and check their latest balances and ownership.
                BigDecimal senderBalance = null;
                BigDecimal recipientBalance = null;
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
                            long customerId = results.getLong("customer_id");
                            String type = results.getString("account_type");
                            if (!("CHECKING".equals(type) || "SAVINGS".equals(type))
                                    || !"ACTIVE".equals(results.getString("status"))) {
                                throw new SQLException("Zelle requires active Checking or Savings Accounts.");
                            }
                            if (accountId == fundingAccountId && customerId == senderCustomerId) {
                                senderBalance = results.getBigDecimal("balance");
                            } else if (accountId == destinationId && customerId == recipientCustomerId) {
                                recipientBalance = results.getBigDecimal("balance");
                            } else {
                                throw new SQLException("Account ownership does not match the payment.");
                            }
                        }
                    }
                }
                if (senderBalance == null || recipientBalance == null) {
                    throw new SQLException("Both payment accounts must be available.");
                }
                if (senderBalance.compareTo(amount) < 0) {
                    throw new SQLException("Insufficient funds for this Zelle payment.");
                }

                BigDecimal senderAfter = senderBalance.subtract(amount);
                BigDecimal recipientAfter = recipientBalance.add(amount);
                //Update both balances before saving the transaction record.
                updateBalance(connection, fundingAccountId, senderAfter);
                updateBalance(connection, destinationId, recipientAfter);

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
                    statement.setString(4, description == null || description.isBlank()
                            ? "Demo Zelle direct payment" : description);
                    statement.setBigDecimal(5, senderAfter);
                    statement.setBigDecimal(6, recipientAfter);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next() || (transactionId = results.getLong("transaction_id")) <= 0) {
                            throw new SQLException("Zelle transaction could not be saved.");
                        }
                    }
                }
                //Save the audit entry in the same database transaction.
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO transaction_audit
                            (customer_id, transaction_id, event_type, outcome)
                        VALUES (?, ?, 'ZELLE_TRANSFER', 'SUCCESS')
                        """)) {
                    statement.setLong(1, senderCustomerId);
                    statement.setLong(2, transactionId);
                    requireOneRow(statement.executeUpdate(), "Zelle audit record");
                }
                connection.commit();
                return transactionId;
            } catch (SQLException | RuntimeException error) {
                //Undo both balance changes and all history if anything fails.
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /** Updates a locked account's balance. */
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

    /** Makes sure exactly one row was saved. */
    private void requireOneRow(int count, String operation) throws SQLException {
        if (count != 1) {
            throw new SQLException(operation + " did not affect exactly one row.");
        }
    }
}
