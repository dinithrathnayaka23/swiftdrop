package com.swiftdrop.common.security;

/**
 * Application roles. Names must match the rows seeded in {@code V1__init.sql}.
 * Use with method security, e.g. {@code @PreAuthorize("hasRole('COURIER')")}.
 */
public enum Role {
    CUSTOMER,
    MERCHANT,
    COURIER,
    DISPATCHER_ADMIN;

    /** Spring Security authority name, e.g. {@code ROLE_COURIER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
