/*
 * PHASE 2A: Domain exception for duplicate registration.
 *
 * Create a public class extending RuntimeException.
 * Add a constructor receiving String email and pass a safe message to super.
 * Do not include passwords or other secrets in exception messages.
 */
package com.mydrive.drive.account;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email already exists: " + email);
    }
}