package com.swiftdrop.identity.dto.response;

/** What the browser needs to show the "Sign in with Google" button. The client ID is public. */
public record GoogleConfigResponse(boolean enabled, String clientId) {
}
