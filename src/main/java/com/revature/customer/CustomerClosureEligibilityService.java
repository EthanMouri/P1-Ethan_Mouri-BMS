package com.revature.customer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks whether a customer has met the requirements for relationship closure.
 * This checkpoint only checks eligibility; it never closes a customer.
 */
public class CustomerClosureEligibilityService {

    private final CustomerDAO customerDAO;
    private final CustomerClosureEligibilityDAO eligibilityDAO;

    /**
     * Creates the service for the real database.
     */
    public CustomerClosureEligibilityService() {
        this(new CustomerDAO(), new CustomerClosureEligibilityDAO());
    }

    /**
     * Creates the service with supplied DAOs for unit testing.
     *
     * @param customerDAO DAO used to find the customer
     * @param eligibilityDAO DAO used to check outstanding obligations
     */
    public CustomerClosureEligibilityService(CustomerDAO customerDAO,
                                             CustomerClosureEligibilityDAO eligibilityDAO) {
        if (customerDAO == null || eligibilityDAO == null) {
            throw new IllegalArgumentException("Customer closure DAOs cannot be null.");
        }
        this.customerDAO = customerDAO;
        this.eligibilityDAO = eligibilityDAO;
    }

    /**
     * Checks a customer without changing any database records.
     *
     * @param username username of the customer to check
     * @return eligibility and any reasons blocking closure
     * @throws SQLException if a database operation fails
     */
    public CustomerClosureEligibility checkEligibility(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }

        //Find the customer before checking their accounts.
        Customer customer = customerDAO.findCustomerByUsername(username.trim().toLowerCase(java.util.Locale.ROOT));
        if (customer == null) {
            throw new IllegalArgumentException("Customer was not found.");
        }

        List<String> reasons = new ArrayList<>();
        if (!"ACTIVE".equals(customer.getStatus())) {
            reasons.add("Customer status must be ACTIVE.");
        }

        //Every financial account must be closed before the relationship can close.
        if (eligibilityDAO.countOpenAccounts(customer.getId()) > 0) {
            reasons.add("Customer still has open accounts, including loans or investments.");
        }

        //Protect against remaining balances, even on an incorrectly closed account.
        if (eligibilityDAO.countNonzeroBalances(customer.getId()) > 0) {
            reasons.add("Customer still has account balances that are not zero.");
        }

        //Both outgoing and incoming payment requests must be resolved.
        if (eligibilityDAO.countPendingZelleRequests(customer.getId()) > 0) {
            reasons.add("Customer still has pending Zelle payment requests.");
        }

        return new CustomerClosureEligibility(reasons.isEmpty(), reasons);
    }
}
