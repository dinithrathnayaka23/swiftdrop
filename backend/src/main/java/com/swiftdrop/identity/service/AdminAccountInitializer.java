package com.swiftdrop.identity.service;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SwiftDrop has exactly one admin (DISPATCHER_ADMIN), and its credentials are ADMIN_EMAIL /
 * ADMIN_PASSWORD from the environment. On every startup the admin account is created, or updated
 * to match them (email, name, password, active). The ux_users_single_admin index stops a second
 * admin from ever existing, and the password can't be reset by email, so the environment stays the
 * only source of the credentials. Startup fails if they are missing or unusable.
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminAccountInitializer(UserService userService, PasswordEncoder passwordEncoder,
                                   @Value("${swiftdrop.bootstrap.admin.email:}") String email,
                                   @Value("${swiftdrop.bootstrap.admin.password:}") String password,
                                   @Value("${swiftdrop.bootstrap.admin.full-name:SwiftDrop Admin}") String fullName) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim();
        this.password = password;
        this.fullName = fullName.isBlank() ? "SwiftDrop Admin" : fullName.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD (at least " + MIN_PASSWORD_LENGTH
                    + " characters) must be set: they are the credentials of the SwiftDrop admin");
        }
        userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN)
                .ifPresentOrElse(this::syncWithEnvironment, this::create);
    }

    private void create() {
        requireEmailNotTaken();
        // Set up by the operator from configuration, so the email counts as verified.
        UserAccount admin = userService.createUser(NewUser.withPassword(fullName, email, null,
                passwordEncoder.encode(password), Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE).withVerifiedEmail());
        log.info("Admin account {} created from ADMIN_EMAIL / ADMIN_PASSWORD", admin.id());
    }

    private void syncWithEnvironment(UserAccount admin) {
        List<String> changed = new ArrayList<>();
        if (!UserService.normalizeEmail(email).equals(admin.email())) {
            requireEmailNotTaken();
            userService.changeEmail(admin.id(), email);
            changed.add("email");
        }
        if (!fullName.equals(admin.fullName())) {
            userService.changeFullName(admin.id(), fullName);
            changed.add("name");
        }
        if (!admin.hasPassword() || !passwordEncoder.matches(password, admin.passwordHash())) {
            // Sessions started with the old password are refused at their next renewal
            // (the refresh token carries a fingerprint of the password hash).
            userService.changePasswordHash(admin.id(), passwordEncoder.encode(password));
            changed.add("password");
        }
        if (admin.accountStatus() != AccountStatus.ACTIVE) {
            userService.changeAccountStatus(admin.id(), AccountStatus.ACTIVE);
            changed.add("status");
        }
        if (!admin.emailVerified()) {
            userService.markEmailVerified(admin.id());
        }
        if (!changed.isEmpty()) {
            log.info("Admin account {} updated to match the environment: {}", admin.id(), String.join(", ", changed));
        }
    }

    private void requireEmailNotTaken() {
        if (userService.emailExists(email)) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL is already used by a non-admin account; choose another email for the admin");
        }
    }
}
