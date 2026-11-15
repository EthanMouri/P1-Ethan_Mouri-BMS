
package com.revature.transactions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Represents a transaction as viewed from one bank account.
 *
 * @param transactionId the transaction's database ID
 * @param transactionType the type of financial transaction
 * @param amount the amount of money involved
 * @param direction whether money moved into or out of the selected account
 * @param status the transaction status
 * @param description the transaction description
 * @param balanceAfter the selected account's balance after the transaction
 * @param createdAt the date and time the transaction was recorded, including its UTC offset
 */
public record TransactionHistoryEntry(
        long transactionId,
        String transactionType,
        BigDecimal amount,
        String direction,
        String status,
        String description,
        BigDecimal balanceAfter,
        OffsetDateTime createdAt
) {
}
