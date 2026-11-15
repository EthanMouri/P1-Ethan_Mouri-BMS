package com.revature.account.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Represents one funded Investment Account contribution and its saved timestamp.
 *
 * @param amount the actual amount contributed
 * @param contributedAt the timestamp assigned by PostgreSQL
 */
public record InvestmentContribution(BigDecimal amount, OffsetDateTime contributedAt) {
}
