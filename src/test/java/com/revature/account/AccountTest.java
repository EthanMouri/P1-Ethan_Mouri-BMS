
package com.revature.account;

import com.revature.account.investment.InvestmentAccount;
import com.revature.customer.Customer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the basic behavior of bank account models.
 */
public class AccountTest {

    /**
     * Tests that a checking account has the correct type and starting values.
     */
    @Test
    void checkingAccountHasCorrectDefaults() {
        //Create a customer to own the checking account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        //Create a checking account.
        CheckingAccount account = new CheckingAccount(customer);

        //Check the account type, status, and starting balance.
        assertEquals("CHECKING", account.getAccountType());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals(new BigDecimal("0.00"), account.getBalance());
    }

    /**
     * Tests that a savings account has the correct type and interest rate.
     */
    @Test
    void savingsAccountHasCorrectDefaults() {
        //Create a customer to own the savings account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        //Create a savings account with a 3.75% annual interest rate.
        SavingsAccount account = new SavingsAccount(customer, new BigDecimal("3.75"));

        //Check the account type, status, balance, and interest rate.
        assertEquals("SAVINGS", account.getAccountType());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals(new BigDecimal("0.00"), account.getBalance());
        assertEquals(new BigDecimal("3.75"), account.getInterestRate());
    }


    /**
     * Tests that an investment account stores its type and investment details correctly.
     */
    @Test
    void investmentAccountHasCorrectDetails() {
        //Create a customer to own the investment account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        //Create an investment with a 12-month term and 6.25% annual interest.
        LocalDate withdrawalUnlockedDate = LocalDate.of(2027, 11, 12);
        InvestmentAccount account = new InvestmentAccount(
                customer, new BigDecimal("5000.00"), 12,
                new BigDecimal("6.25"), withdrawalUnlockedDate);

        //Verify the investment account's type and details.
        assertEquals("INVESTMENT", account.getAccountType());
        assertEquals(new BigDecimal("5000.00"), account.getPrincipal());
        assertEquals(12, account.getTermMonths());
        assertEquals(new BigDecimal("6.25"), account.getInterestRate());
        assertEquals(withdrawalUnlockedDate, account.getWithdrawalUnlockedDate());

        //Verify the inherited account defaults.
        assertEquals("ACTIVE", account.getStatus());
        assertEquals(new BigDecimal("0.00"), account.getBalance());
    }


    /**
     * Tests that a Loan Account stores its type and borrowing details correctly.
     */
    @Test
    void loanAccountHasCorrectDetails() {
        //Create a customer to own the Loan Account.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        //Create a $10,000 loan with 5.75% annual interest and a 36-month repayment period.
        LocalDate loanStartDate = LocalDate.of(2026, 11, 12);
        LoanAccount account = new LoanAccount(
                customer, new BigDecimal("10000.00"),
                new BigDecimal("5.75"), 36, loanStartDate);

        //Verify the Loan Account type and borrowing details.
        assertEquals("LOAN", account.getAccountType());
        assertEquals(new BigDecimal("10000.00"), account.getPrincipalAmount());
        assertEquals(new BigDecimal("5.75"), account.getAnnualInterestRate());
        assertEquals(36, account.getRepaymentPeriodInMonths());
        assertEquals(loanStartDate, account.getLoanStartDate());

        //Verify the inherited Account defaults.
        assertEquals("ACTIVE", account.getStatus());
        assertEquals(new BigDecimal("0.00"), account.getBalance());
    }


}
