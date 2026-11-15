package com.revature.customer;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Performs customer relationship closure after checking eligibility.
 */
public class CustomerClosureService {
    private final CustomerDAO customerDAO;
    private final CustomerClosureEligibilityService eligibilityService;
    private final CustomerClosureDAO closureDAO;

    /**
     * Creates a service connected to the application database.
     */
    public CustomerClosureService() {
        this(new CustomerDAO(), new CustomerClosureEligibilityService(), new CustomerClosureDAO());
    }

    /**
     * Accepts collaborators for unit testing.
     *
     * @param customerDAO customer lookup DAO
     * @param eligibilityService read-only eligibility checker
     * @param closureDAO guarded database update DAO
     */
    public CustomerClosureService(CustomerDAO customerDAO,
                                  CustomerClosureEligibilityService eligibilityService,
                                  CustomerClosureDAO closureDAO) {
        if (customerDAO == null || eligibilityService == null || closureDAO == null) {
            throw new IllegalArgumentException("Customer closure dependencies cannot be null.");
        }
        this.customerDAO = customerDAO;
        this.eligibilityService = eligibilityService;
        this.closureDAO = closureDAO;
    }

    /**
     * Attempts closure without deleting the customer's financial history.
     *
     * @param username customer username
     * @return true if closure completed, false if eligibility changed before the update
     * @throws SQLException if the database fails
     */
    public boolean closeCustomer(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);

        //Provide a useful eligibility error before attempting the guarded update.
        CustomerClosureEligibility eligibility = eligibilityService.checkEligibility(normalized);
        if (!eligibility.eligible()) {
            throw new IllegalStateException(String.join(" ", eligibility.reasons()));
        }

        Customer customer = customerDAO.findCustomerByUsername(normalized);
        if (customer == null) {
            throw new IllegalArgumentException("Customer was not found.");
        }

        //The DAO rechecks all database conditions when updating the status.
        return closureDAO.closeEligibleCustomer(customer.getId());
    }
}
