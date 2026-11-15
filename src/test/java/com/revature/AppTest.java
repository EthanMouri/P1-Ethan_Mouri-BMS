package com.revature;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import com.revature.account.CheckingAccount;
import com.revature.customer.Customer;
import com.revature.transactions.TransactionHistoryEntry;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests deposit amount validation used by the banking CLI.
 */
public class AppTest {

    /**
     * Tests that a valid deposit amount is accepted.
     */
    @Test
    void parseDepositAmountAcceptsValidAmount() {
        //Allow whitespace around a valid amount.
        assertEquals(new BigDecimal("50.25"), App.parseDepositAmount(" 50.25 "));
    }

    /**
     * Tests that a whole-dollar deposit amount is accepted.
     */
    @Test
    void parseDepositAmountAcceptsWholeDollarAmount() {
        //Whole-dollar deposits do not need a decimal point.
        assertEquals(new BigDecimal("50"), App.parseDepositAmount("50"));
    }

    /**
     * Tests that zero and negative deposits are rejected.
     */
    @Test
    void parseDepositAmountRejectsNonPositiveAmounts() {
        //Deposits must increase the account balance.
        assertThrows(IllegalArgumentException.class, () -> App.parseDepositAmount("0"));
        assertThrows(IllegalArgumentException.class, () -> App.parseDepositAmount("-5.00"));
    }

    /**
     * Tests that deposits cannot include fractions of a cent.
     */
    @Test
    void parseDepositAmountRejectsExtraDecimalPlaces() {
        //PostgreSQL stores monetary amounts with two decimal places.
        assertThrows(IllegalArgumentException.class, () -> App.parseDepositAmount("12.345"));
    }

    /**
     * Tests that empty and nonnumeric deposit amounts are rejected.
     */
    @Test
    void parseDepositAmountRejectsInvalidText() {
        //Invalid input should never reach the database service.
        assertThrows(IllegalArgumentException.class, () -> App.parseDepositAmount(""));
        assertThrows(IllegalArgumentException.class, () -> App.parseDepositAmount("abc"));
        assertThrows(IllegalArgumentException.class, () -> App.parseDepositAmount(null));
    }

    /**
     * Tests that an amount exceeding the database's numeric precision is rejected.
     */
    @Test
    void parseDepositAmountRejectsOversizedAmount() {
        //Avoid amounts that cannot fit in a NUMERIC(19,2) column.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseDepositAmount("100000000000000000.00"));
    }


    /**
     * Tests that a valid withdrawal amount is accepted.
     */
    @Test
    void parseWithdrawalAmountAcceptsValidAmount() {
        //Allow whitespace around a valid amount.
        assertEquals(new BigDecimal("50.25"), App.parseWithdrawalAmount(" 50.25 "));
    }

    /**
     * Tests that a whole-dollar withdrawal amount is accepted.
     */
    @Test
    void parseWithdrawalAmountAcceptsWholeDollarAmount() {
        //Whole-dollar withdrawals do not need a decimal point.
        assertEquals(new BigDecimal("50"), App.parseWithdrawalAmount("50"));
    }

    /**
     * Tests that zero and negative withdrawal amounts are rejected.
     */
    @Test
    void parseWithdrawalAmountRejectsNonPositiveAmounts() {
        //Withdrawals must deduct a positive amount.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount("0"));
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount("-5.00"));
    }

    /**
     * Tests that withdrawals cannot include fractions of a cent.
     */
    @Test
    void parseWithdrawalAmountRejectsExtraDecimalPlaces() {
        //PostgreSQL stores monetary amounts with two decimal places.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount("12.345"));
    }

    /**
     * Tests that empty and nonnumeric withdrawal amounts are rejected.
     */
    @Test
    void parseWithdrawalAmountRejectsInvalidText() {
        //Invalid input should never reach the transaction service.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount(""));
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount("abc"));
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount(null));
    }

    /**
     * Tests that amounts exceeding the database's numeric precision are rejected.
     */
    @Test
    void parseWithdrawalAmountRejectsOversizedAmount() {
        //Avoid amounts that cannot fit in a NUMERIC(19,2) column.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseWithdrawalAmount("100000000000000000.00"));
    }


    /**
     * Tests that a valid transfer amount is accepted.
     */
    @Test
    void parseTransferAmountAcceptsValidAmount() {
        //Allow whitespace around a valid amount.
        assertEquals(new BigDecimal("200.25"), App.parseTransferAmount(" 200.25 "));
    }

    /**
     * Tests that a whole-dollar transfer amount is accepted.
     */
    @Test
    void parseTransferAmountAcceptsWholeDollarAmount() {
        //Whole-dollar transfers do not need a decimal point.
        assertEquals(new BigDecimal("200"), App.parseTransferAmount("200"));
    }

    /**
     * Tests that zero and negative transfer amounts are rejected.
     */
    @Test
    void parseTransferAmountRejectsNonPositiveAmounts() {
        //Transfers must move a positive amount of money.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount("0"));
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount("-200.00"));
    }

    /**
     * Tests that transfers cannot include fractions of a cent.
     */
    @Test
    void parseTransferAmountRejectsExtraDecimalPlaces() {
        //PostgreSQL stores monetary amounts with two decimal places.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount("200.345"));
    }

    /**
     * Tests that empty and nonnumeric transfer amounts are rejected.
     */
    @Test
    void parseTransferAmountRejectsInvalidText() {
        //Invalid input should never reach the transaction service.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount(""));
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount("abc"));
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount(null));
    }

    /**
     * Tests that amounts exceeding the database's numeric precision are rejected.
     */
    @Test
    void parseTransferAmountRejectsOversizedAmount() {
        //Avoid amounts that cannot fit in a NUMERIC(19,2) column.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseTransferAmount("100000000000000000.00"));
    }


    /**
     * Tests that an account with no transactions displays
     * an appropriate empty-history message.
     */
    @Test
    void formatTransactionHistoryDisplaysEmptyHistory() {
        //Prepare an account without recorded transactions.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        CheckingAccount account = new CheckingAccount(customer);
        account.setAccountNumber("100000");

        String output = App.formatTransactionHistory(account, List.of());

        assertTrue(output.contains("Transaction History: CHECKING Account #100000"));
        assertTrue(output.contains("No transactions found for this account."));
    }

    /**
     * Tests that outgoing transfers display their amount,
     * direction, status, and resulting account balance.
     */
    @Test
    void formatTransactionHistoryDisplaysOutgoingTransfer() {
        //Prepare a Checking Account and one outgoing transfer.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        CheckingAccount account = new CheckingAccount(customer);
        account.setAccountNumber("100000");

        TransactionHistoryEntry transfer = new TransactionHistoryEntry(
                3L,
                "TRANSFER",
                new BigDecimal("200.00"),
                "OUT",
                "SUCCESS",
                "Transfer between own accounts",
                new BigDecimal("3200.00"),
                OffsetDateTime.parse("2026-11-13T10:30:00Z"));

        String output = App.formatTransactionHistory(account, List.of(transfer));

        assertTrue(output.contains("TRANSFER"));
        assertTrue(output.contains("$200.00"));
        assertTrue(output.contains("OUT"));
        assertTrue(output.contains("SUCCESS"));
        assertTrue(output.contains("$3200.00"));
        assertTrue(output.contains("Transfer between own accounts"));
    }

    /**
     * Tests that incoming transfers display the receiving
     * account's balance and incoming direction.
     */
    @Test
    void formatTransactionHistoryDisplaysIncomingTransfer() {
        //Prepare a Checking Account and one incoming transfer.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        CheckingAccount account = new CheckingAccount(customer);
        account.setAccountNumber("100001");

        TransactionHistoryEntry transfer = new TransactionHistoryEntry(
                3L,
                "TRANSFER",
                new BigDecimal("200.00"),
                "IN",
                "SUCCESS",
                "Transfer between own accounts",
                new BigDecimal("200.00"),
                OffsetDateTime.parse("2026-11-13T10:30:00Z"));

        String output = App.formatTransactionHistory(account, List.of(transfer));

        assertTrue(output.contains("Transaction History: CHECKING Account #100001"));
        assertTrue(output.contains("IN"));
        assertTrue(output.contains("$200.00"));
    }

    /**
     * Tests that transaction timestamps are displayed
     * in a readable date and time format.
     */
    @Test
    void formatTransactionHistoryDisplaysReadableDate() {
        //Prepare a transaction with a timezone-aware timestamp.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        CheckingAccount account = new CheckingAccount(customer);
        account.setAccountNumber("100000");

        TransactionHistoryEntry deposit = new TransactionHistoryEntry(
                1L,
                "DEPOSIT",
                new BigDecimal("3500.00"),
                "IN",
                "SUCCESS",
                "Cash deposit",
                new BigDecimal("3500.00"),
                OffsetDateTime.parse("2026-11-13T10:30:00Z"));

        String output = App.formatTransactionHistory(account, List.of(deposit));

        //The exact hour depends on the computer's configured timezone.
        assertTrue(output.contains("2026-11-13"));
        assertTrue(output.contains("DEPOSIT"));
        assertTrue(output.contains("Cash deposit"));
    }

    /**
     * Tests that missing account or history information
     * is rejected before formatting transaction data.
     */
    @Test
    void formatTransactionHistoryRejectsMissingInputs() {
        //Prepare a valid account for input validation.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        CheckingAccount account = new CheckingAccount(customer);

        assertThrows(IllegalArgumentException.class,
                () -> App.formatTransactionHistory(null, List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> App.formatTransactionHistory(account, null));
    }

    /**
     * Tests that Checking Accounts display the updated transaction options.
     */
    @Test
    void checkingAccountMenuIncludesUpdatedLabelsAndZelle() {
        //Verify the Checking Account menu options and their order.
        assertEquals(List.of(
                "Check Balance",
                "Deposit Cash",
                "Withdraw Cash",
                "Transfer Cash",
                "Zelle",
                "Close This Account",
                "View Transaction History",
                "Back to My Accounts"
        ), App.getSelectedAccountMenuOptions("CHECKING"));
    }

    /**
     * Tests that Savings Accounts display the same transaction options.
     */
    @Test
    void savingsAccountMenuIncludesUpdatedLabelsAndZelle() {
        //Verify that Zelle is available in the Savings Account menu.
        List<String> options = App.getSelectedAccountMenuOptions("SAVINGS");

        assertEquals(8, options.size());
        assertEquals("Deposit Cash", options.get(1));
        assertEquals("Withdraw Cash", options.get(2));
        assertEquals("Transfer Cash", options.get(3));
        assertEquals("Zelle", options.get(4));
        assertEquals("Close This Account", options.get(5));
        assertEquals("View Transaction History", options.get(6));
        assertEquals("Back to My Accounts", options.get(7));
    }



    /**
     * Tests that only Checking and Savings menus expose the closure action.
     */
    @Test
    void closeAccountOptionIsLimitedToCheckingAndSavings() {
        //Do not offer manual closure for Loan or Investment Accounts.
        assertTrue(App.getSelectedAccountMenuOptions("CHECKING").contains("Close This Account"));
        assertTrue(App.getSelectedAccountMenuOptions("SAVINGS").contains("Close This Account"));
        assertFalse(App.getSelectedAccountMenuOptions("LOAN").contains("Close This Account"));
        assertFalse(App.getSelectedAccountMenuOptions("INVESTMENT").contains("Close This Account"));
    }


    /**
     * Tests that Investment and Loan Accounts do not display Zelle,
     * and that each account type has its own supported payment action.
     */
    @Test
    void investmentAndLoanMenusDoNotIncludeZelle() {
        //Keep Zelle limited to Checking and Savings Accounts.
        List<String> investmentOptions = App.getSelectedAccountMenuOptions("INVESTMENT");
        List<String> loanOptions = App.getSelectedAccountMenuOptions("LOAN");

        assertFalse(investmentOptions.contains("Zelle"));
        assertFalse(loanOptions.contains("Zelle"));

        //Investment Accounts now support contributions.
        assertEquals(List.of(
                "Check Balance",
                "Make Investment Contribution",
                "Collect Matured Investment",
                "View Transaction History",
                "Back to My Accounts"
        ), investmentOptions);

        //Loan Accounts retain their repayment option.
        assertEquals(List.of(
                "Check Balance",
                "Make Loan Payment",
                "View Transaction History",
                "Back to My Accounts"
        ), loanOptions);
    }



    /**
     * Tests that the investment maturity payout appears after contributions
     * without changing the Loan Account menu.
     */
    @Test
    void investmentMaturityMenuOptionAppearsInCorrectPosition() {
        //Keep maturity collection separate from contributions and history.
        List<String> options = App.getSelectedAccountMenuOptions("INVESTMENT");
        assertEquals(5, options.size());
        assertEquals("Collect Matured Investment", options.get(2));
        assertEquals("View Transaction History", options.get(3));
        assertEquals("Back to My Accounts", options.get(4));
    }


    /**
     * Tests that valid loan repayment amounts are accepted.
     */
    @Test
    void parseLoanRepaymentAmountAcceptsValidAmount() {
        //Allow whitespace around a valid repayment amount.
        assertEquals(new BigDecimal("100.25"),
                App.parseLoanRepaymentAmount(" 100.25 "));

        //Allow whole-dollar repayments.
        assertEquals(new BigDecimal("100"),
                App.parseLoanRepaymentAmount("100"));
    }

    /**
     * Tests that zero and negative loan repayment amounts are rejected.
     */
    @Test
    void parseLoanRepaymentAmountRejectsNonPositiveAmounts() {
        //Loan repayments must reduce outstanding debt by a positive amount.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseLoanRepaymentAmount("0"));

        assertThrows(IllegalArgumentException.class,
                () -> App.parseLoanRepaymentAmount("-25.00"));
    }

    /**
     * Tests that loan repayments cannot contain fractions of a cent.
     */
    @Test
    void parseLoanRepaymentAmountRejectsExtraDecimalPlaces() {
        //PostgreSQL stores monetary amounts with two decimal places.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseLoanRepaymentAmount("100.345"));
    }

    /**
     * Tests that invalid and oversized loan repayment inputs are rejected.
     */
    @Test
    void parseLoanRepaymentAmountRejectsInvalidTextAndOversizedAmount() {
        //Reject missing and nonnumeric repayment amounts.
        assertThrows(IllegalArgumentException.class, () -> App.parseLoanRepaymentAmount(""));
        assertThrows(IllegalArgumentException.class, () -> App.parseLoanRepaymentAmount("abc"));
        assertThrows(IllegalArgumentException.class, () -> App.parseLoanRepaymentAmount(null));

        //Reject amounts exceeding the database's numeric precision.
        assertThrows(IllegalArgumentException.class, () -> App.parseLoanRepaymentAmount("100000000000000000.00"));
    }


    /**
     * Tests that valid Investment Account contribution amounts are accepted.
     */
    @Test
    void parseInvestmentContributionAmountAcceptsValidAmounts() {
        //Allow whitespace around a contribution amount.
        assertEquals(new BigDecimal("250.75"),
                App.parseInvestmentContributionAmount(" 250.75 "));

        //Allow whole-dollar contributions.
        assertEquals(new BigDecimal("250"),
                App.parseInvestmentContributionAmount("250"));
    }

    /**
     * Tests that zero and negative Investment Account contributions are rejected.
     */
    @Test
    void parseInvestmentContributionAmountRejectsNonPositiveAmounts() {
        //Contributions must increase the Investment Account balance.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount("0"));

        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount("-25.00"));
    }

    /**
     * Tests that Investment Account contributions cannot include fractions of a cent.
     */
    @Test
    void parseInvestmentContributionAmountRejectsExtraDecimalPlaces() {
        //PostgreSQL stores monetary amounts with two decimal places.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount("250.345"));
    }

    /**
     * Tests that invalid text and oversized Investment Account contributions
     * are rejected before they reach the transaction service.
     */
    @Test
    void parseInvestmentContributionAmountRejectsInvalidTextAndOversizedAmount() {
        //Reject missing and nonnumeric amounts.
        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount(""));

        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount("abc"));

        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount(null));

        //Reject amounts exceeding PostgreSQL NUMERIC(19,2).
        assertThrows(IllegalArgumentException.class,
                () -> App.parseInvestmentContributionAmount("100000000000000000.00"));
    }


    /**
     * Tests that long investment contribution transaction types
     * do not shift the amount column out of alignment.
     */
    @Test
    void formatTransactionHistoryAlignsInvestmentContributionColumns() {
        //Prepare an Investment Account owned by the test customer.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");

        CheckingAccount account = new CheckingAccount(customer);
        account.setAccountNumber("100000");

        //Prepare a transaction with the longest supported transaction type.
        TransactionHistoryEntry contribution = new TransactionHistoryEntry(
                6L,
                "INVESTMENT_CONTRIBUTION",
                new BigDecimal("650.00"),
                "OUT",
                "SUCCESS",
                "Investment contribution",
                new BigDecimal("2000.00"),
                OffsetDateTime.parse("2026-11-13T23:43:00Z"));

        String output = App.formatTransactionHistory(account, List.of(contribution));

        //The amount must begin at the same column as the Amount header.
        String[] lines = output.split("\n");

        String header = lines[2];
        String transactionRow = lines[4];

        assertEquals(
                header.indexOf("Amount"),
                transactionRow.indexOf("$650.00"));

        //The longer type and remaining transaction details must still appear.
        assertTrue(transactionRow.contains("INVESTMENT_CONTRIBUTION"));
        assertTrue(transactionRow.contains("OUT"));
        assertTrue(transactionRow.contains("SUCCESS"));
        assertTrue(transactionRow.contains("$2000.00"));
    }



    /** Checks that the demo Zelle submenu has the expected choices. */
    @Test
    void zelleMenuHasFourChoices() {
        //Keep the menu simple and consistent for Checking and Savings.
        assertEquals(List.of("Send Cash", "Request Cash", "View Payment Requests", "Back"),
                App.getZelleMenuOptions());
    }

    /** Checks that customers can return from the Zelle submenu. */
    @Test
    void zelleMenuEndsWithBack() {
        //The fourth option returns to the selected bank account.
        assertEquals("Back", App.getZelleMenuOptions().get(3));
    }
    /** Checks that closure requires an explicit menu selection. */
    @Test
    void customerClosureMenuHasConfirmationAndCancel() {
        //Show a safe cancellation option alongside closure.
        assertEquals(List.of("Confirm Membership Closure", "Cancel"),
                App.getCustomerClosureConfirmationOptions());
    }

    /** Checks that the closure confirmation is the first choice. */
    @Test
    void customerClosureConfirmationIsFirst() {
        //The CLI only attempts closure after choosing the confirmation option.
        assertEquals("Confirm Membership Closure",
                App.getCustomerClosureConfirmationOptions().get(0));
    }

    /** Checks that customers can cancel relationship closure. */
    @Test
    void customerClosureMenuEndsWithCancel() {
        //Customers must be able to return to their accounts without closing.
        assertEquals("Cancel", App.getCustomerClosureConfirmationOptions().get(1));
    }
}
