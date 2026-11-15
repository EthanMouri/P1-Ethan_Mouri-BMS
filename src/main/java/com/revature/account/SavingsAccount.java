
package com.revature.account;

import com.revature.customer.Customer;
import java.math.BigDecimal;

/**
 * Represents a savings bank account that can earn interest.
 */
public class SavingsAccount extends Account {

    private final BigDecimal interestRate;

    /**
     * Creates a savings account belonging to a customer.
     *
     * @param accountOwner the customer who owns the account
     * @param interestRate the annual interest rate, expressed as a percentage
     * @throws IllegalArgumentException if the interest rate is null or negative
     */
    public SavingsAccount(Customer accountOwner, BigDecimal interestRate) {
        super(accountOwner);

        //Make sure the interest rate is valid.
        if (interestRate == null || interestRate.signum() < 0) {
            throw new IllegalArgumentException("Interest rate cannot be null or negative.");
        }

        this.interestRate = interestRate;
    }

    /**
     * Gets the annual interest rate for this savings account.
     *
     * @return the annual interest rate as a percentage
     */
    public BigDecimal getInterestRate() {
        return interestRate;
    }


    /**
     * Gets the PostgreSQL account type for savings accounts.
     *
     * @return SAVINGS
     */
    @Override
    public String getAccountType() {
        return "SAVINGS";
    }

}
