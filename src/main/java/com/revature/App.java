package com.revature;

import com.revature.account.*;
import com.revature.account.investment.InvestmentAccount;
import com.revature.util.PasswordValidator;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.revature.customer.CustomerService;
import com.revature.session.SessionManager;
import com.revature.session.SessionLineReader;
import com.revature.session.SessionExpiredException;
import com.revature.customer.CustomerClosureService;
import com.revature.customer.CustomerClosureEligibilityService;
import com.revature.customer.CustomerClosureEligibility;
import com.revature.zelle.ZelleService;
import com.revature.zelle.ZellePaymentRequest;
import com.revature.transactions.TransactionService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import com.revature.transactions.TransactionHistoryEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

//JLine Imports
import com.revature.util.TerminalConnector;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import com.revature.customer.Customer;

/**
 * Banking CLI!
 */
public class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    /** Only expected business validation messages may reach the customer. */
    static String customerSafeMessage(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException) {
                return "Something went wrong. Please try again.";
            }
            cause = cause.getCause();
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank() || message.length() > 180
                || message.contains("\n") || message.contains("\r")
                || message.matches("(?is).*(sql|jdbc|postgres|database|constraint|exception|stack trace|select |insert |update |delete |\\btable\\b|\\bcolumn\\b).*")) {
            return "Something went wrong. Please try again.";
        }
        return message;
    }

    private static final CustomerService customerService = new CustomerService();
    private static final AccountService accountService = new AccountService();
    private static final TransactionService transactionService = new TransactionService();
    private static final ZelleService zelleService = new ZelleService();
    private static final CustomerClosureService customerClosureService = new CustomerClosureService();
    private static final CustomerClosureEligibilityService customerClosureEligibilityService = new CustomerClosureEligibilityService();

    public static void main(String[] args) {
        //Connect to terminal
        try (Terminal terminal = TerminalConnector.connectToCLI()) {
            LineReader userInput = TerminalConnector.createLineReader(terminal);
            //Go through the main menu, repeatedly for now will fix later
            while (true) {
                mainMenu(userInput);
            }
        }
        catch (IOException exception) {
            LOGGER.error("Unable to initialize CLI terminal", exception);
            System.err.println("Unable to start the terminal. Please try again.");
        }

    }

    /** Displays the branded public welcome screen on launch and after sign-out. */
    private static void printWelcomeBanner() {
        System.out.println();
        System.out.println("╭──────────────────────────────────────╮");
        System.out.println("│                                      │");
        System.out.println("│          K A K U Z U                 │");
        System.out.println("│           C A P I T A L              │");
        System.out.println("│                                      │");
        System.out.println("╰──────────────────────────────────────╯");
        System.out.println();
        System.out.println("  Welcome to Kakuzu Capital.");
        System.out.println("  A smarter way to manage your money.");
        System.out.println();
        System.out.println("  A Revature Banking Management System");
        System.out.println("  Developed by Ethan Mouri");
        System.out.println();
    }

    /** Displays a consistent modern heading for authenticated and public submenus. */
    private static void printSection(String title) {
        System.out.println("\nKAKUZU CAPITAL  /  " + title.toUpperCase(java.util.Locale.ROOT));
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private static void mainMenu(LineReader userInput) {
        printWelcomeBanner();
        System.out.println("  GET STARTED");
        System.out.println("  ──────────────────────────────────────");
        System.out.println("  [1]  Become a Member of Kakuzu Capital");
        System.out.println("  [2]  Sign In");
        System.out.println("  [3]  Exit");
        int menuOption = 0;

        //Read the customer's menu selection.
        try {
            menuOption = Integer.parseInt(userInput.readLine("› Choose an option: ").trim());
        }
        catch (NumberFormatException exception) {
            //Show an error instead of crashing when the customer enters something other than a number.
            System.out.println("That option isn't available. Please choose one from the menu.");
            return;
        }
        switch (menuOption) {
            case 1:
                registerAccount(userInput);
                break;
            case 2:
                loginToAccount(userInput);
                break;
            case 3:
                exitApplication(userInput);
                break;
            default:
                System.out.println("That option isn't available. Please choose one from the menu.");
        }
    }




    /**
     * Allows a customer to log in using their username and password.
     * Checks their credentials and whether their account is locked.
     *
     * @param userInput the terminal reader used to collect login information
     */
    private static void loginToAccount(LineReader userInput) {
        printSection("Sign In");

        //Ask the customer for their username and password.
        String username = userInput.readLine("Enter your username: ");
        String password = userInput.readLine("Enter your password: ", '*');

        try {
            //Check the customer's login information using the secure login method.
            Customer customer = customerService.loginCustomerSecurely(username, password);

            if (customer != null) {
                //The customer entered the correct password and is not locked out.
                System.out.println("Welcome back, " + customer.getCustomerFullName() + ". You're now signed in.");
                //Start a fresh five-minute session for this successful login.
                SessionManager session = new SessionManager();
                try {
                    //Check inactivity at every authenticated CLI input, including nested menus.
                    showAccountMenu(SessionLineReader.protect(userInput, session), customer);
                } catch (SessionExpiredException exception) {
                    //Return to the public login menu without performing the expired action.
                    System.out.println("Your session ended after 5 minutes of inactivity. Please sign in again.");
                }
            } else {
                //The login failed because the credentials are wrong or the account is locked.
                System.out.println("We couldn't sign you in. Check your credentials or try again later.");
            }
        }
        catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Do not show internal database error details to the customer.
            System.out.println("Login could not be completed due to a database error.");
        }
    }


    private static void registerAccount(LineReader userInput) {
        printSection("Become a Member");
        System.out.println("Let's get you started.");

        String username = userInput.readLine("Enter a username: ");
        String password = createPassword(userInput, username);

        System.out.println();

        String firstName = userInput.readLine("Enter your first name: ");
        String lastName = userInput.readLine("Enter your last name: ");
        String email = userInput.readLine("Enter your email: ");
        String phoneNumber = userInput.readLine("Enter your phone number: ");

        //register the customer in the database
        try {
            long customerId = customerService.registerCustomer(firstName, lastName, email, phoneNumber, username, password);

            System.out.println("Your banking profile is ready. You can now sign in.");
            System.out.println("Your customer ID is: " + customerId);

        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            // Invalid registration information or duplicate customer
            System.out.println("Registration failed: " + customerSafeMessage(exception));

        }
        catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            // Database failure; don't expose internal SQL details to the user
            System.out.println("Registration could not be completed due to a database error.");
        }
    }


    private static String createPassword(LineReader userInput, String username) {
        //Keep retrying until a valid password is created
        while (true) {
            String password = userInput.readLine("Enter a password: ", '*');

            String passwordConfirmed = userInput.readLine("Please confirm your password: ", '*');

            //check if entered passwords match each other
            if (!password.equals(passwordConfirmed)) {
                System.out.println("Passwords do not match. Please try again.");
                continue;
            }

            //passwords are valid and matching
            if (PasswordValidator.isValidPassword(password, username)) {
                return password;
            }

            //Invalid password was entered
            System.out.println("Your password does not meet the requirements. Please try again.");
        }
    }

    private static void exitApplication(LineReader userInput) {
        System.out.println("Thanks for choosing Kakuzu Capital. Until next time!");
        System.exit(0);
        //close the scanner the application is using
        //userInput.close(); <-- this causes an error to be thrown
    }



    /**
     * Returns the choices shown when closing the customer relationship.
     *
     * @return closure confirmation choices
     */
    static List<String> getCustomerClosureConfirmationOptions() {
        return List.of("Confirm Membership Closure", "Cancel");
    }

    /**
     * Checks closure requirements and asks for explicit confirmation.
     *
     * @param reader terminal reader
     * @param customer logged-in customer
     * @return true only after the customer has been closed
     */
    private static boolean showCustomerClosureMenu(LineReader reader, Customer customer) {
        printSection("Close My Membership");
        System.out.println("We're sorry to see you go.");
        try {
            //Show every outstanding requirement before allowing closure.
            CustomerClosureEligibility eligibility =
                    customerClosureEligibilityService.checkEligibility(customer.getUsername());
            if (!eligibility.eligible()) {
                System.out.println("Your membership cannot be closed yet:");
                for (String reason : eligibility.reasons()) {
                    System.out.println("- " + reason);
                }
                return false;
            }

            System.out.println("You are eligible to close your membership.");
            System.out.println("Closing your membership will end your access to Kakuzu Capital and cannot be undone here.");
            List<String> choices = getCustomerClosureConfirmationOptions();
            for (int index = 0; index < choices.size(); index++) {
                System.out.println((index + 1) + ". " + choices.get(index));
            }
            String selection = reader.readLine("› Choose an option: ").trim();
            if (!"1".equals(selection)) {
                System.out.println("Membership closure canceled.");
                return false;
            }
            //Require a second confirmation before changing customer status.
            String confirmation = reader.readLine("Type CLOSE to confirm: ").trim();
            if (!"CLOSE".equals(confirmation)) {
                System.out.println("Membership closure canceled.");
                return false;
            }
            if (customerClosureService.closeCustomer(customer.getUsername())) {
                System.out.println("Your membership is now closed. Thank you for banking with Kakuzu Capital.");
                return true;
            }
            System.out.println("Closure requirements changed. Please review your accounts and try again.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Your membership could not be closed: " + customerSafeMessage(exception));
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            System.out.println("Unable to close your membership right now. Please try again later.");
        }
        return false;
    }

    /**
     * Displays the logged-in customer's accounts and allows them to log out.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     */
    private static void showAccountMenu(LineReader lineReader, Customer customer) {
        //Keep the customer in the account menu until they choose to log out.
        while (true) {
            try {
                //Retrieve the customer's accounts from PostgreSQL.
                List<Account> accounts = accountService.getAccountsForCustomer(customer);

                printSection("My Accounts");
                System.out.println("Welcome back, " + customer.getCustomerFullName() + ".");
                System.out.println("MY BANKING");
                System.out.println("──────────────────────────────────────");

                //Display each account with a number, type, and account number.
                if (accounts.isEmpty()) {
                    System.out.println("You do not have any accounts yet.");
                } else {
                    for (int index = 0; index < accounts.size(); index++) {
                        Account account = accounts.get(index);

                        System.out.printf("%d. %s Account #%s%n",
                                index + 1, account.getAccountType(), account.getAccountNumber());
                    }
                }

                //Place account creation and logout after the customer's accounts.
                int openAccountOption = accounts.size() + 1;
                int closureOption = accounts.size() + 2;
                int logoutOption = accounts.size() + 3;

                System.out.println(openAccountOption + ". Open a New Account");
                System.out.println(closureOption + ". Close My Membership");
                System.out.println(logoutOption + ". Sign Out");

                //Read the customer's selection.
                String input = lineReader.readLine("› Choose an option: ").trim();
                int selection = Integer.parseInt(input);

                //Return to the main menu when the customer logs out.
                if (selection == logoutOption) {
                    System.out.println("You've signed out successfully. See you again soon!");
                    return;
                }

                //End the session immediately after a successful relationship closure.
                if (selection == closureOption) {
                    if (showCustomerClosureMenu(lineReader, customer)) {
                        return;
                    }
                    continue;
                }

                //Open the account creation menu when requested.
                if (selection == openAccountOption) {
                    showOpenAccountMenu(lineReader, customer);
                    continue;
                }

                //Open the selected account's menu.
                if (selection >= 1 && selection <= accounts.size()) {
                    Account selectedAccount = accounts.get(selection - 1);
                    showSelectedAccountMenu(lineReader, customer, selectedAccount);
                }
                else {
                    System.out.println("That option isn't available. Please choose one from the menu.");
                }

            } catch (NumberFormatException exception) {
                //Handle input that is not a valid menu number.
                System.out.println("That option isn't available. Please choose one from the menu.");
            }
            catch (SQLException exception) {
                LOGGER.error("Banking operation failed", exception);
                //Return to the main menu if accounts cannot be retrieved.
                System.out.println("Unable to load your accounts. Please try again later.");
                return;
            }
        }
    }


    /**
     * Displays the available account types for an authenticated customer.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer opening an account
     */
    private static void showOpenAccountMenu(LineReader lineReader, Customer customer) {
        //Keep showing the menu until the customer chooses to go back.
        while (true) {
            printSection("Open a New Account");
            System.out.println("1. Checking Account");
            System.out.println("2. Savings Account");
            System.out.println("3. Investment Account");
            System.out.println("4. Loan Account");
            System.out.println("5. Back to My Accounts");

            //Read the customer's menu selection.
            String input = lineReader.readLine("› Choose an option: ").trim();

            try {
                int selection = Integer.parseInt(input);

                //Return to the customer's account list.
                if (selection == 5) {
                    return;
                }

                //Create a Checking Account when option 1 is selected.
                if (selection == 1) {
                    try {
                        CheckingAccount checkingAccount = accountService.openCheckingAccount(customer);

                        System.out.println("\nChecking Account created successfully!");
                        System.out.println("Account Number: " + checkingAccount.getAccountNumber());
                        System.out.println("Starting Balance: $" + checkingAccount.getBalance());

                        //Return to the account list so the new account appears.
                        return;
                    }
                    catch (SQLException exception) {
                        LOGGER.error("Banking operation failed", exception);
                        //Report a database error without closing the logged-in menu.
                        System.out.println("Unable to create Checking Account. Please try again.");
                    }

                }
                else if (selection == 2) {
                    //Create a Savings Account for the authenticated customer.
                    try {
                        SavingsAccount savingsAccount = accountService.openSavingsAccount(customer);

                        System.out.println("\nSavings Account created successfully!");
                        System.out.println("Account Number: " + savingsAccount.getAccountNumber());
                        System.out.println("Starting Balance: $" + savingsAccount.getBalance());
                        System.out.println("Annual Interest Rate: " + savingsAccount.getInterestRate() + "%");

                        //Return to the account list so the new account appears.
                        return;
                    }
                    catch (SQLException exception) {
                        LOGGER.error("Banking operation failed", exception);
                        //Report a database error without closing the logged-in menu.
                        System.out.println("Unable to create Savings Account. Please try again.");
                    }

                }
                else if (selection == 3) {
                    //Ask the customer for the intended investment principal and term.
                    try {
                        String principalInput = lineReader.readLine("Investment amount ($): ").trim();
                        BigDecimal principal = new BigDecimal(principalInput);

                        String termInput = lineReader.readLine("Investment term (months): ").trim();
                        int termMonths = Integer.parseInt(termInput);

                        //Create the Investment Account using one database transaction.
                        InvestmentAccount investmentAccount = accountService.openInvestmentAccount(
                                customer, principal, termMonths);

                        System.out.println("\nInvestment Account created successfully!");
                        System.out.println("Account Number: " + investmentAccount.getAccountNumber());
                        System.out.println("Investment Principal: $" + investmentAccount.getPrincipal());
                        System.out.println("Annual Interest Rate: " + investmentAccount.getInterestRate() + "%");
                        System.out.println("Withdrawal Unlock Date: "
                                + investmentAccount.getWithdrawalUnlockedDate());
                        System.out.println("Current Balance: $" + investmentAccount.getBalance());

                        //Return to the account list so the new account appears.
                        return;
                    } catch (NumberFormatException exception) {
                        //Reject amounts or terms that cannot be converted to numbers.
                        System.out.println("Please enter a valid investment amount and term.");
                    } catch (IllegalArgumentException exception) {
                        //Display validation errors for invalid investment details.
                        System.out.println(customerSafeMessage(exception));
                    }
                    catch (SQLException exception) {
                        LOGGER.error("Banking operation failed", exception);
                        //Report database failures without leaving the logged-in menu.
                        System.out.println("Unable to create Investment Account. Please try again.");
                    }

                }
                else if (selection == 4) {
                    //Ask the customer for the loan principal and repayment period.
                    try {
                        String principalInput = lineReader.readLine("Loan amount ($): ").trim();
                        BigDecimal principalAmount = new BigDecimal(principalInput);

                        String repaymentInput = lineReader.readLine("Repayment period (months): ").trim();
                        int repaymentPeriodInMonths = Integer.parseInt(repaymentInput);

                        //Create the Loan Account using one database transaction.
                        LoanAccount loanAccount = accountService.openLoanAccount(
                                customer, principalAmount, repaymentPeriodInMonths);

                        System.out.println("\nLoan Account created successfully!");
                        System.out.println("Account Number: " + loanAccount.getAccountNumber());
                        System.out.println("Loan Principal: $" + loanAccount.getPrincipalAmount());
                        System.out.println("Annual Interest Rate: " + loanAccount.getAnnualInterestRate() + "%");
                        System.out.println("Repayment Period: " + loanAccount.getRepaymentPeriodInMonths() + " months");
                        System.out.println("Loan Start Date: " + loanAccount.getLoanStartDate());
                        System.out.println("Current Balance: $" + loanAccount.getBalance());

                        //Return to the account list so the new account appears.
                        return;
                    } catch (NumberFormatException exception) {
                        //Reject loan amounts or repayment periods that are not valid numbers.
                        System.out.println("Please enter a valid loan amount and repayment period.");
                    } catch (IllegalArgumentException exception) {
                        //Display validation errors for invalid loan details.
                        System.out.println(customerSafeMessage(exception));
                    }
                    catch (SQLException exception) {
                        LOGGER.error("Banking operation failed", exception);
                        //Report database failures without leaving the logged-in menu.
                        System.out.println("Unable to create Loan Account. Please try again.");
                    }
                }
                else {
                    System.out.println("That option isn't available. Please choose one from the menu.");
                }
            } catch (NumberFormatException exception) {
                //Reject input that is not a valid menu number.
                System.out.println("That option isn't available. Please choose one from the menu.");
            }
        }
    }


    /**
     * Converts customer input into a valid deposit amount.
     *
     * @param input the amount entered by the customer
     * @return the validated deposit amount
     * @throws IllegalArgumentException if the amount is invalid
     */
    static BigDecimal parseDepositAmount(String input) {
        //Reject empty amounts before attempting to convert them.
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a valid deposit amount.");
        }

        try {
            BigDecimal amount = new BigDecimal(input.trim());

            //Deposits must be positive and cannot contain fractions of a cent.
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Enter a positive amount with no more than two decimal places.");
            }

            //PostgreSQL stores account balances with at most 17 digits before the decimal point.
            if (amount.setScale(2).precision() > 19) {
                throw new IllegalArgumentException("Deposit amount is too large.");
            }

            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Please enter a valid deposit amount.", exception);
        }
    }


    /**
     * Converts customer input into a valid withdrawal amount.
     *
     * @param input the amount entered by the customer
     * @return the validated withdrawal amount
     * @throws IllegalArgumentException if the amount is invalid
     */
    static BigDecimal parseWithdrawalAmount(String input) {
        //Reject empty amounts before attempting to convert them.
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a valid withdrawal amount.");
        }

        try {
            BigDecimal amount = new BigDecimal(input.trim());

            //Withdrawals must be positive and cannot contain fractions of a cent.
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "Enter a positive amount with no more than two decimal places.");
            }

            //PostgreSQL stores monetary amounts with at most 17 digits before the decimal point.
            if (amount.setScale(2).precision() > 19) {
                throw new IllegalArgumentException("Withdrawal amount is too large.");
            }

            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Please enter a valid withdrawal amount.", exception);
        }
    }


    /**
     * Converts customer input into a valid transfer amount.
     *
     * @param input the amount entered by the customer
     * @return the validated transfer amount
     * @throws IllegalArgumentException if the amount is invalid
     */
    static BigDecimal parseTransferAmount(String input) {
        //Reject empty amounts before attempting to convert them.
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a valid transfer amount.");
        }

        try {
            BigDecimal amount = new BigDecimal(input.trim());

            //Transfers must be positive and cannot contain fractions of a cent.
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "Enter a positive amount with no more than two decimal places.");
            }

            //PostgreSQL stores monetary amounts with at most 17 digits before the decimal point.
            if (amount.setScale(2).precision() > 19) {
                throw new IllegalArgumentException("Transfer amount is too large.");
            }

            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Please enter a valid transfer amount.", exception);
        }
    }


    /**
     * Converts customer input into a valid loan repayment amount.
     *
     * @param input the amount entered by the customer
     * @return the validated repayment amount
     * @throws IllegalArgumentException if the amount is invalid
     */
    static BigDecimal parseLoanRepaymentAmount(String input) {
        //Reject empty amounts before attempting to convert them.
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a valid loan repayment amount.");
        }

        try {
            BigDecimal amount = new BigDecimal(input.trim());

            //Loan repayments must be positive and cannot contain fractions of a cent.
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "Enter a positive amount with no more than two decimal places.");
            }

            //PostgreSQL stores monetary amounts with at most 17 digits before the decimal point.
            if (amount.setScale(2).precision() > 19) {
                throw new IllegalArgumentException("Loan repayment amount is too large.");
            }

            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Please enter a valid loan repayment amount.", exception);
        }
    }


    /**
     * Converts customer input into a valid Investment Account contribution amount.
     *
     * @param input the contribution amount entered by the customer
     * @return the validated contribution amount
     * @throws IllegalArgumentException if the amount is invalid
     */
    static BigDecimal parseInvestmentContributionAmount(String input) {
        //Reject empty amounts before attempting to convert them.
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Please enter a valid investment contribution amount.");
        }

        try {
            BigDecimal amount = new BigDecimal(input.trim());

            //Contributions must be positive and cannot contain fractions of a cent.
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "Enter a positive amount with no more than two decimal places.");
            }

            //PostgreSQL stores monetary amounts with at most 17 digits before the decimal point.
            if (amount.setScale(2).precision() > 19) {
                throw new IllegalArgumentException(
                        "Investment contribution amount is too large.");
            }

            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Please enter a valid investment contribution amount.", exception);
        }
    }


    /**
     * Prompts the customer to deposit money into a selected account.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param account the account receiving the deposit
     */
    private static void depositIntoAccount(LineReader lineReader, Customer customer, Account account) {
        //Ask for the deposit amount before changing any database records.
        String input = lineReader.readLine("Enter deposit amount ($): ");

        try {
            BigDecimal amount = parseDepositAmount(input);

            //The service updates the balance, transaction record, and audit together.
            long transactionId = transactionService.deposit(customer, account, amount);

            System.out.println("Deposit complete. Your balance has been updated.");
            System.out.println("Transaction ID: " + transactionId);

            //Retrieve the committed balance directly from PostgreSQL.
            BigDecimal updatedBalance = accountService.getAccountBalance(customer, account);
            System.out.println("Current Balance: $" + updatedBalance);
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            //Explain invalid deposits without exposing internal database details.
            System.out.println("Deposit failed: " + customerSafeMessage(exception));
        }
        catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Do not expose internal database errors to the customer.
            System.out.println("Unable to complete the deposit. Please try again later.");
        }
    }


    /**
     * Prompts the customer to withdraw money from a selected account.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param account the account being withdrawn from
     */
    private static void withdrawFromAccount(LineReader lineReader, Customer customer, Account account) {
        //Ask for the withdrawal amount before changing any database records.
        String input = lineReader.readLine("Enter withdrawal amount ($): ");

        try {
            BigDecimal amount = parseWithdrawalAmount(input);

            //The service updates the balance, transaction record, and audit together.
            long transactionId = transactionService.withdraw(customer, account, amount);

            System.out.println("Withdrawal complete. Your balance has been updated.");
            System.out.println("Transaction ID: " + transactionId);

            //Retrieve the committed balance directly from PostgreSQL.
            BigDecimal updatedBalance = accountService.getAccountBalance(customer, account);
            System.out.println("Current Balance: $" + updatedBalance);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            //Explain invalid withdrawals without exposing internal database details.
            System.out.println("Withdrawal failed: " + customerSafeMessage(exception));
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Do not expose internal database errors to the customer.
            System.out.println("Unable to complete the withdrawal. Please try again later.");
        }
    }


    /**
     * Transfers money from the selected account to another eligible
     * Checking or Savings Account owned by the authenticated customer.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param sourceAccount the account sending the money
     */
    private static void transferBetweenAccounts(LineReader lineReader, Customer customer,
                                                Account sourceAccount) {
        try {
            //Retrieve the customer's accounts from PostgreSQL.
            List<Account> accounts = accountService.getAccountsForCustomer(customer);

            //Keep only active Checking and Savings Accounts other than the source.
            List<Account> destinations = accounts.stream()
                    .filter(account -> account.getId() != sourceAccount.getId())
                    .filter(account -> "CHECKING".equals(account.getAccountType()) ||
                            "SAVINGS".equals(account.getAccountType()))
                    .filter(account -> "ACTIVE".equals(account.getStatus()))
                    .toList();

            //A transfer requires another eligible account.
            if (destinations.isEmpty()) {
                System.out.println("No eligible destination accounts are available.");
                return;
            }

            printSection("Transfer Cash");
            System.out.println("From: " + sourceAccount.getAccountType()
                    + " Account #" + sourceAccount.getAccountNumber());

            //Display the available destination accounts.
            for (int index = 0; index < destinations.size(); index++) {
                Account destination = destinations.get(index);
                System.out.printf("%d. %s Account #%s%n",
                        index + 1, destination.getAccountType(),
                        destination.getAccountNumber());
            }

            System.out.println((destinations.size() + 1) + ". Cancel Transfer");

            //Read the customer's destination selection.
            String selectionInput = lineReader.readLine("Choose destination account: ").trim();
            int selection;

            try {
                selection = Integer.parseInt(selectionInput);
            } catch (NumberFormatException exception) {
                //Reject nonnumeric destination selections.
                System.out.println("Please enter a valid destination number.");
                return;
            }

            //Allow the customer to cancel before entering an amount.
            if (selection == destinations.size() + 1) {
                System.out.println("Transfer cancelled.");
                return;
            }

            //Reject selections outside the destination list.
            if (selection < 1 || selection > destinations.size()) {
                System.out.println("Invalid destination account.");
                return;
            }

            Account destinationAccount = destinations.get(selection - 1);

            //Ask for the transfer amount before changing any database records.
            String amountInput = lineReader.readLine("Enter transfer amount ($): ");
            BigDecimal amount = parseTransferAmount(amountInput);

            //The service updates both balances, transaction record, and audit together.
            long transactionId = transactionService.transfer(
                    customer, sourceAccount, destinationAccount, amount);

            System.out.println("Transfer complete. Your balances have been updated.");
            System.out.println("Transaction ID: " + transactionId);

            //Retrieve both committed balances directly from PostgreSQL.
            BigDecimal sourceBalance = accountService.getAccountBalance(customer, sourceAccount);
            BigDecimal destinationBalance = accountService.getAccountBalance(customer, destinationAccount);

            System.out.println("Source Account Balance: $" + sourceBalance);
            System.out.println("Destination Account Balance: $" + destinationBalance);

        } catch (IllegalArgumentException | IllegalStateException exception) {
            //Explain invalid transfer requests without exposing database details.
            System.out.println("Transfer failed: " + customerSafeMessage(exception));
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Do not expose internal database errors to the customer.
            System.out.println("Unable to complete the transfer. Please try again later.");
        }
    }

    /**
     * Allows the authenticated customer to repay the selected Loan Account
     * using one of their active Checking or Savings Accounts.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param loanAccount the Loan Account receiving the repayment
     * @return true if a repayment was successfully committed
     */
    private static boolean makeLoanPayment(LineReader lineReader, Customer customer, Account loanAccount) {
        try {
            //Retrieve the customer's current accounts from PostgreSQL.
            List<Account> accounts = accountService.getAccountsForCustomer(customer);

            //Only active Checking and Savings Accounts can fund loan repayments.
            List<Account> fundingAccounts = accounts.stream()
                    .filter(account -> "CHECKING".equals(account.getAccountType()) ||
                            "SAVINGS".equals(account.getAccountType()))
                    .filter(account -> "ACTIVE".equals(account.getStatus()))
                    .toList();

            //A loan repayment requires an eligible funding account.
            if (fundingAccounts.isEmpty()) {
                System.out.println("No active Checking or Savings Accounts are available.");
                return false;
            }

            //Prevent repayments to a Loan Account that is already closed.
            Account currentLoan = accounts.stream()
                    .filter(account -> account.getId() == loanAccount.getId())
                    .findFirst()
                    .orElse(null);

            if (currentLoan == null || !"ACTIVE".equals(currentLoan.getStatus())) {
                System.out.println("This Loan Account is not active.");
                return false;
            }

            printSection("Make Loan Payment");
            System.out.println("Loan Account #" + currentLoan.getAccountNumber());

            //Show the outstanding loan balance from PostgreSQL.
            BigDecimal outstandingBalance = accountService.getAccountBalance(customer, currentLoan);

            System.out.println("Outstanding Loan Balance: $" + outstandingBalance);

            //Display eligible funding accounts.
            for (int index = 0; index < fundingAccounts.size(); index++) {
                Account fundingAccount = fundingAccounts.get(index);

                System.out.printf("%d. %s Account #%s%n",
                        index + 1,
                        fundingAccount.getAccountType(),
                        fundingAccount.getAccountNumber());
            }

            System.out.println((fundingAccounts.size() + 1) + ". Cancel Payment");

            //Read the funding account selection.
            String selectionInput = lineReader.readLine("Choose funding account: ").trim();

            int selection;

            try {
                selection = Integer.parseInt(selectionInput);
            }
            catch (NumberFormatException exception) {
                //Reject nonnumeric account selections.
                System.out.println("Please enter a valid funding account number.");
                return false;
            }

            //Allow cancellation without changing the database.
            if (selection == fundingAccounts.size() + 1) {
                System.out.println("Loan payment cancelled.");
                return false;
            }

            //Reject selections outside the funding account list.
            if (selection < 1 || selection > fundingAccounts.size()) {
                System.out.println("Invalid funding account.");
                return false;
            }

            Account fundingAccount = fundingAccounts.get(selection - 1);

            //Show the selected account's available balance.
            BigDecimal fundingBalance = accountService.getAccountBalance(customer, fundingAccount);

            System.out.println("Funding Account Balance: $" + fundingBalance);

            //Ask for the repayment amount.
            String amountInput =
                    lineReader.readLine("Enter loan payment amount ($): ");

            BigDecimal amount = parseLoanRepaymentAmount(amountInput);

            //Reject amounts exceeding the displayed outstanding loan balance.
            if (amount.compareTo(outstandingBalance) > 0) {
                System.out.println("Payment exceeds the outstanding loan balance.");
                return false;
            }

            //Reject amounts exceeding the funding account's available balance.
            if (amount.compareTo(fundingBalance) > 0) {
                System.out.println("Insufficient funds in the selected account.");
                return false;
            }

            //Display payment details before asking for confirmation.
            System.out.println("\nPayment Summary:");
            System.out.println("From: " + fundingAccount.getAccountType() + " Account #" + fundingAccount.getAccountNumber());
            System.out.println("To: LOAN Account #" + currentLoan.getAccountNumber());
            System.out.println("Payment Amount: $" + amount.setScale(2));
            System.out.println("Remaining Loan Balance: $" + outstandingBalance.subtract(amount).setScale(2));

            String confirmation =
                    lineReader.readLine("Confirm payment? (yes/no): ").trim();

            if (!"yes".equalsIgnoreCase(confirmation)) {
                System.out.println("Loan payment cancelled.");
                return false;
            }

            //The service atomically updates both accounts, transaction, and audit.
            long transactionId = transactionService.repayLoan(customer, fundingAccount, currentLoan, amount);

            System.out.println("\nLoan payment successful!");
            System.out.println("Transaction ID: " + transactionId);

            //Retrieve both committed balances directly from PostgreSQL.
            BigDecimal updatedFundingBalance = accountService.getAccountBalance(customer, fundingAccount);

            BigDecimal updatedLoanBalance = accountService.getAccountBalance(customer, currentLoan);

            System.out.println("Funding Account Balance: $" + updatedFundingBalance);
            System.out.println("Outstanding Loan Balance: $" + updatedLoanBalance);

            //The DAO automatically closes the Loan Account when fully repaid.
            if (updatedLoanBalance.compareTo(BigDecimal.ZERO) == 0) {
                System.out.println("Loan fully repaid! Your Loan Account is now CLOSED.");
            }

            return true;

        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            //Explain invalid payments without exposing database internals.
            System.out.println("Loan payment failed: " + customerSafeMessage(exception));
            return false;

        }
        catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Keep internal database details hidden from the customer.
            System.out.println("Unable to complete the loan payment. Please try again later.");
            return false;
        }
    }


    /**
     * Allows the authenticated customer to contribute to the selected
     * Investment Account using an active Checking or Savings Account.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param investmentAccount the Investment Account receiving the contribution
     * @return true if the contribution was successfully committed
     */
    private static boolean makeInvestmentContribution(LineReader lineReader,
                                                      Customer customer, Account investmentAccount) {
        try {
            //Retrieve the customer's current accounts from PostgreSQL.
            List<Account> accounts = accountService.getAccountsForCustomer(customer);

            //Only active Checking and Savings Accounts can fund contributions.
            List<Account> fundingAccounts = accounts.stream()
                    .filter(account -> "CHECKING".equals(account.getAccountType()) ||
                            "SAVINGS".equals(account.getAccountType()))
                    .filter(account -> "ACTIVE".equals(account.getStatus()))
                    .toList();

            //A contribution requires an eligible funding account.
            if (fundingAccounts.isEmpty()) {
                System.out.println("No active Checking or Savings Accounts are available.");
                return false;
            }

            //Require the selected Investment Account to still be active.
            Account currentInvestment = accounts.stream()
                    .filter(account -> account.getId() == investmentAccount.getId())
                    .filter(account -> "INVESTMENT".equals(account.getAccountType()))
                    .findFirst()
                    .orElse(null);

            if (currentInvestment == null ||
                    !"ACTIVE".equals(currentInvestment.getStatus())) {
                System.out.println("This Investment Account is not active.");
                return false;
            }

            printSection("Make Investment Contribution");
            System.out.println("Investment Account #" + currentInvestment.getAccountNumber());

            //Show the Investment Account's current balance.
            BigDecimal investmentBalance =
                    accountService.getAccountBalance(customer, currentInvestment);

            System.out.println("Current Investment Balance: $" + investmentBalance);

            //Display eligible funding accounts.
            for (int index = 0; index < fundingAccounts.size(); index++) {
                Account fundingAccount = fundingAccounts.get(index);

                System.out.printf("%d. %s Account #%s%n",
                        index + 1,
                        fundingAccount.getAccountType(),
                        fundingAccount.getAccountNumber());
            }

            System.out.println((fundingAccounts.size() + 1) + ". Cancel Contribution");

            //Read the funding account selection.
            String selectionInput =
                    lineReader.readLine("Choose funding account: ").trim();

            int selection;

            try {
                selection = Integer.parseInt(selectionInput);
            } catch (NumberFormatException exception) {
                //Reject nonnumeric account selections.
                System.out.println("Please enter a valid funding account number.");
                return false;
            }

            //Allow cancellation without changing the database.
            if (selection == fundingAccounts.size() + 1) {
                System.out.println("Investment contribution cancelled.");
                return false;
            }

            //Reject selections outside the funding account list.
            if (selection < 1 || selection > fundingAccounts.size()) {
                System.out.println("Invalid funding account.");
                return false;
            }

            Account fundingAccount = fundingAccounts.get(selection - 1);

            //Show the selected funding account's available balance.
            BigDecimal fundingBalance =
                    accountService.getAccountBalance(customer, fundingAccount);

            System.out.println("Funding Account Balance: $" + fundingBalance);

            //Ask for the contribution amount.
            String amountInput =
                    lineReader.readLine("Enter investment contribution amount ($): ");

            BigDecimal amount = parseInvestmentContributionAmount(amountInput);

            //Reject contributions exceeding the displayed available balance.
            if (amount.compareTo(fundingBalance) > 0) {
                System.out.println("Insufficient funds in the selected account.");
                return false;
            }

            //Display contribution details before asking for confirmation.
            System.out.println("\nContribution Summary:");
            System.out.println("From: " + fundingAccount.getAccountType()
                    + " Account #" + fundingAccount.getAccountNumber());
            System.out.println("To: INVESTMENT Account #"
                    + currentInvestment.getAccountNumber());
            System.out.println("Contribution Amount: $" + amount.setScale(2));
            System.out.println("Investment Balance After: $"
                    + investmentBalance.add(amount).setScale(2));

            String confirmation =
                    lineReader.readLine("Confirm contribution? (yes/no): ").trim();

            if (!"yes".equalsIgnoreCase(confirmation)) {
                System.out.println("Investment contribution cancelled.");
                return false;
            }

            //The service atomically updates both balances and saves all records.
            long transactionId = transactionService.contributeToInvestment(
                    customer, fundingAccount, currentInvestment, amount);

            System.out.println("\nInvestment contribution successful!");
            System.out.println("Transaction ID: " + transactionId);

            //Retrieve both committed balances directly from PostgreSQL.
            BigDecimal updatedFundingBalance =
                    accountService.getAccountBalance(customer, fundingAccount);

            BigDecimal updatedInvestmentBalance =
                    accountService.getAccountBalance(customer, currentInvestment);

            System.out.println("Funding Account Balance: $" + updatedFundingBalance);
            System.out.println("Investment Account Balance: $" + updatedInvestmentBalance);

            return true;

        } catch (IllegalArgumentException | IllegalStateException exception) {
            //Explain invalid contributions without exposing database internals.
            System.out.println("Investment contribution failed: " + customerSafeMessage(exception));
            return false;

        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Keep internal database details hidden from the customer.
            System.out.println(
                    "Unable to complete the investment contribution. Please try again later.");
            return false;
        }
    }



    /**
     * Pays a matured Investment Account into an active Checking or Savings Account.
     * Reloads account data before offering the payout so the service receives
     * the latest maturity date and account status.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param investmentAccount the selected Investment Account
     * @return true if the payout was committed and the account list needs reloading
     */
    private static boolean payInvestmentMaturity(LineReader lineReader,
                                                 Customer customer, Account investmentAccount) {
        try {
            //Reload the selected investment and eligible destination accounts.
            List<Account> accounts = accountService.getAccountsForCustomer(customer);
            Account current = accounts.stream()
                    .filter(account -> account.getId() == investmentAccount.getId())
                    .filter(account -> account instanceof InvestmentAccount)
                    .findFirst().orElse(null);

            if (!(current instanceof InvestmentAccount maturedInvestment)) {
                System.out.println("Investment Account not found.");
                return false;
            }
            if (!"ACTIVE".equals(maturedInvestment.getStatus()) ||
                    java.time.LocalDate.now().isBefore(maturedInvestment.getWithdrawalUnlockedDate())) {
                System.out.println("This Investment Account is not active or has not matured yet.");
                return false;
            }

            List<Account> destinations = accounts.stream()
                    .filter(account -> "CHECKING".equals(account.getAccountType()) ||
                            "SAVINGS".equals(account.getAccountType()))
                    .filter(account -> "ACTIVE".equals(account.getStatus()))
                    .toList();
            if (destinations.isEmpty()) {
                System.out.println("No active Checking or Savings Account is available for the payout.");
                return false;
            }

            printSection("Collect Matured Investment");
            System.out.println("Investment Account #" + maturedInvestment.getAccountNumber());
            System.out.println("Maturity Date: " + maturedInvestment.getWithdrawalUnlockedDate());
            System.out.println("Investment Balance: $" +
                    accountService.getAccountBalance(customer, maturedInvestment));
            System.out.println("Interest will be calculated from recorded contributions.");

            for (int index = 0; index < destinations.size(); index++) {
                Account destination = destinations.get(index);
                System.out.printf("%d. %s Account #%s%n", index + 1,
                        destination.getAccountType(), destination.getAccountNumber());
            }
            System.out.println((destinations.size() + 1) + ". Cancel Payout");

            int selection;
            try {
                selection = Integer.parseInt(
                        lineReader.readLine("Choose destination account: ").trim());
            } catch (NumberFormatException exception) {
                System.out.println("Please enter a valid destination account number.");
                return false;
            }
            if (selection == destinations.size() + 1) {
                System.out.println("Maturity payout cancelled.");
                return false;
            }
            if (selection < 1 || selection > destinations.size()) {
                System.out.println("Invalid destination account.");
                return false;
            }

            Account destination = destinations.get(selection - 1);
            System.out.println("Destination: " + destination.getAccountType() +
                    " Account #" + destination.getAccountNumber());
            System.out.println("The investment will be CLOSED after payout.");
            String confirmation = lineReader.readLine("Confirm maturity payout? (yes/no): ").trim();
            if (!"yes".equalsIgnoreCase(confirmation)) {
                System.out.println("Maturity payout cancelled.");
                return false;
            }

            //The service atomically closes the investment, credits the destination,
            //and writes the transaction and audit records.
            long transactionId = transactionService.payMaturedInvestment(
                    customer, maturedInvestment, destination);
            System.out.println("\nInvestment maturity payout successful!");
            System.out.println("Transaction ID: " + transactionId);
            System.out.println("Destination Balance: $" +
                    accountService.getAccountBalance(customer, destination));
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Investment maturity payout failed: " + customerSafeMessage(exception));
            return false;
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            System.out.println("Unable to complete the maturity payout. Please try again later.");
            return false;
        }
    }


    /**
     * Formats an account's transaction history for display in the CLI.
     * Converts timestamps to the customer's local system timezone.
     *
     * @param account the account whose transactions are displayed
     * @param history the transactions associated with the account
     * @return a formatted transaction history table
     */
    static String formatTransactionHistory(Account account, List<TransactionHistoryEntry> history) {

        //Require an account and transaction history list.
        if (account == null || history == null) {
            throw new IllegalArgumentException(
                    "An account and transaction history are required.");
        }

        StringBuilder output = new StringBuilder();

        output.append("\nKAKUZU CAPITAL  /  Transaction History: ")
                .append(account.getAccountType())
                .append(" Account #")
                .append(account.getAccountNumber())
                .append("\n");

        //Show a helpful message when the account has no recorded transactions.
        if (history.isEmpty()) {
            output.append("No transactions found for this account.\n");
            return output.toString();
        }

        //Display timestamps in the computer's configured local timezone.
        DateTimeFormatter dateFormatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

        ZoneId localZone = ZoneId.systemDefault();

        //Allow enough space for INVESTMENT_CONTRIBUTION.
        output.append(String.format(
                "%-8s %-21s %-25s %-14s %-10s %-12s %-18s%n",
                "ID", "Date", "Type", "Amount", "Direction", "Status", "Balance After"));

        output.append("-".repeat(114)).append("\n");

        //Display each transaction from the selected account's perspective.
        for (TransactionHistoryEntry entry : history) {
            String formattedDate = entry.createdAt()
                    .atZoneSameInstant(localZone)
                    .format(dateFormatter);

            String amount = "$" + entry.amount().setScale(2).toPlainString();

            String balance = entry.balanceAfter() == null
                    ? "N/A"
                    : "$" + entry.balanceAfter().setScale(2).toPlainString();

            output.append(String.format(
                    "%-8d %-21s %-25s %-14s %-10s %-12s %-18s%n",
                    entry.transactionId(),
                    formattedDate,
                    entry.transactionType(),
                    amount,
                    entry.direction(),
                    entry.status(),
                    balance));

            //Show the transaction description beneath its table row.
            if (entry.description() != null && !entry.description().isBlank()) {
                output.append("  Description: ")
                        .append(entry.description())
                        .append("\n");
            }
        }

        return output.toString();
    }


    /**
     * Retrieves and displays the transaction history for the
     * account selected by the authenticated customer.
     *
     * @param customer the authenticated customer
     * @param account the selected bank account
     */
    private static void showTransactionHistory(Customer customer, Account account) {
        try {
            //Retrieve only transactions belonging to the selected account.
            List<TransactionHistoryEntry> history =
                    transactionService.getTransactionHistory(customer, account);

            //Format and display the account's transactions.
            System.out.print(formatTransactionHistory(account, history));

        } catch (IllegalArgumentException | IllegalStateException exception) {
            //Report invalid requests without exposing database internals.
            System.out.println("Unable to view transaction history: "
                    + customerSafeMessage(exception));

        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            //Keep the account menu available if PostgreSQL cannot load the history.
            System.out.println(
                    "Unable to retrieve transaction history. Please try again later.");
        }
    }



    /**
     * Returns the menu options available for a selected account type.
     *
     * @param accountType the account type being displayed
     * @return the account menu options in display order
     */
    static List<String> getSelectedAccountMenuOptions(String accountType) {
        //Checking and Savings Accounts support regular transactions and Zelle.
        if ("CHECKING".equals(accountType) || "SAVINGS".equals(accountType)) {
            return List.of(
                    "Check Balance",
                    "Deposit Cash",
                    "Withdraw Cash",
                    "Transfer Cash",
                    "Zelle",
                    "Close This Account",
                    "View Transaction History",
                    "Back to My Accounts"
            );
        }

        //Loan Accounts support repayments from Checking or Savings Accounts.
        if ("LOAN".equals(accountType)) {
            return List.of(
                    "Check Balance",
                    "Make Loan Payment",
                    "View Transaction History",
                    "Back to My Accounts"
            );
        }

        //Investment Accounts support contributions before their unlock date.
        return List.of(
                "Check Balance",
                "Make Investment Contribution",
                "Collect Matured Investment",
                "View Transaction History",
                "Back to My Accounts"
        );
    }



    /**
     * Returns the choices available in the demo Zelle menu.
     *
     * @return the four menu choices in display order
     */
    static List<String> getZelleMenuOptions() {
        return List.of("Send Cash", "Request Cash", "View Payment Requests", "Back");
    }

    /**
     * Shows simulated Zelle actions for a Checking or Savings Account.
     *
     * @param reader the terminal input reader
     * @param customer the logged-in customer
     * @param account the currently selected account
     */
    private static void showZelleMenu(LineReader reader, Customer customer, Account account) {
        while (true) {
            printSection("Zelle");
            System.out.println("Send and request cash with Zelle.");
            List<String> options = getZelleMenuOptions();
            for (int i = 0; i < options.size(); i++) {
                System.out.println((i + 1) + ". " + options.get(i));
            }
            String choice = reader.readLine("› Choose an option: ").trim();
            switch (choice) {
                case "1" -> sendZelleMoney(reader, customer, account);
                case "2" -> requestZelleMoney(reader, customer);
                case "3" -> showPendingZelleRequests(reader, customer);
                case "4" -> { return; }
                default -> System.out.println("That option isn't available. Please choose one from the menu.");
            }
        }
    }

    /** Sends demo money from the selected active account. */
    private static void sendZelleMoney(LineReader reader, Customer customer, Account account) {
        if (!"ACTIVE".equals(account.getStatus())) {
            System.out.println("Zelle payments require an active Checking or Savings Account.");
            return;
        }
        try {
            //Collect the recipient, amount, and optional note.
            String username = reader.readLine("Recipient username: ");
            BigDecimal amount = parseDepositAmount(reader.readLine("Amount ($): "));
            String description = reader.readLine("Description (optional): ").trim();
            String confirm = reader.readLine("Confirm payment? (yes/no): ").trim();
            if (!"yes".equalsIgnoreCase(confirm)) {
                System.out.println("Payment cancelled.");
                return;
            }
            //The service and DAO recheck account eligibility before transferring funds.
            long transactionId = zelleService.sendMoney(customer, account, username, amount,
                    description.isEmpty() ? null : description);
            System.out.println("Payment sent successfully. Transaction ID: " + transactionId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Unable to send cash: " + customerSafeMessage(exception));
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            System.out.println("Payment was not completed. Please try again later.");
        }
    }

    /** Creates a payment request addressed to another customer's username. */
    private static void requestZelleMoney(LineReader reader, Customer customer) {
        try {
            String username = reader.readLine("Request cash from username: ");
            BigDecimal amount = parseDepositAmount(reader.readLine("Amount ($): "));
            String description = reader.readLine("Description (optional): ").trim();
            //Requests belong to customers, not to the account whose menu is open.
            long requestId = zelleService.requestMoney(customer, username, amount,
                    description.isEmpty() ? null : description);
            System.out.println("Your payment request has been sent. Request ID: " + requestId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Unable to request cash: " + customerSafeMessage(exception));
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            System.out.println("Request was not created. Please try again later.");
        }
    }

    /** Shows pending requests and allows the payer to approve or reject one. */
    private static void showPendingZelleRequests(LineReader reader, Customer customer) {
        try {
            //Load the same customer-level requests from either Checking or Savings.
            List<ZellePaymentRequest> requests = zelleService.getPendingRequests(customer);
            if (requests.isEmpty()) {
                System.out.println("You have no pending payment requests.");
                return;
            }
            printSection("View Payment Requests");
            for (ZellePaymentRequest request : requests) {
                System.out.println("Request #" + request.requestId() + " | $" + request.amount()
                        + " | From " + (request.requesterUsername() == null || request.requesterUsername().isBlank()
                                ? "customer ID " + request.requesterCustomerId()
                                : request.requesterUsername())
                        + " | " + (request.description() == null ? "No description" : request.description()));
            }
            String input = reader.readLine("Request ID to respond to (or Enter to go back): ").trim();
            if (input.isEmpty()) return;
            long requestId = Long.parseLong(input);
            //Only allow responses to requests actually displayed to this customer.
            if (requests.stream().noneMatch(request -> request.requestId() == requestId)) {
                System.out.println("Please select a pending request from the list.");
                return;
            }
            String action = reader.readLine("1. Approve  2. Reject  3. Cancel: ").trim();
            if ("2".equals(action)) {
                zelleService.rejectRequest(customer, requestId);
                System.out.println("Payment request declined.");
            } else if ("1".equals(action)) {
                //Let the payer choose any owned active Checking or Savings Account.
                List<Account> fundingAccounts = accountService.getAccountsForCustomer(customer).stream()
                        .filter(account -> "ACTIVE".equals(account.getStatus()))
                        .filter(account -> "CHECKING".equals(account.getAccountType())
                                || "SAVINGS".equals(account.getAccountType()))
                        .toList();
                if (fundingAccounts.isEmpty()) {
                    System.out.println("No active Checking or Savings Account can fund this request.");
                    return;
                }
                for (int i = 0; i < fundingAccounts.size(); i++) {
                    Account account = fundingAccounts.get(i);
                    System.out.println((i + 1) + ". " + account.getAccountType()
                            + " #" + account.getAccountNumber());
                }
                int selected = Integer.parseInt(reader.readLine("Funding account number (menu option): ").trim());
                if (selected < 1 || selected > fundingAccounts.size()) {
                    System.out.println("Invalid funding account selection.");
                    return;
                }
                if (!"yes".equalsIgnoreCase(reader.readLine("Confirm payment? (yes/no): ").trim())) {
                    System.out.println("Approval cancelled.");
                    return;
                }
                long transactionId = zelleService.approveRequest(customer,
                        fundingAccounts.get(selected - 1), requestId);
                System.out.println("Request approved. Payment completed. Transaction ID: " + transactionId);
            } else {
                System.out.println("No changes made.");
            }
        } catch (NumberFormatException exception) {
            System.out.println("That option isn't available. Please choose one from the menu.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println("Unable to process request: " + customerSafeMessage(exception));
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            System.out.println("Unable to process payment requests. Please try again later.");
        }
    }


    /**
     * Requests confirmation before closing an eligible Checking or Savings Account.
     * The service and DAO enforce the final ownership, status, and zero-balance rules.
     *
     * @param lineReader the terminal reader used for confirmation
     * @param customer the authenticated customer
     * @param account the Checking or Savings Account selected for closure
     * @return true if the account was closed and the account list should be reloaded
     */
    private static boolean closeSelectedAccount(LineReader lineReader, Customer customer, Account account) {
        //Read the latest balance instead of relying on the account object's cached value.
        try {
            if (!"ACTIVE".equals(account.getStatus())) {
                System.out.println("Only active Checking or Savings Accounts can be closed.");
                return false;
            }

            BigDecimal balance = accountService.getAccountBalance(customer, account);
            printSection("Close This Account");
            System.out.println("Account: " + account.getAccountType() + " #" + account.getAccountNumber());
            System.out.println("Current Balance: $" + balance);

            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                System.out.println("Transfer or withdraw the remaining balance before closing this account.");
                return false;
            }

            System.out.println("Closing this account prevents future deposits, withdrawals, and transfers.");
            System.out.println("Your transaction history will be preserved.");
            String confirmation = lineReader.readLine("Confirm account closure? (yes/no): ").trim();
            if (!"yes".equalsIgnoreCase(confirmation)) {
                System.out.println("Account closure cancelled.");
                return false;
            }

            //The database checks the balance and eligibility again in one atomic UPDATE.
            accountService.closeCheckingOrSavingsAccount(customer, account);
            System.out.println("Account #" + account.getAccountNumber() + " closed successfully.");
            return true;
        } catch (IllegalArgumentException exception) {
            System.out.println("Account cannot be closed: " + customerSafeMessage(exception));
            return false;
        } catch (SQLException exception) {
            LOGGER.error("Banking operation failed", exception);
            System.out.println("Account closure was not completed. Check that the account is active and has a zero balance.");
            return false;
        }
    }


    /**
     * Displays the available actions for an account selected by
     * the authenticated customer.
     *
     * @param lineReader the terminal reader used for customer input
     * @param customer the authenticated customer
     * @param account the account selected from the customer's account list
     */
    private static void showSelectedAccountMenu(LineReader lineReader, Customer customer, Account account) {
        //Keep showing the account menu until the customer chooses to go back.
        while (true) {
            printSection(account.getAccountType() + " Account");
            System.out.println("ACCOUNT  #" + account.getAccountNumber());
            System.out.println("──────────────────────────────────────");

            //Only Checking and Savings Accounts support regular deposits, withdrawals, and transfers.
            boolean canTransact = "CHECKING".equals(account.getAccountType()) ||
                    "SAVINGS".equals(account.getAccountType());

            //Loan Accounts support repayments from Checking or Savings Accounts.
            boolean isLoan = "LOAN".equals(account.getAccountType());

            //Investment Accounts support contributions from Checking or Savings Accounts.
            boolean isInvestment = "INVESTMENT".equals(account.getAccountType());

            //Display the numbered options for the selected account type.
            List<String> menuOptions = getSelectedAccountMenuOptions(account.getAccountType());

            for (int index = 0; index < menuOptions.size(); index++) {
                System.out.println((index + 1) + ". " + menuOptions.get(index));
            }

            //Read the customer's menu selection.
            String input = lineReader.readLine("› Choose an option: ").trim();

            try {
                int selection = Integer.parseInt(input);

                if (selection == 1) {
                    //Retrieve the current balance directly from PostgreSQL.
                    try {
                        BigDecimal balance = accountService.getAccountBalance(customer, account);

                        //Show the remaining debt for loans and the current balance for other accounts.
                        if (account instanceof LoanAccount) {
                            System.out.println("Outstanding Loan Balance: $" + balance);
                        } else {
                            System.out.println("Current Balance: $" + balance);
                        }

                    } catch (SQLException exception) {
                        LOGGER.error("Banking operation failed", exception);
                        //Report a database error without closing the account menu.
                        System.out.println("Unable to retrieve your account balance. Please try again.");
                    }
                } else if (canTransact && selection == 2) {
                    //Open the cash deposit prompt for Checking and Savings Accounts.
                    depositIntoAccount(lineReader, customer, account);
                } else if (canTransact && selection == 3) {
                    //Open the cash withdrawal prompt for Checking and Savings Accounts.
                    withdrawFromAccount(lineReader, customer, account);
                } else if (canTransact && selection == 4) {
                    //Open transfers between accounts owned by the same customer.
                    transferBetweenAccounts(lineReader, customer, account);
                } else if (canTransact && selection == 5) {
                    //Open the demo Zelle submenu for Checking and Savings Accounts.
                    showZelleMenu(lineReader, customer, account);
                } else if (canTransact && selection == 6) {
                    //Close a zero-balance Checking or Savings Account after confirmation.
                    if (closeSelectedAccount(lineReader, customer, account)) {
                        //Reload the account list after a committed closure.
                        return;
                    }
                } else if (isLoan && selection == 2) {
                    //Allow active Loan Accounts to receive repayments.
                    if (!"ACTIVE".equals(account.getStatus())) {
                        System.out.println("This Loan Account is closed and cannot receive payments.");
                    } else if (makeLoanPayment(lineReader, customer, account)) {
                        //Reload the account list after a committed repayment.
                        return;
                    }
                } else if (isInvestment && selection == 2) {
                    //Allow active Investment Accounts to receive contributions.
                    if (!"ACTIVE".equals(account.getStatus())) {
                        System.out.println("This Investment Account is closed and cannot receive contributions.");
                    } else if (makeInvestmentContribution(lineReader, customer, account)) {
                        //Reload the account list after a committed contribution.
                        return;
                    }
                } else if (isInvestment && selection == 3) {
                    //Collect a matured investment into an active Checking or Savings Account.
                    if (payInvestmentMaturity(lineReader, customer, account)) {
                        //Reload the account list after a committed payout.
                        return;
                    }
                } else if (selection == (canTransact ? 7 : isInvestment ? 4 : 3)) {
                    //Display transaction history for any account type.
                    showTransactionHistory(customer, account);
                } else if (selection == (canTransact ? 8 : isInvestment ? 5 : 4)) {
                    //Return to the customer's account list.
                    return;
                } else {
                    System.out.println("That option isn't available. Please choose one from the menu.");
                }
            } catch (NumberFormatException exception) {
                //Reject input that is not a valid menu number.
                System.out.println("That option isn't available. Please choose one from the menu.");
            }
        }
    }

}

