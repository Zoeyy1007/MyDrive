/*
 * PHASE 7 SERVER authenticated identity created by the device-token filter.
 *
 * Create a record containing deviceId, userId, and email. Either implement
 * java.security.Principal or make getName() return email. Existing
 * CurrentUserService expects Authentication.getName() to be the user's email.
 *
 * Never put the raw bearer token in this object or in application logs.
 */
package com.mydrive.drive.device;

import java.security.Principal;
import java.util.UUID;

public record DevicePrincipal(
        UUID deviceId,
        UUID userId,
        String email
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
