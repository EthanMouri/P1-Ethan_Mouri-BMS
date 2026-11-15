package com.revature.account.investment;

import java.time.LocalDate;

/**
 * Checks whether an Investment Account is eligible for a maturity payout.
 * This class does not change account balances or statuses.
 */
public final class InvestmentMaturityValidator {

    /**
     * Prevents instantiation of this utility class.
     */
    private InvestmentMaturityValidator() {
    }

    /**
     * Determines whether an Investment Account is active and has reached its
     * withdrawal unlock date.
     *
     * @param account the Investment Account being checked
     * @param currentDate the business date on which eligibility is evaluated
     * @return true when the account is active and its unlock date has arrived
     * @throws IllegalArgumentException if the account or date is null
     */
    public static boolean isEligibleForMaturityPayout(InvestmentAccount account,
                                                      LocalDate currentDate) {
        //Require an account and an explicit date for predictable testing.
        if (account == null || currentDate == null) {
            throw new IllegalArgumentException("Investment Account and current date are required.");
        }

        //A closed account cannot receive a second maturity payout.
        return "ACTIVE".equals(account.getStatus())
                && !currentDate.isBefore(account.getWithdrawalUnlockedDate());
    }
}
