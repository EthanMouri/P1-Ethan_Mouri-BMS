package com.revature.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests customer closure checks without modifying PostgreSQL.
 */
class CustomerClosureEligibilityServiceTest {
    private CustomerDAO customerDAO;
    private CustomerClosureEligibilityDAO eligibilityDAO;
    private CustomerClosureEligibilityService service;

    /**
     * Prepares mocked DAOs for each test.
     */
    @BeforeEach
    void setUp() {
        customerDAO = mock(CustomerDAO.class);
        eligibilityDAO = mock(CustomerClosureEligibilityDAO.class);
        service = new CustomerClosureEligibilityService(customerDAO, eligibilityDAO);
    }

    /**
     * Creates a customer with a known ID and status.
     */
    private Customer customer(String status) {
        Customer customer = new Customer("Test", "Customer", "test@example.com", null,
                "testcustomer", "hash");
        customer.setId(3L);
        customer.setStatus(status);
        return customer;
    }

    @Test
    void rejectsMissingUsername() {
        assertThrows(IllegalArgumentException.class, () -> service.checkEligibility(" "));
        verifyNoInteractions(customerDAO, eligibilityDAO);
    }

    @Test
    void rejectsUnknownCustomer() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.checkEligibility("missing"));
        verifyNoInteractions(eligibilityDAO);
    }

    @Test
    void eligibleWhenNoObligationsRemain() throws SQLException {
        when(customerDAO.findCustomerByUsername("testcustomer")).thenReturn(customer("ACTIVE"));
        CustomerClosureEligibility result = service.checkEligibility(" TestCustomer ");
        assertTrue(result.eligible());
        assertTrue(result.reasons().isEmpty());
        verify(eligibilityDAO).countOpenAccounts(3L);
        verify(eligibilityDAO).countNonzeroBalances(3L);
        verify(eligibilityDAO).countPendingZelleRequests(3L);
    }

    @Test
    void blocksOpenAccounts() throws SQLException {
        when(customerDAO.findCustomerByUsername("testcustomer")).thenReturn(customer("ACTIVE"));
        when(eligibilityDAO.countOpenAccounts(3L)).thenReturn(2L);
        CustomerClosureEligibility result = service.checkEligibility("testcustomer");
        assertFalse(result.eligible());
        assertEquals(1, result.reasons().size());
    }

    @Test
    void blocksNonzeroBalances() throws SQLException {
        when(customerDAO.findCustomerByUsername("testcustomer")).thenReturn(customer("ACTIVE"));
        when(eligibilityDAO.countNonzeroBalances(3L)).thenReturn(1L);
        assertFalse(service.checkEligibility("testcustomer").eligible());
    }

    @Test
    void blocksPendingRequests() throws SQLException {
        when(customerDAO.findCustomerByUsername("testcustomer")).thenReturn(customer("ACTIVE"));
        when(eligibilityDAO.countPendingZelleRequests(3L)).thenReturn(1L);
        assertFalse(service.checkEligibility("testcustomer").eligible());
    }

    @Test
    void blocksNonActiveCustomer() throws SQLException {
        when(customerDAO.findCustomerByUsername("testcustomer")).thenReturn(customer("CLOSED"));
        assertFalse(service.checkEligibility("testcustomer").eligible());
    }

    @Test
    void reportsAllBlockers() throws SQLException {
        when(customerDAO.findCustomerByUsername("testcustomer")).thenReturn(customer("ACTIVE"));
        when(eligibilityDAO.countOpenAccounts(3L)).thenReturn(1L);
        when(eligibilityDAO.countNonzeroBalances(3L)).thenReturn(1L);
        when(eligibilityDAO.countPendingZelleRequests(3L)).thenReturn(1L);
        assertEquals(3, service.checkEligibility("testcustomer").reasons().size());
    }
}
