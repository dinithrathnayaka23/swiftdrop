package com.swiftdrop.identity.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.identity.dto.request.CustomerRegistrationRequest;
import com.swiftdrop.identity.dto.request.EmailVerificationRequest;
import com.swiftdrop.identity.dto.request.GoogleLoginRequest;
import com.swiftdrop.identity.dto.request.LoginRequest;
import com.swiftdrop.identity.dto.request.MerchantRegistrationRequest;
import com.swiftdrop.identity.dto.request.PasswordResetConfirmRequest;
import com.swiftdrop.identity.dto.request.PasswordResetRequest;
import com.swiftdrop.identity.dto.request.ResendVerificationRequest;
import com.swiftdrop.identity.dto.response.AuthResponse;
import com.swiftdrop.identity.dto.response.CustomerRegistrationResponse;
import com.swiftdrop.identity.dto.response.EmailVerifiedResponse;
import com.swiftdrop.identity.dto.response.GoogleConfigResponse;
import com.swiftdrop.identity.dto.response.MerchantRegistrationResponse;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier;
import com.swiftdrop.identity.security.SessionCookies;
import com.swiftdrop.identity.service.AuthService;
import com.swiftdrop.identity.service.EmailVerificationService;
import com.swiftdrop.identity.service.IssuedSession;
import com.swiftdrop.identity.service.PasswordResetService;
import com.swiftdrop.identity.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (/api/v1/auth/** is permitted in SecurityConfig).
 *
 * <p>Successful sign-ins return the access token in the body and set the refresh token as an
 * httpOnly cookie ({@link SessionCookies}); refresh and sign-out read that cookie.
 */
@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final GoogleIdTokenVerifier googleVerifier;
    private final SessionCookies sessionCookies;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // The visitor's IP (taken from X-Forwarded-For behind nginx), for the failed-sign-in limit.
        return signedIn(HttpStatus.OK, authService.login(request, http.getRemoteAddr()));
    }

    /** Whether "Sign in with Google" is configured, and the client ID the button needs. */
    @GetMapping("/google/config")
    public GoogleConfigResponse googleConfig() {
        return googleVerifier.isEnabled()
                ? new GoogleConfigResponse(true, googleVerifier.clientId())
                : new GoogleConfigResponse(false, null);
    }

    /** Signs in (or signs up as a customer) with a Google ID token. */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return signedIn(HttpStatus.OK, authService.loginWithGoogle(request));
    }

    /**
     * New access token (and a renewed session cookie) for the session cookie. 401 when the session
     * is over; the dead cookie is then deleted so the browser stops sending it.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return signedIn(HttpStatus.OK, sessionService.refresh(sessionCookies.read(request)));
        } catch (ApiException ex) {
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookies.clear());
            throw ex;
        }
    }

    /** Ends this device's session and deletes the cookie. Always 204, even without a session. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sessionService.signOut(sessionCookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessionCookies.clear()).build();
    }

    /** Creates a customer account and emails a confirmation link. No session until it is confirmed. */
    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerRegistrationResponse registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request) {
        return new CustomerRegistrationResponse(authService.registerCustomer(request).email());
    }

    /** Submits a merchant for approval. No session: it needs a confirmed email and admin approval. */
    @PostMapping("/register/merchant")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantRegistrationResponse registerMerchant(@Valid @RequestBody MerchantRegistrationRequest request) {
        return authService.registerMerchant(request);
    }

    /** Confirms the email address from the emailed link. 400 if the link is invalid or expired. */
    @PostMapping("/verify-email/confirm")
    public EmailVerifiedResponse confirmEmail(@Valid @RequestBody EmailVerificationRequest request) {
        var user = emailVerificationService.confirm(request.token());
        return new EmailVerifiedResponse(EmailVerificationService.awaitingApproval(user));
    }

    /** Sends a new confirmation link. Always 202, whether or not the email exists. */
    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
    }

    /** Always 202, whether or not the email exists. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
    }

    private ResponseEntity<AuthResponse> signedIn(HttpStatus status, IssuedSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        sessionCookies.issue(session.refreshToken(), session.rememberMe(), session.refreshLifetime()))
                .body(session.response());
    }
}
