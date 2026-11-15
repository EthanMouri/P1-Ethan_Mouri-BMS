package com.revature.account;

import com.revature.account.investment.InvestmentAccount;
import com.revature.customer.Customer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import com.revature.util.ConnectionProvider;
import org.mockito.InOrder;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests business rules for opening bank accounts.
 */
public class AccountServiceTest {

    /**
     * Tests that an active customer can open a checking account.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void activeCustomerCanOpenCheckingAccount() throws SQLException {
        //Create a mock DAO so this test does not access PostgreSQL.
        AccountDAO accountDAO = mock(AccountDAO.class);
        AccountService accountService = new AccountService(accountDAO);

        //Create a customer representing an existing active customer.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Simulate PostgreSQL assigning the generated account identifiers.
        when(accountDAO.insertAccount(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(1L);
            account.setAccountNumber("100000");
            return 1L;
        });

        //Open the checking account.
        CheckingAccount account = accountService.openCheckingAccount(customer);

        //Verify the newly created account.
        assertEquals(1L, account.getId());
        assertEquals("100000", account.getAccountNumber());
        assertEquals("CHECKING", account.getAccountType());
        assertEquals(customer, account.getAccountOwnerName());

        //Verify that the DAO was called exactly once.
        verify(accountDAO, times(1)).insertAccount(account);
    }


    /**
     * Tests that an active customer can open a savings account.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void activeCustomerCanOpenSavingsAccount() throws SQLException {
        //Create a mock DAO so this test does not access PostgreSQL.
        AccountDAO accountDAO = mock(AccountDAO.class);
        AccountService accountService = new AccountService(accountDAO);

        //Create a customer representing an existing active customer.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Simulate PostgreSQL assigning the generated account identifiers.
        when(accountDAO.insertAccount(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(2L);
            account.setAccountNumber("100001");
            return 2L;
        });

        //Open the savings account.
        SavingsAccount account = accountService.openSavingsAccount(customer);

        //Verify the newly created account.
        assertEquals(2L, account.getId());
        assertEquals("100001", account.getAccountNumber());
        assertEquals("SAVINGS", account.getAccountType());
        assertEquals(new BigDecimal("3.75"), account.getInterestRate());
        assertEquals(customer, account.getAccountOwnerName());

        //Verify that the DAO was called exactly once.
        verify(accountDAO, times(1)).insertAccount(account);
    }


    /**
     * Tests that opening an Investment Account saves both database records
     * and commits the transaction after both inserts succeed.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void openInvestmentAccountCommitsBothInserts() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create mocked dependencies for the service.
        AccountDAO accountDAO = mock(AccountDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Make the connection provider return our mocked connection.
        when(connectionProvider.getConnection()).thenReturn(connection);

        //Simulate PostgreSQL assigning an account ID during insertion.
        when(accountDAO.insertAccount(any(Connection.class), any(InvestmentAccount.class)))
                .thenAnswer(invocation -> {
                    InvestmentAccount account = invocation.getArgument(1);
                    account.setId(17L);
                    account.setAccountNumber("100017");
                    return 17L;
                });

        //Open an Investment Account using the mocked dependencies.
        AccountService accountService = new AccountService(accountDAO, connectionProvider);
        LocalDate dateBeforeOpening = LocalDate.now();

        InvestmentAccount investmentAccount = accountService.openInvestmentAccount(
                customer, new BigDecimal("5000.00"), 12);

        LocalDate dateAfterOpening = LocalDate.now();

        //Verify the newly created Investment Account.
        assertEquals(17L, investmentAccount.getId());
        assertEquals("100017", investmentAccount.getAccountNumber());
        assertEquals("INVESTMENT", investmentAccount.getAccountType());
        assertEquals(new BigDecimal("5000.00"), investmentAccount.getPrincipal());
        assertEquals(12, investmentAccount.getTermMonths());
        assertEquals(new BigDecimal("0.00"), investmentAccount.getBalance());

        //Verify that the randomly assigned interest rate is within the allowed range.
        assertTrue(investmentAccount.getInterestRate().compareTo(new BigDecimal("4.00")) >= 0);
        assertTrue(investmentAccount.getInterestRate().compareTo(new BigDecimal("10.00")) <= 0);

        //Verify that the withdrawal unlock date is calculated from the investment term.
        LocalDate withdrawalUnlockedDate = investmentAccount.getWithdrawalUnlockedDate();
        assertFalse(withdrawalUnlockedDate.isBefore(dateBeforeOpening.plusMonths(12)));
        assertFalse(withdrawalUnlockedDate.isAfter(dateAfterOpening.plusMonths(12)));

        //Verify that the same connection is used for both inserts.
        verify(accountDAO).insertAccount(same(connection), same(investmentAccount));
        verify(accountDAO).insertInvestmentDetails(same(connection), same(investmentAccount));

        //Verify that the service starts and commits the transaction.
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection, never()).rollback();

        //Verify that the service closes the connection after finishing.
        verify(connection).close();

        //Verify that the inserts happen before the transaction commits.
        InOrder insertionOrder = inOrder(accountDAO, connection);
        insertionOrder.verify(accountDAO).insertAccount(same(connection), same(investmentAccount));
        insertionOrder.verify(accountDAO).insertInvestmentDetails(same(connection), same(investmentAccount));
        insertionOrder.verify(connection).commit();
    }


    /**
     * Tests that opening an Investment Account rolls back the transaction
     * when saving its investment details fails.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void openInvestmentAccountRollsBackWhenDetailsInsertFails() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Return the same connection for the entire transaction.
        when(connectionProvider.getConnection()).thenReturn(connection);

        //Simulate PostgreSQL successfully inserting the general account record.
        when(accountDAO.insertAccount(any(Connection.class), any(InvestmentAccount.class)))
                .thenAnswer(invocation -> {
                    InvestmentAccount account = invocation.getArgument(1);
                    account.setId(18L);
                    account.setAccountNumber("100018");
                    return 18L;
                });

        //Simulate a database failure when inserting the investment details.
        doThrow(new SQLException("Simulated investment details insert failure"))
                .when(accountDAO)
                .insertInvestmentDetails(any(Connection.class), any(InvestmentAccount.class));

        //Create the service with mocked dependencies.
        AccountService accountService = new AccountService(accountDAO, connectionProvider);

        //Verify that the database failure is propagated to the caller.
        SQLException exception = assertThrows(SQLException.class,
                () -> accountService.openInvestmentAccount(
                        customer, new BigDecimal("5000.00"), 12));

        assertEquals("Simulated investment details insert failure", exception.getMessage());

        //Verify that the service attempted both inserts using the same connection.
        verify(accountDAO).insertAccount(same(connection), any(InvestmentAccount.class));
        verify(accountDAO).insertInvestmentDetails(same(connection), any(InvestmentAccount.class));

        //Verify that the transaction is rolled back instead of committed.
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();

        //Verify that the database connection is closed afterward.
        verify(connection).close();

        //Verify that the rollback happens after the failed details insert.
        InOrder operationOrder = inOrder(accountDAO, connection);
        operationOrder.verify(accountDAO)
                .insertAccount(same(connection), any(InvestmentAccount.class));
        operationOrder.verify(accountDAO)
                .insertInvestmentDetails(same(connection), any(InvestmentAccount.class));
        operationOrder.verify(connection).rollback();
    }


    /**
     * Tests that opening a Loan Account saves both database records
     * and commits the transaction after both inserts succeed.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void openLoanAccountCommitsBothInserts() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create mocked dependencies for the service.
        AccountDAO accountDAO = mock(AccountDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Make the connection provider return our mocked connection.
        when(connectionProvider.getConnection()).thenReturn(connection);

        //Simulate PostgreSQL assigning an account ID and account number.
        doAnswer(invocation -> {
            LoanAccount account = invocation.getArgument(1);
            account.setId(19L);
            account.setAccountNumber("100019");
            return null;
        }).when(accountDAO).insertAccount(any(Connection.class), any(LoanAccount.class));

        //Create the service with mocked dependencies.
        AccountService accountService = new AccountService(accountDAO, connectionProvider);
        LocalDate dateBeforeOpening = LocalDate.now();

        //Open a Loan Account with a $10,000 principal and 36-month repayment period.
        LoanAccount loanAccount = accountService.openLoanAccount(
                customer, new BigDecimal("10000.00"), 36);

        LocalDate dateAfterOpening = LocalDate.now();

        //Verify the newly created Loan Account.
        assertEquals(19L, loanAccount.getId());
        assertEquals("100019", loanAccount.getAccountNumber());
        assertEquals("LOAN", loanAccount.getAccountType());
        assertEquals(new BigDecimal("10000.00"), loanAccount.getPrincipalAmount());
        assertEquals(36, loanAccount.getRepaymentPeriodInMonths());
        //Verify that the initial loan balance represents the outstanding principal.
        assertEquals(new BigDecimal("10000.00"), loanAccount.getBalance());

        //Verify that the annual interest rate is within the allowed range.
        assertTrue(loanAccount.getAnnualInterestRate().compareTo(new BigDecimal("3.00")) >= 0);
        assertTrue(loanAccount.getAnnualInterestRate().compareTo(new BigDecimal("9.00")) <= 0);

        //Verify that the loan start date is today's date.
        assertFalse(loanAccount.getLoanStartDate().isBefore(dateBeforeOpening));
        assertFalse(loanAccount.getLoanStartDate().isAfter(dateAfterOpening));

        //Verify that both inserts use the same connection and account.
        verify(accountDAO).insertAccount(same(connection), same(loanAccount));
        verify(accountDAO).insertLoanDetails(same(connection), same(loanAccount));

        //Verify that the transaction is committed without a rollback.
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection, never()).rollback();

        //Verify that the connection is closed afterward.
        verify(connection).close();

        //Verify that the account and loan details are inserted before committing.
        InOrder operationOrder = inOrder(accountDAO, connection);
        operationOrder.verify(accountDAO).insertAccount(same(connection), same(loanAccount));
        operationOrder.verify(accountDAO).insertLoanDetails(same(connection), same(loanAccount));
        operationOrder.verify(connection).commit();
    }


    /**
     * Tests that opening a Loan Account rolls back the transaction
     * when saving its loan details fails.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void openLoanAccountRollsBackWhenDetailsInsertFails() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create mocked database dependencies.
        AccountDAO accountDAO = mock(AccountDAO.class);
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);

        //Return the same connection for the entire transaction.
        when(connectionProvider.getConnection()).thenReturn(connection);

        //Simulate PostgreSQL successfully inserting the general account record.
        doAnswer(invocation -> {
            LoanAccount account = invocation.getArgument(1);
            account.setId(20L);
            account.setAccountNumber("100020");
            return null;
        }).when(accountDAO).insertAccount(any(Connection.class), any(LoanAccount.class));

        //Simulate a database failure when inserting the loan details.
        doThrow(new SQLException("Simulated loan details insert failure"))
                .when(accountDAO)
                .insertLoanDetails(any(Connection.class), any(LoanAccount.class));

        //Create the service with mocked dependencies.
        AccountService accountService = new AccountService(accountDAO, connectionProvider);

        //Verify that the database failure is propagated to the caller.
        SQLException exception = assertThrows(SQLException.class,
                () -> accountService.openLoanAccount(
                        customer, new BigDecimal("10000.00"), 36));

        assertEquals("Simulated loan details insert failure", exception.getMessage());

        //Verify that both inserts were attempted using the same connection.
        verify(accountDAO).insertAccount(same(connection), any(LoanAccount.class));
        verify(accountDAO).insertLoanDetails(same(connection), any(LoanAccount.class));

        //Verify that the service rolls back instead of committing.
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();

        //Verify that the connection is closed afterward.
        verify(connection).close();

        //Verify that rollback happens after the failed loan details insert.
        InOrder operationOrder = inOrder(accountDAO, connection);
        operationOrder.verify(accountDAO)
                .insertAccount(same(connection), any(LoanAccount.class));
        operationOrder.verify(accountDAO)
                .insertLoanDetails(same(connection), any(LoanAccount.class));
        operationOrder.verify(connection).rollback();
    }


    /**
     * Tests that the service retrieves all accounts belonging to
     * an active customer through the AccountDAO.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getAccountsForCustomerReturnsCustomerAccounts() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create two accounts belonging to the customer.
        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(21L);
        checkingAccount.setAccountNumber("100021");

        SavingsAccount savingsAccount = new SavingsAccount(customer, new BigDecimal("3.75"));
        savingsAccount.setId(22L);
        savingsAccount.setAccountNumber("100022");

        //Create a list representing the accounts returned by PostgreSQL.
        List<Account> expectedAccounts = List.of(checkingAccount, savingsAccount);

        //Create a mocked DAO and configure its response.
        AccountDAO accountDAO = mock(AccountDAO.class);
        when(accountDAO.findAccountsByCustomer(customer)).thenReturn(expectedAccounts);

        //Create the service using the mocked DAO.
        AccountService accountService = new AccountService(accountDAO);

        //Retrieve the customer's accounts.
        List<Account> actualAccounts = accountService.getAccountsForCustomer(customer);

        //Verify that the service returns the accounts supplied by the DAO.
        assertEquals(2, actualAccounts.size());
        assertSame(checkingAccount, actualAccounts.get(0));
        assertSame(savingsAccount, actualAccounts.get(1));

        //Verify that the DAO was called for the correct customer.
        verify(accountDAO).findAccountsByCustomer(same(customer));
    }


    /**
     * Tests that the service retrieves an account's current balance
     * using the correct account ID and customer ID.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getAccountBalanceReturnsBalanceForCustomer() throws SQLException {
        //Create an active customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");

        //Create a Checking Account with an existing database ID.
        CheckingAccount checkingAccount = new CheckingAccount(customer);
        checkingAccount.setId(1L);
        checkingAccount.setAccountNumber("100000");

        //Create a mocked DAO that returns the account's current database balance.
        AccountDAO accountDAO = mock(AccountDAO.class);
        when(accountDAO.getAccountBalance(1L, 1L))
                .thenReturn(new BigDecimal("250.75"));

        //Create the service using the mocked DAO.
        AccountService accountService = new AccountService(accountDAO);

        //Retrieve the current balance through the service.
        BigDecimal balance = accountService.getAccountBalance(customer, checkingAccount);

        //Verify that the balance matches the value returned by PostgreSQL.
        assertEquals(new BigDecimal("250.75"), balance);

        //Verify that the DAO received the correct account ID and customer ID.
        verify(accountDAO).getAccountBalance(1L, 1L);
    }



    /**
     * Tests that closing an owned active Checking Account delegates to the DAO
     * and updates the in-memory account status after database success.
     *
     * @throws SQLException if the mocked DAO fails
     */
    @Test
    void closeCheckingAccountSucceeds() throws SQLException {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        CheckingAccount account = new CheckingAccount(customer);
        account.setId(10L);
        AccountDAO accountDAO = mock(AccountDAO.class);

        new AccountService(accountDAO).closeCheckingOrSavingsAccount(customer, account);

        verify(accountDAO).closeCheckingOrSavingsAccount(10L, 1L);
        assertEquals("CLOSED", account.getStatus());
    }

    /**
     * Tests that a customer cannot close an account owned by someone else.
     *
     * @throws SQLException if an unexpected database operation fails
     */
    @Test
    void closeAccountRejectsDifferentOwner() throws SQLException {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        Customer other = new Customer("Other", "Customer", "other@example.com",
                "1234567891", "othercustomer", "unused-test-hash");
        other.setId(2L);
        CheckingAccount account = new CheckingAccount(other);
        account.setId(11L);
        AccountDAO accountDAO = mock(AccountDAO.class);

        assertThrows(IllegalArgumentException.class,
                () -> new AccountService(accountDAO).closeCheckingOrSavingsAccount(customer, account));
        verifyNoInteractions(accountDAO);
    }

    /**
     * Tests that Investment Accounts cannot use Checking/Savings closure.
     *
     * @throws SQLException if an unexpected database operation fails
     */
    @Test
    void closeAccountRejectsInvestmentAccount() throws SQLException {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        InvestmentAccount account = mock(InvestmentAccount.class);
        when(account.getId()).thenReturn(12L);
        when(account.getAccountOwnerName()).thenReturn(customer);
        AccountDAO accountDAO = mock(AccountDAO.class);

        assertThrows(IllegalArgumentException.class,
                () -> new AccountService(accountDAO).closeCheckingOrSavingsAccount(customer, account));
        verifyNoInteractions(accountDAO);
    }

    /**
     * Tests that a rejected database closure does not change account status.
     *
     * @throws SQLException when the mocked DAO rejects closure
     */
    @Test
    void closeAccountKeepsStatusWhenDatabaseRejects() throws SQLException {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        customer.setStatus("ACTIVE");
        SavingsAccount account = new SavingsAccount(customer, new BigDecimal("3.75"));
        account.setId(13L);
        AccountDAO accountDAO = mock(AccountDAO.class);
        doThrow(new SQLException("Account has a nonzero balance"))
                .when(accountDAO).closeCheckingOrSavingsAccount(13L, 1L);

        assertThrows(SQLException.class,
                () -> new AccountService(accountDAO).closeCheckingOrSavingsAccount(customer, account));
        assertEquals("ACTIVE", account.getStatus());
    }

}
