package com.swiftdrop.identity.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.identity.dto.request.CustomerRegistrationRequest;
import com.swiftdrop.identity.dto.request.GoogleLoginRequest;
import com.swiftdrop.identity.dto.request.LoginPortal;
import com.swiftdrop.identity.dto.request.LoginRequest;
import com.swiftdrop.identity.dto.request.MerchantRegistrationRequest;
import com.swiftdrop.identity.dto.response.MerchantRegistrationResponse;
import com.swiftdrop.identity.exception.AccountNotActiveException;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier;
import com.swiftdrop.identity.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.swiftdrop.identity.security.LoginAttemptGuard;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves who the user is (password or Google) and self-registration (customers and merchants).
 * The resulting sign-in session is created by {@link SessionService}.
 */
@Slf4j
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String EMAIL_TAKEN = "An account with this email already exists";
    private static final String REGISTRATION_NO_TAKEN = "A business with this registration number is already registered";

    private final UserService userService;
    private final MerchantService merchantService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleIdTokenVerifier googleVerifier;
    private final SessionService sessionService;
    private final LoginAttemptGuard loginAttempts;
    private final EmailVerificationService emailVerification;
    /** Compared against when the email is unknown, so both cases take the same time. */
    private final String dummyPasswordHash;

    public AuthService(UserService userService, MerchantService merchantService, PasswordEncoder passwordEncoder,
                       GoogleIdTokenVerifier googleVerifier, SessionService sessionService,
                       LoginAttemptGuard loginAttempts, EmailVerificationService emailVerification) {
        this.userService = userService;
        this.merchantService = merchantService;
        this.passwordEncoder = passwordEncoder;
        this.googleVerifier = googleVerifier;
        this.sessionService = sessionService;
        this.loginAttempts = loginAttempts;
        this.emailVerification = emailVerification;
        this.dummyPasswordHash = passwordEncoder.encode("swiftdrop-timing-equaliser");
    }

    /**
     * Password sign-in. After too many wrong passwords for an account the sign-in is paused (429)
     * before the password is even checked.
     *
     * @param clientIp the caller's IP, for the per-account-and-IP failure limit
     */
    @Transactional(readOnly = true)
    public IssuedSession login(LoginRequest request, String clientIp) {
        loginAttempts.checkAllowed(request.email(), clientIp);

        Optional<UserAccount> account = userService.findAccountByEmail(request.email());
        String hash = account.filter(UserAccount::hasPassword).map(UserAccount::passwordHash)
                .orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (account.isEmpty() || !account.get().hasPassword() || !passwordMatches) {
            loginAttempts.recordFailure(request.email(), clientIp);
            log.info("Failed sign-in attempt");
            // Same message whether the email or the password was wrong: emails can't be enumerated.
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        UserAccount user = account.get();
        loginAttempts.recordSuccess(request.email(), clientIp);
        // The role, email and account status are revealed only after the correct password.
        requirePortal(request.portalOrDefault(), user);
        SessionService.requireCanSignIn(user);
        log.info("User {} signed in (remember me: {})", user.id(), request.rememberMeOrDefault());
        return sessionService.issue(user, request.rememberMeOrDefault());
    }

    /**
     * Signs in with a verified Google account. Finds the account by Google ID; otherwise uses the
     * account with the same email (Google has proven the user owns it); otherwise creates a customer
     * account. Merchants still need approval: a pending merchant gets the same 403 as with a password.
     * Google sign-in is only on the main page, so staff accounts are sent to their own pages.
     */
    @Transactional
    public IssuedSession loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdentity google = googleVerifier.verify(request.credential());
        UserAccount user;
        try {
            user = userService.findAccountByGoogleSubject(google.subject())
                    .or(() -> userService.findAccountByEmail(google.email()).map(existing -> linkGoogle(existing, google)))
                    .orElseGet(() -> registerGoogleCustomer(google));
        } catch (DataIntegrityViolationException ex) {
            // Two first-time Google sign-ins for the same account at the same moment: one wins.
            throw new ConflictException("Your account was just set up. Please sign in again.");
        }
        requirePortal(LoginPortal.PUBLIC, user);
        SessionService.requireCanSignIn(user);
        log.info("User {} signed in with Google (remember me: {})", user.id(), request.rememberMeOrDefault());
        return sessionService.issue(user, request.rememberMeOrDefault());
    }

    private static void requirePortal(LoginPortal portal, UserAccount user) {
        if (!portal.allows(user.role())) {
            throw AccountNotActiveException.wrongSignInPage(user.role());
        }
    }

    private UserAccount linkGoogle(UserAccount existing, GoogleIdentity google) {
        // Staff can't use Google sign-in: refuse before the account is changed.
        requirePortal(LoginPortal.PUBLIC, existing);
        if (existing.googleSubject() != null) {
            // The email now belongs to another Google account: don't silently hand the account over.
            throw new UnauthorizedException("This email is already linked to a different Google account.");
        }
        if (!existing.emailVerified()) {
            // Nobody ever proved they own this address, so whoever registered it may be an impostor
            // waiting for the real owner to use the account. Google has now proven ownership: the
            // account goes to the Google user, and the unproven password and its sessions go away.
            UserAccount claimed = userService.claimUnverifiedAccountWithGoogle(existing.id(), google.subject());
            sessionService.signOutEverywhere(existing.id());
            log.info("Unverified account {} claimed by its email owner through Google", existing.id());
            return claimed;
        }
        log.info("Linked Google account to user {}", existing.id());
        return userService.linkGoogleAccount(existing.id(), google.subject());
    }

    private UserAccount registerGoogleCustomer(GoogleIdentity google) {
        UserAccount user = userService.createUser(NewUser.withGoogle(displayName(google), google.email(),
                google.subject(), Role.CUSTOMER, AccountStatus.ACTIVE));
        log.info("Customer {} registered with Google", user.id());
        return user;
    }

    private static String displayName(GoogleIdentity google) {
        String name = google.name() == null || google.name().isBlank()
                ? google.email().substring(0, google.email().indexOf('@'))
                : google.name().trim();
        return name.length() > 120 ? name.substring(0, 120) : name; // users.full_name is VARCHAR(120)
    }

    /**
     * Creates a customer account and emails a confirmation link. No session yet: the customer can
     * sign in once the email address is confirmed.
     */
    @Transactional
    public UserAccount registerCustomer(CustomerRegistrationRequest request) {
        if (userService.emailExists(request.email())) {
            throw ConflictException.forField("email", EMAIL_TAKEN);
        }
        UserAccount user;
        try {
            user = userService.createUser(NewUser.withPassword(request.fullName(), request.email(),
                    request.phone(), passwordEncoder.encode(request.password()), Role.CUSTOMER, AccountStatus.ACTIVE));
        } catch (DataIntegrityViolationException ex) {
            throw ConflictException.forField("email", EMAIL_TAKEN); // same email registered at the same moment
        }
        log.info("Customer {} registered", user.id());
        emailVerification.sendVerification(user);
        return user;
    }

    /**
     * Creates the MERCHANT user (pending) and the merchant (pending) in one transaction, and emails
     * a confirmation link. Signing in needs both the confirmed email and admin approval.
     */
    @Transactional
    public MerchantRegistrationResponse registerMerchant(MerchantRegistrationRequest request) {
        if (userService.emailExists(request.email())) {
            throw ConflictException.forField("email", EMAIL_TAKEN);
        }
        if (merchantService.registrationNoExists(request.registrationNo())) {
            throw ConflictException.forField("registrationNo", REGISTRATION_NO_TAKEN);
        }
        MerchantResponse merchant;
        UserAccount contactPerson;
        try {
            contactPerson = userService.createUser(NewUser.withPassword(request.contactPersonName(),
                    request.email(), request.phone(), passwordEncoder.encode(request.password()),
                    Role.MERCHANT, AccountStatus.PENDING_APPROVAL));
            merchant = merchantService.registerPending(new NewMerchant(contactPerson.id(),
                    request.businessName(), request.registrationNo(), request.email(), request.phone(),
                    request.businessAddress()));
        } catch (DataIntegrityViolationException ex) {
            // Same email or registration number submitted at the same moment: say which one.
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
            throw cause.contains("ux_merchants_registration_no")
                    ? ConflictException.forField("registrationNo", REGISTRATION_NO_TAKEN)
                    : ConflictException.forField("email", EMAIL_TAKEN);
        }
        emailVerification.sendVerification(contactPerson);
        return new MerchantRegistrationResponse(merchant.merchantId(), merchant.businessName(),
                merchant.registrationNo(), merchant.approvalStatus(), merchant.submittedAt());
    }
}
