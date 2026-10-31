package com.swiftdrop.user.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.mapper.UserMapper;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.model.User;
import com.swiftdrop.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the user module. Other modules use this, never UserRepository or the User entity. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** Emails are stored trimmed and lower-case. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

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

    @Transactional
    public void changeAccountStatus(UUID userId, AccountStatus status) {
        load(userId).setAccountStatus(status);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
