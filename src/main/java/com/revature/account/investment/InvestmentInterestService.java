package com.revature.account.investment;

import com.revature.account.AccountDAO;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

/**
 * Calculates total simple interest earned by an Investment Account's funded contributions.
 */
public class InvestmentInterestService {

    private final AccountDAO accountDAO;

    /**
     * Creates a service that retrieves contribution records through the account DAO.
     *
     * @param accountDAO the DAO used to retrieve funded contributions
     */
    public InvestmentInterestService(AccountDAO accountDAO) {
        if (accountDAO == null) {
            throw new IllegalArgumentException("An AccountDAO is required.");
        }
        this.accountDAO = accountDAO;
    }

    /**
     * Calculates interest for each funded contribution through the account's unlock date.
     * This method only reads contribution records and does not change account balances.
     *
     * @param investmentAccount the saved Investment Account to calculate interest for
     * @return total interest rounded to cents
     * @throws SQLException if contribution records cannot be retrieved
     */
    public BigDecimal calculateTotalInterest(InvestmentAccount investmentAccount) throws SQLException {
        //Require a saved Investment Account with a saved owner.
        if (investmentAccount == null || investmentAccount.getId() <= 0
                || investmentAccount.getAccountOwnerName() == null
                || investmentAccount.getAccountOwnerName().getId() <= 0) {
            throw new IllegalArgumentException("A saved Investment Account and owner are required.");
        }

        List<InvestmentContribution> contributions = accountDAO.findInvestmentContributions(
                investmentAccount.getId(), investmentAccount.getAccountOwnerName().getId());
        LocalDate maturityDate = investmentAccount.getWithdrawalUnlockedDate();
        BigDecimal totalInterest = new BigDecimal("0.00");

        //Each contribution accrues interest only from its own saved contribution date.
        for (InvestmentContribution contribution : contributions) {
            if (contribution == null || contribution.contributedAt() == null) {
                throw new IllegalArgumentException("Contribution records must include a timestamp.");
            }

            LocalDate contributionDate = contribution.contributedAt().toLocalDate();
            totalInterest = totalInterest.add(InvestmentInterestCalculator.calculateInterest(
                    contribution.amount(), investmentAccount.getInterestRate(),
                    contributionDate, maturityDate));
        }

        return totalInterest;
    }

    /**
     * Calculates investment interest using contribution records read on the caller's
     * database connection, so maturity settlement can use one atomic transaction.
     *
     * @param connection the existing payout transaction connection
     * @param investmentAccount the saved Investment Account
     * @return the total interest earned through the unlock date
     * @throws SQLException if contribution records cannot be retrieved
     */
    public BigDecimal calculateTotalInterest(Connection connection,
                                             InvestmentAccount investmentAccount) throws SQLException {
        //Validate before requesting contribution records.
        if (connection == null || investmentAccount == null || investmentAccount.getId() <= 0 ||
                investmentAccount.getAccountOwnerName() == null ||
                investmentAccount.getAccountOwnerName().getId() <= 0) {
            throw new IllegalArgumentException("A connection and saved Investment Account with owner are required.");
        }

        List<InvestmentContribution> contributions = accountDAO.findInvestmentContributions(
                connection, investmentAccount.getId(), investmentAccount.getAccountOwnerName().getId());
        BigDecimal totalInterest = new BigDecimal("0.00");
        LocalDate maturityDate = investmentAccount.getWithdrawalUnlockedDate();

        //Each contribution earns interest only from its own contribution date.
        for (InvestmentContribution contribution : contributions) {
            if (contribution == null || contribution.contributedAt() == null) {
                throw new IllegalArgumentException("Contribution records must include a timestamp.");
            }
            totalInterest = totalInterest.add(InvestmentInterestCalculator.calculateInterest(
                    contribution.amount(), investmentAccount.getInterestRate(),
                    contribution.contributedAt().toLocalDate(), maturityDate));
        }
        return totalInterest;
    }

}
