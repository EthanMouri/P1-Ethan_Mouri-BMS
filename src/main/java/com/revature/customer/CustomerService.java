package com.revature.customer;

import com.revature.security.PasswordHasher;
import com.revature.util.PasswordValidator;
import com.revature.util.DatabaseConnection;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.Locale;
import com.revature.util.ConnectionProvider;

public class CustomerService {

    private final CustomerDAO customerDAO;
    private final ConnectionProvider connectionProvider;


    /**
     * Creates a CustomerService to work with the actual database.
     */
    public CustomerService() {
        this(new CustomerDAO(), DatabaseConnection::getConnection);
    }

    /**
     * Creates a CustomerService with a given DAO to work with the actual database.
     *
     * @param customerDAO the DAO for customer database operations
     * @throws IllegalArgumentException if customerDAO is null
     */
    public CustomerService(CustomerDAO customerDAO) {
        this(customerDAO, DatabaseConnection::getConnection);
    }

    /**
     * Creates a CustomerService with supplied mock for unit tests.
     *
     * @param customerDAO the DAO for customer database operations
     * @param connectionProvider the provider for database connections
     * @throws IllegalArgumentException if either dependency is null
     */
    public CustomerService(CustomerDAO customerDAO, ConnectionProvider connectionProvider) {
        //Make sure arguments are not null
        if (customerDAO == null) {
            throw new IllegalArgumentException("CustomerDAO cannot be null.");
        }
        if (connectionProvider == null) {
            throw new IllegalArgumentException("ConnectionProvider cannot be null.");
        }

        this.customerDAO = customerDAO;
        this.connectionProvider = connectionProvider;
    }




    /**
     * Validates and registers a new customer.
     *
     * @return the database-generated customer ID
     * @throws IllegalArgumentException if registration details are invalid
     * @throws IllegalStateException if the username or email is already registered
     * @throws SQLException if a database operation fails
     */
    public long registerCustomer(String firstName, String lastName, String email, String phoneNumber,
                                 String username, String password) throws SQLException {

        //make sure none of the customer info is null
        if (firstName == null || lastName == null || email == null || username == null) {
            throw new IllegalArgumentException("First name, last name, email, and username are required.");
        }

        //Remove whitespace from customer information and lowercase it when necessary
        firstName = firstName.trim();
        lastName = lastName.trim();
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
        username = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
            if (phoneNumber.isEmpty()) {
                phoneNumber = null;
            }
        }

        //Check that the information isn't empty strings
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || username.isBlank()) {
            throw new IllegalArgumentException("First name, last name, email, and username are required.");
        }

        //Confirm the customer info can fit in the database columns
        if (firstName.length() > 50 || lastName.length() > 50
                || email.length() > 254 || username.length() > 80
                || (phoneNumber != null && phoneNumber.length() > 30)) {

            throw new IllegalArgumentException("One or more registration fields exceed the allowed length.");
        }

        //checks email format (text@text.text)
        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }

        //Check for valid phone number
        if (phoneNumber != null && !phoneNumber.matches("^\\+?[0-9() .-]{7,30}$")) {
            throw new IllegalArgumentException("Please enter a valid phone number.");
        }

        //Validate password
        if (!PasswordValidator.isValidPassword(password, username)) {
            throw new IllegalArgumentException("Password does not meet the security requirements.");
        }

        //Check for existing username or email
        if (customerDAO.usernameExists(username)) {
            throw new IllegalStateException("That username is already registered.");
        }

        if (customerDAO.emailExists(email)) {
            throw new IllegalStateException("That email address is already registered.");
        }

        //Hash the password
        String hashedPassword = PasswordHasher.hashPassword(password);
        //Create the customer object with all the data
        Customer customer = new Customer(firstName, lastName, email, phoneNumber, username, hashedPassword);

        //Insert customer into PostgreSQL Database
        try {
            return customerDAO.insertCustomer(customer);
        }
        catch (SQLException exception) {
            //There was a duplicate in the database and this customer could not be inserted
            if ("23505".equals(exception.getSQLState())) {
                throw new IllegalStateException("That username or email address is already registered.", exception);
            }

            throw exception;
        }
    }


    /**
     * Authenticates a customer using their username and password.
     *
     * @param username the username entered during login
     * @param password the plaintext password entered during login
     * @return the authenticated Customer, or null if login fails
     * @throws SQLException if a database operation fails
     */
    public Customer loginCustomer(String username, String password) throws SQLException {

        // Reject missing login credentials
        if (username == null || password == null || username.isBlank() || password.isEmpty()) {
            return null;
        }

        // Remove whitespace and make the username lowercase
        username = username.trim().toLowerCase(java.util.Locale.ROOT);

        // Find the customer in PostgreSQL database
        Customer customer = customerDAO.findCustomerByUsername(username);

        // No customer exists with this username
        if (customer == null) {
            return null;
        }

        // Compare the entered password against the stored PBKDF2 hash
        boolean passwordMatches = PasswordHasher.verifyPassword(password, customer.getHashedPassword());
        if (!passwordMatches) {
            return null;
        }

        // Authentication succeeded
        return customer;
    }


    /**
     * Authenticates a customer while enforcing account lockouts
     * and recording login attempts.
     *
     * @param username Customer's password
     * @param password Customer's username
     *
     * @return the authenticated customer, or null if login fails
     */
    public Customer loginCustomerSecurely(String username, String password) throws SQLException {
        //make sure the username and password are valid and formatted properly
        if (username == null || password == null || username.isBlank()) {
            return null;
        }
        String formattedUsername = username.trim().toLowerCase(Locale.ROOT);

        //connect to the database
        try (Connection connection = connectionProvider.getConnection()) {
            //we are making a transaction, so auto commit has to be off
            connection.setAutoCommit(false);

            try {
                //find the customer from the database
                Customer customer = customerDAO.findCustomerByUsernameForUpdate(connection, formattedUsername);

                // Couldn't find the customer/user.
                if (customer == null) {
                    //log the failed login attempt
                    customerDAO.insertLoginAudit(connection, null, formattedUsername, "FAILURE");
                    connection.commit();
                    return null;
                }

                //check what time is it right now
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                // Account is closed, login can not happen
                if ("CLOSED".equals(customer.getStatus())) {
                    //add the login attempt to the log
                    customerDAO.insertLoginAudit(connection, customer.getId(), formattedUsername, "FAILURE");
                    connection.commit();
                    return null;
                }

                // Account is locked, login can't happen until the lock countdown is over
                if ("LOCKED".equals(customer.getStatus()) && customer.getLockedUntil() != null
                        //has the account lock expired
                        && now.isBefore(customer.getLockedUntil())) {
                    //add the login attempt to the log
                    customerDAO.insertLoginAudit(connection, customer.getId(), formattedUsername, "LOCKED");
                    connection.commit();
                    return null;
                }

                // the lockout timer has expired if the code gets here
                //allow the user to try and sign in again
                int failedLoginCount = customer.getFailedLoginCount();
                if ("LOCKED".equals(customer.getStatus())) {
                    failedLoginCount = 0;
                }

                //verify the password
                boolean passwordMatches = PasswordHasher.verifyPassword(password, customer.getHashedPassword());

                //if the password was correct:
                if (passwordMatches) {
                    //update the login attempt in the audit and customer
                    customerDAO.updateLoginSecurity(connection, customer.getId(), 0, "ACTIVE", null);
                    customerDAO.insertLoginAudit(connection, customer.getId(), formattedUsername, "SUCCESS");
                    connection.commit();

                    customer.setStatus("ACTIVE");
                    customer.setFailedLoginCount(0);
                    customer.setLockedUntil(null);
                    return customer;
                }

                // If the password was incorrect:
                // increment consecutive failures.
                int newFailedLoginCount = failedLoginCount + 1;

                //if the customer got the password wrong 5 time, lock them out for 15 minutes
                if (newFailedLoginCount >= 3) {
                    OffsetDateTime lockedUntil = now.plusMinutes(15);
                    customerDAO.updateLoginSecurity(connection, customer.getId(), newFailedLoginCount, "LOCKED", lockedUntil);
                }
                //update the database if there are still more attempts left to log in
                else {
                    customerDAO.updateLoginSecurity(connection, customer.getId(), newFailedLoginCount, "ACTIVE", null);
                }
                //update the log to show the newest log in fail
                customerDAO.insertLoginAudit(connection, customer.getId(), formattedUsername, "FAILURE");
                connection.commit(); //end the transaction
                return null;

            }
            //The attempted the log in caused an error
            catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }




}
