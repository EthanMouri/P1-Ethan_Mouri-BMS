package com.revature.customer;

import com.revature.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * Database operations for customers.
 */
public class CustomerDAO {

    /**
     * Inserts a new customer into the database
     * @param customer the customer being added to the database
     * @return customer_id: the id PostgreSQL generated for customer
     * @throws SQLException if the insert to database did not work
     */
    public long insertCustomer(Customer customer) throws SQLException {
        String sqlQuery = """
            INSERT INTO customers (first_name, last_name, email, phone, username, password_hash)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING customer_id
            """;

        //try with resources
        //connect to the database and create a prepared sql statemnt
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {

            //insert the customer values into the sql query (INSERT INTO)
            preparedStatement.setString(1, customer.getFirstName());
            preparedStatement.setString(2, customer.getLastName());
            preparedStatement.setString(3, customer.getEmail());
            preparedStatement.setString(4, customer.getPhoneNumber());
            preparedStatement.setString(5, customer.getUsername());
            preparedStatement.setString(6, customer.getHashedPassword());

            //get the result of the query (INSERT INTO)
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                //get the returned customer id from PostgreSQL and store it in the customer's field
                if (resultSet.next()) {
                    long generatedCustomerId = resultSet.getLong("customer_id");
                    customer.setId(generatedCustomerId);
                    return generatedCustomerId;
                }
                //The INSERT returned no customer ID, which is unexpected
                throw new SQLException("Customer insertion did not return a customer ID.");
            }
        }
    }



    /**
     * Checks whether a username already exists in the database.
     *
     * @param username the username to check
     * @return true if the username exists, false otherwise
     * @throws SQLException if the database query fails
     */
    public boolean usernameExists(String username) throws SQLException {

        String sqlQuery = """
            SELECT 1
            FROM customers
            WHERE username = ?
            """;

        //connect to the database and add the username to SQL query
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {

            preparedStatement.setString(1, username);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                //check if any records returned (true) or if no records returned (false)
                return resultSet.next();
            }
        }
    }


    /**
     * Checks whether an email address already exists in the database.
     *
     * @param email the email address to check
     * @return true if the email exists, false otherwise
     * @throws SQLException if the database query fails
     */
    public boolean emailExists(String email) throws SQLException {

        String sqlQuery = """
            SELECT 1
            FROM customers
            WHERE email = ?
            """;

        //connect to the database and add the email to SQL query
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement =
                     connection.prepareStatement(sqlQuery)) {

            preparedStatement.setString(1, email);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                //check if any records returned (true) or if no records returned (false)
                return resultSet.next();
            }
        }
    }


    /**
     * Finds a customer by username.
     *
     * @param username the username to search for
     * @return the matching Customer, or null if none exists
     * @throws SQLException if the database query fails
     */
    public Customer findCustomerByUsername(String username) throws SQLException {

        String sqlQuery = """
        SELECT customer_id, first_name, last_name, email, phone, username, password_hash, status, failed_login_count, locked_until
        FROM customers
        WHERE username = ?
        FOR UPDATE
        """;

        //connect to the database and query for the customer based on username
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {

            preparedStatement.setString(1, username);

            //Get the customer's details and store them
            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                if (resultSet.next()) {
                    Customer customer = new Customer(
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("email"),
                            resultSet.getString("phone"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash")
                    );

                    //set the PostgreSQL generated id for customer
                    customer.setId(resultSet.getLong("customer_id"));

                    //control account status/lock out feature for customer
                    customer.setStatus(resultSet.getString("status"));
                    customer.setFailedLoginCount(resultSet.getInt("failed_login_count"));
                    customer.setLockedUntil(resultSet.getObject("locked_until", OffsetDateTime.class));
                    return customer;
                }

                return null;
            }
        }
    }


    /**
     * Retrieves a customer and locks their database row until the current transaction finishes.
     *
     * @param connection the active database connection
     * @param username the username to search for
     * @return the matching Customer, or null if not found
     * @throws SQLException if the query fails
     */
    public Customer findCustomerByUsernameForUpdate(Connection connection, String username) throws SQLException {

        String sqlQuery = """
                SELECT customer_id, first_name, last_name, email, phone, username, password_hash, status, failed_login_count, locked_until
                FROM customers
                WHERE username = ?
                FOR UPDATE
                """;

        //connect to the database and get the user (by using the username)
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {
            preparedStatement.setString(1, username);

            //make a customer object with the data retrieved from the database
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                Customer customer = new Customer(
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getString("email"),
                        resultSet.getString("phone"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash")
                );

                customer.setId(resultSet.getLong("customer_id"));
                customer.setStatus(resultSet.getString("status"));
                customer.setFailedLoginCount(resultSet.getInt("failed_login_count"));
                customer.setLockedUntil(resultSet.getObject("locked_until", OffsetDateTime.class));

                return customer;
            }
        }

    }


    /**
     * Updates a customer's login security information.
     *
     * Uses an existing database connection so the update
     * can participate in the caller's transaction.
     *
     * @param connection the active database connection
     * @param customerId the customer whose security fields are updated
     * @param failedLoginCount the new number of failed attempts
     * @param status the customer's new status
     * @param lockedUntil when the temporary lock expires, or null
     * @throws SQLException if the update fails
     */
    public void updateLoginSecurity(Connection connection, long customerId, int failedLoginCount,
                                    String status, OffsetDateTime lockedUntil) throws SQLException {

        String sqlQuery = """
            UPDATE customers
            SET failed_login_count = ?,
                status = ?,
                locked_until = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE customer_id = ?
            """;

        //update all the information related to a customer logging in
        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {
            preparedStatement.setInt(1, failedLoginCount);
            preparedStatement.setString(2, status);
            preparedStatement.setObject(3, lockedUntil);
            preparedStatement.setLong(4, customerId);

            int rowsUpdated = preparedStatement.executeUpdate();

            if (rowsUpdated != 1) {
                throw new SQLException("Expected to update exactly one customer record.");
            }
        }
    }


    /**
     * Records a customer login event in the login_audit table.
     *
     * Uses the caller's database connection so the audit
     * can participate in the same transaction as login updates.
     *
     * @param connection the active database connection
     * @param customerId the customer ID, or null if the username is unknown
     * @param usernameAttempted the username entered during login
     * @param outcome the login result
     * @throws SQLException if the audit insertion fails
     */
    public void insertLoginAudit(Connection connection, Long customerId, String usernameAttempted, String outcome) throws SQLException {

        String sqlQuery = """
            INSERT INTO login_audit (customer_id, username_attempted, outcome)
            VALUES (?, ?, ?)
            """;


        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {
            // Unknown usernames do not have a customer ID
            if (customerId == null) {
                preparedStatement.setNull(1, java.sql.Types.BIGINT);
            }
            else {
                preparedStatement.setLong(1, customerId);
            }

            preparedStatement.setString(2, usernameAttempted);
            preparedStatement.setString(3, outcome);

            int rowsInserted = preparedStatement.executeUpdate();

            if (rowsInserted != 1) {
                throw new SQLException(
                        "Expected to insert exactly one login audit record."
                );
            }
        }
    }


}
