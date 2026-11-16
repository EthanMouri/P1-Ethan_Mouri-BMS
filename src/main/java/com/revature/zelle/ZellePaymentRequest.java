package com.revature.zelle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A simulated Zelle payment request belonging to two customers.
 */
public record ZellePaymentRequest(long requestId, long requesterCustomerId,
                                  long payerCustomerId, BigDecimal amount,
                                  String description, String status,
                                  Long fundingAccountId, Long paymentTransactionId,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                  OffsetDateTime resolvedAt, String requesterUsername) {
    /** Keeps existing callers compatible when the username is not loaded. */
    public ZellePaymentRequest(long requestId, long requesterCustomerId,
                               long payerCustomerId, BigDecimal amount,
                               String description, String status,
                               Long fundingAccountId, Long paymentTransactionId,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               OffsetDateTime resolvedAt) {
        this(requestId, requesterCustomerId, payerCustomerId, amount, description, status,
             fundingAccountId, paymentTransactionId, createdAt, updatedAt, resolvedAt, null);
    }
}
