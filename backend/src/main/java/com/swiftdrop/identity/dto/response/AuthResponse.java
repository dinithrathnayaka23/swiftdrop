package com.swiftdrop.identity.dto.response;

import com.swiftdrop.common.security.Role;

/**
 * Returned by sign-in, Google sign-in, refresh and customer registration. Matches the frontend's
 * AuthResponse type. The refresh token is not in the body: it is set as an httpOnly cookie.
 *
 * @param token      short-lived access token (send as {@code Authorization: Bearer ...})
 * @param expiresIn  access token lifetime in seconds
 * @param rememberMe whether this session survives a browser restart
 */
public record AuthResponse(
        String token,
        long expiresIn,
        boolean rememberMe,
        AuthUser user) {

    public record AuthUser(String id, String displayName, Role role) {
    }

    @Override
    public String toString() {
        return "AuthResponse[user=" + user + ", rememberMe=" + rememberMe + "]";
    }
}
