package com.revature;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppErrorMessageTest {
    @Test
    void preservesBusinessValidation() {
        assertEquals("Insufficient available funds.",
                App.customerSafeMessage(new IllegalArgumentException("Insufficient available funds.")));
    }

    @Test
    void suppressesSqlDetails() {
        assertEquals("Something went wrong. Please try again.",
                App.customerSafeMessage(new IllegalStateException("Unable to process", new SQLException("secret SQL details"))));
    }

    @Test
    void suppressesExceptionDetailsAndMultilineMessages() {
        assertEquals("Something went wrong. Please try again.",
                App.customerSafeMessage(new IllegalArgumentException("org.postgresql.util.PSQLException: duplicate key")));
        assertEquals("Something went wrong. Please try again.",
                App.customerSafeMessage(new IllegalArgumentException("invalid input\ninternal details")));
    }
}
