package com.swiftdrop.user.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.event.UserAccountStatusChangedEvent;
import com.swiftdrop.user.event.UserRoleChangedEvent;
import com.swiftdrop.user.mapper.UserMapper;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.model.User;
import com.swiftdrop.user.repository.UserRepository;
import com.swiftdrop.user.repository.UserSpecifications;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user module's service. Other modules use it through {@link UserApi}, never UserRepository or
 * the User entity; the identity module also uses the sign-in specific methods here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserApi {

    /** Newest first; the ID keeps the order stable between pages for accounts created together. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Emails are stored trimmed and lower-case. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public boolean anyUserWithRole(Role role) {
        return userRepository.existsByRole(role);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findAccountByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email)).map(UserMapper::toAccount);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findAccountByGoogleSubject(String googleSubject) {
        return userRepository.findByGoogleSubject(googleSubject).map(UserMapper::toAccount);
    }

    @Transactional(readOnly = true)
    public UserAccount getAccount(UUID userId) {
        return UserMapper.toAccount(load(userId));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        return UserMapper.toProfile(load(userId));
    }

    /** Creates an account. Callers check {@link #emailExists} first; the DB unique index is the final guard. */
    @Override
    @Transactional
    public UserAccount createUser(NewUser newUser) {
        User user = new User();
        user.setFullName(newUser.fullName().trim());
        user.setEmail(normalizeEmail(newUser.email()));
        user.setPhone(newUser.phone());
        user.setPasswordHash(newUser.passwordHash());
        user.setGoogleSubject(newUser.googleSubject());
        user.setRole(newUser.role());
        user.setAccountStatus(newUser.accountStatus());
        user.setEmailVerifiedAt(newUser.emailVerified() ? Instant.now() : null);
        return UserMapper.toAccount(userRepository.saveAndFlush(user));
    }

    /** The account with this role, for roles that have a single account (the admin). */
    @Transactional(readOnly = true)
    public Optional<UserAccount> findOnlyAccountWithRole(Role role) {
        return userRepository.findFirstByRole(role).map(UserMapper::toAccount);
    }

    /** Changes the sign-in email. Callers check {@link #emailExists} first; the DB unique index is the final guard. */
    @Transactional
    public void changeEmail(UUID userId, String email) {
        load(userId).setEmail(normalizeEmail(email));
    }

    @Transactional
    public void changeFullName(UUID userId, String fullName) {
        load(userId).setFullName(fullName.trim());
    }

    @Transactional
    public void changePasswordHash(UUID userId, String passwordHash) {
        load(userId).setPasswordHash(passwordHash);
    }

    /** Records that the user proved they own their email address. Idempotent. */
    @Transactional
    public UserAccount markEmailVerified(UUID userId) {
        User user = load(userId);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return UserMapper.toAccount(user);
    }

    /**
     * Hands an account whose email was never verified to the person Google has proven owns that
     * email: the unknown password is removed (whoever registered it may not own the address) and
     * the Google account becomes the sign-in method. Callers must also end its sessions.
     */
    @Transactional
    public UserAccount claimUnverifiedAccountWithGoogle(UUID userId, String googleSubject) {
        User user = load(userId);
        if (user.getEmailVerifiedAt() != null) {
            throw new IllegalStateException("Account email is already verified; link it instead");
        }
        user.setGoogleSubject(googleSubject);
        user.setPasswordHash(null);
        user.setEmailVerifiedAt(Instant.now());
        return UserMapper.toAccount(user);
    }

    /** Connects a Google account to an existing account whose email is verified. */
    @Transactional
    public UserAccount linkGoogleAccount(UUID userId, String googleSubject) {
        User user = load(userId);
        user.setGoogleSubject(googleSubject);
        return UserMapper.toAccount(user);
    }

    @Override
    @Transactional
    public UserSummary changeAccountStatus(UUID userId, AccountStatus status) {
        User user = load(userId);
        AccountStatus previous = user.getAccountStatus();
        if (previous == status) {
            return UserMapper.toSummary(user);
        }
        if (user.getRole() == Role.DISPATCHER_ADMIN) {
            throw new ConflictException("The admin account always stays active");
        }
        user.setAccountStatus(status);
        log.info("Account status of user {} changed from {} to {}", userId, previous, status);
        eventPublisher.publishEvent(new UserAccountStatusChangedEvent(userId, user.getRole(), previous, status));
        return UserMapper.toSummary(user);
    }

    /**
     * The admin's User Management screen changing an account's status. On top of
     * {@link #changeAccountStatus}: merchant registrations are decided through merchant approval,
     * not here, so a pending account can't be changed, no account can be put back to pending, and
     * a deactivated (e.g. rejected) merchant can't be reactivated.
     */
    @Transactional
    public UserSummary changeAccountStatusAsAdmin(UUID userId, AccountStatus status) {
        if (status == AccountStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only a new merchant registration waits for approval");
        }
        User user = load(userId);
        if (user.getAccountStatus() == AccountStatus.PENDING_APPROVAL) {
            throw new ConflictException("This merchant is waiting for approval: approve or reject the registration instead");
        }
        if (user.getRole() == Role.MERCHANT && user.getAccountStatus() == AccountStatus.DEACTIVATED
                && status != AccountStatus.DEACTIVATED) {
            throw new ConflictException(
                    "A deactivated merchant account can't be reactivated here. Suspend a merchant to pause it instead.");
        }
        return changeAccountStatus(userId, status);
    }

    @Override
    @Transactional
    public UserSummary changeRole(UUID userId, Role role) {
        User user = load(userId);
        Role previous = user.getRole();
        if (previous == role) {
            return UserMapper.toSummary(user);
        }
        if (previous == Role.DISPATCHER_ADMIN || role == Role.DISPATCHER_ADMIN) {
            throw new ConflictException("There is only one admin account, set in the server configuration");
        }
        if (previous == Role.MERCHANT || role == Role.MERCHANT) {
            throw new ConflictException(
                    "Merchant accounts belong to a registered business and are created through merchant registration");
        }
        user.setRole(role);
        log.info("Role of user {} changed from {} to {}", userId, previous, role);
        eventPublisher.publishEvent(new UserRoleChangedEvent(userId, previous, role));
        return UserMapper.toSummary(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findUser(UUID userId) {
        return userRepository.findById(userId).map(UserMapper::toSummary);
    }

    /** The account, for the admin's user screen. */
    @Transactional(readOnly = true)
    public UserSummary getUser(UUID userId) {
        return UserMapper.toSummary(load(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSummary> listUsers(UserSearch search, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return userRepository.findAll(UserSpecifications.matching(search), newestFirst).map(UserMapper::toSummary);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
