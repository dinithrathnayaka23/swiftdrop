package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.settings.model.FailedDeliveryAction;

/**
 * The System Settings default for a failed delivery. RETURN_TO_SENDER and HOLD_FOR_COLLECTION have no
 * parcel status of their own: the screen uses them to pre-fill the close reason.
 */
public record DefaultFailedActionResponse(FailedDeliveryAction action) {
}
