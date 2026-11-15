package com.revature.customer;
import java.time.OffsetDateTime;

public class Customer {

    private long id;

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String username;
    private String hashedPassword;

    private String status;
    private int failedLoginCount;
    private OffsetDateTime lockedUntil;

    // Constructor for a customer who has not yet been saved
    // PostgreSQL will generate the customer ID
    public Customer(String firstName, String lastName, String email, String phoneNumber, String username, String hashedPassword) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.hashedPassword = hashedPassword;
    }

    // CUSTOMER ID GETTER AND SETTER

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    // FULL NAME GETTER

    public String getCustomerFullName() {
        return firstName + " " + lastName;
    }

    // FIRST NAME GETTER AND SETTER

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    // LAST NAME GETTER AND SETTER
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    // EMAIL GETTER AND SETTER
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // PHONE NUMBER GETTER AND SETTER
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    // USERNAME GETTER AND SETTER
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    // PASSWORD HASH GETTER AND SETTER
    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    //CUSTOMER ACCOUNT STATUS GETTER AND SETTER
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    //FAILED LOGIN COUNT GETTER AND SETTER
    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    //ACCOUNT LOCKED UNTIL GETTER AND SETTER
    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

}
