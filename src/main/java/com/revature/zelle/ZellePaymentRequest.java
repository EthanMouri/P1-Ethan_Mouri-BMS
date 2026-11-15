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
                                  OffsetDateTime resolvedAt) {
}
