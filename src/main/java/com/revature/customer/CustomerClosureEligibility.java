package com.revature.customer;

import java.util.List;

/**
 * Read-only result of checking whether a customer can close their relationship.
 *
 * @param eligible whether closure requirements are satisfied
 * @param reasons reasons preventing closure
 */
public record CustomerClosureEligibility(boolean eligible, List<String> reasons) {

    /**
     * Copies the reasons so callers cannot modify the result.
     */
    public CustomerClosureEligibility {
        if (reasons == null) {
            throw new IllegalArgumentException("Reasons cannot be null.");
        }
        reasons = List.copyOf(reasons);
    }
}
