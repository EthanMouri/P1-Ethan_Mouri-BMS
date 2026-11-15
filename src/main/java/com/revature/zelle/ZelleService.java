package com.revature.zelle;

import com.revature.account.Account;
import com.revature.customer.Customer;
import com.revature.customer.CustomerDAO;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Business validation for simulated Zelle payment requests.
 * No real Zelle network is contacted and no funds are moved here.
 */
public class ZelleService {

    private final CustomerDAO customerDAO;
    private final ZellePaymentRequestDAO requestDAO;
    private final ZellePaymentDAO paymentDAO;
    private final ZelleDirectPaymentDAO directPaymentDAO;

    /** Creates a service backed by PostgreSQL. */
    public ZelleService() {
        this(new CustomerDAO(), new ZellePaymentRequestDAO(), new ZellePaymentDAO());
    }

    /** Creates a service with injectable DAOs for unit tests. */
    public ZelleService(CustomerDAO customerDAO, ZellePaymentRequestDAO requestDAO) {
        this(customerDAO, requestDAO, new ZellePaymentDAO());
    }

    /**
     * Creates a service with mockable DAOs for payment approval tests.
     *
     * @param customerDAO finds customers by username
     * @param requestDAO stores and retrieves payment requests
     * @param paymentDAO approves payments in one database transaction
     */
    public ZelleService(CustomerDAO customerDAO, ZellePaymentRequestDAO requestDAO,
                        ZellePaymentDAO paymentDAO) {
        this(customerDAO, requestDAO, paymentDAO, new ZelleDirectPaymentDAO());
    }

    /**
     * Creates a service with all DAOs supplied for unit tests.
     *
     * @param customerDAO looks up customer usernames
     * @param requestDAO manages pending payment requests
     * @param paymentDAO approves payment requests
     * @param directPaymentDAO sends direct payments
     */
    public ZelleService(CustomerDAO customerDAO, ZellePaymentRequestDAO requestDAO,
                        ZellePaymentDAO paymentDAO, ZelleDirectPaymentDAO directPaymentDAO) {
        if (customerDAO == null || requestDAO == null || paymentDAO == null || directPaymentDAO == null) {
            throw new IllegalArgumentException("All Zelle DAOs are required.");
        }
        this.customerDAO = customerDAO;
        this.requestDAO = requestDAO;
        this.paymentDAO = paymentDAO;
        this.directPaymentDAO = directPaymentDAO;
    }

    /**
     * Creates a pending request addressed to an existing active customer by username.
     * The requester must be the authenticated customer supplied by the caller.
     * This method does not send or transfer money.
     *
     * @return the generated payment request ID
     */
    public long requestMoney(Customer requester, String payerUsername,
                             BigDecimal amount, String description) throws SQLException {
        requireActiveCustomer(requester);
        if (payerUsername == null || payerUsername.isBlank()) {
            throw new IllegalArgumentException("Payer username is required.");
        }
        String normalizedUsername = payerUsername.trim().toLowerCase(java.util.Locale.ROOT);
        if (amount == null || amount.signum() <= 0 ||
                amount.stripTrailingZeros().scale() > 2 ||
                amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Amount must be positive and have at most two decimal places.");
        }
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("Description cannot exceed 255 characters.");
        }
        Customer payer = customerDAO.findCustomerByUsername(normalizedUsername);
        if (payer == null) {
            throw new IllegalArgumentException("Payer username was not found.");
        }
        requireActiveCustomer(payer);
        if (payer.getId() == requester.getId()) {
            throw new IllegalArgumentException("You cannot request money from yourself.");
        }
        return requestDAO.createRequest(requester.getId(), payer.getId(), amount, description);
    }

    /** Returns pending requests received by an active customer across all their accounts. */
    public List<ZellePaymentRequest> getPendingRequests(Customer payer) throws SQLException {
        requireActiveCustomer(payer);
        return requestDAO.findPendingRequestsForPayer(payer.getId());
    }

    /** Looks up a request visible to an active requester or payer. */
    public ZellePaymentRequest getRequest(Customer customer, long requestId) throws SQLException {
        requireActiveCustomer(customer);
        if (requestId <= 0) {
            throw new IllegalArgumentException("Valid request ID is required.");
        }
        return requestDAO.findRequestForCustomer(requestId, customer.getId());
    }

    /**
     * Approves a pending request using the payer's chosen bank account.
     * The DAO checks the latest balances and completes the transfer safely.
     *
     * @param payer the logged-in customer paying the request
     * @param fundingAccount the Checking or Savings Account used to pay
     * @param requestId the payment request to approve
     * @return the completed Zelle transaction ID
     * @throws SQLException if the payment fails or cannot be approved
     */
    public long approveRequest(Customer payer, Account fundingAccount, long requestId)
            throws SQLException {
        //Only active customers can approve requests.
        requireActiveCustomer(payer);

        //Check the request ID before calling the database.
        if (requestId <= 0) {
            throw new IllegalArgumentException("Valid payment request ID is required.");
        }

        //The selected account must belong to the logged-in customer.
        if (fundingAccount == null || fundingAccount.getId() <= 0
                || fundingAccount.getAccountOwnerName() == null
                || fundingAccount.getAccountOwnerName().getId() != payer.getId()) {
            throw new IllegalArgumentException("Select an account that belongs to you.");
        }

        //Zelle payments can only use active Checking or Savings Accounts.
        if (!("CHECKING".equals(fundingAccount.getAccountType())
                || "SAVINGS".equals(fundingAccount.getAccountType()))
                || !"ACTIVE".equals(fundingAccount.getStatus())) {
            throw new IllegalArgumentException("Select an active Checking or Savings Account.");
        }

        //PostgreSQL rechecks ownership, account status, and available funds.
        //The DAO also prevents the same request from being paid twice.
        return paymentDAO.approveRequest(requestId, payer.getId(), fundingAccount.getId());
    }

    /**
     * Rejects a pending request sent to the logged-in customer.
     * No money is transferred when a request is rejected.
     *
     * @param payer the logged-in customer rejecting the request
     * @param requestId the payment request to reject
     * @throws SQLException if the request cannot be rejected
     */
    public void rejectRequest(Customer payer, long requestId) throws SQLException {
        //Only active customers may respond to payment requests.
        requireActiveCustomer(payer);

        //Stop invalid request IDs before reaching the database.
        if (requestId <= 0) {
            throw new IllegalArgumentException("Valid payment request ID is required.");
        }

        //The DAO verifies payer ownership and that the request is still pending.
        requestDAO.rejectRequest(requestId, payer.getId());
    }

    /**
     * Sends a demo Zelle payment directly to another customer by username.
     * The database checks the latest balances and transfers money atomically.
     *
     * @param sender the logged-in customer sending money
     * @param fundingAccount the sender's chosen Checking or Savings Account
     * @param recipientUsername the customer receiving the payment
     * @param amount the amount to send
     * @param description an optional payment description
     * @return the new transaction ID
     * @throws SQLException if the transfer fails
     */
    public long sendMoney(Customer sender, Account fundingAccount, String recipientUsername,
                          BigDecimal amount, String description) throws SQLException {
        //Only active customers can send demo Zelle payments.
        requireActiveCustomer(sender);
        if (fundingAccount == null || fundingAccount.getId() <= 0
                || fundingAccount.getAccountOwnerName() == null
                || fundingAccount.getAccountOwnerName().getId() != sender.getId()) {
            throw new IllegalArgumentException("Select an account that belongs to you.");
        }
        if (!("CHECKING".equals(fundingAccount.getAccountType())
                || "SAVINGS".equals(fundingAccount.getAccountType()))
                || !"ACTIVE".equals(fundingAccount.getStatus())) {
            throw new IllegalArgumentException("Select an active Checking or Savings Account.");
        }
        if (recipientUsername == null || recipientUsername.isBlank()) {
            throw new IllegalArgumentException("Recipient username is required.");
        }
        if (amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Amount must be positive and have at most two decimal places.");
        }
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("Description cannot exceed 255 characters.");
        }

        //Find the recipient by their unique username.
        String username = recipientUsername.trim().toLowerCase(java.util.Locale.ROOT);
        Customer recipient = customerDAO.findCustomerByUsername(username);
        if (recipient == null) {
            throw new IllegalArgumentException("Recipient username was not found.");
        }
        requireActiveCustomer(recipient);
        if (recipient.getId() == sender.getId()) {
            throw new IllegalArgumentException("You cannot send money to yourself.");
        }

        //PostgreSQL rechecks account ownership, status, and available funds.
        return directPaymentDAO.sendMoney(sender.getId(), fundingAccount.getId(),
                recipient.getId(), amount, description);
    }

    private void requireActiveCustomer(Customer customer) {
        if (customer == null || customer.getId() <= 0 ||
                !"ACTIVE".equals(customer.getStatus())) {
            throw new IllegalArgumentException("An active customer is required.");
        }
    }
}
