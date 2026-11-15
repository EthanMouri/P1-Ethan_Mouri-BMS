
package com.revature.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    /**
     * The password is hashed and stored in the expected format
     */
    @Test
    void hashingPasswordProducesValidHash() {
        String password = "SecureBank123!";
        String hashedPassword = PasswordHasher.hashPassword(password);
        assertNotNull(hashedPassword);
        assertNotEquals(password, hashedPassword);
        assertTrue(hashedPassword.startsWith("pbkdf2_sha256:"));
    }

    /**
     * 	A correct password can be verified
     */
    @Test
    void correctPasswordMatchesStoredHash() {
        String password = "SecureBank123!";
        String hashedPassword = PasswordHasher.hashPassword(password);
        boolean passwordMatches = PasswordHasher.verifyPassword(password, hashedPassword);
        assertTrue(passwordMatches);
    }

    /**
     * An incorrect password is rejected
     */
    @Test
    void incorrectPasswordDoesNotMatchStoredHash() {

        String password = "SecureBank123!";
        String hashedPassword = PasswordHasher.hashPassword(password);
        boolean passwordMatches = PasswordHasher.verifyPassword("WrongPassword123!", hashedPassword);
        assertFalse(passwordMatches);
    }

    /**
     * A random salt produces different stored password hashes
     */
    @Test
    void hashingSamePasswordTwiceProducesDifferentHashes() {
        String password = "SecureBank123!";
        String firstHash = PasswordHasher.hashPassword(password);
        String secondHash = PasswordHasher.hashPassword(password);
        assertNotEquals(firstHash, secondHash);
    }
}
