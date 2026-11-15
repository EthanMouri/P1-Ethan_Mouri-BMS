# Kakuzu Capital

**A smarter way to manage your money.**

A Java command-line banking management application developed by **Ethan Mouri** as a **Revature Banking Management System (BMS)** project. Kakuzu Capital is a fictional banking platform for learning and demonstration; it does **not** connect to real banks or the real Zelle network.

## Overview

Kakuzu Capital combines a terminal-based customer experience with a PostgreSQL database. Customers can register, sign in, manage multiple types of accounts, move funds, view transaction history, and use simulated Zelle-style payments and payment requests. The CLI features a branded welcome screen and consistent menus.

## Features

- **Membership:** customer registration, secure login, sign-out, and membership closure subject to eligibility checks.
- **Accounts:** checking, savings, investment, and loan accounts; account creation, balances, and supported account-closure workflows.
- **Banking transactions:** deposits, withdrawals, transfers, and transaction history.
- **Investments:** fixed-term investment accounts, contributions, maturity validation, and maturity payouts.
- **Zelle-style demo:** send cash between customers, request cash, review pending requests, approve or decline requests, and record completed transfers.
- **Security and consistency:** salted PBKDF2-HMAC-SHA256 password hashes, login lockout handling, a five-minute inactivity timeout, account ownership checks, customer-status validation, PostgreSQL transactions, row locking, and transaction/audit records.
- **Testing:** JUnit 5 and Mockito automated tests. **267 tests were reported passing** at the completion of the CLI redesign checkpoint.

> **Demo terminology:** Menu options such as “Deposit Cash,” “Transfer Cash,” and “Send Cash” operate on simulated account balances. They do not handle physical currency or real payment rails.

## Technology

| Component | Technology |
| --- | --- |
| Language | Java 25 (Maven compiler release 25) |
| Build | Apache Maven |
| Database | PostgreSQL via JDBC |
| Terminal input | JLine 3 |
| Tests | JUnit 5, Mockito |

## Project layout

```text
.
├── pom.xml
├── README.md
├── sql/
│   └── postgres/
│       └── Schema.sql
└── src/
    ├── main/
    │   ├── java/com/revature/
    │   │   ├── App.java
    │   │   ├── account/
    │   │   ├── customer/
    │   │   ├── security/
    │   │   ├── session/
    │   │   ├── transactions/
    │   │   ├── util/
    │   │   └── zelle/
    │   └── resources/
    └── test/java/com/revature/
```

## Getting started

### Prerequisites

- JDK **25** installed and selected for Maven and the IDE.
- Apache Maven installed, or Maven support in IntelliJ IDEA.
- A running local PostgreSQL server.
- A terminal capable of interactive input (the application uses JLine for input and password masking).

### 1. Prepare PostgreSQL

The database connection is currently configured in `src/main/java/com/revature/util/DatabaseConnection.java`:

| Setting | Current value |
| --- | --- |
| Host | `localhost` |
| Port | `5432` |
| Database | `banking_app` |
| Database user | `bms_owner` |
| Password | Read from the `DB_PASSWORD` environment variable |

Create a PostgreSQL database named `banking_app` and a login role named `bms_owner`, with privileges sufficient to create and modify the application's schema. Set the role's password privately in PostgreSQL; **do not commit it to the repository**.

**Important schema note:** `sql/postgres/Schema.sql` includes both original table definitions and later `ALTER TABLE`, rename, and sequence statements accumulated during development. It is **not a verified, repeatable, from-scratch migration script**. Some statements are not idempotent, so do not rerun it blindly against an existing database. Use a backed-up development database and review the SQL before applying it. A clean, versioned database bootstrap/migration process is a recommended future improvement.

### 2. Set the database password

For macOS/Linux, in the terminal session used to launch the app:

```bash
export DB_PASSWORD='your_local_database_password'
```

For IntelliJ IDEA, configure `DB_PASSWORD` in the application's **Run/Debug Configuration → Environment variables**. Keep the password out of source files and screenshots.

### 3. Compile and test

From the project root:

```bash
mvn clean test
```

The project uses Java 25. Check your installed versions if compilation fails:

```bash
java -version
mvn -version
```

### 4. Run the application

Open the Maven project in IntelliJ IDEA and run the `main` method in:

```text
src/main/java/com/revature/App.java
```

Make sure PostgreSQL is running, `DB_PASSWORD` is configured in the run environment, and the schema is already set up. The CLI uses interactive terminal input; an IDE terminal or a properly configured IntelliJ run console is recommended.

## Example customer walkthrough

This walkthrough describes the available workflows; actual prompts and menu numbers may differ by account type.

1. Launch Kakuzu Capital and choose **Become a Member of Kakuzu Capital**.
2. Register a customer, then choose **Sign In**.
3. Open a checking or savings account and inspect its balance.
4. Use the supported **Deposit Cash**, **Withdraw Cash**, or **Transfer Cash** options.
5. Open **Zelle** on an eligible checking or savings account to **Send Cash**, **Request Cash**, or review pending payment requests. Use a second demo customer to test cross-customer interactions.
6. Review **Transaction History** to inspect completed transactions.
7. Choose **Sign Out** and confirm the Kakuzu Capital welcome screen returns with a sign-out message.
8. For a dedicated disposable demo customer, review **Close My Membership** and its eligibility/confirmation steps. Closing a membership is not the same as closing an individual account.

### Suggested live demo

Demonstrate a successful login, account opening, deposit, balance inquiry, transfer, Zelle payment request and approval, transaction history, and sign-out. Optionally show an insufficient-funds rejection and the five-minute inactivity timeout. Use **test-only customers and balances**; do not include real passwords or personal financial information in recordings.

## Security notes

- Passwords are salted and hashed with PBKDF2-HMAC-SHA256; they are not stored as plaintext.
- Login has lockout handling, and authenticated CLI sessions expire after five minutes of inactivity.
- Database operations include ownership/status checks, transactional updates, and locking to reduce inconsistent balances and duplicate processing.
- Financial and login activity is represented in transaction/audit tables.
- The system is a **learning/demo project**, not a production-grade bank. It has not undergone an independent security audit and should not be used to hold real funds or personal financial records.

## Tests

Run the complete automated suite with:

```bash
mvn clean test
```

The test source is under `../src/test/java/com/revature`. The latest developer-confirmed checkpoint reported **267 passing tests**. This README update changes documentation only; it does not add or modify Java tests.

## Limitations and future improvements

- Replace the evolving `Schema.sql` with reproducible, versioned migrations and a documented clean-install path.
- Externalize database host, port, name, and username rather than hardcoding them in `DatabaseConnection.java`.
- Add integration tests against a disposable PostgreSQL database and more concurrency-focused tests.
- Consider structured logging and production-oriented deployment/configuration if expanding beyond a classroom demo.

## Credits

**Developed by Ethan Mouri**  
**Revature Banking Management System project**  
**Kakuzu Capital** is a fictional brand inspired by an anime reference, not an affiliated or licensed financial institution.
