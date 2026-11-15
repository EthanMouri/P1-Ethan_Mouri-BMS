package com.revature.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    //This is the connection string to connect to the database in java with jdbc
    private static final String URL =
            "jdbc:postgresql://localhost:5432/banking_app";

    //this is the user I set up (in postgres CLI) to access the postgres database
    private static final String USER = "bms_owner";

    //Gets a connection to the PostgreSQL Database
    public static Connection getConnection() throws SQLException {
        String password = System.getenv("DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "DB_PASSWORD environment variable is not set"
            );
        }
                //this method asks the PostgreSQL JDBC driver to
                    //establish a connection with your database
        return DriverManager.getConnection(URL, USER, password);
    }
}
