package com.revature.account.investment;

import com.revature.account.AccountDAO;
import com.revature.customer.Customer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests total investment interest calculations using mocked contribution records.
 */
public class InvestmentInterestServiceTest {

    /**
     * Tests that separate contributions accrue interest for different numbers of days.
     *
     * @throws SQLException if the mocked DAO fails
     */
    @Test
    void calculateTotalInterestAddsInterestFromEachContributionDate() throws SQLException {
        AccountDAO accountDAO = mock(AccountDAO.class);
        InvestmentAccount account = createSavedInvestmentAccount();
        when(accountDAO.findInvestmentContributions(3L, 1L)).thenReturn(List.of(
                new InvestmentContribution(new BigDecimal("650.00"),
                        OffsetDateTime.parse("2026-01-01T10:00:00-05:00")),
                new InvestmentContribution(new BigDecimal("200.00"),
                        OffsetDateTime.parse("2026-07-01T10:00:00-04:00"))));

        BigDecimal interest = new InvestmentInterestService(accountDAO).calculateTotalInterest(account);

        //365 days on $650 earns $32.50; 184 days on $200 earns $5.04.
        assertEquals(new BigDecimal("37.54"), interest);
        verify(accountDAO).findInvestmentContributions(3L, 1L);
    }

    /**
     * Tests that an unfunded investment earns no interest.
     *
     * @throws SQLException if the mocked DAO fails
     */
    @Test
    void calculateTotalInterestReturnsZeroForNoContributions() throws SQLException {
        AccountDAO accountDAO = mock(AccountDAO.class);
        InvestmentAccount account = createSavedInvestmentAccount();
        when(accountDAO.findInvestmentContributions(3L, 1L)).thenReturn(List.of());

        assertEquals(new BigDecimal("0.00"),
                new InvestmentInterestService(accountDAO).calculateTotalInterest(account));
    }

    /**
     * Tests that a contribution made on the unlock date earns no interest.
     *
     * @throws SQLException if the mocked DAO fails
     */
    @Test
    void calculateTotalInterestReturnsZeroForSameDayContribution() throws SQLException {
        AccountDAO accountDAO = mock(AccountDAO.class);
        InvestmentAccount account = createSavedInvestmentAccount();
        when(accountDAO.findInvestmentContributions(3L, 1L)).thenReturn(List.of(
                new InvestmentContribution(new BigDecimal("650.00"),
                        OffsetDateTime.parse("2027-01-01T09:00:00-05:00"))));

        assertEquals(new BigDecimal("0.00"),
                new InvestmentInterestService(accountDAO).calculateTotalInterest(account));
    }

    /**
     * Tests that an unsaved account is rejected before any database query.
     */
    @Test
    void calculateTotalInterestRejectsUnsavedAccount() {
        AccountDAO accountDAO = mock(AccountDAO.class);
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        InvestmentAccount unsaved = new InvestmentAccount(customer, new BigDecimal("1000.00"),
                12, new BigDecimal("5.00"), LocalDate.of(2027, 1, 1));

        assertThrows(IllegalArgumentException.class,
                () -> new InvestmentInterestService(accountDAO).calculateTotalInterest(unsaved));
        verifyNoInteractions(accountDAO);
    }

    /**
     * Creates a saved Investment Account for interest calculation tests.
     *
     * @return an Investment Account with a saved customer and account ID
     */
    private InvestmentAccount createSavedInvestmentAccount() {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                "1234567890", "testcustomer1", "unused-test-hash");
        customer.setId(1L);
        InvestmentAccount account = new InvestmentAccount(customer, new BigDecimal("1000.00"),
                12, new BigDecimal("5.00"), LocalDate.of(2027, 1, 1));
        account.setId(3L);
        return account;
    }

    /**
     * Tests that maturity interest uses the supplied connection and contribution dates.
     *
     * @throws SQLException if the mocked DAO fails
     */
    @Test
    void calculateTotalInterestWithConnectionUsesTransactionLedger() throws SQLException {
        AccountDAO accountDAO = mock(AccountDAO.class);
        Connection connection = mock(Connection.class);
        InvestmentAccount account = createSavedInvestmentAccount();
        when(accountDAO.findInvestmentContributions(connection, 3L, 1L)).thenReturn(List.of(
                new InvestmentContribution(new BigDecimal("650.00"),
                        OffsetDateTime.parse("2026-01-01T10:00:00-05:00"))));

        assertEquals(new BigDecimal("32.50"),
                new InvestmentInterestService(accountDAO).calculateTotalInterest(connection, account));
        verify(accountDAO, never()).findInvestmentContributions(3L, 1L);
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    /**
     * Tests that invalid arguments never reach the contribution DAO.
     */
    @Test
    void calculateTotalInterestWithConnectionRejectsMissingConnection() {
        AccountDAO accountDAO = mock(AccountDAO.class);
        assertThrows(IllegalArgumentException.class,
                () -> new InvestmentInterestService(accountDAO)
                        .calculateTotalInterest(null, createSavedInvestmentAccount()));
        verifyNoInteractions(accountDAO);
    }

}
