package com.revature.account.investment;

import com.revature.customer.Customer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests eligibility rules for Investment Account maturity payouts.
 */
public class InvestmentMaturityValidatorTest {

    /**
     * Creates a test Investment Account with a fixed unlock date.
     *
     * @return an active Investment Account
     */
    private InvestmentAccount createInvestmentAccount() {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "maturitytest", "unused-test-hash");
        customer.setId(1L);
        return new InvestmentAccount(customer, new BigDecimal("1000.00"), 12,
                new BigDecimal("5.00"), LocalDate.of(2027, 1, 1));
    }

    /**
     * Tests that an active investment is ineligible before its unlock date.
     */
    @Test
    void rejectsPayoutBeforeUnlockDate() {
        InvestmentAccount account = createInvestmentAccount();
        assertFalse(InvestmentMaturityValidator.isEligibleForMaturityPayout(
                account, LocalDate.of(2026, 12, 31)));
    }

    /**
     * Tests that an active investment becomes eligible on its unlock date.
     */
    @Test
    void allowsPayoutOnUnlockDate() {
        InvestmentAccount account = createInvestmentAccount();
        assertTrue(InvestmentMaturityValidator.isEligibleForMaturityPayout(
                account, LocalDate.of(2027, 1, 1)));
    }

    /**
     * Tests that an active investment remains eligible after its unlock date.
     */
    @Test
    void allowsPayoutAfterUnlockDate() {
        InvestmentAccount account = createInvestmentAccount();
        assertTrue(InvestmentMaturityValidator.isEligibleForMaturityPayout(
                account, LocalDate.of(2027, 2, 1)));
    }

    /**
     * Tests that a closed investment cannot receive another maturity payout.
     */
    @Test
    void rejectsPayoutForClosedAccount() {
        InvestmentAccount account = createInvestmentAccount();
        account.setStatus("CLOSED");
        assertFalse(InvestmentMaturityValidator.isEligibleForMaturityPayout(
                account, LocalDate.of(2027, 2, 1)));
    }

    /**
     * Tests that missing inputs are rejected instead of silently accepted.
     */
    @Test
    void rejectsMissingAccountOrDate() {
        InvestmentAccount account = createInvestmentAccount();
        assertThrows(IllegalArgumentException.class, () ->
                InvestmentMaturityValidator.isEligibleForMaturityPayout(null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                InvestmentMaturityValidator.isEligibleForMaturityPayout(account, null));
    }
}
