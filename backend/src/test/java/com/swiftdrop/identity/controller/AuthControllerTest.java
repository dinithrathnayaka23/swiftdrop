package com.swiftdrop.identity.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.exception.GlobalExceptionHandler;
import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.identity.dto.request.GoogleLoginRequest;
import com.swiftdrop.identity.dto.request.LoginRequest;
import com.swiftdrop.identity.dto.response.AuthResponse;
import com.swiftdrop.identity.dto.response.MerchantRegistrationResponse;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier;
import com.swiftdrop.identity.security.SessionCookies;
import com.swiftdrop.identity.exception.InvalidVerificationTokenException;
import com.swiftdrop.identity.service.AuthService;
import com.swiftdrop.identity.service.EmailVerificationService;
import com.swiftdrop.identity.service.IssuedSession;
import com.swiftdrop.identity.service.PasswordResetService;
import com.swiftdrop.identity.service.SessionService;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP layer only: routes, status codes, JSON, cookies and request validation. Services are
 * Mockito mocks and no Spring context is started (standalone MockMvc).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String COOKIE = "swiftdrop_session";

    @Mock private AuthService authService;
    @Mock private SessionService sessionService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private GoogleIdTokenVerifier googleVerifier;

    private MockMvc mvc;

    private static IssuedSession session(boolean rememberMe) {
        return new IssuedSession(new AuthResponse("access-jwt", 900, rememberMe,
                new AuthResponse.AuthUser("user-1", "Nimal Perera", Role.CUSTOMER)), "refresh-jwt",
                rememberMe ? Duration.ofDays(30) : Duration.ofDays(1));
    }

    @BeforeEach
    void setUp() {
        var controller = new AuthController(authService, sessionService, passwordResetService,
                emailVerificationService, googleVerifier, new SessionCookies(COOKIE, true));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void loginReturnsTheAccessTokenAndSetsTheSessionCookie() throws Exception {
        when(authService.login(eq(new LoginRequest("nimal@example.com", "parcel2026", true)), eq("203.0.113.7")))
                .thenReturn(session(true));

        mvc.perform(json(post("/api/v1/auth/login"),
                        "{\"email\":\"nimal@example.com\",\"password\":\"parcel2026\",\"rememberMe\":true}")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()) // never readable by page scripts
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE + "=refresh-jwt"), containsString("HttpOnly"),
                        containsString("SameSite=Strict"), containsString("Max-Age=2592000"))));
    }

    @Test
    void invalidLoginBodyIsRejectedBeforeTheService() throws Exception {
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
        verifyNoInteractions(authService);
    }

    @Test
    void malformedJsonGetsAClearMessage() throws Exception {
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing or is not valid JSON"));
    }

    @Test
    void serviceErrorsUseTheStandardErrorFormat() throws Exception {
        when(authService.login(any(), any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"a@b.lk\",\"password\":\"wrong1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void pausedSignInIs429WithRetryAfter() throws Exception {
        when(authService.login(any(), any())).thenThrow(
                new RateLimitExceededException("Too many failed sign-in attempts.", Duration.ofSeconds(90)));

        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"a@b.lk\",\"password\":\"wrong1234\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value(
                        "Too many failed sign-in attempts. Please try again in 2 minutes."));
    }

    @Test
    void refreshReadsTheCookieAndRenewsIt() throws Exception {
        when(sessionService.refresh("old-refresh")).thenReturn(session(false));

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("access-jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE + "=refresh-jwt"), not(containsString("Max-Age")))));
    }

    @Test
    void failedRefreshDeletesTheDeadCookie() throws Exception {
        when(sessionService.refresh(null)).thenThrow(new UnauthorizedException("Your session has expired."));

        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void logoutEndsTheSessionAndDeletesTheCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, "refresh-jwt")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(sessionService).signOut("refresh-jwt");
    }

    @Test
    void customerRegistrationIs201WithoutASessionUntilTheEmailIsConfirmed() throws Exception {
        when(authService.registerCustomer(any())).thenReturn(new UserAccount(UUID.randomUUID(), "Nimal Perera",
                "nimal@example.com", "$2a$hash", null, Role.CUSTOMER, AccountStatus.ACTIVE, false));

        mvc.perform(json(post("/api/v1/auth/register/customer"), """
                        {"fullName":"Nimal Perera","email":"nimal@example.com","phone":"+94712345678",
                         "password":"parcel2026"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nimal@example.com"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void confirmingTheEmailSaysWhetherApprovalIsStillNeeded() throws Exception {
        when(emailVerificationService.confirm("verify-jwt")).thenReturn(new UserAccount(UUID.randomUUID(), "Shop",
                "shop@example.com", "$2a$hash", null, Role.MERCHANT, AccountStatus.PENDING_APPROVAL, true));

        mvc.perform(json(post("/api/v1/auth/verify-email/confirm"), "{\"token\":\"verify-jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awaitingApproval").value(true));
    }

    @Test
    void invalidConfirmationLinkIs400() throws Exception {
        when(emailVerificationService.confirm("bad")).thenThrow(new InvalidVerificationTokenException());

        mvc.perform(json(post("/api/v1/auth/verify-email/confirm"), "{\"token\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void resendingTheConfirmationLinkIsAlways202() throws Exception {
        mvc.perform(json(post("/api/v1/auth/verify-email/resend"), "{\"email\":\"nimal@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(emailVerificationService).resend("nimal@example.com");

        mvc.perform(json(post("/api/v1/auth/verify-email/resend"), "{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void merchantRegistrationIs201WithoutASession() throws Exception {
        when(authService.registerMerchant(any())).thenReturn(new MerchantRegistrationResponse(UUID.randomUUID(),
                "BrightMart", "PV00234567", MerchantApprovalStatus.PENDING, Instant.now()));

        mvc.perform(json(post("/api/v1/auth/register/merchant"), """
                        {"businessName":"BrightMart","registrationNo":"PV00234567","contactPersonName":"Sunil Fernando",
                         "email":"sunil@brightmart.lk","phone":"+94771234567",
                         "businessAddress":"12 Galle Road, Colombo 03","password":"merchant2026"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void badRegistrationNumberIsAFieldError() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register/merchant"), """
                        {"businessName":"BrightMart","registrationNo":"AB123","contactPersonName":"Sunil Fernando",
                         "email":"sunil@brightmart.lk","phone":"+94771234567",
                         "businessAddress":"12 Galle Road, Colombo 03","password":"merchant2026"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("registrationNo"));
        verifyNoInteractions(authService);
    }

    @Test
    void passwordResetRequestIsAlways202() throws Exception {
        mvc.perform(json(post("/api/v1/auth/password-reset"), "{\"email\":\"nimal@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(passwordResetService).requestReset("nimal@example.com");
    }

    @Test
    void passwordResetConfirmIs204() throws Exception {
        mvc.perform(json(post("/api/v1/auth/password-reset/confirm"),
                        "{\"token\":\"reset-jwt\",\"newPassword\":\"newpass2026\"}"))
                .andExpect(status().isNoContent());
        verify(passwordResetService).confirmReset("reset-jwt", "newpass2026");
    }

    @Test
    void googleConfigShowsTheClientIdOnlyWhenEnabled() throws Exception {
        when(googleVerifier.isEnabled()).thenReturn(true);
        when(googleVerifier.clientId()).thenReturn("abc.apps.googleusercontent.com");
        mvc.perform(get("/api/v1/auth/google/config"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.clientId").value("abc.apps.googleusercontent.com"));

        when(googleVerifier.isEnabled()).thenReturn(false);
        mvc.perform(get("/api/v1/auth/google/config"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.clientId").doesNotExist());
    }

    @Test
    void googleSignInSetsTheSessionCookie() throws Exception {
        when(authService.loginWithGoogle(new GoogleLoginRequest("google-id-token", true))).thenReturn(session(true));

        mvc.perform(json(post("/api/v1/auth/google"), "{\"credential\":\"google-id-token\",\"rememberMe\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE + "=refresh-jwt")));
    }

    @Test
    void googleSignInNeedsACredential() throws Exception {
        mvc.perform(json(post("/api/v1/auth/google"), "{\"credential\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }
}
