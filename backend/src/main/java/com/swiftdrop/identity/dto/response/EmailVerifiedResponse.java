package com.swiftdrop.identity.dto.response;

/** @param awaitingApproval true for a merchant: signing in also needs admin approval */
public record EmailVerifiedResponse(boolean awaitingApproval) {
}
