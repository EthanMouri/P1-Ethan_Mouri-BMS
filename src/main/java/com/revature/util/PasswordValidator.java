package com.revature.util;

public class PasswordValidator {

    /**
     * Determines if a password is secure/meets all the constraints
     * @param password the password being tested
     * @param username the user's username
     * @return Boolean: if the password is valid or not
     */
    public static boolean isValidPassword(String password, String username) {
        if (password == null || password.length() < 10) {
            System.out.println("Password must be at least 10 characters.");
            return false;
        }

        if (password.length() > 39) {
            System.out.println("Password must be less than 40 characters.");
            return false;
        }

        for (char character : password.toCharArray()) {
            if (Character.isWhitespace(character) || Character.isSpaceChar(character)) {
                System.out.println("Password cannot contain whitespace.");
                return false;
            }
        }

        if (password.equalsIgnoreCase(username)) {
            System.out.println("Password cannot be the same as your username.");
            return false;
        }

        //Check password has uppercase, lowercase, number, and special character
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecialCharacter = false;

        //check each character for requirements
        for (char character : password.toCharArray()) {
            if (Character.isUpperCase(character)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(character)) {
                hasLowercase = true;
            } else if (Character.isDigit(character)) {
                hasDigit = true;
            } else {
                hasSpecialCharacter = true;
            }
        }

        //Password does not contain all required chars
        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSpecialCharacter) {
            System.out.println("Password must contain uppercase, lowercase, a number, and a special character.");
            return false;
        }

        return true;
    }
}

