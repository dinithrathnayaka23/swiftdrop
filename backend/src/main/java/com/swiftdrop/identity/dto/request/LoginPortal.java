package com.swiftdrop.identity.dto.request;

import com.swiftdrop.common.security.Role;
import java.util.Set;

/**
 * The sign-in page a login comes from. Each page is for certain roles only, so staff use their own
 * pages and customers can't stumble into the staff ones. Not a security boundary on its own: every
 * endpoint still checks the role in the access token.
 */
public enum LoginPortal {
    /** The main sign-in page (/login). */
    PUBLIC(Set.of(Role.CUSTOMER, Role.MERCHANT)),
    /** The courier app (/courier/login). */
    COURIER(Set.of(Role.COURIER)),
    /** The admin portal (/admin/login). */
    ADMIN(Set.of(Role.DISPATCHER_ADMIN));

    private final Set<Role> roles;

    LoginPortal(Set<Role> roles) {
        this.roles = roles;
    }

    public boolean allows(Role role) {
        return roles.contains(role);
    }
}
