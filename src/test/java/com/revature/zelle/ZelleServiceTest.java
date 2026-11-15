package com.revature.zelle;

import com.revature.account.CheckingAccount;
import com.revature.account.SavingsAccount;
import com.revature.customer.Customer;
import com.revature.customer.CustomerDAO;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests Zelle service validation without accessing the live database. */
public class ZelleServiceTest {

    private CustomerDAO customerDAO;
    private ZellePaymentRequestDAO requestDAO;
    private ZelleService service;
    private ZellePaymentDAO paymentDAO;
    private ZelleDirectPaymentDAO directPaymentDAO;

    @BeforeEach
    void setUp() {
        customerDAO = mock(CustomerDAO.class);
        requestDAO = mock(ZellePaymentRequestDAO.class);
        paymentDAO = mock(ZellePaymentDAO.class);
        directPaymentDAO = mock(ZelleDirectPaymentDAO.class);
        service = new ZelleService(customerDAO, requestDAO, paymentDAO, directPaymentDAO);
    }

    private Customer activeCustomer(long id) {
        Customer customer = new Customer("Test", "Customer", "test@example.com",
                null, "testcustomer" + id, "hash");
        customer.setId(id);
        customer.setStatus("ACTIVE");
        return customer;
    }

    @Test
    void requestMoneyCreatesPendingRequestForExistingActivePayer() throws Exception {
        Customer requester = activeCustomer(1L);
        Customer payer = activeCustomer(2L);
        when(customerDAO.findCustomerByUsername("payername")).thenReturn(payer);
        when(requestDAO.createRequest(1L, 2L, new BigDecimal("15.25"), "Dinner"))
                .thenReturn(42L);

        assertEquals(42L, service.requestMoney(requester, "  PayerName  ",
                new BigDecimal("15.25"), "Dinner"));
        verify(requestDAO).createRequest(1L, 2L, new BigDecimal("15.25"), "Dinner");
    }

    @Test
    void requestMoneyRejectsUnknownAndSelfPayers() throws Exception {
        Customer requester = activeCustomer(1L);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMoney(requester, "missing", new BigDecimal("1.00"), null));
        when(customerDAO.findCustomerByUsername("self")).thenReturn(requester);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMoney(requester, "self", new BigDecimal("1.00"), null));
        verifyNoInteractions(requestDAO);
    }

    @Test
    void requestMoneyRejectsInvalidAmountAndInactiveCustomers() throws Exception {
        Customer requester = activeCustomer(1L);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMoney(requester, "payer", new BigDecimal("0.00"), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMoney(requester, "payer", new BigDecimal("1.001"), null));
        requester.setStatus("CLOSED");
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMoney(requester, "payer", new BigDecimal("1.00"), null));
        requester.setStatus("ACTIVE");
        Customer inactivePayer = activeCustomer(2L);
        inactivePayer.setStatus("CLOSED");
        when(customerDAO.findCustomerByUsername("payer")).thenReturn(inactivePayer);
        assertThrows(IllegalArgumentException.class,
                () -> service.requestMoney(requester, "payer", new BigDecimal("1.00"), null));
        verifyNoInteractions(requestDAO);
    }

    @Test
    void getPendingRequestsUsesCustomerIdentityNotAccountIdentity() throws Exception {
        Customer payer = activeCustomer(2L);
        when(requestDAO.findPendingRequestsForPayer(2L)).thenReturn(List.of());
        assertTrue(service.getPendingRequests(payer).isEmpty());
        verify(requestDAO).findPendingRequestsForPayer(2L);
    }

    @Test
    void getRequestRestrictsLookupToCustomerAndValidRequestId() throws Exception {
        Customer customer = activeCustomer(3L);
        assertNull(service.getRequest(customer, 99L));
        verify(requestDAO).findRequestForCustomer(99L, 3L);
        assertThrows(IllegalArgumentException.class, () -> service.getRequest(customer, 0L));
    }

    @Test
    void databaseFailurePropagatesWithoutCreatingPayment() throws Exception {
        Customer requester = activeCustomer(1L);
        when(customerDAO.findCustomerByUsername("payer")).thenThrow(new SQLException("offline"));
        assertThrows(SQLException.class,
                () -> service.requestMoney(requester, "payer", new BigDecimal("5.00"), null));
        verifyNoInteractions(requestDAO);
    }
    /** Checks that an active payer can approve using their own Checking Account. */
    @Test
    void approveRequestUsesOwnedCheckingAccount() throws Exception {
        Customer payer = activeCustomer(2L);
        CheckingAccount account = new CheckingAccount(payer);
        account.setId(5L);
        when(paymentDAO.approveRequest(10L, 2L, 5L)).thenReturn(88L);

        assertEquals(88L, service.approveRequest(payer, account, 10L));
        verify(paymentDAO).approveRequest(10L, 2L, 5L);
    }

    /** Checks that Savings Accounts can also fund an approved request. */
    @Test
    void approveRequestAcceptsSavingsAccount() throws Exception {
        Customer payer = activeCustomer(2L);
        SavingsAccount account = new SavingsAccount(payer, new BigDecimal("1.00"));
        account.setId(6L);
        when(paymentDAO.approveRequest(11L, 2L, 6L)).thenReturn(89L);

        assertEquals(89L, service.approveRequest(payer, account, 11L));
    }

    /** Stops an invalid request ID before any payment is attempted. */
    @Test
    void approveRequestRejectsInvalidRequestId() {
        Customer payer = activeCustomer(2L);
        CheckingAccount account = new CheckingAccount(payer);
        account.setId(5L);

        assertThrows(IllegalArgumentException.class,
                () -> service.approveRequest(payer, account, 0L));
        verifyNoInteractions(paymentDAO);
    }

    /** Prevents customers from paying from another person's account. */
    @Test
    void approveRequestRejectsAccountOwnedByAnotherCustomer() {
        Customer payer = activeCustomer(2L);
        CheckingAccount account = new CheckingAccount(activeCustomer(3L));
        account.setId(5L);

        assertThrows(IllegalArgumentException.class,
                () -> service.approveRequest(payer, account, 10L));
        verifyNoInteractions(paymentDAO);
    }

    /** Prevents closed accounts and inactive customers from paying requests. */
    @Test
    void approveRequestRejectsInactiveCustomerAndClosedAccount() {
        Customer payer = activeCustomer(2L);
        CheckingAccount account = new CheckingAccount(payer);
        account.setId(5L);
        account.setStatus("CLOSED");
        assertThrows(IllegalArgumentException.class,
                () -> service.approveRequest(payer, account, 10L));

        account.setStatus("ACTIVE");
        payer.setStatus("CLOSED");
        assertThrows(IllegalArgumentException.class,
                () -> service.approveRequest(payer, account, 10L));
        verifyNoInteractions(paymentDAO);
    }

    /** Lets the DAO report failed payments without hiding the error. */
    @Test
    void approveRequestPropagatesDatabaseFailure() throws Exception {
        Customer payer = activeCustomer(2L);
        CheckingAccount account = new CheckingAccount(payer);
        account.setId(5L);
        when(paymentDAO.approveRequest(10L, 2L, 5L))
                .thenThrow(new SQLException("Insufficient funds"));

        SQLException error = assertThrows(SQLException.class,
                () -> service.approveRequest(payer, account, 10L));
        assertEquals("Insufficient funds", error.getMessage());
    }

    /** Confirms that rejection uses the logged-in payer's customer ID. */
    @Test
    void rejectRequestCallsDaoForActivePayer() throws Exception {
        Customer payer = activeCustomer(2L);

        service.rejectRequest(payer, 17L);

        verify(requestDAO).rejectRequest(17L, 2L);
        verifyNoInteractions(paymentDAO);
    }

    /** Invalid IDs and inactive customers cannot reject requests. */
    @Test
    void rejectRequestRejectsInvalidInput() {
        Customer payer = activeCustomer(2L);
        assertThrows(IllegalArgumentException.class, () -> service.rejectRequest(payer, 0L));
        payer.setStatus("CLOSED");
        assertThrows(IllegalArgumentException.class, () -> service.rejectRequest(payer, 17L));
        assertThrows(IllegalArgumentException.class, () -> service.rejectRequest(null, 17L));
        verifyNoInteractions(requestDAO);
    }

    /** A database rejection failure is reported without moving money. */
    @Test
    void rejectRequestPropagatesDatabaseFailure() throws Exception {
        Customer payer = activeCustomer(2L);
        doThrow(new SQLException("Request already resolved"))
                .when(requestDAO).rejectRequest(17L, 2L);

        SQLException error = assertThrows(SQLException.class,
                () -> service.rejectRequest(payer, 17L));
        assertEquals("Request already resolved", error.getMessage());
        verifyNoInteractions(paymentDAO);
    }

    /** A valid direct payment uses the sender's selected Checking Account. */
    @Test
    void sendMoneyUsesCheckingAccount() throws Exception {
        Customer sender = activeCustomer(1);
        Customer recipient = activeCustomer(2);
        CheckingAccount account = new CheckingAccount(sender);
        account.setId(10);
        when(customerDAO.findCustomerByUsername("receiver")).thenReturn(recipient);
        when(directPaymentDAO.sendMoney(1, 10, 2, new BigDecimal("12.50"), "Lunch"))
                .thenReturn(50L);
        assertEquals(50L, service.sendMoney(sender, account, " RECEIVER ",
                new BigDecimal("12.50"), "Lunch"));
    }

    /** Savings Accounts can also fund a direct payment. */
    @Test
    void sendMoneyUsesSavingsAccount() throws Exception {
        Customer sender = activeCustomer(1);
        Customer recipient = activeCustomer(2);
        SavingsAccount account = new SavingsAccount(sender, new BigDecimal("1.00"));
        account.setId(11);
        when(customerDAO.findCustomerByUsername("receiver")).thenReturn(recipient);
        when(directPaymentDAO.sendMoney(1, 11, 2, new BigDecimal("5.00"), null))
                .thenReturn(51L);
        assertEquals(51L, service.sendMoney(sender, account, "receiver",
                new BigDecimal("5.00"), null));
    }

    /** Unknown recipients and self-payments must be rejected. */
    @Test
    void sendMoneyRejectsUnknownRecipientAndSelfPayment() throws Exception {
        Customer sender = activeCustomer(1);
        CheckingAccount account = new CheckingAccount(sender);
        account.setId(10);
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, account, "missing", new BigDecimal("1.00"), null));
        when(customerDAO.findCustomerByUsername("self")).thenReturn(sender);
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, account, "self", new BigDecimal("1.00"), null));
        verifyNoInteractions(directPaymentDAO);
    }

    /** Invalid amounts and descriptions never reach the payment DAO. */
    @Test
    void sendMoneyRejectsInvalidAmounts() throws Exception {
        Customer sender = activeCustomer(1);
        CheckingAccount account = new CheckingAccount(sender);
        account.setId(10);
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, account, "receiver", BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, account, "receiver", new BigDecimal("1.001"), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, account, "receiver", new BigDecimal("1.00"), "x".repeat(256)));
        verifyNoInteractions(directPaymentDAO);
    }

    /** Closed accounts and accounts owned by others cannot send money. */
    @Test
    void sendMoneyRejectsIneligibleFundingAccount() throws Exception {
        Customer sender = activeCustomer(1);
        CheckingAccount otherAccount = new CheckingAccount(activeCustomer(3));
        otherAccount.setId(10);
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, otherAccount, "receiver", new BigDecimal("1.00"), null));
        CheckingAccount closed = new CheckingAccount(sender);
        closed.setId(11);
        closed.setStatus("CLOSED");
        assertThrows(IllegalArgumentException.class,
                () -> service.sendMoney(sender, closed, "receiver", new BigDecimal("1.00"), null));
        verifyNoInteractions(directPaymentDAO);
    }

    /** Database payment failures are returned to the caller. */
    @Test
    void sendMoneyPropagatesDatabaseFailure() throws Exception {
        Customer sender = activeCustomer(1);
        Customer recipient = activeCustomer(2);
        CheckingAccount account = new CheckingAccount(sender);
        account.setId(10);
        when(customerDAO.findCustomerByUsername("receiver")).thenReturn(recipient);
        when(directPaymentDAO.sendMoney(1, 10, 2, new BigDecimal("2.00"), null))
                .thenThrow(new SQLException("Database error"));
        assertThrows(SQLException.class,
                () -> service.sendMoney(sender, account, "receiver", new BigDecimal("2.00"), null));
    }

}
