package com.revature.customer;

import com.revature.security.PasswordHasher;
import com.revature.util.ConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for customer authentication and login security.
 * Uses Mockito to avoid changing records in the real database.
 */
class CustomerServiceTest {

    private CustomerDAO mockCustomerDAO;
    private Connection mockConnection;
    private CustomerService customerService;

    /**
     * Creates new mocks before each test.
     */
    @BeforeEach
    void setUp() {
        // Create mocks instead of connecting to PostgreSQL.
        mockCustomerDAO = mock(CustomerDAO.class);
        mockConnection = mock(Connection.class);

        // Return the mock connection whenever the service requests one.
        ConnectionProvider mockConnectionProvider = () -> mockConnection;
        customerService = new CustomerService(mockCustomerDAO, mockConnectionProvider);
    }


    /**
     * Creates a test customer with a hashed password and the given
     * account status, failed login count, and lockout expiration.
     *
     * @param status the customer's account status
     * @param failedLoginCount the number of failed login attempts
     * @param lockedUntil the time the account lockout expires, or null
     * @return a customer configured for authentication tests
     */
    private Customer createTestCustomer(String status, int failedLoginCount, OffsetDateTime lockedUntil) {
        //Create a customer with a hashed password.
        String hashedPassword = PasswordHasher.hashPassword("SecureBank123!");

        Customer customer = new Customer(
                "Test", "Customer", "testcustomer1@gmail.com",
                "5551234567", "testcustomer1", hashedPassword
        );

        //Set the customer's account information for the test.
        customer.setId(1L);
        customer.setStatus(status);
        customer.setFailedLoginCount(failedLoginCount);
        customer.setLockedUntil(lockedUntil);

        return customer;
    }


    /**
     * Tests that a customer can log in with the correct password.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void correctPasswordAllowsLogin() throws SQLException {
        //Create an active customer with no failed login attempts.
        String password = "SecureBank123!";
        Customer customer = createTestCustomer("ACTIVE", 0, null);

        // Tell the mocked DAO which customer to return.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Test: attempt login using the correct password.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", password);

        // Assert: login succeeds and returns the expected customer.
        assertNotNull(loggedInCustomer);
        assertEquals(1L, loggedInCustomer.getId());

        // Verify that the login security fields were reset.
        verify(mockCustomerDAO).updateLoginSecurity(mockConnection, 1L, 0, "ACTIVE", null);

        // Verify that the successful login was audited.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "SUCCESS");

        // Verify that the database transaction was committed.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }


    /**
     * Tests that an incorrect password is rejected and increments the customer's failed-login count.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void incorrectPasswordIncrementsFailedLoginCount() throws SQLException {
        //Create an active customer with no failed login attempts.
        Customer customer = createTestCustomer("ACTIVE", 0, null);

        //Return this customer when the service performs its database lookup.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Test: attempt login using an incorrect password.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", "WrongPassword123!");

        //The incorrect password must not authenticate the customer.
        assertNull(loggedInCustomer);

        // Verify that the failed-login count increases from zero to one.
        verify(mockCustomerDAO).updateLoginSecurity(mockConnection, 1L, 1, "ACTIVE", null);

        // Verify that the failed attempt is recorded in the audit table.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "FAILURE");

        // Verify that the database transaction completes successfully.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }



    /**
     * Tests that a customer's third failed login attempt locks the account for 15 minutes.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void thirdFailedLoginLocksCustomer() throws SQLException {
        //Create a customer who already has two failed login attempts.
        Customer customer = createTestCustomer("ACTIVE", 2, null);

        //Return this customer when the service looks up the username.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Record the time immediately before the login attempt.
        OffsetDateTime beforeLogin = OffsetDateTime.now(ZoneOffset.UTC);

        //Test: attempt login with an incorrect password for the third time.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", "WrongPassword123!");

        //Record the time immediately after the login attempt.
        OffsetDateTime afterLogin = OffsetDateTime.now(ZoneOffset.UTC);

        //Check that the login is rejected.
        assertNull(loggedInCustomer);

        //Capture the lockout expiration time that was passed to the DAO.
        ArgumentCaptor<OffsetDateTime> lockedUntilCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

        //Verify that the third failed attempt locks the account.
        verify(mockCustomerDAO).updateLoginSecurity(
                eq(mockConnection), eq(1L), eq(3), eq("LOCKED"), lockedUntilCaptor.capture());

        //Check that the lockout expires 15 minutes after the failed login.
        OffsetDateTime lockedUntil = lockedUntilCaptor.getValue();
        assertFalse(lockedUntil.isBefore(beforeLogin.plusMinutes(15)));
        assertFalse(lockedUntil.isAfter(afterLogin.plusMinutes(15)));

        //Verify that the failed login is recorded in the audit table.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "FAILURE");

        //Verify that the database transaction is committed.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }




    /**
     * Tests that a locked out customer cannot log in with the correct password while the
     * 15-minute lockout period is still active.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void lockedCustomerCannotLoginWithCorrectPassword() throws SQLException {
        //Create a customer whose account is currently locked.
        String correctPassword = "SecureBank123!";
        //Create a customer who is locked after three failed login attempts.
        Customer customer = createTestCustomer("LOCKED", 3, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        //Return the locked customer when the service looks up the username.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Test: attempt login with the correct password during the lockout.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", correctPassword);

        //Checks the customer does not get authenticated.
        assertNull(loggedInCustomer);

        //Verify that the rejected attempt is recorded as LOCKED.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "LOCKED");

        //Verify that the service never tries to change the customer's lockout information.
        verify(mockCustomerDAO, never()).updateLoginSecurity(
                any(), anyLong(), anyInt(), anyString(), nullable(OffsetDateTime.class));

        //Verify that the audit transaction is committed.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }


    /**
     * Tests that a locked out customer can log in with the correct password
     * after the 15-minute lockout period has expired.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void expiredLockoutAllowsCorrectPassword() throws SQLException {
        //Create a customer whose lockout period has expired.
        String correctPassword = "SecureBank123!";
        //Create a customer whose three-attempt lockout has expired.
        Customer customer = createTestCustomer("LOCKED", 3, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        //Return the customer when the service looks up the username.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Test: attempt login with the correct password after the lockout expires.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", correctPassword);

        //Checks that the customer is successfully authenticated.
        assertNotNull(loggedInCustomer);
        assertEquals(1L, loggedInCustomer.getId());

        //Verify that the account is unlocked and failed attempts are reset to zero.
        verify(mockCustomerDAO).updateLoginSecurity(mockConnection, 1L, 0, "ACTIVE", null);

        //Verify that the successful login is recorded.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "SUCCESS");

        //Verify that the database transaction is committed.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }


    /**
     * Login attempt with an unknown username is rejected
     * and recorded in the login audit table.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void unknownUsernameIsRejectedAndAudited() throws SQLException {
        //Return null because the username does not exist in the database.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "unknownuser")).thenReturn(null);

        //Test: attempt login with an unknown username.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("unknownuser", "SecureBank123!");

        //Check that the login is rejected.
        assertNull(loggedInCustomer);

        //Verify that the failed login is recorded without a customer ID.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, null, "unknownuser", "FAILURE");

        //Verify that no customer security information is changed.
        verify(mockCustomerDAO, never()).updateLoginSecurity(
                any(), anyLong(), anyInt(), anyString(), nullable(OffsetDateTime.class));

        //Verify that the audit transaction is committed.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }


    /**
     * Tests that a customer with a closed account cannot log in even when they enter the correct password.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void closedCustomerCannotLoginWithCorrectPassword() throws SQLException {
        //Create a customer whose account has been closed.
        String correctPassword = "SecureBank123!";
        Customer customer = createTestCustomer("CLOSED", 0, null);

        //Return the closed customer when the service looks up the username.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Test: attempt login with the correct password.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", correctPassword);

        //Check that the closed customer is not authenticated.
        assertNull(loggedInCustomer);

        //Verify that the rejected login is recorded in the audit table.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "FAILURE");

        //Verify that the service does not change the closed customer's security information.
        verify(mockCustomerDAO, never()).updateLoginSecurity(
                any(), anyLong(), anyInt(), anyString(), nullable(OffsetDateTime.class));

        //Verify that the failed login is recorded by committing the audit transaction.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }


    /**
     * Tests that a database error during login causes the transaction to roll back
     * instead of committing incomplete changes.
     *
     * @throws SQLException if the expected database error is thrown
     */
    @Test
    void databaseErrorDuringLoginRollsBackTransaction() throws SQLException {
        //Create an active customer with a valid password.
        String correctPassword = "SecureBank123!";
        Customer customer = createTestCustomer("ACTIVE", 0, null);

        //Return the customer when the service looks up the username.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Make the audit operation fail to simulate a database error.
        doThrow(new SQLException("Simulated database failure"))
                .when(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "SUCCESS");

        //Test: attempt login and check that the database error is thrown.
        assertThrows(SQLException.class, () -> customerService.loginCustomerSecurely("testcustomer1", correctPassword));

        //Verify that the service attempted to update the customer's login information.
        verify(mockCustomerDAO).updateLoginSecurity(mockConnection, 1L, 0, "ACTIVE", null);

        //Verify that the failed transaction is rolled back.
        verify(mockConnection).rollback();

        //Verify that the failed transaction is never committed.
        verify(mockConnection, never()).commit();
    }


    /**
     * Tests that an incorrect password after an expired lockout
     * restarts the failed login count at one instead of locking
     * the customer again.
     *
     * @throws SQLException if a mocked database operation fails
     */
    @Test
    void incorrectPasswordAfterExpiredLockoutResetsFailedLoginCount() throws SQLException {
        //Create a customer whose three-attempt lockout expired one minute ago.
        Customer customer = createTestCustomer("LOCKED", 3, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        //Return the customer when the service looks up the username.
        when(mockCustomerDAO.findCustomerByUsernameForUpdate(mockConnection, "testcustomer1"))
                .thenReturn(customer);

        //Test: attempt login with an incorrect password after the lockout expires.
        Customer loggedInCustomer = customerService.loginCustomerSecurely("testcustomer1", "WrongPassword123!");

        //Check that the incorrect password is rejected.
        assertNull(loggedInCustomer);

        //Verify that the failed login count restarts at one and the account becomes active.
        verify(mockCustomerDAO).updateLoginSecurity(mockConnection, 1L, 1, "ACTIVE", null);

        //Verify that the incorrect password is recorded in the login audit table.
        verify(mockCustomerDAO).insertLoginAudit(mockConnection, 1L, "testcustomer1", "FAILURE");

        //Verify that the database transaction is committed.
        verify(mockConnection).commit();
        verify(mockConnection, never()).rollback();
    }


}
