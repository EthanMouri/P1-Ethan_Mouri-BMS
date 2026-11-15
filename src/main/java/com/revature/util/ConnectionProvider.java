package com.revature.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides a database connection to your application.
 * For unit tests to supply mocked connections.
 */
@FunctionalInterface
public interface ConnectionProvider {

    /**
     * Obtains a database connection.
     *
     * @return an open database connection
     * @throws SQLException if a connection cannot be established
     */
    Connection getConnection() throws SQLException;
}
