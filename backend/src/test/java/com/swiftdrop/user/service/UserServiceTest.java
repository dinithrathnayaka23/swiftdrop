package com.swiftdrop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.event.UserAccountStatusChangedEvent;
import com.swiftdrop.user.event.UserRoleChangedEvent;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.model.User;
import com.swiftdrop.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private UserService userService;

    private User stored(Role role, AccountStatus status) {
        User user = storedUser();
        user.setRole(role);
        user.setAccountStatus(status);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

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
        assertThatThrownBy(() -> userService.getUser(id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.changeRole(id, Role.COURIER)).isInstanceOf(NotFoundException.class);
        assertThat(userService.findUser(id)).isEmpty();
    }

    // --- listing -------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked") // any(Specification.class) on a generic type
    void listsMatchingUsersNewestFirstWithoutCredentials() {
        User user = storedUser();
        user.setPhone("+94712345678");
        user.setEmailVerifiedAt(Instant.now());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        Page<UserSummary> page = userService.listUsers(
                new UserSearch("nimal", Role.CUSTOMER, AccountStatus.ACTIVE), PageRequest.of(2, 20));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(UserService.NEWEST_FIRST);
        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(user.getId());
            assertThat(summary.phone()).isEqualTo("+94712345678");
            assertThat(summary.emailVerified()).isTrue();
        });
    }

    @Test
    void findsASingleUser() {
        User user = stored(Role.COURIER, AccountStatus.ACTIVE);

        assertThat(userService.findUser(user.getId())).get()
                .extracting(UserSummary::role).isEqualTo(Role.COURIER);
    }

    // --- changing the role -----------------------------------------------------------------------

    @Test
    void changesACustomerIntoACourierAndAnnouncesIt() {
        User user = stored(Role.CUSTOMER, AccountStatus.ACTIVE);

        UserSummary changed = userService.changeRole(user.getId(), Role.COURIER);

        assertThat(changed.role()).isEqualTo(Role.COURIER);
        assertThat(user.getRole()).isEqualTo(Role.COURIER);
        verify(eventPublisher).publishEvent(new UserRoleChangedEvent(user.getId(), Role.CUSTOMER, Role.COURIER));
    }

    @Test
    void keepingTheSameRoleChangesNothing() {
        User user = stored(Role.COURIER, AccountStatus.ACTIVE);

        userService.changeRole(user.getId(), Role.COURIER);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "DISPATCHER_ADMIN, CUSTOMER",
            "CUSTOMER, DISPATCHER_ADMIN",
            "MERCHANT, CUSTOMER",
            "COURIER, MERCHANT"})
    void neverGivesOrTakesAwayTheAdminOrMerchantRole(Role from, Role to) {
        User user = stored(from, AccountStatus.ACTIVE);

        assertThatThrownBy(() -> userService.changeRole(user.getId(), to)).isInstanceOf(ConflictException.class);
        assertThat(user.getRole()).isEqualTo(from);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- changing the status -------------------------------------------------------------------

    @Test
    void deactivatingAnnouncesThatAccessWasRevoked() {
        User user = stored(Role.COURIER, AccountStatus.ACTIVE);

        UserSummary changed = userService.deactivate(user.getId());

        assertThat(changed.accountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        ArgumentCaptor<UserAccountStatusChangedEvent> event = ArgumentCaptor.forClass(UserAccountStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().accessRevoked()).isTrue();
        assertThat(event.getValue().role()).isEqualTo(Role.COURIER);
    }

    @Test
    void approvingAMerchantDoesNotRevokeAccess() {
        User user = stored(Role.MERCHANT, AccountStatus.PENDING_APPROVAL);

        userService.changeAccountStatus(user.getId(), AccountStatus.ACTIVE);

        ArgumentCaptor<UserAccountStatusChangedEvent> event = ArgumentCaptor.forClass(UserAccountStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().accessRevoked()).isFalse();
    }

    @Test
    void theAdminAccountAlwaysStaysActive() {
        User admin = stored(Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE);

        assertThatThrownBy(() -> userService.deactivate(admin.getId())).isInstanceOf(ConflictException.class);
        assertThat(admin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        userService.changeAccountStatus(admin.getId(), AccountStatus.ACTIVE); // already active: fine
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void theAdminScreenCanSuspendAndReactivateAccounts() {
        User user = stored(Role.MERCHANT, AccountStatus.ACTIVE);

        userService.changeAccountStatusAsAdmin(user.getId(), AccountStatus.SUSPENDED);
        userService.changeAccountStatusAsAdmin(user.getId(), AccountStatus.ACTIVE);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void theAdminScreenLeavesMerchantApprovalToTheMerchantReview() {
        User pending = stored(Role.MERCHANT, AccountStatus.PENDING_APPROVAL);
        User rejected = stored(Role.MERCHANT, AccountStatus.DEACTIVATED);
        UUID customerId = UUID.randomUUID(); // refused before the account is even loaded

        assertThatThrownBy(() -> userService.changeAccountStatusAsAdmin(pending.getId(), AccountStatus.ACTIVE))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> userService.changeAccountStatusAsAdmin(rejected.getId(), AccountStatus.ACTIVE))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> userService.changeAccountStatusAsAdmin(customerId, AccountStatus.PENDING_APPROVAL))
                .isInstanceOf(BadRequestException.class);
        assertThat(pending.getAccountStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(rejected.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void aDeactivatedCustomerCanBeReactivated() {
        User user = stored(Role.CUSTOMER, AccountStatus.DEACTIVATED);

        userService.changeAccountStatusAsAdmin(user.getId(), AccountStatus.ACTIVE);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
