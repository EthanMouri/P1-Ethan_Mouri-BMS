
package com.revature.account;

import com.revature.account.investment.InvestmentAccount;
import com.revature.customer.Customer;

import java.math.BigDecimal;
import java.sql.SQLException;
import com.revature.util.ConnectionProvider;
import com.revature.util.DatabaseConnection;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles business rules for opening bank accounts.
 */
public class AccountService {

    private final AccountDAO accountDAO;
    private final ConnectionProvider connectionProvider;


    /**
     * Creates an AccountService that uses the actual PostgreSQL database.
     */
    public AccountService() {
        this(new AccountDAO(), DatabaseConnection::getConnection);
    }

    /**
     * Creates an AccountService with a provided AccountDAO.
     * Allows existing unit tests to supply a mocked DAO.
     *
     * @param accountDAO the DAO used for account database operations
     */
    public AccountService(AccountDAO accountDAO) {
        this(accountDAO, DatabaseConnection::getConnection);
    }

    /**
     * Creates an AccountService with a provided DAO and connection provider.
     * Allows transactions to be tested using mocked database connections.
     *
     * @param accountDAO the DAO used for account database operations
     * @param connectionProvider the provider of database connections
     */
    public AccountService(AccountDAO accountDAO, ConnectionProvider connectionProvider) {
        //Check that both dependencies were provided.
        if (accountDAO == null || connectionProvider == null) {
            throw new IllegalArgumentException("AccountDAO and ConnectionProvider cannot be null.");
        }

        this.accountDAO = accountDAO;
        this.connectionProvider = connectionProvider;
    }


    /**
     * Opens a checking account for the supplied customer.
     *
     * @param customer the customer who successfully logged in
     * @return the newly created checking account
     * @throws SQLException if the database operation fails
     */
    public CheckingAccount openCheckingAccount(Customer customer) throws SQLException {
        //Check that the customer has an existing database ID.
        if (customer == null || customer.getId() <= 0) {
            throw new IllegalArgumentException("A valid customer is required to open an account.");
        }

        //Check that the customer is eligible to open an account.
        if (!"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalStateException("Only active customers can open bank accounts.");
        }

        //Create a checking account with the default zero balance.
        CheckingAccount account = new CheckingAccount(customer);

        //Save the account and let PostgreSQL generate its identifiers.
        accountDAO.insertAccount(account);

        return account;
    }


    /**
     * Opens a savings account for the supplied customer.
     *
     * @param customer the customer who successfully logged in
     * @return the newly created savings account
     * @throws SQLException if the database operation fails
     */
    public SavingsAccount openSavingsAccount(Customer customer) throws SQLException {
        //Check that the customer has an existing database ID.
        if (customer == null || customer.getId() <= 0) {
            throw new IllegalArgumentException("A valid customer is required to open an account.");
        }

        //Check that the customer is eligible to open an account.
        if (!"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalStateException("Only active customers can open bank accounts.");
        }

        //Create a savings account with a 3.75% annual interest rate.
        SavingsAccount account = new SavingsAccount(customer, new BigDecimal("3.75"));

        //Save the account and let PostgreSQL generate its identifiers.
        accountDAO.insertAccount(account);

        return account;
    }


    /**
     * Opens an Investment Account for an active customer.
     * Saves the account and its investment details in one database transaction.
     *
     * @param customer the customer opening the Investment Account
     * @param principal the amount the customer intends to invest
     * @param termMonths the investment term in months
     * @return the newly created Investment Account
     * @throws SQLException if a database operation fails
     */
    public InvestmentAccount openInvestmentAccount(Customer customer, BigDecimal principal,
                                                   int termMonths) throws SQLException {
        //Check that the customer is valid and allowed to open an account.
        if (customer == null || customer.getId() <= 0 || !"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("An active customer is required to open an Investment Account.");
        }

        //Check that the investment amount is positive and has at most two decimal places.
        if (principal == null || principal.signum() <= 0 ||
                principal.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Investment principal must be positive with no more than two decimal places.");
        }

        //Check that the investment term is valid.
        if (termMonths <= 0) {
            throw new IllegalArgumentException("Investment term must be greater than zero months.");
        }

        //Generate an annual interest rate between 4.00% and 10.00%.
        int interestRateInHundredths = ThreadLocalRandom.current().nextInt(400, 1001);
        BigDecimal interestRate = BigDecimal.valueOf(interestRateInHundredths, 2);

        //Calculate the date when withdrawals become available without an early withdrawal penalty.
        LocalDate withdrawalUnlockedDate = LocalDate.now().plusMonths(termMonths);

        //Create the Investment Account using the agreed investment details.
        InvestmentAccount investmentAccount = new InvestmentAccount(
                customer, principal, termMonths, interestRate, withdrawalUnlockedDate);

        //Open one connection for both database inserts.
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);

            try {
                //Save the general account information.
                accountDAO.insertAccount(connection, investmentAccount);

                //Save the investment details using the same connection.
                accountDAO.insertInvestmentDetails(connection, investmentAccount);

                //Save both records together.
                connection.commit();

                return investmentAccount;
            } catch (SQLException | RuntimeException exception) {
                //Undo both inserts if either operation fails.
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }


    /**
     * Opens a Loan Account for an active customer.
     * Saves the account and loan details in one database transaction.
     *
     * @param customer the customer opening the Loan Account
     * @param principalAmount the amount the customer intends to borrow
     * @param repaymentPeriodInMonths the loan repayment period in months
     * @return the newly created Loan Account
     * @throws SQLException if a database operation fails
     */
    public LoanAccount openLoanAccount(Customer customer, BigDecimal principalAmount,
                                       int repaymentPeriodInMonths) throws SQLException {
        //Check that the customer is valid and allowed to open an account.
        if (customer == null || customer.getId() <= 0 || !"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("An active customer is required to open a Loan Account.");
        }

        //Check that the loan amount is positive and has at most two decimal places.
        if (principalAmount == null || principalAmount.signum() <= 0 ||
                principalAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Loan principal must be positive with no more than two decimal places.");
        }

        //Check that the repayment period is valid.
        if (repaymentPeriodInMonths <= 0) {
            throw new IllegalArgumentException("Loan repayment period must be greater than zero months.");
        }

        //Generate an annual interest rate between 3.00% and 9.00%.
        int interestRateInHundredths = ThreadLocalRandom.current().nextInt(300, 901);
        BigDecimal annualInterestRate = BigDecimal.valueOf(interestRateInHundredths, 2);

        //Record the date when the loan is opened.
        LocalDate loanStartDate = LocalDate.now();

        //Create the Loan Account using the agreed borrowing details.
        LoanAccount loanAccount = new LoanAccount(
                customer, principalAmount, annualInterestRate,
                repaymentPeriodInMonths, loanStartDate);

        //Set the initial outstanding loan balance to the amount borrowed.
        loanAccount.setBalance(principalAmount);

        //Open one connection for both database inserts.
        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);

            try {
                //Save the general account information.
                accountDAO.insertAccount(connection, loanAccount);

                //Save the loan details using the same connection.
                accountDAO.insertLoanDetails(connection, loanAccount);

                //Save both records together.
                connection.commit();

                return loanAccount;
            } catch (SQLException | RuntimeException exception) {
                //Undo both inserts if either operation fails.
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }


    /**
     * Retrieves all accounts belonging to an active customer.
     *
     * @param customer the customer whose accounts should be retrieved
     * @return the customer's accounts, ordered by account ID
     * @throws SQLException if the database query fails
     */
    public List<Account> getAccountsForCustomer(Customer customer) throws SQLException {
        //Check that the customer has a valid database ID and active status.
        if (customer == null || customer.getId() <= 0 || !"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("An active customer is required to view accounts.");
        }

        //Retrieve only the accounts associated with this customer's ID.
        return accountDAO.findAccountsByCustomer(customer);
    }


    /**
     * Retrieves the current balance of an account owned by an active customer.
     *
     * @param customer the authenticated customer requesting the balance
     * @param account the account whose balance is being requested
     * @return the account's current balance from PostgreSQL
     * @throws SQLException if the account cannot be found or the query fails
     */
    public BigDecimal getAccountBalance(Customer customer, Account account) throws SQLException {
        //Check that the customer is active and has a valid database ID.
        if (customer == null || customer.getId() <= 0 || !"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("An active customer is required to view an account balance.");
        }

        //Check that the account has a valid database ID.
        if (account == null || account.getId() <= 0) {
            throw new IllegalArgumentException("A valid account is required to view its balance.");
        }

        //Retrieve the balance using both the account ID and authenticated customer's ID.
        return accountDAO.getAccountBalance(account.getId(), customer.getId());
    }






    /**
     * Closes an active Checking or Savings Account owned by an active customer.
     * PostgreSQL makes the final balance and eligibility checks atomically.
     *
     * @param customer the authenticated customer requesting closure
     * @param account the Checking or Savings Account to close
     * @throws SQLException if the database rejects closure or cannot complete it
     */
    public void closeCheckingOrSavingsAccount(Customer customer, Account account) throws SQLException {
        //Validate the authenticated customer before accessing the database.
        if (customer == null || customer.getId() <= 0 || !"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("An active customer is required to close an account.");
        }

        //Require an existing Checking or Savings Account owned by this customer.
        if (account == null || account.getId() <= 0 ||
                !(account instanceof CheckingAccount || account instanceof SavingsAccount) ||
                account.getAccountOwnerName().getId() != customer.getId()) {
            throw new IllegalArgumentException("An owned Checking or Savings Account is required.");
        }

        //Reject accounts already marked inactive in the current view.
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalArgumentException("Only active accounts can be closed.");
        }

        //The DAO checks the current PostgreSQL balance, ownership, type and status atomically.
        accountDAO.closeCheckingOrSavingsAccount(account.getId(), customer.getId());

        //Reflect successful closure in the account object shown by the application.
        account.setStatus("CLOSED");
    }

}
