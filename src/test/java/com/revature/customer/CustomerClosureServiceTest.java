package com.revature.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests customer closure without changing the real database.
 */
class CustomerClosureServiceTest {
    private CustomerDAO customerDAO;
    private CustomerClosureEligibilityService eligibilityService;
    private CustomerClosureDAO closureDAO;
    private CustomerClosureService service;

    @BeforeEach
    void setUp() {
        customerDAO = mock(CustomerDAO.class);
        eligibilityService = mock(CustomerClosureEligibilityService.class);
        closureDAO = mock(CustomerClosureDAO.class);
        service = new CustomerClosureService(customerDAO, eligibilityService, closureDAO);
    }

    @Test
    void rejectsBlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> service.closeCustomer("  "));
        verifyNoInteractions(customerDAO, eligibilityService, closureDAO);
    }

    @Test
    void rejectsNullUsername() {
        assertThrows(IllegalArgumentException.class, () -> service.closeCustomer(null));
    }

    @Test
    void blocksIneligibleCustomer() throws SQLException {
        when(eligibilityService.checkEligibility("testuser"))
                .thenReturn(new CustomerClosureEligibility(false, List.of("Open accounts remain.")));
        assertThrows(IllegalStateException.class, () -> service.closeCustomer("testuser"));
        verifyNoInteractions(customerDAO, closureDAO);
    }

    @Test
    void closesEligibleCustomer() throws SQLException {
        Customer customer = new Customer("Test", "User", "test@example.com", null, "testuser", "hash");
        customer.setId(3L);
        when(eligibilityService.checkEligibility("testuser"))
                .thenReturn(new CustomerClosureEligibility(true, List.of()));
        when(customerDAO.findCustomerByUsername("testuser")).thenReturn(customer);
        when(closureDAO.closeEligibleCustomer(3L)).thenReturn(true);
        assertTrue(service.closeCustomer(" TestUser "));
        verify(closureDAO).closeEligibleCustomer(3L);
    }

    @Test
    void reportsEligibilityChangedBeforeUpdate() throws SQLException {
        Customer customer = new Customer("Test", "User", "test@example.com", null, "testuser", "hash");
        customer.setId(3L);
        when(eligibilityService.checkEligibility("testuser"))
                .thenReturn(new CustomerClosureEligibility(true, List.of()));
        when(customerDAO.findCustomerByUsername("testuser")).thenReturn(customer);
        assertFalse(service.closeCustomer("testuser"));
    }

    @Test
    void rejectsMissingCustomer() throws SQLException {
        when(eligibilityService.checkEligibility("missing"))
                .thenReturn(new CustomerClosureEligibility(true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.closeCustomer("missing"));
        verifyNoInteractions(closureDAO);
    }
}
