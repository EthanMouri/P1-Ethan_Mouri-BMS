package com.revature.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {

    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH_BITS = 256;

    //For generating a truly random salt
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     *  Takes a plain-text password and hashes it with the pbkdf2_sha256 algorithm
     * @param password plain-text user entered password
     * @return The plain-text password that has been hashed
     */
    public static String hashPassword(String password) {
        //Generate a truly random 16 byte salt
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);

        //turn the plain-text password into an array
        char[] passwordCharacters = password.toCharArray();
        //Set up the hashing algorithm
        PBEKeySpec specification = new PBEKeySpec(passwordCharacters, salt, ITERATIONS, HASH_LENGTH_BITS);

        try {
            //Hash the password with the salt
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hashedPassword = factory.generateSecret(specification).getEncoded();

            // Return the hashed password (and hashing algorithm config) as a human-readable string
            return "pbkdf2_sha256" + ":" + ITERATIONS + ":" +
                    Base64.getEncoder().encodeToString(salt) + ":" +
                    Base64.getEncoder().encodeToString(hashedPassword);

        }
        catch (Exception exception) {
            throw new IllegalStateException("Unable to hash password", exception);
        }
        finally {
            //clear the objects that stored the password info (for security)
            specification.clearPassword();
            java.util.Arrays.fill(passwordCharacters, '\0');
        }
    }

    public static boolean verifyPassword(String givenPassword, String passwordFromDatabase) {
        //a password can't be null
        if (givenPassword == null || passwordFromDatabase == null) {
            return false;
        }

        try {
            //split the hashed password to get the hashing config info and the actual hashed password
            String[] parts = passwordFromDatabase.split(":", -1);
            //make sure the hashed password has all the correct parts
            if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) {
                return false;
            }

            //get how many iterations the hashing algorithm used
            int iterations = Integer.parseInt(parts[1]);
            // Reject unreasonable values before doing expensive work.
            if (iterations < 1 || iterations > 2_000_000) {
                return false;
            }

            //get the salt and hashed password
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] storedHash = Base64.getDecoder().decode(parts[3]);

            //confirm the salt attributes match what we put in database originally
            if (salt.length != SALT_LENGTH || storedHash.length != HASH_LENGTH_BITS / 8) {
                return false;
            }

            //Hash the entered password and check it matches the stored already hashed password
            char[] passwordCharacters = givenPassword.toCharArray();
            PBEKeySpec specification = new PBEKeySpec(passwordCharacters, salt, iterations, storedHash.length * 8);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] calculatedHash = factory.generateSecret(specification).getEncoded();
                return MessageDigest.isEqual(storedHash, calculatedHash);

            }
            finally {
                //clear the objects used to validate password (for security)
                specification.clearPassword();
                java.util.Arrays.fill(passwordCharacters, '\0');
            }

        }
        catch (IllegalArgumentException exception) {
            // Malformed stored password hash
            return false;
        }
        catch (Exception exception) {
            throw new IllegalStateException("Unable to verify password", exception);
        }
    }
}
