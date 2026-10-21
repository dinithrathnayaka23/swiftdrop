package com.swiftdrop.common.security;

import java.util.Set;

/**
 * Principal placed in the SecurityContext by the JWT filter.
 * Inject in controllers with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(String userId, Set<Role> roles) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
