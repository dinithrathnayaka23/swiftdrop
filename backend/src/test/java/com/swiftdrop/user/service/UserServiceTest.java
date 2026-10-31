package com.swiftdrop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.model.User;
import com.swiftdrop.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    private static User storedUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nimal Perera");
        user.setEmail("nimal@example.com");
        user.setPasswordHash("$2a$hash");
        user.setRole(Role.CUSTOMER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return user;
    }

    @Test
    void emailsAreStoredTrimmedAndLowerCase() {
        assertThat(UserService.normalizeEmail("  Nimal@Example.COM ")).isEqualTo("nimal@example.com");
    }

    @Test
    void emailLookupsAreNormalised() {
        when(userRepository.existsByEmail("nimal@example.com")).thenReturn(true);
        when(userRepository.findByEmail("nimal@example.com")).thenReturn(Optional.of(storedUser()));

        assertThat(userService.emailExists(" NIMAL@example.com")).isTrue();
        assertThat(userService.findAccountByEmail("Nimal@Example.com")).get()
                .extracting(UserAccount::fullName).isEqualTo("Nimal Perera");
    }

    @Test
    void createUserCleansUpTheInputAndReturnsTheAccount() {
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        UserAccount account = userService.createUser(NewUser.withGoogle("  Kamal Silva ", "Kamal@Gmail.com",
                "google-sub", Role.CUSTOMER, AccountStatus.ACTIVE));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo("Kamal Silva");
        assertThat(saved.getValue().getEmail()).isEqualTo("kamal@gmail.com");
        assertThat(saved.getValue().getPasswordHash()).isNull();
        assertThat(saved.getValue().getGoogleSubject()).isEqualTo("google-sub");
        assertThat(account.id()).isNotNull();
        assertThat(account.hasPassword()).isFalse();
    }

    @Test
    void changesAreAppliedToTheLoadedEntity() {
        User user = storedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.changePasswordHash(user.getId(), "$2a$new");
        userService.changeAccountStatus(user.getId(), AccountStatus.SUSPENDED);
        UserAccount linked = userService.linkGoogleAccount(user.getId(), "google-sub");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$new");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(linked.googleSubject()).isEqualTo("google-sub");
    }

    @Test
    void passwordAccountsStartUnverifiedAndGoogleAccountsVerified() {
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UserAccount password = userService.createUser(NewUser.withPassword("A", "a@b.lk", null, "$2a$h",
                Role.CUSTOMER, AccountStatus.ACTIVE));
        UserAccount google = userService.createUser(NewUser.withGoogle("B", "b@gmail.com", "sub", Role.CUSTOMER,
                AccountStatus.ACTIVE));
        UserAccount admin = userService.createUser(NewUser.withPassword("C", "c@b.lk", null, "$2a$h",
                Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE).withVerifiedEmail());

        assertThat(password.emailVerified()).isFalse();
        assertThat(google.emailVerified()).isTrue();
        assertThat(admin.emailVerified()).isTrue();
    }

    @Test
    void markEmailVerifiedKeepsTheFirstVerificationTime() {
        User user = storedUser();
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        user.setEmailVerifiedAt(first);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(userService.markEmailVerified(user.getId()).emailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(first);
    }

    @Test
    void claimingAnUnverifiedAccountWithGoogleRemovesTheUnprovenPassword() {
        User user = storedUser(); // has a password, email never verified
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserAccount claimed = userService.claimUnverifiedAccountWithGoogle(user.getId(), "google-sub");

        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleSubject()).isEqualTo("google-sub");
        assertThat(claimed.emailVerified()).isTrue();
        assertThat(claimed.hasPassword()).isFalse();
    }

    @Test
    void verifiedAccountsCannotBeClaimed() {
        User user = storedUser();
        user.setEmailVerifiedAt(Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.claimUnverifiedAccountWithGoogle(user.getId(), "google-sub"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getPasswordHash()).isEqualTo("$2a$hash");
    }

    @Test
    void findsAccountsByGoogleId() {
        User user = storedUser();
        user.setGoogleSubject("google-sub");
        when(userRepository.findByGoogleSubject("google-sub")).thenReturn(Optional.of(user));

        assertThat(userService.findAccountByGoogleSubject("google-sub")).get()
                .extracting(UserAccount::id).isEqualTo(user.getId());
    }

    @Test
    void unknownUserIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getAccount(id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.getProfile(id)).isInstanceOf(NotFoundException.class);
    }
}
