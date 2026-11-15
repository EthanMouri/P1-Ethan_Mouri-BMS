
package com.revature.account.investment;

import com.revature.account.Account;
import com.revature.customer.Customer;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents an Investment Account with a fixed term and interest rate.
 */
public class InvestmentAccount extends Account {

    private final BigDecimal principal;
    private final int termMonths;
    private final BigDecimal interestRate;
    private final LocalDate withdrawalUnlockedDate;

    /**
     * Creates an Investment Account.
     *
     * @param customer the customer who owns the account
     * @param principal the original amount deposited
     * @param termMonths the length of the deposit in months
     * @param interestRate the fixed annual interest rate as a percentage
     * @param withdrawalUnlockedDate the date the deposit allows you to withdraw money
     */
    public InvestmentAccount(Customer customer, BigDecimal principal,
                             int termMonths, BigDecimal interestRate, LocalDate withdrawalUnlockedDate) {
        super(customer);

        //Check that the Investment Account has a positive principal amount.
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("Investment Account principal must be positive.");
        }

        //Check that the principal has no more than two decimal places.
        if (principal.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Investment Account principal cannot have more than two decimal places.");
        }

        //Check that the Investment Account has a valid term.
        if (termMonths <= 0) {
            throw new IllegalArgumentException("Investment Account term must be greater than zero.");
        }

        //Check that the interest rate falls within our bank's allowed range.
        if (interestRate == null ||
                interestRate.compareTo(new BigDecimal("4.00")) < 0 ||
                interestRate.compareTo(new BigDecimal("10.00")) > 0) {
            throw new IllegalArgumentException("Investment Account interest rate must be between 4.00% and 10.00%.");
        }

        //Check that a withdrawalUnlockedDate was provided.
        if (withdrawalUnlockedDate == null) {
            throw new IllegalArgumentException("Investment Account 'withdrawal unlocked date' is required.");
        }

        this.principal = principal.setScale(2);
        this.termMonths = termMonths;
        this.interestRate = interestRate.setScale(2, java.math.RoundingMode.HALF_UP);
        this.withdrawalUnlockedDate = withdrawalUnlockedDate;
    }

    /**
     * Gets the PostgreSQL account type for investment accounts.
     *
     * @return INVESTMENT
     */
    @Override
    public String getAccountType() {
        return "INVESTMENT";
    }

    /**
     * Gets the original deposit amount.
     *
     * @return the Investment Account principal
     */
    public BigDecimal getPrincipal() {
        return principal;
    }

    /**
     * Gets the Investment Account term in months.
     *
     * @return the number of months
     */
    public int getTermMonths() {
        return termMonths;
    }

    /**
     * Gets the fixed annual interest rate.
     *
     * @return the annual interest rate as a percentage
     */
    public BigDecimal getInterestRate() {
        return interestRate;
    }

    /**
     * Gets the date when the Investment Account allows you to withdraw money.
     *
     * @return the date you can withdraw money from the account
     */
    public LocalDate getWithdrawalUnlockedDate() {
        return withdrawalUnlockedDate;
    }
}
