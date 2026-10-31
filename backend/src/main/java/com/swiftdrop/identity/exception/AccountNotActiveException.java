package com.swiftdrop.identity.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.common.security.Role;
import org.springframework.http.HttpStatus;

/** 403 after a correct password, when the account can't be used yet (email unconfirmed, pending approval) or at all (disabled). */
public class AccountNotActiveException extends ApiException {

    private AccountNotActiveException(ErrorCode code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public static AccountNotActiveException pendingApproval() {
        return new AccountNotActiveException(ErrorCode.ACCOUNT_PENDING_APPROVAL,
                "Your merchant account is awaiting approval. We'll email you as soon as it's approved.");
    }

    public static AccountNotActiveException emailNotVerified() {
        return new AccountNotActiveException(ErrorCode.EMAIL_NOT_VERIFIED,
                "Please confirm your email address first. Check your inbox for the link we sent you.");
    }

    /** The account is fine, but belongs on another sign-in page; the message says which. */
    public static AccountNotActiveException wrongSignInPage(Role role) {
        String message = switch (role) {
            case COURIER -> "This is a courier account. Please sign in through the courier app.";
            case DISPATCHER_ADMIN -> "This is an admin account. Please sign in through the admin portal.";
            case CUSTOMER, MERCHANT -> "This sign-in page is for SwiftDrop staff. Please use the main sign-in page.";
        };
        return new AccountNotActiveException(ErrorCode.WRONG_SIGN_IN_PAGE, message);
    }

    public static AccountNotActiveException disabled() {
        return new AccountNotActiveException(ErrorCode.ACCOUNT_DISABLED,
                "This account is not active. Please contact support.");
    }
}
