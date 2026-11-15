package com.revature.account.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calculates simple interest earned by an individual investment contribution.
 * Each contribution earns interest from its own contribution date.
 */
public final class InvestmentInterestCalculator {

    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal PERCENT_DIVISOR = new BigDecimal("100");

    /**
     * Prevents creation of a calculator that contains only static behavior.
     */
    private InvestmentInterestCalculator() {
    }

    /**
     * Calculates simple interest for one contribution using actual days divided by 365.
     * The contribution date is included and the end date is excluded when counting
     * elapsed days. Interest is rounded to cents only after the calculation.
     *
     * @param contributionAmount the amount actually contributed to the investment
     * @param annualInterestRate the fixed annual interest rate as a percentage
     * @param contributionDate the date on which the contribution was made
     * @param endDate the date through which interest is calculated
     * @return interest earned, rounded to two decimal places
     * @throws IllegalArgumentException if an argument is invalid
     */
    public static BigDecimal calculateInterest(BigDecimal contributionAmount,
                                               BigDecimal annualInterestRate,
                                               LocalDate contributionDate,
                                               LocalDate endDate) {
        //Require a positive contribution with no fractional cents.
        if (contributionAmount == null || contributionAmount.signum() <= 0
                || contributionAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Contribution amount must be positive and have no more than two decimal places.");
        }

        //Use the bank's existing permitted annual interest rate range.
        if (annualInterestRate == null
                || annualInterestRate.compareTo(new BigDecimal("4.00")) < 0
                || annualInterestRate.compareTo(new BigDecimal("10.00")) > 0) {
            throw new IllegalArgumentException(
                    "Annual interest rate must be between 4.00% and 10.00%.");
        }

        //Require a valid period; no interest accrues before a contribution exists.
        if (contributionDate == null || endDate == null || endDate.isBefore(contributionDate)) {
            throw new IllegalArgumentException(
                    "End date must be on or after the contribution date.");
        }

        long daysInvested = ChronoUnit.DAYS.between(contributionDate, endDate);

        //Divide only once so intermediate rounding cannot change the final cents.
        return contributionAmount
                .multiply(annualInterestRate)
                .multiply(BigDecimal.valueOf(daysInvested))
                .divide(PERCENT_DIVISOR.multiply(DAYS_PER_YEAR), 2, RoundingMode.HALF_UP);
    }
}
