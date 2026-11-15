package com.revature.account;

import com.revature.account.investment.InvestmentAccount;
import com.revature.account.investment.InvestmentContribution;
import com.revature.customer.Customer;
import com.revature.util.ConnectionProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests database operations for bank accounts.
 */
public class AccountDAOTest {

    /**
     * Tests that inserting a checking account retrieves its generated identifiers.
     *
     * @throws Exception if a mocked database operation fails
     */
    @Test
    void insertAccountReturnsGeneratedIdentifiers() throws Exception {
        //Create mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        PreparedStatement customerLock = mock(PreparedStatement.class);
        ResultSet customerResult = mock(ResultSet.class);

        //Provide the mocked connection to AccountDAO.
        ConnectionProvider connectionProvider = () -> connection;
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Create a new checking account.
        CheckingAccount account = new CheckingAccount(customer);

        //Simulate PostgreSQL preparing and executing the INSERT.
        when(customerLock.executeQuery()).thenReturn(customerResult);
        when(customerResult.next()).thenReturn(true);
        when(customerResult.getString("status")).thenReturn("ACTIVE");
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.prepareStatement("SELECT status FROM customers WHERE customer_id = ? FOR UPDATE"))
                .thenReturn(customerLock);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Simulate PostgreSQL returning the generated account identifiers.
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("account_id")).thenReturn(1L);
        when(resultSet.getString("account_number")).thenReturn("100000");

        //Insert the account using the mocked database.
        long generatedId = accountDAO.insertAccount(account);

        //Check that PostgreSQL's generated values were assigned correctly.
        assertEquals(1L, generatedId);
        assertEquals(1L, account.getId());
        assertEquals("100000", account.getAccountNumber());

        //Verify that the correct account information was sent to JDBC.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setString(2, "CHECKING");
        verify(preparedStatement).setBigDecimal(3, new BigDecimal("0.00"));
        verify(preparedStatement).setString(4, "ACTIVE");

        //Verify that the INSERT was executed.
        verify(preparedStatement).executeQuery();
    }


    /**
     * Tests that the DAO retrieves accounts belonging to the requested customer.
     *
     * @throws Exception if a mocked database operation fails
     */
    @Test
    void findAccountsByCustomerReturnsCustomerAccounts() throws Exception {
        //Create mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        ConnectionProvider connectionProvider = () -> connection;
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Simulate the database returning one checking account.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("account_type")).thenReturn("CHECKING");
        when(resultSet.getLong("account_id")).thenReturn(5L);
        when(resultSet.getString("account_number")).thenReturn("100000");
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("250.00"));
        when(resultSet.getString("status")).thenReturn("ACTIVE");

        //Retrieve the customer's accounts.
        List<Account> accounts = accountDAO.findAccountsByCustomer(customer);

        //Verify the returned account information.
        assertEquals(1, accounts.size());
        assertInstanceOf(CheckingAccount.class, accounts.get(0));
        assertEquals(5L, accounts.get(0).getId());
        assertEquals("100000", accounts.get(0).getAccountNumber());
        assertEquals(new BigDecimal("250.00"), accounts.get(0).getBalance());

        //Verify that the query was restricted to the correct customer.
        verify(preparedStatement).setLong(1, 1L);
    }


    /**
     * Tests that investment account details are correctly retrieved from database results.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void findAccountsByCustomerLoadsInvestmentAccount() throws SQLException {
        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Mock the database connection, prepared statement, and result set.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        //Create a mock provider so the DAO uses our fake database connection.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Return one investment account row, followed by the end of the results.
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("account_type")).thenReturn("INVESTMENT");
        when(resultSet.getLong("account_id")).thenReturn(10L);
        when(resultSet.getString("account_number")).thenReturn("100010");
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("5000.00"));
        when(resultSet.getString("status")).thenReturn("ACTIVE");

        //Return the saved investment details.
        when(resultSet.getBigDecimal("investment_principal")).thenReturn(new BigDecimal("5000.00"));
        when(resultSet.getInt("investment_term_months")).thenReturn(12);
        when(resultSet.getBigDecimal("investment_interest_rate")).thenReturn(new BigDecimal("6.25"));
        when(resultSet.getDate("withdrawal_unlocked_date"))
                .thenReturn(Date.valueOf(LocalDate.of(2027, 11, 12)));

        //Retrieve the customer's accounts.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        List<Account> accounts = accountDAO.findAccountsByCustomer(customer);

        //Verify that exactly one investment account was returned.
        assertEquals(1, accounts.size());
        assertInstanceOf(InvestmentAccount.class, accounts.get(0));

        InvestmentAccount investmentAccount = (InvestmentAccount) accounts.get(0);

        //Verify the account information.
        assertEquals(10L, investmentAccount.getId());
        assertEquals("100010", investmentAccount.getAccountNumber());
        assertEquals(new BigDecimal("5000.00"), investmentAccount.getBalance());
        assertEquals("ACTIVE", investmentAccount.getStatus());

        //Verify the investment details.
        assertEquals(new BigDecimal("5000.00"), investmentAccount.getPrincipal());
        assertEquals(12, investmentAccount.getTermMonths());
        assertEquals(new BigDecimal("6.25"), investmentAccount.getInterestRate());
        assertEquals(LocalDate.of(2027, 11, 12), investmentAccount.getWithdrawalUnlockedDate());

        //Verify that the query was restricted to the correct customer.
        verify(preparedStatement).setLong(1, customer.getId());
    }


    /**
     * Tests that a Loan Account is reconstructed with its saved borrowing details.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void findAccountsByCustomerLoadsLoanAccount() throws SQLException {
        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Create mocked database objects instead of connecting to PostgreSQL.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Make the mocked connection return the mocked loan results.
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Return one Loan Account row.
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("account_type")).thenReturn("LOAN");
        when(resultSet.getLong("account_id")).thenReturn(11L);
        when(resultSet.getString("account_number")).thenReturn("100011");
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("10000.00"));
        when(resultSet.getString("status")).thenReturn("ACTIVE");

        //Return the loan details saved in PostgreSQL.
        when(resultSet.getBigDecimal("loan_principal")).thenReturn(new BigDecimal("10000.00"));
        when(resultSet.getBigDecimal("annual_interest_rate")).thenReturn(new BigDecimal("5.75"));
        when(resultSet.getInt("repayment_period_in_months")).thenReturn(36);
        when(resultSet.getDate("loan_start_date"))
                .thenReturn(Date.valueOf(LocalDate.of(2026, 11, 12)));

        //Retrieve the customer's accounts using the mocked connection.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        List<Account> accounts = accountDAO.findAccountsByCustomer(customer);

        //Verify that exactly one Loan Account was returned.
        assertEquals(1, accounts.size());
        assertInstanceOf(LoanAccount.class, accounts.get(0));

        LoanAccount loanAccount = (LoanAccount) accounts.get(0);

        //Verify the account's general information.
        assertEquals(11L, loanAccount.getId());
        assertEquals("100011", loanAccount.getAccountNumber());
        assertEquals(new BigDecimal("10000.00"), loanAccount.getBalance());
        assertEquals("ACTIVE", loanAccount.getStatus());

        //Verify the saved loan details.
        assertEquals(new BigDecimal("10000.00"), loanAccount.getPrincipalAmount());
        assertEquals(new BigDecimal("5.75"), loanAccount.getAnnualInterestRate());
        assertEquals(36, loanAccount.getRepaymentPeriodInMonths());
        assertEquals(LocalDate.of(2026, 11, 12), loanAccount.getLoanStartDate());

        //Verify that the query uses the correct customer ID.
        verify(preparedStatement).setLong(1, customer.getId());
    }


    /**
     * Tests that inserting an account with an existing connection
     * uses that connection without closing or committing it.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertAccountWithExistingConnectionDoesNotCloseOrCommitConnection() throws SQLException {
        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Create a Checking Account to insert.
        CheckingAccount account = new CheckingAccount(customer);

        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        PreparedStatement customerLock = mock(PreparedStatement.class);
        ResultSet customerResult = mock(ResultSet.class);

        //Make the provided connection return the mocked insert results.
        when(customerLock.executeQuery()).thenReturn(customerResult);
        when(customerResult.next()).thenReturn(true);
        when(customerResult.getString("status")).thenReturn("ACTIVE");
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.prepareStatement("SELECT status FROM customers WHERE customer_id = ? FOR UPDATE"))
                .thenReturn(customerLock);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("account_id")).thenReturn(12L);
        when(resultSet.getString("account_number")).thenReturn("100012");

        //Insert the account using the existing connection.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        long accountId = accountDAO.insertAccount(connection, account);

        //Verify that the account received its generated database information.
        assertEquals(12L, accountId);
        assertEquals(12L, account.getId());
        assertEquals("100012", account.getAccountNumber());

        //Verify that the correct account information was sent to PostgreSQL.
        verify(preparedStatement).setLong(1, customer.getId());
        verify(preparedStatement).setString(2, "CHECKING");
        verify(preparedStatement).setBigDecimal(3, new BigDecimal("0.00"));
        verify(preparedStatement).setString(4, "ACTIVE");

        //Verify that the DAO did not control the caller's transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();

        //Verify that the DAO still closes its own statement and result set.
        verify(preparedStatement).close();
        verify(resultSet).close();

        //Verify that the DAO did not request another connection.
        verify(connectionProvider, never()).getConnection();
    }


    /**
     * Tests that Investment Account details are inserted using an existing
     * database connection without committing or closing that connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentDetailsUsesExistingConnection() throws SQLException {
        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Create an Investment Account with a 12-month term and 6.25% annual interest.
        LocalDate withdrawalUnlockedDate = LocalDate.of(2027, 11, 12);
        InvestmentAccount investmentAccount = new InvestmentAccount(
                customer, new BigDecimal("5000.00"), 12,
                new BigDecimal("6.25"), withdrawalUnlockedDate);

        //Give the Investment Account an ID as though it was already inserted.
        investmentAccount.setId(15L);

        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        //Make the provided connection return our mocked prepared statement.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        //Save the Investment Account details using the existing connection.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        accountDAO.insertInvestmentDetails(connection, investmentAccount);

        //Verify that the correct investment details were sent to PostgreSQL.
        verify(preparedStatement).setLong(1, 15L);
        verify(preparedStatement).setBigDecimal(2, new BigDecimal("5000.00"));
        verify(preparedStatement).setInt(3, 12);
        verify(preparedStatement).setBigDecimal(4, new BigDecimal("6.25"));
        verify(preparedStatement).setDate(5, Date.valueOf(withdrawalUnlockedDate));

        //Verify that the details were inserted.
        verify(preparedStatement).executeUpdate();

        //Verify that the DAO did not control the caller's transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();

        //Verify that the DAO closes the prepared statement it created.
        verify(preparedStatement).close();

        //Verify that the DAO did not request a separate database connection.
        verify(connectionProvider, never()).getConnection();
    }


    /**
     * Tests that Loan Account details are inserted using an existing
     * database connection without committing or closing that connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertLoanDetailsUsesExistingConnection() throws SQLException {
        //Create a customer with an existing database ID.
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);

        //Create a Loan Account with 5.75% annual interest and a 36-month repayment period.
        LocalDate loanStartDate = LocalDate.of(2026, 11, 12);
        LoanAccount loanAccount = new LoanAccount(
                customer, new BigDecimal("10000.00"),
                new BigDecimal("5.75"), 36, loanStartDate);

        //Give the Loan Account an ID as though it was already inserted.
        loanAccount.setId(16L);

        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);

        //Make the provided connection return our mocked prepared statement.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        //Save the Loan Account details using the existing connection.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        accountDAO.insertLoanDetails(connection, loanAccount);

        //Verify that the correct loan details were sent to PostgreSQL.
        verify(preparedStatement).setLong(1, 16L);
        verify(preparedStatement).setBigDecimal(2, new BigDecimal("10000.00"));
        verify(preparedStatement).setBigDecimal(3, new BigDecimal("5.75"));
        verify(preparedStatement).setInt(4, 36);
        verify(preparedStatement).setDate(5, Date.valueOf(loanStartDate));

        //Verify that the loan details were inserted.
        verify(preparedStatement).executeUpdate();

        //Verify that the DAO did not control the caller's transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();

        //Verify that the DAO closes the prepared statement it created.
        verify(preparedStatement).close();

        //Verify that the DAO did not request a separate database connection.
        verify(connectionProvider, never()).getConnection();
    }

    /**
     * Tests that the DAO retrieves an account balance using both
     * the account ID and the owning customer's ID.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getAccountBalanceReturnsBalanceForCustomer() throws SQLException {
        //Create mocked database dependencies.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Configure the mocked database connection and query.
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Simulate PostgreSQL returning one account with a $250.75 balance.
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("250.75"));

        //Retrieve the account balance through the DAO.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        BigDecimal balance = accountDAO.getAccountBalance(1L, 1L);

        //Verify that the correct balance was returned.
        assertEquals(new BigDecimal("250.75"), balance);

        //Verify that the query restricts results to the account's owner.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).executeQuery();

        //Verify that all database resources are closed.
        verify(resultSet).close();
        verify(preparedStatement).close();
        verify(connection).close();
    }



    /**
     * Tests that the DAO rejects a balance request when the account
     * does not belong to the specified customer.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void getAccountBalanceRejectsAccountNotOwnedByCustomer() throws SQLException {
        //Create mocked database dependencies.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Configure the mocked database connection and query.
        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Simulate PostgreSQL finding no account matching both IDs.
        when(resultSet.next()).thenReturn(false);

        //Create the DAO using the mocked connection provider.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        //Request account 1's balance using customer 2's ID.
        SQLException exception = assertThrows(SQLException.class,
                () -> accountDAO.getAccountBalance(1L, 2L));

        //Verify that the DAO rejects the request.
        assertEquals("Account not found for this customer.", exception.getMessage());

        //Verify that both account ID and customer ID were supplied.
        verify(preparedStatement).setLong(1, 1L);
        verify(preparedStatement).setLong(2, 2L);
        verify(preparedStatement).executeQuery();

        //Verify that the DAO does not read a balance when no account is found.
        verify(resultSet, never()).getBigDecimal("balance");

        //Verify that all database resources are closed.
        verify(resultSet).close();
        verify(preparedStatement).close();
        verify(connection).close();
    }


    /**
     * Tests that depositing money updates an eligible account balance
     * using the existing database connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void depositReturnsUpdatedBalanceUsingExistingConnection() throws SQLException {
        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Configure the prepared statement and returned result.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        //Simulate PostgreSQL returning the updated balance after a deposit.
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("150.00"));

        //Deposit $50.00 into account 1 belonging to customer 1.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        BigDecimal updatedBalance = accountDAO.deposit(
                connection, 1L, 1L, new BigDecimal("50.00"));

        //Verify that the DAO returns the updated balance.
        assertEquals(new BigDecimal("150.00"), updatedBalance);

        //Verify that the correct deposit amount and account identifiers are used.
        verify(preparedStatement).setBigDecimal(1, new BigDecimal("50.00"));
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).executeQuery();

        //Verify that the DAO closes its own database resources.
        verify(resultSet).close();
        verify(preparedStatement).close();

        //Verify that the DAO does not control or close the caller's connection.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();

        //Verify that no separate connection was requested.
        verify(connectionProvider, never()).getConnection();
    }



    /**
     * Tests that withdrawing money updates an eligible account balance
     * using the existing database connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void withdrawReturnsUpdatedBalanceUsingExistingConnection() throws SQLException {
        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Configure the mocked withdrawal query.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("3400.00"));

        //Withdraw $100.00 from an account with a $3500.00 balance.
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        BigDecimal updatedBalance = accountDAO.withdraw(
                connection, 1L, 1L, new BigDecimal("100.00"));

        //Verify the returned balance.
        assertEquals(new BigDecimal("3400.00"), updatedBalance);

        //Verify that the correct withdrawal details were supplied.
        verify(preparedStatement).setBigDecimal(1, new BigDecimal("100.00"));
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).executeQuery();

        //Verify that the DAO closes its own resources.
        verify(resultSet).close();
        verify(preparedStatement).close();

        //Verify that the caller retains control of the transaction.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
        verify(connectionProvider, never()).getConnection();
    }

    /**
     * Tests that a withdrawal is rejected when PostgreSQL cannot
     * update the account because sufficient funds are unavailable.
     *
     * @throws SQLException if the expected database rejection occurs
     */
    @Test
    void withdrawRejectsInsufficientFunds() throws SQLException {
        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Simulate PostgreSQL returning no updated account.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        //Attempt to withdraw more money than the account contains.
        assertThrows(SQLException.class, () ->
                accountDAO.withdraw(connection, 1L, 1L, new BigDecimal("5000.00")));

        //Verify that the withdrawal was attempted using the correct IDs.
        verify(preparedStatement).setBigDecimal(1, new BigDecimal("5000.00"));
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).setLong(3, 1L);

        //Verify that the DAO did not commit or close the caller's connection.
        verify(connection, never()).commit();
        verify(connection, never()).close();

        //Verify that JDBC resources were closed.
        verify(resultSet).close();
        verify(preparedStatement).close();
    }


    /**
     * Tests that a transfer debit updates the source balance using
     * the provided database connection.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void debitTransferReturnsUpdatedSourceBalance() throws SQLException {
        //Prepare a mocked connection and the balance returned by PostgreSQL.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("3200.00"));

        //Debit $200 from an account that previously held $3400.
        BigDecimal updatedBalance = accountDAO.debitTransfer(
                connection, 1L, 1L, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("3200.00"), updatedBalance);
        verify(preparedStatement).setBigDecimal(1, new BigDecimal("200.00"));
        verify(preparedStatement).setLong(2, 1L);
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).setBigDecimal(4, new BigDecimal("200.00"));
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that a transfer debit rejects accounts without sufficient funds.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void debitTransferRejectsInsufficientFunds() throws SQLException {
        //PostgreSQL returns no updated row when the source cannot be debited.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertThrows(SQLException.class, () -> accountDAO.debitTransfer(
                connection, 1L, 1L, new BigDecimal("5000.00")));

        //The DAO must leave transaction rollback to the service.
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    /**
     * Tests that a transfer credit updates the destination balance
     * using the provided database connection.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void creditTransferReturnsUpdatedDestinationBalance() throws SQLException {
        //Prepare the destination account's updated balance.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("200.00"));

        BigDecimal updatedBalance = accountDAO.creditTransfer(
                connection, 2L, 1L, new BigDecimal("200.00"));

        assertEquals(new BigDecimal("200.00"), updatedBalance);
        verify(preparedStatement).setBigDecimal(1, new BigDecimal("200.00"));
        verify(preparedStatement).setLong(2, 2L);
        verify(preparedStatement).setLong(3, 1L);
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }


    /**
     * Tests that a partial loan repayment returns the remaining balance.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void repayLoanReturnsRemainingBalance() throws SQLException {
        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Simulate PostgreSQL returning the updated loan balance.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("450.00"));

        //Repay $100.00 toward a loan with $550.00 outstanding.
        BigDecimal remainingBalance = accountDAO.repayLoan(
                connection, 4L, 1L, new BigDecimal("100.00"));

        //Verify the remaining debt and SQL parameters.
        assertEquals(new BigDecimal("450.00"), remainingBalance);
        verify(preparedStatement).setBigDecimal(1, new BigDecimal("100.00"));
        verify(preparedStatement).setBigDecimal(2, new BigDecimal("100.00"));
        verify(preparedStatement).setLong(3, 4L);
        verify(preparedStatement).setLong(4, 1L);
        verify(preparedStatement).setBigDecimal(5, new BigDecimal("100.00"));

        //Verify that the DAO does not commit the caller's transaction.
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that a full loan repayment returns a zero balance.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void repayLoanReturnsZeroAfterFullRepayment() throws SQLException {
        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Simulate PostgreSQL returning a fully repaid loan.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance")).thenReturn(new BigDecimal("0.00"));

        //Repay the entire outstanding loan amount.
        BigDecimal remainingBalance = accountDAO.repayLoan(
                connection, 4L, 1L, new BigDecimal("550.00"));

        //Verify that the remaining debt is zero.
        assertEquals(new BigDecimal("0.00"), remainingBalance);

        //Verify that the SQL contains automatic loan closure logic.
        org.mockito.ArgumentCaptor<String> sqlCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("WHEN balance - ? = 0 THEN 'CLOSED'"));
        assertTrue(sqlCaptor.getValue().contains("account_type = 'LOAN'"));
        assertTrue(sqlCaptor.getValue().contains("status = 'ACTIVE'"));

        //Verify that transaction management remains with the caller.
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that an ineligible loan or overpayment is rejected.
     *
     * @throws SQLException if the mocked repayment is rejected
     */
    @Test
    void repayLoanRejectsIneligibleAccountOrOverpayment() throws SQLException {
        //Create mocked database objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        //Simulate an UPDATE that matches no eligible Loan Account.
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        //Verify that PostgreSQL's rejection is reported.
        SQLException exception = assertThrows(SQLException.class,
                () -> accountDAO.repayLoan(
                        connection, 4L, 1L, new BigDecimal("600.00")));

        assertTrue(exception.getMessage().contains("Loan repayment rejected"));

        //Verify that the amount is checked against the outstanding balance.
        verify(preparedStatement).setBigDecimal(5, new BigDecimal("600.00"));
        verify(connection, never()).commit();
    }

    /**
     * Tests that invalid loan repayment amounts are rejected before database access.
     */
    @Test
    void repayLoanRejectsInvalidAmounts() {
        //Create a DAO and mocked connection.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);

        //Reject zero, negative, and amounts with more than two decimal places.
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.repayLoan(connection, 4L, 1L, BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.repayLoan(connection, 4L, 1L, new BigDecimal("-10.00")));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.repayLoan(connection, 4L, 1L, new BigDecimal("10.001")));

        //Reject invalid account or customer IDs.
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.repayLoan(connection, 0L, 1L, new BigDecimal("10.00")));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.repayLoan(connection, 4L, 0L, new BigDecimal("10.00")));

        //Verify that invalid inputs never reach PostgreSQL.
        verifyNoInteractions(connection);
    }


    /**
     * Tests that an eligible Investment Account receives a contribution
     * using the caller's existing database connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void creditInvestmentContributionReturnsUpdatedBalance() throws SQLException {
        //Prepare mocked JDBC objects.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("balance"))
                .thenReturn(new BigDecimal("500.00"));

        //Credit a $500 contribution to the Investment Account.
        BigDecimal amount = new BigDecimal("500.00");

        BigDecimal updatedBalance = accountDAO.creditInvestmentContribution(
                connection, 3L, 1L, amount);

        assertEquals(new BigDecimal("500.00"), updatedBalance);

        //Verify the amount and account ownership parameters.
        verify(preparedStatement).setBigDecimal(1, amount);
        verify(preparedStatement).setLong(2, 3L);
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).setBigDecimal(4, amount);

        //The service, not the DAO, controls committing the transaction.
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that a contribution is rejected when PostgreSQL finds no
     * eligible Investment Account to update.
     *
     * @throws SQLException if the contribution is rejected
     */
    @Test
    void creditInvestmentContributionRejectsIneligibleAccount() throws SQLException {
        //Prepare a database result containing no updated account.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        //A closed, matured, incorrectly owned, or ineligible account is rejected.
        assertThrows(SQLException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 1L, new BigDecimal("500.00")));

        verify(preparedStatement).executeQuery();
        verify(connection, never()).commit();
    }

    /**
     * Tests that invalid Investment Account contributions are rejected
     * before any SQL statement is prepared.
     */
    @Test
    void creditInvestmentContributionRejectsInvalidAmounts() {
        //Create the DAO with mocked database dependencies.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);

        //Reject missing, zero, negative, and fractional-cent amounts.
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 1L, null));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 1L, BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 1L, new BigDecimal("-50.00")));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 1L, new BigDecimal("50.345")));

        //Reject contributions exceeding NUMERIC(19,2).
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 1L, new BigDecimal("100000000000000000.00")));

        verifyNoInteractions(connection);
    }

    /**
     * Tests that Investment Account contributions require valid
     * account and customer database IDs.
     */
    @Test
    void creditInvestmentContributionRejectsInvalidIds() {
        //Create the DAO with mocked database dependencies.
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        AccountDAO accountDAO = new AccountDAO(connectionProvider);
        Connection connection = mock(Connection.class);

        BigDecimal amount = new BigDecimal("100.00");

        //Reject missing or invalid database identifiers.
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 0L, 1L, amount));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, 3L, 0L, amount));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.creditInvestmentContribution(
                        connection, -1L, 1L, amount));

        verifyNoInteractions(connection);
    }


    /**
     * Tests that an Investment Account contribution is recorded
     * and PostgreSQL returns the generated contribution ID.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void insertInvestmentContributionReturnsGeneratedId() throws SQLException {
        //Prepare mocked JDBC objects.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        ConnectionProvider connectionProvider = () -> connection;
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("contribution_id")).thenReturn(1L);

        //Record a $500 contribution to Investment Account ID 3.
        BigDecimal amount = new BigDecimal("500.00");

        long contributionId = accountDAO.insertInvestmentContribution(
                connection, 3L, amount);

        assertEquals(1L, contributionId);

        //Verify the correct account and contribution amount were used.
        verify(preparedStatement).setLong(1, 3L);
        verify(preparedStatement).setBigDecimal(2, amount);
        verify(preparedStatement).executeQuery();

        //The service will control the database transaction.
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that contribution history insertion fails when PostgreSQL
     * does not return a generated contribution record.
     *
     * @throws SQLException if the insert fails
     */
    @Test
    void insertInvestmentContributionRejectsMissingResult() throws SQLException {
        //Prepare a database result with no generated contribution ID.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        ConnectionProvider connectionProvider = () -> connection;
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        //A missing result must cause the transaction to fail.
        assertThrows(SQLException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, new BigDecimal("500.00")));

        verify(connection, never()).commit();
    }

    /**
     * Tests that PostgreSQL must return a positive contribution ID.
     *
     * @throws SQLException if the generated ID is invalid
     */
    @Test
    void insertInvestmentContributionRejectsInvalidGeneratedId() throws SQLException {
        //Prepare a database result containing an invalid ID.
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        ConnectionProvider connectionProvider = () -> connection;
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("contribution_id")).thenReturn(0L);

        //A contribution record must have a valid generated identifier.
        assertThrows(SQLException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, new BigDecimal("500.00")));

        verify(connection, never()).commit();
    }

    /**
     * Tests that invalid contribution IDs and amounts are rejected
     * before preparing a database statement.
     */
    @Test
    void insertInvestmentContributionRejectsInvalidInputs() {
        //Create the DAO with a mocked connection.
        Connection connection = mock(Connection.class);
        ConnectionProvider connectionProvider = () -> connection;
        AccountDAO accountDAO = new AccountDAO(connectionProvider);

        //Reject invalid Investment Account IDs.
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 0L, new BigDecimal("100.00")));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, -1L, new BigDecimal("100.00")));

        //Reject missing, zero, negative, and fractional-cent amounts.
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, null));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, new BigDecimal("-25.00")));

        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, new BigDecimal("25.345")));

        //Reject amounts exceeding NUMERIC(19,2).
        assertThrows(IllegalArgumentException.class,
                () -> accountDAO.insertInvestmentContribution(
                        connection, 3L, new BigDecimal("100000000000000000.00")));

        verifyNoInteractions(connection);
    }


    /**
     * Tests that the DAO retrieves contributions in the database-provided order.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void findInvestmentContributionsReturnsContributions() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        AccountDAO dao = new AccountDAO(() -> connection);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true, true, false);
        OffsetDateTime firstDate = OffsetDateTime.parse("2026-11-13T23:43:29-05:00");
        OffsetDateTime secondDate = OffsetDateTime.parse("2026-12-01T09:00:00-05:00");
        when(results.getBigDecimal("amount"))
                .thenReturn(new BigDecimal("650.00"), new BigDecimal("200.00"));
        when(results.getObject("contributed_at", OffsetDateTime.class))
                .thenReturn(firstDate, secondDate);

        List<InvestmentContribution> contributions = dao.findInvestmentContributions(3L, 1L);

        assertEquals(2, contributions.size());
        assertEquals(new BigDecimal("650.00"), contributions.get(0).amount());
        assertEquals(firstDate, contributions.get(0).contributedAt());
        assertEquals(new BigDecimal("200.00"), contributions.get(1).amount());
        assertEquals(secondDate, contributions.get(1).contributedAt());
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 1L);
        verify(connection).close();
        verify(statement).close();
        verify(results).close();
    }

    /**
     * Tests that a valid Investment Account without contributions returns an empty list.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void findInvestmentContributionsReturnsEmptyListForUnfundedAccount() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        AccountDAO dao = new AccountDAO(() -> connection);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true, false);

        assertTrue(dao.findInvestmentContributions(3L, 1L).isEmpty());
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 1L);
    }

    /**
     * Tests that a missing or differently owned Investment Account is rejected.
     *
     * @throws SQLException if the account cannot be found
     */
    @Test
    void findInvestmentContributionsRejectsUnownedAccount() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        AccountDAO dao = new AccountDAO(() -> connection);

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(false);

        SQLException exception = assertThrows(SQLException.class,
                () -> dao.findInvestmentContributions(3L, 2L));
        assertEquals("Investment Account not found for this customer.", exception.getMessage());
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 2L);
    }

    /**
     * Tests that invalid identifiers are rejected before a database connection is opened.
     */
    @Test
    void findInvestmentContributionsRejectsInvalidIds() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        AccountDAO dao = new AccountDAO(provider);

        assertThrows(IllegalArgumentException.class,
                () -> dao.findInvestmentContributions(0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findInvestmentContributions(3L, -1L));
        verifyNoInteractions(provider);
    }



    /**
     * Tests that maturity release returns the previous invested balance and uses the caller's connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void releaseMaturedInvestmentReturnsContributedBalance() throws SQLException {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true);
        when(results.getBigDecimal("released_balance")).thenReturn(new BigDecimal("650.00"));

        BigDecimal released = new AccountDAO(provider).releaseMaturedInvestment(connection, 3L, 1L);

        assertEquals(new BigDecimal("650.00"), released);
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 1L);
        verify(statement).close();
        verify(results).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
        verifyNoInteractions(provider);
    }

    /**
     * Tests that PostgreSQL rejecting an early, closed, or unowned investment prevents release.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void releaseMaturedInvestmentRejectsIneligibleAccount() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(false);

        assertThrows(SQLException.class,
                () -> new AccountDAO(() -> connection).releaseMaturedInvestment(connection, 3L, 1L));
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    /**
     * Tests that a maturity payout credits an eligible destination in the same transaction.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void creditInvestmentMaturityPayoutReturnsUpdatedBalance() throws SQLException {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true);
        when(results.getBigDecimal("balance")).thenReturn(new BigDecimal("2682.50"));

        BigDecimal balance = new AccountDAO(provider).creditInvestmentMaturityPayout(
                connection, 2L, 1L, new BigDecimal("682.50"));

        assertEquals(new BigDecimal("2682.50"), balance);
        verify(statement).setBigDecimal(1, new BigDecimal("682.50"));
        verify(statement).setLong(2, 2L);
        verify(statement).setLong(3, 1L);
        verify(statement).setBigDecimal(4, new BigDecimal("682.50"));
        verify(statement).close();
        verify(results).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
        verifyNoInteractions(provider);
    }

    /**
     * Tests that a closed, unowned, or ineligible destination cannot receive maturity funds.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void creditInvestmentMaturityPayoutRejectsIneligibleDestination() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(false);

        assertThrows(SQLException.class, () -> new AccountDAO(() -> connection)
                .creditInvestmentMaturityPayout(connection, 2L, 1L, new BigDecimal("100.00")));
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that invalid release identifiers and payout amounts fail before SQL executes.
     */
    @Test
    void maturityOperationsRejectInvalidArguments() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        AccountDAO dao = new AccountDAO(provider);

        assertThrows(IllegalArgumentException.class,
                () -> dao.releaseMaturedInvestment(connection, 0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> dao.releaseMaturedInvestment(connection, 3L, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> dao.creditInvestmentMaturityPayout(connection, 2L, 1L, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> dao.creditInvestmentMaturityPayout(connection, 2L, 1L, new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> dao.creditInvestmentMaturityPayout(connection, 0L, 1L, new BigDecimal("1.00")));
        verifyNoInteractions(connection, provider);
    }


    /**
     * Tests that transaction-scoped contribution retrieval uses the supplied connection.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void findInvestmentContributionsWithConnectionReturnsSavedRecords() throws SQLException {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        AccountDAO dao = new AccountDAO(provider);
        OffsetDateTime date = OffsetDateTime.parse("2026-11-13T23:43:29-05:00");

        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true, false);
        when(results.getBigDecimal("amount")).thenReturn(new BigDecimal("650.00"));
        when(results.getObject("contributed_at", OffsetDateTime.class)).thenReturn(date);

        List<InvestmentContribution> contributions = dao.findInvestmentContributions(connection, 3L, 1L);

        assertEquals(1, contributions.size());
        assertEquals(new BigDecimal("650.00"), contributions.get(0).amount());
        assertEquals(date, contributions.get(0).contributedAt());
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 1L);
        verify(statement).close();
        verify(results).close();
        verify(connection, never()).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verifyNoInteractions(provider);
    }

    /**
     * Tests that an unfunded investment produces no contribution records.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void findInvestmentContributionsWithConnectionReturnsEmptyList() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true, false);

        AccountDAO dao = new AccountDAO(mock(ConnectionProvider.class));
        assertTrue(dao.findInvestmentContributions(connection, 3L, 1L).isEmpty());
        verify(connection, never()).close();
    }

    /**
     * Tests that the transaction-scoped retrieval rejects an unowned account.
     *
     * @throws SQLException if the account cannot be found
     */
    @Test
    void findInvestmentContributionsWithConnectionRejectsUnownedAccount() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(false);

        AccountDAO dao = new AccountDAO(mock(ConnectionProvider.class));
        SQLException exception = assertThrows(SQLException.class,
                () -> dao.findInvestmentContributions(connection, 3L, 2L));
        assertEquals("Investment Account not found for this customer.", exception.getMessage());
        verify(statement).setLong(1, 3L);
        verify(statement).setLong(2, 2L);
        verify(connection, never()).close();
    }

    /**
     * Tests that invalid transaction-scoped retrieval arguments are rejected before JDBC access.
     */
    @Test
    void findInvestmentContributionsWithConnectionRejectsInvalidArguments() {
        Connection connection = mock(Connection.class);
        ConnectionProvider provider = mock(ConnectionProvider.class);
        AccountDAO dao = new AccountDAO(provider);

        assertThrows(IllegalArgumentException.class,
                () -> dao.findInvestmentContributions(null, 3L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findInvestmentContributions(connection, 0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findInvestmentContributions(connection, 3L, -1L));
        verifyNoInteractions(connection, provider);
    }

    /**
     * Tests that an eligible zero-balance account is closed by a single guarded update.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void closeCheckingOrSavingsAccountSucceeds() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(true);

        AccountDAO dao = new AccountDAO(() -> connection);
        dao.closeCheckingOrSavingsAccount(2L, 1L);

        verify(statement).setLong(1, 2L);
        verify(statement).setLong(2, 1L);
        verify(statement).executeQuery();
        verify(connection).close();
        verify(statement).close();
        verify(results).close();
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("account_type IN ('CHECKING', 'SAVINGS')"));
        assertTrue(sql.getValue().contains("status = 'ACTIVE'"));
        assertTrue(sql.getValue().contains("balance = 0.00"));
        assertTrue(sql.getValue().contains("customer_id = ?"));
    }

    /**
     * Tests that an ineligible account cannot be closed.
     *
     * @throws SQLException if the mocked database rejects the closure
     */
    @Test
    void closeCheckingOrSavingsAccountRejectsIneligibleAccount() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        when(results.next()).thenReturn(false);

        AccountDAO dao = new AccountDAO(() -> connection);
        SQLException exception = assertThrows(SQLException.class,
                () -> dao.closeCheckingOrSavingsAccount(2L, 1L));
        assertTrue(exception.getMessage().contains("Account closure rejected"));
        verify(connection).close();
    }

    /**
     * Tests that invalid IDs are rejected without opening a database connection.
     */
    @Test
    void closeCheckingOrSavingsAccountRejectsInvalidIds() {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        AccountDAO dao = new AccountDAO(provider);

        assertThrows(IllegalArgumentException.class,
                () -> dao.closeCheckingOrSavingsAccount(0L, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> dao.closeCheckingOrSavingsAccount(2L, -1L));
        verifyNoInteractions(provider);
    }

    /**
     * Tests that JDBC errors propagate to the caller instead of being hidden.
     *
     * @throws SQLException if the mocked database operation fails
     */
    @Test
    void closeCheckingOrSavingsAccountPropagatesDatabaseError() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("database unavailable"));
        AccountDAO dao = new AccountDAO(() -> connection);

        SQLException exception = assertThrows(SQLException.class,
                () -> dao.closeCheckingOrSavingsAccount(2L, 1L));
        assertEquals("database unavailable", exception.getMessage());
        verify(connection).close();
    }

    /**
     * Rejects a closed customer using the database status, not a cached customer object.
     */
    @Test
    void insertAccountRejectsClosedCustomerBeforeInsert() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT status FROM customers WHERE customer_id = ? FOR UPDATE"))
                .thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString("status")).thenReturn("CLOSED");
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer", "unused-test-hash");
        customer.setId(3L);
        AccountDAO dao = new AccountDAO(() -> connection);

        assertThrows(IllegalStateException.class, () -> dao.insertAccount(new CheckingAccount(customer)));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection, never()).prepareStatement(org.mockito.ArgumentMatchers.contains("INSERT INTO accounts"));
    }

    /**
     * Rejects a missing customer instead of attempting to create an orphaned account.
     */
    @Test
    void insertAccountRejectsMissingCustomer() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT status FROM customers WHERE customer_id = ? FOR UPDATE"))
                .thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer", "unused-test-hash");
        customer.setId(3L);
        AccountDAO dao = new AccountDAO(() -> connection);

        assertThrows(IllegalStateException.class, () -> dao.insertAccount(new CheckingAccount(customer)));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    /**
     * Checks customer status before inserting within an existing transaction.
     */
    @Test
    void existingTransactionChecksCustomerStatusWithoutCommitting() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT status FROM customers WHERE customer_id = ? FOR UPDATE"))
                .thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString("status")).thenReturn("CLOSED");
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer", "unused-test-hash");
        customer.setId(3L);
        AccountDAO dao = new AccountDAO(() -> connection);

        assertThrows(IllegalStateException.class,
                () -> dao.insertAccount(connection, new CheckingAccount(customer)));
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
        verify(lock).setLong(1, 3L);
    }

    /**
     * Rolls back when an account insert fails after obtaining the customer lock.
     */
    @Test
    void insertAccountRollsBackOnInsertFailure() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(insert);
        when(connection.prepareStatement("SELECT status FROM customers WHERE customer_id = ? FOR UPDATE"))
                .thenReturn(lock);
        when(lock.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString("status")).thenReturn("ACTIVE");
        when(insert.executeQuery()).thenThrow(new SQLException("Insert failed"));
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer", "unused-test-hash");
        customer.setId(3L);
        AccountDAO dao = new AccountDAO(() -> connection);

        assertThrows(SQLException.class, () -> dao.insertAccount(new CheckingAccount(customer)));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

}
