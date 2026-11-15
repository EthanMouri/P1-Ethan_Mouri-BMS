
package com.revature.account;

import com.revature.customer.Customer;

/**
 * Represents a current (checking) bank account.
 */
public class CheckingAccount extends Account {

    /**
     * Creates a checking account belonging to a customer.
     *
     * @param accountOwner the customer who owns the account
     */
    public CheckingAccount(Customer accountOwner) {
        super(accountOwner);
    }


    /**
     * Gets the PostgreSQL account type for checking accounts.
     *
     * @return CHECKING
     */
    @Override
    public String getAccountType() {
        return "CHECKING";
    }

}
