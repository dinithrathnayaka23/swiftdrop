package com.swiftdrop.user.api;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the user module. Other modules find, list, create and manage accounts through this
 * interface and never use the user repository or entity directly.
 * <p>
 * Changing an account's role or status publishes {@link com.swiftdrop.user.event.UserRoleChangedEvent}
 * or {@link com.swiftdrop.user.event.UserAccountStatusChangedEvent} once committed; the identity module
 * ends the user's sessions on them, and other modules (e.g. dispatch for couriers) may react too.
 */
public interface UserApi {

    /** The account, or empty if there is none with this ID. */
    Optional<UserSummary> findUser(UUID userId);

    /** Accounts matching the search, newest first. */
    Page<UserSummary> listUsers(UserSearch search, Pageable pageable);

    /** Whether an account already uses this email (case-insensitive). Check before {@link #createUser}. */
    boolean emailExists(String email);

    /**
     * Creates an account, e.g. a courier added by dispatch. Check {@link #emailExists} first; the
     * database's unique index is the final guard.
     */
    UserAccount createUser(NewUser newUser);

    /**
     * Sets the account status: {@code DEACTIVATED} or {@code SUSPENDED} stop the user signing in and
     * end their sessions. The admin account always stays {@code ACTIVE}.
     *
     * @throws com.swiftdrop.common.exception.NotFoundException if there is no such account
     * @throws com.swiftdrop.common.exception.ConflictException  for the admin account
     */
    UserSummary changeAccountStatus(UUID userId, AccountStatus status);

    /** Deactivates the account; see {@link #changeAccountStatus}. */
    default UserSummary deactivate(UUID userId) {
        return changeAccountStatus(userId, AccountStatus.DEACTIVATED);
    }

    /**
     * Changes the role between {@code CUSTOMER} and {@code COURIER} and ends the user's sessions, so
     * they sign in again with the new role. Admin and merchant roles can't be given or taken away
     * here: there is a single admin (set in the server configuration), and a merchant account belongs
     * to a registered business.
     *
     * @throws com.swiftdrop.common.exception.NotFoundException if there is no such account
     * @throws com.swiftdrop.common.exception.ConflictException  if the change involves the admin or merchant role
     */
    UserSummary changeRole(UUID userId, Role role);
}
