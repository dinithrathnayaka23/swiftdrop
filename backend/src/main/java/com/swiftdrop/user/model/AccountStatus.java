package com.swiftdrop.user.model;

/** Values of {@code users.account_status}. */
public enum AccountStatus {
    ACTIVE,
    /** e.g. a merchant account waiting for admin approval */
    PENDING_APPROVAL,
    SUSPENDED,
    DEACTIVATED
}
