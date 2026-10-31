package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.identity.dto.request.CustomerRegistrationRequest;
import com.swiftdrop.identity.dto.request.GoogleLoginRequest;
import com.swiftdrop.identity.dto.request.LoginPortal;
import com.swiftdrop.identity.dto.request.LoginRequest;
import com.swiftdrop.identity.dto.request.MerchantRegistrationRequest;
import com.swiftdrop.identity.dto.response.AuthResponse;
import com.swiftdrop.identity.exception.AccountNotActiveException;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.swiftdrop.identity.security.LoginAttemptGuard;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** AuthService on its own: every collaborator is a Mockito mock. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_HASH = "dummy-hash";
    private static final String IP = "203.0.113.7";

    @Mock private UserService userService;
    @Mock private MerchantService merchantService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GoogleIdTokenVerifier googleVerifier;
    @Mock private SessionService sessionService;
    @Mock private LoginAttemptGuard loginAttempts;
    @Mock private EmailVerificationService emailVerification;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("swiftdrop-timing-equaliser")).thenReturn(DUMMY_HASH);
        authService = new AuthService(userService, merchantService, passwordEncoder, googleVerifier, sessionService,
                loginAttempts, emailVerification);
    }

    private static UserAccount account(String passwordHash, String googleSubject, Role role, AccountStatus status) {
        return new UserAccount(UUID.randomUUID(), "Nimal Perera", "nimal@example.com", passwordHash, googleSubject,
                role, status, true);
    }

    private static UserAccount unverified(UserAccount account) {
        return new UserAccount(account.id(), account.fullName(), account.email(), account.passwordHash(),
                account.googleSubject(), account.role(), account.accountStatus(), false);
    }

    private static UserAccount activeCustomer() {
        return account("$2a$hash", null, Role.CUSTOMER, AccountStatus.ACTIVE);
    }

    private static IssuedSession session(UserAccount user, boolean rememberMe) {
        return new IssuedSession(new AuthResponse("access-jwt", 900, rememberMe,
                new AuthResponse.AuthUser(user.id().toString(), user.fullName(), user.role())),
                "refresh-jwt", Duration.ofDays(1));
    }

    private void stubSessions() {
        when(sessionService.issue(any(), anyBoolean()))
                .thenAnswer(inv -> session(inv.getArgument(0), inv.getArgument(1)));
    }

    private static void assertApiError(Throwable thrown, ErrorCode code) {
        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getCode()).isEqualTo(code);
    }

    @Nested
    class Login {

        @Test
        void correctPasswordStartsASessionAndClearsFailedAttempts() {
            UserAccount user = activeCustomer();
            when(userService.findAccountByEmail("nimal@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("parcel2026", "$2a$hash")).thenReturn(true);
            stubSessions();

            IssuedSession issued = authService.login(new LoginRequest("nimal@example.com", "parcel2026", true), IP);

            assertThat(issued.response().user().id()).isEqualTo(user.id().toString());
            verify(sessionService).issue(user, true);
            verify(loginAttempts).checkAllowed("nimal@example.com", IP);
            verify(loginAttempts).recordSuccess("nimal@example.com", IP);
        }

        @Test
        void rememberMeDefaultsToOff() {
            UserAccount user = activeCustomer();
            when(userService.findAccountByEmail(anyString())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            stubSessions();

            authService.login(new LoginRequest("nimal@example.com", "parcel2026", null), IP);

            verify(sessionService).issue(user, false);
        }

        @Test
        void unknownEmailStillChecksAPasswordAndCountsAsAFailure() {
            when(userService.findAccountByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "parcel2026", false), IP))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
            verify(passwordEncoder).matches("parcel2026", DUMMY_HASH); // same timing as a wrong password
            verify(loginAttempts).recordFailure("ghost@example.com", IP);
        }

        @Test
        void wrongPasswordIsCountedAndGetsTheSameMessage() {
            when(userService.findAccountByEmail(anyString())).thenReturn(Optional.of(activeCustomer()));
            when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("nimal@example.com", "wrong", false), IP))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
            verify(loginAttempts).recordFailure("nimal@example.com", IP);
            verify(sessionService, never()).issue(any(), anyBoolean());
        }

        @Test
        void pausedAccountIsRefusedBeforeThePasswordIsChecked() {
            doThrow(new RateLimitExceededException("Too many failed sign-in attempts.", Duration.ofMinutes(10)))
                    .when(loginAttempts).checkAllowed("nimal@example.com", IP);

            assertThatThrownBy(() -> authService.login(new LoginRequest("nimal@example.com", "parcel2026", false), IP))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining("10 minutes");
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        void googleOnlyAccountCannotUseThePasswordForm() {
            when(userService.findAccountByEmail(anyString()))
                    .thenReturn(Optional.of(account(null, "google-sub", Role.CUSTOMER, AccountStatus.ACTIVE)));

            assertThatThrownBy(() -> authService.login(new LoginRequest("nimal@example.com", "anything1", false), IP))
                    .isInstanceOf(UnauthorizedException.class);
            verify(passwordEncoder).matches("anything1", DUMMY_HASH);
        }

        @Test
        void pendingMerchantIsToldAboutApprovalOnlyAfterTheCorrectPassword() {
            when(userService.findAccountByEmail(anyString()))
                    .thenReturn(Optional.of(account("$2a$hash", null, Role.MERCHANT, AccountStatus.PENDING_APPROVAL)));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            Throwable thrown = catchThrowable(
                    () -> authService.login(new LoginRequest("shop@example.com", "merchant2026", false), IP));

            assertThat(thrown).isInstanceOf(AccountNotActiveException.class);
            assertApiError(thrown, ErrorCode.ACCOUNT_PENDING_APPROVAL);
            verify(sessionService, never()).issue(any(), anyBoolean());
        }

        @Test
        void unconfirmedEmailBlocksSignInAfterTheCorrectPassword() {
            when(userService.findAccountByEmail(anyString())).thenReturn(Optional.of(unverified(activeCustomer())));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertApiError(catchThrowable(
                    () -> authService.login(new LoginRequest("nimal@example.com", "parcel2026", false), IP)),
                    ErrorCode.EMAIL_NOT_VERIFIED);
            verify(sessionService, never()).issue(any(), anyBoolean());
        }

        @Test
        void staffSignInOnTheirOwnPages() {
            UserAccount courier = account("$2a$hash", null, Role.COURIER, AccountStatus.ACTIVE);
            UserAccount admin = account("$2a$hash", null, Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE);
            when(userService.findAccountByEmail("rider@swiftdrop.lk")).thenReturn(Optional.of(courier));
            when(userService.findAccountByEmail("admin@swiftdrop.lk")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            stubSessions();

            authService.login(new LoginRequest("rider@swiftdrop.lk", "parcel2026", true, LoginPortal.COURIER), IP);
            authService.login(new LoginRequest("admin@swiftdrop.lk", "parcel2026", false, LoginPortal.ADMIN), IP);

            verify(sessionService).issue(courier, true);
            verify(sessionService).issue(admin, false);
        }

        @Test
        void accountOnTheWrongSignInPageGetsNoSessionAndIsToldWhereToGo() {
            when(userService.findAccountByEmail("rider@swiftdrop.lk"))
                    .thenReturn(Optional.of(account("$2a$hash", null, Role.COURIER, AccountStatus.ACTIVE)));
            when(userService.findAccountByEmail("admin@swiftdrop.lk"))
                    .thenReturn(Optional.of(account("$2a$hash", null, Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE)));
            when(userService.findAccountByEmail("nimal@example.com")).thenReturn(Optional.of(activeCustomer()));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            // A courier on the main page (no portal given), an admin on the courier page,
            // a customer on the admin page.
            Throwable courierOnMain = catchThrowable(
                    () -> authService.login(new LoginRequest("rider@swiftdrop.lk", "parcel2026", false), IP));
            Throwable adminOnCourier = catchThrowable(() -> authService.login(
                    new LoginRequest("admin@swiftdrop.lk", "parcel2026", false, LoginPortal.COURIER), IP));
            Throwable customerOnAdmin = catchThrowable(() -> authService.login(
                    new LoginRequest("nimal@example.com", "parcel2026", false, LoginPortal.ADMIN), IP));

            assertApiError(courierOnMain, ErrorCode.WRONG_SIGN_IN_PAGE);
            assertThat(courierOnMain).hasMessageContaining("courier app");
            assertApiError(adminOnCourier, ErrorCode.WRONG_SIGN_IN_PAGE);
            assertThat(adminOnCourier).hasMessageContaining("admin portal");
            assertApiError(customerOnAdmin, ErrorCode.WRONG_SIGN_IN_PAGE);
            assertThat(customerOnAdmin).hasMessageContaining("main sign-in page");
            verify(sessionService, never()).issue(any(), anyBoolean());
        }

        @Test
        void wrongPasswordOnAStaffPageStillSaysOnlyInvalidCredentials() {
            when(userService.findAccountByEmail(anyString())).thenReturn(Optional.of(activeCustomer()));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            // The role is never revealed without the correct password.
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("nimal@example.com", "wrong", false, LoginPortal.ADMIN), IP))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        void suspendedAccountIsDisabled() {
            when(userService.findAccountByEmail(anyString()))
                    .thenReturn(Optional.of(account("$2a$hash", null, Role.CUSTOMER, AccountStatus.SUSPENDED)));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertApiError(catchThrowable(
                    () -> authService.login(new LoginRequest("nimal@example.com", "parcel2026", false), IP)),
                    ErrorCode.ACCOUNT_DISABLED);
        }
    }

    @Nested
    class Registration {

        private final CustomerRegistrationRequest customer =
                new CustomerRegistrationRequest("Nimal Perera", "nimal@example.com", "+94712345678", "parcel2026");
        private final MerchantRegistrationRequest merchant = new MerchantRegistrationRequest("BrightMart",
                "PV00234567", "Sunil Fernando", "sunil@brightmart.lk", "+94771234567", "12 Galle Road", "merchant2026");

        private String conflictField(Throwable thrown) {
            assertThat(thrown).isInstanceOf(ConflictException.class);
            return ((ConflictException) thrown).getFieldErrors().getFirst().field();
        }

        @Test
        void duplicateCustomerEmailIsAFieldConflict() {
            when(userService.emailExists("nimal@example.com")).thenReturn(true);

            assertThat(conflictField(catchThrowable(() -> authService.registerCustomer(customer)))).isEqualTo("email");
            verify(userService, never()).createUser(any());
        }

        @Test
        void sameEmailRegisteredAtTheSameMomentIsStillAFieldConflict() {
            when(userService.emailExists(anyString())).thenReturn(false);
            when(passwordEncoder.encode("parcel2026")).thenReturn("$2a$new-hash");
            when(userService.createUser(any())).thenThrow(new DataIntegrityViolationException("ux_users_email_lower"));

            assertThat(conflictField(catchThrowable(() -> authService.registerCustomer(customer)))).isEqualTo("email");
        }

        @Test
        void customerIsCreatedUnverifiedAndEmailedALinkInsteadOfSignedIn() {
            when(userService.emailExists(anyString())).thenReturn(false);
            when(passwordEncoder.encode("parcel2026")).thenReturn("$2a$new-hash");
            when(userService.createUser(any())).thenAnswer(inv -> {
                NewUser n = inv.getArgument(0);
                return new UserAccount(UUID.randomUUID(), n.fullName(), n.email(), n.passwordHash(), null, n.role(),
                        n.accountStatus(),
                        n.emailVerified());
            });

            UserAccount registered = authService.registerCustomer(customer);

            ArgumentCaptor<NewUser> created = ArgumentCaptor.forClass(NewUser.class);
            verify(userService).createUser(created.capture());
            assertThat(created.getValue()).isEqualTo(NewUser.withPassword("Nimal Perera", "nimal@example.com",
                    "+94712345678", "$2a$new-hash", Role.CUSTOMER, AccountStatus.ACTIVE));
            assertThat(created.getValue().emailVerified()).isFalse();
            verify(emailVerification).sendVerification(registered);
            verify(sessionService, never()).issue(any(), anyBoolean()); // no session until confirmed
        }

        @Test
        void duplicateMerchantEmail() {
            when(userService.emailExists(anyString())).thenReturn(true);

            assertThat(conflictField(catchThrowable(() -> authService.registerMerchant(merchant)))).isEqualTo("email");
        }

        @Test
        void duplicateRegistrationNumber() {
            when(userService.emailExists(anyString())).thenReturn(false);
            when(merchantService.registrationNoExists("PV00234567")).thenReturn(true);

            assertThat(conflictField(catchThrowable(() -> authService.registerMerchant(merchant))))
                    .isEqualTo("registrationNo");
            verify(userService, never()).createUser(any());
        }

        @Test
        void registrationNumberTakenAtTheSameMomentPointsAtThatField() {
            when(userService.emailExists(anyString())).thenReturn(false);
            when(merchantService.registrationNoExists(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$m-hash");
            when(userService.createUser(any())).thenReturn(account("$2a$m-hash", null, Role.MERCHANT,
                    AccountStatus.PENDING_APPROVAL));
            when(merchantService.registerPending(any())).thenThrow(new DataIntegrityViolationException("insert failed",
                    new SQLException("duplicate key value violates unique constraint \"ux_merchants_registration_no\"")));

            assertThat(conflictField(catchThrowable(() -> authService.registerMerchant(merchant))))
                    .isEqualTo("registrationNo");
        }

        @Test
        void merchantUserAndBusinessAreBothCreatedPending() {
            UUID userId = UUID.randomUUID();
            UUID merchantId = UUID.randomUUID();
            Instant submitted = Instant.parse("2026-10-31T10:00:00Z");
            when(userService.emailExists(anyString())).thenReturn(false);
            when(merchantService.registrationNoExists(anyString())).thenReturn(false);
            when(passwordEncoder.encode("merchant2026")).thenReturn("$2a$m-hash");
            when(userService.createUser(any())).thenReturn(new UserAccount(userId, "Sunil Fernando",
                    "sunil@brightmart.lk", "$2a$m-hash", null, Role.MERCHANT, AccountStatus.PENDING_APPROVAL, true));
            when(merchantService.registerPending(any())).thenReturn(new MerchantResponse(merchantId, "BrightMart",
                    "PV00234567", "sunil@brightmart.lk", "+94771234567", "12 Galle Road",
                    MerchantApprovalStatus.PENDING, submitted, null, null));

            var response = authService.registerMerchant(merchant);

            verify(userService).createUser(NewUser.withPassword("Sunil Fernando", "sunil@brightmart.lk",
                    "+94771234567", "$2a$m-hash", Role.MERCHANT, AccountStatus.PENDING_APPROVAL));
            verify(merchantService).registerPending(new NewMerchant(userId, "BrightMart", "PV00234567",
                    "sunil@brightmart.lk", "+94771234567", "12 Galle Road"));
            assertThat(response.merchantId()).isEqualTo(merchantId);
            assertThat(response.submittedAt()).isEqualTo(submitted);
            verify(sessionService, never()).issue(any(), anyBoolean()); // no session until approved
            verify(emailVerification).sendVerification(any());
        }
    }

    @Nested
    class GoogleSignIn {

        private final GoogleIdentity google = new GoogleIdentity("google-sub", "nimal@gmail.com", "Nimal Perera");

        @BeforeEach
        void verifiedCredential() {
            when(googleVerifier.verify("id-token")).thenReturn(google);
        }

        @Test
        void returningGoogleUserSignsStraightIn() {
            UserAccount user = account(null, "google-sub", Role.CUSTOMER, AccountStatus.ACTIVE);
            when(userService.findAccountByGoogleSubject("google-sub")).thenReturn(Optional.of(user));
            stubSessions();

            authService.loginWithGoogle(new GoogleLoginRequest("id-token", true));

            verify(sessionService).issue(user, true);
            verify(userService, never()).createUser(any());
            verify(userService, never()).linkGoogleAccount(any(), anyString());
        }

        @Test
        void unverifiedAccountWithTheSameEmailIsClaimedByTheGoogleOwner() {
            // Someone registered this address with a password but never proved they own it.
            UserAccount squatted = unverified(activeCustomer());
            UserAccount claimed = account(null, "google-sub", Role.CUSTOMER, AccountStatus.ACTIVE);
            when(userService.findAccountByGoogleSubject("google-sub")).thenReturn(Optional.empty());
            when(userService.findAccountByEmail("nimal@gmail.com")).thenReturn(Optional.of(squatted));
            when(userService.claimUnverifiedAccountWithGoogle(squatted.id(), "google-sub")).thenReturn(claimed);
            stubSessions();

            authService.loginWithGoogle(new GoogleLoginRequest("id-token", false));

            verify(userService).claimUnverifiedAccountWithGoogle(squatted.id(), "google-sub"); // password removed
            verify(sessionService).signOutEverywhere(squatted.id());
            verify(userService, never()).linkGoogleAccount(any(), anyString());
            verify(sessionService).issue(claimed, false);
        }

        @Test
        void existingAccountWithTheSameEmailIsLinked() {
            UserAccount existing = activeCustomer();
            when(userService.findAccountByGoogleSubject("google-sub")).thenReturn(Optional.empty());
            when(userService.findAccountByEmail("nimal@gmail.com")).thenReturn(Optional.of(existing));
            when(userService.linkGoogleAccount(existing.id(), "google-sub")).thenReturn(existing);
            stubSessions();

            authService.loginWithGoogle(new GoogleLoginRequest("id-token", false));

            verify(userService).linkGoogleAccount(existing.id(), "google-sub");
            verify(userService, never()).createUser(any());
        }

        @Test
        void staffCannotUseGoogleSignIn() {
            UserAccount linkedCourier = account(null, "google-sub", Role.COURIER, AccountStatus.ACTIVE);
            when(userService.findAccountByGoogleSubject("google-sub")).thenReturn(Optional.of(linkedCourier));

            assertApiError(catchThrowable(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token", false))),
                    ErrorCode.WRONG_SIGN_IN_PAGE);
            verify(sessionService, never()).issue(any(), anyBoolean());
        }

        @Test
        void staffAccountWithTheSameEmailIsNotLinkedToGoogle() {
            when(userService.findAccountByGoogleSubject("google-sub")).thenReturn(Optional.empty());
            when(userService.findAccountByEmail("nimal@gmail.com"))
                    .thenReturn(Optional.of(account("$2a$hash", null, Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE)));

            assertApiError(catchThrowable(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token", false))),
                    ErrorCode.WRONG_SIGN_IN_PAGE);
            verify(userService, never()).linkGoogleAccount(any(), anyString());
            verify(userService, never()).claimUnverifiedAccountWithGoogle(any(), anyString());
        }

        @Test
        void emailAlreadyLinkedToAnotherGoogleAccountIsRefused() {
            when(userService.findAccountByGoogleSubject("google-sub")).thenReturn(Optional.empty());
            when(userService.findAccountByEmail(anyString()))
                    .thenReturn(Optional.of(account(null, "other-sub", Role.CUSTOMER, AccountStatus.ACTIVE)));

            assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token", false)))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("different Google account");
            verify(userService, never()).linkGoogleAccount(any(), anyString());
        }

        @Test
        void newGoogleUserBecomesAnActiveCustomer() {
            when(userService.findAccountByGoogleSubject(anyString())).thenReturn(Optional.empty());
            when(userService.findAccountByEmail(anyString())).thenReturn(Optional.empty());
            when(userService.createUser(any())).thenAnswer(inv -> {
                NewUser n = inv.getArgument(0);
                return new UserAccount(UUID.randomUUID(), n.fullName(), n.email(), null, n.googleSubject(), n.role(),
                        n.accountStatus(),
                        n.emailVerified());
            });
            stubSessions();

            authService.loginWithGoogle(new GoogleLoginRequest("id-token", null));

            verify(userService).createUser(NewUser.withGoogle("Nimal Perera", "nimal@gmail.com", "google-sub",
                    Role.CUSTOMER, AccountStatus.ACTIVE));
            verify(sessionService).issue(any(), eq(false));
        }

        @Test
        void twoFirstTimeSignInsAtTheSameMomentGiveAFriendlyConflict() {
            when(userService.findAccountByGoogleSubject(anyString())).thenReturn(Optional.empty());
            when(userService.findAccountByEmail(anyString())).thenReturn(Optional.empty());
            when(userService.createUser(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token", false)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("sign in again");
        }

        @Test
        void pendingMerchantIsStillBlocked() {
            when(userService.findAccountByGoogleSubject(anyString())).thenReturn(Optional.of(
                    account("$2a$hash", "google-sub", Role.MERCHANT, AccountStatus.PENDING_APPROVAL)));

            assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("id-token", false)))
                    .isInstanceOf(AccountNotActiveException.class);
        }
    }

    @Test
    void googleNameFallsBackToTheEmailAndFitsTheColumn() {
        when(googleVerifier.verify("no-name")).thenReturn(new GoogleIdentity("s1", "kamal.silva@gmail.com", " "));
        when(googleVerifier.verify("long-name")).thenReturn(new GoogleIdentity("s2", "long@gmail.com", "x".repeat(200)));
        when(userService.findAccountByGoogleSubject(anyString())).thenReturn(Optional.empty());
        when(userService.findAccountByEmail(anyString())).thenReturn(Optional.empty());
        when(userService.createUser(any())).thenAnswer(inv -> {
            NewUser n = inv.getArgument(0);
            return new UserAccount(UUID.randomUUID(), n.fullName(), n.email(), null, n.googleSubject(), n.role(),
                    n.accountStatus(),
                        n.emailVerified());
        });
        stubSessions();

        authService.loginWithGoogle(new GoogleLoginRequest("no-name", false));
        authService.loginWithGoogle(new GoogleLoginRequest("long-name", false));

        ArgumentCaptor<NewUser> created = ArgumentCaptor.forClass(NewUser.class);
        verify(userService, times(2)).createUser(created.capture());
        assertThat(created.getAllValues().get(0).fullName()).isEqualTo("kamal.silva");
        assertThat(created.getAllValues().get(1).fullName()).hasSize(120); // users.full_name is VARCHAR(120)
    }

    @Test
    void googleVerificationFailureCreatesNothing() {
        when(googleVerifier.verify("bad")).thenThrow(new UnauthorizedException("Google sign-in failed."));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("bad", false)))
                .isInstanceOf(UnauthorizedException.class);
        verify(userService, never()).createUser(any());
    }
}
