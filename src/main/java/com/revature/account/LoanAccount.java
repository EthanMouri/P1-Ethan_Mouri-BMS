
package com.revature.account;

import com.revature.customer.Customer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Represents a Loan Account with a fixed annual interest rate and repayment period.
 */
public class LoanAccount extends Account {

    private final BigDecimal principalAmount;
    private final BigDecimal annualInterestRate;
    private final int repaymentPeriodInMonths;
    private final LocalDate loanStartDate;

    /**
     * Creates a Loan Account with the agreed borrowing details.
     *
     * @param customer the customer who owns the Loan Account
     * @param principalAmount the original amount of money borrowed
     * @param annualInterestRate the annual interest rate as a percentage
     * @param repaymentPeriodInMonths the number of months allowed to repay the loan
     * @param loanStartDate the date the loan begins
     */
    public LoanAccount(Customer customer, BigDecimal principalAmount,
                       BigDecimal annualInterestRate, int repaymentPeriodInMonths,
                       LocalDate loanStartDate) {
        super(customer);

        //Check that the amount borrowed is positive.
        if (principalAmount == null || principalAmount.signum() <= 0) {
            throw new IllegalArgumentException("Loan principal amount must be positive.");
        }

        //Check that the amount borrowed has no more than two decimal places.
        if (principalAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Loan principal amount cannot have more than two decimal places.");
        }

        //Check that the annual interest rate is between 3.00% and 9.00%.
        if (annualInterestRate == null ||
                annualInterestRate.compareTo(new BigDecimal("3.00")) < 0 ||
                annualInterestRate.compareTo(new BigDecimal("9.00")) > 0) {
            throw new IllegalArgumentException("Loan annual interest rate must be between 3.00% and 9.00%.");
        }

        //Check that the customer has at least one month to repay the loan.
        if (repaymentPeriodInMonths <= 0) {
            throw new IllegalArgumentException("Loan repayment period must be greater than zero months.");
        }

        //Check that the loan start date was provided.
        if (loanStartDate == null) {
            throw new IllegalArgumentException("Loan start date is required.");
        }

        //Store the loan details using two decimal places for money and interest.
        this.principalAmount = principalAmount.setScale(2);
        this.annualInterestRate = annualInterestRate.setScale(2, RoundingMode.HALF_UP);
        this.repaymentPeriodInMonths = repaymentPeriodInMonths;
        this.loanStartDate = loanStartDate;
    }

    /**
     * Gets the PostgreSQL account type for Loan Accounts.
     *
     * @return LOAN
     */
    @Override
    public String getAccountType() {
        return "LOAN";
    }

    /**
     * Gets the original amount of money borrowed.
     *
     * @return the original loan principal amount
     */
    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    /**
     * Gets the fixed annual interest rate for the loan.
     *
     * @return the annual interest rate as a percentage
     */
    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    /**
     * Gets the number of months allowed to repay the loan.
     *
     * @return the loan repayment period in months
     */
    public int getRepaymentPeriodInMonths() {
        return repaymentPeriodInMonths;
    }

    /**
     * Gets the date when the loan begins.
     *
     * @return the loan start date
     */
    public LocalDate getLoanStartDate() {
        return loanStartDate;
    }
}
