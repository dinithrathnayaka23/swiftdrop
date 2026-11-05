package com.swiftdrop.user.api;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.model.AccountStatus;

/**
 * Filters for {@link UserApi#listUsers}. Every filter is optional ({@code null} or blank matches all).
 *
 * @param text   part of the name, email or phone number, case-insensitive
 * @param role   only accounts with this role
 * @param status only accounts with this status
 */
public record UserSearch(String text, Role role, AccountStatus status) {

    /** No filters: every account. */
    public static UserSearch all() {
        return new UserSearch(null, null, null);
    }
}
