
package com.mydrive.drive.account;

public class AppUserNotFoundException extends RuntimeException {
    public AppUserNotFoundException(String email) {
        super("AppUser not found: " + email);
    }
}