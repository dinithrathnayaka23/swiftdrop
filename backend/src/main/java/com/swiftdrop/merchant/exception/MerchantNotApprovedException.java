package com.swiftdrop.merchant.exception;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import org.springframework.http.HttpStatus;

/**
 * 403 when a merchant that isn't APPROVED uses the portal or the order API. Reuses the account
 * status codes, so the frontend treats it like a blocked sign-in.
 */
public class MerchantNotApprovedException extends ApiException {

    private MerchantNotApprovedException(ErrorCode code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    /** Rejected and suspended share one message, so the response doesn't say which it was. */
    public static MerchantNotApprovedException forStatus(MerchantApprovalStatus status) {
        return switch (status) {
            case PENDING -> new MerchantNotApprovedException(ErrorCode.ACCOUNT_PENDING_APPROVAL,
                    "Your merchant account is awaiting approval. We'll email you as soon as it's approved.");
            case REJECTED, SUSPENDED -> new MerchantNotApprovedException(ErrorCode.ACCOUNT_DISABLED,
                    "This merchant account is not active. Please contact support.");
            case APPROVED -> throw new IllegalArgumentException("Approved merchants are not blocked");
        };
    }
}
