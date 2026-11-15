package com.revature.account.investment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests simple interest calculations for individually funded investment contributions.
 */
class InvestmentInterestCalculatorTest {

    /**
     * Verifies that a contribution earns one full year of simple interest.
     */
    @Test
    void calculateInterestForFullYear() {
        BigDecimal interest = InvestmentInterestCalculator.calculateInterest(
                new BigDecimal("650.00"),
                new BigDecimal("5.00"),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1));

        assertEquals(new BigDecimal("32.50"), interest);
    }

    /**
     * Verifies that a contribution earns nothing on the date it is deposited.
     */
    @Test
    void calculateInterestForZeroDays() {
        LocalDate contributionDate = LocalDate.of(2026, 11, 13);

        BigDecimal interest = InvestmentInterestCalculator.calculateInterest(
                new BigDecimal("650.00"), new BigDecimal("5.00"),
                contributionDate, contributionDate);

        assertEquals(new BigDecimal("0.00"), interest);
    }

    /**
     * Verifies that interest uses actual elapsed days, including leap days.
     */
    @Test
    void calculateInterestAcrossLeapYear() {
        BigDecimal interest = InvestmentInterestCalculator.calculateInterest(
                new BigDecimal("365.00"),
                new BigDecimal("10.00"),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1));

        assertEquals(new BigDecimal("36.60"), interest);
    }

    /**
     * Verifies that interest is rounded to cents using HALF_UP.
     */
    @Test
    void calculateInterestRoundsToCents() {
        BigDecimal interest = InvestmentInterestCalculator.calculateInterest(
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2));

        assertEquals(new BigDecimal("0.01"), interest);
    }

    /**
     * Verifies that an end date before the contribution date is rejected.
     */
    @Test
    void calculateInterestRejectsEndDateBeforeContribution() {
        assertThrows(IllegalArgumentException.class,
                () -> InvestmentInterestCalculator.calculateInterest(
                        new BigDecimal("650.00"),
                        new BigDecimal("5.00"),
                        LocalDate.of(2026, 11, 13),
                        LocalDate.of(2026, 11, 12)));
    }

    /**
     * Verifies that zero, negative, and fractional-cent contributions are rejected.
     */
    @Test
    void calculateInterestRejectsInvalidContributionAmounts() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 1);

        assertThrows(IllegalArgumentException.class,
                () -> InvestmentInterestCalculator.calculateInterest(
                        BigDecimal.ZERO, new BigDecimal("5.00"), start, end));
        assertThrows(IllegalArgumentException.class,
                () -> InvestmentInterestCalculator.calculateInterest(
                        new BigDecimal("-1.00"), new BigDecimal("5.00"), start, end));
        assertThrows(IllegalArgumentException.class,
                () -> InvestmentInterestCalculator.calculateInterest(
                        new BigDecimal("1.001"), new BigDecimal("5.00"), start, end));
    }

    /**
     * Verifies that interest rates outside the Investment Account range are rejected.
     */
    @Test
    void calculateInterestRejectsInvalidRates() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 2, 1);

        assertThrows(IllegalArgumentException.class,
                () -> InvestmentInterestCalculator.calculateInterest(
                        new BigDecimal("100.00"), new BigDecimal("3.99"), start, end));
        assertThrows(IllegalArgumentException.class,
                () -> InvestmentInterestCalculator.calculateInterest(
                        new BigDecimal("100.00"), new BigDecimal("10.01"), start, end));
    }
}
